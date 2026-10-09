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
 * Aliados, inimigos e grupos pelo Loader (Relations e Players): quem alimenta e o Combat, ou qualquer outro mod — o HUD
 * nao conhece nenhum deles, so pergunta. Sem ninguem cadastrando nada, ninguem tem relacao e o minimapa pinta todos de
 * branco. Os nomes daqui sao os que o Tab e o minimapa ja usavam.
 */
final class CombatLink {

    static final int NONE = 0;
    static final int ALLY = 1;
    static final int CLAN = 2;
    static final int ENEMY = 3;
    static final int NEUTRAL = 4;

    private CombatLink() {
    }

    /** Alguem esta dizendo relacoes (o Combat ou outro mod)? */
    static boolean available() {
        return Relations.of(Players.self()) != Relation.NONE || Relations.version() > 0;
    }

    /** {@link #NONE}, {@link #ALLY}, {@link #CLAN}, {@link #ENEMY} ou {@link #NEUTRAL}. */
    static int relation(String nick) {
        return switch (Relations.of(nick)) {
            case GROUP -> CLAN;
            case ALLY -> ALLY;
            case ENEMY -> ENEMY;
            case NEUTRAL -> NEUTRAL;
            case NONE -> NONE;
            // Voce mesmo fica com os do seu grupo, como antes; sem grupo, neutro.
            case SELF -> Players.group(nick) != null ? CLAN : NEUTRAL;
        };
    }

    /** O grupo do jogador ("ABC"), ou null. */
    static String clan(String nick) {
        return Players.group(nick);
    }

    /** A cor do nick (RGB): a escolhida para a relacao dele (no Combat, ou em quem as define). */
    static int color(String nick) {
        return switch (relation(nick)) {
            case CLAN -> Relations.color(Relation.GROUP);
            case ALLY -> Relations.color(Relation.ALLY);
            case ENEMY -> Relations.color(Relation.ENEMY);
            case NEUTRAL -> Relations.color(Relation.NEUTRAL);
            default -> 0xFFFFFF;
        };
    }

    /** A cor dos aliados (RGB). */
    static int allyColor() {
        return Relations.color(Relation.ALLY);
    }

    /** A cor de cada relacao (RGB), para os titulos do Tab. */
    static int colorOf(int relation) {
        return switch (relation) {
            case CLAN -> Relations.color(Relation.GROUP);
            case ALLY -> Relations.color(Relation.ALLY);
            case ENEMY -> Relations.color(Relation.ENEMY);
            default -> Relations.color(Relation.NEUTRAL);
        };
    }
}
