/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Mesh;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Theme;

import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The crosshair's pixel editor: the grid on the left (left click paints, right click erases, with
 * the mirrors on what is painted on one side shows on the other), the tools, presets, colors and
 * favorites on the right, and a preview at the real size, over dark and light, under the grid.
 *
 * <p>Every change shows on the game's crosshair at once; the file is written when a stroke or a
 * drag ends, and when the screen closes.
 */
final class CrosshairEditor extends GuiScreen {

    private static final int MARGIN = 10;
    private static final float ROW = 14;
    private static final float GAP = 4;
    private static final float LABEL_H = 9;
    private static final float GROUP = 6;
    private static final float THUMB = 18;
    private static final float PREVIEW_H = 30;
    private static final int COLUMN = 200;

    /** What a click means to {@link #controls}: nothing (drawing), or the mouse button. */
    private static final int DRAW = -1;

    private static final String[] CHANNELS = {
        "crosshair.red", "crosshair.green", "crosshair.blue", "crosshair.opacity"
    };
    private static final int[] CHANNEL_COLORS = {0xFFE0708A, 0xFF9BD4A8, 0xFF7FB8C4, Theme.ACCENT};

    private final SteinHud mod;
    private final GuiScreen parent;
    private CrosshairPattern pattern;

    // The layout, from initGui: the grid's corner and cell, the right column, the preview.
    private float gridX;
    private float gridY;
    private float cell;
    private float columnX;
    private float columnW;
    private float previewY;

    /** A stroke on the grid: 1 paints, 0 erases, -1 none. */
    private int stroke = -1;

    /** The color slider being dragged (0 red to 3 opacity), or -1. */
    private int dragging = -1;

    /** The sliders change the outline's color instead of the crosshair's. */
    private boolean editOutline;

    private Mesh gridFill;
    private Mesh gridOutline;
    private boolean gridStale = true;
    private final List<Mesh> thumbs = new ArrayList<>();
    private boolean thumbsStale = true;

    // The mouse for this pass of controls(): where it is and which button (DRAW: none).
    private int mouseX;
    private int mouseY;
    private int click = DRAW;

    CrosshairEditor(SteinHud mod, GuiScreen parent) {
        this.mod = mod;
        this.parent = parent;
        this.pattern = CrosshairPattern.decode(mod.config.crosshair.pattern);
    }

    private HudConfig.Crosshair c() {
        return mod.config.crosshair;
    }

    @Override
    public void initGui() {
        layout();
    }

    private void layout() {
        int n = pattern.size();
        columnW = Math.min(COLUMN, Math.max(150, width / 2 - MARGIN));
        columnX = width - MARGIN - columnW;
        float top = MARGIN + 20;
        float side = Math.min(height - top - MARGIN - PREVIEW_H - 6, columnX - MARGIN - 14);
        cell = Math.max(2, (float) Math.floor(side / n));
        gridX = MARGIN;
        gridY = top;
        previewY = gridY + cell * n + 6;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // --- drawing ---

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        Render.rect(0, 0, width, height, 0xC00A0614);
        EditorUi.title(T.tr("crosshair.editor"), MARGIN, MARGIN);
        drawGrid(mx, my);
        drawPreview();
        mouseX = mx;
        mouseY = my;
        click = DRAW;
        controls();
    }

    private void drawGrid(int mx, int my) {
        int n = pattern.size();
        float side = cell * n;
        Render.rect(gridX, gridY, side, side, 0xFF120C1E);
        for (int i = 1; i < n; i++) {
            Render.rect(gridX + i * cell, gridY, 1, side, Theme.RULE);
            Render.rect(gridX, gridY + i * cell, side, 1, Theme.RULE);
        }
        buildGrid();
        Render.clip(gridX, gridY, side, side);
        Render.push();
        Render.translate(gridX, gridY);
        Render.scale(cell);
        if (gridOutline != null) {
            Render.mesh(gridOutline);
        }
        if (gridFill != null) {
            Render.mesh(gridFill);
        }
        Render.pop();
        Render.unclip();
        int mid = pattern.middle();
        if (c().mirrorX) {
            Render.rect(gridX + side / 2 - 0.5F, gridY, 1, side, 0x667C4DFF);
        }
        if (c().mirrorY) {
            Render.rect(gridX, gridY + side / 2 - 0.5F, side, 1, 0x667C4DFF);
        }
        Render.rectOutline(gridX + mid * cell, gridY + mid * cell, cell, cell, 1, Theme.ACCENT);
        int hx = cellAt(mx, gridX);
        int hy = cellAt(my, gridY);
        if (hx >= 0 && hy >= 0) {
            Render.rectOutline(gridX + hx * cell, gridY + hy * cell, cell, cell, 1, Theme.GOLD);
        }
        Render.rectOutline(gridX - 1, gridY - 1, side + 2, side + 2, 1, Theme.RULE_STRONG);
    }

    /** The real size, over a dark and a light half: what inverting does shows on both. */
    private void drawPreview() {
        float w = cell * pattern.size();
        float half = w / 2;
        Render.rect(gridX, previewY, half, PREVIEW_H, 0xFF000000);
        Render.gradient(gridX + half, previewY, half, PREVIEW_H, 0xFFFFFFFF, 0xFF7FB8C4, true);
        CrosshairDrawer d = mod.crosshair();
        float s = d.cellSize();
        float total = d.size() * s;
        Render.clip(gridX, previewY, w, PREVIEW_H);
        float cy = previewY + (PREVIEW_H - total) / 2;
        d.draw(gridX + (half - total) / 2, cy, s);
        d.draw(gridX + half + (half - total) / 2, cy, s);
        Render.unclip();
    }

    private void buildGrid() {
        if (!gridStale) {
            return;
        }
        releaseGrid();
        gridStale = false;
        HudConfig.Crosshair c = c();
        // Never invisible on the grid, even at zero opacity: the cells must show to be edited.
        int fill = Math.max(0x50, c.color >>> 24) << 24 | (c.color & 0xFFFFFF);
        gridFill = CrosshairDrawer.cells(pattern, false, fill);
        gridOutline = c.outline ? CrosshairDrawer.cells(pattern, true, c.outlineColor) : null;
    }

    // --- the right column: drawn and clicked through the same code ---

    private void controls() {
        HudConfig.Crosshair c = c();
        float x = columnX;
        float w = columnW;
        float y = MARGIN + 20;

        // Grid size and mirrors, on one line.
        float half = (w - GAP * 2) / 2;
        label(T.tr("crosshair.size"), x, y);
        label(T.tr("crosshair.mirror"), x + half + GAP * 2, y);
        y += LABEL_H;
        if (button("-", x, y, 16, false)) {
            resize(-2);
        }
        EditorUi.text(String.valueOf(pattern.size()), x + 16 + 13, y + ROW / 2, Theme.GOLD);
        if (button("+", x + 42, y, 16, false)) {
            resize(2);
        }
        float mw = (half - GAP) / 2;
        float mx = x + half + GAP * 2;
        if (button(T.tr("horizontal"), mx, y, mw, c.mirrorX)) {
            c.mirrorX = !c.mirrorX;
            mod.crosshairChanged();
        }
        if (button(T.tr("vertical"), mx + mw + GAP, y, mw, c.mirrorY)) {
            c.mirrorY = !c.mirrorY;
            mod.crosshairChanged();
        }
        y += ROW + GROUP;

        // Presets and clear.
        label(T.tr("crosshair.presets"), x, y);
        y += LABEL_H;
        int count = CrosshairPattern.PRESET_KEYS.length + 1;
        float bw = (w - GAP * (count - 1)) / count;
        for (int i = 0; i < CrosshairPattern.PRESET_KEYS.length; i++) {
            if (button(T.tr(CrosshairPattern.PRESET_KEYS[i]), x + i * (bw + GAP), y, bw, false)) {
                pattern = CrosshairPattern.preset(i, pattern.size());
                patternChanged(true);
            }
        }
        if (button(T.tr("crosshair.clear"), x + (count - 1) * (bw + GAP), y, bw, false)) {
            pattern.clear();
            patternChanged(true);
        }
        y += ROW + GROUP;

        y = colors(x, y, w);
        favorites(x, y, w);

        float dw = 70;
        if (primary(Lang.tr("gui.done"), width - MARGIN - dw, height - MARGIN - ROW, dw)) {
            mc.displayGuiScreen(parent);
        }
    }

    /** The color group: which color the sliders edit, and the four sliders. */
    private float colors(float x, float y, float w) {
        label(T.tr("crosshair.color"), x, y);
        y += LABEL_H;
        float tw = (w - GAP) / 2;
        if (button(T.tr("crosshair.fill"), x, y, tw, !editOutline)) {
            editOutline = false;
        }
        if (button(T.tr("crosshair.outline"), x + tw + GAP, y, tw, editOutline)) {
            editOutline = true;
        }
        y += ROW + 2;
        int argb = editOutline ? c().outlineColor : c().color;
        float lw = 40;
        float vw = 30;
        for (int i = 0; i < 4; i++) {
            float rowY = y + i * 12;
            int shift = i == 3 ? 24 : 16 - i * 8;
            int v = argb >>> shift & 255;
            float tx = x + lw;
            float tw2 = w - lw - vw;
            boolean over = EditorUi.inside(mouseX, mouseY, tx - 4, rowY, tw2 + 8, 12);
            if (click == DRAW) {
                EditorUi.label(T.tr(CHANNELS[i]), x, rowY + 3);
                EditorUi.slider(tx, rowY + 6, tw2, v / 255.0F, CHANNEL_COLORS[i], over);
                String value = i == 3 ? Math.round(v * 100 / 255.0F) + "%" : String.valueOf(v);
                EditorUi.text(value, x + w - vw / 2, rowY + 6, Theme.GOLD);
            } else if (over && click == 0) {
                dragging = i;
                slide(mouseX);
            }
        }
        return y + 4 * 12 + GROUP;
    }

    /** The favorites: save the current pattern, then one thumbnail per favorite. */
    private void favorites(float x, float y, float w) {
        List<String> favs = c().favorites;
        label(T.tr("crosshair.favorites"), x, y);
        y += LABEL_H;
        boolean full = favs.size() >= CrosshairPattern.MAX_FAVORITES;
        if (button(T.tr("crosshair.save"), x, y, 50, false) && !full) {
            String code = pattern.encode();
            if (!favs.contains(code)) {
                favs.add(code);
                thumbsStale = true;
                mod.crosshairChanged();
            }
        }
        if (click == DRAW) {
            EditorUi.hint(
                    T.tr(favs.isEmpty() ? "crosshair.favorites.empty" : "crosshair.favorites.hint"),
                    x + 56,
                    y + 4);
        }
        y += ROW + 3;
        buildThumbs();
        int perRow = Math.max(1, (int) ((w + 3) / (THUMB + 3)));
        for (int i = 0; i < favs.size() && i < thumbs.size(); i++) {
            float tx = x + (i % perRow) * (THUMB + 3);
            float ty = y + (i / perRow) * (THUMB + 3);
            boolean over = EditorUi.inside(mouseX, mouseY, tx, ty, THUMB, THUMB);
            if (click == DRAW) {
                Render.roundRect(tx, ty, THUMB, THUMB, 3, over ? 0x24B89CD9 : 0x14E8E1F5);
                Mesh m = thumbs.get(i);
                if (m != null) {
                    int n = CrosshairPattern.decode(favs.get(i)).size();
                    float s = (THUMB - 4) / n;
                    Render.push();
                    Render.translate(tx + 2, ty + 2);
                    Render.scale(s);
                    Render.mesh(m);
                    Render.pop();
                }
            } else if (over && click == 0) {
                pattern = CrosshairPattern.decode(favs.get(i));
                patternChanged(true);
                return;
            } else if (over && click == 1) {
                favs.remove(i);
                thumbsStale = true;
                mod.crosshairChanged();
                return;
            }
        }
    }

    private void buildThumbs() {
        if (!thumbsStale) {
            return;
        }
        releaseThumbs();
        thumbsStale = false;
        for (String f : c().favorites) {
            thumbs.add(CrosshairDrawer.cells(CrosshairPattern.decode(f), false, Theme.INK));
        }
    }

    private void label(String s, float x, float y) {
        if (click == DRAW) {
            EditorUi.label(s, x, y);
        }
    }

    /** Draws the button, or, in a click pass, says whether the left click landed on it. */
    private boolean button(String s, float x, float y, float w, boolean selected) {
        boolean over = EditorUi.inside(mouseX, mouseY, x, y, w, ROW);
        if (click == DRAW) {
            EditorUi.button(s, x, y, w, ROW, over, selected);
            return false;
        }
        return over && click == 0;
    }

    private boolean primary(String s, float x, float y, float w) {
        boolean over = EditorUi.inside(mouseX, mouseY, x, y, w, ROW);
        if (click == DRAW) {
            EditorUi.primary(s, x, y, w, ROW, over);
            return false;
        }
        return over && click == 0;
    }

    // --- input ---

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button > 1) {
            return;
        }
        if (cellAt(mx, gridX) >= 0 && cellAt(my, gridY) >= 0) {
            stroke = button == 0 ? 1 : 0;
            paint(mx, my);
            return;
        }
        mouseX = mx;
        mouseY = my;
        click = button;
        controls();
        click = DRAW;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long timeSinceLastClick) {
        if (stroke >= 0) {
            paint(mx, my);
        } else if (dragging >= 0) {
            slide(mx);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (stroke >= 0 || dragging >= 0) {
            stroke = -1;
            dragging = -1;
            mod.crosshairChanged();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void onGuiClosed() {
        mod.crosshairChanged();
        releaseGrid();
        releaseThumbs();
    }

    /** The cell under a mouse coordinate along one axis, or -1 outside the grid. */
    private int cellAt(int m, float origin) {
        float f = (m - origin) / cell;
        return f >= 0 && f < pattern.size() ? (int) f : -1;
    }

    private void paint(int mx, int my) {
        int x = cellAt(mx, gridX);
        int y = cellAt(my, gridY);
        boolean on = stroke == 1;
        if (x < 0 || y < 0 || pattern.get(x, y) == on) {
            return;
        }
        pattern.paint(x, y, on, c().mirrorX, c().mirrorY);
        patternChanged(false);
    }

    /** Moves the dragged slider to the mouse. */
    private void slide(int mx) {
        float lw = 40;
        float vw = 30;
        float tx = columnX + lw;
        float tw = columnW - lw - vw;
        int v = Math.round(Math.max(0, Math.min(1, (mx - tx) / tw)) * 255);
        int shift = dragging == 3 ? 24 : 16 - dragging * 8;
        HudConfig.Crosshair c = c();
        if (editOutline) {
            c.outlineColor = c.outlineColor & ~(255 << shift) | v << shift;
        } else {
            c.color = c.color & ~(255 << shift) | v << shift;
        }
        gridStale = true;
        mod.crosshair().invalidate();
    }

    private void resize(int by) {
        pattern.resize(pattern.size() + by);
        layout();
        patternChanged(true);
    }

    /** The pattern changed: the grid, the preview and the game's crosshair follow. */
    private void patternChanged(boolean save) {
        c().pattern = pattern.encode();
        gridStale = true;
        if (save) {
            layout();
            mod.crosshairChanged();
        } else {
            mod.crosshair().invalidate();
        }
    }

    private void releaseGrid() {
        if (gridFill != null) {
            gridFill.release();
            gridFill = null;
        }
        if (gridOutline != null) {
            gridOutline.release();
            gridOutline = null;
        }
        gridStale = true;
    }

    private void releaseThumbs() {
        for (Mesh m : thumbs) {
            if (m != null) {
                m.release();
            }
        }
        thumbs.clear();
        thumbsStale = true;
    }
}
