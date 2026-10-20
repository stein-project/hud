/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import static dev.xavier.stein.hud.TabLayout.HEAD;
import static dev.xavier.stein.hud.TabLayout.ROW;

import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Render;

import java.util.List;

/**
 * Our Tab list instead of the game's (the Loader hands over the drawing through {@code
 * onPlayerList}).
 *
 * <p>What the game's does that gets in the way on a full server: it cuts at <b>80 players</b>,
 * shows ping as five bars with cuts at 150, 300, 600 and 1000 ms (40 ms and 140 ms look the same)
 * and lets the server rule the looks: header, footer and the team's prefix/suffix glued to the
 * nick.
 *
 * <p>Here all of that is an option, and the list can also be <b>filtered and grouped</b> ({@link
 * TabEntries}): only those with a clan, only allies or enemies, only those nearby, grouped by clan
 * or by relation, with each one's distance.
 */
final class TabList {

    private final TabConfig c;
    private final TabEntries entries;

    /** The distance column this frame: the player's option, if the server allows it. */
    private boolean showDistance;

    /** The drawing's result, written by the game font's callback. */
    private boolean drawn;

    TabList(TabConfig c, Log log) {
        this.c = c;
        this.entries = new TabEntries(c, log);
    }

    TabEntries entries() {
        return entries;
    }

    /**
     * Draws the list. Returns false only without a connection (the game then draws its own): with a
     * filter that left nobody, the list is still ours, empty and saying why. It used to return
     * false in that case too, and the game drew its list, without filter, order or groups; that was
     * the "sometimes it sorts, sometimes not": my clan not read yet on joining or switching
     * servers, or nobody nearby, and the whole list was the game's again.
     */
    boolean draw(int width, boolean objective) {
        if (Players.self() == null) {
            return false;
        }
        if (c.gameFont) {
            drawn = false;
            Render.gameFont(() -> drawn = drawList(width, objective));
            return drawn;
        }
        return drawList(width, objective);
    }

    private boolean drawList(int width, boolean objective) {
        List<TabEntries.Line> lines = entries.lines();
        String header = c.headerFooter ? Players.tabHeader() : null;
        String footer = c.headerFooter ? Players.tabFooter() : null;
        // With a filter on, a row saying which one and how many were kept out of how many: without
        // it, a list that shrank looks like a broken list, and the filter itself is the
        // explanation.
        int filter = entries.filter();
        showDistance = c.distance && ServerLimits.allows(ServerLimits.DISTANCE);
        String note =
                filter == 0
                        ? null
                        : T.tr(TabEntries.FILTERS[clamp(filter, TabEntries.FILTERS.length)])
                                + " · "
                                + entries.kept
                                + "/"
                                + entries.total;
        String empty = lines.isEmpty() ? T.tr("tab.nobody") : null;

        int scoreW = 0;
        if (objective) {
            for (TabEntries.Line l : lines) {
                if (l.player != null) {
                    scoreW = Math.max(scoreW, Render.textWidth(score(l)) + 6);
                }
            }
        }
        TabLayout at =
                TabLayout.of(
                        lines,
                        TabLayout.nameWidth(lines, empty),
                        scoreW,
                        width,
                        Render.guiHeight(),
                        header,
                        footer,
                        note != null,
                        showDistance,
                        c);
        float scale = at.scale;
        int columns = at.columns;
        int rows = at.rows;
        int shown = at.shown;
        int hidden = at.hidden;
        int cellW = at.cellW;

        Render.push();
        Render.scale(scale);
        int logicalW = (int) (width / scale);
        int totalW = cellW * columns + (columns - 1) * 5;
        int left = logicalW / 2 - totalW / 2;
        int top = (int) (10 / scale);
        if (header != null && !header.isEmpty()) {
            top = centered(header, logicalW, top);
        }
        if (note != null) {
            Render.text(
                    "§7" + note,
                    left + totalW / 2 - Render.textWidth(note) / 2,
                    top,
                    0x9A9A9A,
                    true);
            top += ROW + 2;
        }
        int listH = Math.min(rows, Math.max(1, shown)) * ROW + (hidden > 0 ? ROW : 0);
        // Stein's standard background, at the chosen transparency; the row strips follow it.
        float bg = Math.max(0.0F, Math.min(1.0F, c.background));
        Render.surface(left - 3, top - 3, totalW + 6, listH + 6, bg);
        if (empty != null) {
            Render.text(
                    "§7" + empty,
                    left + totalW / 2 - Render.textWidth(empty) / 2,
                    top,
                    0xA0A0A0,
                    true);
        }
        for (int i = 0; i < shown; i++) {
            TabEntries.Line l = lines.get(i);
            int x = left + (i / rows) * (cellW + 5);
            int y = top + (i % rows) * ROW;
            if (l.player == null) {
                if (l.title != null) {
                    group(l, x, y, cellW); // a row without a title is the gap between groups
                }
            } else {
                row(l, x, y, cellW, objective);
            }
        }
        if (hidden > 0) {
            String more = "+" + hidden;
            Render.text(
                    "§7" + more,
                    left + totalW / 2 - Render.textWidth(more) / 2,
                    top + rows * ROW,
                    0xA0A0A0,
                    true);
        }
        int bottom = top + listH + 4;
        if (footer != null && !footer.isEmpty()) {
            bottom = centered(footer, logicalW, bottom);
        }
        Render.pop();
        // What lies under the list (header to footer) leaves the grid while it is open.
        Hud.cover((left - 3) * scale, 0, (totalW + 6) * scale, bottom * scale);
        return true;
    }

    private static int clamp(int i, int length) {
        return Math.max(0, Math.min(length - 1, i));
    }

    /**
     * A group's title: a dark bar, the name in bold and the count on the right. Grouped by
     * relation, the title takes the relation's color, the same as the names: with 400 players and a
     * tiny list, the color is what you see before any letter.
     */
    private void group(TabEntries.Line l, int x, int y, int cellW) {
        Render.rect(x, y - 1, cellW, ROW, alphaOf(0x90000000));
        int color = c.group == 2 ? relationColor(l.relation) : 0xE0C890;
        Render.text("§l" + l.title, x + 2, y, color, true);
        String count = String.valueOf(l.count);
        Render.text("§7" + count, x + cellW - 2 - Render.textWidth(count), y, 0xA0A0A0, true);
    }

    /**
     * The color with its opacity scaled by the chosen transparency (the default, 0.63, keeps it).
     */
    private int alphaOf(int argb) {
        float k = Math.max(0.0F, Math.min(1.0F, c.background / 0.63F));
        return Math.round((argb >>> 24) * k) << 24 | argb & 0xFFFFFF;
    }

    /**
     * The relation group's color, by the relation kept in the title (not the text, which is
     * translated).
     */
    private static int relationColor(int relation) {
        return switch (relation) {
            case RelationLink.CLAN, RelationLink.ALLY, RelationLink.ENEMY ->
                    RelationLink.colorOf(relation);
            default -> 0xD0D0D0;
        };
    }

    private void row(TabEntries.Line l, int x, int y, int cellW, boolean objective) {
        if (c.rowBackground) {
            Render.rect(x, y, cellW, ROW - 1, alphaOf(0x20FFFFFF));
        }
        int textX = x;
        if (c.heads) {
            // The server's skin rules; without any (an offline-mode server, everyone Steve), the
            // premium account's with that nick, through the Loader. It may not be that person: on
            // an offline server their "Notch" is not Notch; it is only to recognise who you know.
            Render.playerHead(l.nick, x, y, HEAD, true);
            textX += HEAD + 2;
        }
        Render.text(
                l.spectator ? "§7§o" + TabEntries.strip(l.name) : l.name, textX, y, color(l), true);
        int right = x + cellW;
        if (c.ping != 1) {
            Render.pingIcon(l.ping, right - 11, y); // the game's bars, in the same place
            right -= 12;
        }
        if (c.ping != 0) {
            String ms = l.ping + " ms";
            Render.text(ms, right - Render.textWidth(ms), y, pingColor(l.ping), true);
            right -= Render.textWidth(ms) + 4;
        }
        if (showDistance) {
            String d = l.distance < 0 ? "--" : Math.round(l.distance) + " m";
            Render.text("§7" + d, right - Render.textWidth(d), y, 0xA0A0A0, true);
            right -= Render.textWidth(d) + 6;
        }
        if (objective) {
            String s = score(l);
            Render.text("§e" + s, right - Render.textWidth(s), y, 0xFFFFFF, true);
        }
    }

    /** White, the server's, or by relation (the same color as on the minimap). */
    private int color(TabEntries.Line l) {
        if (c.nameColor != 2 || l.player == null) {
            return 0xFFFFFF;
        }
        return RelationLink.color(l.nick);
    }

    private static int pingColor(int ms) {
        return ms <= 0
                ? 0x808080
                : ms < 80 ? 0x55FF55 : ms < 150 ? 0xFFFF55 : ms < 300 ? 0xFFAA00 : 0xFF5555;
    }

    /** The list's score (the number column) of the row's player. */
    private static String score(TabEntries.Line l) {
        String s = Players.listScore(l.nick);
        return s == null ? "" : s;
    }

    /** Header (or footer) centred; returns the y just below it. */
    private static int centered(String text, int width, int y) {
        List<String> all = Render.wrap(text, width - 50);
        for (int i = 0; i < all.size(); i++) {
            String line = all.get(i);
            Render.text(line, width / 2 - Render.textWidth(line) / 2, y + i * ROW, 0xFFFFFF, true);
        }
        return y + all.size() * ROW + 2;
    }
}
