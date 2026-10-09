/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Uso de CPU e GPU do sistema, amostrados uma vez por segundo numa thread propria (ler os contadores do Windows leva
 * alguns milissegundos, que nao podem sair do quadro), e a memoria do sistema. A thread so roda enquanto algum
 * elemento pede os valores: 5 s sem pedido e ela para.
 */
final class Sensors {

    private static final long IDLE_NS = 5_000_000_000L;
    /** MEMORYSTATUSEX: dwLength, dwMemoryLoad, ullTotalPhys (8), ullAvailPhys (16) e mais cinco contadores. */
    private static final long MEMORY_STATUS_SIZE = 64;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile long wanted;
    private static volatile double cpu = -1;
    private static volatile double gpu = -1;
    private static MethodHandle memoryStatus;
    private static boolean noMemoryStatus;

    private Sensors() {
    }

    /** Uso do processador (todo o sistema), 0-100; -1 enquanto nao ha amostra ou sem os contadores do Windows. */
    static double cpu() {
        want();
        return cpu;
    }

    /** Uso da placa de video, 0-100; -1 enquanto nao ha amostra ou sem os contadores do Windows. */
    static double gpu() {
        want();
        return gpu;
    }

    /** Memoria do sistema em uso e total, em bytes (GlobalMemoryStatusEx do Windows); null sem a informacao. */
    static long[] systemMemory() {
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

    private static MethodHandle memoryStatus() {
        if (memoryStatus == null && !noMemoryStatus) {
            try {
                memoryStatus = Linker.nativeLinker().downcallHandle(
                        SymbolLookup.libraryLookup("kernel32.dll", Arena.global()).find("GlobalMemoryStatusEx").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS));
            } catch (Throwable t) {
                noMemoryStatus = true;
            }
        }
        return memoryStatus;
    }

    private static void want() {
        wanted = System.nanoTime();
        if (RUNNING.compareAndSet(false, true)) {
            Thread t = new Thread(Sensors::run, "Stein HUD: sensores");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
    }

    private static void run() {
        Pdh pdh = Pdh.open();
        try {
            while (pdh != null && System.nanoTime() - wanted < IDLE_NS) {
                try {
                    pdh.collect();
                    cpu = pdh.cpu();
                    gpu = pdh.gpu();
                } catch (Throwable t) {
                    System.out.println("[mods] HUD: contadores do Windows falharam (" + t + ")");
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
            RUNNING.set(false);
        }
    }
}
