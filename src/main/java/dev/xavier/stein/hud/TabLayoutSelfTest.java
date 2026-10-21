/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Players;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The screen part of the Tab list self-test ({@link TabSelfTest}): for each list built, columns x
 * header x ping x heads x distance x four screen sizes, and the list must fit, "+N" must add up and
 * fixed columns must be kept.
 */
final class TabLayoutSelfTest {

    /** Four screens (in GUI pixels): 854x480 and 1920x1017 at GUI scales 2, 3 and 1. */
    private static final int[][] SCREENS = {{427, 240}, {960, 508}, {640, 339}, {1920, 1017}};

    private final SelfTestTally tally;

    /** Lists equal for drawing (same size, same name width) are checked once. */
    private final Set<String> laidOut = new HashSet<>();

    int layouts;

    TabLayoutSelfTest(SelfTestTally tally) {
        this.tally = tally;
    }

    void layouts(TabConfig c, List<TabEntries.Line> out, String cfg) {
        String empty = out.isEmpty() ? T.tr("tab.nobody") : null;
        int nameW = TabLayout.nameWidth(out, empty);
        boolean note = c.filter != 0;
        if (!laidOut.add(out.size() + "/" + nameW + "/" + note)) {
            return;
        }
        String header = Players.tabHeader();
        String footer = Players.tabFooter();
        TabConfig v = new TabConfig();
        v.copyFrom(c);
        for (int columns = 0; columns < TabLayout.COLUMN_NAMES.length; columns++) {
            for (int hf = 0; hf < 2; hf++) {
                for (int ping = 0; ping < TabEntries.PING_MODES.length; ping++) {
                    for (int look = 0; look < 4; look++) {
                        for (int[] screen : SCREENS) {
                            v.columns = columns;
                            v.headerFooter = hf == 1;
                            v.ping = ping;
                            v.heads = (look & 1) != 0;
                            v.distance = (look & 2) != 0;
                            layout(
                                    v,
                                    out,
                                    nameW,
                                    screen,
                                    v.headerFooter ? header : null,
                                    v.headerFooter ? footer : null,
                                    note,
                                    cfg);
                        }
                    }
                }
            }
        }
    }

    private void layout(
            TabConfig v,
            List<TabEntries.Line> out,
            int nameW,
            int[] screen,
            String header,
            String footer,
            boolean note,
            String cfg) {
        layouts++;
        TabLayout at =
                TabLayout.of(
                        out, nameW, 0, screen[0], screen[1], header, footer, note, v.distance, v);
        String where =
                cfg
                        + "[screen "
                        + screen[0]
                        + "x"
                        + screen[1]
                        + " columns "
                        + v.columns
                        + " header "
                        + v.headerFooter
                        + " ping "
                        + v.ping
                        + " heads "
                        + v.heads
                        + " distance "
                        + v.distance
                        + "] ";
        int size = out.size();
        tally.check(
                at.scale >= TabLayout.MIN_SCALE - 0.001F && at.scale <= 1.0001F,
                where + "scale " + at.scale);
        tally.check(
                at.columns >= 1 && at.rows >= 1,
                where + at.columns + " columns, " + at.rows + " rows");
        tally.check(
                at.shown == Math.min(size, at.rows * at.columns),
                where + "shows " + at.shown + " of " + size);
        tally.check(
                at.shown + at.hidden == size,
                where + at.shown + " + " + at.hidden + " hidden != " + size);
        tally.check(
                at.hidden == 0 || at.scale <= TabLayout.MIN_SCALE + 0.001F,
                where + at.hidden + " hidden at a scale " + at.scale + " that could still shrink");
        tally.check(
                at.bottom <= at.logicalH,
                where + "runs past the bottom: ends at " + at.bottom + " of " + at.logicalH);
        tally.check(
                at.totalW + 4 <= at.logicalW,
                where + "runs past the width: " + (at.totalW + 4) + " of " + at.logicalW);
        // Fixed columns: the ones asked for when they fit the width (at the smallest scale); when
        // not even there, as many as fit.
        int fixed = TabLayout.COLUMNS[v.columns];
        if (fixed > 0) {
            int room = Math.max(1, ((int) (screen[0] / TabLayout.MIN_SCALE) - 8) / (at.cellW + 5));
            int want = Math.min(Math.min(fixed, Math.max(1, size)), room);
            tally.check(
                    at.columns == want || at.hidden > 0 && at.columns == Math.min(fixed, room),
                    where
                            + "asked for "
                            + fixed
                            + " columns ("
                            + room
                            + " fit) and got "
                            + at.columns);
        }
    }
}
