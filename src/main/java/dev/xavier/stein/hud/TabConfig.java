/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

/**
 * The Tab list's settings, the {@code "tab"} object of {@code config/steinhud.json}. The field
 * names are the JSON keys players already have: do not rename them.
 *
 * <p>Not a grid element: the list is transient and covers the whole screen while the key is down,
 * so it has no place and no neighbours; it stays where the game draws its own.
 */
public final class TabConfig {

    /**
     * Who draws the list: false the game, true ours.
     *
     * <p>With the game's, nothing here applies: it shows exactly what the server sends, header,
     * footer and order included. That is what "Restore defaults" brings back: turning off what we
     * did, not imitating the game with our options. Who already had a configuration keeps ours
     * ({@link HudConfigFile#load}).
     */
    public boolean own = true;

    /** 0 bars (like the game, and the default), 1 number in ms, 2 both. */
    public int ping = 0;

    /** The header and footer the server sends. */
    public boolean headerFooter = true;

    /** Hide the name's prefix (only on screen; the server still sends it). */
    public boolean hidePrefix = false;

    /** Hide the name's suffix (only on screen). */
    public boolean hideSuffix = false;

    /** 0 the server's color, 1 white, 2 by relation. */
    public int nameColor = 0;

    /** The player's head before the name. */
    public boolean heads = true;

    /**
     * Most players in the list, in the order of TabEntries.LIMITS (the game's, 80, is the default).
     */
    public int limit = 2;

    /** 0 team and name (like the game), 1 ping, 2 name. */
    public int sort = 0;

    /** Who gets in, in the order of TabEntries.FILTERS. */
    public int filter = 0;

    /** 0 no groups, 1 by clan, 2 by relation (my clan, allies, enemies, no clan). */
    public int group = 0;

    /**
     * Columns, in the order of TabLayout.COLUMN_NAMES: 0 automatic, 1 to 3 are 2, 3 and 4 fixed.
     */
    public int columns = 0;

    /** The list's background opacity, from 0 (none) to 1 (the Panel sets the transparency). */
    public float background = 0.63F;

    /** The strip behind each name (the separator between rows). */
    public boolean rowBackground = true;

    /** The game's own font on the Tab list, instead of Stein's. */
    public boolean gameFont = false;

    /**
     * Everything back to the defaults, in the same object: the list and the Panel's options hold
     * this instance, so swapping it for a new one would leave both looking at the old one.
     */
    void reset() {
        copyFrom(new TabConfig());
    }

    /**
     * Every field of another one, into this object (the self-test saves and restores the player's).
     */
    void copyFrom(TabConfig d) {
        own = d.own;
        ping = d.ping;
        headerFooter = d.headerFooter;
        hidePrefix = d.hidePrefix;
        hideSuffix = d.hideSuffix;
        nameColor = d.nameColor;
        heads = d.heads;
        limit = d.limit;
        sort = d.sort;
        filter = d.filter;
        group = d.group;
        columns = d.columns;
        background = d.background;
        rowBackground = d.rowBackground;
        gameFont = d.gameFont;
    }

    /** The fields as text, to compare two configurations and for the log. */
    String describe() {
        return "own="
                + own
                + " ping="
                + ping
                + " headerFooter="
                + headerFooter
                + " prefix="
                + hidePrefix
                + " suffix="
                + hideSuffix
                + " color="
                + nameColor
                + " heads="
                + heads
                + " limit="
                + limit
                + " sort="
                + sort
                + " filter="
                + filter
                + " group="
                + group
                + " columns="
                + columns
                + " background="
                + background
                + " rows="
                + rowBackground
                + " gameFont="
                + gameFont;
    }

    /** Indexes out of range (a hand-edited file, an old version) go back to each one's default. */
    void repair() {
        TabConfig d = new TabConfig();
        ping = in(ping, 3, d.ping);
        nameColor = in(nameColor, 3, d.nameColor);
        limit = in(limit, TabEntries.LIMITS.length, d.limit);
        sort = in(sort, TabEntries.SORTS.length, d.sort);
        filter = in(filter, TabEntries.FILTERS.length, d.filter);
        group = in(group, TabEntries.GROUPS.length, d.group);
        columns = in(columns, TabLayout.COLUMN_NAMES.length, d.columns);
    }

    private static int in(int v, int n, int fallback) {
        return v >= 0 && v < n ? v : fallback;
    }
}
