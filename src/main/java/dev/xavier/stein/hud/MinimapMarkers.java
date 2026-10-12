/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * What the minimap marks over the terrain: spawners, monsters, animals and players (always as a
 * dot, with relation colors), the player's arrow and the north.
 */
final class MinimapMarkers {

    private static final int HOSTILE = 0xFFAA00;
    private static final int PASSIVE = 0xD0D0D0;
    private static final int NEUTRAL = 0xFFFFFF;

    /** The arrow: tip, left, notch, right (the winding the game draws front-facing). */
    private static final float[] ARROW_X = {0, -3.5F, 0, 3.5F};

    private static final float[] ARROW_Y = {-5, 3.5F, 1.5F, 3.5F};

    /** {@code -Dsteinhud.minimap.debug=true}: every 2 s, the entities seen and those drawn. */
    private static final boolean DEBUG = Boolean.getBoolean("steinhud.minimap.debug");

    private final HudConfig.Minimap c;
    private final MinimapView view;
    private final Log log;
    private final List<Object> players = new ArrayList<>();
    private final float[] fx = new float[4];
    private final float[] fy = new float[4];
    private long debugAt;

    MinimapMarkers(HudConfig.Minimap c, MinimapView view, Log log) {
        this.c = c;
        this.view = view;
        this.log = log;
    }

    void spawners(float half) {
        Terrain.forEachSpawner(
                (bx, by, bz) -> {
                    view.toScreen(bx + 0.5 - view.px, bz + 0.5 - view.pz);
                    if (!view.inside(half)) {
                        return;
                    }
                    int x = Math.round(half + view.sx);
                    int y = Math.round(half + view.sy);
                    int a = view.fade(by + 0.5);
                    Render.rect(x - 3, y - 3, 6, 6, a << 24);
                    Render.rect(x - 2, y - 2, 4, 4, a << 24 | 0x9B3FD0);
                    Render.rect(x - 1, y - 2, 1, 4, a << 24 | 0x2A1438); // the cage's bars
                    Render.rect(x + 1, y - 2, 1, 4, a << 24 | 0x2A1438);
                });
    }

    void entities(float half, Object self, float partial) {
        players.clear();
        boolean debug = DEBUG && System.nanoTime() - debugAt > 2_000_000_000L;
        StringBuilder seen = debug ? new StringBuilder() : null;
        int drawn = 0;
        if (debug) {
            debugAt = System.nanoTime();
        }
        for (Object e : Entities.all()) {
            Entities.Kind kind = Entities.kind(e);
            if (debug && kind != Entities.Kind.PLAYER) {
                seen.append(Entities.typeName(e))
                        .append(Entities.isInvisible(e) ? "(invisible)" : "")
                        .append(kind == Entities.Kind.HOSTILE ? "/monster" : "")
                        .append(' ');
            }
            // First what does not depend on the entity being invisible (type and options): the
            // same ones come out in the same order, without reading the flags of those that would
            // not be drawn anyway (items on the ground, arrows, a type turned off). After the whole
            // world is drawn, that read is a cache miss per entity.
            int color;
            if (kind == Entities.Kind.PLAYER) {
                if (!c.players) {
                    continue;
                }
                color = 0;
            } else if (kind == Entities.Kind.HOSTILE) {
                if (c.entities == 2 || c.entities == 3) {
                    continue;
                }
                color = HOSTILE;
            } else if (kind == Entities.Kind.PASSIVE) {
                if (c.entities == 1 || c.entities == 3) {
                    continue;
                }
                color = PASSIVE;
            } else {
                continue;
            }
            if (e == self || Entities.isInvisible(e)) {
                continue;
            }
            if (kind == Entities.Kind.PLAYER) {
                players.add(e);
                continue;
            }
            if (!place(e, half, partial)) {
                continue;
            }
            int x = Math.round(half + view.sx);
            int y = Math.round(half + view.sy);
            int a = view.fade(Entities.y(e, 1));
            Render.rect(x - 2, y - 2, 4, 4, a << 24);
            Render.rect(x - 1, y - 1, 2, 2, a << 24 | color);
            drawn++;
        }
        if (debug) {
            log.info(
                    "minimap: "
                            + drawn
                            + " mob marker(s), "
                            + players.size()
                            + " player(s); seen: "
                            + seen);
        }
        // Players on top of everything, always as a dot (a PvP mode: faces would hinder reading).
        for (Object p : players) {
            if (!place(p, half, partial)) {
                continue;
            }
            int x = Math.round(half + view.sx);
            int y = Math.round(half + view.sy);
            int a = view.fade(Entities.y(p, 1));
            Render.rect(x - 2, y - 2, 5, 5, a << 24);
            Render.rect(x - 1, y - 1, 3, 3, a << 24 | playerColor(Entities.name(p)));
        }
    }

    /** The entity's interpolated position on screen (view.sx/sy); false outside the map. */
    private boolean place(Object e, float half, float partial) {
        view.toScreen(Entities.x(e, partial) - view.px, Entities.z(e, partial) - view.pz);
        return view.inside(half);
    }

    /**
     * The color the relations give this player (my clan, ally, enemy, no clan); without them,
     * white.
     */
    private int playerColor(String nick) {
        return c.combat ? RelationLink.color(nick) : NEUTRAL;
    }

    /** The player's arrow at the centre: up with the map turning; otherwise, where they face. */
    void arrow(float half, boolean live) {
        double phi = c.rotate || !live ? 0 : Math.atan2(view.sin, view.cos) + Math.PI;
        arrowShape(half, phi, 1.45F, 0xFF000000);
        arrowShape(half, phi, 1.0F, 0xFFFFFFFF);
    }

    private void arrowShape(float half, double phi, float k, int argb) {
        double cp = Math.cos(phi);
        double sp = Math.sin(phi);
        for (int i = 0; i < 4; i++) {
            double x = ARROW_X[i] * k;
            double y = ARROW_Y[i] * k;
            fx[i] = (float) (half + x * cp - y * sp);
            fy[i] = (float) (half + x * sp + y * cp);
        }
        Render.fan(fx, fy, 4, argb);
    }

    void north(float half, boolean live) {
        float nx;
        float ny;
        if (c.rotate && live) {
            view.toScreen(0, -view.bpp); // one pixel north
            float len = (float) Math.sqrt(view.sx * view.sx + view.sy * view.sy);
            nx = view.sx / len;
            ny = view.sy / len;
        } else {
            nx = 0;
            ny = -1;
        }
        float r = half - 5;
        if (c.shape == 0) {
            r = r / Math.max(Math.abs(nx), Math.abs(ny)); // on the square's edge
        }
        Render.text("N", half + nx * r - 2.5F, half + ny * r - 4, 0xFFFFFF, true);
    }
}
