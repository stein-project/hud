/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import static dev.xavier.stein.hud.SelfTestTally.TAG;

import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Panel part of the Tab list self-test ({@link TabSelfTest}): each option of the page clicked
 * through every value, forwards and backwards, and "Restore defaults" must bring the defaults back.
 */
final class TabPanelSelfTest {

    private final SelfTestTally tally;
    private final Log log;

    TabPanelSelfTest(SelfTestTally tally, Log log) {
        this.tally = tally;
        this.log = log;
    }

    void run(SteinHud hud, TabConfig c) {
        c.reset();
        // By default the game draws the list, and then the page is only the list choice and its
        // restore: none of our options apply, so none shows.
        Page server = new Page("HUD");
        hud.onPage(server);
        int shown = 0;
        for (Page.Entry e : server.entries()) {
            if (e.option != null) {
                shown++;
            }
        }
        tally.check(
                shown == 2,
                "Panel: with the server's list the page has "
                        + shown
                        + " rows, should be 2 (the choice and the restore)");
        c.own = true;
        Page page = new Page("HUD");
        hud.onPage(page);
        String defaults = new TabConfig().describe();
        int options = 0;
        List<Option> restores = new ArrayList<>();
        String restoreLabel = Lang.tr("steinloader.restore");
        for (Page.Entry e : page.entries()) {
            if (e.option == null) {
                continue;
            }
            Option o = e.option;
            if (o.label.equals(restoreLabel)) {
                restores.add(o);
                continue;
            }
            options++;
            cycle(o, c);
        }
        tally.check(options >= 13, "Panel: only " + options + " options on the list's page");
        if (tally.check(
                restores.size() == 4,
                "Panel: " + restores.size() + " restores, should be 4 (one per section)")) {
            tally.check(
                    !c.describe().equals(defaults),
                    "Panel: the options did not leave the defaults before Restore");
            for (Option r : restores) {
                String before = c.describe();
                r.click(true); // the first click only asks for confirmation
                tally.check(
                        c.describe().equals(before),
                        "Panel: restore changed something unconfirmed");
                r.click(true); // the second restores
            }
            tally.check(
                    c.describe().equals(defaults),
                    "Panel: Restore left " + c.describe() + ", the defaults are " + defaults);
        }
        log.info(
                TAG
                        + "Panel: "
                        + options
                        + " options clicked through every value, Restore "
                        + (c.describe().equals(defaults) ? "ok" : "FAILED"));
    }

    /** One option through all its values and back to the start; left off the default at the end. */
    private void cycle(Option o, TabConfig c) {
        String before = c.describe();
        String first = o.value();
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            String value = o.value();
            tally.check(
                    !value.equals("?"),
                    "Panel: \"" + o.label + "\" shows \"?\" (" + c.describe() + ")");
            values.add(value);
            TabConfig copy = new TabConfig();
            copy.copyFrom(c);
            copy.repair();
            tally.check(
                    copy.describe().equals(c.describe()),
                    "Panel: \"" + o.label + "\" left a value out of range: " + c.describe());
            o.click(true);
            if (o.value().equals(first)) {
                break;
            }
        }
        tally.check(
                o.value().equals(first) && c.describe().equals(before),
                "Panel: \"" + o.label + "\" does not come back to the start after every value");
        o.click(false);
        o.click(true);
        tally.check(
                c.describe().equals(before),
                "Panel: \"" + o.label + "\" backwards and forwards does not come back");
        tally.check(values.size() >= 2, "Panel: \"" + o.label + "\" does not change value");
        o.click(true); // off the default, so Restore has something to do
    }
}
