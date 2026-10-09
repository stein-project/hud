/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.io.File;
import dev.xavier.stein.loader.api.Game;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.SteinMod;

/**
 * Stein HUD: HUD personalizavel — armadura, efeitos, direcao, FPS, 1% low, ping, CPU, GPU, RAM e inventario — na grade do HUD do Stein
 * Loader (Shift direito abre o editor, que e do Loader: la tambem ficam a action bar, a barra do chefe e o placar).
 *
 * <p>Refaz as funcoes do ArmorStatusHUD, do StatusEffectHUD e do DirectionHUD
 * (bspkrs) com codigo proprio: as opcoes de cada um viram opcoes do elemento, e o
 * alinhamento por deslocamento vira arrastar na grade.
 *
 * <p>Escrito com os nomes legiveis do MCP (os do SDK); o tools/build-mods.sh traduz para os do jogo.
 */
public final class SteinHud implements SteinMod {

    HudConfig config = new HudConfig();
    private final ModContext ctx = ModContext.of(this);
    private File configFile;
    private StatsBlock stats;
    private TabList tabList;
    private Minimap minimap;
    private TargetHud target;

    /** Pacote de texturas novo: o minimapa refaz as cores. */
    @Override
    public void onModelsReloaded() {
        if (minimap != null) {
            minimap.resourcesReloaded();
        }
    }

    /** Golpe seu num jogador: o elemento do alvo passa a mostrar ele. */
    @Override
    public boolean onAttackEntity(Object entity) {
        if (target != null) {
            target.hit(entity);
        }
        return false;
    }

    /** Cada clique que o jogo tratou no mundo vai para o CPS. */
    @Override
    public void onMouseInput() {
        if (stats != null && Keys.mousePressed()) {
            stats.click(Keys.mouseButton());
        }
    }

    @Override
    public void afterStartGame() {
        configFile = ctx.configFile();
        config = HudConfig.load(configFile);
        // O lugar de cada um vai para a grade do Loader; o que estava no steinhud.json vale na primeira vez.
        Hud.register(new ArmorStatus(config.armor, this));
        Hud.register(new StatusEffects(config.effects, this));
        Hud.register(new Direction(config.direction, this));
        stats = new StatsBlock(config.stats, this);
        Hud.register(stats);
        minimap = new Minimap(config.minimap, this);
        Hud.register(minimap);
        Hud.register(new InventoryView(config.inventory, this));
        target = new TargetHud(config.target, this);
        Hud.register(target);
        tabList = new TabList(config.tab);
        applyInventoryEffects();
        System.out.println("[mods] HUD iniciado (" + configFile + ")");
    }

    /** A lista de efeitos do inventario some quando o elemento de efeitos pede. */
    void applyInventoryEffects() {
        Game.setInventoryEffectsHidden(Hud.isEnabled("steinhud.effects") && config.effects.hideInventoryList);
    }

    void save() {
        if (configFile != null) {
            config.save(configFile);
        }
        applyInventoryEffects();
    }

    /**
     * {@code -Dsteinhud.tabGroupAtSec=20,30,40}: nos segundos dados, troca o agrupamento da lista do Tab (nenhum, por
     * cla, por relacao). E so para conferir os tres numa sessao so — num servidor de verdade, sair e entrar de novo
     * por causa de uma opcao custa uma reconexao a cada teste.
     */
    /**
     * {@code -Dsteinhud.tabFilterAtSec=20,26,32}: nos segundos dados, passa para o proximo filtro da lista do Tab. E
     * so para conferir todos numa sessao so: num servidor de verdade, sair e entrar por causa de uma opcao custa uma
     * reconexao a cada teste.
     */
    private static final String[] FILTER_AT = System.getProperty("steinhud.tabFilterAtSec", "").isEmpty()
            ? new String[0] : System.getProperty("steinhud.tabFilterAtSec").split(",");
    private int filterNext;

    private void filterTest() {
        if (filterNext >= FILTER_AT.length
                || System.nanoTime() - started < (long) (Double.parseDouble(FILTER_AT[filterNext]) * 1e9)) {
            return;
        }
        config.tab.filter = filterNext % TabList.FILTERS.length;
        filterNext++;
        System.out.println("[hud] lista do Tab: mostrar = " + config.tab.filter + " ("
                + TabList.FILTERS[config.tab.filter] + ")");
    }

    private static final String[] GROUP_AT = System.getProperty("steinhud.tabGroupAtSec", "").isEmpty()
            ? new String[0] : System.getProperty("steinhud.tabGroupAtSec").split(",");
    private final long started = System.nanoTime();
    private int groupNext;

    private void groupTest() {
        if (groupNext >= GROUP_AT.length
                || System.nanoTime() - started < (long) (Double.parseDouble(GROUP_AT[groupNext]) * 1e9)) {
            return;
        }
        config.tab.group = groupNext % 3;
        groupNext++;
        System.out.println("[hud] lista do Tab: agrupar = " + config.tab.group);
    }

    /**
     * {@code -Dsteinhud.tabSetAtSec=S:filter=2,sort=1,group=0|S:reset|...}: nos segundos dados, muda de uma vez as
     * opcoes da lista do Tab (filter, sort, group, radius, limit, columns, headerFooter=0/1) ou volta ao padrao. E para
     * conferir as combinacoes — varias opcoes ligadas juntas — numa sessao so, sem mexer no arquivo do jogador.
     */
    private static final String[] SET_AT = System.getProperty("steinhud.tabSetAtSec", "").isEmpty()
            ? new String[0] : System.getProperty("steinhud.tabSetAtSec").split("\\|");
    private int setNext;

    private void setTest() {
        if (setNext >= SET_AT.length) {
            return;
        }
        String entry = SET_AT[setNext];
        int colon = entry.indexOf(':');
        if (System.nanoTime() - started < (long) (Double.parseDouble(entry.substring(0, colon)) * 1e9)) {
            return;
        }
        setNext++;
        HudConfig.Tab t = config.tab;
        for (String kv : entry.substring(colon + 1).split(",")) {
            String[] p = kv.split("=");
            int v = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            switch (p[0].trim()) {
                case "reset" -> t.reset();
                case "filter" -> t.filter = v;
                case "sort" -> t.sort = v;
                case "group" -> t.group = v;
                case "radius" -> t.radius = v;
                case "limit" -> t.limit = v;
                case "columns" -> t.columns = v;
                case "headerFooter" -> t.headerFooter = v != 0;
                case "own" -> t.own = v != 0;
                default -> System.out.println("[hud] tabSetAtSec: opcao desconhecida " + p[0]);
            }
        }
        t.repair();
        System.out.println("[hud] lista do Tab: " + entry.substring(colon + 1) + " -> filtro " + t.filter + ", ordem "
                + t.sort + ", grupo " + t.group + ", colunas " + t.columns);
    }

    /** {@code -Dsteinhud.tabSelfTestAtSec=N}: o autoteste de todas as combinacoes (TabSelfTest), com o Tab segurado. */
    private static final long SELF_TEST_AT = Long.getLong("steinhud.tabSelfTestAtSec", -1L);
    private boolean selfTested;

    /**
     * A lista do Tab. <b>Nada e desenhado aqui</b>: o jogo desenha a lista no meio do HUD, antes dos elementos da
     * grade, e ela acabava embaixo deles — a lista e o que se le enquanto se segura o Tab, e nada pode ficar na
     * frente. Entao guardamos o pedido e desenhamos no {@link #onOverlayTop}, depois de tudo; e de la que sai tanto
     * a nossa quanto a do jogo (que passa a ser desenhada por nos, no mesmo lugar do desenho, so que por cima).
     */
    @Override
    public boolean onPlayerList(Object overlay, int width, Object scoreboard, Object objective) {
        groupTest();
        filterTest();
        setTest();
        if (SELF_TEST_AT >= 0 && !selfTested && tabList != null
                && System.nanoTime() - started >= SELF_TEST_AT * 1_000_000_000L) {
            selfTested = true;
            TabSelfTest.run(this, tabList, config.tab);
        }
        if (tabList == null) {
            return false;   // sem a nossa montada, o jogo desenha a dele como sempre
        }
        pendingWidth = width;
        pendingObjective = objective != null;
        return true;
    }

    /** O pedido da lista do Tab deste quadro: a largura da tela (-1 sem pedido) e se ha placar na lista. */
    private int pendingWidth = -1;
    private boolean pendingObjective;

    @Override
    public void onOverlayTop(float partialTicks) {
        int width = pendingWidth;
        pendingWidth = -1;
        if (width < 0) {
            return;
        }
        if (config.tab.own && tabList.draw(width, pendingObjective)) {
            return;
        }
        // "Do servidor", ou a nossa desistiu (sem conexao): a do jogo, agora por cima da grade. A regra do patcher
        // so troca a chamada de dentro do renderGameOverlay, entao a do Loader e a de verdade.
        Render.gameTabList(width);
    }


    /**
     * A pagina do HUD no Painel: a lista do Tab (o resto e por elemento, na pagina Tela). Tres secoes, cada uma com o
     * seu "Restaurar padrao" — e o padrao e o jeito do jogo: barras de ping, texto do servidor, ordem do servidor,
     * todos, sem grupos, 80 jogadores.
     */
    @Override
    public void onPage(Page page) {
        HudConfig.Tab t = config.tab;
        HudConfig.Tab d = HudConfig.defaults().tab;
        page.title(T.tr("tab.title")).icon("list");

        // Primeiro quem desenha a lista. Na do servidor sai exatamente o que ele manda — cabecalho, rodape, ordem —
        // e nenhuma opcao nossa vale, entao o resto da pagina nem aparece (o Painel remonta a cada clique).
        page.option(Option.cycle(T.tr("tab.list"), T.all("tab.list.server", "tab.list.own"), () -> t.own ? 1 : 0,
                i -> {
                    t.own = i == 1;
                    save();
                }));
        page.restore(() -> {
            t.own = d.own;
            save();
        });
        if (!t.own) {
            return;
        }

        page.section(T.tr("tab.look"));
        page.option(Option.cycle(T.tr("tab.ping"), TabList.names(TabList.PING_MODES), () -> t.ping, i -> {
            t.ping = i;
            save();
        }));
        page.option(Option.toggle(T.tr("tab.heads"), () -> t.heads, v -> {
            t.heads = v;
            save();
        }));
        page.option(Option.slider(T.tr("tab.transparency"), 0, 100, 5, () -> Math.round((1.0F - t.background) * 100.0F),
                v -> {
                    t.background = 1.0F - (float) v / 100.0F;
                    save();
                }, v -> Math.round(v) + "%"));
        page.option(Option.toggle(T.tr("tab.rowBackground"), () -> t.rowBackground, v -> {
            t.rowBackground = v;
            save();
        }));
        page.option(Option.cycle(T.tr("tab.font"), new String[] {T.tr("tab.font.stein"), T.tr("tab.font.game")},
                () -> t.gameFont ? 1 : 0, i -> {
                    t.gameFont = i == 1;
                    save();
                }));
        page.option(Option.cycle(T.tr("tab.nameColor"), TabList.names(TabList.NAME_COLORS), () -> t.nameColor, i -> {
            t.nameColor = i;
            save();
        }).advanced());
        page.option(Option.toggle(T.tr("tab.distance"), () -> t.distance, v -> {
            t.distance = v;
            save();
        }).advanced());
        page.option(Option.cycle(T.tr("tab.columns"), TabList.names(TabList.COLUMN_NAMES), () -> t.columns, i -> {
            t.columns = i;
            save();
        }).advanced());
        page.restore(() -> {
            t.ping = d.ping;
            t.heads = d.heads;
            t.nameColor = d.nameColor;
            t.distance = d.distance;
            t.columns = d.columns;
            t.background = d.background;
            t.rowBackground = d.rowBackground;
            t.gameFont = d.gameFont;
            save();
        });

        page.section(T.tr("tab.hide"));
        page.option(Option.toggle(T.tr("tab.headerFooter"), () -> !t.headerFooter, v -> {
            t.headerFooter = !v;
            save();
        }));
        page.option(Option.toggle(T.tr("tab.prefix"), () -> t.hidePrefix, v -> {
            t.hidePrefix = v;
            save();
        }).advanced());
        page.option(Option.toggle(T.tr("tab.suffix"), () -> t.hideSuffix, v -> {
            t.hideSuffix = v;
            save();
        }).advanced());
        page.restore(() -> {
            t.headerFooter = d.headerFooter;
            t.hidePrefix = d.hidePrefix;
            t.hideSuffix = d.hideSuffix;
            save();
        });

        page.section(T.tr("tab.order"));
        page.option(Option.cycle(T.tr("tab.show"), TabList.names(TabList.FILTERS), () -> t.filter, i -> {
            t.filter = i;
            save();
        }));
        if (t.filter == 5) {   // o raio so vale para "Perto": com outro filtro, some (o Painel remonta no clique)
            page.option(Option.cycle(T.tr("tab.radius"), TabList.radiusNames(), () -> t.radius, i -> {
                t.radius = i;
                save();
            }));
        }
        page.option(Option.cycle(T.tr("tab.sort"), TabList.names(TabList.SORTS), () -> t.sort, i -> {
            t.sort = i;
            save();
        }));
        page.option(Option.cycle(T.tr("tab.group"), TabList.names(TabList.GROUPS), () -> t.group, i -> {
            t.group = i;
            save();
        }));
        page.option(Option.cycle(T.tr("tab.limit"), TabList.LIMIT_NAMES, () -> t.limit, i -> {
            t.limit = i;
            save();
        }).advanced());
        page.restore(() -> {
            t.filter = d.filter;
            t.radius = d.radius;
            t.sort = d.sort;
            t.group = d.group;
            t.limit = d.limit;
            save();
        });
    }

    /** Configurar, no menu do Stein Loader: o editor da grade, voltando ao menu. */
    @Override
    public Object configScreen(Object parent) {
        return Hud.editorScreen(parent);
    }
}
