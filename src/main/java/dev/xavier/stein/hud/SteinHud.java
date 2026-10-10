/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import dev.xavier.stein.loader.api.Game;
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.SteinMod;

import java.io.File;

/**
 * A customizable HUD on the Stein Loader's HUD grid: armor, effects, compass, performance, minimap,
 * inventory and target, plus our own Tab list. Right Shift opens the grid editor, which belongs to
 * the Loader (the action bar, the boss bar and the scoreboard live there too).
 *
 * <p>Redoes what ArmorStatusHUD, StatusEffectHUD and DirectionHUD (bspkrs) did, with code of its
 * own: their settings became each element's options, and their offset alignment became dragging on
 * the grid.
 */
public final class SteinHud implements SteinMod {

    private final ModContext ctx = ModContext.of(this);
    private final Log log = ctx.log();
    HudConfig config = new HudConfig();
    private File configFile;
    private StatsBlock stats;
    private TabList tabList;
    private Minimap minimap;
    private TargetHud target;
    private TabDiagnostics diagnostics;

    /** This frame's Tab list request: the screen width (-1 without a request). */
    private int pendingWidth = -1;

    /** Whether this frame's Tab list has a scoreboard objective. */
    private boolean pendingObjective;

    Log log() {
        return log;
    }

    /** A new resource pack: the minimap takes its colors again. */
    @Override
    public void onModelsReloaded() {
        if (minimap != null) {
            minimap.resourcesReloaded();
        }
    }

    /** A hit of yours on a player: the target element shows them. */
    @Override
    public boolean onAttackEntity(Object entity) {
        if (target != null) {
            target.hit(entity);
        }
        return false;
    }

    /** Every click the game handled in the world counts for the CPS. */
    @Override
    public void onMouseInput() {
        if (stats != null && Keys.mousePressed()) {
            stats.click(Keys.mouseButton());
        }
    }

    @Override
    public void afterStartGame() {
        configFile = ctx.configFile();
        config = HudConfigFile.load(configFile, log);
        // Each element's place goes to the Loader's grid; what steinhud.json had counts the first
        // time.
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
        tabList = new TabList(config.tab, log);
        diagnostics = new TabDiagnostics(this);
        applyInventoryEffects();
        log.info("started (" + configFile + ")");
    }

    /** The inventory's effect list goes away when the effects element asks for it. */
    void applyInventoryEffects() {
        Game.setInventoryEffectsHidden(
                Hud.isEnabled("steinhud.effects") && config.effects.hideInventoryList);
    }

    void save() {
        if (configFile != null) {
            HudConfigFile.save(config, configFile, log);
        }
        applyInventoryEffects();
    }

    /**
     * The Tab list. <b>Nothing is drawn here</b>: the game draws the list in the middle of the HUD,
     * before the grid's elements, and it ended up beneath them, while the list is what you read
     * while holding Tab and nothing may cover it. So the request is kept and drawn in {@link
     * #onOverlayTop}, after everything; both ours and the game's are drawn from there (the game's
     * then by us, in the same place, only on top).
     */
    @Override
    public boolean onPlayerList(Object overlay, int width, Object scoreboard, Object objective) {
        if (tabList == null) {
            return false; // without ours built, the game draws its own as always
        }
        diagnostics.tick(tabList);
        pendingWidth = width;
        pendingObjective = objective != null;
        return true;
    }

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
        // "Server", or ours gave up (no connection): the game's, now on top of the grid. The
        // patcher's rule only replaces the call inside renderGameOverlay, so the Loader's one is
        // the
        // real one.
        Render.gameTabList(width);
    }

    /** The HUD page on the Panel: the Tab list (the rest is per element, on the Screen page). */
    @Override
    public void onPage(Page page) {
        new TabPage(config.tab, this::save).build(page);
    }

    /** Configure, on the Stein Loader menu: the grid editor, returning to the menu. */
    @Override
    public Object configScreen(Object parent) {
        return Hud.editorScreen(parent);
    }
}
