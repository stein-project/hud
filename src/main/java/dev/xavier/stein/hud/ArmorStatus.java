/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Stacks;

import java.util.ArrayList;
import java.util.List;

/**
 * Armor and the held item, with durability (what ArmorStatusHUD did): a horizontal or vertical
 * list, the item's name, the durability bar, how many of the held item there are (arrows for a
 * bow), durability as a value, "value/max" or a percentage, and colors by durability threshold
 * ({@link DamageColors}).
 */
final class ArmorStatus extends HudElement {

    private final HudConfig.Armor c;
    private final DamageColors colors = new DamageColors();
    private final List<Object> stacks = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private final List<String> counts = new ArrayList<>();
    private Object[] previewArmor;
    private Object previewSword;

    ArmorStatus(HudConfig.Armor c, SteinHud mod) {
        super("armor", c, mod);
        this.c = c;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        stacks.clear();
        texts.clear();
        counts.clear();
        Object p = Entities.self();
        int armorPieces = 0;
        if (p != null) {
            for (int i = Inventory.HELMET; i >= Inventory.BOOTS; i--) {
                Object piece = Inventory.armor(i);
                if (piece != null) {
                    add(p, piece, true);
                    armorPieces++;
                }
            }
        }
        if (preview && armorPieces == 0) {
            for (Object s : previewArmor()) {
                add(null, s, true);
            }
        }
        if (c.equippedItem) {
            Object held = p != null ? Inventory.heldItem() : null;
            if (held != null) {
                add(p, held, false);
            } else if (preview) {
                previewArmor();
                add(null, previewSword, false);
            }
        }
        if (stacks.isEmpty()) {
            return false;
        }
        if (c.horizontal) {
            int x = 0;
            for (int i = 0; i < stacks.size(); i++) {
                x += 16 + textWidth(i) + (i < stacks.size() - 1 ? 4 : 0);
            }
            w = x;
            h = 16;
        } else {
            int text = 0;
            for (int i = 0; i < stacks.size(); i++) {
                text = Math.max(text, textWidth(i));
            }
            w = 16 + text;
            h = stacks.size() * 17 - 1;
        }
        return true;
    }

    /** An entry's text width, with the gap after the icon; 0 without text. */
    private int textWidth(int i) {
        String t = texts.get(i);
        return t.isEmpty() ? 0 : Render.textWidth(t) + 2;
    }

    private void add(Object p, Object s, boolean armor) {
        stacks.add(s);
        StringBuilder t = new StringBuilder();
        if (c.itemName) {
            t.append(Stacks.name(s));
        }
        if (Stacks.isDamageable(s)
                && (armor ? c.armorDamage : c.itemDamage)
                && !"none".equals(c.damageDisplay)) {
            int max = Stacks.maxDamage(s);
            int left = max - Stacks.damage(s);
            int percent = max > 0 ? left * 100 / max : 0;
            String d =
                    "percent".equals(c.damageDisplay)
                            ? percent + "%"
                            : c.maxDamage ? left + "/" + max : String.valueOf(left);
            if (t.length() > 0) {
                t.append(' ');
            }
            int value = "percent".equals(c.thresholdType) ? percent : left;
            t.append(Option.fmt(colors.of(c.damageColors, value))).append(d);
        }
        texts.add(t.toString());
        counts.add(armor ? "" : count(p, s));
    }

    /** How many of the held item are in the inventory; for a bow, the arrows. */
    private String count(Object p, Object s) {
        if (!c.itemCount) {
            return "";
        }
        boolean bow = Stacks.is(s, "bow");
        if (p == null) {
            return bow ? "64" : "";
        }
        int n;
        if (bow) {
            n = Inventory.count(x -> Stacks.is(x, "arrow"));
        } else if (Stacks.maxCount(s) > 1) {
            n = Inventory.count(x -> Stacks.sameKind(x, s));
        } else {
            return "";
        }
        return bow || n > 1 ? String.valueOf(n) : "";
    }

    private Object[] previewArmor() {
        if (previewArmor == null) {
            previewArmor =
                    new Object[] {
                        Stacks.create("diamond_helmet", 1, 40),
                        Stacks.create("diamond_chestplate", 1, 230),
                        Stacks.create("diamond_leggings", 1, 420),
                        Stacks.create("diamond_boots", 1, 380),
                    };
            previewSword = Stacks.create("diamond_sword", 1, 900);
        }
        return previewArmor;
    }

    @Override
    void draw(boolean alignRight) {
        if (c.horizontal) {
            int x = 0;
            for (int i = 0; i < stacks.size(); i++) {
                Render.item(stacks.get(i), x, 0, c.damageOverlay, counts.get(i));
                String t = texts.get(i);
                if (!t.isEmpty()) {
                    Render.text(t, x + 18, 4, 0xFFFFFF, true);
                }
                x += 16 + textWidth(i) + 4;
            }
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            int y = i * 17;
            String t = texts.get(i);
            Render.item(stacks.get(i), alignRight ? w - 16 : 0, y, c.damageOverlay, counts.get(i));
            if (!t.isEmpty()) {
                Render.text(
                        t, alignRight ? w - 18 - Render.textWidth(t) : 18, y + 4, 0xFFFFFF, true);
            }
        }
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(
                Option.cycle(
                        T.tr("layout"),
                        T.all("horizontal", "vertical"),
                        () -> c.horizontal ? 0 : 1,
                        i -> c.horizontal = i == 0));
        out.add(Option.toggle(T.tr("armor.held"), () -> c.equippedItem, v -> c.equippedItem = v));
        out.add(Option.toggle(T.tr("armor.itemName"), () -> c.itemName, v -> c.itemName = v));
        out.add(Option.toggle(T.tr("armor.bar"), () -> c.damageOverlay, v -> c.damageOverlay = v));
        out.add(Option.toggle(T.tr("count"), () -> c.itemCount, v -> c.itemCount = v));
        out.add(
                Option.toggle(
                        T.tr("armor.armorDurability"),
                        () -> c.armorDamage,
                        v -> c.armorDamage = v));
        out.add(
                Option.toggle(
                        T.tr("armor.itemDurability"), () -> c.itemDamage, v -> c.itemDamage = v));
        out.add(Option.toggle(T.tr("armor.max"), () -> c.maxDamage, v -> c.maxDamage = v));
        String[] display = {"value", "percent", "none"};
        out.add(
                Option.cycle(
                        T.tr("armor.display"),
                        T.all("value", "percent", "off"),
                        () -> Options.indexOf(display, c.damageDisplay),
                        i -> c.damageDisplay = display[i]));
        String[] type = {"value", "percent"};
        out.add(
                Option.cycle(
                        T.tr("armor.colorsBy"),
                        T.all("value", "percent"),
                        () -> Options.indexOf(type, c.thresholdType),
                        i -> c.thresholdType = type[i]));
        out.add(
                new Option.Text(
                        T.tr("armor.colors"),
                        "100,f; 50,e; 25,c",
                        () -> c.damageColors,
                        v -> c.damageColors = v));
    }
}
