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
        if (now - next >= 0) {
            next = now + SECOND;
            low = frames.low();
            worst = frames.worst();
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
            lines.add(
                    line(
                            label,
                            T.tr("stats.worstShort"),
                            ms < 0 ? label + "--" : worstColor(ms) + fmtMs(ms)));
        }
        if (c.ping) {
            int ping = ping();
            if (ping < 0 && preview) {
                ping = 42;
            }
            if (ping >= 0) {
                String v =
                        (c.colorByValue
                                        ? Option.fmt(ping <= 80 ? "a" : ping <= 150 ? "e" : "c")
                                        : label)
                                + ping
                                + " ms";
                lines.add(line(label, "Ping", v));
            }
        }
        if (c.cpu) {
            lines.add(line(label, "CPU", percent(sensors.cpu(), label)));
        }
        if (c.gpu) {
            lines.add(line(label, "GPU", percent(sensors.gpu(), label)));
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

    /** Usage in percent, green up to 70, yellow up to 90, red above; "--" without a measure. */
    private String percent(double v, String label) {
        if (v < 0) {
            return label + "--";
        }
        int p = (int) Math.round(v);
        return (c.colorByValue ? Option.fmt(p < 70 ? "a" : p < 90 ? "e" : "c") : label) + p + "%";
    }

    /** The computer's RAM in use and total, in GB; "--" without the information. */
    private String ram(String label) {
        long[] m = sensors.systemMemory();
        if (m == null || m[1] <= 0) {
            return label + "--";
        }
        int p = (int) Math.min(100, m[0] * 100 / m[1]);
        String color = c.colorByValue ? Option.fmt(p < 70 ? "a" : p < 90 ? "e" : "c") : label;
        double g = 1024.0 * 1024 * 1024;
        return color + T.num(String.format(Locale.ROOT, "%.1f/%.1f GB", m[0] / g, m[1] / g));
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
            int x = alignRight ? w - 3 - Render.textWidth(s) : 3;
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
