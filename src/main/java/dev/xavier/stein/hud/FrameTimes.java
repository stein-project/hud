/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.util.Arrays;

/**
 * Frame durations for the performance element: the 1% low (the average of the slowest 1% of frames
 * over the last 10 s) and the worst frame of the last second.
 *
 * <p>Only frames of the game being played count: on the pause menu (which the game also opens when
 * the window loses focus) the Stein Bridge holds the frame rate down, so those frames are slow on
 * purpose, and counting them made the 1% low look like lag for the next 10 s ({@link #pause}).
 */
final class FrameTimes {

    private static final long SECOND = 1_000_000_000L;

    /** Frames kept for the 1% low (10 s of a very fast game). */
    private static final int FRAMES = 16384;

    private static final float WINDOW_MS = 10_000.0F;

    /** Each frame's duration in ms, in a ring, and the sorted copy of the computation. */
    private final float[] frames = new float[FRAMES];

    private final float[] sorted = new float[FRAMES];
    private int head;
    private int count;
    private long lastFrame;

    /**
     * The interval between this frame and the previous; hidden for more than a second, it starts
     * over.
     */
    void frame(long now) {
        if (lastFrame != 0) {
            long dt = now - lastFrame;
            if (dt < SECOND) {
                frames[head] = dt / 1_000_000.0F;
                head = (head + 1) % FRAMES;
                count = Math.min(count + 1, FRAMES);
            } else {
                count = 0;
            }
        }
        lastFrame = now;
    }

    /**
     * A frame that does not count (the game is paused). The next counted frame starts a new
     * interval, so the time spent paused never enters the window either.
     */
    void pause() {
        lastFrame = 0;
    }

    /** The worst (longest) frame of the last second, in ms; -1 with few frames. */
    float worst() {
        float total = 0;
        float max = -1;
        int k = 0;
        for (int i = 1; i <= count && total < 1000.0F; i++) {
            float f = frames[(head - i + FRAMES) % FRAMES];
            total += f;
            k++;
            if (f > max) {
                max = f;
            }
        }
        return k < 5 ? -1 : max;
    }

    /** The FPS of the slowest 1% of the window's frames; -1 with few frames. */
    int low() {
        int k = 0;
        float total = 0;
        for (int i = 1; i <= count && total < WINDOW_MS; i++) {
            float f = frames[(head - i + FRAMES) % FRAMES];
            sorted[k++] = f;
            total += f;
        }
        if (k < 20) {
            return -1;
        }
        Arrays.sort(sorted, 0, k);
        int n = Math.max(1, k / 100);
        float slow = 0;
        for (int i = k - n; i < k; i++) {
            slow += sorted[i];
        }
        return Math.round(1000.0F * n / slow);
    }
}
