/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Game;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Performance in a single block, like the effects list: FPS, 1% low, peak, ping, CPU, GPU, RAM and
 * CPS, one line per measure (or all on one line), each one on or off. The numbers come out every
 * second; the CPS, every frame.
 *
 * <ul>
 *   <li>FPS: the F3 number. 1% low and peak (the longest frame of the last second, in ms: the
 *       stutter the average FPS hides): {@link FrameTimes}, without the frames of the pause menu,
 *       where the Stein Bridge holds the frame rate down; back in the game they update at once.
 *   <li>The color comes from the machine's target (the monitor's refresh rate, or the FPS limit if
 *       lower), not from a fixed 60/30.
 *   <li>Ping: the one the client measures itself ({@link Server#latency}), like the server list
 *       does; until the first measure, the player list's, if the server writes one (many leave 0).
 *       Not shown in singleplayer.
 *   <li>CPU and GPU: the whole system's, the Task Manager's numbers ({@link Sensors}).
 *   <li>RAM: the computer's (the Task Manager's), in use and total, in GB. Never the game's: Java's
 *       goes up and down with garbage collection and tells a player nothing.
 *   <li>CPS: clicks per second in the world, the left button and, if chosen, the right; no limit.
 * </ul>
 */
final class StatsBlock extends HudElement {

    private static final String[] STYLES = {"FPS: 120", "120 FPS"};
    private static final String[] LAYOUTS = {"vertical", "horizontal"};
    private static final String[] CPS_MODES = {"off", "stats.cps.left", "stats.cps.both"};
    private static final long SECOND = 1_000_000_000L;
    private static final int LINE = 10;

    private final HudConfig.Stats c;
    private final List<String> lines = new ArrayList<>();
    private final FrameTimes frames = new FrameTimes();
    private final Sensors sensors;
    private final ClickCounter left = new ClickCounter();
    private final ClickCounter right = new ClickCounter();
    private long next;
    private boolean paused;
    private int low = -1;

    /** The worst frame of the last second, in ms; -1 without a measure. */
    private float worst = -1;

    /**
     * Frames per second the monitor (or the FPS limit) allows: the target that colors the numbers.
     */
    private int target = 60;

    /** How many numbers {@link #key} holds. */
    private static final int KEYS = 14;

    // The numbers and settings the lines were built from (key: this frame's; builtKey: the lines').
    private final int[] key = new int[KEYS];
    private final int[] builtKey = new int[KEYS];
    private String builtColor;
    private boolean built;
    private int[] lineWidths = new int[12];

    // The computer's memory, sampled once a second (ramSample counts the samples).
    private long ramUsed = -1;
    private long ramTotal = -1;
    private int ramSample;
    private boolean ramSampled;

    StatsBlock(HudConfig.Stats c, SteinHud mod) {
        super("stats", c, mod);
        this.c = c;
        this.sensors = new Sensors(mod.log());
    }

    /** A button pressed in the world (0 left, 1 right), from the mod's onMouseInput. */
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
            boolean pause = Screens.currentKind() == Screens.Kind.PAUSE;
            if (pause) {
                frames.pause();
            } else {
                frames.frame(now);
                if (paused) {
                    next = now; // back in the game: no waiting a second with the menu's numbers
                }
            }
            paused = pause;
        }
        boolean second = now - next >= 0;
        if (second) {
            next = now + SECOND;
            low = frames.low();
            worst = frames.worst();
            target = target();
        }
        // The RAM is a call into Windows: once a second, with the other numbers, and at once when
        // it is turned on.
        if (c.ram == 0) {
            ramSampled = false;
        } else if (second || !ramSampled) {
            sampleRam();
        }
        int fps = c.fps ? Game.fps() : 0;
        int ping = c.ping ? ping() : 0;
        if (ping < 0 && preview) {
            ping = 42;
        }
        int cpu = c.cpu ? percentOf(sensors.cpu()) : 0;
        int gpu = c.gpu ? percentOf(sensors.gpu()) : 0;
        int cpsLeft = c.cps > 0 ? left.count(now) : 0;
        int cpsRight = c.cps == 2 ? right.count(now) : 0;
        // The lines change a few times a second at most: built again only when a number in them
        // (or a setting) changed, instead of formatting and measuring them every frame.
        int[] k = key;
        k[0] = fps;
        k[1] = low;
        k[2] = Float.floatToIntBits(worst);
        k[3] = target;
        k[4] = ping;
        k[5] = cpu;
        k[6] = gpu;
        k[7] = ramSample;
        k[8] = cpsLeft;
        k[9] = cpsRight;
        k[10] =
                (c.fps ? 1 : 0)
                        | (c.low ? 2 : 0)
                        | (c.worst ? 4 : 0)
                        | (c.ping ? 8 : 0)
                        | (c.cpu ? 16 : 0)
                        | (c.gpu ? 32 : 0)
                        | (c.ram != 0 ? 64 : 0)
                        | (c.horizontal ? 128 : 0)
                        | (c.colorByValue ? 256 : 0)
                        | (preview ? 512 : 0);
        k[11] = c.style;
        k[12] = c.cps;
        k[13] = textEpoch();
        if (built && c.color == builtColor && Arrays.equals(k, builtKey)) {
            return !lines.isEmpty();
        }
        System.arraycopy(k, 0, builtKey, 0, KEYS);
        builtColor = c.color;
        built = true;
        build(preview, fps, ping, cpu, gpu, cpsLeft, cpsRight);
        if (lines.isEmpty()) {
            return false;
        }
        if (c.horizontal) {
            String all = String.join("   ", lines);
            lines.clear();
            lines.add(all);
        }
        if (lineWidths.length < lines.size()) {
            lineWidths = new int[lines.size()];
        }
        int max = 0;
        for (int i = 0; i < lines.size(); i++) {
            lineWidths[i] = Render.textWidth(lines.get(i));
            max = Math.max(max, lineWidths[i]);
        }
        w = max + 6;
        h = c.horizontal ? 12 : lines.size() * LINE + 2;
        return true;
    }

    /** The lines, from this frame's numbers. */
    private void build(
            boolean preview, int fps, int ping, int cpu, int gpu, int cpsLeft, int cpsRight) {
        lines.clear();
        String label = Option.fmt(c.color);
        if (c.fps) {
            lines.add(line(label, "FPS", fpsColor(fps) + fps));
        }
        if (c.low) {
            lines.add(line(label, T.tr("stats.low"), low < 0 ? label + "--" : fpsColor(low) + low));
        }
        if (c.worst) {
            float ms = worst < 0 && preview ? 2000.0F / target : worst;
            lines.add(
                    line(
                            label,
                            T.tr("stats.worstShort"),
                            ms < 0 ? label + "--" : worstColor(ms) + fmtMs(ms)));
        }
        if (c.ping && ping >= 0) {
            String v =
                    (c.colorByValue
                                    ? Option.fmt(ping <= 80 ? "a" : ping <= 150 ? "e" : "c")
                                    : label)
                            + ping
                            + " ms";
            lines.add(line(label, "Ping", v));
        }
        if (c.cpu) {
            lines.add(line(label, "CPU", percent(cpu, label)));
        }
        if (c.gpu) {
            lines.add(line(label, "GPU", percent(gpu, label)));
        }
        if (c.ram != 0) {
            lines.add(line(label, "RAM", ram(label)));
        }
        if (c.cps > 0) {
            String v = cpsLeft + (c.cps == 2 ? " | " + cpsRight : "");
            lines.add(line(label, "CPS", label + v));
        }
    }

    private void sampleRam() {
        long[] m = sensors.systemMemory();
        if (m == null || m[1] <= 0) {
            ramUsed = -1;
            ramTotal = -1;
        } else {
            ramUsed = m[0];
            ramTotal = m[1];
        }
        ramSample++;
        ramSampled = true;
    }

    private String line(String label, String name, String value) {
        return c.style == 1 ? value + " " + label + name : label + name + ": " + value;
    }

    /**
     * The target frames per second: the monitor's refresh rate, or the game's FPS limit when lower
     * (260 = unlimited). The color comes from it: on a 144 Hz monitor, 70 FPS is not "good" just
     * because it passed 60.
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

    /** Green while the number keeps up with the target, yellow down to half of it, red below. */
    private String fpsColor(int fps) {
        return c.colorByValue
                ? Option.fmt(fps >= target * 0.95F ? "a" : fps >= target * 0.5F ? "e" : "c")
                : Option.fmt(c.color);
    }

    /**
     * A second's worst frame almost always exceeds a frame's budget (1000/target ms), and a single
     * delay does not show: green up to twice the budget, yellow up to four times, red above (a
     * stutter you see).
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

    /** A usage as the whole percent shown; -1 without a measure. */
    private static int percentOf(double v) {
        return v < 0 ? -1 : (int) Math.round(v);
    }

    /** Usage in percent, green up to 70, yellow up to 90, red above; "--" without a measure. */
    private String percent(int p, String label) {
        if (p < 0) {
            return label + "--";
        }
        return (c.colorByValue ? Option.fmt(p < 70 ? "a" : p < 90 ? "e" : "c") : label) + p + "%";
    }

    /** The computer's RAM in use and total, in GB; "--" without the information. */
    private String ram(String label) {
        if (ramTotal <= 0) {
            return label + "--";
        }
        int p = (int) Math.min(100, ramUsed * 100 / ramTotal);
        String color = c.colorByValue ? Option.fmt(p < 70 ? "a" : p < 90 ? "e" : "c") : label;
        double g = 1024.0 * 1024 * 1024;
        return color + T.num(String.format(Locale.ROOT, "%.1f/%.1f GB", ramUsed / g, ramTotal / g));
    }

    /**
     * Ping in ms: the client's measure; until there is one, the Tab list's if the server writes one
     * (0 is many servers' "don't know", not a real ping); -1 outside a server or without either.
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
            int x = alignRight ? w - 3 - lineWidths[i] : 3;
            Render.text(s, x, 2 + i * LINE, 0xFFFFFF, true);
        }
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(
                Option.cycle(
                        T.tr("style"),
                        STYLES,
                        () -> Math.max(0, Math.min(1, c.style)),
                        i -> c.style = i));
        out.add(
                Option.cycle(
                        T.tr("layout"),
                        T.all(LAYOUTS),
                        () -> c.horizontal ? 1 : 0,
                        i -> c.horizontal = i == 1));
        out.add(Option.toggle("FPS", () -> c.fps, v -> c.fps = v));
        out.add(Option.toggle(T.tr("stats.low"), () -> c.low, v -> c.low = v));
        out.add(Option.toggle(T.tr("stats.worst"), () -> c.worst, v -> c.worst = v));
        out.add(Option.toggle("Ping", () -> c.ping, v -> c.ping = v));
        out.add(Option.toggle("CPU", () -> c.cpu, v -> c.cpu = v));
        out.add(Option.toggle("GPU", () -> c.gpu, v -> c.gpu = v));
        out.add(Option.toggle("RAM", () -> c.ram != 0, v -> c.ram = v ? 1 : 0));
        out.add(
                Option.cycle(
                        "CPS",
                        T.all(CPS_MODES),
                        () -> Math.max(0, Math.min(2, c.cps)),
                        i -> c.cps = i));
        out.add(
                Option.toggle(
                        T.tr("stats.colorByValue"), () -> c.colorByValue, v -> c.colorByValue = v));
        out.add(Option.color(T.tr("color"), () -> c.color, v -> c.color = v));
    }
}
