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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.xavier.stein.loader.api.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

/**
 * Reads and writes {@link HudConfig}. Not the Loader's {@code ModConfig} on purpose: the HUD's file
 * has nested objects (one per element, plus the Tab list) that are salvaged field by field too, the
 * defaults come from the packaged {@code defaults.json} rather than from the field initializers,
 * and old files need a migration (see {@link #load}). The file is the same {@code
 * config/steinhud.json} it always was.
 */
final class HudConfigFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The HUD's defaults (without a file, and what "Restore defaults" brings back). */
    static final String DEFAULTS = "/assets/steinhud/defaults.json";

    private HudConfigFile() {}

    /**
     * Reads the file field by field: a field from an old version with another type ("limit": true,
     * from when the cap was yes or no) goes back to its default alone, and the rest stays. Reading
     * the file at once used to throw away the whole configuration for a field like that, which the
     * first save then overwrote with the defaults.
     */
    static HudConfig load(File file, Log log) {
        if (file.isFile()) {
            try (Reader r =
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonElement tree = new JsonParser().parse(r);
                if (tree != null && tree.isJsonObject()) {
                    HudConfig c = lenient(tree.getAsJsonObject(), HudConfig.class, "", log);
                    // Up to 0.1.1 the Tab list was always ours, with no way to choose. Whoever
                    // already had a configuration keeps it; the new default (the server's) is for
                    // fresh installs.
                    JsonElement tab = tree.getAsJsonObject().get("tab");
                    if (!(tab != null && tab.isJsonObject() && tab.getAsJsonObject().has("own"))) {
                        c.tab.own = true;
                    }
                    c.repair();
                    return c;
                }
            } catch (Exception e) {
                log.warn("unreadable configuration, using the defaults: " + e);
            }
        }
        return defaults(log);
    }

    /**
     * A new configuration with the packaged defaults ({@link #DEFAULTS}); without them, the
     * fields'.
     */
    static HudConfig defaults() {
        return defaults(null);
    }

    private static HudConfig defaults(Log log) {
        try (InputStream in = HudConfig.class.getResourceAsStream(DEFAULTS)) {
            if (in != null) {
                JsonElement tree =
                        new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (tree != null && tree.isJsonObject()) {
                    HudConfig c = lenient(tree.getAsJsonObject(), HudConfig.class, "", log);
                    c.repair();
                    return c;
                }
            }
        } catch (Exception e) {
            if (log != null) {
                log.warn("unreadable packaged defaults: " + e);
            }
        }
        return new HudConfig();
    }

    /**
     * An element's default: the packaged defaults' field of the same class (without it, a new one).
     */
    static HudConfig.Placement defaultFor(HudConfig.Placement p) {
        HudConfig d = defaults();
        for (Field f : HudConfig.class.getDeclaredFields()) {
            if (f.getType() == p.getClass()) {
                try {
                    f.setAccessible(true);
                    return (HudConfig.Placement) f.get(d);
                } catch (ReflectiveOperationException e) {
                    break;
                }
            }
        }
        return p.fresh();
    }

    /** A settings object of ours (an element's or the Tab list's), salvaged field by field. */
    private static boolean ours(Class<?> type) {
        return type == TabConfig.class || type.getEnclosingClass() == HudConfig.class;
    }

    /**
     * An object with the constructor's defaults and, on top, each field of the file of the right
     * type.
     */
    private static <T> T lenient(JsonObject o, Class<T> type, String path, Log log)
            throws Exception {
        Constructor<T> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        T out = ctor.newInstance();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m) || !o.has(f.getName())) {
                    continue;
                }
                f.setAccessible(true);
                JsonElement v = o.get(f.getName());
                try {
                    f.set(out, GSON.fromJson(v, f.getGenericType()));
                } catch (RuntimeException e) {
                    // One of our objects (tab, armor...): what can be used of it; another type
                    // stays at the default.
                    if (v.isJsonObject() && ours(f.getType())) {
                        f.set(
                                out,
                                lenient(
                                        v.getAsJsonObject(),
                                        f.getType(),
                                        path + f.getName() + ".",
                                        log));
                    } else if (log != null) {
                        log.warn(
                                path
                                        + f.getName()
                                        + " is from another version, back to the default");
                    }
                }
            }
        }
        return out;
    }

    static void save(HudConfig config, File file, Log log) {
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        try (Writer w =
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(config, w);
        } catch (IOException e) {
            log.error("could not save the configuration", e);
        }
    }
}
