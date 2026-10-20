/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Players;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is on our Tab list and in which order: the server's list, filtered, sorted, capped and, when
 * asked, grouped with a title and a count per group. Clan and relation come from the Loader's
 * Relations (fed by any mod that marks players).
 *
 * <p><b>What the client does not know:</b> which world each one on the list is in. The 1.8.9
 * protocol does not say, and on a network with several servers the list may hold people from
 * another server. The "nearby" filter solves it in practice: whoever has a loaded entity is, by
 * definition, in your world and within the range the server sends. Whoever is outside that shows no
 * distance ("--").
 */
final class TabEntries {

    // The names are translation keys (T.tr); the numbers of a cycle option are shown as they are.
    static final String[] PING_MODES = {"tab.bars", "tab.number", "tab.barsAndNumber"};
    static final String[] NAME_COLORS = {"tab.server", "tab.white", "tab.byRelation"};
    static final String[] FILTERS = {
        "tab.everyone", "tab.withClan", "tab.myClan", "tab.allies", "tab.enemies", "tab.nearby"
    };

    /** The index of "nearby" in {@link #FILTERS}: the only filter with a radius. */
    static final int FILTER_NEARBY = 5;

    static final String[] GROUPS = {"off", "tab.byClan", "tab.byRelation"};
    static final String[] SORTS = {"tab.server", "tab.ping", "tab.name", "tab.distance"};

    /** The index of "distance" in {@link #SORTS}. */
    static final int SORT_DISTANCE = 3;

    static final int[] RADIUS = {0, 16, 32, 64, 128, 256};

    /** Most players on the list; 80 is the game's and the default. */
    static final String[] LIMIT_NAMES = {"20", "40", "80", "150", "250", "500"};

    static final int[] LIMITS = {20, 40, 80, 150, 250, 500};

    /**
     * Each relation group's name, by RelationLink code (NONE never shows: every player has one).
     */
    static final String[] RELATION_NAMES = {
        "tab.noClan", "tab.allies", "tab.myClan", "tab.enemies", "tab.noClan"
    };

    /**
     * {@code -Dsteinhud.tabDebug=true}: a line per second with how many were kept out of how many
     * and my clan as read, and a line whenever my clan changes (to "none" too). That is how the
     * list falling back to the game's was seen on a real server.
     */
    private static final boolean DEBUG = Boolean.getBoolean("steinhud.tabDebug");

    /** The list is rebuilt at most once per tick (50 ms), or as soon as someone joins or leaves. */
    private static final long REBUILD_NS = 50_000_000L;

    /** One row: a player, or a group's title (or, with neither, the gap between groups). */
    static final class Line {
        Players.Entry player;
        String title;
        int count;
        String name = "";

        /**
         * What the order compares, read once when building: sorting compares each row many times.
         */
        String nick = "";

        String team = "";
        int ping;
        boolean spectator;
        int relation;
        String clan;

        /** Their clan's position when grouping by clan: 0 mine, 1 ally, 2 other, 3 no clan. */
        int clanRank;

        /** Distance in blocks, or -1 without a loaded entity (outside your world or range). */
        double distance = -1;
    }

    private final TabConfig c;
    private final Log log;

    /** The built list, rebuilt every tick: building reads the scoreboard and the world. */
    private List<Line> cached;

    private long cachedAt;
    private int cachedCount;

    /** How many players the server has on the list and how many passed the filter. */
    int total;

    int kept;

    private long debugAt;
    private String debugClan = "?";
    private int empties;

    TabEntries(TabConfig c, Log log) {
        this.c = c;
        this.log = log;
    }

    /** An option's names in the game's language: a key becomes text, a number stays as it is. */
    static String[] names(String[] keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = Character.isDigit(keys[i].charAt(0)) ? keys[i] : T.tr(keys[i]);
        }
        return out;
    }

    /** The radius names: no limit, then the blocks. */
    static String[] radiusNames() {
        String[] out = new String[RADIUS.length];
        for (int i = 0; i < RADIUS.length; i++) {
            out[i] = RADIUS[i] == 0 ? T.tr("tab.noLimit") : T.tr("tab.blocks", RADIUS[i]);
        }
        return out;
    }

    /**
     * This frame's rows. Building (formatting names, asking for clan and relation, measuring
     * distances, filtering, sorting, grouping) costs per player, and with Tab held that used to
     * happen every frame: with hundreds of players, that was what made Tab slow. The real list
     * changes at the game's pace, so it is rebuilt every 50 ms (a tick) or as soon as someone joins
     * or leaves.
     */
    List<Line> lines() {
        long now = System.nanoTime();
        int count = Players.count();
        if (cached == null || count != cachedCount || now - cachedAt > REBUILD_NS) {
            cached = collect();
            cachedAt = now;
            cachedCount = count;
        }
        return cached;
    }

    /** Builds the rows: filters, sorts and, when asked, groups with a title and a count. */
    List<Line> collect() {
        // A single pass over the loaded entities: looking each player up would be a scan per row.
        Map<String, Object> loaded = new HashMap<>();
        for (Object e : Entities.players()) {
            loaded.put(Entities.name(e), e);
        }
        Object self = Entities.self();
        // Where the server does not allow the players' distance it is not even measured.
        boolean distances = ServerLimits.allows(ServerLimits.DISTANCE);
        List<Line> out = new ArrayList<>();
        total = 0;
        for (Players.Entry p : Players.list()) {
            total++;
            Line l = new Line();
            l.player = p;
            l.name = name(p);
            String nick = p.nick;
            l.nick = nick;
            l.team = p.team;
            l.ping = p.ping;
            l.spectator = p.spectator;
            l.relation = RelationLink.relation(nick);
            l.clan = RelationLink.clan(nick);
            Object e = loaded.get(nick);
            if (distances && e != null && self != null) {
                l.distance = Entities.distance(e, self);
            }
            if (keep(l)) {
                out.add(l);
            }
        }
        kept = out.size();
        // Each clan's position when grouping by clan: mine, then the allies, then the others, and
        // no clan last. Per clan, not per player: an ally by name inside an enemy clan must not
        // split the group in two.
        Map<String, Integer> clanRank = new HashMap<>();
        for (Line l : out) {
            if (l.clan != null) {
                int r =
                        l.relation == RelationLink.CLAN
                                ? 0
                                : l.relation == RelationLink.ALLY ? 1 : 2;
                clanRank.merge(l.clan, r, Math::min);
            }
        }
        for (Line l : out) {
            l.clanRank = l.clan == null ? 3 : clanRank.get(l.clan);
        }
        boolean print = debug();
        out.sort(order());
        if (print) {
            StringBuilder b = new StringBuilder("tab: first:");
            for (int i = 0; i < Math.min(8, out.size()); i++) {
                Line l = out.get(i);
                b.append(' ')
                        .append(strip(l.name))
                        .append('/')
                        .append(l.ping)
                        .append("ms/")
                        .append(l.clan);
            }
            log.info(b.toString());
        }
        int limit = LIMITS[Math.max(0, Math.min(LIMITS.length - 1, c.limit))];
        if (out.size() > limit) {
            out =
                    new ArrayList<>(
                            out.subList(0, limit)); // cut after sorting: the cap drops the last
        }
        return c.group == 0 ? out : grouped(out);
    }

    private boolean debug() {
        if (!DEBUG) {
            return false;
        }
        String mine = Players.self() == null ? null : RelationLink.clan(Players.self());
        if (kept == 0) {
            empties++;
        }
        if (!String.valueOf(mine).equals(debugClan)) {
            log.info(
                    "tab: my clan "
                            + debugClan
                            + " -> "
                            + mine
                            + " ("
                            + kept
                            + " of "
                            + total
                            + ")");
            debugClan = String.valueOf(mine);
        }
        long now = System.nanoTime();
        if (now - debugAt > 1_000_000_000L) {
            debugAt = now;
            log.info(
                    "tab: filter "
                            + c.filter
                            + " sort "
                            + c.sort
                            + " group "
                            + c.group
                            + ": "
                            + kept
                            + " of "
                            + total
                            + ", my clan "
                            + mine
                            + ", empty in the last second "
                            + empties);
            empties = 0;
            return true;
        }
        return false;
    }

    /**
     * Who gets on the list. "Allies" includes my clan (whoever is in my clan is an ally before
     * anything); "enemies" are those in another clan with no alliance, not everyone who is not an
     * ally.
     */
    private boolean keep(Line l) {
        return switch (filter()) {
            case 1 -> l.clan != null;
            case 2 -> l.relation == RelationLink.CLAN;
            case 3 -> l.relation == RelationLink.CLAN || l.relation == RelationLink.ALLY;
            case 4 -> l.relation == RelationLink.ENEMY;
            case FILTER_NEARBY -> l.distance >= 0 && (radius() == 0 || l.distance <= radius());
            default -> true;
        };
    }

    /**
     * The filter in use: "nearby" becomes everyone on a server that does not allow the players'
     * distance. The player's choice is kept as it was, for when they leave.
     */
    int filter() {
        return c.filter == FILTER_NEARBY && !ServerLimits.allows(ServerLimits.DISTANCE)
                ? 0
                : c.filter;
    }

    /** The order in use: by distance becomes the game's where distance is not allowed. */
    int sort() {
        return c.sort == SORT_DISTANCE && !ServerLimits.allows(ServerLimits.DISTANCE) ? 0 : c.sort;
    }

    private int radius() {
        return RADIUS[Math.max(0, Math.min(RADIUS.length - 1, c.radius))];
    }

    /**
     * Inserts a title before each group, with how many it holds, and an empty row between one group
     * and the next: the columns run top to bottom, so without the gap a group starts glued to the
     * end of the previous one and the whole list looks like one thing (as seen on a real server,
     * with 400 players and dozens of clans).
     */
    private List<Line> grouped(List<Line> players) {
        List<Line> out = new ArrayList<>(players.size() + 4);
        String current = null;
        int start = -1;
        for (Line l : players) {
            String key = groupTitle(c.group, l.clan, l.relation);
            if (!key.equals(current)) {
                if (start >= 0) {
                    out.get(start).count = out.size() - start - 1;
                    out.add(new Line()); // empty row: the gap between one group and the next
                }
                Line title = new Line();
                title.title = key;
                title.relation = l.relation; // the title's color, grouped by relation
                start = out.size();
                out.add(title);
                current = key;
            }
            out.add(l);
        }
        if (start >= 0) {
            out.get(start).count = out.size() - start - 1;
        }
        return out;
    }

    /**
     * The rows' order. Grouped, the group comes first: my clan, allies, enemies, no clan.
     *
     * <p>"Name" is the <b>nick</b>, not the Tab text: the text starts with the prefix ("[FoF] ",
     * "[VIP] "), and sorting by it bunched people by clan or rank; with 1700 on the list it looked
     * unsorted. Ping 0 (the server has not measured yet) and those without a distance go last, not
     * first.
     */
    private Comparator<Line> order() {
        Comparator<Line> byName =
                Comparator.comparing((Line l) -> l.nick, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(l -> l.nick);
        Comparator<Line> base =
                switch (sort()) {
                    case 1 ->
                            Comparator.<Line>comparingInt(
                                            l -> l.ping <= 0 ? Integer.MAX_VALUE : l.ping)
                                    .thenComparing(byName);
                    case 2 -> byName;
                    case 3 ->
                            Comparator.<Line>comparingDouble(
                                            l -> l.distance < 0 ? Double.MAX_VALUE : l.distance)
                                    .thenComparing(byName);
                    // The game's (GuiPlayerTabOverlay.PlayerComparator): spectators last, then the
                    // team name, then the nick.
                    default ->
                            Comparator.<Line>comparingInt(l -> l.spectator ? 1 : 0)
                                    .thenComparing(l -> l.team)
                                    .thenComparing(l -> l.nick);
                };
        if (c.group == 1) {
            // My clan first and no clan last: with the cap cutting after sorting, in plain
            // alphabetical order a 1700-player server showed only the clans at the start of the
            // alphabet, and mine never appeared.
            return Comparator.<Line>comparingInt(l -> l.clanRank)
                    .thenComparing(l -> l.clan == null ? "" : l.clan)
                    .thenComparing(base);
        }
        if (c.group == 2) {
            return Comparator.<Line>comparingInt(
                            l ->
                                    switch (l.relation) {
                                        case RelationLink.CLAN -> 0;
                                        case RelationLink.ALLY -> 1;
                                        case RelationLink.ENEMY -> 2;
                                        default -> 3;
                                    })
                    .thenComparing(base);
        }
        return base;
    }

    /** A player's group title: the clan tag (or "no clan"), or the relation's name. */
    static String groupTitle(int group, String clan, int relation) {
        if (group == 1) {
            return clan == null ? T.tr("tab.noClan") : clan;
        }
        return T.tr(RELATION_NAMES[Math.max(0, Math.min(RELATION_NAMES.length - 1, relation))]);
    }

    /**
     * The name as the player will see it. The server builds "prefix + nick + suffix" (through the
     * scoreboard team, or sending the finished Tab name), and the two options remove one or the
     * other <b>only here</b>: what goes to and from the server stays the same, and the nick is
     * found inside the text so it works both ways.
     */
    String name(Players.Entry p) {
        String full = p.tabName;
        if (!c.hidePrefix && !c.hideSuffix) {
            return full;
        }
        String nick = p.nick;
        int i = full.indexOf(nick);
        if (i < 0) {
            return full; // the server replaced the whole nick: no prefix or suffix to split
        }
        String prefix = c.hidePrefix ? "" : full.substring(0, i);
        String suffix = c.hideSuffix ? "" : full.substring(i + nick.length());
        return prefix + nick + suffix;
    }

    static String strip(String s) {
        return s.replaceAll("(?i)§[0-9a-fk-or]", "");
    }
}
