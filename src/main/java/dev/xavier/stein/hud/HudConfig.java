/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Configuracao do Stein HUD (config/steinhud.json no diretorio do jogo).
 *
 * <p>Os padroes sao os da configuracao que o jogador usava nos mods originais
 * (armorstatushud.cfg, statuseffecthud.cfg, DirectionHUD.cfg): armadura
 * horizontal embaixo no centro (logo acima da barra de armadura do jogo); efeitos
 * no meio a esquerda; direcao em cima no centro. Os sem mod original: FPS e ping no
 * canto superior esquerdo, lado a lado, e o inventario, pequeno, no inferior direito.
 */
public final class HudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Grade do editor, em pixels de GUI; 0 = sem grade. */
    public int grid = 8;
    /** Encaixar na grade, nas bordas e no centro da tela ao arrastar. */
    public boolean snap = true;
    /** Esconde o HUD com a tela F3 aberta, como os mods originais. */
    public boolean hideWithDebug = true;
    public Armor armor = new Armor();
    public Effects effects = new Effects();
    public Compass direction = new Compass();
    public Inventory inventory = new Inventory();
    public Target target = new Target();
    public Stats stats = new Stats();
    public Minimap minimap = new Minimap();
    public Tab tab = new Tab();

    /**
     * A lista do Tab. Nao e um elemento da grade: ela e passageira e cobre a tela toda enquanto a tecla esta
     * descida, entao nao tem lugar nem vizinho — fica onde o jogo desenha a dele.
     */
    public static final class Tab {
        /**
         * Quem desenha a lista: false o jogo (o padrao) e true a nossa.
         *
         * <p>Com a do jogo, nada daqui vale — sai exatamente o que o servidor manda, cabecalho, rodape e ordem
         * inclusive. E o que o "Restaurar padrao" devolve: desligar o que fizemos, e nao imitar o jogo com as
         * nossas opcoes. Quem ja tinha configuracao continua com a nossa (HudConfig.load).
         */
        public boolean own = true;
        /** 0 barras (como o jogo, e o padrao: o restaurar volta ao jeito do jogo), 1 numero em ms, 2 os dois. */
        public int ping = 0;
        /** Cabecalho e rodape que o servidor manda. */
        public boolean headerFooter = true;
        /** Esconder o prefixo do nome (so na tela; o servidor continua mandando). */
        public boolean hidePrefix = false;
        /** Esconder o sufixo do nome (so na tela). */
        public boolean hideSuffix = false;
        /** 0 a cor do servidor, 1 branca, 2 pela relacao do Combat. */
        public int nameColor = 0;
        /** Cabeca do jogador antes do nome. */
        public boolean heads = true;
        /** Teto de jogadores na lista, na ordem de TabList.LIMITS (o do jogo, 80, e o padrao). */
        public int limit = 2;
        /** 0 time e nome (como o jogo), 1 ping, 2 nome, 3 distancia. */
        public int sort = 0;
        /** Quem entra, na ordem de TabList.FILTERS. */
        public int filter = 0;
        /** Raio do filtro "perto", na ordem de TabList.RADIUS (padrao 64 blocos). */
        public int radius = 3;
        /** 0 sem grupos, 1 por cla, 2 por relacao (meu cla, aliados, inimigos, sem cla). */
        public int group = 0;
        /** Distancia de cada um, para quem tem entidade carregada. */
        public boolean distance = false;
        /** Colunas, na ordem de TabList.COLUMN_NAMES: 0 automatico (as que couberem), 1 a 3 sao 2, 3 e 4 fixas. */
        public int columns = 0;
        /** Opacidade do fundo da lista, de 0 (sem fundo) a 1 (o jogador ajusta a transparencia no Painel). */
        public float background = 0.63F;
        /** A faixa atras de cada nome (o separador entre as linhas). */
        public boolean rowBackground = true;
        /** A fonte do proprio jogo no Tab, no lugar da do Stein. */
        public boolean gameFont = false;

        /**
         * Tudo de volta ao padrao, no mesmo objeto: a lista e as opcoes do Painel guardam esta instancia, entao
         * trocar por uma nova deixaria as duas olhando para a velha.
         */
        void reset() {
            copyFrom(new Tab());
        }

        /** Todos os campos de outra, no mesmo objeto (o autoteste guarda e devolve a configuracao do jogador). */
        void copyFrom(Tab d) {
            own = d.own;
            ping = d.ping;
            headerFooter = d.headerFooter;
            hidePrefix = d.hidePrefix;
            hideSuffix = d.hideSuffix;
            nameColor = d.nameColor;
            heads = d.heads;
            limit = d.limit;
            sort = d.sort;
            filter = d.filter;
            radius = d.radius;
            group = d.group;
            distance = d.distance;
            columns = d.columns;
            background = d.background;
            rowBackground = d.rowBackground;
            gameFont = d.gameFont;
        }

        /** Os campos como texto, para comparar duas configuracoes e para o log. */
        String describe() {
            return "propria=" + own + " ping=" + ping + " cabecalho=" + headerFooter + " prefixo=" + hidePrefix
                    + " sufixo=" + hideSuffix + " cor=" + nameColor + " cabecas=" + heads + " teto=" + limit
                    + " ordem=" + sort + " filtro=" + filter + " raio=" + radius + " grupo=" + group
                    + " distancia=" + distance + " colunas=" + columns + " fundo=" + background + " faixas=" + rowBackground + " fonteJogo=" + gameFont;
        }

        /** Indices fora da faixa (arquivo editado a mao, versao antiga) voltam ao padrao de cada um. */
        void repair() {
            Tab d = new Tab();
            ping = in(ping, 3, d.ping);
            nameColor = in(nameColor, 3, d.nameColor);
            limit = in(limit, TabList.LIMITS.length, d.limit);
            sort = in(sort, TabList.SORTS.length, d.sort);
            filter = in(filter, TabList.FILTERS.length, d.filter);
            radius = in(radius, TabList.RADIUS.length, d.radius);
            group = in(group, TabList.GROUPS.length, d.group);
            columns = in(columns, TabList.COLUMN_NAMES.length, d.columns);
        }

        private static int in(int v, int n, int fallback) {
            return v >= 0 && v < n ? v : fallback;
        }
    }

    /** Posicao e opcoes comuns de um elemento. */
    public abstract static class Placement {
        public boolean enabled = true;
        /**
         * Borda da tela que o elemento acompanha: 0 = esquerda/topo, 1 = centro,
         * 2 = direita/base. Muda sozinha ao arrastar, pelo terco da tela em que o
         * centro do elemento fica; mantem o elemento no lugar quando a janela ou a
         * escala da GUI mudam.
         */
        public int anchorX;
        public int anchorY;
        /** Deslocamento da borda correspondente da caixa (ou do centro) ate a ancora, em pixels de GUI. */
        public int x;
        public int y;
        public float scale = 1.0F;
        public boolean showInChat;

        /** Posicao padrao do elemento. */
        abstract void resetPosition();

        /** Um novo com os padroes (a posicao de fabrica, para a grade do Loader). */
        abstract Placement fresh();
    }

    /** ArmorStatusHUD: armadura e item na mao, com durabilidade. */
    public static final class Armor extends Placement {
        public boolean horizontal = true;
        public boolean itemName = false;
        /** Barra de durabilidade do inventario sobre o icone. */
        public boolean damageOverlay = true;
        /** Quantidade do item na mao no inventario (flechas, para o arco). */
        public boolean itemCount = true;
        public boolean armorDamage = true;
        public boolean itemDamage = true;
        /** "restante/maximo" quando a durabilidade aparece como valor. */
        public boolean maxDamage = false;
        public boolean equippedItem = true;
        /** Limiar e codigo de cor (0-9, a-f): a cor vale quando a durabilidade e <= o limiar. */
        public String damageColors = "100,f; 80,7; 60,e; 40,6; 25,c; 10,4";
        /** value, percent ou none. */
        public String damageDisplay = "value";
        /** A lista de cores compara com a durabilidade restante (value) ou com a porcentagem (percent). */
        public String thresholdType = "value";

        public Armor() {
            resetPosition();
            showInChat = false;
        }

        @Override
        void resetPosition() {
            // Embaixo no centro como no ArmorStatusHUD do jogador, mas acima da barra de
            // armadura do jogo (49 px da borda): nos 41 px do original, com armadura
            // vestida, os icones cobriam a barra.
            anchorX = 1;
            anchorY = 2;
            x = 0;
            y = -50;
            scale = 1.0F;
        }

        @Override
        Placement fresh() {
            return new Armor();
        }
    }

    /** StatusEffectHUD: efeitos ativos, com icone, nome e duracao. */
    public static final class Effects extends Placement {
        /** Tira a lista de efeitos do inventario (e o deslocamento da tela para o lado). */
        public boolean hideInventoryList = true;
        public boolean effectName = true;
        public String nameColor = "f";
        public String durationColor = "f";
        /** O icone pisca no fim do efeito. */
        public boolean iconBlink = true;
        /** Segundos restantes em que a duracao comeca a piscar; -1 desliga. */
        public int blinkSeconds = 10;

        public Effects() {
            resetPosition();
            showInChat = true;
        }

        @Override
        void resetPosition() {
            anchorX = 0;
            anchorY = 1;
            x = 2;
            y = 0;
            scale = 1.0F;
        }

        @Override
        Placement fresh() {
            return new Effects();
        }
    }

    /** DirectionHUD: bussola com a direcao para onde o jogador olha. */
    public static final class Compass extends Placement {
        /** 0 faixa, 1 faixa sem fundo, 2 texto, 3 faixa com graus. */
        public int style = 1;
        public String markerColor = "c";

        public Compass() {
            resetPosition();
            showInChat = true;
        }

        @Override
        void resetPosition() {
            anchorX = 1;
            anchorY = 0;
            x = 0;
            y = 2;
            scale = 1.0F;
        }

        @Override
        Placement fresh() {
            return new Compass();
        }
    }

    /** Inventario com ele fechado: as 27 casas do inventario principal. */
    public static final class Inventory extends Placement {
        /** A hotbar tambem, embaixo, como na tela do inventario. */
        public boolean hotbar = false;
        /** As casas marcadas (o fundo em si e o padrao da grade, no Loader). */
        public boolean background = true;
        /** Barra de durabilidade, como no inventario. */
        public boolean durability = true;
        /** Quantidade das pilhas. */
        public boolean count = true;
        /** Some com o inventario vazio. */
        public boolean hideEmpty = true;

        public Inventory() {
            resetPosition();
            showInChat = false;
        }

        @Override
        void resetPosition() {
            // Pequeno, no canto inferior direito: na escala 0,5 fica longe da barra de fome
            // mesmo na janela minima (854x480).
            anchorX = 2;
            anchorY = 2;
            x = -2;
            y = -2;
            scale = 0.5F;
        }

        @Override
        Placement fresh() {
            return new Inventory();
        }
    }

    /**
     * Alvo: o ultimo jogador que voce acertou (no alcance e dentro do tempo) ou, sem golpe recente, o da mira — com
     * cabeca, nome, vida, armadura e item na mao, e a durabilidade de cada peca.
     */
    public static final class Target extends Placement {
        /** Sem golpe recente, mostra quem esta na mira. */
        public boolean aim = true;
        /** Blocos: o alcance da mira, e mais longe que isso o alvo sai. */
        public float range = 8.0F;
        /** Segundos na tela depois do ultimo golpe. */
        public float seconds = 5.0F;
        /** Segundos que o alvo de agora segura o lugar: um golpe noutro jogador antes disso nao troca. */
        public float switchSeconds = 1.0F;
        public boolean head = true;
        public boolean name = true;
        public boolean distance = true;
        /** Barra de vida (com o pedaco perdido sumindo devagar). */
        public boolean healthBar = true;
        public boolean healthText = true;
        public boolean armor = true;
        public boolean held = true;
        /** Barra de durabilidade sobre o icone, como no inventario. */
        public boolean durabilityBar = true;
        /** value, percent ou none: a durabilidade embaixo de cada peca. */
        public String durability = "percent";
        /** Entrar, sair e a vida mudando com animacao. */
        public boolean animation = true;

        public Target() {
            resetPosition();
            showInChat = false;
        }

        @Override
        void resetPosition() {
            // Ao lado da mira, um pouco abaixo: a luta acontece ali, e o olho nao precisa sair do alvo.
            anchorX = 1;
            anchorY = 1;
            x = 70;
            y = 30;
            scale = 1.0F;
        }

        @Override
        Placement fresh() {
            return new Target();
        }
    }

    /** Desempenho num bloco so: FPS, 1% low, ping, CPU, GPU, RAM e CPS. */
    public static final class Stats extends Placement {
        /** 0 "FPS: 120", 1 "120 FPS". */
        public int style = 0;
        public boolean fps = true;
        public boolean low = false;
        /** Pior quadro do ultimo segundo, em ms; desligado por padrao (uma linha a mais mudaria o tamanho do bloco). */
        public boolean worst = false;
        public boolean ping = false;
        public boolean cpu = false;
        public boolean gpu = false;
        /**
         * A RAM do computador, em GB: 0 desligada, qualquer outro valor ligada. Era um modo (1 a 4: a do jogo ou a do
         * sistema, em % ou em tamanho); quem tinha um ligado continua com a linha, agora so com a do computador.
         */
        public int ram = 0;
        /** 0 sem, 1 so o botao esquerdo, 2 os dois. */
        public int cps = 0;
        /** Tudo numa linha so, no lugar de uma linha por medida. */
        public boolean horizontal = true;
        public boolean colorByValue = true;
        /** Cor dos nomes (e dos numeros, sem a cor pelo valor). */
        public String color = "f";

        public Stats() {
            resetPosition();
            showInChat = true;
        }

        @Override
        void resetPosition() {
            anchorX = 0;
            anchorY = 0;
            x = 2;
            y = 2;
            scale = 1.0F;
        }

        @Override
        Placement fresh() {
            return new Stats();
        }
    }

    /** Minimapa: o terreno de cima (como o Rei's Minimap) ou so o radar, com jogadores, mobs e gaiolas. */
    public static final class Minimap extends Placement {
        /** 0 mapa detalhado, 1 mapa simples (menos detalhe, contorno), 2 so radar. */
        public int mode = 0;
        /** 0 quadrado, 1 redondo. */
        public int shape = 0;
        /** Lado em pixels de GUI: 64, 80, 96 ou 128. */
        public int size = 80;
        /** Blocos por pixel: 0,5, 1, 2 ou 4. */
        public float zoom = 1.0F;
        /** Gira com o jogador (a frente para cima); sem, o norte fica para cima. */
        public boolean rotate = true;
        /** Jogadores no mapa (sempre como ponto). */
        public boolean players = true;
        /** 0 monstros e animais, 1 so monstros, 2 so animais, 3 nenhum. */
        public int entities = 0;
        /** Gaiolas (spawners) por perto. */
        public boolean spawners = true;
        /** Cores do Stein Combat nos jogadores: inimigos, aliados e o meu cla. */
        public boolean combat = true;
        public boolean coords = true;
        /** No Nether e embaixo da terra, o chao do andar do jogador. */
        public boolean caves = true;

        public Minimap() {
            resetPosition();
            showInChat = true;
        }

        @Override
        void resetPosition() {
            anchorX = 2;
            anchorY = 0;
            x = -2;
            y = 2;
            scale = 1.0F;
        }

        @Override
        Placement fresh() {
            return new Minimap();
        }
    }

    /**
     * Le o arquivo campo por campo: um campo de versao antiga com outro tipo ("limit": true, de quando o teto era
     * sim ou nao) volta ao padrao sozinho, e o resto fica. Antes o arquivo era lido de uma vez, e um campo desses
     * jogava fora a configuracao inteira — que a primeira gravacao ainda sobrescrevia com o padrao.
     */
    static HudConfig load(File file) {
        if (file.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                com.google.gson.JsonElement tree = new com.google.gson.JsonParser().parse(r);
                if (tree != null && tree.isJsonObject()) {
                    HudConfig c = lenient(tree.getAsJsonObject(), HudConfig.class, "");
                    // Ate a 0.1.1 a lista do Tab era sempre a nossa, e nao havia como escolher. Quem ja tinha
                    // configuracao fica com ela; o padrao novo (a do servidor) vale para quem instala do zero.
                    com.google.gson.JsonElement tab = tree.getAsJsonObject().get("tab");
                    if (!(tab != null && tab.isJsonObject() && tab.getAsJsonObject().has("own"))) {
                        c.tab.own = true;
                    }
                    c.repair();
                    return c;
                }
            } catch (Exception e) {
                System.out.println("[mods] HUD: configuracao ilegivel, usando o padrao: " + e);
            }
        }
        return defaults();
    }

    /** Os padroes do HUD (o que vale sem arquivo e o que "Restaurar padrao" traz de volta), no pacote do mod. */
    static final String DEFAULTS = "/assets/steinhud/defaults.json";

    /** Uma configuracao nova com os padroes do pacote ({@link #DEFAULTS}); sem eles, a dos campos. */
    static HudConfig defaults() {
        try (java.io.InputStream in = HudConfig.class.getResourceAsStream(DEFAULTS)) {
            if (in != null) {
                com.google.gson.JsonElement tree = new com.google.gson.JsonParser()
                        .parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (tree != null && tree.isJsonObject()) {
                    HudConfig c = lenient(tree.getAsJsonObject(), HudConfig.class, "");
                    c.repair();
                    return c;
                }
            }
        } catch (Exception e) {
            System.out.println("[mods] HUD: padroes do pacote ilegiveis: " + e);
        }
        return new HudConfig();
    }

    /** O padrao de um elemento: o campo dos padroes do pacote com a mesma classe (sem ele, um novo). */
    static Placement defaultFor(Placement p) {
        HudConfig d = defaults();
        for (java.lang.reflect.Field f : HudConfig.class.getDeclaredFields()) {
            if (f.getType() == p.getClass()) {
                try {
                    f.setAccessible(true);
                    return (Placement) f.get(d);
                } catch (ReflectiveOperationException e) {
                    break;
                }
            }
        }
        return p.fresh();
    }

    /** Um objeto com os padroes do construtor e, por cima, cada campo do arquivo que for do tipo certo. */
    private static <T> T lenient(com.google.gson.JsonObject o, Class<T> type, String path) throws Exception {
        java.lang.reflect.Constructor<T> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        T out = ctor.newInstance();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                int m = f.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isTransient(m)
                        || !o.has(f.getName())) {
                    continue;
                }
                f.setAccessible(true);
                com.google.gson.JsonElement v = o.get(f.getName());
                try {
                    f.set(out, GSON.fromJson(v, f.getGenericType()));
                } catch (RuntimeException e) {
                    // Um objeto nosso (tab, armor...): o que der para aproveitar dele; outro tipo fica no padrao.
                    if (v.isJsonObject() && f.getType().getEnclosingClass() == HudConfig.class) {
                        f.set(out, lenient(v.getAsJsonObject(), f.getType(), path + f.getName() + "."));
                    } else {
                        System.out.println("[mods] HUD: " + path + f.getName() + " de outra versao, volta ao padrao");
                    }
                }
            }
        }
        return out;
    }

    void save(File file) {
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            System.out.println("[mods] HUD: nao consegui salvar a configuracao: " + e);
        }
    }

    /** Arquivo editado a mao ou de versao anterior: campos faltando voltam ao padrao. */
    private void repair() {
        if (armor == null) {
            armor = new Armor();
        }
        if (effects == null) {
            effects = new Effects();
        }
        if (direction == null) {
            direction = new Compass();
        }
        if (stats == null) {
            stats = new Stats();
        }
        if (stats.color == null) {
            stats.color = "f";
        }
        if (inventory == null) {
            inventory = new Inventory();
        }
        if (target == null) {
            target = new Target();
        }
        if (target.durability == null) {
            target.durability = "percent";
        }
        if (minimap == null) {
            minimap = new Minimap();
        }
        if (tab == null) {
            tab = new Tab();
        }
        tab.repair();
        for (Placement p : new Placement[] {armor, effects, direction, inventory, target, stats, minimap}) {
            if (!(p.scale >= 0.25F && p.scale <= 4.0F)) {
                p.scale = 1.0F;
            }
            p.anchorX = Math.max(0, Math.min(2, p.anchorX));
            p.anchorY = Math.max(0, Math.min(2, p.anchorY));
        }
        if (armor.damageColors == null) {
            armor.damageColors = new Armor().damageColors;
        }
        grid = Math.max(0, Math.min(32, grid));
    }
}
