/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

import net.minecraft.client.gui.GuiScreen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The crosshair's section on the HUD page of the Panel: on or off, the editor (pattern and colors)
 * and how it behaves. The rest only shows with the crosshair on.
 */
final class CrosshairPage {

    private final SteinHud mod;

    CrosshairPage(SteinHud mod) {
        this.mod = mod;
    }

    void build(Page page) {
        HudConfig.Crosshair c = mod.config.crosshair;
        BooleanSupplier on = () -> mod.config.crosshair.enabled;
        page.section(T.tr("crosshair.title"));
        page.option(toggle("crosshair.enabled", () -> c.enabled, v -> c.enabled = v));
        page.screen(T.tr("crosshair.edit"), parent -> new CrosshairEditor(mod, (GuiScreen) parent));
        page.option(toggle("crosshair.invert", () -> c.invert, v -> c.invert = v).visibleWhen(on));
        page.option(
                toggle("crosshair.outline", () -> c.outline, v -> c.outline = v).visibleWhen(on));
        page.option(
                Option.slider(
                                T.tr("crosshair.scale"),
                                0,
                                CrosshairPattern.MAX_SCALE,
                                1,
                                () -> c.scale,
                                v -> {
                                    c.scale = (int) Math.round(v);
                                    mod.crosshairChanged();
                                },
                                v ->
                                        v < 0.5
                                                ? T.tr("crosshair.scale.gui")
                                                : T.tr("crosshair.scale.px", Math.round(v)))
                        .visibleWhen(on));
        page.option(
                toggle("crosshair.thirdPerson", () -> c.hideThirdPerson, v -> c.hideThirdPerson = v)
                        .visibleWhen(on));
        page.option(
                toggle("crosshair.screens", () -> c.hideInScreens, v -> c.hideInScreens = v)
                        .visibleWhen(on));
        page.restore(
                () -> {
                    // Everything but the favorites, which are the player's own work.
                    c.restoreLook(HudConfigFile.defaults().crosshair);
                    mod.crosshairChanged();
                });
    }

    private Option toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
        return Option.toggle(
                T.tr(key),
                get,
                v -> {
                    set.accept(v);
                    mod.crosshairChanged();
                });
    }
}
