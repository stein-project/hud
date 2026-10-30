/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

/**
 * The HUD page on the Panel: the Tab list (everything else is per element, on the Screen page).
 * Three sections, each with its own "Restore defaults", and the default is the game's way: ping
 * bars, the server's text, the server's order, everyone, no groups, 80 players.
 */
final class TabPage {

    private final TabConfig t;
    private final TabConfig d = HudConfigFile.defaults().tab;
    private final Runnable save;

    TabPage(TabConfig t, Runnable save) {
        this.t = t;
        this.save = save;
    }

    void build(Page page) {
        page.title(T.tr("tab.title")).icon("list");

        // First, who draws the list. The server's shows exactly what it sends (header, footer,
        // order) and none of our options apply, so the rest of the page is left out (the Panel
        // rebuilds on every click).
        page.option(
                Option.cycle(
                        T.tr("tab.list"),
                        T.all("tab.list.server", "tab.list.own"),
                        () -> t.own ? 1 : 0,
                        i -> {
                            t.own = i == 1;
                            save.run();
                        }));
        page.restore(
                () -> {
                    t.own = d.own;
                    save.run();
                });
        if (!t.own) {
            return;
        }
        look(page);
        hide(page);
        order(page);
    }

    private void look(Page page) {
        page.section(T.tr("tab.look"));
        page.option(
                Option.cycle(
                        T.tr("tab.ping"),
                        TabEntries.names(TabEntries.PING_MODES),
                        () -> t.ping,
                        i -> {
                            t.ping = i;
                            save.run();
                        }));
        page.option(
                Option.toggle(
                        T.tr("tab.heads"),
                        () -> t.heads,
                        v -> {
                            t.heads = v;
                            save.run();
                        }));
        page.option(
                Option.slider(
                        T.tr("tab.transparency"),
                        0,
                        100,
                        5,
                        () -> Math.round((1.0F - t.background) * 100.0F),
                        v -> {
                            t.background = 1.0F - (float) v / 100.0F;
                            save.run();
                        },
                        v -> Math.round(v) + "%"));
        page.option(
                Option.toggle(
                        T.tr("tab.rowBackground"),
                        () -> t.rowBackground,
                        v -> {
                            t.rowBackground = v;
                            save.run();
                        }));
        page.option(
                Option.cycle(
                        T.tr("tab.font"),
                        new String[] {T.tr("tab.font.stein"), T.tr("tab.font.game")},
                        () -> t.gameFont ? 1 : 0,
                        i -> {
                            t.gameFont = i == 1;
                            save.run();
                        }));
        page.option(
                Option.cycle(
                                T.tr("tab.nameColor"),
                                TabEntries.names(TabEntries.NAME_COLORS),
                                () -> t.nameColor,
                                i -> {
                                    t.nameColor = i;
                                    save.run();
                                })
                        .advanced());
        page.option(
                Option.cycle(
                                T.tr("tab.columns"),
                                TabEntries.names(TabLayout.COLUMN_NAMES),
                                () -> t.columns,
                                i -> {
                                    t.columns = i;
                                    save.run();
                                })
                        .advanced());
        page.restore(
                () -> {
                    t.ping = d.ping;
                    t.heads = d.heads;
                    t.nameColor = d.nameColor;
                    t.columns = d.columns;
                    t.background = d.background;
                    t.rowBackground = d.rowBackground;
                    t.gameFont = d.gameFont;
                    save.run();
                });
    }

    private void hide(Page page) {
        page.section(T.tr("tab.hide"));
        page.option(
                Option.toggle(
                        T.tr("tab.headerFooter"),
                        () -> !t.headerFooter,
                        v -> {
                            t.headerFooter = !v;
                            save.run();
                        }));
        page.option(
                Option.toggle(
                                T.tr("tab.prefix"),
                                () -> t.hidePrefix,
                                v -> {
                                    t.hidePrefix = v;
                                    save.run();
                                })
                        .advanced());
        page.option(
                Option.toggle(
                                T.tr("tab.suffix"),
                                () -> t.hideSuffix,
                                v -> {
                                    t.hideSuffix = v;
                                    save.run();
                                })
                        .advanced());
        page.restore(
                () -> {
                    t.headerFooter = d.headerFooter;
                    t.hidePrefix = d.hidePrefix;
                    t.hideSuffix = d.hideSuffix;
                    save.run();
                });
    }

    private void order(Page page) {
        page.section(T.tr("tab.order"));
        page.option(
                Option.cycle(
                        T.tr("tab.show"),
                        TabEntries.names(TabEntries.FILTERS),
                        () -> t.filter,
                        i -> {
                            t.filter = i;
                            save.run();
                        }));
        page.option(
                Option.cycle(
                        T.tr("tab.sort"),
                        TabEntries.names(TabEntries.SORTS),
                        () -> t.sort,
                        i -> {
                            t.sort = i;
                            save.run();
                        }));
        page.option(
                Option.cycle(
                        T.tr("tab.group"),
                        TabEntries.names(TabEntries.GROUPS),
                        () -> t.group,
                        i -> {
                            t.group = i;
                            save.run();
                        }));
        page.option(
                Option.cycle(
                                T.tr("tab.limit"),
                                TabEntries.LIMIT_NAMES,
                                () -> t.limit,
                                i -> {
                                    t.limit = i;
                                    save.run();
                                })
                        .advanced());
        page.restore(
                () -> {
                    t.filter = d.filter;
                    t.sort = d.sort;
                    t.group = d.group;
                    t.limit = d.limit;
                    save.run();
                });
    }
}
