/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Option;

import java.util.ArrayList;
import java.util.List;

/**
 * The armor element's durability colors: a list like {@code "100,f; 80,7; 60,e"} of thresholds and
 * color codes. A color applies when the durability is at or below its threshold; above all of them,
 * white. Parsed again only when the text changes.
 */
final class DamageColors {

    /** The list already parsed (thresholds in decreasing order). */
    private String parsed;

    private int[] thresholds = new int[0];
    private String[] colors = new String[0];

    /** The color code of the smallest threshold the value reaches; above all of them, white. */
    String of(String list, int value) {
        if (!list.equals(parsed)) {
            parse(list);
        }
        String color = "f";
        for (int i = 0; i < thresholds.length; i++) {
            if (value <= thresholds[i]) {
                color = colors[i];
            }
        }
        return color;
    }

    /** "100,f; 80,7; ..." to thresholds in decreasing order; invalid pairs are ignored. */
    private void parse(String list) {
        parsed = list;
        List<int[]> pairs = new ArrayList<>();
        for (String part : list.split(";")) {
            String[] kv = part.trim().split(",");
            if (kv.length == 2) {
                try {
                    int limit = Integer.parseInt(kv[0].trim());
                    String code = kv[1].trim();
                    int index =
                            code.length() == 1
                                    ? Option.CODES.indexOf(Character.toLowerCase(code.charAt(0)))
                                    : -1;
                    if (index >= 0) {
                        pairs.add(new int[] {limit, index});
                    }
                } catch (NumberFormatException ignored) {
                    // pair ignored
                }
            }
        }
        pairs.sort((a, b) -> Integer.compare(b[0], a[0]));
        thresholds = new int[pairs.size()];
        colors = new String[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            thresholds[i] = pairs.get(i)[0];
            colors[i] = String.valueOf(Option.CODES.charAt(pairs.get(i)[1]));
        }
    }
}
