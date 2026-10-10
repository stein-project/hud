/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Terrain;

import java.util.Arrays;
import java.util.Locale;

/**
 * The minimap's terrain in a repeating 1024x1024 texture, one pixel per block: block (x, z) lives
 * at pixel (x mod 1024, z mod 1024), so walking moves nothing. Only the chunks that come into range
 * are read, nearest first and a few at a time, and one already read is read again now and then, in
 * rotation, for the blocks that change. The pixels come from {@link MapColumns}; the texture is the
 * mod's own (Render.texture, repeating).
 *
 * <p>The pace is by time, not by frame: each column's color is expensive (the game blends the
 * neighbours' biome colors, with noise), and with a per-frame budget the cost grew with the FPS (at
 * 1800 FPS, ~1800 chunks re-read per second for a map that barely changes). Now at most
 * READS_PER_SECOND new reads and REFRESH_PER_SECOND re-reads per second, and never more than BUDGET
 * / one per frame: up to 60 FPS it is the same as before; above, the same as at 60 FPS.
 */
final class MapTexture {

    static final int SIZE = 1024;
    private static final int TILES = SIZE / 16;
    private static final int MASK = TILES - 1;

    /** Chunks read per frame, at most. */
    private static final int BUDGET = 6;

    /** The reference pace: 60 frames per second with the budget above. */
    private static final double READS_PER_SECOND = BUDGET * 60.0;

    private static final double REFRESH_PER_SECOND = 60.0;

    /**
     * {@code -Dsteinhud.minimap.cost=true}: every 5 s, the time spent by the map and the chunks
     * read.
     */
    private static final boolean COST = Boolean.getBoolean("steinhud.minimap.cost");

    private final Log log;
    private final MapColumns columns = new MapColumns();

    /** The chunk kept in each place of the texture, and whether it was read. */
    private final long[] keys = new long[TILES * TILES];

    private final boolean[] done = new boolean[TILES * TILES];
    private final int[] tile = new int[256];
    private final int[] blank = new int[256];
    private Render.Texture texture;
    private Object world;

    /** -1 surface; otherwise the feet's y in cave mode. */
    private int layer = Integer.MIN_VALUE;

    private boolean simple;
    private int refresh;

    /** New reads and re-reads available: buckets that fill with time, capped at one frame's. */
    private double reads = BUDGET;

    private double refreshes = 1;
    private long lastNanos;
    private long costAt;
    private long costNanos;
    private int costFrames;
    private int costReads;
    private int costRefreshes;

    MapTexture(Log log) {
        this.log = log;
    }

    Render.Texture texture() {
        ensure();
        return texture;
    }

    /** Everything will be read again (new colors). */
    void reset() {
        world = null;
    }

    /**
     * Reads what is missing around chunk (pcx, pcz), radius r in chunks, at level layer (-1
     * surface).
     */
    void update(Object w, int pcx, int pcz, int r, int layer, boolean simple) {
        long t0 = System.nanoTime();
        double sec = lastNanos == 0 ? 1.0 : Math.max(0, Math.min(1.0, (t0 - lastNanos) / 1e9));
        lastNanos = t0;
        reads = Math.min(BUDGET, reads + sec * READS_PER_SECOND);
        refreshes = Math.min(1, refreshes + sec * REFRESH_PER_SECOND);
        int read = scanAround(w, pcx, pcz, r, layer, simple);
        if (COST) {
            cost(t0, read);
        }
    }

    /**
     * Chunks read this frame: the new ones, plus 1000 if there was a re-read (the diag's count).
     */
    private int scanAround(Object w, int pcx, int pcz, int r, int layer, boolean simple) {
        ensure();
        if (w != world || layer != this.layer || simple != this.simple) {
            world = w;
            this.layer = layer;
            this.simple = simple;
            columns.mode(layer, simple);
            Arrays.fill(keys, Long.MIN_VALUE);
            Arrays.fill(done, false);
        }
        // A chunk that came into range: its place (which held another) stays empty until read.
        for (int cz = pcz - r; cz <= pcz + r; cz++) {
            for (int cx = pcx - r; cx <= pcx + r; cx++) {
                int i = index(cx, cz);
                long k = key(cx, cz);
                if (keys[i] != k) {
                    keys[i] = k;
                    done[i] = false;
                    put(cx, cz, blank);
                }
            }
        }
        // Nearest first; the re-read only when nothing that has arrived is left to read.
        int start = (int) reads;
        int budget = start;
        boolean pending = false;
        for (int d = 0; d <= r && !pending; d++) {
            int cells = d == 0 ? 1 : 8 * d;
            for (int k = 0; k < cells && !pending; k++) {
                int cx;
                int cz;
                if (d == 0) {
                    cx = pcx;
                    cz = pcz;
                } else {
                    int o = k % (2 * d);
                    switch (k / (2 * d)) {
                        case 0 -> {
                            cx = pcx - d + o;
                            cz = pcz - d;
                        }
                        case 1 -> {
                            cx = pcx + d;
                            cz = pcz - d + o;
                        }
                        case 2 -> {
                            cx = pcx + d - o;
                            cz = pcz + d;
                        }
                        default -> {
                            cx = pcx - d;
                            cz = pcz + d - o;
                        }
                    }
                }
                int i = index(cx, cz);
                if (done[i]) {
                    continue;
                }
                if (budget == 0) {
                    // No read available: pending only what has arrived (what has not does not hold
                    // the re-read back).
                    pending = Terrain.isLoaded(cx, cz);
                } else if (scan(cx, cz)) {
                    done[i] = true;
                    budget--;
                }
            }
        }
        int out = start - budget;
        reads -= out;
        // Re-read when the frame had budget left; with the bucket empty (high FPS), when nothing is
        // missing.
        if (!pending && (budget > 0 || start == 0) && refreshes >= 1) {
            refreshes -= 1;
            int n = 2 * r + 1;
            refresh = (refresh + 1) % (n * n);
            int cx = pcx - r + refresh % n;
            int cz = pcz - r + refresh / n;
            if (done[index(cx, cz)] && scan(cx, cz)) {
                out += 1000;
            }
        }
        return out;
    }

    private void cost(long t0, int read) {
        long now = System.nanoTime();
        costNanos += now - t0;
        costFrames++;
        costReads += read % 1000;
        costRefreshes += read / 1000;
        if (costAt == 0) {
            costAt = now;
        } else if (now - costAt >= 5_000_000_000L) {
            double s = (now - costAt) / 1e9;
            log.info(
                    String.format(
                            Locale.ROOT,
                            "minimap: %.2f ms/s on the map (%.1f us/frame, %d frames), %.0f new"
                                    + " chunks/s, %.0f re-read/s",
                            costNanos / 1e6 / s,
                            costNanos / 1e3 / Math.max(1, costFrames),
                            costFrames,
                            costReads / s,
                            costRefreshes / s));
            costAt = now;
            costNanos = 0;
            costFrames = 0;
            costReads = 0;
            costRefreshes = 0;
        }
    }

    private static int index(int cx, int cz) {
        return (cz & MASK) * TILES + (cx & MASK);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private boolean scan(int cx, int cz) {
        if (!columns.paint(cx, cz, tile)) {
            return false;
        }
        put(cx, cz, tile);
        return true;
    }

    private void put(int cx, int cz, int[] data) {
        texture.set((cx & MASK) * 16, (cz & MASK) * 16, 16, 16, data, 0);
    }

    private void ensure() {
        if (texture == null) {
            // Repeating: block (x, z) always lives at the same pixel.
            texture = Render.texture(SIZE, SIZE, true);
        }
    }
}
