/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Log;

/**
 * Test hooks for the Tab list, all off unless a system property asks for them. They exist to check
 * every option in a single session: on a real server, leaving and joining again for each option
 * costs a reconnection per test, and the player's file is never touched.
 *
 * <ul>
 *   <li>{@code -Dsteinhud.tabFilterAtSec=20,26,32}: at those seconds, the next filter.
 *   <li>{@code -Dsteinhud.tabGroupAtSec=20,30,40}: at those seconds, the next grouping (none, by
 *       clan, by relation).
 *   <li>{@code -Dsteinhud.tabSetAtSec=S:filter=2,sort=1,group=0|S:reset|...}: at those seconds,
 *       several options at once (filter, sort, group, radius, limit, columns, headerFooter=0/1,
 *       own=0/1) or back to the defaults, to check combinations.
 *   <li>{@code -Dsteinhud.tabSelfTestAtSec=N}: the self-test of every combination ({@link
 *       TabSelfTest}), with Tab held.
 * </ul>
 */
final class TabDiagnostics {

    private static final String[] FILTER_AT = split("steinhud.tabFilterAtSec", ",");
    private static final String[] GROUP_AT = split("steinhud.tabGroupAtSec", ",");
    private static final String[] SET_AT = split("steinhud.tabSetAtSec", "\\|");
    private static final long SELF_TEST_AT = Long.getLong("steinhud.tabSelfTestAtSec", -1L);

    private final SteinHud hud;
    private final Log log;
    private final long started = System.nanoTime();
    private int filterNext;
    private int groupNext;
    private int setNext;
    private boolean selfTested;

    TabDiagnostics(SteinHud hud) {
        this.hud = hud;
        this.log = hud.log();
    }

    private static String[] split(String property, String separator) {
        String value = System.getProperty(property, "");
        return value.isEmpty() ? new String[0] : value.split(separator);
    }

    /** Called with every Tab list request. */
    void tick(TabList list) {
        group();
        filter();
        set();
        if (SELF_TEST_AT >= 0
                && !selfTested
                && System.nanoTime() - started >= SELF_TEST_AT * 1_000_000_000L) {
            selfTested = true;
            TabSelfTest.run(hud, list, hud.config.tab);
        }
    }

    private boolean due(String seconds) {
        return System.nanoTime() - started >= (long) (Double.parseDouble(seconds) * 1e9);
    }

    private void filter() {
        if (filterNext >= FILTER_AT.length || !due(FILTER_AT[filterNext])) {
            return;
        }
        TabConfig t = hud.config.tab;
        t.filter = filterNext % TabEntries.FILTERS.length;
        filterNext++;
        log.info("tab list: show = " + t.filter + " (" + TabEntries.FILTERS[t.filter] + ")");
    }

    private void group() {
        if (groupNext >= GROUP_AT.length || !due(GROUP_AT[groupNext])) {
            return;
        }
        hud.config.tab.group = groupNext % 3;
        groupNext++;
        log.info("tab list: group = " + hud.config.tab.group);
    }

    private void set() {
        if (setNext >= SET_AT.length) {
            return;
        }
        String entry = SET_AT[setNext];
        int colon = entry.indexOf(':');
        if (!due(entry.substring(0, colon))) {
            return;
        }
        setNext++;
        TabConfig t = hud.config.tab;
        for (String kv : entry.substring(colon + 1).split(",")) {
            String[] p = kv.split("=");
            int v = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            switch (p[0].trim()) {
                case "reset" -> t.reset();
                case "filter" -> t.filter = v;
                case "sort" -> t.sort = v;
                case "group" -> t.group = v;
                case "radius" -> t.radius = v;
                case "limit" -> t.limit = v;
                case "columns" -> t.columns = v;
                case "headerFooter" -> t.headerFooter = v != 0;
                case "own" -> t.own = v != 0;
                default -> log.warn("tabSetAtSec: unknown option " + p[0]);
            }
        }
        t.repair();
        log.info(
                "tab list: "
                        + entry.substring(colon + 1)
                        + " -> filter "
                        + t.filter
                        + ", sort "
                        + t.sort
                        + ", group "
                        + t.group
                        + ", columns "
                        + t.columns);
    }
}
