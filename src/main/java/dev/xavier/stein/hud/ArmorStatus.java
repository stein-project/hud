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

import java.util.List;
import java.util.function.Predicate;

/**
 * Armor and the held item, with durability (what ArmorStatusHUD did): a horizontal or vertical
 * list, the item's name, the durability bar, how many of the held item there are (arrows for a
 * bow), durability as a value, "value/max" or a percentage, and colors by durability threshold
 * ({@link DamageColors}).
 *
 * <p>Drawn every frame, but its texts only change with the items: each entry keeps its text, count
 * and width, built again only when its stack, the stack's damage or size, the settings or the
 * inventory count changed. The count is a scan of the inventory by item identity, done at most once
 * per tick or slot change ({@link SteinHud#inventoryEpoch}).
 */
final class ArmorStatus extends HudElement {

    /** Four armor pieces and the held item. */
    private static final int MAX = 5;

    private final HudConfig.Armor c;
    private final DamageColors colors = new DamageColors();

    // The entries on screen, and what each one was built from.
    private final Object[] stacks = new Object[MAX];
    private final boolean[] armorOf = new boolean[MAX];
    private final int[] damageOf = new int[MAX];
    private final int[] sizeOf = new int[MAX];
    private final int[] countOf = new int[MAX];
    private final int[] inventoryOf = new int[MAX];
    private final String[] texts = new String[MAX];
    private final String[] counts = new String[MAX];
    private final int[] widths = new int[MAX];
    private int entries;

    // What every entry depends on: when any of it changes, all are built again.
    private Object builtSelf;
    private int builtEpoch = -1;
    private int builtFlags = -1;
    private String builtDisplay;
    private String builtThreshold;
    private String builtColors;
    private boolean rebuild;

    /** The item {@link #sameKind} counts (set around each count, so the predicate is made once). */
    private Object countKind;

    private final Predicate<Object> sameKind = x -> Stacks.sameKind(x, countKind);
    private Object bow;
    private Object arrow;
    private Object[] previewArmor;
    private Object previewSword;

    ArmorStatus(HudConfig.Armor c, SteinHud mod) {
        super("armor", c, mod);
        this.c = c;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        Object p = Entities.self();
        settings(p);
        int n = 0;
        if (p != null) {
            for (int i = Inventory.HELMET; i >= Inventory.BOOTS; i--) {
                Object piece = Inventory.armor(i);
                if (piece != null) {
                    put(n++, p, piece, true);
                }
            }
        }
        if (preview && n == 0) {
            for (Object s : previewArmor()) {
                put(n++, null, s, true);
            }
        }
        if (c.equippedItem) {
            Object held = p != null ? Inventory.heldItem() : null;
            if (held != null) {
                put(n++, p, held, false);
            } else if (preview) {
                previewArmor();
                put(n++, null, previewSword, false);
            }
        }
        for (int i = n; i < entries; i++) {
            stacks[i] = null; // no old stack kept alive by the cache
        }
        entries = n;
        if (n == 0) {
            return false;
        }
        if (c.horizontal) {
            int x = 0;
            for (int i = 0; i < n; i++) {
                x += 16 + widths[i] + (i < n - 1 ? 4 : 0);
            }
            w = x;
            h = 16;
        } else {
            int text = 0;
            for (int i = 0; i < n; i++) {
                text = Math.max(text, widths[i]);
            }
            w = 16 + text;
            h = n * 17 - 1;
        }
        return true;
    }

    /** Notes whether something every entry depends on changed since the last frame. */
    private void settings(Object p) {
        int flags =
                (c.itemName ? 1 : 0)
                        | (c.armorDamage ? 2 : 0)
                        | (c.itemDamage ? 4 : 0)
                        | (c.maxDamage ? 8 : 0)
                        | (c.itemCount ? 16 : 0);
        int epoch = textEpoch();
        rebuild =
                p != builtSelf
                        || epoch != builtEpoch
                        || flags != builtFlags
                        || c.damageDisplay != builtDisplay
                        || c.thresholdType != builtThreshold
                        || c.damageColors != builtColors;
        builtSelf = p;
        builtEpoch = epoch;
        builtFlags = flags;
        builtDisplay = c.damageDisplay;
        builtThreshold = c.thresholdType;
        builtColors = c.damageColors;
    }

    /** Entry {@code i} is {@code s}: built again only if something it shows changed. */
    private void put(int i, Object p, Object s, boolean armor) {
        int damage = Stacks.damage(s);
        int size = Stacks.count(s);
        boolean same =
                !rebuild
                        && stacks[i] == s
                        && armorOf[i] == armor
                        && damageOf[i] == damage
                        && sizeOf[i] == size;
        if (same && (armor || inventoryOf[i] == inventoryEpoch())) {
            return;
        }
        if (armor) {
            counts[i] = "";
        } else {
            // The count may have changed (or the entry is new): count again; when only the count
            // was due and it is the same, the entry stays as it is.
            int count = count(p, s);
            inventoryOf[i] = inventoryEpoch();
            if (same && countOf[i] == count) {
                return;
            }
            countOf[i] = count;
            counts[i] = count < 0 ? "" : String.valueOf(count);
        }
        stacks[i] = s;
        armorOf[i] = armor;
        damageOf[i] = damage;
        sizeOf[i] = size;
        String t = text(s, armor, damage);
        texts[i] = t;
        widths[i] = t.isEmpty() ? 0 : Render.textWidth(t) + 2;
    }

    private String text(Object s, boolean armor, int damage) {
        StringBuilder t = new StringBuilder();
        if (c.itemName) {
            t.append(Stacks.name(s));
        }
        if (Stacks.isDamageable(s)
                && (armor ? c.armorDamage : c.itemDamage)
                && !"none".equals(c.damageDisplay)) {
            int max = Stacks.maxDamage(s);
            int left = max - damage;
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
        return t.toString();
    }

    /**
     * How many of the held item are in the inventory (for a bow, the arrows); -1 when nothing is
     * shown. Compared by item identity ({@link Stacks#sameKind}), not by name.
     */
    private int count(Object p, Object s) {
        if (!c.itemCount) {
            return -1;
        }
        boolean isBow = isBow(s);
        if (p == null) {
            return isBow ? 64 : -1;
        }
        int n;
        if (isBow) {
            if (arrow == null) {
                return -1; // no arrow item to count: does not happen in the game
            }
            n = countOf(arrow);
        } else if (Stacks.maxCount(s) > 1) {
            n = countOf(s);
        } else {
            return -1;
        }
        return isBow || n > 1 ? n : -1;
    }

    private boolean isBow(Object s) {
        if (bow == null) {
            bow = Stacks.create("bow", 1, 0);
            arrow = Stacks.create("arrow", 1, 0);
        }
        return bow != null ? Stacks.sameKind(s, bow) : Stacks.is(s, "bow");
    }

    private int countOf(Object kind) {
        countKind = kind;
        try {
            return Inventory.count(sameKind);
        } finally {
            countKind = null;
        }
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
            for (int i = 0; i < entries; i++) {
                Render.item(stacks[i], x, 0, c.damageOverlay, counts[i]);
                String t = texts[i];
                if (!t.isEmpty()) {
                    Render.text(t, x + 18, 4, 0xFFFFFF, true);
                }
                x += 16 + widths[i] + 4;
            }
            return;
        }
        for (int i = 0; i < entries; i++) {
            int y = i * 17;
            String t = texts[i];
            Render.item(stacks[i], alignRight ? w - 16 : 0, y, c.damageOverlay, counts[i]);
            if (!t.isEmpty()) {
                Render.text(t, alignRight ? w - 16 - widths[i] : 18, y + 4, 0xFFFFFF, true);
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
