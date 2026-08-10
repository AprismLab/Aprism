package com.aprism.loader.introspection;

import com.aprism.api.introspection.ClassStats;
import com.aprism.api.introspection.CompilationSummary;
import com.aprism.api.introspection.GcSummary;
import com.aprism.api.introspection.HeapSummary;
import com.aprism.api.introspection.JvmInsight;
import com.aprism.api.introspection.ThreadInsight;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link JvmInsight} over the standard
 * {@code ManagementFactory} MXBeans (v26.4-Alpha.4). Works on any
 * compliant JVM; on AprismJDK deeper seams may replace individual
 * methods later without changing this contract.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class JvmInsightImpl implements JvmInsight {

    /** Maximum number of stack frames captured per thread. */
    private static final int MAX_FRAMES = 16;

    @Override
    public List<ThreadInsight> threads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = threadBean.dumpAllThreads(false, false);
        List<ThreadInsight> result = new ArrayList<>(infos.length);
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
            }
            StackTraceElement[] stack = info.getStackTrace();
            List<String> frames = new ArrayList<>();
            int limit = Math.min(stack.length, MAX_FRAMES);
            for (int i = 0; i < limit; i++) {
                frames.add(stack[i].toString());
            }
            result.add(new ThreadInsight(info.getThreadId(), info.getThreadName(),
                    info.getThreadState().name(), stack.length, frames));
        }
        return result;
    }

    @Override
    public ClassStats classStats() {
        ClassLoadingMXBean bean = ManagementFactory.getClassLoadingMXBean();
        return new ClassStats(bean.getLoadedClassCount(), bean.getUnloadedClassCount(),
                bean.getTotalLoadedClassCount());
    }

    @Override
    public HeapSummary heap() {
        MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = bean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = bean.getNonHeapMemoryUsage();
        return new HeapSummary(heapUsage.getUsed(), heapUsage.getCommitted(),
                heapUsage.getMax(), nonHeapUsage.getUsed());
    }

    @Override
    public List<GcSummary> gcCollectors() {
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        List<GcSummary> result = new ArrayList<>(beans.size());
        for (GarbageCollectorMXBean bean : beans) {
            result.add(new GcSummary(bean.getName(), bean.getCollectionCount(),
                    bean.getCollectionTime()));
        }
        return result;
    }

    @Override
    public CompilationSummary compilation() {
        CompilationMXBean bean = ManagementFactory.getCompilationMXBean();
        if (bean == null) {
            return new CompilationSummary(null, -1, false);
        }
        long time = bean.isCompilationTimeMonitoringSupported()
                ? bean.getTotalCompilationTime() : -1;
        return new CompilationSummary(bean.getName(), time, true);
    }

    @Override
    public long uptimeMs() {
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        return bean.getUptime();
    }

    @Override
    public String vmName() {
        return ManagementFactory.getRuntimeMXBean().getVmName();
    }

    @Override
    public String vmVendor() {
        return ManagementFactory.getRuntimeMXBean().getVmVendor();
    }

    @Override
    public String javaVersion() {
        return System.getProperty("java.version");
    }
}
