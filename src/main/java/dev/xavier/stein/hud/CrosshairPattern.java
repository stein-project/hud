/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

/**
 * The crosshair's painted cells: a square grid of odd side, so one cell is the exact middle. Each
 * row is a bit mask (bit x = column x), which fits up to 31 columns in an int.
 *
 * <p>Text form ({@link #encode}): the side, a colon and the rows in hexadecimal separated by
 * commas, top to bottom: {@code "7:0,8,8,77,8,8,0"}. It is what the configuration and the favorites
 * keep.
 */
final class CrosshairPattern {

    static final int MIN_SIZE = 7;
    static final int MAX_SIZE = 31;
    static final int DEFAULT_SIZE = 15;
    static final int MAX_FAVORITES = 12;

    /** The largest "screen pixels per cell" of the scale option. */
    static final int MAX_SCALE = 8;

    /** The built-in patterns, in the editor's order. */
    static final int DOT = 0;

    static final int CROSS = 1;
    static final int GAP = 2;
    static final int CIRCLE = 3;
    static final String[] PRESET_KEYS = {
        "crosshair.preset.dot",
        "crosshair.preset.cross",
        "crosshair.preset.gap",
        "crosshair.preset.circle"
    };

    private int size;
    private int[] rows;

    CrosshairPattern(int size) {
        this.size = clampSize(size);
        this.rows = new int[this.size];
    }

    int size() {
        return size;
    }

    int middle() {
        return size / 2;
    }

    boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && (rows[y] >>> x & 1) != 0;
    }

    void set(int x, int y, boolean on) {
        if (x < 0 || y < 0 || x >= size || y >= size) {
            return;
        }
        rows[y] = on ? rows[y] | 1 << x : rows[y] & ~(1 << x);
    }

    /** Paints or erases (x, y) and its mirror images. */
    void paint(int x, int y, boolean on, boolean mirrorX, boolean mirrorY) {
        int mx = size - 1 - x;
        int my = size - 1 - y;
        set(x, y, on);
        if (mirrorX) {
            set(mx, y, on);
        }
        if (mirrorY) {
            set(x, my, on);
        }
        if (mirrorX && mirrorY) {
            set(mx, my, on);
        }
    }

    boolean isEmpty() {
        for (int r : rows) {
            if (r != 0) {
                return false;
            }
        }
        return true;
    }

    void clear() {
        rows = new int[size];
    }

    /** Another side, keeping the drawing around the middle (what falls outside is lost). */
    void resize(int newSize) {
        int n = clampSize(newSize);
        int shift = (n - size) / 2;
        int[] out = new int[n];
        for (int y = 0; y < size; y++) {
            int ty = y + shift;
            if (ty < 0 || ty >= n) {
                continue;
            }
            for (int x = 0; x < size; x++) {
                int tx = x + shift;
                if (tx >= 0 && tx < n && get(x, y)) {
                    out[ty] |= 1 << tx;
                }
            }
        }
        size = n;
        rows = out;
    }

    CrosshairPattern copy() {
        CrosshairPattern p = new CrosshairPattern(size);
        System.arraycopy(rows, 0, p.rows, 0, size);
        return p;
    }

    String encode() {
        StringBuilder sb = new StringBuilder(size * 3 + 4).append(size).append(':');
        for (int y = 0; y < size; y++) {
            if (y > 0) {
                sb.append(',');
            }
            sb.append(Integer.toHexString(rows[y]));
        }
        return sb.toString();
    }

    /** Whether {@code code} is a pattern {@link #decode} reads as written. */
    static boolean valid(String code) {
        return parse(code) != null;
    }

    /** The pattern of {@code code}; a code that cannot be read gives the default cross. */
    static CrosshairPattern decode(String code) {
        CrosshairPattern p = parse(code);
        return p != null ? p : preset(CROSS, DEFAULT_SIZE);
    }

    private static CrosshairPattern parse(String code) {
        if (code == null) {
            return null;
        }
        int colon = code.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            int n = Integer.parseInt(code.substring(0, colon).trim());
            String[] parts = code.substring(colon + 1).split(",", -1);
            if (n != clampSize(n) || parts.length != n) {
                return null;
            }
            CrosshairPattern p = new CrosshairPattern(n);
            int mask = (1 << n) - 1;
            for (int y = 0; y < n; y++) {
                p.rows[y] = (int) Long.parseLong(parts[y].trim(), 16) & mask;
            }
            return p;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A built-in pattern on a grid of {@code size}: the arms shrink to fit a small grid.
     *
     * @param kind {@link #DOT}, {@link #CROSS}, {@link #GAP} or {@link #CIRCLE}
     */
    static CrosshairPattern preset(int kind, int size) {
        CrosshairPattern p = new CrosshairPattern(size);
        int c = p.middle();
        switch (kind) {
            case DOT -> p.set(c, c, true);
            case CROSS -> {
                // The game's look: a plus nine cells across, one thick.
                int arm = Math.min(4, c);
                for (int i = -arm; i <= arm; i++) {
                    p.set(c + i, c, true);
                    p.set(c, c + i, true);
                }
            }
            case GAP -> {
                int arm = Math.min(5, c);
                for (int i = 2; i <= arm; i++) {
                    p.paint(c + i, c, true, true, false);
                    p.paint(c, c + i, true, false, true);
                }
                p.set(c, c, true);
            }
            default -> {
                // A ring with a dot in the middle.
                double r = Math.min(4, c);
                for (int y = 0; y < p.size; y++) {
                    for (int x = 0; x < p.size; x++) {
                        double d = Math.hypot(x - c, y - c);
                        if (Math.abs(d - r) < 0.5) {
                            p.set(x, y, true);
                        }
                    }
                }
                p.set(c, c, true);
            }
        }
        return p;
    }

    /** An odd side between {@link #MIN_SIZE} and {@link #MAX_SIZE}. */
    static int clampSize(int n) {
        int s = Math.max(MIN_SIZE, Math.min(MAX_SIZE, n));
        return (s & 1) == 0 ? s - 1 : s;
    }
}
