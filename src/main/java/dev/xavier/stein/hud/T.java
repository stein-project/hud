/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Lang;

/** Textos do HUD no idioma do jogo (assets/steinhud/lang): as chaves sao "steinhud." + o nome curto. */
final class T {

    private T() {
    }

    static String tr(String key, Object... args) {
        return Lang.tr("steinhud." + key, args);
    }

    /** Varios de uma vez, para os valores de uma opcao de ciclo. */
    static String[] all(String... keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = tr(keys[i]);
        }
        return out;
    }

    /** Numero ja formatado com ponto (Locale.ROOT) passado para a virgula do idioma: "1.5" vira "1,5" em pt e es. */
    static String num(String formatted) {
        String sep = tr("decimal");
        return ".".equals(sep) ? formatted : formatted.replace(".", sep);
    }
}
