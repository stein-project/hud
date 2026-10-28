/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Active effects (what StatusEffectHUD did): icon, name with level, duration, name and duration
 * colors, icon and duration blinking in the last seconds, and taking the list out of the inventory.
 *
 * <p>Each row keeps its texts and widths: the name is translated again only when the effect or its
 * level changes, the duration once a second (it is written in whole seconds), and both when the
 * colors or the language change.
 */
final class StatusEffects extends HudElement {

    private final HudConfig.Effects c;
    private final List<Effects.Effect> list = new ArrayList<>();
    private List<Effects.Effect> preview;

    /** The editor's sample does not run the clock: its blinking follows the wall clock. */
    private boolean showingPreview;

    private int rowH;

    // Per row: what its texts were built from, the texts (with their colors) and their widths.
    private int[] rowId = new int[0];
    private int[] rowLevel = new int[0];
    private int[] rowSeconds = new int[0];
    private String[] rowName = new String[0];
    private String[] rowDuration = new String[0];
    private int[] rowNameW = new int[0];
    private int[] rowDurationW = new int[0];

    // What every row depends on.
    private int builtEpoch = -1;
    private String builtNameColor;
    private String builtDurationColor;

    StatusEffects(HudConfig.Effects c, SteinHud mod) {
        super("effects", c, mod);
        this.c = c;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        list.clear();
        Object p = Entities.self();
        if (p != null) {
            list.addAll(Effects.of(p));
        }
        showingPreview = list.isEmpty() && preview;
        if (showingPreview) {
            list.addAll(preview());
        }
        if (list.isEmpty()) {
            return false;
        }
        boolean all = rowsStale();
        int text = 0;
        for (int i = 0; i < list.size(); i++) {
            row(i, list.get(i), all);
            if (c.effectName) {
                text = Math.max(text, rowNameW[i]);
            }
            text = Math.max(text, rowDurationW[i]);
        }
        w = 20 + text;
        rowH = 20;
        h = list.size() * rowH - 2;
        return true;
    }

    /** True when every row must be built again (colors, language, font); makes room for them. */
    private boolean rowsStale() {
        int n = list.size();
        if (rowId.length < n) {
            int size = Math.max(n, 8);
            rowId = Arrays.copyOf(rowId, size);
            rowLevel = Arrays.copyOf(rowLevel, size);
            rowSeconds = Arrays.copyOf(rowSeconds, size);
            rowName = Arrays.copyOf(rowName, size);
            rowDuration = Arrays.copyOf(rowDuration, size);
            rowNameW = Arrays.copyOf(rowNameW, size);
            rowDurationW = Arrays.copyOf(rowDurationW, size);
            builtEpoch = -1; // the new rows have nothing yet
        }
        int epoch = textEpoch();
        boolean stale =
                epoch != builtEpoch
                        || c.nameColor != builtNameColor
                        || c.durationColor != builtDurationColor;
        builtEpoch = epoch;
        builtNameColor = c.nameColor;
        builtDurationColor = c.durationColor;
        return stale;
    }

    /** Row {@code i} shows {@code e}: its texts built again only where they changed. */
    private void row(int i, Effects.Effect e, boolean all) {
        if (all || rowName[i] == null || rowId[i] != e.id || rowLevel[i] != e.amplifier) {
            String name = Effects.name(e);
            rowName[i] = Option.fmt(c.nameColor) + name;
            rowNameW[i] = Render.textWidth(name);
            rowId[i] = e.id;
            rowLevel[i] = e.amplifier;
            all = true; // another effect in this row: its duration too
        }
        // The duration is written in whole seconds; -1 for an endless one.
        int seconds = e.permanent ? -1 : e.duration / 20;
        if (all || rowDuration[i] == null || rowSeconds[i] != seconds) {
            String duration = Effects.durationText(e);
            rowDuration[i] = Option.fmt(c.durationColor) + duration;
            rowDurationW[i] = Render.textWidth(duration);
            rowSeconds[i] = seconds;
        }
    }

    private List<Effects.Effect> preview() {
        if (preview == null) {
            preview = new ArrayList<>();
            preview.add(new Effects.Effect(1, 1, 90 * 20, false)); // Speed II, 1:30
            preview.add(new Effects.Effect(5, 0, 8 * 20, false)); // Strength, 0:08 (blinking)
            preview.add(new Effects.Effect(12, 0, 180 * 20, false)); // Fire Resistance, 3:00
        }
        return preview;
    }

    @Override
    void draw(boolean alignRight) {
        for (int i = 0; i < list.size(); i++) {
            Effects.Effect e = list.get(i);
            int y = i * rowH;
            int left = e.duration;
            boolean ending = c.blinkSeconds >= 0 && !e.permanent && left <= c.blinkSeconds * 20;
            // Half a second on, half off (10 ticks).
            boolean off =
                    ending
                            && (showingPreview
                                    ? System.currentTimeMillis() / 500L % 2 == 0
                                    : (left / 10) % 2 == 0);
            String name = c.effectName ? rowName[i] : null;

            int iconX = alignRight ? w - 18 : 0;
            if (!(c.iconBlink && off)) {
                Render.effectIcon(e.id, iconX, y);
            }
            int textLine = name != null ? y + 1 : y + 5;
            if (name != null) {
                Render.text(name, alignRight ? w - 20 - rowNameW[i] : 20, textLine, 0xFFFFFF, true);
            }
            if (!off) {
                int dy = name != null ? y + 10 : textLine;
                Render.text(
                        rowDuration[i],
                        alignRight ? w - 20 - rowDurationW[i] : 20,
                        dy,
                        0xFFFFFF,
                        true);
            }
        }
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(
                Option.toggle(
                        T.tr("effects.hideInventory"),
                        () -> c.hideInventoryList,
                        v -> c.hideInventoryList = v));
        out.add(Option.toggle(T.tr("effects.name"), () -> c.effectName, v -> c.effectName = v));
        out.add(Option.color(T.tr("effects.nameColor"), () -> c.nameColor, v -> c.nameColor = v));
        out.add(
                Option.color(
                        T.tr("effects.durationColor"),
                        () -> c.durationColor,
                        v -> c.durationColor = v));
        int[] seconds = {-1, 5, 10, 15, 20, 30, 60};
        String[] names = {T.tr("off"), "5 s", "10 s", "15 s", "20 s", "30 s", "60 s"};
        out.add(
                Option.cycle(
                        T.tr("effects.blink"),
                        names,
                        () -> {
                            for (int i = 0; i < seconds.length; i++) {
                                if (seconds[i] == c.blinkSeconds) {
                                    return i;
                                }
                            }
                            return 2;
                        },
                        i -> c.blinkSeconds = seconds[i]));
        // The icon only blinks at the end of the effect, so it comes after the blinking time.
        out.add(Option.toggle(T.tr("effects.iconBlink"), () -> c.iconBlink, v -> c.iconBlink = v));
    }
}
