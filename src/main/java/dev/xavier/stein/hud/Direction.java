/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;

import java.util.List;

/**
 * A compass (what DirectionHUD did): a strip with the cardinal points that runs with the gaze, a
 * colored marker at the centre and styles (DirectionHUD swapped the compass image; here the strip
 * is drawn, with no texture).
 */
final class Direction extends HudElement {

    private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /**
     * The styles, in the option's order; 1 (the strip without background) went away when the
     * background became the grid's.
     */
    private static final String[] STYLES = {
        "direction.strip", "direction.text", "direction.degrees"
    };

    private static final int[] STYLE_VALUES = {1, 2, 3};

    /** Strip: 180 degrees in 121 px (an exact centre), letters on top, ticks below. */
    private static final int STRIP_W = 121;

    private static final int STRIP_H = 20;

    /** "0°" to "360°", made once: the degrees under the strip changed every frame. */
    private static final String[] DEGREES = new String[361];

    private final HudConfig.Compass c;
    private float bearing;
    private String text;

    // The text style's line and the strip's label widths, kept with what they were built from.
    private int textWidth;
    private int textDegrees = -1;
    private int textLabel = -1;
    private String textColor;
    private int textBuiltEpoch = -1;
    private final int[] labelWidths = new int[LABELS.length];
    private int labelEpoch = -1;

    Direction(HudConfig.Compass c, SteinHud mod) {
        super("direction", c, mod);
        this.c = c;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        Object p = Entities.self();
        float yaw;
        if (p != null) {
            yaw = Entities.yaw(p, partialTicks);
        } else if (preview) {
            yaw = (System.currentTimeMillis() % 36_000L) / 100.0F; // turns slowly in the editor
        } else {
            return false;
        }
        // The game's yaw: 0 = south, 90 = west. The compass bearing: 0 = north, 90 = east.
        bearing = ((yaw + 180.0F) % 360.0F + 360.0F) % 360.0F;
        if (c.style == 2) {
            int degrees = (int) bearing;
            int label = Math.round(bearing / 45.0F) % 8;
            int epoch = textEpoch();
            if (degrees != textDegrees
                    || label != textLabel
                    || c.markerColor != textColor
                    || epoch != textBuiltEpoch) {
                text = Option.fmt(c.markerColor) + LABELS[label] + "§f " + degrees(degrees);
                textWidth = Render.textWidth(text);
                textDegrees = degrees;
                textLabel = label;
                textColor = c.markerColor;
                textBuiltEpoch = epoch;
            }
            w = textWidth + 6;
            h = 12;
        } else {
            w = STRIP_W;
            h = c.style == 3 ? STRIP_H + 11 : STRIP_H;
        }
        return true;
    }

    @Override
    void draw(boolean alignRight) {
        int marker = 0xFF000000 | Option.rgb(c.markerColor);
        if (c.style == 2) {
            Render.text(text, 3, 2, 0xFFFFFF, true);
            return;
        }
        int cx = STRIP_W / 2;
        float pxPerDegree = (STRIP_W / 2.0F) / 90.0F;
        for (int a = 0; a < 360; a += 15) {
            float off = wrap180(a - bearing);
            if (Math.abs(off) > 90.0F) {
                continue;
            }
            int px = cx + Math.round(off * pxPerDegree);
            boolean major = a % 45 == 0;
            Render.rect(
                    px,
                    major ? STRIP_H - 6 : STRIP_H - 3,
                    1,
                    major ? 5 : 2,
                    major ? 0xFFFFFFFF : 0x90FFFFFF);
            if (major) {
                String label = LABELS[a / 45];
                int lw = labelWidth(a / 45);
                int lx = px - lw / 2;
                if (lx >= 1 && lx + lw <= STRIP_W - 1) {
                    Render.text(label, lx, 2, a % 90 == 0 ? 0xFFFFFF : 0xAAAAAA, true);
                }
            }
        }
        // The marker: a line across the ticks and a tip below.
        Render.rect(cx, STRIP_H - 9, 1, 9, marker);
        Render.rect(cx - 1, STRIP_H - 2, 3, 2, marker);
        if (c.style == 3) {
            String d = degrees((int) bearing);
            Render.text(d, cx - Render.textWidth(d) / 2, STRIP_H + 2, 0xFFFFFF, true);
        }
    }

    /** "N°", for 0 to 360. */
    private static String degrees(int d) {
        if (d < 0 || d >= DEGREES.length) {
            return d + "°";
        }
        String s = DEGREES[d];
        if (s == null) {
            s = d + "°";
            DEGREES[d] = s;
        }
        return s;
    }

    /** The width of a cardinal label, measured again when the font may have changed. */
    private int labelWidth(int i) {
        int epoch = textEpoch();
        if (epoch != labelEpoch) {
            for (int j = 0; j < LABELS.length; j++) {
                labelWidths[j] = Render.textWidth(LABELS[j]);
            }
            labelEpoch = epoch;
        }
        return labelWidths[i];
    }

    /** The angle between -180 and 180 (the game's wrapAngleTo180). */
    private static float wrap180(float a) {
        float r = a % 360.0F;
        if (r >= 180.0F) {
            r -= 360.0F;
        }
        if (r < -180.0F) {
            r += 360.0F;
        }
        return r;
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(
                Option.cycle(
                        T.tr("style"),
                        T.all(STYLES),
                        () -> Math.max(0, c.style - 1),
                        i -> c.style = STYLE_VALUES[i]));
        out.add(
                Option.color(
                        T.tr("direction.marker"), () -> c.markerColor, v -> c.markerColor = v));
    }
}
