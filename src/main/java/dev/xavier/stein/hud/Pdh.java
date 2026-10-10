/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import dev.xavier.stein.loader.api.Log;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Windows performance counters (pdh.dll), the Task Manager's: processor usage (% Processor Utility)
 * and graphics card usage (GPU Engine). Through Java 25's foreign function API; only on the thread
 * that opened it (the sensors'). English names (PdhAddEnglishCounter): they work on Windows in any
 * language.
 */
final class Pdh implements AutoCloseable {

    private static final int FMT_DOUBLE = 0x00000200;
    private static final int FMT_NOCAP100 = 0x00008000;
    private static final int MORE_DATA = 0x800007D2;

    /** PDH_FMT_COUNTERVALUE: status (4 bytes, plus 4 of alignment) and the value (8). */
    private static final long VALUE_SIZE = 16;

    /** PDH_FMT_COUNTERVALUE_ITEM_W: a pointer to the name, and the value. */
    private static final long ITEM_SIZE = 8 + VALUE_SIZE;

    private final Arena arena = Arena.ofConfined();
    private final MethodHandle collect;
    private final MethodHandle value;
    private final MethodHandle array;
    private final MethodHandle closeQuery;
    private final MemorySegment query;
    private final MemorySegment cpu;
    private final MemorySegment gpu;

    private Pdh() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = SymbolLookup.libraryLookup("pdh.dll", arena);
        MethodHandle open =
                linker.downcallHandle(
                        lib.find("PdhOpenQueryW").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
        MethodHandle add =
                linker.downcallHandle(
                        lib.find("PdhAddEnglishCounterW").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        collect =
                linker.downcallHandle(
                        lib.find("PdhCollectQueryData").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS));
        value =
                linker.downcallHandle(
                        lib.find("PdhGetFormattedCounterValue").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
        array =
                linker.downcallHandle(
                        lib.find("PdhGetFormattedCounterArrayW").orElseThrow(),
                        FunctionDescriptor.of(
                                JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        closeQuery =
                linker.downcallHandle(
                        lib.find("PdhCloseQuery").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS));

        MemorySegment out = arena.allocate(ADDRESS);
        int st = (int) open.invokeExact(MemorySegment.NULL, 0L, out);
        if (st != 0) {
            throw new IllegalStateException("PdhOpenQuery: 0x" + Integer.toHexString(st));
        }
        query = out.get(ADDRESS, 0);
        MemorySegment c = counter(add, "\\Processor Information(_Total)\\% Processor Utility");
        cpu = c != null ? c : counter(add, "\\Processor(_Total)\\% Processor Time");
        gpu = counter(add, "\\GPU Engine(*)\\Utilization Percentage");
    }

    /** Open, or null outside Windows or without the counters. */
    static Pdh open(Log log) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return null;
        }
        try {
            return new Pdh();
        } catch (Throwable t) {
            log.warn("no Windows counters (" + t + "); CPU and GPU unavailable");
            return null;
        }
    }

    private MemorySegment counter(MethodHandle add, String path) throws Throwable {
        MemorySegment out = arena.allocate(ADDRESS);
        int st =
                (int)
                        add.invokeExact(
                                query,
                                arena.allocateFrom(path, StandardCharsets.UTF_16LE),
                                0L,
                                out);
        return st == 0 ? out.get(ADDRESS, 0) : null;
    }

    /**
     * A new sample; the values are the average since the previous one (the first comes on the
     * second).
     */
    void collect() throws Throwable {
        int st = (int) collect.invokeExact(query);
    }

    /** Processor usage, 0-100; -1 without data. */
    double cpu() throws Throwable {
        if (cpu == null) {
            return -1;
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment v = a.allocate(VALUE_SIZE, 8);
            int st = (int) value.invokeExact(cpu, FMT_DOUBLE | FMT_NOCAP100, MemorySegment.NULL, v);
            if (st != 0 || !valid(v.get(JAVA_INT, 0))) {
                return -1;
            }
            return clamp(v.get(JAVA_DOUBLE, 8));
        }
    }

    /**
     * Graphics card usage, 0-100; -1 without data. Like the Task Manager: adds up every process's
     * usage on each engine of the card (3D, copy, video...) and keeps the busiest.
     */
    double gpu() throws Throwable {
        if (gpu == null) {
            return -1;
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment size = a.allocate(JAVA_INT);
            MemorySegment count = a.allocate(JAVA_INT);
            int st =
                    (int)
                            array.invokeExact(
                                    gpu,
                                    FMT_DOUBLE | FMT_NOCAP100,
                                    size,
                                    count,
                                    MemorySegment.NULL);
            if (st != MORE_DATA) {
                return -1;
            }
            MemorySegment items = a.allocate(Math.max(ITEM_SIZE, size.get(JAVA_INT, 0)), 8);
            st = (int) array.invokeExact(gpu, FMT_DOUBLE | FMT_NOCAP100, size, count, items);
            if (st != 0) {
                return -1;
            }
            // Instance: "pid_1234_luid_0x..._0x..._phys_0_eng_3_engtype_3D"; the engine is what
            // comes from "luid_".
            Map<String, Double> engines = new HashMap<>();
            int n = count.get(JAVA_INT, 0);
            for (int i = 0; i < n; i++) {
                long off = i * ITEM_SIZE;
                if (!valid(items.get(JAVA_INT, off + 8))) {
                    continue;
                }
                String name =
                        items.get(ADDRESS, off)
                                .reinterpret(Long.MAX_VALUE)
                                .getString(0, StandardCharsets.UTF_16LE);
                int luid = name.indexOf("luid_");
                engines.merge(
                        luid >= 0 ? name.substring(luid) : name,
                        items.get(JAVA_DOUBLE, off + 16),
                        Double::sum);
            }
            double max = engines.isEmpty() ? -1 : 0;
            for (double v : engines.values()) {
                max = Math.max(max, v);
            }
            return max < 0 ? -1 : clamp(max);
        }
    }

    /** PDH_CSTATUS_VALID_DATA or PDH_CSTATUS_NEW_DATA. */
    private static boolean valid(int status) {
        return status == 0 || status == 1;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    @Override
    public void close() {
        try {
            int st = (int) closeQuery.invokeExact(query);
        } catch (Throwable ignored) {
            // the query dies with the process anyway
        }
        arena.close();
    }
}
