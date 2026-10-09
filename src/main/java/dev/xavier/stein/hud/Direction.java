/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Option;
import java.util.List;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Render;

/**
 * Bussola — as funcoes do DirectionHUD: faixa com os pontos cardeais que corre
 * com o olhar, marcador colorido no centro e estilos (o DirectionHUD trocava a
 * imagem da bussola; aqui a faixa e desenhada, sem textura).
 */
final class Direction extends HudElement {

    private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    /** Os formatos, na ordem da opcao; o 1 (a faixa sem fundo) sumiu quando o fundo passou a ser o da grade. */
    private static final String[] STYLES = {"direction.strip", "direction.text", "direction.degrees"};
    private static final int[] STYLE_VALUES = {1, 2, 3};
    /** Faixa: 180 graus em 121 px (centro exato), letras em cima, marcas embaixo. */
    private static final int STRIP_W = 121;
    private static final int STRIP_H = 20;

    private final HudConfig.Compass c;
    private float bearing;
    private String text;

    Direction(HudConfig.Compass c, SteinHud mod) {
        super("direction", c, mod);
        this.c = c;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        Object p = Entities.self();
        float yaw;
        if (p != null) {
            yaw = Entities.yaw(p, partialTicks);
        } else if (preview) {
            yaw = (System.currentTimeMillis() % 36_000L) / 100.0F; // gira devagar no editor
        } else {
            return false;
        }
        // Yaw do jogo: 0 = sul, 90 = oeste. Rumo da bussola: 0 = norte, 90 = leste.
        bearing = ((yaw + 180.0F) % 360.0F + 360.0F) % 360.0F;
        if (c.style == 2) {
            text = Option.fmt(c.markerColor) + LABELS[Math.round(bearing / 45.0F) % 8] + "§f " + (int) bearing + "°";
            w = Render.textWidth(text) + 6;
            h = 12;
        } else {
            w = STRIP_W;
            h = c.style == 3 ? STRIP_H + 11 : STRIP_H;
        }
        return true;
    }

    @Override
    void draw(boolean alignRight) {
        int marker = 0xFF000000 | Option.rgb(c.markerColor);
        if (c.style == 2) {
            Render.text(text, 3, 2, 0xFFFFFF, true);
            return;
        }
        int cx = STRIP_W / 2;
        float pxPerDegree = (STRIP_W / 2.0F) / 90.0F;
        for (int a = 0; a < 360; a += 15) {
            float off = wrap180(a - bearing);
            if (Math.abs(off) > 90.0F) {
                continue;
            }
            int px = cx + Math.round(off * pxPerDegree);
            boolean major = a % 45 == 0;
            Render.rect(px, major ? STRIP_H - 6 : STRIP_H - 3, 1, major ? 5 : 2, major ? 0xFFFFFFFF : 0x90FFFFFF);
            if (major) {
                String label = LABELS[a / 45];
                int lw = Render.textWidth(label);
                int lx = px - lw / 2;
                if (lx >= 1 && lx + lw <= STRIP_W - 1) {
                    Render.text(label, lx, 2, a % 90 == 0 ? 0xFFFFFF : 0xAAAAAA, true);
                }
            }
        }
        // Marcador: linha na faixa das marcas e ponta embaixo.
        Render.rect(cx, STRIP_H - 9, 1, 9, marker);
        Render.rect(cx - 1, STRIP_H - 2, 3, 2, marker);
        if (c.style == 3) {
            String d = (int) bearing + "°";
            Render.text(d, cx - Render.textWidth(d) / 2, STRIP_H + 2, 0xFFFFFF, true);
        }
    }

    /** O angulo entre -180 e 180 (o wrapAngleTo180 do jogo). */
    private static float wrap180(float a) {
        float r = a % 360.0F;
        if (r >= 180.0F) {
            r -= 360.0F;
        }
        if (r < -180.0F) {
            r += 360.0F;
        }
        return r;
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(Option.cycle(T.tr("style"), T.all(STYLES), () -> Math.max(0, c.style - 1), i -> c.style = STYLE_VALUES[i]));
        out.add(Option.color(T.tr("direction.marker"), () -> c.markerColor, v -> c.markerColor = v));
    }
}
