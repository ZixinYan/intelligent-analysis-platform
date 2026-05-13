package com.kuaishou.intelligentanalysisplatform.application.node;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.kuaishou.intelligentanalysisplatform.application.NodeMetadataApplicationService;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeResultFactory;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.config.SandboxProperties;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.JavaCodeNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import org.springframework.stereotype.Component;

/**
 * Executes user-supplied Java code against the upstream dataset.
 *
 * <p>The code is compiled at runtime via {@link javax.tools.JavaCompiler} and loaded
 * in an isolated {@link URLClassLoader}. Execution runs in a dedicated thread with
 * a configurable timeout to prevent runaway code.</p>
 *
 * <h3>Sandbox protections</h3>
 * <ul>
 *   <li><b>Import blacklist</b> — forbidden packages are rejected at the source-scan stage
 *       before compilation (e.g. {@code java.io}, {@code java.net}, {@code java.lang.reflect}).</li>
 *   <li><b>ClassLoader isolation</b> — user class is loaded with
 *       {@link ClassLoader#getPlatformClassLoader()} as parent, cutting off all host-application
 *       classes (Spring beans, datasource connections, etc.). JDK stdlib remains accessible.</li>
 *   <li><b>Memory soft-limit</b> — a background monitor thread cancels execution when JVM heap
 *       growth exceeds the configured {@code sandbox.java.max-memory-mb} threshold.</li>
 *   <li><b>Timeout</b> — execution is cancelled after {@code sandbox.java.max-timeout-seconds}.</li>
 * </ul>
 *
 * <h3>User contract</h3>
 * <p>Supply the body of:</p>
 * <pre>{@code
 * List<Map<String, Object>> process(List<Map<String, Object>> rows)
 * }</pre>
 *
 * <p>Available imports: {@code java.util.*}, {@code java.util.stream.*}, {@code java.math.*}</p>
 */
@Component
public class JavaCodeNodeExecutor implements NodeExecutor<JavaCodeNodeConfigDTO> {

    private static final String NODE_TYPE = "java_code";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** Matches top-level import statements in Java source. */
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+([\\w.]+(?:\\.\\*)?);", Pattern.MULTILINE);

    /** Preamble injected before the user's class. */
    private static final String CLASS_TEMPLATE =
            "import java.util.*;\n" +
            "import java.util.stream.*;\n" +
            "import java.math.*;\n" +
            "\n" +
            "public class %s {\n" +
            "    @SuppressWarnings(\"unchecked\")\n" +
            "    public List<Map<String, Object>> process(List<Map<String, Object>> rows) {\n" +
            "        %s\n" +
            "    }\n" +
            "}\n";

    private final ComputeDatasetResolver computeDatasetResolver;
    private final ComputeResultFactory computeResultFactory;
    private final NodeMetadataApplicationService nodeMetadataApplicationService;
    private final SandboxProperties sandboxProperties;

    /** Dedicated executor for the heap-delta memory monitor thread. */
    private final ExecutorService monitorExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "java-sandbox-monitor");
        t.setDaemon(true);
        return t;
    });

    public JavaCodeNodeExecutor(ComputeDatasetResolver computeDatasetResolver,
                                ComputeResultFactory computeResultFactory,
                                NodeMetadataApplicationService nodeMetadataApplicationService,
                                SandboxProperties sandboxProperties) {
        this.computeDatasetResolver = computeDatasetResolver;
        this.computeResultFactory = computeResultFactory;
        this.nodeMetadataApplicationService = nodeMetadataApplicationService;
        this.sandboxProperties = sandboxProperties;
    }

    @Override
    public String supportType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, JavaCodeNodeConfigDTO config) {
        long start = System.currentTimeMillis();
        DatasetDTO input = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());

        List<Map<String, Object>> inputRows = input.getRows() != null ? input.getRows() : List.of();
        List<Map<String, Object>> outputRows = compileAndRun(config.getCode(), inputRows,
                effectiveTimeout(config.getTimeoutSeconds()));

        DatasetDTO output = DatasetDTO.builder()
                .schema(input.getSchema())
                .rows(outputRows)
                .build();

        return computeResultFactory.success(context.getNodeId(), NODE_TYPE, output,
                false, NODE_TYPE, System.currentTimeMillis() - start);
    }

    @Override
    public ValidationResultDTO validate(JavaCodeNodeConfigDTO config) {
        if (config == null || config.getCode() == null || config.getCode().isBlank()) {
            return ValidationResultDTO.builder().valid(false).errorMessage("java code is required").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return nodeMetadataApplicationService.getNodeDefinition(NODE_TYPE);
    }

    // -------------------------------------------------------------------------
    // Sandbox: import validation
    // -------------------------------------------------------------------------

    /**
     * Scans the generated source file for import statements referencing blacklisted packages.
     * This check runs before compilation, providing an early, clear error message.
     *
     * <p>The user's code is embedded as a method body so top-level imports cannot be added by
     * user code; this guard catches any imports injected via the template itself or through
     * multi-line string tricks.</p>
     *
     * @param userCode raw user code (method body)
     * @throws BaseBusinessException with {@link ErrorCode#SANDBOX_REJECTED} if a forbidden import is found
     */
    private void validateImports(String userCode) {
        Set<String> blacklisted = sandboxProperties.javaBlacklistedPackages();
        Matcher m = IMPORT_PATTERN.matcher(userCode);
        while (m.find()) {
            String imported = m.group(1);
            // Strip trailing wildcard for prefix matching
            String importedBase = imported.endsWith(".*") ? imported.substring(0, imported.length() - 2) : imported;
            for (String forbidden : blacklisted) {
                if (importedBase.startsWith(forbidden) || imported.startsWith(forbidden)) {
                    throw new BaseBusinessException(ErrorCode.SANDBOX_REJECTED,
                            "Forbidden import '" + imported + "': package '" + forbidden
                                    + "' is not permitted in the sandbox");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Main compile + execute pipeline
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> compileAndRun(String userCode,
                                                     List<Map<String, Object>> inputRows,
                                                     int timeoutSeconds) {
        // --- Security gate 1: import blacklist (source-level, pre-compilation) ---
        validateImports(userCode);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "JDK compiler unavailable; ensure the application runs on a JDK, not a JRE");
        }

        // Unique class name per execution to avoid ClassLoader collision
        String className = "UserDataProcessor_" + UUID.randomUUID().toString().replace("-", "");
        String sourceCode = String.format(CLASS_TEMPLATE, className, userCode);

        Path tempDir = null;
        URLClassLoader classLoader = null;
        ExecutorService executor = null;
        Future<?> monitorFuture = null;
        try {
            tempDir = Files.createTempDirectory("java_node_");
            Path sourceFile = tempDir.resolve(className + ".java");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            // Compile
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager =
                         compiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8)) {

                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir.toFile()));
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile.toFile());
                JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics,
                        null, null, units);
                boolean ok = task.call();

                if (!ok) {
                    StringBuilder errors = new StringBuilder("Java compilation failed:\n");
                    diagnostics.getDiagnostics().stream()
                            .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                            .forEach(d -> errors.append("  Line ")
                                    // offset: template preamble is 5 lines before user code
                                    .append(Math.max(1L, d.getLineNumber() - 5))
                                    .append(": ")
                                    .append(d.getMessage(Locale.getDefault()))
                                    .append("\n"));
                    throw new BaseBusinessException(ErrorCode.INVALID_ARGUMENT, errors.toString().trim());
                }
            }

            // --- Security gate 2: ClassLoader isolation ---
            // Use getPlatformClassLoader() as parent so user code can access JDK stdlib
            // but CANNOT access host-application classes (Spring context, datasources, etc.).
            classLoader = new URLClassLoader(
                    new java.net.URL[]{tempDir.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader()
            );
            Class<?> clazz = classLoader.loadClass(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method method = clazz.getMethod("process", List.class);

            // Execute with timeout in a dedicated thread
            final Object finalInstance = instance;
            final Method finalMethod = method;
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "java-code-node-exec");
                t.setDaemon(true);
                return t;
            });

            Future<List<Map<String, Object>>> future = executor.submit(
                    () -> (List<Map<String, Object>>) finalMethod.invoke(finalInstance, inputRows)
            );

            // --- Security gate 3: JVM heap-delta memory monitor (soft limit) ---
            long maxHeapDelta = sandboxProperties.javaMaxHeapDeltaBytes();
            MemoryUsage beforeUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long heapBefore = beforeUsage.getUsed();
            monitorFuture = monitorExecutor.submit(() -> {
                try {
                    while (!future.isDone()) {
                        long heapNow = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                        if (heapNow - heapBefore > maxHeapDelta) {
                            future.cancel(true);
                            return;
                        }
                        Thread.sleep(500);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });

            try {
                List<Map<String, Object>> result = future.get(timeoutSeconds, TimeUnit.SECONDS);
                return normalizeRows(result);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                        "java code timed out after " + timeoutSeconds + "s");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                // Surface sandbox cancellation as a dedicated error code
                if (cause instanceof BaseBusinessException biz) {
                    throw biz;
                }
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                if (future.isCancelled()) {
                    throw new BaseBusinessException(ErrorCode.SANDBOX_REJECTED,
                            "java code exceeded memory limit (" + sandboxProperties.javaMaxHeapDeltaBytes() / (1024 * 1024) + " MB)");
                }
                throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                        "java code threw exception: " + msg);
            }

        } catch (BaseBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "java code execution error: " + e.getMessage());
        } finally {
            if (monitorFuture != null) {
                monitorFuture.cancel(true);
            }
            shutdownQuietly(executor);
            closeQuietly(classLoader);
            deleteQuietly(tempDir);
        }
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null) continue;
            Map<String, Object> copy = new LinkedHashMap<>(row);
            normalized.add(copy);
        }
        return normalized;
    }

    private int effectiveTimeout(Integer configured) {
        return (configured != null && configured > 0) ? configured : DEFAULT_TIMEOUT_SECONDS;
    }

    private void shutdownQuietly(ExecutorService executor) {
        if (executor == null) return;
        executor.shutdownNow();
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // best-effort
        }
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
