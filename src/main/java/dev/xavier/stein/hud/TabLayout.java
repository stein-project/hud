/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Render;

import java.util.List;

/**
 * Where the Tab list goes: scale, columns, rows per column, how many rows show and each cell's
 * width. The largest scale at which the list fits is chosen, and only what does not fit even at the
 * smallest becomes "+N".
 */
final class TabLayout {

    /** Each row's height and the head's width, like the game. */
    static final int ROW = 9;

    static final int HEAD = 8;

    /** The smallest scale accepted before cutting the list with "+N". */
    static final float MIN_SCALE = 0.6F;

    /** Columns: automatic (as many as fit) or a fixed number, kept by shrinking the scale. */
    static final String[] COLUMN_NAMES = {"tab.auto", "2", "3", "4"};

    static final int[] COLUMNS = {0, 2, 3, 4};

    /** Never more than this in automatic: with more, each column gets too narrow for the name. */
    private static final int MAX_COLUMNS = 8;

    float scale;
    int columns;
    int rows;
    int shown;
    int hidden;
    int cellW;

    /**
     * Where the drawing ends at the bottom, and the box's width, at scale: the self-test checks
     * they fit.
     */
    int bottom;

    int totalW;
    int logicalW;
    int logicalH;

    /** The widest name (or group title, or the empty list's notice). */
    static int nameWidth(List<TabEntries.Line> lines, String empty) {
        int nameW = empty == null ? 0 : Render.textWidth(empty);
        for (TabEntries.Line l : lines) {
            String text =
                    l.player != null ? l.name : l.title == null ? "" : l.title + "  " + l.count;
            nameW = Math.max(nameW, Render.textWidth(text));
        }
        return nameW;
    }

    /**
     * Everything here is measured at scale (header and footer are drawn at it too), in GUI pixels.
     * The column cap enters the height: before, with more players than 8 columns hold, the list ran
     * past the bottom of the screen; and when "+N" is left, its row must fit as well.
     */
    static TabLayout of(
            List<TabEntries.Line> lines,
            int nameW,
            int scoreW,
            int width,
            int guiH,
            String header,
            String footer,
            boolean note,
            TabConfig c) {
        int pingW = (c.ping != 1 ? 12 : 0) + (c.ping != 0 ? Render.textWidth("999 ms") + 4 : 0);
        int distW = c.distance ? Render.textWidth("999 m") + 6 : 0;
        TabLayout at = new TabLayout();
        at.cellW = (c.heads ? HEAD + 2 : 0) + nameW + 6 + distW + scoreW + pingW;
        int size = lines.size();
        int n = Math.max(1, size);
        int fixed = COLUMNS[Math.max(0, Math.min(COLUMNS.length - 1, c.columns))];
        int headerH = 0;
        int footerH = 0;
        for (int step = 0; ; step++) {
            float s = Math.max(MIN_SCALE, 1.0F - step * 0.05F);
            boolean last = s <= MIN_SCALE + 0.001F;
            int w = (int) (width / s);
            headerH = wrapped(header, w);
            footerH = wrapped(footer, w);
            int h = (int) ((guiH - 20) / s) - headerH - footerH - (note ? ROW + 2 : 0);
            int fit = Math.max(1, h / ROW);
            int widest =
                    Math.max(
                            1, Math.min(fixed > 0 ? fixed : MAX_COLUMNS, (w - 8) / (at.cellW + 5)));
            int cols = fixed > 0 ? Math.min(fixed, n) : (n + fit - 1) / fit;
            at.scale = s;
            if (cols <= widest && (n + cols - 1) / cols <= fit) {
                at.columns = cols;
                at.rows = (n + cols - 1) / cols;
                break;
            }
            if (last) {
                // Not even at the smallest scale: the columns that fit the width, full to the
                // bottom minus the "+N" row.
                at.columns = widest;
                at.rows = Math.max(1, fit - 1);
                break;
            }
        }
        at.shown = Math.min(size, at.rows * at.columns);
        at.hidden = size - at.shown;
        at.logicalW = (int) (width / at.scale);
        at.logicalH = (int) (guiH / at.scale);
        at.totalW = at.cellW * at.columns + (at.columns - 1) * 5;
        int listH = Math.min(at.rows, Math.max(1, at.shown)) * ROW + (at.hidden > 0 ? ROW : 0);
        // As the list draws: 10 of margin, header, the filter's row, the list's box and the footer
        // 4 below it.
        at.bottom =
                (int) (10 / at.scale)
                        + headerH
                        + (note ? ROW + 2 : 0)
                        + listH
                        + 4
                        + Math.max(0, footerH - 2);
        return at;
    }

    /**
     * The header's (or footer's) height wrapped at the given width, with the gap below; 0 without
     * text.
     */
    private static int wrapped(String text, int width) {
        return text == null || text.isEmpty() ? 0 : Render.wrap(text, width - 50).size() * ROW + 2;
    }
}
