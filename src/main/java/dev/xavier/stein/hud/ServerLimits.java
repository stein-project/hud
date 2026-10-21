/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Server;

/**
 * The HUD features a server may not allow, by the names the Stein registry lists them under ({@code
 * servers.json}): the minimap, the target element and the players' distance in the Tab list give
 * information the game itself would not, and servers like Hypixel forbid exactly that. While the
 * player is on such a server they are left off; the player's settings are never touched, so leaving
 * the server brings everything back.
 */
final class ServerLimits {

    /** The minimap, radar included. */
    static final String MINIMAP = "minimap";

    /** The target element: another player's health, armor, distance and held item. */
    static final String TARGET = "targetInfo";

    /** Distance in the Tab list: the column, the "nearby" filter and sorting by distance. */
    static final String DISTANCE = "playerDistance";

    private ServerLimits() {}

    /** Does the current server allow the feature? Cheap: once per frame or per element is fine. */
    static boolean allows(String feature) {
        return Server.allows(feature);
    }

    /**
     * A row for the options saying the feature is off on this server, and which one: shown only
     * while it is. A click does nothing (there is nothing the player can change there).
     */
    static Option note(String feature) {
        return new Option(T.tr("restricted")) {
            @Override
            public String value() {
                return T.tr("restricted.by", Server.restrictedBy(feature));
            }

            @Override
            public void click(boolean forward) {}
        }.visibleWhen(() -> !Server.allows(feature));
    }
}
