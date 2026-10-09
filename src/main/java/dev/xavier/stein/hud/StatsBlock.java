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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import dev.xavier.stein.loader.api.Game;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Server;

/**
 * Desempenho num bloco so, como a lista de efeitos: FPS, 1% low, pico, ping, CPU, GPU, RAM e CPS, uma linha por
 * medida (ou tudo numa linha so), cada uma que se liga ou desliga. Os numeros saem a cada segundo; o CPS, a cada
 * quadro.
 *
 * <ul>
 *   <li>FPS: o numero do F3. 1% low: a media do 1% de quadros mais lentos dos ultimos 10 s.
 *   <li>Pico: o quadro mais demorado do ultimo segundo, em ms — o engasgo que o FPS medio esconde.
 *   <li>A cor sai do alvo da maquina (a taxa do monitor, ou o limite de FPS se for menor), e nao de 60/30 fixos.
 *   <li>Ping: o medido pelo proprio cliente ({@link Server#latency}), como a lista de servidores mede; ate a
 *       primeira medida, o da lista de jogadores, se o servidor escrever um (muitos deixam 0). Nao aparece em
 *       singleplayer.
 *   <li>CPU e GPU: do sistema inteiro, os numeros do Gerenciador de Tarefas ({@link Sensors}).
 *   <li>RAM: a do computador (a do Gerenciador de Tarefas), em uso e total, em GB. Nunca a do jogo: a do Java
 *       sobe e desce com a coleta de lixo e nao diz nada a quem joga.
 *   <li>CPS: cliques por segundo no mundo, do botao esquerdo e, se escolhido, do direito; sem limite de cliques.
 * </ul>
 */
final class StatsBlock extends HudElement {

    private static final String[] STYLES = {"FPS: 120", "120 FPS"};
    private static final String[] LAYOUTS = {"vertical", "horizontal"};
    private static final String[] CPS_MODES = {"off", "stats.cps.left", "stats.cps.both"};
    private static final long SECOND = 1_000_000_000L;
    private static final int LINE = 10;
    /** Quadros guardados para o 1% low (10 s de jogo bem rapido). */
    private static final int FRAMES = 16384;
    private static final float WINDOW_MS = 10_000.0F;

    private final HudConfig.Stats c;
    private final List<String> lines = new ArrayList<>();
    /** Duracao de cada quadro em ms, num anel, e a copia ordenada do calculo. */
    private final float[] frames = new float[FRAMES];
    private final float[] sorted = new float[FRAMES];
    private int head;
    private int count;
    private long lastFrame;
    private long next;
    private int low = -1;
    /** Pior quadro do ultimo segundo, em ms; -1 sem medida. */
    private float worst = -1;
    /** Quadros por segundo que o monitor (ou o limite de FPS) permite: o alvo que da cor aos numeros. */
    private int target = 60;
    private final Clicks left = new Clicks();
    private final Clicks right = new Clicks();

    StatsBlock(HudConfig.Stats c, SteinHud mod) {
        super("stats", c, mod);
        this.c = c;
    }

    /** Cliques de um botao no ultimo segundo. A lista cresce sozinha: nao ha teto de cliques por segundo. */
    private static final class Clicks {
        private long[] times = new long[64];
        private int head;

        void add(long now) {
            // O lugar que seria sobrescrito ainda esta no ultimo segundo: a lista dobra em vez de perder cliques.
            if (times[head] != 0 && now - times[head] < SECOND) {
                long[] bigger = new long[times.length * 2];
                for (int i = 0; i < times.length; i++) {
                    bigger[i] = times[(head + i) % times.length];
                }
                head = times.length;
                times = bigger;
            }
            times[head] = now;
            head = (head + 1) % times.length;
        }

        int count(long now) {
            int n = 0;
            for (long t : times) {
                if (t != 0 && now - t < SECOND) {
                    n++;
                }
            }
            return n;
        }
    }

    /** Botao apertado no mundo (0 esquerdo, 1 direito), pelo onMouseInput do mod. */
    void click(int button) {
        long now = System.nanoTime();
        if (button == 0) {
            left.add(now);
        } else if (button == 1) {
            right.add(now);
        }
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        long now = System.nanoTime();
        if (!preview) {
            frame(now);
        }
        if (now - next >= 0) {
            next = now + SECOND;
            low = computeLow();
            worst = computeWorst();
            target = target();
        }
        lines.clear();
        String label = Option.fmt(c.color);
        if (c.fps) {
            int fps = Game.fps();
            lines.add(line(label, "FPS", fpsColor(fps) + fps));
        }
        if (c.low) {
            lines.add(line(label, T.tr("stats.low"), low < 0 ? label + "--" : fpsColor(low) + low));
        }
        if (c.worst) {
            float ms = worst < 0 && preview ? 2000.0F / target : worst;
            lines.add(line(label, T.tr("stats.worstShort"), ms < 0 ? label + "--" : worstColor(ms) + fmtMs(ms)));
        }
        if (c.ping) {
            int ping = ping();
            if (ping < 0 && preview) {
                ping = 42;
            }
            if (ping >= 0) {
                String v = (c.colorByValue ? Option.fmt(ping <= 80 ? "a" : ping <= 150 ? "e" : "c") : label) + ping + " ms";
                lines.add(line(label, "Ping", v));
            }
        }
        if (c.cpu) {
            lines.add(line(label, "CPU", percent(Sensors.cpu(), label)));
        }
        if (c.gpu) {
            lines.add(line(label, "GPU", percent(Sensors.gpu(), label)));
        }
        if (c.ram != 0) {
            lines.add(line(label, "RAM", ram(label)));
        }
        if (c.cps > 0) {
            String v = left.count(now) + (c.cps == 2 ? " | " + right.count(now) : "");
            lines.add(line(label, "CPS", label + v));
        }
        if (lines.isEmpty()) {
            return false;
        }
        if (c.horizontal) {
            String all = String.join("   ", lines);
            lines.clear();
            lines.add(all);
            w = Render.textWidth(all) + 6;
            h = 12;
            return true;
        }
        int max = 0;
        for (String s : lines) {
            max = Math.max(max, Render.textWidth(s));
        }
        w = max + 6;
        h = lines.size() * LINE + 2;
        return true;
    }

    /** Intervalo entre este quadro e o anterior; escondido por mais de um segundo, a medida recomeca. */
    private void frame(long now) {
        if (lastFrame != 0) {
            long dt = now - lastFrame;
            if (dt < SECOND) {
                frames[head] = dt / 1_000_000.0F;
                head = (head + 1) % FRAMES;
                count = Math.min(count + 1, FRAMES);
            } else {
                count = 0;
            }
        }
        lastFrame = now;
    }

    /** Pior quadro (o mais demorado) do ultimo segundo, em ms; -1 com poucos quadros. */
    private float computeWorst() {
        float total = 0;
        float max = -1;
        int k = 0;
        for (int i = 1; i <= count && total < 1000.0F; i++) {
            float f = frames[(head - i + FRAMES) % FRAMES];
            total += f;
            k++;
            if (f > max) {
                max = f;
            }
        }
        return k < 5 ? -1 : max;
    }

    /** FPS do 1% mais lento dos quadros da janela; -1 com poucos quadros. */
    private int computeLow() {
        int k = 0;
        float total = 0;
        for (int i = 1; i <= count && total < WINDOW_MS; i++) {
            float f = frames[(head - i + FRAMES) % FRAMES];
            sorted[k++] = f;
            total += f;
        }
        if (k < 20) {
            return -1;
        }
        Arrays.sort(sorted, 0, k);
        int n = Math.max(1, k / 100);
        float slow = 0;
        for (int i = k - n; i < k; i++) {
            slow += sorted[i];
        }
        return Math.round(1000.0F * n / slow);
    }

    private String line(String label, String name, String value) {
        return c.style == 1 ? value + " " + label + name : label + name + ": " + value;
    }

    /**
     * Alvo de quadros por segundo: a taxa do monitor, ou o limite de FPS do jogo quando ele for menor (260 = sem
     * limite). E dele que sai a cor — num monitor de 144 Hz, 70 FPS nao e "bom" so porque passou de 60.
     */
    private static int target() {
        int refresh = Game.refreshRate();
        int t = refresh > 0 ? refresh : 60;
        int limit = Game.fpsLimit();
        if (limit > 0 && limit < t) {
            t = limit;
        }
        return Math.max(10, t);
    }

    /** Verde enquanto o numero acompanha o alvo, amarelo ate a metade dele, vermelho abaixo. */
    private String fpsColor(int fps) {
        return c.colorByValue ? Option.fmt(fps >= target * 0.95F ? "a" : fps >= target * 0.5F ? "e" : "c")
                : Option.fmt(c.color);
    }

    /**
     * O pior quadro de um segundo quase sempre passa do orcamento de um quadro (1000/alvo ms), e um atraso sozinho
     * nao se ve: verde ate o dobro do orcamento, amarelo ate o quadruplo, vermelho acima — engasgo que aparece.
     */
    private String worstColor(float ms) {
        if (!c.colorByValue) {
            return Option.fmt(c.color);
        }
        float budget = 1000.0F / target;
        return Option.fmt(ms <= budget * 2 ? "a" : ms <= budget * 4 ? "e" : "c");
    }

    private static String fmtMs(float ms) {
        return T.num(String.format(Locale.ROOT, "%.1f", ms)) + " ms";
    }

    /** Uso em porcentagem, verde ate 70, amarelo ate 90, vermelho acima; "--" sem medida. */
    private String percent(double v, String label) {
        if (v < 0) {
            return label + "--";
        }
        int p = (int) Math.round(v);
        return (c.colorByValue ? Option.fmt(p < 70 ? "a" : p < 90 ? "e" : "c") : label) + p + "%";
    }

    /** A RAM do computador em uso e total, em GB; "--" sem a informacao. */
    private String ram(String label) {
        long[] m = Sensors.systemMemory();
        if (m == null || m[1] <= 0) {
            return label + "--";
        }
        int p = (int) Math.min(100, m[0] * 100 / m[1]);
        String color = c.colorByValue ? Option.fmt(p < 70 ? "a" : p < 90 ? "e" : "c") : label;
        double g = 1024.0 * 1024 * 1024;
        return color + T.num(String.format(Locale.ROOT, "%.1f/%.1f GB", m[0] / g, m[1] / g));
    }

    /**
     * Ping em ms: o medido pelo cliente; sem ele ainda, o do Tab se o servidor escrever um (0 e o "nao sei" de muito
     * servidor, nao um ping de verdade); -1 fora de um servidor ou sem nenhum dos dois.
     */
    private static int ping() {
        if (!Server.isMultiplayer()) {
            return -1;
        }
        int measured = Server.latency();
        if (measured >= 0) {
            return measured;
        }
        String me = Players.self();
        int tab = me == null ? -1 : Players.ping(me);
        return tab > 0 ? tab : -1;
    }

    @Override
    void draw(boolean alignRight) {
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            int x = alignRight ? w - 3 - Render.textWidth(s) : 3;
            Render.text(s, x, 2 + i * LINE, 0xFFFFFF, true);
        }
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(Option.cycle(T.tr("style"), STYLES, () -> Math.max(0, Math.min(1, c.style)), i -> c.style = i));
        out.add(Option.cycle(T.tr("layout"), T.all(LAYOUTS), () -> c.horizontal ? 1 : 0, i -> c.horizontal = i == 1));
        out.add(Option.toggle("FPS", () -> c.fps, v -> c.fps = v));
        out.add(Option.toggle(T.tr("stats.low"), () -> c.low, v -> c.low = v));
        out.add(Option.toggle(T.tr("stats.worst"), () -> c.worst, v -> c.worst = v));
        out.add(Option.toggle("Ping", () -> c.ping, v -> c.ping = v));
        out.add(Option.toggle("CPU", () -> c.cpu, v -> c.cpu = v));
        out.add(Option.toggle("GPU", () -> c.gpu, v -> c.gpu = v));
        out.add(Option.toggle("RAM", () -> c.ram != 0, v -> c.ram = v ? 1 : 0));
        out.add(Option.cycle("CPS", T.all(CPS_MODES), () -> Math.max(0, Math.min(2, c.cps)), i -> c.cps = i));
        out.add(Option.toggle(T.tr("stats.colorByValue"), () -> c.colorByValue, v -> c.colorByValue = v));
        out.add(Option.color(T.tr("color"), () -> c.color, v -> c.color = v));
    }
}
