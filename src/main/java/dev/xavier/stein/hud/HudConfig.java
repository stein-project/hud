/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

/**
 * The HUD's settings, {@code config/steinhud.json} in the game folder (read and written by {@link
 * HudConfigFile}). The field names, here and in the nested classes, are the JSON keys players
 * already have: do not rename them.
 *
 * <p>The defaults come from the configuration the player used with the original mods
 * (armorstatushud.cfg, statuseffecthud.cfg, DirectionHUD.cfg): armor horizontal at the bottom
 * centre (just above the game's armor bar); effects at the middle left; direction at the top
 * centre. Those without an original mod: performance at the top left, and the inventory, small, at
 * the bottom right. The packaged defaults ({@code assets/steinhud/defaults.json}) take precedence.
 */
public final class HudConfig {

    /** The editor's grid, in GUI pixels; 0 = no grid. */
    public int grid = 8;

    /** Snap to the grid, the edges and the screen centre when dragging. */
    public boolean snap = true;

    /** Hide the HUD with the F3 screen open, like the original mods. */
    public boolean hideWithDebug = true;

    public Armor armor = new Armor();
    public Effects effects = new Effects();
    public Compass direction = new Compass();
    public Inventory inventory = new Inventory();
    public Target target = new Target();
    public Stats stats = new Stats();
    public Minimap minimap = new Minimap();
    public TabConfig tab = new TabConfig();

    /**
     * Place and common options of an element. Place, scale and enabled now belong to the Loader's
     * grid; these fields are the old version's, read once so the Loader keeps the place the player
     * already had.
     */
    public abstract static class Placement {
        public boolean enabled = true;

        /**
         * The screen edge the element follows: 0 = left/top, 1 = centre, 2 = right/bottom. Keeps
         * the element in place when the window or the GUI scale changes.
         */
        public int anchorX;

        public int anchorY;

        /** Offset from the box's matching edge (or centre) to the anchor, in GUI pixels. */
        public int x;

        public int y;
        public float scale = 1.0F;
        public boolean showInChat;

        /** The element's default place. */
        abstract void resetPosition();

        /** A new one with the defaults (the factory place, for the Loader's grid). */
        abstract Placement fresh();
    }

    /** ArmorStatusHUD: armor and the held item, with durability. */
    public static final class Armor extends Placement {
        public boolean horizontal = true;
        public boolean itemName = false;

        /** The inventory's durability bar over the icon. */
        public boolean damageOverlay = true;

        /** How many of the held item are in the inventory (arrows, for a bow). */
        public boolean itemCount = true;

        public boolean armorDamage = true;
        public boolean itemDamage = true;

        /** "left/max" when durability shows as a value. */
        public boolean maxDamage = false;

        public boolean equippedItem = true;

        /** Threshold and color code (0-9, a-f): the color applies when durability is <= it. */
        public String damageColors = "100,f; 80,7; 60,e; 40,6; 25,c; 10,4";

        /** value, percent or none. */
        public String damageDisplay = "value";

        /** The color list compares with the durability left (value) or the percentage (percent). */
        public String thresholdType = "value";

        public Armor() {
            resetPosition();
            showInChat = false;
        }

        @Override
        void resetPosition() {
            // Bottom centre like the player's ArmorStatusHUD, but above the game's armor bar (49 px
            // from the edge): at the original's 41 px, with armor on, the icons covered the bar.
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

    /** StatusEffectHUD: active effects, with icon, name and duration. */
    public static final class Effects extends Placement {
        /** Takes the effect list out of the inventory (and the screen's shift to the side). */
        public boolean hideInventoryList = true;

        public boolean effectName = true;
        public String nameColor = "f";
        public String durationColor = "f";

        /** The icon blinks at the end of the effect. */
        public boolean iconBlink = true;

        /** Seconds left at which the duration starts blinking; -1 turns it off. */
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

    /** DirectionHUD: a compass with the direction the player faces. */
    public static final class Compass extends Placement {
        /** 0 strip, 1 strip without background, 2 text, 3 strip with degrees. */
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

    /** The inventory with it closed: the 27 slots of the main inventory. */
    public static final class Inventory extends Placement {
        /** The hotbar too, below, like on the inventory screen. */
        public boolean hotbar = false;

        /** Marked slots (the background itself is the grid's, in the Loader). */
        public boolean background = true;

        /** Durability bar, like in the inventory. */
        public boolean durability = true;

        /** Stack counts. */
        public boolean count = true;

        /** Hidden with an empty inventory. */
        public boolean hideEmpty = true;

        public Inventory() {
            resetPosition();
            showInChat = false;
        }

        @Override
        void resetPosition() {
            // Small, at the bottom right: at scale 0.5 it stays clear of the hunger bar even in the
            // smallest window (854x480).
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
     * Target: the last player you hit (in range and in time) or, without a recent hit, the one in
     * your crosshair, with head, name, health, armor and held item, and each piece's durability.
     */
    public static final class Target extends Placement {
        /** Without a recent hit, shows who is in the crosshair. */
        public boolean aim = true;

        /** Blocks: the crosshair's reach; farther than this the target leaves. */
        public float range = 8.0F;

        /** Seconds on screen after the last hit. */
        public float seconds = 5.0F;

        /**
         * Seconds the current target holds the place: a hit on another player before that does not
         * switch.
         */
        public float switchSeconds = 1.0F;

        public boolean head = true;
        public boolean name = true;
        public boolean distance = true;

        /** Health bar (with the lost part fading slowly). */
        public boolean healthBar = true;

        public boolean healthText = true;
        public boolean armor = true;
        public boolean held = true;

        /** Durability bar over the icon, like in the inventory. */
        public boolean durabilityBar = true;

        /** value, percent or none: the durability under each piece. */
        public String durability = "percent";

        /** Entering, leaving and health changes animated. */
        public boolean animation = true;

        public Target() {
            resetPosition();
            showInChat = false;
        }

        @Override
        void resetPosition() {
            // Beside the crosshair, a little below: the fight happens there, and the eye need not
            // leave the target.
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

    /** Performance in a single block: FPS, 1% low, ping, CPU, GPU, RAM and CPS. */
    public static final class Stats extends Placement {
        /** 0 "FPS: 120", 1 "120 FPS". */
        public int style = 0;

        public boolean fps = true;
        public boolean low = false;

        /**
         * Worst frame of the last second, in ms; off by default (one more line resizes the block).
         */
        public boolean worst = false;

        public boolean ping = false;
        public boolean cpu = false;
        public boolean gpu = false;

        /**
         * The computer's RAM, in GB: 0 off, any other value on. It used to be a mode (1 to 4: the
         * game's or the system's, in % or in size); whoever had one on keeps the line, now only
         * with the computer's.
         */
        public int ram = 0;

        /** 0 none, 1 left button only, 2 both. */
        public int cps = 0;

        /** Everything on a single line, instead of one line per measure. */
        public boolean horizontal = true;

        public boolean colorByValue = true;

        /** Color of the names (and of the numbers, without color by value). */
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

    /**
     * Minimap: the terrain from above (like Rei's Minimap) or radar only, with players, mobs and
     * spawners.
     */
    public static final class Minimap extends Placement {
        /** 0 detailed map, 1 simple map (less detail, outlines), 2 radar only. */
        public int mode = 0;

        /** 0 square, 1 round. */
        public int shape = 0;

        /** Side in GUI pixels: 64, 80, 96 or 128. */
        public int size = 80;

        /** Blocks per pixel: 0.5, 1, 2 or 4. */
        public float zoom = 1.0F;

        /** Turns with the player (facing up); without it, north is up. */
        public boolean rotate = true;

        /** Players on the map (always as a dot). */
        public boolean players = true;

        /** 0 monsters and animals, 1 monsters only, 2 animals only, 3 none. */
        public int entities = 0;

        /** Spawners nearby. */
        public boolean spawners = true;

        /** Relation colors on players: enemies, allies and my clan. */
        public boolean combat = true;

        public boolean coords = true;

        /** In the Nether and underground, the floor of the player's level. */
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

    /** A hand-edited file or one from an earlier version: missing fields go back to the default. */
    void repair() {
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
            tab = new TabConfig();
        }
        tab.repair();
        for (Placement p :
                new Placement[] {armor, effects, direction, inventory, target, stats, minimap}) {
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
