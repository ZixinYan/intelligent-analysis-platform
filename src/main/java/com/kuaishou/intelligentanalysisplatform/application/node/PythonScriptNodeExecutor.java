package com.kuaishou.intelligentanalysisplatform.application.node;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.PythonScriptNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

/**
 * Executes a user-provided Python 3 script against the upstream dataset.
 *
 * <p>The script is executed in an isolated OS subprocess via {@code python3}.
 * Input rows are written to the process stdin as JSON; the script must write
 * the result rows to stdout as JSON. Both streams use UTF-8.</p>
 *
 * <h3>User contract</h3>
 * <pre>{@code
 * # Available variable:  rows — list[dict]
 * # Must assign to:      output_rows — list[dict]
 *
 * output_rows = [r for r in rows if r.get('score', 0) >= 60]
 * }</pre>
 */
@Component
public class PythonScriptNodeExecutor implements NodeExecutor<PythonScriptNodeConfigDTO> {

    private static final String NODE_TYPE = "python_script";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Wrapper template: injects {@code rows} from stdin, expects {@code output_rows} to be set.
     */
    private static final String SCRIPT_TEMPLATE =
            "import sys, json\n" +
            "_payload = json.loads(sys.stdin.read())\n" +
            "rows = _payload.get('rows', [])\n" +
            "# ---- user script begin ----\n" +
            "%s\n" +
            "# ---- user script end ----\n" +
            "if 'output_rows' in dir():\n" +
            "    sys.stdout.write(json.dumps({'rows': output_rows}))\n" +
            "else:\n" +
            "    sys.stdout.write(json.dumps({'rows': rows}))\n";

    private final ComputeDatasetResolver computeDatasetResolver;
    private final ComputeResultFactory computeResultFactory;
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ObjectMapper objectMapper;

    public PythonScriptNodeExecutor(ComputeDatasetResolver computeDatasetResolver,
                                    ComputeResultFactory computeResultFactory,
                                    NodeMetadataApplicationService nodeMetadataApplicationService,
                                    ObjectMapper objectMapper) {
        this.computeDatasetResolver = computeDatasetResolver;
        this.computeResultFactory = computeResultFactory;
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, PythonScriptNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());

        List<Map<String, Object>> inputRows = input.getRows() != null ? input.getRows() : List.of();
        List<Map<String, Object>> outputRows = runScript(config.getScript(), inputRows,
                effectiveTimeout(config.getTimeoutSeconds()));

        DatasetDTO output = DatasetDTO.builder()
                .schema(input.getSchema())
                .rows(outputRows)
                .build();

        return computeResultFactory.success(context.getNodeId(), NODE_TYPE, output,
                false, NODE_TYPE, System.currentTimeMillis() - start);
    }

    @Override
    public ValidationResultDTO validate(PythonScriptNodeConfigDTO config) {
        if (config == null || config.getScript() == null || config.getScript().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("python script is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(NODE_TYPE);
    }

    // -------------------------------------------------------------------------

    private List<Map<String, Object>> runScript(String userScript,
                                                 List<Map<String, Object>> inputRows,
                                                 int timeoutSeconds) {
        Path scriptFile = null;
        try {
            // Materialize wrapped script to a temp file
            String wrappedScript = String.format(SCRIPT_TEMPLATE, userScript);
            scriptFile = Files.createTempFile("py_node_", ".py");
            Files.writeString(scriptFile, wrappedScript, StandardCharsets.UTF_8);

            String inputJson = objectMapper.writeValueAsString(Map.of("rows", inputRows));

            ProcessBuilder pb = new ProcessBuilder("python3", scriptFile.toAbsolutePath().toString());
            pb.redirectErrorStream(true);   // merge stderr → stdout for unified error capture
            Process process = pb.start();

            // Write stdin in a daemon thread to avoid pipe deadlock
            byte[] inputBytes = inputJson.getBytes(StandardCharsets.UTF_8);
            Thread stdinWriter = new Thread(() -> {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(inputBytes);
                } catch (IOException ignored) {
                    // process may have died early
                }
            });
            stdinWriter.setDaemon(true);
            stdinWriter.start();

            // Read stdout in a background future; the real timeout guard is waitFor() below.
            // Doing this avoids blocking the main thread when the script produces no output
            // before the OS pipe buffer fills up.
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return process.getInputStream().readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });

            // Enforce timeout: waitFor blocks until the process exits or the deadline passes
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly(); // closes the stream → outputFuture unblocks
                throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                        "python script timed out after " + timeoutSeconds + "s");
            }

            // Process exited normally; stdout is fully buffered — collect it quickly
            byte[] outputBytes = outputFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            String output = new String(outputBytes, StandardCharsets.UTF_8).trim();

            if (exitCode != 0) {
                throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                        "python script failed (exit=" + exitCode + "): " + truncate(output, 500));
            }

            return parseOutputRows(output);

        } catch (BaseBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "python script execution error: " + e.getMessage());
        } finally {
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseOutputRows(String output) {
        try {
            Map<String, Object> result = objectMapper.readValue(output, MAP_TYPE);
            Object rows = result.get("rows");
            if (rows instanceof List<?> list) {
                List<Map<String, Object>> parsed = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        map.forEach((k, v) -> row.put(String.valueOf(k), v));
                        parsed.add(row);
                    }
                }
                return parsed;
            }
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "python script output must be {\"rows\": [...]}, got: " + truncate(output, 200));
        } catch (BaseBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to parse python script output: " + truncate(output, 300));
        }
    }

    private int effectiveTimeout(Integer configured) {
        return (configured != null && configured > 0) ? configured : DEFAULT_TIMEOUT_SECONDS;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
