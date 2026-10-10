/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Terrain;

/**
 * The minimap's pixels, one per block column: which block to paint (on the surface the highest; in
 * cave mode the floor of the player's level) and its color, with relief from the north neighbour
 * like the game's map, and shallow water showing the bottom. Blocks and colors come from the Loader
 * ({@link Terrain}).
 */
final class MapColumns {

    static final int NONE_Y = Integer.MIN_VALUE;
    static final int WALL_Y = Integer.MIN_VALUE + 1;
    static final int WALL = 0xFF181818;
    static final int VOID = 0xFF050505;

    private final int[] north = new int[16];

    /** -1 surface; otherwise the feet's y in cave mode. */
    private int layer;

    /** Simple mode: the game's map color and stronger relief (less detail, outlines show). */
    private boolean simple;

    void mode(int layer, boolean simple) {
        this.layer = layer;
        this.simple = simple;
    }

    /** Paints chunk (cx, cz) into tile (16x16, row by row); false when it is not loaded. */
    boolean paint(int cx, int cz, int[] tile) {
        if (!Terrain.isLoaded(cx, cz)) {
            return false;
        }
        boolean hasNorth = Terrain.isLoaded(cx, cz - 1);
        for (int lx = 0; lx < 16; lx++) {
            north[lx] = hasNorth ? top((cx << 4) + lx, (cz << 4) - 1) : NONE_Y;
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (cx << 4) + lx;
                int z = (cz << 4) + lz;
                int y = top(x, z);
                int argb;
                if (y == WALL_Y) {
                    argb = WALL;
                } else if (y == NONE_Y) {
                    argb = VOID;
                } else {
                    argb = color(x, y, z);
                    int n = north[lx];
                    if (n != NONE_Y && n != WALL_Y && n != y) {
                        argb =
                                scale(
                                        argb,
                                        y > n ? (simple ? 1.3F : 1.1F) : (simple ? 0.72F : 0.86F));
                    }
                }
                north[lx] = y;
                tile[(lz << 4) + lx] = argb;
            }
        }
        return true;
    }

    /** The y of the block to paint in column (x, z), or a wall (WALL_Y) / nothing (NONE_Y). */
    private int top(int x, int z) {
        if (layer < 0) {
            int h = Terrain.height(x, z);
            if (h <= 0) {
                return NONE_Y;
            }
            // The height map goes through flowers, thin snow and glass: what lies just above the
            // ground counts.
            return Terrain.isAir(x, h, z) ? h - 1 : h;
        }
        if (Terrain.isSolid(x, layer + 1, z)) {
            return WALL_Y;
        }
        for (int y = layer; y >= Math.max(0, layer - 24); y--) {
            if (!Terrain.isAir(x, y, z)) {
                return y;
            }
        }
        return NONE_Y;
    }

    private int color(int x, int y, int z) {
        int c = simple ? Terrain.mapColor(x, y, z) : Terrain.color(x, y, z);
        if (Terrain.isWater(x, y, z)) {
            // Water: the bottom shows in the shallows, and the deep gets darker.
            int d = 1;
            while (d < 12 && Terrain.isWater(x, y - d, z)) {
                d++;
            }
            int fy = d < 12 ? y - d : y - 11; // bottom beyond 12: the water's own color stays
            int floor = simple ? Terrain.mapColor(x, fy, z) : Terrain.color(x, fy, z);
            c = mix(c, floor, d <= 1 ? 0.5F : d <= 3 ? 0.3F : d <= 6 ? 0.15F : 0.05F);
            c = scale(c, 1.0F - Math.min(d, 10) * 0.025F);
        }
        if (layer >= 0) {
            // Caves: deeper, darker.
            c = scale(c, Math.max(0.35F, 1.0F - (layer - y) * 0.06F));
        }
        return c;
    }

    static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** a with a fraction f of b. */
    static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
