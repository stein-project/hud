/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

/**
 * A mouse button's clicks in the last second. The list grows by itself: there is no cap on clicks
 * per second.
 */
final class ClickCounter {

    private static final long SECOND = 1_000_000_000L;

    private long[] times = new long[64];
    private int head;

    void add(long now) {
        // The place that would be overwritten is still within the last second: the list doubles
        // instead of losing clicks.
        if (times[head] != 0 && now - times[head] < SECOND) {
            long[] bigger = new long[times.length * 2];
            for (int i = 0; i < times.length; i++) {
                bigger[i] = times[(head + i) % times.length];
            }
            head = times.length;
            times = bigger;
        }
        times[head] = now;
        head = (head + 1) % times.length;
    }

    int count(long now) {
        int n = 0;
        for (long t : times) {
            if (t != 0 && now - t < SECOND) {
                n++;
            }
        }
        return n;
    }
}
