/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import static dev.xavier.stein.hud.SelfTestTally.TAG;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code -Dsteinhud.tabSelfTestAtSec=N}: N seconds after the game opens, with Tab held, runs the
 * Tab list through <b>every</b> combination of its settings, on the server's real list, and checks
 * each one by itself.
 *
 * <p>Who gets in, order, groups and cap: filter x radius x sort x group x cap x prefix x suffix,
 * each combination built by the list and compared with a count made here another way (the filter
 * again, player by player, straight from the relations; the order checked pair by pair by the key;
 * each group once, with the right count and in the right order). The screen: {@link
 * TabLayoutSelfTest}. The Panel: {@link TabPanelSelfTest}.
 *
 * <p>Everything runs in a single frame, on the game thread: no server packet changes the list in
 * the middle of the count. At the end, the player's configuration is back as it was. The result
 * goes to the log, lines "Tab list self-test".
 */
final class TabSelfTest {

    private final SelfTestTally tally = new SelfTestTally();
    private int lists;
    private final TabLayoutSelfTest screens = new TabLayoutSelfTest(tally);

    /**
     * A player of the server's list, read here on its own (the list takes no part in this count).
     */
    private static final class Ref {
        String nick;
        String clan;
        int relation;
        double distance = -1;
        int ping;
        String team;
        boolean spectator;
    }

    static void run(SteinHud hud, TabList list, TabConfig c) {
        new TabSelfTest().all(hud, list.entries(), c);
    }

    private void all(SteinHud hud, TabEntries list, TabConfig c) {
        Log log = hud.log();
        if (Players.self() == null) {
            log.info(TAG + "no connection, nothing to test");
            return;
        }
        TabConfig saved = new TabConfig();
        saved.copyFrom(c);
        long start = System.nanoTime();
        try {
            Map<String, Ref> refs = references();
            String mine = RelationLink.clan(Players.self());
            log.info(
                    TAG
                            + refs.size()
                            + " players on the list, relations "
                            + (RelationLink.available() ? "on" : "MISSING")
                            + ", my clan "
                            + mine);
            int[] perFilter = new int[TabEntries.FILTERS.length];
            for (int filter = 0; filter < TabEntries.FILTERS.length; filter++) {
                for (int radius = 0; radius < TabEntries.RADIUS.length; radius++) {
                    int expected = 0;
                    for (Ref r : refs.values()) {
                        expected += passes(filter, radius, r) ? 1 : 0;
                    }
                    if (radius == 0) {
                        perFilter[filter] = expected;
                    }
                    combinations(list, c, refs, mine, filter, radius, expected);
                }
            }
            StringBuilder counts = new StringBuilder();
            for (int i = 0; i < perFilter.length; i++) {
                counts.append(i == 0 ? "" : ", ")
                        .append(TabEntries.FILTERS[i])
                        .append(' ')
                        .append(perFilter[i]);
            }
            log.info(TAG + "how many pass each filter (radius: everything loaded): " + counts);
            new TabPanelSelfTest(tally, log).run(hud, c);
        } catch (Throwable t) {
            tally.fail("the self-test stopped with " + t);
            log.error(TAG + "stopped", t);
        } finally {
            c.copyFrom(saved);
            hud.save();
        }
        log.info(
                TAG
                        + lists
                        + " list combinations, "
                        + screens.layouts
                        + " screen ones, "
                        + tally.checks
                        + " checks, "
                        + tally.failures
                        + " failure(s), in "
                        + (System.nanoTime() - start) / 1_000_000L
                        + " ms");
        tally.report(log);
    }

    private void combinations(
            TabEntries list,
            TabConfig c,
            Map<String, Ref> refs,
            String mine,
            int filter,
            int radius,
            int expected) {
        for (int sort = 0; sort < TabEntries.SORTS.length; sort++) {
            for (int group = 0; group < TabEntries.GROUPS.length; group++) {
                for (int limit = 0; limit < TabEntries.LIMITS.length; limit++) {
                    for (int names = 0; names < 4; names++) {
                        c.reset();
                        c.filter = filter;
                        c.radius = radius;
                        c.sort = sort;
                        c.group = group;
                        c.limit = limit;
                        c.hidePrefix = (names & 1) != 0;
                        c.hideSuffix = (names & 2) != 0;
                        one(list, c, refs, mine, expected);
                    }
                }
            }
        }
    }

    // ---- the count made here ----

    private static Map<String, Ref> references() {
        Map<String, Object> loaded = new HashMap<>();
        for (Object e : Entities.players()) {
            loaded.put(Entities.name(e), e);
        }
        Object self = Entities.self();
        Map<String, Ref> out = new HashMap<>();
        for (Players.Entry p : Players.list()) {
            Ref r = new Ref();
            r.nick = p.nick;
            r.clan = RelationLink.clan(r.nick);
            r.relation = RelationLink.relation(r.nick);
            r.ping = p.ping;
            r.team = p.team;
            r.spectator = p.spectator;
            Object e = loaded.get(r.nick);
            if (e != null && self != null) {
                double dx = Entities.x(e, 1) - Entities.x(self, 1);
                double dy = Entities.y(e, 1) - Entities.y(self, 1);
                double dz = Entities.z(e, 1) - Entities.z(self, 1);
                r.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            out.put(r.nick, r);
        }
        return out;
    }

    /** The filter, written again from what each option promises on the Panel. */
    private static boolean passes(int filter, int radius, Ref r) {
        int max = TabEntries.RADIUS[radius];
        return switch (filter) {
            case 1 -> r.clan != null;
            case 2 -> r.relation == RelationLink.CLAN;
            case 3 -> r.relation == RelationLink.CLAN || r.relation == RelationLink.ALLY;
            case 4 -> r.relation == RelationLink.ENEMY;
            case 5 -> r.distance >= 0 && (max == 0 || r.distance <= max);
            default -> true;
        };
    }

    /** The group's key, as the title shows. */
    private static String groupKey(int group, Ref r) {
        return TabEntries.groupTitle(group, r.clan, r.relation);
    }

    /**
     * The group's expected position: by clan, mine, allies, the others, no clan; by relation, by
     * title.
     */
    private static int groupRank(int group, String key, Map<String, Integer> clanRanks) {
        if (group == 1) {
            return key.equals(T.tr("tab.noClan")) ? 3 : clanRanks.getOrDefault(key, 2);
        }
        if (key.equals(T.tr("tab.myClan"))) {
            return 0;
        }
        if (key.equals(T.tr("tab.allies"))) {
            return 1;
        }
        return key.equals(T.tr("tab.enemies")) ? 2 : 3;
    }

    /**
     * a before b (or tied) in the chosen order, checked by the key, without the list's Comparator.
     */
    private static boolean inOrder(int sort, Ref a, Ref b) {
        switch (sort) {
            case 1:
                {
                    long pa = a.ping <= 0 ? Long.MAX_VALUE : a.ping;
                    long pb = b.ping <= 0 ? Long.MAX_VALUE : b.ping;
                    return pa <= pb;
                }
            case 2:
                return a.nick.compareToIgnoreCase(b.nick) <= 0;
            case 3:
                {
                    double da = a.distance < 0 ? Double.MAX_VALUE : a.distance;
                    double db = b.distance < 0 ? Double.MAX_VALUE : b.distance;
                    return da <= db;
                }
            default:
                if (a.spectator != b.spectator) {
                    return !a.spectator;
                }
                int t = a.team.compareTo(b.team);
                return t < 0 || t == 0 && a.nick.compareTo(b.nick) <= 0;
        }
    }

    // ---- one combination ----

    private void one(
            TabEntries list, TabConfig c, Map<String, Ref> refs, String mine, int expected) {
        lists++;
        String cfg =
                "[filter "
                        + c.filter
                        + " radius "
                        + c.radius
                        + " sort "
                        + c.sort
                        + " group "
                        + c.group
                        + " limit "
                        + c.limit
                        + " prefix "
                        + c.hidePrefix
                        + " suffix "
                        + c.hideSuffix
                        + "] ";
        List<TabEntries.Line> out = list.collect();
        int limit = TabEntries.LIMITS[c.limit];
        tally.check(
                list.total == refs.size(),
                cfg + "total " + list.total + ", the server has " + refs.size());
        tally.check(
                list.kept == expected, cfg + list.kept + " passed, " + expected + " should have");

        List<TabEntries.Line> players = new ArrayList<>();
        for (TabEntries.Line l : out) {
            if (l.player != null) {
                players.add(l);
            }
        }
        tally.check(
                players.size() == Math.min(limit, expected),
                cfg + players.size() + " on the list, should be " + Math.min(limit, expected));

        // Everyone there passes the filter; nobody twice.
        Set<String> seen = new HashSet<>();
        for (TabEntries.Line l : players) {
            Ref r = refs.get(l.nick);
            if (!tally.check(r != null, cfg + l.nick + " is not on the server's list")) {
                return;
            }
            tally.check(seen.add(l.nick), cfg + l.nick + " shows twice");
            tally.check(
                    passes(c.filter, c.radius, r),
                    cfg
                            + l.nick
                            + " does not pass the filter (clan "
                            + r.clan
                            + ", relation "
                            + r.relation
                            + ", distance "
                            + r.distance
                            + ")");
            names(c, l, cfg);
        }

        if (c.group == 0) {
            ungrouped(c, refs, out, players, cfg);
        } else {
            grouped(c, refs, out, players, mine, cfg);
        }
        screens.layouts(c, out, cfg);
    }

    private void ungrouped(
            TabConfig c,
            Map<String, Ref> refs,
            List<TabEntries.Line> out,
            List<TabEntries.Line> players,
            String cfg) {
        for (TabEntries.Line l : out) {
            tally.check(l.player != null, cfg + "not grouped, but a title or empty row is there");
        }
        for (int i = 1; i < players.size(); i++) {
            Ref a = refs.get(players.get(i - 1).nick);
            Ref b = refs.get(players.get(i).nick);
            tally.check(
                    inOrder(c.sort, a, b), cfg + "out of order: " + a.nick + " before " + b.nick);
        }
    }

    /**
     * Groups: each title once, in the right order, with the right count and only its people below.
     */
    private void grouped(
            TabConfig c,
            Map<String, Ref> refs,
            List<TabEntries.Line> out,
            List<TabEntries.Line> players,
            String mine,
            String cfg) {
        Map<String, Integer> clanRanks = new HashMap<>();
        for (Ref r : refs.values()) {
            if (r.clan != null && passes(c.filter, c.radius, r)) {
                int rank =
                        r.relation == RelationLink.CLAN
                                ? 0
                                : r.relation == RelationLink.ALLY ? 1 : 2;
                clanRanks.merge(r.clan, rank, Math::min);
            }
        }
        Set<String> titles = new HashSet<>();
        String current = null;
        int lastRank = -1;
        int counted = 0;
        TabEntries.Line title = null;
        Ref previous = null;
        for (int i = 0; i < out.size(); i++) {
            TabEntries.Line l = out.get(i);
            if (l.player == null && l.title == null) {
                tally.check(
                        i > 0 && i + 1 < out.size() && out.get(i + 1).title != null,
                        cfg + "empty row out of place (position " + i + ")");
                continue;
            }
            if (l.title != null) {
                if (title != null) {
                    tally.check(
                            title.count == counted,
                            cfg
                                    + "group "
                                    + title.title
                                    + " says "
                                    + title.count
                                    + ", has "
                                    + counted);
                }
                tally.check(titles.add(l.title), cfg + "group " + l.title + " shows twice");
                int rank = groupRank(c.group, l.title, clanRanks);
                tally.check(rank >= lastRank, cfg + "group " + l.title + " out of order");
                lastRank = rank;
                title = l;
                current = l.title;
                counted = 0;
                previous = null;
                continue;
            }
            if (!tally.check(current != null, cfg + l.nick + " before the first title")) {
                break;
            }
            Ref r = refs.get(l.nick);
            tally.check(
                    groupKey(c.group, r).equals(current),
                    cfg
                            + l.nick
                            + " in group "
                            + current
                            + ", should be in "
                            + groupKey(c.group, r));
            if (previous != null) {
                tally.check(
                        inOrder(c.sort, previous, r),
                        cfg
                                + "out of order in group "
                                + current
                                + ": "
                                + previous.nick
                                + " before "
                                + r.nick);
            }
            previous = r;
            counted++;
        }
        if (title != null) {
            tally.check(
                    title.count == counted,
                    cfg + "group " + title.title + " says " + title.count + ", has " + counted);
        }
        // Grouped by clan, with people of my clan on the list, my clan is the first group.
        if (c.group == 1 && mine != null && !players.isEmpty() && players.get(0) != null) {
            boolean mineIn = false;
            for (TabEntries.Line l : players) {
                mineIn |= mine.equals(l.clan);
            }
            if (mineIn) {
                tally.check(
                        mine.equals(out.get(0).title),
                        cfg
                                + "my clan ("
                                + mine
                                + ") is not the first group, "
                                + out.get(0).title
                                + " is");
            }
        }
    }

    /** Prefix and suffix: hidden does not show, shown is still there. */
    private void names(TabConfig c, TabEntries.Line l, String cfg) {
        String full = l.player.tabName;
        int at = full.indexOf(l.nick);
        if (at < 0) {
            tally.check(
                    l.name.equals(full),
                    cfg + l.nick + ": name replaced by the server, and the list changed it");
            return;
        }
        String expected =
                (c.hidePrefix ? "" : full.substring(0, at))
                        + l.nick
                        + (c.hideSuffix ? "" : full.substring(at + l.nick.length()));
        tally.check(
                l.name.equals(expected),
                cfg
                        + l.nick
                        + ": name \""
                        + TabEntries.strip(l.name)
                        + "\", should be \""
                        + TabEntries.strip(expected)
                        + "\"");
    }
}
