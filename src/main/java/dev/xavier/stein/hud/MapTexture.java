/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hud;

import java.util.Arrays;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Terrain;

/**
 * O terreno do minimapa numa textura de 1024x1024 pixels, um por bloco, que se repete (GL_REPEAT): o bloco (x, z) fica
 * no pixel (x mod 1024, z mod 1024), entao andar nao move nada — so os chunks que entram no alcance sao lidos, os mais
 * perto primeiro e poucos por vez, e um ja lido e relido de tempos em tempos, em rodizio, para os blocos que mudam. Na
 * superficie, o bloco mais alto; no modo caverna, o chao do andar do jogador. Relevo pelo vizinho do norte, como o mapa
 * do jogo; agua rasa deixa ver o fundo. Os blocos e as cores vem do Loader (Terrain), e a textura e uma do mod
 * (Render.texture, repetida).
 *
 * <p>O ritmo e por tempo, nao por quadro: a cor de cada coluna custa caro (o jogo mistura a cor de bioma dos vizinhos,
 * com ruido), e com orcamento por quadro o custo crescia com o FPS — a 1800 FPS eram ~1800 chunks relidos por segundo
 * para um mapa que quase nao muda. Agora no maximo READS_PER_SECOND leituras novas e REFRESH_PER_SECOND releituras por
 * segundo, e nunca mais que BUDGET / uma por quadro: ate 60 FPS e o mesmo de antes; acima, o mesmo que a 60 FPS. O que
 * se le e como se pinta nao mudou, so quando.
 */
final class MapTexture {

    static final int SIZE = 1024;
    private static final int TILES = SIZE / 16;
    private static final int MASK = TILES - 1;
    /** Chunks lidos por quadro, no maximo. */
    private static final int BUDGET = 6;
    /** Ritmo de referencia: o de 60 quadros por segundo com o orcamento acima. */
    private static final double READS_PER_SECOND = BUDGET * 60.0;
    private static final double REFRESH_PER_SECOND = 60.0;
    /** -Dsteinhud.minimap.cost=true: a cada 5 s, o tempo gasto pelo mapa e os chunks lidos. */
    private static final boolean COST = Boolean.getBoolean("steinhud.minimap.cost");
    private static final int NONE_Y = Integer.MIN_VALUE;
    private static final int WALL_Y = Integer.MIN_VALUE + 1;
    private static final int WALL = 0xFF181818;
    private static final int VOID = 0xFF050505;

    /** Chunk guardado em cada lugar da textura, e se ja foi lido. */
    private final long[] keys = new long[TILES * TILES];
    private final boolean[] done = new boolean[TILES * TILES];
    private final int[] tile = new int[256];
    private final int[] north = new int[16];
    private final int[] blank = new int[256];
    private Render.Texture texture;
    private Object world;
    /** -1 superficie; senao o y dos pes no modo caverna. */
    private int layer = Integer.MIN_VALUE;
    private int refresh;
    /** Leituras novas e releituras disponiveis: baldes que enchem com o tempo, limitados ao de um quadro. */
    private double reads = BUDGET;
    private double refreshes = 1;
    private long lastNanos;
    private long costAt;
    private long costNanos;
    private int costFrames;
    private int costReads;
    private int costRefreshes;
    /** Modo simples: a cor de mapa do jogo e o relevo mais forte — menos detalhe, o contorno das coisas aparece. */
    private boolean simple;

    Render.Texture texture() {
        ensure();
        return texture;
    }

    /** Tudo sera lido de novo (cores novas). */
    void reset() {
        world = null;
    }

    /** Le o que falta em volta do chunk (pcx, pcz), raio r em chunks, no andar layer (-1 superficie). */
    void update(Object w, int pcx, int pcz, int r, int layer, boolean simple) {
        long t0 = System.nanoTime();
        double sec = lastNanos == 0 ? 1.0 : Math.max(0, Math.min(1.0, (t0 - lastNanos) / 1e9));
        lastNanos = t0;
        reads = Math.min(BUDGET, reads + sec * READS_PER_SECOND);
        refreshes = Math.min(1, refreshes + sec * REFRESH_PER_SECOND);
        int read = scanAround(w, pcx, pcz, r, layer, simple);
        if (COST) {
            cost(t0, read);
        }
    }

    /** Chunks lidos neste quadro: os novos, mais 1000 se houve releitura (a conta do diag). */
    private int scanAround(Object w, int pcx, int pcz, int r, int layer, boolean simple) {
        ensure();
        if (w != world || layer != this.layer || simple != this.simple) {
            world = w;
            this.layer = layer;
            this.simple = simple;
            Arrays.fill(keys, Long.MIN_VALUE);
            Arrays.fill(done, false);
        }
        // Chunk que entrou no alcance: o lugar dele (que guardava outro) fica vazio ate a leitura.
        for (int cz = pcz - r; cz <= pcz + r; cz++) {
            for (int cx = pcx - r; cx <= pcx + r; cx++) {
                int i = index(cx, cz);
                long k = key(cx, cz);
                if (keys[i] != k) {
                    keys[i] = k;
                    done[i] = false;
                    put(cx, cz, blank);
                }
            }
        }
        // Como antes: os mais perto primeiro; a releitura so quando nao falta ler nada que ja chegou.
        int start = (int) reads;
        int budget = start;
        boolean pending = false;
        for (int d = 0; d <= r && !pending; d++) {
            int cells = d == 0 ? 1 : 8 * d;
            for (int k = 0; k < cells && !pending; k++) {
                int cx;
                int cz;
                if (d == 0) {
                    cx = pcx;
                    cz = pcz;
                } else {
                    int o = k % (2 * d);
                    switch (k / (2 * d)) {
                        case 0 -> {
                            cx = pcx - d + o;
                            cz = pcz - d;
                        }
                        case 1 -> {
                            cx = pcx + d;
                            cz = pcz - d + o;
                        }
                        case 2 -> {
                            cx = pcx + d - o;
                            cz = pcz + d;
                        }
                        default -> {
                            cx = pcx - d;
                            cz = pcz + d - o;
                        }
                    }
                }
                int i = index(cx, cz);
                if (done[i]) {
                    continue;
                }
                if (budget == 0) {
                    // Sem leitura disponivel: pendente so o que ja chegou (o que nao chegou nao segura a releitura).
                    pending = Terrain.isLoaded(cx, cz);
                } else if (scan(cx, cz)) {
                    done[i] = true;
                    budget--;
                }
            }
        }
        int out = start - budget;
        reads -= out;
        // Antes: releitura quando sobrava orcamento no quadro. Com o balde vazio (FPS alto), quando nada falta.
        if (!pending && (budget > 0 || start == 0) && refreshes >= 1) {
            refreshes -= 1;
            int n = 2 * r + 1;
            refresh = (refresh + 1) % (n * n);
            int cx = pcx - r + refresh % n;
            int cz = pcz - r + refresh / n;
            if (done[index(cx, cz)] && scan(cx, cz)) {
                out += 1000;
            }
        }
        return out;
    }

    private void cost(long t0, int read) {
        long now = System.nanoTime();
        costNanos += now - t0;
        costFrames++;
        costReads += read % 1000;
        costRefreshes += read / 1000;
        if (costAt == 0) {
            costAt = now;
        } else if (now - costAt >= 5_000_000_000L) {
            double s = (now - costAt) / 1e9;
            System.out.printf("[mods] minimapa: %.2f ms/s no mapa (%.1f us/quadro, %d quadros), %.0f chunks novos/s, %.0f relidos/s%n",
                    costNanos / 1e6 / s, costNanos / 1e3 / Math.max(1, costFrames), costFrames, costReads / s,
                    costRefreshes / s);
            costAt = now;
            costNanos = 0;
            costFrames = 0;
            costReads = 0;
            costRefreshes = 0;
        }
    }

    private static int index(int cx, int cz) {
        return (cz & MASK) * TILES + (cx & MASK);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private boolean scan(int cx, int cz) {
        if (!Terrain.isLoaded(cx, cz)) {
            return false;
        }
        boolean hasNorth = Terrain.isLoaded(cx, cz - 1);
        for (int lx = 0; lx < 16; lx++) {
            north[lx] = hasNorth ? top((cx << 4) + lx, (cz << 4) - 1) : NONE_Y;
        }
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (cx << 4) + lx;
                int z = (cz << 4) + lz;
                int y = top(x, z);
                int argb;
                if (y == WALL_Y) {
                    argb = WALL;
                } else if (y == NONE_Y) {
                    argb = VOID;
                } else {
                    argb = color(x, y, z);
                    int n = north[lx];
                    if (n != NONE_Y && n != WALL_Y && n != y) {
                        argb = scale(argb, y > n ? (simple ? 1.3F : 1.1F) : (simple ? 0.72F : 0.86F));
                    }
                }
                north[lx] = y;
                tile[(lz << 4) + lx] = argb;
            }
        }
        put(cx, cz, tile);
        return true;
    }

    /** y do bloco a pintar na coluna (x, z), ou parede (WALL_Y) / nada (NONE_Y). */
    private int top(int x, int z) {
        if (layer < 0) {
            int h = Terrain.height(x, z);
            if (h <= 0) {
                return NONE_Y;
            }
            // O mapa de altura passa por flores, neve fina e vidro: o que estiver logo acima do chao conta.
            return Terrain.isAir(x, h, z) ? h - 1 : h;
        }
        if (Terrain.isSolid(x, layer + 1, z)) {
            return WALL_Y;
        }
        for (int y = layer; y >= Math.max(0, layer - 24); y--) {
            if (!Terrain.isAir(x, y, z)) {
                return y;
            }
        }
        return NONE_Y;
    }

    private int color(int x, int y, int z) {
        int c = simple ? Terrain.mapColor(x, y, z) : Terrain.color(x, y, z);
        if (Terrain.isWater(x, y, z)) {
            // Agua: o fundo aparece no raso, e o fundo escurece.
            int d = 1;
            while (d < 12 && Terrain.isWater(x, y - d, z)) {
                d++;
            }
            int fy = d < 12 ? y - d : y - 11;   // fundo alem de 12: fica a cor da propria agua
            int floor = simple ? Terrain.mapColor(x, fy, z) : Terrain.color(x, fy, z);
            c = mix(c, floor, d <= 1 ? 0.5F : d <= 3 ? 0.3F : d <= 6 ? 0.15F : 0.05F);
            c = scale(c, 1.0F - Math.min(d, 10) * 0.025F);
        }
        if (layer >= 0) {
            c = scale(c, Math.max(0.35F, 1.0F - (layer - y) * 0.06F)); // caverna: mais fundo, mais escuro
        }
        return c;
    }

    private void put(int cx, int cz, int[] data) {
        texture.set((cx & MASK) * 16, (cz & MASK) * 16, 16, 16, data, 0);
    }

    private void ensure() {
        if (texture == null) {
            texture = Render.texture(SIZE, SIZE, true);   // repetida: o bloco (x, z) fica sempre no mesmo pixel
        }
    }

    static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** a com uma fracao f de b. */
    static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
