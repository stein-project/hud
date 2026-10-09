/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Render;

/**
 * A lista do Tab no lugar da do jogo (a ponte entrega o desenho pelo {@code onPlayerList}).
 *
 * <p>O que a do jogo faz e incomoda num servidor cheio: corta em <b>80 jogadores</b>, mostra o ping em cinco
 * barrinhas com cortes em 150, 300, 600 e 1000 ms (40 ms e 140 ms desenham igual) e deixa o servidor mandar na
 * aparencia — cabecalho, rodape e o prefixo/sufixo do time colados no nick.
 *
 * <p>Aqui tudo isso e opcao, e ainda da para <b>filtrar e agrupar</b>: so quem tem cla, so aliados e inimigos, so
 * quem esta perto, agrupado por cla ou por relacao (Meu cla, Aliados, Inimigos, Sem cla), com a distancia de cada um.
 * Cla e relacao vem do Stein Combat, quando ele esta instalado.
 *
 * <p><b>O que o cliente nao sabe:</b> em que mundo cada um da lista esta — o protocolo do 1.8.9 nao diz, e numa rede
 * com varios servidores a lista pode ter gente de outro servidor. O filtro "so perto" resolve na pratica: quem tem
 * entidade carregada esta, por definicao, no seu mundo e dentro do alcance que o servidor envia. Quem esta fora
 * disso aparece sem distancia ("--").
 */
final class TabList {

    /** Altura de cada linha e largura da cabeca, como no jogo. */
    private static final int ROW = 9;
    private static final int HEAD = 8;
    /** Menor escala que aceitamos antes de cortar a lista com "+N". */
    static final float MIN_SCALE = 0.6F;

    // Os nomes sao chaves de traducao (T.tr); os numeros da opcao de ciclo saem direto.
    static final String[] PING_MODES = {"tab.bars", "tab.number", "tab.barsAndNumber"};
    static final String[] NAME_COLORS = {"tab.server", "tab.white", "tab.byRelation"};
    static final String[] FILTERS = {"tab.everyone", "tab.withClan", "tab.myClan", "tab.allies", "tab.enemies",
        "tab.nearby"};
    static final String[] GROUPS = {"off", "tab.byClan", "tab.byRelation"};
    static final String[] SORTS = {"tab.server", "tab.ping", "tab.name", "tab.distance"};
    static final int[] RADIUS = {0, 16, 32, 64, 128, 256};
    /** Teto de jogadores na lista; 80 e o do jogo e o padrao. */
    static final String[] LIMIT_NAMES = {"20", "40", "80", "150", "250", "500"};
    static final int[] LIMITS = {20, 40, 80, 150, 250, 500};
    /** Colunas: automatico (as que couberem) ou um numero fixo, que a lista respeita encolhendo a escala. */
    static final String[] COLUMN_NAMES = {"tab.auto", "2", "3", "4"};
    static final int[] COLUMNS = {0, 2, 3, 4};
    /** Nunca mais que isto no automatico: com mais, cada coluna fica estreita demais para o nome. */
    private static final int MAX_COLUMNS = 8;
    /** Nome de cada grupo por relacao, pelo codigo do CombatLink (NONE nao aparece: todo jogador tem relacao). */
    static final String[] RELATION_NAMES = {"tab.noClan", "tab.allies", "tab.myClan", "tab.enemies", "tab.noClan"};

    /** Os nomes de uma opcao no idioma do jogo: chave vira texto, numero fica como esta. */
    static String[] names(String[] keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = Character.isDigit(keys[i].charAt(0)) ? keys[i] : T.tr(keys[i]);
        }
        return out;
    }

    /** Nomes do raio: sem limite, e depois os blocos. */
    static String[] radiusNames() {
        String[] out = new String[RADIUS.length];
        for (int i = 0; i < RADIUS.length; i++) {
            out[i] = RADIUS[i] == 0 ? T.tr("tab.noLimit") : T.tr("tab.blocks", RADIUS[i]);
        }
        return out;
    }

    private final HudConfig.Tab c;
    TabList(HudConfig.Tab c) {
        this.c = c;
    }

    /** Uma linha: um jogador, ou o titulo de um grupo. */
    static final class Line {
        Players.Entry player;
        String title;
        int count;
        String name = "";
        /** O que a ordem compara, lido uma vez na montagem: a ordenacao compara cada linha muitas vezes. */
        String nick = "";
        String team = "";
        int ping;
        boolean spectator;
        int relation;
        String clan;
        /** Posicao do cla dele no agrupamento por cla: 0 o meu, 1 aliado, 2 outro, 3 sem cla. */
        int clanRank;
        /** Distancia em blocos, ou -1 sem entidade carregada (fora do seu mundo ou do alcance). */
        double distance = -1;
    }

    /**
     * Desenha a lista. Devolve false so sem conexao ou fonte (o jogo entao desenha a dela): com filtro que nao deixou
     * ninguem, a lista continua sendo a nossa, vazia e dizendo por que. Antes ela devolvia false tambem nesse caso, e
     * o jogo desenhava a lista dele — sem filtro, sem ordem e sem grupos. Era o "as vezes ordena, as vezes nao": o meu
     * cla ainda nao lido ao entrar ou trocar de servidor, ou ninguem perto, e a lista inteira voltava a ser a do jogo.
     */
    boolean draw(int width, boolean objective) {
        if (Players.self() == null) {
            return false;
        }
        if (c.gameFont) {
            boolean[] out = new boolean[1];
            Render.gameFont(() -> out[0] = drawList(width, objective));
            return out[0];
        }
        return drawList(width, objective);
    }

    private boolean drawList(int width, boolean objective) {
        List<Line> lines = lines();
        String header = c.headerFooter ? Players.tabHeader() : null;
        String footer = c.headerFooter ? Players.tabFooter() : null;
        // Com filtro ligado, uma linha dizendo qual e e quantos ficaram de quantos: sem isso, uma lista que encolheu
        // parece uma lista quebrada — e o proprio filtro e a explicacao.
        String note = c.filter == 0 ? null
                : T.tr(FILTERS[Math.max(0, Math.min(FILTERS.length - 1, c.filter))]) + " · " + kept + "/" + total;
        String empty = lines.isEmpty() ? T.tr("tab.nobody") : null;

        int scoreW = 0;
        if (objective) {
            for (Line l : lines) {
                if (l.player != null) {
                    scoreW = Math.max(scoreW, Render.textWidth(score(l)) + 6);
                }
            }
        }
        Layout at = layout(lines, nameWidth(lines, empty), scoreW, width, Render.guiHeight(), header, footer,
                note != null, c);
        float scale = at.scale;
        int columns = at.columns;
        int rows = at.rows;
        int shown = at.shown;
        int hidden = at.hidden;
        int cellW = at.cellW;

        Render.push();
        Render.scale(scale);
        int logicalW = (int) (width / scale);
        int totalW = cellW * columns + (columns - 1) * 5;
        int left = logicalW / 2 - totalW / 2;
        int top = (int) (10 / scale);
        if (header != null && !header.isEmpty()) {
            top = lines(header, logicalW, top);
        }
        if (note != null) {
            Render.text("§7" + note, left + totalW / 2 - Render.textWidth(note) / 2, top, 0x9A9A9A, true);
            top += ROW + 2;
        }
        int listH = Math.min(rows, Math.max(1, shown)) * ROW + (hidden > 0 ? ROW : 0);
        // O fundo padrao do Stein, na transparencia escolhida; as faixas das linhas acompanham.
        float bg = Math.max(0.0F, Math.min(1.0F, c.background));
        Render.surface(left - 3, top - 3, totalW + 6, listH + 6, bg);
        if (empty != null) {
            Render.text("§7" + empty, left + totalW / 2 - Render.textWidth(empty) / 2, top, 0xA0A0A0, true);
        }
        for (int i = 0; i < shown; i++) {
            Line l = lines.get(i);
            int x = left + (i / rows) * (cellW + 5);
            int y = top + (i % rows) * ROW;
            if (l.player == null) {
                if (l.title != null) {
                    group(l, x, y, cellW);   // linha sem titulo e o respiro entre grupos: nao se desenha
                }
            } else {
                row(l, x, y, cellW, objective);
            }
        }
        if (hidden > 0) {
            String more = "+" + hidden;
            Render.text("§7" + more, left + totalW / 2 - Render.textWidth(more) / 2, top + rows * ROW, 0xA0A0A0, true);
        }
        int bottom = top + listH + 4;
        if (footer != null && !footer.isEmpty()) {
            bottom = lines(footer, logicalW, bottom);
        }
        Render.pop();
        // O que fica embaixo da lista (cabecalho ao rodape) some da grade enquanto ela esta aberta.
        Hud.cover((left - 3) * scale, 0, (totalW + 6) * scale, bottom * scale);
        return true;
    }

    /** Onde a lista fica: escala, colunas, linhas por coluna, quantas linhas aparecem e a largura de cada celula. */
    static final class Layout {
        float scale;
        int columns;
        int rows;
        int shown;
        int hidden;
        int cellW;
        /** Onde o desenho termina embaixo, e a largura da caixa, na escala: o autoteste confere que cabem na tela. */
        int bottom;
        int totalW;
        int logicalW;
        int logicalH;
    }

    /** O nome mais largo (ou o titulo de grupo, ou o aviso de lista vazia). */
    static int nameWidth(List<Line> lines, String empty) {
        int nameW = empty == null ? 0 : Render.textWidth(empty);
        for (Line l : lines) {
            String text = l.player != null ? l.name : l.title == null ? "" : l.title + "  " + l.count;
            nameW = Math.max(nameW, Render.textWidth(text));
        }
        return nameW;
    }

    /**
     * A maior escala em que a lista cabe e a escolhida, e so o que nao couber nem na menor vira "+N". Tudo aqui e
     * medido ja na escala (cabecalho e rodape tambem sao desenhados nela), em pixels da GUI. O teto de colunas entra na
     * conta da altura: antes, com mais jogadores do que 8 colunas levam, a lista passava do fim da tela; e quando sobra
     * "+N", a linha dele tambem precisa caber.
     */
    static Layout layout(List<Line> lines, int nameW, int scoreW, int width, int guiH, String header,
            String footer, boolean note, HudConfig.Tab c) {
        int pingW = (c.ping != 1 ? 12 : 0) + (c.ping != 0 ? Render.textWidth("999 ms") + 4 : 0);
        int distW = c.distance ? Render.textWidth("999 m") + 6 : 0;
        Layout at = new Layout();
        at.cellW = (c.heads ? HEAD + 2 : 0) + nameW + 6 + distW + scoreW + pingW;
        int size = lines.size();
        int n = Math.max(1, size);
        int fixed = COLUMNS[Math.max(0, Math.min(COLUMNS.length - 1, c.columns))];
        int headerH = 0;
        int footerH = 0;
        for (int step = 0; ; step++) {
            float s = Math.max(MIN_SCALE, 1.0F - step * 0.05F);
            boolean last = s <= MIN_SCALE + 0.001F;
            int w = (int) (width / s);
            headerH = wrapped(header, w);
            footerH = wrapped(footer, w);
            int h = (int) ((guiH - 20) / s) - headerH - footerH - (note ? ROW + 2 : 0);
            int fit = Math.max(1, h / ROW);
            int widest = Math.max(1, Math.min(fixed > 0 ? fixed : MAX_COLUMNS, (w - 8) / (at.cellW + 5)));
            int cols = fixed > 0 ? Math.min(fixed, n) : (n + fit - 1) / fit;
            at.scale = s;
            if (cols <= widest && (n + cols - 1) / cols <= fit) {
                at.columns = cols;
                at.rows = (n + cols - 1) / cols;
                break;
            }
            if (last) {
                // Nem na menor escala: as colunas que couberem na largura, cheias ate embaixo menos a linha do "+N".
                at.columns = widest;
                at.rows = Math.max(1, fit - 1);
                break;
            }
        }
        at.shown = Math.min(size, at.rows * at.columns);
        at.hidden = size - at.shown;
        at.logicalW = (int) (width / at.scale);
        at.logicalH = (int) (guiH / at.scale);
        at.totalW = at.cellW * at.columns + (at.columns - 1) * 5;
        int listH = Math.min(at.rows, Math.max(1, at.shown)) * ROW + (at.hidden > 0 ? ROW : 0);
        // Como o draw desenha: 10 de margem, cabecalho, a linha do filtro, a caixa da lista e o rodape 4 abaixo dela.
        at.bottom = (int) (10 / at.scale) + headerH + (note ? ROW + 2 : 0) + listH + 4 + Math.max(0, footerH - 2);
        return at;
    }

    /** Altura do cabecalho (ou rodape) quebrado na largura dada, com o respiro de baixo; 0 sem texto. */
    private static int wrapped(String text, int width) {
        return text == null || text.isEmpty() ? 0 : Render.wrap(text, width - 50).size() * ROW + 2;
    }

    // ---- quem entra na lista ----

    /** A lista montada, refeita a cada tick: a montagem le o placar e o mundo, e nada disso muda entre quadros. */
    private List<Line> cached;
    private long cachedAt;
    private int cachedCount;
    /** Quantos jogadores o servidor tem na lista e quantos passaram pelo filtro, para a linha do filtro. */
    int total;
    int kept;

    /**
     * As linhas do quadro. Montar (formatar nome, perguntar cla e relacao, medir distancia, filtrar, ordenar, agrupar)
     * custa por jogador, e com o Tab segurado isso acontecia a cada quadro — com centenas de jogadores, era o que
     * deixava o Tab lento. A lista de verdade muda no ritmo do jogo, entao e refeita a cada 50 ms (um tick) ou assim
     * que alguem entra ou sai.
     */
    private List<Line> lines() {
        long now = System.nanoTime();
        int count = Players.count();
        if (cached == null || count != cachedCount || now - cachedAt > 50_000_000L) {
            cached = collect();
            cachedAt = now;
            cachedCount = count;
        }
        return cached;
    }

    /** Monta as linhas: filtra, ordena e, se for o caso, agrupa com titulo e contagem. */
    List<Line> collect() {
        // Uma volta so pelas entidades carregadas: procurar jogador por jogador seria uma varredura por linha.
        Map<String, Object> loaded = new HashMap<>();
        for (Object e : Entities.players()) {
            loaded.put(Entities.name(e), e);
        }
        Object self = Entities.self();
        List<Line> out = new ArrayList<>();
        total = 0;
        for (Players.Entry p : Players.list()) {
            total++;
            Line l = new Line();
            l.player = p;
            l.name = name(p);
            String nick = p.nick;
            l.nick = nick;
            l.team = p.team;
            l.ping = p.ping;
            l.spectator = p.spectator;
            l.relation = CombatLink.relation(nick);
            l.clan = CombatLink.clan(nick);
            Object e = loaded.get(nick);
            if (e != null && self != null) {
                l.distance = Entities.distance(e, self);
            }
            if (keep(l)) {
                out.add(l);
            }
        }
        kept = out.size();
        // Posicao de cada cla no agrupamento por cla: o meu, depois os aliados, depois os outros, e sem cla no fim. E
        // por cla, nao por jogador — um aliado de nome dentro de um cla inimigo nao pode partir o grupo em dois.
        Map<String, Integer> clanRank = new HashMap<>();
        for (Line l : out) {
            if (l.clan != null) {
                int r = l.relation == CombatLink.CLAN ? 0 : l.relation == CombatLink.ALLY ? 1 : 2;
                clanRank.merge(l.clan, r, Math::min);
            }
        }
        for (Line l : out) {
            l.clanRank = l.clan == null ? 3 : clanRank.get(l.clan);
        }
        boolean print = debug();
        out.sort(order());
        if (print) {
            StringBuilder b = new StringBuilder("[hud] tab: primeiros:");
            for (int i = 0; i < Math.min(8, out.size()); i++) {
                Line l = out.get(i);
                b.append(' ').append(strip(l.name)).append('/').append(l.ping).append("ms/")
                        .append(l.clan);
            }
            System.out.println(b);
        }
        int limit = LIMITS[Math.max(0, Math.min(LIMITS.length - 1, c.limit))];
        if (out.size() > limit) {
            out = new ArrayList<>(out.subList(0, limit));   // corta depois de ordenar: o teto tira os ultimos
        }
        return c.group == 0 ? out : grouped(out);
    }

    /**
     * {@code -Dsteinhud.tabDebug=true}: uma linha por segundo com quantos ficaram de quantos e o meu cla lido, e uma
     * linha na hora em que o meu cla muda (inclusive para "nenhum"). Foi assim que se viu, no servidor de verdade, a
     * lista voltando a ser a do jogo.
     */
    private static final boolean DEBUG = Boolean.getBoolean("steinhud.tabDebug");
    private long debugAt;
    private String debugClan = "?";
    private int empties;

    private boolean debug() {
        if (!DEBUG) {
            return false;
        }
        String mine = Players.self() == null ? null : CombatLink.clan(Players.self());
        if (kept == 0) {
            empties++;
        }
        if (!String.valueOf(mine).equals(debugClan)) {
            System.out.println("[hud] tab: meu cla " + debugClan + " -> " + mine + " (" + kept + " de " + total + ")");
            debugClan = String.valueOf(mine);
        }
        long now = System.nanoTime();
        if (now - debugAt > 1_000_000_000L) {
            debugAt = now;
            System.out.println("[hud] tab: filtro " + c.filter + " ordem " + c.sort + " grupo " + c.group + ": " + kept
                    + " de " + total + ", meu cla " + mine + ", vazias no ultimo segundo " + empties);
            empties = 0;
            return true;
        }
        return false;
    }

    /**
     * Quem entra na lista. "Aliados" inclui o meu cla — quem e do meu cla e aliado antes de tudo; "inimigos" e quem
     * esta em outro cla sem alianca, e nao todo mundo que nao e aliado.
     */
    private boolean keep(Line l) {
        return switch (c.filter) {
            case 1 -> l.clan != null;
            case 2 -> l.relation == CombatLink.CLAN;
            case 3 -> l.relation == CombatLink.CLAN || l.relation == CombatLink.ALLY;
            case 4 -> l.relation == CombatLink.ENEMY;
            case 5 -> l.distance >= 0 && (radius() == 0 || l.distance <= radius());
            default -> true;
        };
    }

    private int radius() {
        return RADIUS[Math.max(0, Math.min(RADIUS.length - 1, c.radius))];
    }

    /**
     * Insere um titulo antes de cada grupo, com quantos tem dentro, e uma linha vazia entre um grupo e outro: as
     * colunas correm de cima para baixo, entao sem o respiro um grupo comeca colado no fim do anterior e a lista toda
     * parece uma coisa so — foi o que apareceu num servidor de verdade, com 400 jogadores e dezenas de clas.
     */
    private List<Line> grouped(List<Line> players) {
        List<Line> out = new ArrayList<>(players.size() + 4);
        String current = null;
        int start = -1;
        for (Line l : players) {
            String key = groupTitle(c.group, l.clan, l.relation);
            if (!key.equals(current)) {
                if (start >= 0) {
                    out.get(start).count = out.size() - start - 1;
                    out.add(new Line());   // linha vazia: o respiro entre um grupo e o proximo
                }
                Line title = new Line();
                title.title = key;
                title.relation = l.relation;   // a cor do titulo, agrupado por relacao
                start = out.size();
                out.add(title);
                current = key;
            }
            out.add(l);
        }
        if (start >= 0) {
            out.get(start).count = out.size() - start - 1;
        }
        return out;
    }

    /**
     * Ordem das linhas. Agrupado, o grupo manda primeiro: meu cla, aliados, inimigos, sem cla.
     *
     * <p>"Nome" e o <b>nick</b>, nao o texto do Tab: o texto comeca pelo prefixo ("[FoF] ", "[VIP] "), e ordenar por
     * ele juntava por cla ou cargo — no Solaris, com 1700 na lista, parecia lista sem ordem nenhuma. Ping 0 (o
     * servidor ainda nao mediu) e quem esta sem distancia vao para o fim, nao para o topo.
     */
    private Comparator<Line> order() {
        Comparator<Line> byName = Comparator.comparing((Line l) -> l.nick, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(l -> l.nick);
        Comparator<Line> base = switch (c.sort) {
            case 1 -> Comparator.<Line>comparingInt(l -> l.ping <= 0 ? Integer.MAX_VALUE : l.ping)
                    .thenComparing(byName);
            case 2 -> byName;
            case 3 -> Comparator.<Line>comparingDouble(l -> l.distance < 0 ? Double.MAX_VALUE : l.distance)
                    .thenComparing(byName);
            // A do jogo (GuiPlayerTabOverlay.PlayerComparator): espectador no fim, depois o nome do time, depois o nick.
            default -> Comparator.<Line>comparingInt(l -> l.spectator ? 1 : 0)
                    .thenComparing(l -> l.team)
                    .thenComparing(l -> l.nick);
        };
        if (c.group == 1) {
            // O meu cla primeiro e sem cla no fim: com o teto cortando depois da ordem, em ordem alfabetica pura um
            // servidor de 1700 jogadores mostrava so os clas do comeco do alfabeto, e o meu nunca aparecia.
            return Comparator.<Line>comparingInt(l -> l.clanRank)
                    .thenComparing(l -> l.clan == null ? "" : l.clan)
                    .thenComparing(base);
        }
        if (c.group == 2) {
            return Comparator.<Line>comparingInt(l -> switch (l.relation) {
                case CombatLink.CLAN -> 0;
                case CombatLink.ALLY -> 1;
                case CombatLink.ENEMY -> 2;
                default -> 3;
            }).thenComparing(base);
        }
        return base;
    }

    // ---- desenho de cada linha ----

    /**
     * Titulo do grupo: barra escura, o nome em negrito e a contagem a direita. Agrupado por relacao, o titulo sai na
     * cor da relacao (do Combat), que e a mesma dos nomes: com 400 jogadores e a lista miuda, a cor e o que se enxerga
     * antes de qualquer letra.
     */
    private void group(Line l, int x, int y, int cellW) {
        Render.rect(x, y - 1, cellW, ROW, alphaOf(0x90000000));
        int color = c.group == 2 ? relationColor(l.relation) : 0xE0C890;
        Render.text("§l" + l.title, x + 2, y, color, true);
        String count = String.valueOf(l.count);
        Render.text("§7" + count, x + cellW - 2 - Render.textWidth(count), y, 0xA0A0A0, true);
    }

    /** A cor com a opacidade escalada pela transparencia escolhida (o padrao, 0,63, mantem a de antes). */
    private int alphaOf(int argb) {
        float k = Math.max(0.0F, Math.min(1.0F, c.background / 0.63F));
        return Math.round((argb >>> 24) * k) << 24 | argb & 0xFFFFFF;
    }

    /** A cor do grupo de relacao, pela relacao guardada no titulo (e nao pelo texto, que muda com o idioma). */
    private static int relationColor(int relation) {
        return switch (relation) {
            case CombatLink.CLAN, CombatLink.ALLY, CombatLink.ENEMY -> CombatLink.colorOf(relation);
            default -> 0xD0D0D0;
        };
    }

    /** O titulo do grupo de um jogador: a tag do cla (ou "sem cla"), ou o nome da relacao. */
    static String groupTitle(int group, String clan, int relation) {
        if (group == 1) {
            return clan == null ? T.tr("tab.noClan") : clan;
        }
        return T.tr(RELATION_NAMES[Math.max(0, Math.min(RELATION_NAMES.length - 1, relation))]);
    }

    private void row(Line l, int x, int y, int cellW, boolean objective) {
        if (c.rowBackground) {
            Render.rect(x, y, cellW, ROW - 1, alphaOf(0x20FFFFFF));
        }
        int textX = x;
        if (c.heads) {
            // A skin do servidor manda; sem nenhuma (servidor sem autenticacao, todo mundo Steve), a da conta
            // original com esse nick, pelo Loader. Pode nao ser daquela pessoa: num servidor offline o "Notch" de la
            // nao e o Notch — e so para reconhecer quem voce conhece.
            Render.playerHead(l.nick, x, y, HEAD, true);
            textX += HEAD + 2;
        }
        Render.text(l.spectator ? "§7§o" + strip(l.name) : l.name, textX, y, color(l), true);
        int right = x + cellW;
        if (c.ping != 1) {
            Render.pingIcon(l.ping, right - 11, y);   // as barrinhas do jogo, no mesmo lugar
            right -= 12;
        }
        if (c.ping != 0) {
            String ms = l.ping + " ms";
            Render.text(ms, right - Render.textWidth(ms), y, pingColor(l.ping), true);
            right -= Render.textWidth(ms) + 4;
        }
        if (c.distance) {
            String d = l.distance < 0 ? "--" : Math.round(l.distance) + " m";
            Render.text("§7" + d, right - Render.textWidth(d), y, 0xA0A0A0, true);
            right -= Render.textWidth(d) + 6;
        }
        if (objective) {
            String s = score(l);
            Render.text("§e" + s, right - Render.textWidth(s), y, 0xFFFFFF, true);
        }
    }

    /**
     * O nome como o jogador vai ver. O servidor monta "prefixo + nick + sufixo" — pelo time do placar ou mandando o
     * nome pronto para o Tab —, e as duas opcoes tiram um ou outro <b>so daqui</b>: o que vai e volta do servidor
     * continua igual, e o nick e achado dentro do texto para a conta valer nos dois jeitos.
     */
    String name(Players.Entry p) {
        String full = p.tabName;
        if (!c.hidePrefix && !c.hideSuffix) {
            return full;
        }
        String nick = p.nick;
        int i = full.indexOf(nick);
        if (i < 0) {
            return full;   // o servidor trocou o nick inteiro: nao ha prefixo nem sufixo que separar
        }
        String prefix = c.hidePrefix ? "" : full.substring(0, i);
        String suffix = c.hideSuffix ? "" : full.substring(i + nick.length());
        return prefix + nick + suffix;
    }

    /** Branca, do servidor ou a do Combat — a mesma cor do minimapa, escolhida la por relacao. */
    private int color(Line l) {
        if (c.nameColor != 2 || l.player == null) {
            return 0xFFFFFF;
        }
        return CombatLink.color(l.nick);
    }

    private static int pingColor(int ms) {
        return ms <= 0 ? 0x808080 : ms < 80 ? 0x55FF55 : ms < 150 ? 0xFFFF55 : ms < 300 ? 0xFFAA00 : 0xFF5555;
    }

    /** O placar da lista (a coluna de numero) do jogador da linha. */
    private static String score(Line l) {
        String s = Players.listScore(l.nick);
        return s == null ? "" : s;
    }

    /** Cabecalho (ou rodape) centrado; devolve o y logo abaixo dele. */
    private static int lines(String text, int width, int y) {
        List<String> all = Render.wrap(text, width - 50);
        for (int i = 0; i < all.size(); i++) {
            String line = all.get(i);
            Render.text(line, width / 2 - Render.textWidth(line) / 2, y + i * ROW, 0xFFFFFF, true);
        }
        return y + all.size() * ROW + 2;
    }

    static String strip(String s) {
        return s.replaceAll("(?i)§[0-9a-fk-or]", "");
    }
}
