package com.aprism.conformance.probe;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.AprismRuntime;

/**
 * Lifecycle contract probe (v26.9-Alpha.1): initialize binds version
 * metadata and the two-phase load over empty directories completes with
 * zero mods and zero extensions.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LifecycleProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("lifecycle",
                "initialize + two-phase load", "unit", CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run");
        try {
            Instrumentation inst = (Instrumentation) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {Instrumentation.class},
                    (proxy, method, args) -> {
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        if (rt == int.class) {
                            return 0;
                        }
                        if (rt == long.class) {
                            return 0L;
                        }
                        return null;
                    });
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(inst, "conformance", "JE", "conformance-target");
            boolean versionBound = "conformance".equals(runtime.getAprismVersion())
                    && "conformance-target".equals(runtime.getMcVersion());
            Path empty = Files.createTempDirectory("aprism-conf-lifecycle");
            runtime.performLoad(empty, empty.resolve("aprism-extensions"));
            boolean emptyLoad = runtime.getMods().isEmpty()
                    && runtime.getLoadedExtensions().isEmpty();
            runtime.shutdown();
            boolean pass = versionBound && emptyLoad;
            return new ProbeResult(cell, pass,
                    "versionBound=" + versionBound + " emptyLoad=" + emptyLoad);
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
