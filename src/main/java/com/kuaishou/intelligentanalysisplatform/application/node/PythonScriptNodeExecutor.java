package com.kuaishou.intelligentanalysisplatform.application.node;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.config.SandboxProperties;
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
 * <h3>Sandbox protections</h3>
 * <ul>
 *   <li><b>Restricted mode (default)</b> — wraps the user script in a security harness that
 *       patches {@code builtins.__import__} to block dangerous modules (configurable via
 *       {@code sandbox.python.blocked-modules}) and removes {@code builtins.open}.</li>
 *   <li><b>Firejail mode</b> — wraps the {@code python3} process with {@code firejail}
 *       (Linux only). Requires {@code firejail} to be installed on the host.</li>
 *   <li><b>Timeout</b> — process is killed with {@link Process#destroyForcibly()} after
 *       {@code sandbox.python.max-timeout-seconds}.</li>
 * </ul>
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
     * Restricted execution wrapper (macOS / universal — method B from design doc).
     *
     * <p>Substitution tokens (replaced via {@link String#replace}, not {@link String#format}):
     * <ul>
     *   <li>{@code {{BLOCKED_MODULES}}} — Python frozenset literal of blocked module names</li>
     *   <li>{@code {{USER_CODE}}}       — raw user script body</li>
     * </ul></p>
     */
    private static final String RESTRICTED_TEMPLATE =
            "import sys as _sys, json as _json, builtins as _builtins\n" +
            "\n" +
            "# Sandbox: block dangerous module imports via __import__ hook\n" +
            "_SANDBOX_BLOCKED = {{BLOCKED_MODULES}}\n" +
            "_real_import = _builtins.__import__\n" +
            "\n" +
            "def _guarded_import(name, *args, **kwargs):\n" +
            "    top = name.split('.')[0]\n" +
            "    if top in _SANDBOX_BLOCKED:\n" +
            "        raise ImportError(\"Module '{}' is blocked by the execution sandbox\".format(name))\n" +
            "    return _real_import(name, *args, **kwargs)\n" +
            "\n" +
            "_builtins.__import__ = _guarded_import\n" +
            "\n" +
            "# Sandbox: disable direct file I/O\n" +
            "_builtins.open = None\n" +
            "\n" +
            "# Load input rows from stdin\n" +
            "_payload = _json.loads(_sys.stdin.read())\n" +
            "rows = _payload.get('rows', [])\n" +
            "\n" +
            "# ---- user script begin ----\n" +
            "{{USER_CODE}}\n" +
            "# ---- user script end ----\n" +
            "\n" +
            "if 'output_rows' in dir():\n" +
            "    _sys.stdout.write(_json.dumps({'rows': output_rows}))\n" +
            "else:\n" +
            "    _sys.stdout.write(_json.dumps({'rows': rows}))\n";

    private final ComputeDatasetResolver computeDatasetResolver;
    private final ComputeResultFactory computeResultFactory;
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final ObjectMapper objectMapper;
    private final SandboxProperties sandboxProperties;

    public PythonScriptNodeExecutor(ComputeDatasetResolver computeDatasetResolver,
                                    ComputeResultFactory computeResultFactory,
                                    NodeMetadataApplicationService nodeMetadataApplicationService,
                                    ObjectMapper objectMapper,
                                    SandboxProperties sandboxProperties) {
        this.computeDatasetResolver = computeDatasetResolver;
        this.computeResultFactory = computeResultFactory;
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.objectMapper = objectMapper;
        this.sandboxProperties = sandboxProperties;
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
        String mode = sandboxProperties.getPythonMode();
        if ("firejail".equals(mode)) {
            return runWithFirejail(userScript, inputRows, timeoutSeconds);
        } else {
            return runWithRestrictedPython(userScript, inputRows, timeoutSeconds);
        }
    }

    // -------------------------------------------------------------------------
    // Mode A: firejail (Linux)
    // -------------------------------------------------------------------------

    /**
     * Executes the user script inside a {@code firejail} sandbox (Linux only).
     *
     * <p>Requires {@code firejail} to be installed. Key restrictions applied:
     * <ul>
     *   <li>{@code --net=none} — no outbound network</li>
     *   <li>{@code --read-only=/} — root filesystem is read-only</li>
     *   <li>{@code --read-write=<tempDir>} — only the script's temp directory is writable</li>
     *   <li>{@code --noroot} — prevents privilege escalation</li>
     * </ul></p>
     */
    private List<Map<String, Object>> runWithFirejail(String userScript,
                                                       List<Map<String, Object>> inputRows,
                                                       int timeoutSeconds) {
        Path scriptFile = null;
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("py_node_firejail_");
            String wrappedScript = buildRestrictedScript(userScript);
            scriptFile = tempDir.resolve("script.py");
            Files.writeString(scriptFile, wrappedScript, StandardCharsets.UTF_8);

            String inputJson = objectMapper.writeValueAsString(Map.of("rows", inputRows));

            ProcessBuilder pb = new ProcessBuilder(
                    "firejail",
                    "--quiet",
                    "--net=none",
                    "--read-only=/",
                    "--read-write=" + tempDir.toAbsolutePath(),
                    "--noroot",
                    sandboxProperties.getPythonBin(),
                    scriptFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            return executeProcess(pb, inputJson, timeoutSeconds);
        } catch (BaseBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "python script (firejail) execution error: " + e.getMessage());
        } finally {
            deleteQuietly(tempDir);
        }
    }

    // -------------------------------------------------------------------------
    // Mode B: restricted Python (macOS / universal)
    // -------------------------------------------------------------------------

    /**
     * Executes the user script with a Python-level import hook that blocks dangerous modules.
     *
     * <p>This mode works on all platforms without additional OS tools. The harness patches
     * {@code builtins.__import__} before running user code, blocking any attempt to import
     * modules in the configured blocked list.</p>
     */
    private List<Map<String, Object>> runWithRestrictedPython(String userScript,
                                                               List<Map<String, Object>> inputRows,
                                                               int timeoutSeconds) {
        Path scriptFile = null;
        try {
            String wrappedScript = buildRestrictedScript(userScript);
            scriptFile = Files.createTempFile("py_node_", ".py");
            Files.writeString(scriptFile, wrappedScript, StandardCharsets.UTF_8);

            String inputJson = objectMapper.writeValueAsString(Map.of("rows", inputRows));

            ProcessBuilder pb = new ProcessBuilder(
                    sandboxProperties.getPythonBin(),
                    scriptFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            return executeProcess(pb, inputJson, timeoutSeconds);
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

    // -------------------------------------------------------------------------
    // Shared process execution logic
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> executeProcess(ProcessBuilder pb,
                                                      String inputJson,
                                                      int timeoutSeconds) throws Exception {
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

        // Read stdout asynchronously to prevent pipe-buffer deadlock
        CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return process.getInputStream().readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });

        // Enforce timeout
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "python script timed out after " + timeoutSeconds + "s");
        }

        byte[] outputBytes = outputFuture.get(5, TimeUnit.SECONDS);
        int exitCode = process.exitValue();
        String output = new String(outputBytes, StandardCharsets.UTF_8).trim();

        if (exitCode != 0) {
            // Detect sandbox import block errors
            if (output.contains("blocked by the execution sandbox")) {
                throw new BaseBusinessException(ErrorCode.SANDBOX_REJECTED,
                        "python script attempted a forbidden import: " + truncate(output, 300));
            }
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "python script failed (exit=" + exitCode + "): " + truncate(output, 500));
        }

        return parseOutputRows(output);
    }

    // -------------------------------------------------------------------------
    // Script construction
    // -------------------------------------------------------------------------

    /**
     * Builds the security-hardened script by substituting blocked-modules list and user code
     * into {@link #RESTRICTED_TEMPLATE}.
     */
    private String buildRestrictedScript(String userCode) {
        String blockedModulesPyLiteral = sandboxProperties.pythonBlockedModules().stream()
                .map(m -> "'" + m + "'")
                .collect(Collectors.joining(", ", "frozenset({", "})"));

        return RESTRICTED_TEMPLATE
                .replace("{{BLOCKED_MODULES}}", blockedModulesPyLiteral)
                .replace("{{USER_CODE}}", userCode);
    }

    // -------------------------------------------------------------------------
    // Output parsing & helpers
    // -------------------------------------------------------------------------

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

    private void deleteQuietly(Path dir) {
        if (dir == null) return;
        try {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
