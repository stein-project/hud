/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;

/**
 * Allies, enemies and groups through the Loader (Relations and Players): any mod that marks players
 * feeds them; the HUD knows none of them, it only asks. With nobody registering anything, nobody
 * has a relation and the minimap paints everyone white. The names here are the ones the Tab list
 * and the minimap already used.
 */
final class RelationLink {

    static final int NONE = 0;
    static final int ALLY = 1;
    static final int CLAN = 2;
    static final int ENEMY = 3;
    static final int NEUTRAL = 4;

    private RelationLink() {}

    /** Is anyone telling relations (any mod)? */
    static boolean available() {
        return Relations.of(Players.self()) != Relation.NONE || Relations.version() > 0;
    }

    /** {@link #NONE}, {@link #ALLY}, {@link #CLAN}, {@link #ENEMY} or {@link #NEUTRAL}. */
    static int relation(String nick) {
        return switch (Relations.of(nick)) {
            case GROUP -> CLAN;
            case ALLY -> ALLY;
            case ENEMY -> ENEMY;
            case NEUTRAL -> NEUTRAL;
            case NONE -> NONE;
            // You yourself go with your group, as before; without a group, neutral.
            case SELF -> Players.group(nick) != null ? CLAN : NEUTRAL;
        };
    }

    /** The player's group ("ABC"), or null. */
    static String clan(String nick) {
        return Players.group(nick);
    }

    /**
     * The nick's color (RGB): the one chosen for their relation (by the mod that marks them, or
     * whoever defines them).
     */
    static int color(String nick) {
        return switch (relation(nick)) {
            case CLAN -> Relations.color(Relation.GROUP);
            case ALLY -> Relations.color(Relation.ALLY);
            case ENEMY -> Relations.color(Relation.ENEMY);
            case NEUTRAL -> Relations.color(Relation.NEUTRAL);
            default -> 0xFFFFFF;
        };
    }

    /** The allies' color (RGB). */
    static int allyColor() {
        return Relations.color(Relation.ALLY);
    }

    /** Each relation's color (RGB), for the Tab list's titles. */
    static int colorOf(int relation) {
        return switch (relation) {
            case CLAN -> Relations.color(Relation.GROUP);
            case ALLY -> Relations.color(Relation.ALLY);
            case ENEMY -> Relations.color(Relation.ENEMY);
            default -> Relations.color(Relation.NEUTRAL);
        };
    }
}
