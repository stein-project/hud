/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Option;
import java.util.List;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Inventario com ele fechado: as 27 casas do inventario principal na ordem da tela
 * do inventario e, se pedir, a hotbar embaixo. Itens como no inventario — icone,
 * barra de durabilidade e quantidade.
 */
final class InventoryView extends HudElement {

    /** Casa de 18 px: o icone de 16 e 1 px de cada lado, como na tela do inventario. */
    private static final int SLOT = 18;
    /** Espaco entre o inventario e a hotbar, como na tela do inventario. */
    private static final int HOTBAR_GAP = 4;

    private final HudConfig.Inventory c;
    private final Object[] shown = new Object[36];
    private int rows;
    private Object[] sample;

    InventoryView(HudConfig.Inventory c, SteinHud mod) {
        super("inventory", c, mod);
        this.c = c;
    }

    /** A hotbar e a bolsa (0-35), pelo Loader. */
    private static Object[] mainInventory() {
        Object[] out = new Object[Inventory.ARMOR_START];
        for (int i = 0; i < out.length; i++) {
            out[i] = Inventory.stack(i);
        }
        return out;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        Object[] inv = Entities.self() != null ? mainInventory() : null;
        if (inv == null) {
            if (!preview) {
                return false;
            }
            inv = sample();
        }
        rows = c.hotbar ? 4 : 3;
        boolean any = false;
        // Linhas de cima para baixo: casas 9-17, 18-26, 27-35 e a hotbar (0-8).
        for (int i = 0; i < 27; i++) {
            shown[i] = inv[9 + i];
            any |= shown[i] != null;
        }
        for (int i = 0; i < 9; i++) {
            shown[27 + i] = c.hotbar ? inv[i] : null;
            any |= shown[27 + i] != null;
        }
        if (!any && c.hideEmpty && !preview) {
            return false;
        }
        w = 9 * SLOT;
        h = rows * SLOT + (c.hotbar ? HOTBAR_GAP : 0);
        return true;
    }

    @Override
    void draw(boolean alignRight) {
        for (int i = 0; i < rows * 9; i++) {
            int x = (i % 9) * SLOT;
            int y = (i / 9) * SLOT + (i >= 27 ? HOTBAR_GAP : 0);
            if (c.background) {
                Render.rect(x + 1, y + 1, 16, 16, 0x30FFFFFF);
            }
            Object s = shown[i];
            if (s != null) {
                // Com a barra, o proprio desenho do inventario poe a quantidade (null) ou nada ("").
                String count = c.durability ? (c.count ? null : "")
                        : c.count && Stacks.count(s) > 1 ? String.valueOf(Stacks.count(s)) : "";
                Render.item(s, x + 1, y + 1, c.durability, count);
            }
        }
    }

    /** Exemplo para o editor fora de um mundo. */
    private Object[] sample() {
        if (sample == null) {
            sample = new Object[36];
            sample[9] = Stacks.create("diamond_sword", 1, 0);
            sample[10] = Stacks.create("bow", 1, 0);
            sample[11] = Stacks.create("arrow", 64, 0);
            sample[13] = Stacks.create("diamond_helmet", 1, 0);
            sample[19] = Stacks.create("arrow", 23, 0);
            sample[24] = Stacks.create("diamond_boots", 1, 0);
            sample[30] = Stacks.create("diamond_chestplate", 1, 0);
        }
        return sample;
    }

    @Override
    public float defaultBackground() {
        return 0.5F;
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(Option.toggle(T.tr("inventory.hotbar"), () -> c.hotbar, v -> c.hotbar = v));
        out.add(Option.toggle(T.tr("inventory.slots"), () -> c.background, v -> c.background = v));
        out.add(Option.toggle(T.tr("durability"), () -> c.durability, v -> c.durability = v));
        out.add(Option.toggle(T.tr("count"), () -> c.count, v -> c.count = v));
        out.add(Option.toggle(T.tr("inventory.hideEmpty"), () -> c.hideEmpty, v -> c.hideEmpty = v));
    }
}
