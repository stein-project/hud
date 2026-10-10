/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Lang;

/**
 * The HUD's texts in the game's language (assets/steinhud/lang): the keys are "steinhud." + the
 * short name.
 */
final class T {

    private T() {}

    static String tr(String key, Object... args) {
        return Lang.tr("steinhud." + key, args);
    }

    /** Several at once, for a cycle option's values. */
    static String[] all(String... keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = tr(keys[i]);
        }
        return out;
    }

    /**
     * A number already formatted with a dot (Locale.ROOT) to the language's separator: "1.5"
     * becomes "1,5" in pt and es.
     */
    static String num(String formatted) {
        String sep = tr("decimal");
        return ".".equals(sep) ? formatted : formatted.replace(".", sep);
    }
}
