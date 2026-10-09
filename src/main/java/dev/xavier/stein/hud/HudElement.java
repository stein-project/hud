/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.util.List;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.HudPlacement;

/**
 * Um bloco do Stein HUD na grade do HUD do Stein Loader: o Loader guarda o lugar, a escala e se esta ligado, e abre o
 * editor; aqui ficam o conteudo e as opcoes de cada elemento.
 */
abstract class HudElement implements dev.xavier.stein.loader.api.HudElement {

    private final String id;
    private final String key;
    /**
     * Opcoes do elemento. O lugar (ancora, deslocamento, escala) e o ligado ficaram no Loader; os campos dele aqui
     * sao os da versao antiga, lidos uma vez para o Loader nao perder o lugar que o jogador ja tinha.
     */
    final HudConfig.Placement cfg;
    private final SteinHud mod;

    /** Tamanho do conteudo, sem escala, calculado em {@link #layout}. */
    int w;
    int h;

    HudElement(String key, HudConfig.Placement cfg, SteinHud mod) {
        this.id = "steinhud." + key;
        this.key = key;
        this.cfg = cfg;
        this.mod = mod;
    }

    // layout(preview, partialTicks), da API: monta o conteudo e define w/h (preview: no editor, com exemplo se faltar dado).

    /** Desenha em (0, 0), ja transladado e com escala. */
    abstract void draw(boolean alignRight);

    @Override
    public final void draw(boolean alignRight, float partialTicks) {
        draw(alignRight);
    }

    /** Opcoes proprias do elemento, na tela de opcoes. */
    abstract void addOptions(List<Option> out);

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String name() {
        return T.tr(key);   // no idioma de agora: trocar de idioma no jogo troca o nome
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
        // O lugar de fabrica e o do resetPosition, numa copia: a configuracao do jogador fica como esta.
        HudConfig.Placement p = cfg.fresh();
        return new HudPlacement(p.anchorX, p.anchorY, p.x, p.y, p.scale, p.enabled, p.showInChat);
    }

    @Override
    public final HudPlacement savedPlacement() {
        return new HudPlacement(cfg.anchorX, cfg.anchorY, cfg.x, cfg.y, cfg.scale, cfg.enabled, cfg.showInChat);
    }

    @Override
    public final void options(List<Option> out) {
        addOptions(out);
    }

    /**
     * As opcoes do elemento de volta ao padrao: os campos da configuracao dele (a subclasse de Placement) copiados de uma
     * nova. Lugar, escala e ligado sao do Loader, que restaura os dele; os campos de Placement ficam como estao.
     */
    @Override
    public final void restoreOptions() {
        HudConfig.Placement fresh = HudConfig.defaultFor(cfg);
        for (java.lang.reflect.Field f : cfg.getClass().getDeclaredFields()) {
            int m = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isFinal(m)) {
                continue;
            }
            try {
                f.setAccessible(true);
                f.set(cfg, f.get(fresh));
            } catch (ReflectiveOperationException e) {
                System.out.println("[hud] " + id + ": nao restaurou " + f.getName() + ": " + e);
            }
        }
    }

    @Override
    public final void optionsChanged() {
        mod.save();
    }
}
