package com.kuaishou.intelligentanalysisplatform.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sandbox configuration for user-supplied code execution nodes (JavaCode, PythonScript).
 *
 * <p>All values can be overridden via application.yml under the {@code sandbox.*} prefix.</p>
 */
@Component
public class SandboxProperties {

    // -------------------------------------------------------------------------
    // Java sandbox
    // -------------------------------------------------------------------------

    @Value("${sandbox.java.max-memory-mb:256}")
    private int javaMaxMemoryMb;

    @Value("${sandbox.java.max-timeout-seconds:30}")
    private int javaMaxTimeoutSeconds;

    /**
     * Comma-separated list of package prefixes forbidden in user Java code imports.
     * e.g. {@code java.io,java.net,java.nio.file,java.lang.reflect}
     */
    @Value("${sandbox.java.blacklisted-imports:java.io,java.net,java.nio.file,java.lang.reflect,sun.,com.sun.,java.lang.Runtime,java.lang.Process,java.lang.ProcessBuilder}")
    private String javaBlacklistedImportsRaw;

    // -------------------------------------------------------------------------
    // Python sandbox
    // -------------------------------------------------------------------------

    /** Execution mode: {@code restricted} (built-in import hook), {@code firejail}, or {@code sidecar}. */
    @Value("${sandbox.python.mode:restricted}")
    private String pythonMode;

    @Value("${sandbox.python.max-memory-mb:256}")
    private int pythonMaxMemoryMb;

    @Value("${sandbox.python.max-timeout-seconds:30}")
    private int pythonMaxTimeoutSeconds;

    @Value("${sandbox.python.python-bin:python3}")
    private String pythonBin;

    /** Comma-separated list of Python top-level module names to block. */
    @Value("${sandbox.python.blocked-modules:os,subprocess,socket,shutil,pathlib,tempfile,ctypes,multiprocessing,threading,concurrent,pty,signal,resource}")
    private String pythonBlockedModulesRaw;

    // -------------------------------------------------------------------------
    // Derived accessors
    // -------------------------------------------------------------------------

    public long javaMaxHeapDeltaBytes() {
        return (long) javaMaxMemoryMb * 1024 * 1024;
    }

    public int getJavaMaxTimeoutSeconds() {
        return javaMaxTimeoutSeconds;
    }

    public Set<String> javaBlacklistedPackages() {
        return Arrays.stream(javaBlacklistedImportsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String getPythonMode() {
        return pythonMode;
    }

    public int getPythonMaxTimeoutSeconds() {
        return pythonMaxTimeoutSeconds;
    }

    public String getPythonBin() {
        return pythonBin;
    }

    public List<String> pythonBlockedModules() {
        return Arrays.stream(pythonBlockedModulesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
