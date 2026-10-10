/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Log;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** The checks and failures of the Tab list self-test, grouped by kind for the log. */
final class SelfTestTally {

    static final String TAG = "Tab list self-test: ";

    int checks;
    int failures;

    /**
     * Failures by kind (the sentence without the combination or numbers), with the first example.
     */
    private final Map<String, Integer> kinds = new LinkedHashMap<>();

    private final Map<String, String> firstOfKind = new HashMap<>();

    void fail(String what) {
        failures++;
        String msg = what.contains("] ") ? what.substring(what.lastIndexOf("] ") + 2) : what;
        String kind = msg.replaceAll("[0-9.]+", "#").replaceAll("\"[^\"]*\"", "\"*\"");
        kind = kind.length() > 60 ? kind.substring(0, 60) : kind;
        if (kinds.merge(kind, 1, Integer::sum) == 1) {
            firstOfKind.put(kind, what);
        }
    }

    boolean check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fail(what);
        }
        return ok;
    }

    /** One line per kind of failure. */
    void report(Log log) {
        for (Map.Entry<String, Integer> k : kinds.entrySet()) {
            log.info(
                    TAG
                            + "FAILURE x"
                            + k.getValue()
                            + ": "
                            + k.getKey()
                            + " | e.g. "
                            + firstOfKind.get(k.getKey()));
        }
    }
}
