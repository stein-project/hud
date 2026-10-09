/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Option;
import java.util.ArrayList;
import java.util.List;
import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Render;

/**
 * Efeitos ativos — as funcoes do StatusEffectHUD: icone, nome com nivel, duracao,
 * caixa de fundo como a do inventario, cores do nome e da duracao, icone e
 * duracao piscando nos ultimos segundos, e tirar a lista do inventario.
 */
final class StatusEffects extends HudElement {

    /** Caixa do inventario: 140 x 32 em (0, 166) da textura, uma a cada 33 px. */

    private final HudConfig.Effects c;
    private final List<Effects.Effect> list = new ArrayList<>();
    private List<Effects.Effect> preview;
    /** O exemplo do editor nao corre o tempo: o piscar dele segue o relogio. */
    private boolean showingPreview;
    private int rowH;

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
        int text = 0;
        for (Effects.Effect e : list) {
            if (c.effectName) {
                text = Math.max(text, Render.textWidth(Effects.name(e)));
            }
            text = Math.max(text, Render.textWidth(Effects.durationText(e)));
        }
        w = 20 + text;
        rowH = 20;
        h = list.size() * rowH - 2;
        return true;
    }

    private List<Effects.Effect> preview() {
        if (preview == null) {
            preview = new ArrayList<>();
            preview.add(new Effects.Effect(1, 1, 90 * 20, false));   // Velocidade II, 1:30
            preview.add(new Effects.Effect(5, 0, 8 * 20, false));    // Forca, 0:08 (piscando)
            preview.add(new Effects.Effect(12, 0, 180 * 20, false)); // Resistencia ao fogo, 3:00
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
            // Meio segundo aceso, meio apagado (10 ticks).
            boolean off = ending && (showingPreview ? System.currentTimeMillis() / 500L % 2 == 0 : (left / 10) % 2 == 0);
            String name = c.effectName ? Option.fmt(c.nameColor) + Effects.name(e) : null;
            String duration = Option.fmt(c.durationColor) + Effects.durationText(e);

            int iconX = alignRight ? w - 18 : 0;
            icon(e, iconX, y, c.iconBlink && off);
            int textLine = name != null ? y + 1 : y + 5;
            if (name != null) {
                Render.text(name, alignRight ? w - 20 - Render.textWidth(name) : 20, textLine, 0xFFFFFF, true);
            }
            if (!off) {
                int dy = name != null ? y + 10 : textLine;
                Render.text(duration, alignRight ? w - 20 - Render.textWidth(duration) : 20, dy, 0xFFFFFF, true);
            }
        }
    }

    private static void icon(Effects.Effect e, int x, int y, boolean hidden) {
        if (!hidden) {
            Render.effectIcon(e.id, x, y);
        }
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(Option.toggle(T.tr("effects.hideInventory"), () -> c.hideInventoryList, v -> c.hideInventoryList = v));
        out.add(Option.toggle(T.tr("effects.name"), () -> c.effectName, v -> c.effectName = v));
        out.add(Option.color(T.tr("effects.nameColor"), () -> c.nameColor, v -> c.nameColor = v));
        out.add(Option.color(T.tr("effects.durationColor"), () -> c.durationColor, v -> c.durationColor = v));
        int[] seconds = {-1, 5, 10, 15, 20, 30, 60};
        String[] names = {T.tr("off"), "5 s", "10 s", "15 s", "20 s", "30 s", "60 s"};
        out.add(Option.cycle(T.tr("effects.blink"), names,
                () -> {
                    for (int i = 0; i < seconds.length; i++) {
                        if (seconds[i] == c.blinkSeconds) {
                            return i;
                        }
                    }
                    return 2;
                },
                i -> c.blinkSeconds = seconds[i]));
        // O icone so pisca no fim do efeito, entao vem depois do tempo de piscar.
        out.add(Option.toggle(T.tr("effects.iconBlink"), () -> c.iconBlink, v -> c.iconBlink = v));
    }
}
