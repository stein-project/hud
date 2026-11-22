/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Blend;
import dev.xavier.stein.loader.api.Game;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Mesh;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Screens;

/**
 * Draws the custom crosshair in place of the game's, in the middle of the screen, from {@link
 * HudConfig.Crosshair}. The cells become two meshes (the fill and the outline), built again only
 * when the settings change ({@link #invalidate}), so a frame costs a handful of calls and no
 * allocation.
 *
 * <p>It follows the game's own rules for when a crosshair shows (not with F3, and in spectator only
 * over an entity: {@link Hud#crosshairAllowed}); the player's options only take it away (third
 * person, screens), never show it where the game would not.
 */
final class CrosshairDrawer {

    private final SteinHud mod;

    private Mesh fill;
    private Mesh outline;
    private boolean stale = true;
    private boolean invert;

    /** The side of the pattern the meshes were built from. */
    private int size;

    /** The mesh being drawn by {@link #drawFill}, inside {@link Render#blend}. */
    private Mesh drawing;

    private final Runnable drawFill = this::drawFill;

    CrosshairDrawer(SteinHud mod) {
        this.mod = mod;
    }

    /** The settings changed: the meshes are built again before the next drawing. */
    void invalidate() {
        stale = true;
    }

    /** From onOverlay: the crosshair in the middle of the screen, or nothing. */
    void frame() {
        HudConfig.Crosshair c = mod.config.crosshair;
        if (!c.enabled) {
            return;
        }
        // Even when the options hide ours, the game's stays out: the player chose a crosshair of
        // their own, and the game's coming back in third person would undo that choice.
        Hud.replaceCrosshair();
        if (!Hud.crosshairAllowed()
                || (c.hideThirdPerson && Game.perspective() != 0)
                || (c.hideInScreens && Screens.isOpen())
                // The editor shows its own preview; the real one would sit in the middle of it.
                || Screens.current() instanceof CrosshairEditor) {
            return;
        }
        int gui = Math.max(1, Render.guiScale());
        int cellPx = c.scale > 0 ? c.scale : gui;
        float cell = (float) cellPx / gui;
        build();
        int n = size;
        // Whole screen pixels, the pattern's middle on the window's middle: the game's crosshair
        // sits
        // on the middle GUI pixel, half a GUI pixel right of and below the true centre on an even
        // window, which a small dot makes visible. Only the drawing moves; the aim is the centre.
        int total = n * cellPx;
        float x = (float) ((Render.displayWidth() - total) / 2) / gui;
        float y = (float) ((Render.displayHeight() - total) / 2) / gui;
        draw(x, y, cell);
    }

    /**
     * Draws the crosshair with its top-left cell at (x, y), {@code cell} GUI units per cell: the
     * game's crosshair and the editor's preview.
     */
    void draw(float x, float y, float cell) {
        build();
        if (fill == null) {
            return;
        }
        Render.push();
        Render.translate(x, y);
        Render.scale(cell);
        if (outline != null) {
            Render.mesh(outline);
        }
        drawing = fill;
        Render.blend(invert ? Blend.INVERT : Blend.NORMAL, drawFill);
        Render.pop();
    }

    /** GUI units per cell at the chosen scale (1 follows the interface). */
    float cellSize() {
        int scale = mod.config.crosshair.scale;
        return scale > 0 ? (float) scale / Math.max(1, Render.guiScale()) : 1.0F;
    }

    /** The side, in cells, of what {@link #draw} draws. */
    int size() {
        build();
        return size;
    }

    private void drawFill() {
        Render.mesh(drawing);
    }

    private void build() {
        if (!stale) {
            return;
        }
        release();
        stale = false;
        HudConfig.Crosshair c = mod.config.crosshair;
        CrosshairPattern p = CrosshairPattern.decode(c.pattern);
        size = p.size();
        invert = c.invert;
        fill = cells(p, false, fillColor(c));
        outline = c.outline ? cells(p, true, c.outlineColor) : null;
    }

    /**
     * The colour the fill is drawn with. Inverting ignores the alpha (the game's blend uses only
     * the colour), so the opacity goes into the colour itself: a half-transparent white inverts
     * halfway.
     */
    static int fillColor(HudConfig.Crosshair c) {
        if (!c.invert) {
            return c.color;
        }
        float a = (c.color >>> 24) / 255.0F;
        int r = Math.round((c.color >> 16 & 255) * a);
        int g = Math.round((c.color >> 8 & 255) * a);
        int b = Math.round((c.color & 255) * a);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * One quad per horizontal run of cells: the painted ones, or with {@code edge} the empty ones
     * that touch a painted one (the eight around it), one cell beyond the grid included.
     */
    static Mesh cells(CrosshairPattern p, boolean edge, int argb) {
        if ((argb >>> 24) == 0) {
            return null;
        }
        Mesh.Builder b = Mesh.builder(Mesh.Primitive.QUADS, Mesh.Format.COLOR);
        int n = p.size();
        int quads = 0;
        for (int y = -1; y <= n; y++) {
            int start = Integer.MIN_VALUE;
            for (int x = -1; x <= n + 1; x++) {
                boolean on = x <= n && (edge ? touches(p, x, y) : p.get(x, y));
                if (on && start == Integer.MIN_VALUE) {
                    start = x;
                } else if (!on && start != Integer.MIN_VALUE) {
                    b.vertex(start, y, 0).color(argb);
                    b.vertex(start, y + 1, 0).color(argb);
                    b.vertex(x, y + 1, 0).color(argb);
                    b.vertex(x, y, 0).color(argb);
                    quads++;
                    start = Integer.MIN_VALUE;
                }
            }
        }
        return quads == 0 ? null : b.build();
    }

    private static boolean touches(CrosshairPattern p, int x, int y) {
        if (p.get(x, y)) {
            return false;
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (p.get(x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Frees the meshes (the mod is suspended, or they are about to be built again). */
    void release() {
        if (fill != null) {
            fill.release();
            fill = null;
        }
        if (outline != null) {
            outline.release();
            outline = null;
        }
        stale = true;
    }
}
