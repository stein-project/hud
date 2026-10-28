/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Option;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * A block of the HUD on the Stein Loader's HUD grid: the Loader keeps the place, the scale and
 * whether it is on, and opens the editor; the content and each element's options live here.
 */
abstract class HudElement implements dev.xavier.stein.loader.api.HudElement {

    private final String id;
    private final String key;

    /**
     * The element's options. Place (anchor, offset, scale) and enabled moved to the Loader; its
     * fields here are the old version's, read once so the Loader keeps the place the player had.
     */
    final HudConfig.Placement cfg;

    private final SteinHud mod;

    /** Content size, without scale, computed in {@link #layout}. */
    int w;

    int h;

    HudElement(String key, HudConfig.Placement cfg, SteinHud mod) {
        this.id = "steinhud." + key;
        this.key = key;
        this.cfg = cfg;
        this.mod = mod;
    }

    // layout(preview, partialTicks), from the API: builds the content and sets w/h (preview: in the
    // editor, with a sample where data is missing).

    /** Draws at (0, 0), already translated and scaled. */
    abstract void draw(boolean alignRight);

    @Override
    public final void draw(boolean alignRight, float partialTicks) {
        draw(alignRight);
    }

    /** The element's own options, on the options screen. */
    abstract void addOptions(List<Option> out);

    final Log log() {
        return mod.log();
    }

    /** See {@link SteinHud#textEpoch}. */
    final int textEpoch() {
        return mod.textEpoch;
    }

    /** See {@link SteinHud#inventoryEpoch}. */
    final int inventoryEpoch() {
        return mod.inventoryEpoch;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String name() {
        return T.tr(key); // in the current language: switching it in game switches the name
    }

    @Override
    public final int width() {
        return w;
    }

    @Override
    public final int height() {
        return h;
    }

    @Override
    public final HudPlacement defaultPlacement() {
        // The factory place is resetPosition's, on a copy: the player's configuration stays as is.
        HudConfig.Placement p = cfg.fresh();
        return new HudPlacement(p.anchorX, p.anchorY, p.x, p.y, p.scale, p.enabled, p.showInChat);
    }

    @Override
    public final HudPlacement savedPlacement() {
        return new HudPlacement(
                cfg.anchorX, cfg.anchorY, cfg.x, cfg.y, cfg.scale, cfg.enabled, cfg.showInChat);
    }

    @Override
    public final void options(List<Option> out) {
        addOptions(out);
    }

    /**
     * The element's options back to the defaults: the fields of its configuration (the Placement
     * subclass) copied from a new one. Place, scale and enabled belong to the Loader, which
     * restores its own; the Placement fields stay as they are.
     */
    @Override
    public final void restoreOptions() {
        HudConfig.Placement fresh = HudConfigFile.defaultFor(cfg);
        for (Field f : cfg.getClass().getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m)) {
                continue;
            }
            try {
                f.setAccessible(true);
                f.set(cfg, f.get(fresh));
            } catch (ReflectiveOperationException e) {
                log().warn(id + ": could not restore " + f.getName() + ": " + e);
            }
        }
    }

    @Override
    public final void optionsChanged() {
        mod.save();
    }
}
