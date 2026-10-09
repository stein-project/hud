/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

/**
 * {@code -Dsteinhud.tabSelfTestAtSec=N}: N segundos depois de abrir o jogo, com o Tab segurado, passa a lista do Tab
 * por <b>todas</b> as combinacoes da configuracao, na lista de verdade do servidor, e confere cada uma sozinho.
 *
 * <p>Quem entra, ordem, grupos e teto: filtro x raio x ordem x grupo x teto x prefixo x sufixo, cada combinacao
 * montada pela lista e comparada com uma conta feita aqui, por outro caminho (o filtro de novo, jogador por jogador,
 * direto do Combat; a ordem conferida par a par pela chave; cada grupo uma vez so, com a contagem certa e na ordem
 * certa). A tela: para cada lista montada, colunas x cabecalho x ping x cabecas x distancia x quatro tamanhos de tela,
 * e a lista tem de caber, o "+N" tem de somar certo e as colunas fixas tem de ser respeitadas. O Painel: cada opcao da
 * pagina clicada por todos os valores, para frente e para tras, e o "Restaurar" tem de devolver o padrao.
 *
 * <p>Tudo roda num quadro so, na thread do jogo: nenhum pacote do servidor muda a lista no meio da conta. No fim, a
 * configuracao do jogador volta como estava. O resultado sai no log, linhas "Autoteste da lista do Tab".
 */
final class TabSelfTest {

    private static final String TAG = "Autoteste da lista do Tab: ";
    /** Quatro telas (em pixels da GUI): 854x480 e 1920x1017 nas escalas 2, 3 e 1 da GUI. */
    private static final int[][] SCREENS = {{427, 240}, {960, 508}, {640, 339}, {1920, 1017}};

    private final List<String> failures = new ArrayList<>();
    private int failureCount;
    private int lists;
    private int layouts;
    private int checks;

    /** Um jogador da lista do servidor, lido aqui por conta propria (a lista nao participa desta conta). */
    private static final class Ref {
        String nick;
        String clan;
        int relation;
        double distance = -1;
        int ping;
        String team;
        boolean spectator;
    }

    static void run(SteinHud hud, TabList list, HudConfig.Tab c) {
        new TabSelfTest().all(hud, list, c);
    }

    private void all(SteinHud hud, TabList list, HudConfig.Tab c) {
        if (Players.self() == null) {
            System.out.println(TAG + "sem conexao, nada a testar");
            return;
        }
        HudConfig.Tab saved = new HudConfig.Tab();
        saved.copyFrom(c);
        long start = System.nanoTime();
        try {
            Map<String, Ref> refs = references();
            String mine = CombatLink.clan(Players.self());
            System.out.println(TAG + refs.size() + " jogadores na lista, Combat " + (CombatLink.available() ? "ligado"
                    : "AUSENTE") + ", meu cla " + mine);
            int[] perFilter = new int[TabList.FILTERS.length];
            for (int filter = 0; filter < TabList.FILTERS.length; filter++) {
                for (int radius = 0; radius < TabList.RADIUS.length; radius++) {
                    int expected = 0;
                    for (Ref r : refs.values()) {
                        expected += passes(filter, radius, r) ? 1 : 0;
                    }
                    if (radius == 0) {
                        perFilter[filter] = expected;
                    }
                    for (int sort = 0; sort < TabList.SORTS.length; sort++) {
                        for (int group = 0; group < TabList.GROUPS.length; group++) {
                            for (int limit = 0; limit < TabList.LIMITS.length; limit++) {
                                for (int names = 0; names < 4; names++) {
                                    c.reset();
                                    c.filter = filter;
                                    c.radius = radius;
                                    c.sort = sort;
                                    c.group = group;
                                    c.limit = limit;
                                    c.hidePrefix = (names & 1) != 0;
                                    c.hideSuffix = (names & 2) != 0;
                                    one(list, c, refs, mine, expected);
                                }
                            }
                        }
                    }
                }
            }
            StringBuilder counts = new StringBuilder();
            for (int i = 0; i < perFilter.length; i++) {
                counts.append(i == 0 ? "" : ", ").append(TabList.FILTERS[i]).append(' ').append(perFilter[i]);
            }
            System.out.println(TAG + "quantos passam em cada filtro (raio: tudo que carregou): " + counts);
            panel(hud, c);
        } catch (Throwable t) {
            fail("o autoteste parou com " + t);
            t.printStackTrace(System.out);
        } finally {
            c.copyFrom(saved);
            hud.save();
        }
        System.out.println(TAG + lists + " combinacoes de lista, " + layouts + " de tela, " + checks
                + " conferencias, " + failureCount + " falha(s), em " + (System.nanoTime() - start) / 1_000_000L
                + " ms");
        for (Map.Entry<String, Integer> k : kinds.entrySet()) {
            System.out.println(TAG + "FALHA x" + k.getValue() + ": " + k.getKey() + " | ex.: "
                    + firstOfKind.get(k.getKey()));
        }
    }

    /** Falhas por tipo (a frase sem a combinacao e sem os numeros), com o primeiro exemplo de cada. */
    private final Map<String, Integer> kinds = new java.util.LinkedHashMap<>();
    private final Map<String, String> firstOfKind = new HashMap<>();

    private void fail(String what) {
        failureCount++;
        String msg = what.contains("] ") ? what.substring(what.lastIndexOf("] ") + 2) : what;
        String kind = msg.replaceAll("[0-9.]+", "#").replaceAll("\"[^\"]*\"", "\"*\"");
        kind = kind.length() > 60 ? kind.substring(0, 60) : kind;
        if (kinds.merge(kind, 1, Integer::sum) == 1) {
            firstOfKind.put(kind, what);
        }
        if (failures.size() < 10) {
            failures.add(what);
        }
    }

    private boolean check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fail(what);
        }
        return ok;
    }

    // ---- a conta feita aqui ----

    private static Map<String, Ref> references() {
        Map<String, Object> loaded = new HashMap<>();
        for (Object e : Entities.players()) {
            loaded.put(Entities.name(e), e);
        }
        Object self = Entities.self();
        Map<String, Ref> out = new HashMap<>();
        for (Players.Entry p : Players.list()) {
            Ref r = new Ref();
            r.nick = p.nick;
            r.clan = CombatLink.clan(r.nick);
            r.relation = CombatLink.relation(r.nick);
            r.ping = p.ping;
            r.team = p.team;
            r.spectator = p.spectator;
            Object e = loaded.get(r.nick);
            if (e != null && self != null) {
                double dx = Entities.x(e, 1) - Entities.x(self, 1);
                double dy = Entities.y(e, 1) - Entities.y(self, 1);
                double dz = Entities.z(e, 1) - Entities.z(self, 1);
                r.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            out.put(r.nick, r);
        }
        return out;
    }

    /** O filtro, escrito de novo a partir do que cada opcao promete no Painel. */
    private static boolean passes(int filter, int radius, Ref r) {
        int max = TabList.RADIUS[radius];
        switch (filter) {
            case 1: return r.clan != null;
            case 2: return r.relation == CombatLink.CLAN;
            case 3: return r.relation == CombatLink.CLAN || r.relation == CombatLink.ALLY;
            case 4: return r.relation == CombatLink.ENEMY;
            case 5: return r.distance >= 0 && (max == 0 || r.distance <= max);
            default: return true;
        }
    }

    /** Chave do grupo, como o titulo aparece. */
    private static String groupKey(int group, Ref r) {
        return TabList.groupTitle(group, r.clan, r.relation);
    }

    /** Posicao que o grupo deve ter: por cla, o meu, aliados, os outros, sem cla; por relacao, na ordem do titulo. */
    private static int groupRank(int group, String key, Map<String, Integer> clanRanks) {
        if (group == 1) {
            return key.equals(T.tr("tab.noClan")) ? 3 : clanRanks.getOrDefault(key, 2);
        }
        if (key.equals(T.tr("tab.myClan"))) {
            return 0;
        }
        if (key.equals(T.tr("tab.allies"))) {
            return 1;
        }
        return key.equals(T.tr("tab.enemies")) ? 2 : 3;
    }

    /** a antes de b (ou empatado) na ordem escolhida — conferido pela chave, sem o Comparator da lista. */
    private static boolean inOrder(int sort, Ref a, Ref b) {
        switch (sort) {
            case 1: {
                long pa = a.ping <= 0 ? Long.MAX_VALUE : a.ping;
                long pb = b.ping <= 0 ? Long.MAX_VALUE : b.ping;
                return pa <= pb;
            }
            case 2:
                return a.nick.compareToIgnoreCase(b.nick) <= 0;
            case 3: {
                double da = a.distance < 0 ? Double.MAX_VALUE : a.distance;
                double db = b.distance < 0 ? Double.MAX_VALUE : b.distance;
                return da <= db;
            }
            default:
                if (a.spectator != b.spectator) {
                    return !a.spectator;
                }
                int t = a.team.compareTo(b.team);
                return t < 0 || t == 0 && a.nick.compareTo(b.nick) <= 0;
        }
    }

    // ---- uma combinacao ----

    private void one(TabList list, HudConfig.Tab c, Map<String, Ref> refs, String mine, int expected) {
        lists++;
        String cfg = "[filtro " + c.filter + " raio " + c.radius + " ordem " + c.sort + " grupo " + c.group + " teto "
                + c.limit + " prefixo " + c.hidePrefix + " sufixo " + c.hideSuffix + "] ";
        List<TabList.Line> out = list.collect();
        int limit = TabList.LIMITS[c.limit];
        check(list.total == refs.size(), cfg + "total " + list.total + ", o servidor tem " + refs.size());
        check(list.kept == expected, cfg + "passaram " + list.kept + ", deviam passar " + expected);

        List<TabList.Line> players = new ArrayList<>();
        for (TabList.Line l : out) {
            if (l.player != null) {
                players.add(l);
            }
        }
        check(players.size() == Math.min(limit, expected),
                cfg + players.size() + " na lista, deviam ser " + Math.min(limit, expected));

        // Cada um que esta la passa no filtro; ninguem repetido.
        Set<String> seen = new HashSet<>();
        for (TabList.Line l : players) {
            Ref r = refs.get(l.nick);
            if (!check(r != null, cfg + l.nick + " nao esta na lista do servidor")) {
                return;
            }
            check(seen.add(l.nick), cfg + l.nick + " aparece duas vezes");
            check(passes(c.filter, c.radius, r), cfg + l.nick + " nao passa no filtro (cla " + r.clan + ", relacao "
                    + r.relation + ", distancia " + r.distance + ")");
            names(c, l, cfg);
        }

        // Grupos: cada titulo uma vez, na ordem certa, com a contagem certa e so gente do grupo embaixo.
        Map<String, Integer> clanRanks = new HashMap<>();
        for (Ref r : refs.values()) {
            if (r.clan != null && passes(c.filter, c.radius, r)) {
                int rank = r.relation == CombatLink.CLAN ? 0 : r.relation == CombatLink.ALLY ? 1 : 2;
                clanRanks.merge(r.clan, rank, Math::min);
            }
        }
        if (c.group == 0) {
            for (TabList.Line l : out) {
                check(l.player != null, cfg + "sem agrupar, mas ha titulo ou linha vazia na lista");
            }
            for (int i = 1; i < players.size(); i++) {
                Ref a = refs.get(players.get(i - 1).nick);
                Ref b = refs.get(players.get(i).nick);
                check(inOrder(c.sort, a, b), cfg + "fora de ordem: " + a.nick + " antes de " + b.nick);
            }
        } else {
            Set<String> titles = new HashSet<>();
            String current = null;
            int lastRank = -1;
            int counted = 0;
            TabList.Line title = null;
            Ref previous = null;
            for (int i = 0; i < out.size(); i++) {
                TabList.Line l = out.get(i);
                if (l.player == null && l.title == null) {
                    check(i > 0 && i + 1 < out.size() && out.get(i + 1).title != null,
                            cfg + "linha vazia fora do lugar (posicao " + i + ")");
                    continue;
                }
                if (l.title != null) {
                    if (title != null) {
                        check(title.count == counted, cfg + "grupo " + title.title + " diz " + title.count + ", tem "
                                + counted);
                    }
                    check(titles.add(l.title), cfg + "grupo " + l.title + " aparece duas vezes");
                    int rank = groupRank(c.group, l.title, clanRanks);
                    check(rank >= lastRank, cfg + "grupo " + l.title + " fora de ordem");
                    lastRank = rank;
                    title = l;
                    current = l.title;
                    counted = 0;
                    previous = null;
                    continue;
                }
                if (!check(current != null, cfg + l.nick + " antes do primeiro titulo")) {
                    break;
                }
                Ref r = refs.get(l.nick);
                check(groupKey(c.group, r).equals(current), cfg + l.nick + " no grupo " + current + ", devia estar em "
                        + groupKey(c.group, r));
                if (previous != null) {
                    check(inOrder(c.sort, previous, r), cfg + "fora de ordem no grupo " + current + ": "
                            + previous.nick + " antes de " + r.nick);
                }
                previous = r;
                counted++;
            }
            if (title != null) {
                check(title.count == counted, cfg + "grupo " + title.title + " diz " + title.count + ", tem " + counted);
            }
            // Agrupado por cla, com gente do meu cla na lista, o meu cla e o primeiro grupo.
            if (c.group == 1 && mine != null && !players.isEmpty() && players.get(0) != null) {
                boolean mineIn = false;
                for (TabList.Line l : players) {
                    mineIn |= mine.equals(l.clan);
                }
                if (mineIn) {
                    check(mine.equals(out.get(0).title), cfg + "o meu cla (" + mine + ") nao e o primeiro grupo, e "
                            + out.get(0).title);
                }
            }
        }
        layouts(c, out, cfg);
    }

    /** Prefixo e sufixo: escondido nao aparece, mostrado continua la. */
    private void names(HudConfig.Tab c, TabList.Line l, String cfg) {
        String full = l.player.tabName;
        int at = full.indexOf(l.nick);
        if (at < 0) {
            check(l.name.equals(full), cfg + l.nick + ": nome trocado pelo servidor, e a lista mexeu nele");
            return;
        }
        String expected = (c.hidePrefix ? "" : full.substring(0, at)) + l.nick
                + (c.hideSuffix ? "" : full.substring(at + l.nick.length()));
        check(l.name.equals(expected), cfg + l.nick + ": nome \"" + TabList.strip(l.name) + "\", devia ser \""
                + TabList.strip(expected) + "\"");
    }

    // ---- a tela ----

    /** Listas iguais para o desenho (mesmo tamanho, mesma largura de nome) so se conferem uma vez. */
    private final Set<String> laidOut = new HashSet<>();

    private void layouts(HudConfig.Tab c, List<TabList.Line> out, String cfg) {
        String empty = out.isEmpty() ? T.tr("tab.nobody") : null;
        int nameW = TabList.nameWidth(out, empty);
        boolean note = c.filter != 0;
        if (!laidOut.add(out.size() + "/" + nameW + "/" + note)) {
            return;
        }
        String header = Players.tabHeader();
        String footer = Players.tabFooter();
        HudConfig.Tab v = new HudConfig.Tab();
        v.copyFrom(c);
        for (int columns = 0; columns < TabList.COLUMN_NAMES.length; columns++) {
            for (int hf = 0; hf < 2; hf++) {
                for (int ping = 0; ping < TabList.PING_MODES.length; ping++) {
                    for (int look = 0; look < 4; look++) {
                        for (int[] screen : SCREENS) {
                            v.columns = columns;
                            v.headerFooter = hf == 1;
                            v.ping = ping;
                            v.heads = (look & 1) != 0;
                            v.distance = (look & 2) != 0;
                            layout(v, out, nameW, screen, v.headerFooter ? header : null,
                                    v.headerFooter ? footer : null, note, cfg);
                        }
                    }
                }
            }
        }
    }

    private void layout(HudConfig.Tab v, List<TabList.Line> out, int nameW, int[] screen,
            String header, String footer, boolean note, String cfg) {
        layouts++;
        TabList.Layout at = TabList.layout(out, nameW, 0, screen[0], screen[1], header, footer, note, v);
        String where = cfg + "[tela " + screen[0] + "x" + screen[1] + " colunas " + v.columns + " cabecalho "
                + v.headerFooter + " ping " + v.ping + " cabecas " + v.heads + " distancia " + v.distance + "] ";
        int size = out.size();
        check(at.scale >= TabList.MIN_SCALE - 0.001F && at.scale <= 1.0001F, where + "escala " + at.scale);
        check(at.columns >= 1 && at.rows >= 1, where + at.columns + " colunas, " + at.rows + " linhas");
        check(at.shown == Math.min(size, at.rows * at.columns), where + "mostra " + at.shown + " de " + size);
        check(at.shown + at.hidden == size, where + at.shown + " + " + at.hidden + " escondidos != " + size);
        check(at.hidden == 0 || at.scale <= TabList.MIN_SCALE + 0.001F,
                where + at.hidden + " escondidos numa escala " + at.scale + " que ainda podia diminuir");
        check(at.bottom <= at.logicalH, where + "passa do fim da tela: termina em " + at.bottom + " de "
                + at.logicalH);
        check(at.totalW + 4 <= at.logicalW, where + "passa da largura: " + (at.totalW + 4) + " de " + at.logicalW);
        // Colunas fixas: as pedidas quando cabem na largura (na menor escala); quando nem la cabem, as que couberem.
        int fixed = TabList.COLUMNS[v.columns];
        if (fixed > 0) {
            int room = Math.max(1, ((int) (screen[0] / TabList.MIN_SCALE) - 8) / (at.cellW + 5));
            int want = Math.min(Math.min(fixed, Math.max(1, size)), room);
            check(at.columns == want || at.hidden > 0 && at.columns == Math.min(fixed, room),
                    where + "pediu " + fixed + " colunas (cabem " + room + ") e saiu " + at.columns);
        }
    }

    // ---- o Painel ----

    /** Cada opcao da pagina por todos os valores, nos dois sentidos, e o Restaurar. */
    private void panel(SteinHud hud, HudConfig.Tab c) {
        c.reset();
        // No padrao quem desenha e o jogo, e ai a pagina e so a escolha da lista e o restaurar dela: nenhuma
        // opcao nossa vale, entao nenhuma aparece.
        Page server = new Page("HUD");
        hud.onPage(server);
        int shown = 0;
        for (Page.Entry e : server.entries()) {
            if (e.option != null) {
                shown++;
            }
        }
        check(shown == 2, "Painel: com a lista do servidor a pagina tem " + shown + " linhas, deviam ser 2"
                + " (a escolha e o restaurar)");
        c.own = true;
        Page page = new Page("HUD");
        hud.onPage(page);
        String defaults = new HudConfig.Tab().describe();
        int options = 0;
        List<Option> restores = new ArrayList<>();
        String restoreLabel = Lang.tr("steinloader.restore");
        for (Page.Entry e : page.entries()) {
            if (e.option == null) {
                continue;
            }
            Option o = e.option;
            if (o.label.equals(restoreLabel)) {
                restores.add(o);
                continue;
            }
            options++;
            String before = c.describe();
            String first = o.value();
            Set<String> values = new HashSet<>();
            for (int i = 0; i < 12; i++) {
                String value = o.value();
                check(!value.equals("?"), "Painel: \"" + o.label + "\" mostra \"?\" (" + c.describe() + ")");
                values.add(value);
                HudConfig.Tab copy = new HudConfig.Tab();
                copy.copyFrom(c);
                copy.repair();
                check(copy.describe().equals(c.describe()), "Painel: \"" + o.label + "\" deixou valor fora da faixa: "
                        + c.describe());
                o.click(true);
                if (o.value().equals(first)) {
                    break;
                }
            }
            check(o.value().equals(first) && c.describe().equals(before),
                    "Painel: \"" + o.label + "\" nao volta ao comeco depois de passar por todos os valores");
            o.click(false);
            o.click(true);
            check(c.describe().equals(before), "Painel: \"" + o.label + "\" para tras e para frente nao volta");
            check(values.size() >= 2, "Painel: \"" + o.label + "\" nao muda de valor");
            o.click(true);   // deixa fora do padrao, para o Restaurar ter o que fazer
        }
        check(options >= 13, "Painel: so " + options + " opcoes na pagina da lista");
        if (check(restores.size() == 4, "Painel: " + restores.size() + " restaurar, deviam ser 4 (um por secao)")) {
            check(!c.describe().equals(defaults), "Painel: as opcoes nao sairam do padrao antes do Restaurar");
            for (Option r : restores) {
                String before = c.describe();
                r.click(true);   // o primeiro clique so pede confirmacao
                check(c.describe().equals(before), "Painel: restaurar mudou algo sem confirmar");
                r.click(true);   // o segundo restaura
            }
            check(c.describe().equals(defaults), "Painel: Restaurar deixou " + c.describe() + ", o padrao e "
                    + defaults);
        }
        System.out.println(TAG + "Painel: " + options + " opcoes clicadas por todos os valores, Restaurar "
                + (c.describe().equals(defaults) ? "ok" : "FALHOU"));
    }
}
