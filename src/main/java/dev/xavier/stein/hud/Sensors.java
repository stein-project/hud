/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import dev.xavier.stein.loader.api.Log;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * System CPU and GPU usage, sampled once per second on a thread of its own (reading the Windows
 * counters takes a few milliseconds, which must not come out of the frame), and the system's
 * memory. The thread only runs while an element asks for the values: 5 s without a request and it
 * stops.
 */
final class Sensors {

    private static final long IDLE_NS = 5_000_000_000L;

    /**
     * MEMORYSTATUSEX: dwLength, dwMemoryLoad, ullTotalPhys (8), ullAvailPhys (16) and five more.
     */
    private static final long MEMORY_STATUS_SIZE = 64;

    private final Log log;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long wanted;
    private volatile double cpu = -1;
    private volatile double gpu = -1;
    private MethodHandle memoryStatus;
    private boolean noMemoryStatus;

    Sensors(Log log) {
        this.log = log;
    }

    /**
     * Processor usage (the whole system), 0-100; -1 until a sample or without the Windows counters.
     */
    double cpu() {
        want();
        return cpu;
    }

    /** Graphics card usage, 0-100; -1 until a sample or without the Windows counters. */
    double gpu() {
        want();
        return gpu;
    }

    /**
     * System memory in use and total, in bytes (Windows' GlobalMemoryStatusEx); null without it.
     */
    long[] systemMemory() {
        MethodHandle h = memoryStatus();
        if (h == null) {
            return null;
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = a.allocate(MEMORY_STATUS_SIZE, 8);
            s.set(JAVA_INT, 0, (int) MEMORY_STATUS_SIZE);
            if ((int) h.invokeExact(s) == 0) {
                return null;
            }
            long total = s.get(JAVA_LONG, 8);
            return total > 0 ? new long[] {total - s.get(JAVA_LONG, 16), total} : null;
        } catch (Throwable t) {
            noMemoryStatus = true;
            return null;
        }
    }

    private MethodHandle memoryStatus() {
        if (memoryStatus == null && !noMemoryStatus) {
            try {
                memoryStatus =
                        Linker.nativeLinker()
                                .downcallHandle(
                                        SymbolLookup.libraryLookup("kernel32.dll", Arena.global())
                                                .find("GlobalMemoryStatusEx")
                                                .orElseThrow(),
                                        FunctionDescriptor.of(JAVA_INT, ADDRESS));
            } catch (Throwable t) {
                noMemoryStatus = true;
            }
        }
        return memoryStatus;
    }

    private void want() {
        wanted = System.nanoTime();
        if (running.compareAndSet(false, true)) {
            Thread t = new Thread(this::run, "Stein HUD: sensors");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
    }

    private void run() {
        Pdh pdh = Pdh.open(log);
        try {
            while (pdh != null && System.nanoTime() - wanted < IDLE_NS) {
                try {
                    pdh.collect();
                    cpu = pdh.cpu();
                    gpu = pdh.gpu();
                } catch (Throwable t) {
                    log.warn("Windows counters failed (" + t + ")");
                    break;
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (pdh != null) {
                pdh.close();
            }
            cpu = -1;
            gpu = -1;
            running.set(false);
        }
    }
}
