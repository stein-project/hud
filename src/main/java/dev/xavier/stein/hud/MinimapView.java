/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

/**
 * Where the minimap looks from, this frame: the player's position, the rotation and the zoom, and
 * the projection from world offsets to screen pixels shared by the map and its markers.
 */
final class MinimapView {

    double px;
    double py;
    double pz;

    /** Cosine and sine of the player's yaw. */
    double cos;

    double sin;

    /** Blocks per pixel. */
    float bpp;

    boolean rotate;
    boolean round;

    /** The last {@link #toScreen} result, in pixels from the centre. */
    float sx;

    float sy;

    /** A world offset (blocks) to the screen (pixels from the centre), into sx/sy. */
    void toScreen(double dx, double dz) {
        double x = dx / bpp;
        double z = dz / bpp;
        if (!rotate) {
            sx = (float) x;
            sy = (float) z;
        } else {
            // The player's front up: yaw 0 faces south (+z).
            sx = (float) (-cos * x - sin * z);
            sy = (float) (sin * x - cos * z);
        }
    }

    /** Whether the last {@link #toScreen} point is inside the map. */
    boolean inside(float half) {
        float m = half - 2;
        return round ? sx * sx + sy * sy <= m * m : Math.abs(sx) <= m && Math.abs(sy) <= m;
    }

    /** Alpha by height difference: what is far above or below shows faded. */
    int fade(double y) {
        return Math.abs(y - py) > 12 ? 0x70 : 0xFF;
    }
}
