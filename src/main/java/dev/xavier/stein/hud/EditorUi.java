/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Theme;
import dev.xavier.stein.loader.api.Typeface;

import java.util.Locale;

/**
 * The pieces of the HUD's own screens, in the Stein design system and drawn only through the API:
 * labels, pill buttons and sliders, with the theme's colors and fonts. Coordinates in GUI units.
 */
final class EditorUi {

    /** The uppercase labels above each group. */
    static final float LABEL_SIZE = 6.0F;

    /** The text on buttons. */
    static final float TEXT_SIZE = 7.5F;

    static final float TITLE_SIZE = 12.0F;

    /** The surface of a button at rest (the ink at 8%). */
    private static final int SURFACE = 0x14E8E1F5;

    private static final int GOLD_FAINT = 0x1FB89CD9;

    private EditorUi() {}

    static boolean inside(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    static void title(String s, float x, float y) {
        Render.text(s, x, y, Theme.INK, Typeface.TITLE, TITLE_SIZE);
    }

    /** An uppercase label, in the muted ink. */
    static void label(String s, float x, float y) {
        Render.text(s.toUpperCase(Locale.ROOT), x, y, Theme.MUTED, Typeface.MONO, LABEL_SIZE);
    }

    /** A hint in the faint ink. */
    static void hint(String s, float x, float y) {
        Render.text(s, x, y, Theme.FAINT, Typeface.TEXT, LABEL_SIZE);
    }

    /**
     * A pill button: at rest a faint surface, under the cursor the gold, selected (a switch that is
     * on, the chosen tool) the accent.
     */
    static void button(
            String s, float x, float y, float w, float h, boolean hover, boolean selected) {
        float r = h / 2.0F;
        int ink;
        if (selected) {
            Render.roundRect(x, y, w, h, r, hover ? 0xFF8D63FF : Theme.ACCENT);
            ink = 0xFFFFFFFF;
        } else {
            Render.roundRect(x, y, w, h, r, hover ? GOLD_FAINT : SURFACE);
            ink = hover ? Theme.GOLD : Theme.INK;
        }
        text(s, x + w / 2.0F, y + h / 2.0F, ink);
    }

    /** The main action (Done): the filled accent pill. */
    static void primary(String s, float x, float y, float w, float h, boolean hover) {
        button(s, x, y, w, h, hover, true);
    }

    /** Text centred on (cx, cy), cut to fit nothing: the buttons are sized for their labels. */
    static void text(String s, float cx, float cy, int argb) {
        float w = Render.textWidth(s, Typeface.TEXT, TEXT_SIZE);
        float h = Render.textHeight(Typeface.TEXT, TEXT_SIZE);
        Render.text(s, cx - w / 2.0F, cy - h / 2.0F, argb, Typeface.TEXT, TEXT_SIZE);
    }

    /**
     * The design system's slider: a thin track, the part up to the value in {@code fillColor} and a
     * round knob in the ink, bigger under the cursor.
     */
    static void slider(float x, float cy, float w, float fraction, int fillColor, boolean hover) {
        float f = Math.max(0, Math.min(1, fraction));
        float th = 3.0F;
        Render.roundRect(x, cy - th / 2, w, th, th / 2, Theme.RULE_STRONG);
        if (w * f >= th) {
            Render.roundRect(x, cy - th / 2, w * f, th, th / 2, fillColor);
        }
        float kx = x + w * f;
        if (hover) {
            Render.circle(kx, cy, 6.0F, Theme.ACCENT_SOFT);
        }
        Render.circle(kx, cy, hover ? 4.0F : 3.5F, Theme.INK);
    }

    /** A checkerboard behind something with transparency, {@code cell} units per square. */
    static void checker(float x, float y, float w, float h, float cell) {
        Render.rect(x, y, w, h, 0xFF1A1426);
        int cols = (int) Math.ceil(w / cell);
        int rows = (int) Math.ceil(h / cell);
        for (int j = 0; j < rows; j++) {
            for (int i = (j & 1); i < cols; i += 2) {
                float cx = x + i * cell;
                float cy = y + j * cell;
                Render.rect(
                        cx, cy, Math.min(cell, x + w - cx), Math.min(cell, y + h - cy), 0xFF241D33);
            }
        }
    }
}
