/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Terrain;

import java.util.List;

/**
 * A minimap in three modes: detailed (the terrain like Rei's Minimap: texture colors, relief,
 * shallow water, caves in the Nether and underground), simple (the game's map colors and strong
 * relief: the outline of things without as much detail) and radar (no map, only the markers).
 * Square or round, turning with the player or with north up. Shows players as dots (with relation
 * colors), monsters, animals and spawners ({@link MinimapMarkers}); what is far above or below
 * shows faded.
 */
final class Minimap extends HudElement {

    static final int[] SIZES = {64, 80, 96, 128};
    private static final String[] SIZE_NAMES = {"64", "80", "96", "128"};

    /** Blocks per pixel. */
    static final float[] ZOOMS = {0.5F, 1.0F, 2.0F, 4.0F};

    private static final String[] ZOOM_NAMES = {
        "minimap.zoom.near", "minimap.zoom.normal", "minimap.zoom.far", "minimap.zoom.veryFar"
    };
    private static final String[] MODES = {"minimap.detailed", "minimap.simple", "minimap.radar"};
    private static final String[] SHAPES = {"minimap.square", "minimap.round"};
    private static final String[] ENTITIES = {
        "minimap.all", "minimap.monsters", "minimap.animals", "minimap.none"
    };
    private static final int CIRCLE = 48;

    private final HudConfig.Minimap c;
    private final MapTexture map;
    private final MinimapView view = new MinimapView();
    private final MinimapMarkers markers;

    /** A fan's points (the round background, the map), reused from one frame to the next. */
    private final float[] fx = new float[CIRCLE + 2];

    private final float[] fy = new float[CIRCLE + 2];
    private final float[] fu = new float[CIRCLE + 2];
    private final float[] fv = new float[CIRCLE + 2];
    private Object world;
    private Object self;
    private int layer = -1;
    private float partial;
    private int size;
    private String coords;

    /** The player's coordinates text and the block it was built from. */
    private String coordsBuilt;

    private int coordsX;
    private int coordsY;
    private int coordsZ;

    Minimap(HudConfig.Minimap c, SteinHud mod) {
        super("minimap", c, mod);
        this.c = c;
        this.map = new MapTexture(mod.log());
        this.markers = new MinimapMarkers(c, view, mod.log());
    }

    /** A new resource pack: colors and the map come out again. */
    void resourcesReloaded() {
        map.reset(); // the Loader redoes the block colors by itself
    }

    private int sizeIndex() {
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] == c.size) {
                return i;
            }
        }
        return 1;
    }

    private int zoomIndex() {
        int best = 1;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (Math.abs(ZOOMS[i] - c.zoom) < Math.abs(ZOOMS[best] - c.zoom)) {
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        if (!preview && !ServerLimits.allows(ServerLimits.MINIMAP)) {
            return false; // this server does not allow minimaps (the player's choice stays as is)
        }
        self = Entities.self();
        world = Terrain.current();
        if ((self == null || world == null) && !preview) {
            return false;
        }
        size = SIZES[sizeIndex()];
        view.bpp = ZOOMS[zoomIndex()];
        view.rotate = c.rotate;
        view.round = c.shape == 1;
        partial = partialTicks;
        w = size;
        h = size + (c.coords ? 12 : 0);
        if (self == null || world == null) {
            coords = "0, 64, 0";
            return true;
        }
        view.px = Entities.x(self, partialTicks);
        view.py = Entities.y(self, partialTicks);
        view.pz = Entities.z(self, partialTicks);
        double yaw = Math.toRadians(Entities.yaw(self, partialTicks));
        view.cos = Math.cos(yaw);
        view.sin = Math.sin(yaw);
        // The text only changes when the block changes: no building the same string every frame.
        int bx = floor(view.px);
        int by = floor(view.py);
        int bz = floor(view.pz);
        if (coordsBuilt == null || bx != coordsX || by != coordsY || bz != coordsZ) {
            coordsX = bx;
            coordsY = by;
            coordsZ = bz;
            coordsBuilt = bx + ", " + by + ", " + bz;
        }
        coords = coordsBuilt;
        if (c.mode <= 1) {
            updateMap();
        }
        return true;
    }

    private void updateMap() {
        double px = view.px;
        double py = view.py;
        double pz = view.pz;
        int want = -1;
        if (c.caves
                && (!Terrain.hasSky()
                        || Terrain.skyLight(floor(px), floor(py + 1), floor(pz)) < 4)) {
            int feet = floor(py);
            // Half a block of slack: no re-reading on every step.
            want = layer >= 0 && Math.abs(feet - layer) < 2 ? layer : feet;
        }
        layer = want;
        double reach = size / 2.0 * view.bpp * (c.rotate && c.shape == 0 ? 1.42 : 1.0);
        int r = Math.min(30, (int) Math.ceil(reach / 16.0) + 1);
        map.update(world, floor(px) >> 4, floor(pz) >> 4, r, layer, c.mode == 1);
    }

    @Override
    void draw(boolean alignRight) {
        float half = size / 2.0F;
        boolean live = self != null && world != null;
        shape(half, half + 1, c.mode <= 1 ? 0xFF000000 : 0xA0000000);
        if (c.mode <= 1 && live) {
            drawMap(half);
        } else {
            Render.rect(1, (int) half, size - 2, 1, 0x30FFFFFF);
            Render.rect((int) half, 1, 1, size - 2, 0x30FFFFFF);
        }
        if (live) {
            if (c.spawners) {
                markers.spawners(half);
            }
            markers.entities(half, self, partial);
        }
        markers.arrow(half, self != null);
        markers.north(half, self != null);
        if (c.coords) {
            int tw = Render.textWidth(coords);
            Render.text(coords, half - tw / 2.0F, size + 3, 0xFFFFFF, true);
        }
    }

    /** A square or circle (by the shape option) of radius r around the centre, in one color. */
    private void shape(float half, float r, int argb) {
        if (c.shape == 1) {
            fx[0] = half;
            fy[0] = half;
            for (int i = 0; i <= CIRCLE; i++) {
                double ang = -2 * Math.PI * i / CIRCLE;
                fx[i + 1] = (float) (half + Math.cos(ang) * r);
                fy[i + 1] = (float) (half + Math.sin(ang) * r);
            }
            Render.fan(fx, fy, CIRCLE + 2, argb);
        } else {
            corners(half, r);
            Render.fan(fx, fy, 4, argb);
        }
    }

    /** The square's four corners of radius r, in the winding the game draws front-facing. */
    private void corners(float half, float r) {
        fx[0] = half - r;
        fy[0] = half + r;
        fx[1] = half + r;
        fy[1] = half + r;
        fx[2] = half + r;
        fy[2] = half - r;
        fx[3] = half - r;
        fy[3] = half - r;
    }

    private void drawMap(float half) {
        // Texture coordinates near zero (the texture repeats): far from the origin, a float would
        // lose the block.
        double baseX = Math.floor(view.px / MapTexture.SIZE) * MapTexture.SIZE;
        double baseZ = Math.floor(view.pz / MapTexture.SIZE) * MapTexture.SIZE;
        int n;
        if (c.shape == 1) {
            mapVertex(0, half, 0, 0, baseX, baseZ);
            for (int i = 0; i <= CIRCLE; i++) {
                double ang = -2 * Math.PI * i / CIRCLE;
                mapVertex(i + 1, half, Math.cos(ang) * half, Math.sin(ang) * half, baseX, baseZ);
            }
            n = CIRCLE + 2;
        } else {
            mapVertex(0, half, -half, half, baseX, baseZ);
            mapVertex(1, half, half, half, baseX, baseZ);
            mapVertex(2, half, half, -half, baseX, baseZ);
            mapVertex(3, half, -half, -half, baseX, baseZ);
            n = 4;
        }
        map.texture().drawFan(fx, fy, fu, fv, n, 0xFFFFFFFF);
    }

    /** Point i at (centre + ox, centre + oy), with the texture coordinate of the block there. */
    private void mapVertex(int i, float half, double ox, double oy, double baseX, double baseZ) {
        double dx;
        double dz;
        if (!c.rotate) {
            dx = ox * view.bpp;
            dz = oy * view.bpp;
        } else {
            dx = (-ox * view.cos + oy * view.sin) * view.bpp;
            dz = (-ox * view.sin - oy * view.cos) * view.bpp;
        }
        fx[i] = (float) (half + ox);
        fy[i] = (float) (half + oy);
        fu[i] = (float) ((view.px - baseX + dx) / MapTexture.SIZE);
        fv[i] = (float) ((view.pz - baseZ + dz) / MapTexture.SIZE);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(ServerLimits.note(ServerLimits.MINIMAP));
        out.add(
                Option.cycle(
                        T.tr("minimap.mode"),
                        T.all(MODES),
                        () -> Math.max(0, Math.min(2, c.mode)),
                        i -> c.mode = i));
        out.add(
                Option.cycle(
                        T.tr("minimap.shape"),
                        T.all(SHAPES),
                        () -> c.shape == 1 ? 1 : 0,
                        i -> c.shape = i));
        out.add(
                Option.cycle(
                        T.tr("minimap.size"), SIZE_NAMES, this::sizeIndex, i -> c.size = SIZES[i]));
        out.add(
                Option.cycle(
                        T.tr("minimap.zoom"),
                        T.all(ZOOM_NAMES),
                        this::zoomIndex,
                        i -> c.zoom = ZOOMS[i]));
        out.add(Option.toggle(T.tr("minimap.rotate"), () -> c.rotate, v -> c.rotate = v));
        out.add(Option.toggle(T.tr("minimap.players"), () -> c.players, v -> c.players = v));
        out.add(
                Option.cycle(
                        T.tr("minimap.entities"),
                        T.all(ENTITIES),
                        () -> Math.max(0, Math.min(3, c.entities)),
                        i -> c.entities = i));
        out.add(Option.toggle(T.tr("minimap.spawners"), () -> c.spawners, v -> c.spawners = v));
        out.add(Option.toggle(T.tr("minimap.relationColors"), () -> c.combat, v -> c.combat = v));
        out.add(Option.toggle(T.tr("minimap.coords"), () -> c.coords, v -> c.coords = v));
        out.add(Option.toggle(T.tr("minimap.caves"), () -> c.caves, v -> c.caves = v));
    }
}
