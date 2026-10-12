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
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Stacks;

import net.minecraft.entity.EntityLivingBase;

import java.util.List;
import java.util.Locale;

/**
 * Target: what you need to know about whoever you are fighting ({@link TargetTracker} picks who).
 * Shows the head, the name in the relation's color, the distance, the health (the bar drops with
 * the lost part fading slowly), the armor and the held item, each with its durability.
 *
 * <p>The items are the ones the server sends for each player (the equipment packet carries the
 * whole item, with enchantments and damage), drawn by {@link Render#item}, the inventory's own
 * drawing: resource packs and CIT apply here too.
 */
final class TargetHud extends HudElement {

    private static final int HEAD = 24;
    private static final int BAR_W = 100;

    private final HudConfig.Target c;
    private final TargetTracker tracker;

    /** What is on screen now. */
    private Object target;

    private String nick = "";

    /** How much of the element shows (0 to 1): rises when coming in, falls when leaving. */
    private float shown;

    /** The health the bar shows (chasing the real one) and the lost part that fades slowly. */
    private float shownHealth = -1;

    private float trailHealth = -1;
    private long lastFrame;

    // Built in layout, drawn in draw.
    private Object drawn;
    private String drawnNick = "";
    private final Object[] items = new Object[5];
    private int itemCount;
    private float health;
    private float maxHealth;
    private float absorption;
    private float distance;
    private String loggedDamage = "";

    TargetHud(HudConfig.Target c, SteinHud mod) {
        super("target", c, mod);
        this.c = c;
        this.tracker = new TargetTracker(c, mod.log());
    }

    /** You hit someone (the attack click, before it goes to the server). */
    void hit(Object entity) {
        tracker.hit(entity);
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        Object next = tracker.choose(now);
        if (next != target) {
            target = next;
            if (next != null) {
                nick = Entities.name(next);
                shownHealth = -1;
                trailHealth = -1;
            }
        }
        boolean live = target != null;
        float want = live || preview ? 1 : 0;
        shown = c.animation ? approach(shown, want, dt * 6) : want;
        if (live) {
            drawn = target;
            drawnNick = nick;
        } else if (preview && shown >= 1) {
            drawn = null;
            drawnNick = Players.self() != null ? Players.self() : "Steve";
        }
        if (shown <= 0.01F && !preview) {
            drawn = null;
            return false;
        }
        collect(preview && !live);
        float goal = health + absorption;
        if (shownHealth < 0 || !c.animation) {
            shownHealth = goal;
            trailHealth = goal;
        } else {
            shownHealth =
                    approach(
                            shownHealth, goal, Math.max(1, Math.abs(goal - shownHealth)) * dt * 10);
            // The lost part waits a moment and falls slowly, so you can see how big the hit was.
            trailHealth =
                    trailHealth < shownHealth
                            ? shownHealth
                            : approach(trailHealth, shownHealth, dt * 6);
        }
        int right = Math.max(c.healthBar ? BAR_W : 0, itemCount * 18 - 2);
        right = Math.max(right, Render.textWidth(title()));
        w = (c.head ? HEAD + 4 : 0) + right;
        h = Math.max(c.head ? HEAD : 0, lines());
        return true;
    }

    /** The target's data (or a sample, in the grid editor). */
    private void collect(boolean sample) {
        itemCount = 0;
        if (sample || !(drawn instanceof EntityLivingBase l)) {
            health = 14.5F;
            maxHealth = 20;
            absorption = 4;
            distance = 3.2F;
            if (c.armor) {
                items[itemCount++] = Stacks.create("diamond_helmet", 1, 120);
                items[itemCount++] = Stacks.create("diamond_chestplate", 1, 300);
                items[itemCount++] = Stacks.create("iron_leggings", 1, 40);
                items[itemCount++] = Stacks.create("diamond_boots", 1, 410);
            }
            if (c.held) {
                items[itemCount++] = Stacks.create("diamond_sword", 1, 700);
            }
            return;
        }
        health = Entities.health(l);
        maxHealth = Math.max(1, Entities.maxHealth(l));
        absorption = Entities.absorption(l);
        Object me = Entities.self();
        distance = me != null ? Entities.distance(me, l) : 0;
        if (c.armor) {
            for (int slot = 4; slot >= 1; slot--) { // helmet down to boots
                Object s = l.getEquipmentInSlot(slot);
                if (s != null) {
                    items[itemCount++] = s;
                }
            }
        }
        if (c.held) {
            Object s = l.getEquipmentInSlot(0);
            if (s != null) {
                items[itemCount++] = s;
            }
        }
        if (TargetTracker.LOG) {
            logDamage();
        }
    }

    private void logDamage() {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < itemCount; i++) {
            d.append(Stacks.damage(items[i]))
                    .append('@')
                    .append(System.identityHashCode(items[i]))
                    .append(' ');
        }
        String now = d.toString();
        if (!now.equals(loggedDamage)) {
            log().info("target " + drawnNick + " wear: " + now);
            loggedDamage = now;
        }
    }

    private String title() {
        StringBuilder t = new StringBuilder();
        if (c.name) {
            t.append(drawnNick);
        }
        if (c.distance) {
            if (t.length() > 0) {
                t.append("  ");
            }
            t.append("§7").append(T.num(String.format(Locale.ROOT, "%.1f m", distance)));
        }
        return t.toString();
    }

    private int lines() {
        int y = 0;
        if (c.name || c.distance) {
            y += 10;
        }
        if (c.healthBar || c.healthText) {
            y += c.healthBar ? 9 : 10;
        }
        if (itemCount > 0) {
            y += 16 + (!"none".equals(c.durability) ? 7 : 0);
        }
        return y;
    }

    @Override
    void draw(boolean alignRight) {
        if (shown <= 0.01F) {
            return;
        }
        Render.push();
        if (shown < 1) {
            // Slides in and grows from the crosshair's side.
            float e = 1 - (1 - shown) * (1 - shown);
            Render.translate((1 - e) * (alignRight ? 12 : -12), h * (1 - e) * 0.5F);
            Render.scale(0.85F + 0.15F * e);
        }
        float a = shown;
        int x = 0;
        if (c.head) {
            Render.playerHead(drawnNick, alignRight ? w - HEAD : 0, 0, HEAD, true);
            if (!alignRight) {
                x = HEAD + 4;
            }
        }
        int y = 0;
        String title = title();
        if (!title.isEmpty()) {
            int color = c.name ? RelationLink.color(drawnNick) : 0xFFFFFF;
            Render.text(title, x, y + 1, Render.alpha(0xFF000000 | color, a), true);
            y += 10;
        }
        if (c.healthBar || c.healthText) {
            if (c.healthBar) {
                bar(x, y, a);
            }
            if (c.healthText) {
                healthText(x, y, a);
            }
            y += c.healthBar ? 9 : 10;
        }
        for (int i = 0; i < itemCount; i++) {
            int ix = x + i * 18;
            Object s = items[i];
            Render.item(s, ix, y, c.durabilityBar);
            String d = durability(s);
            if (!d.isEmpty()) {
                Render.push();
                Render.translate(ix + 8 - Render.textWidth(d) * 0.25F, y + 17);
                Render.scale(0.5F);
                Render.text(d, 0, 0, Render.alpha(0xFFFFFFFF, a), true);
                Render.pop();
            }
        }
        Render.pop();
    }

    private void healthText(int x, int y, float a) {
        String hp =
                T.num(String.format(Locale.ROOT, "%.1f", health))
                        + (absorption > 0
                                ? " §e+" + String.format(Locale.ROOT, "%.0f", absorption)
                                : "");
        if (c.healthBar) {
            // Small, inside the bar, against the right.
            Render.push();
            Render.translate(x + BAR_W - Render.textWidth(hp) * 0.5F - 1, y + 1);
            Render.scale(0.5F);
            Render.text(hp, 0, 0, Render.alpha(0xFFFFFFFF, a), true);
            Render.pop();
        } else {
            Render.text("§c" + hp, x, y + 1, Render.alpha(0xFFFFFFFF, a), true);
        }
    }

    /**
     * The health bar: the background, the lost part (light), health (green to red) and absorption
     * (gold).
     */
    private void bar(int x, int y, float a) {
        float total = maxHealth + Math.max(absorption, 0);
        Render.rect(x, y, BAR_W, 6, Render.alpha(0xFF1A1A1A, a * 0.8F));
        float trail = Math.min(1, trailHealth / total);
        float life = Math.min(1, Math.min(shownHealth, maxHealth) / total);
        float abs = Math.min(1, shownHealth / total) - life;
        Render.rect(x, y, BAR_W * trail, 6, Render.alpha(0xFFE8E0D0, a * 0.7F));
        float f = Math.min(1, health / maxHealth);
        int color =
                f > 0.5F
                        ? Render.mix(0xFFE0C030, 0xFF40C040, (f - 0.5F) * 2)
                        : Render.mix(0xFFD03030, 0xFFE0C030, f * 2);
        Render.rect(x, y, BAR_W * life, 6, Render.alpha(color, a));
        if (abs > 0) {
            Render.rect(x + BAR_W * life, y, BAR_W * abs, 6, Render.alpha(0xFFE8B020, a));
        }
    }

    private String durability(Object s) {
        if ("none".equals(c.durability) || !Stacks.isDamageable(s)) {
            return "";
        }
        int max = Stacks.maxDamage(s);
        int left = max - Stacks.damage(s);
        int percent = max > 0 ? left * 100 / max : 0;
        String color = percent > 60 ? "§a" : percent > 30 ? "§e" : percent > 15 ? "§6" : "§c";
        return color + ("percent".equals(c.durability) ? percent + "%" : String.valueOf(left));
    }

    private static float approach(float from, float to, float step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(Option.toggle(T.tr("target.aim"), () -> c.aim, v -> c.aim = v));
        out.add(
                Option.slider(
                        T.tr("target.range"),
                        3,
                        32,
                        1,
                        () -> c.range,
                        v -> c.range = (float) v,
                        v -> String.format(Locale.ROOT, "%.0f m", v)));
        out.add(
                Option.slider(
                        T.tr("target.seconds"),
                        1,
                        15,
                        0.5,
                        () -> c.seconds,
                        v -> c.seconds = (float) v,
                        v -> T.num(String.format(Locale.ROOT, "%.1f s", v))));
        out.add(
                Option.slider(
                        T.tr("target.switch"),
                        0,
                        5,
                        0.25,
                        () -> c.switchSeconds,
                        v -> c.switchSeconds = (float) v,
                        v -> T.num(String.format(Locale.ROOT, "%.2f s", v))));
        out.add(Option.toggle(T.tr("target.head"), () -> c.head, v -> c.head = v));
        out.add(Option.toggle(T.tr("target.name"), () -> c.name, v -> c.name = v));
        out.add(Option.toggle(T.tr("target.distance"), () -> c.distance, v -> c.distance = v));
        out.add(Option.toggle(T.tr("target.healthBar"), () -> c.healthBar, v -> c.healthBar = v));
        out.add(
                Option.toggle(
                        T.tr("target.healthText"), () -> c.healthText, v -> c.healthText = v));
        out.add(Option.toggle(T.tr("armor"), () -> c.armor, v -> c.armor = v));
        out.add(Option.toggle(T.tr("armor.held"), () -> c.held, v -> c.held = v));
        out.add(Option.toggle(T.tr("armor.bar"), () -> c.durabilityBar, v -> c.durabilityBar = v));
        String[] display = {"percent", "value", "none"};
        out.add(
                Option.cycle(
                        T.tr("armor.display"),
                        T.all("percent", "value", "off"),
                        () -> Options.indexOf(display, c.durability),
                        i -> c.durability = display[i]));
        out.add(Option.toggle(T.tr("target.animation"), () -> c.animation, v -> c.animation = v));
    }
}
