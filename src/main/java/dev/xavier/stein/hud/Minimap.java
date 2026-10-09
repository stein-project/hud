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
import java.util.List;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.Terrain;

/**
 * Minimapa em tres modos: detalhado (o terreno como no Reis Minimap: cor das texturas, relevo, agua rasa, cavernas no
 * Nether e embaixo da terra), simples (a cor de mapa do jogo e relevo forte — o contorno das coisas sem tanto detalhe)
 * e radar (sem mapa, so os marcadores). Quadrado ou redondo, girando com o jogador ou com o norte para cima. Mostra
 * jogadores como ponto (com o Stein Combat, inimigos em vermelho, aliados na cor do Combat e o meu cla em verde),
 * monstros, animais e gaiolas (spawners); o que esta bem acima ou abaixo aparece apagado.
 */
final class Minimap extends HudElement {

    static final int[] SIZES = {64, 80, 96, 128};
    private static final String[] SIZE_NAMES = {"64", "80", "96", "128"};
    /** Blocos por pixel. */
    static final float[] ZOOMS = {0.5F, 1.0F, 2.0F, 4.0F};
    private static final String[] ZOOM_NAMES = {"minimap.zoom.near", "minimap.zoom.normal", "minimap.zoom.far", "minimap.zoom.veryFar"};
    private static final String[] MODES = {"minimap.detailed", "minimap.simple", "minimap.radar"};
    private static final String[] SHAPES = {"minimap.square", "minimap.round"};
    private static final String[] ENTITIES = {"minimap.all", "minimap.monsters", "minimap.animals", "minimap.none"};
    private static final int CIRCLE = 48;
    private static final int HOSTILE = 0xFFAA00;
    private static final int PASSIVE = 0xD0D0D0;
    private static final int ENEMY = 0xFF5555;
    private static final int CLAN = 0x55FF55;
    private static final int NEUTRAL = 0xFFFFFF;

    private final HudConfig.Minimap c;
    private final MapTexture map = new MapTexture();
    private final List<Object> players = new ArrayList<>();
    /** Pontos de um leque (o fundo redondo, o mapa, a seta), reaproveitados de um quadro para o outro. */
    private final float[] fx = new float[CIRCLE + 2];
    private final float[] fy = new float[CIRCLE + 2];
    private final float[] fu = new float[CIRCLE + 2];
    private final float[] fv = new float[CIRCLE + 2];
    private Object world;
    private Object self;
    private int layer = -1;
    private float partial;
    private double px;
    private double py;
    private double pz;
    private double cos;
    private double sin;
    private float bpp;
    private int size;
    private String coords;
    /** O texto das coordenadas do jogador e o bloco de que ele foi montado. */
    private String coordsBuilt;
    private int coordsX;
    private int coordsY;
    private int coordsZ;
    private float sx;
    private float sy;

    Minimap(HudConfig.Minimap c, SteinHud mod) {
        super("minimap", c, mod);
        this.c = c;
    }

    /** Pacote de texturas novo: as cores e o mapa saem de novo. */
    void resourcesReloaded() {
        map.reset();   // as cores de bloco o Loader refaz sozinho
    }

    private int sizeIndex() {
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] == c.size) {
                return i;
            }
        }
        return 1;
    }

    private int zoomIndex() {
        int best = 1;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (Math.abs(ZOOMS[i] - c.zoom) < Math.abs(ZOOMS[best] - c.zoom)) {
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        self = Entities.self();
        world = Terrain.current();
        if ((self == null || world == null) && !preview) {
            return false;
        }
        size = SIZES[sizeIndex()];
        bpp = ZOOMS[zoomIndex()];
        partial = partialTicks;
        w = size;
        h = size + (c.coords ? 12 : 0);
        if (self == null || world == null) {
            coords = "0, 64, 0";
            return true;
        }
        px = Entities.x(self, partialTicks);
        py = Entities.y(self, partialTicks);
        pz = Entities.z(self, partialTicks);
        double yaw = Math.toRadians(Entities.yaw(self, partialTicks));
        cos = Math.cos(yaw);
        sin = Math.sin(yaw);
        // O texto so muda quando o bloco muda: sem montar a mesma string a cada quadro.
        int bx = floor(px);
        int by = floor(py);
        int bz = floor(pz);
        if (coordsBuilt == null || bx != coordsX || by != coordsY || bz != coordsZ) {
            coordsX = bx;
            coordsY = by;
            coordsZ = bz;
            coordsBuilt = bx + ", " + by + ", " + bz;
        }
        coords = coordsBuilt;
        if (c.mode <= 1) {
            updateMap();
        }
        return true;
    }

    private void updateMap() {
        int want = -1;
        if (c.caves && (!Terrain.hasSky() || Terrain.skyLight(floor(px), floor(py + 1), floor(pz)) < 4)) {
            int feet = floor(py);
            want = layer >= 0 && Math.abs(feet - layer) < 2 ? layer : feet; // meio bloco de folga: sem reler a cada degrau
        }
        layer = want;
        double reach = size / 2.0 * bpp * (c.rotate && c.shape == 0 ? 1.42 : 1.0);
        int r = Math.min(30, (int) Math.ceil(reach / 16.0) + 1);
        map.update(world, floor(px) >> 4, floor(pz) >> 4, r, layer, c.mode == 1);
    }

    /** Deslocamento no mundo (blocos) -> na tela (pixels a partir do centro), em sx/sy. */
    private void toScreen(double dx, double dz) {
        double x = dx / bpp;
        double z = dz / bpp;
        if (!c.rotate) {
            sx = (float) x;
            sy = (float) z;
        } else {
            // A frente do jogador para cima: yaw 0 olha para o sul (+z).
            sx = (float) (-cos * x - sin * z);
            sy = (float) (sin * x - cos * z);
        }
    }

    private boolean inside(float half) {
        float m = half - 2;
        return c.shape == 1 ? sx * sx + sy * sy <= m * m : Math.abs(sx) <= m && Math.abs(sy) <= m;
    }

    @Override
    void draw(boolean alignRight) {
        float half = size / 2.0F;
        boolean live = self != null && world != null;
        shape(half, half + 1, c.mode <= 1 ? 0xFF000000 : 0xA0000000);
        if (c.mode <= 1 && live) {
            drawMap(half);
        } else {
            Render.rect(1, (int) half, size - 2, 1, 0x30FFFFFF);
            Render.rect((int) half, 1, 1, size - 2, 0x30FFFFFF);
        }
        if (live) {
            if (c.spawners) {
                spawners(half);
            }
            entities(half);
        }
        arrow(half);
        north(half);
        if (c.coords) {
            int tw = Render.textWidth(coords);
            Render.text(coords, half - tw / 2.0F, size + 3, 0xFFFFFF, true);
        }
    }

    // ---- fundo e mapa ----

    /** Quadrado ou circulo (conforme o formato) de raio r em volta do centro, numa cor so. */
    private void shape(float half, float r, int argb) {
        if (c.shape == 1) {
            fx[0] = half;
            fy[0] = half;
            for (int i = 0; i <= CIRCLE; i++) {
                double ang = -2 * Math.PI * i / CIRCLE;
                fx[i + 1] = (float) (half + Math.cos(ang) * r);
                fy[i + 1] = (float) (half + Math.sin(ang) * r);
            }
            Render.fan(fx, fy, CIRCLE + 2, argb);
        } else {
            corners(half, r);
            Render.fan(fx, fy, 4, argb);
        }
    }

    /** Os quatro cantos do quadrado de raio r, na ordem em que o jogo desenha de frente. */
    private void corners(float half, float r) {
        fx[0] = half - r;
        fy[0] = half + r;
        fx[1] = half + r;
        fy[1] = half + r;
        fx[2] = half + r;
        fy[2] = half - r;
        fx[3] = half - r;
        fy[3] = half - r;
    }

    private void drawMap(float half) {
        // Coordenadas de textura perto de zero (a textura se repete): longe da origem, float perderia o bloco.
        double baseX = Math.floor(px / MapTexture.SIZE) * MapTexture.SIZE;
        double baseZ = Math.floor(pz / MapTexture.SIZE) * MapTexture.SIZE;
        int n;
        if (c.shape == 1) {
            mapVertex(0, half, 0, 0, baseX, baseZ);
            for (int i = 0; i <= CIRCLE; i++) {
                double ang = -2 * Math.PI * i / CIRCLE;
                mapVertex(i + 1, half, Math.cos(ang) * half, Math.sin(ang) * half, baseX, baseZ);
            }
            n = CIRCLE + 2;
        } else {
            mapVertex(0, half, -half, half, baseX, baseZ);
            mapVertex(1, half, half, half, baseX, baseZ);
            mapVertex(2, half, half, -half, baseX, baseZ);
            mapVertex(3, half, -half, -half, baseX, baseZ);
            n = 4;
        }
        map.texture().drawFan(fx, fy, fu, fv, n, 0xFFFFFFFF);
    }

    /** Ponto i em (centro + ox, centro + oy), com a coordenada de textura do bloco que cai ali. */
    private void mapVertex(int i, float half, double ox, double oy, double baseX, double baseZ) {
        double dx;
        double dz;
        if (!c.rotate) {
            dx = ox * bpp;
            dz = oy * bpp;
        } else {
            dx = (-ox * cos + oy * sin) * bpp;
            dz = (-ox * sin - oy * cos) * bpp;
        }
        fx[i] = (float) (half + ox);
        fy[i] = (float) (half + oy);
        fu[i] = (float) ((px - baseX + dx) / MapTexture.SIZE);
        fv[i] = (float) ((pz - baseZ + dz) / MapTexture.SIZE);
    }

    // ---- marcadores ----

    private void spawners(float half) {
        Terrain.forEachSpawner((bx, by, bz) -> {
            toScreen(bx + 0.5 - px, bz + 0.5 - pz);
            if (!inside(half)) {
                return;
            }
            int x = Math.round(half + sx);
            int y = Math.round(half + sy);
            int a = fade(by + 0.5);
            Render.rect(x - 3, y - 3, 6, 6, a << 24);
            Render.rect(x - 2, y - 2, 4, 4, a << 24 | 0x9B3FD0);
            Render.rect(x - 1, y - 2, 1, 4, a << 24 | 0x2A1438); // grades da gaiola
            Render.rect(x + 1, y - 2, 1, 4, a << 24 | 0x2A1438);
        });
    }

    /** Alfa pela diferenca de altura: o que esta bem acima ou abaixo aparece apagado. */
    private int fade(double y) {
        return Math.abs(y - py) > 12 ? 0x70 : 0xFF;
    }

    /** -Dsteinhud.minimap.debug=true: a cada 2 s, as entidades que o minimapa viu e as que desenhou. */
    private static final boolean DEBUG = Boolean.getBoolean("steinhud.minimap.debug");
    private long debugAt;

    private void entities(float half) {
        players.clear();
        boolean log = DEBUG && System.nanoTime() - debugAt > 2_000_000_000L;
        StringBuilder seen = log ? new StringBuilder() : null;
        int drawn = 0;
        if (log) {
            debugAt = System.nanoTime();
        }
        for (Object e : Entities.all()) {
            Entities.Kind kind = Entities.kind(e);
            if (log && kind != Entities.Kind.PLAYER) {
                seen.append(Entities.typeName(e)).append(Entities.isInvisible(e) ? "(invisivel)" : "")
                        .append(kind == Entities.Kind.HOSTILE ? "/monstro" : "").append(' ');
            }
            // Primeiro o que nao depende da entidade estar invisivel (tipo e opcoes): o que sai e a ordem sao os mesmos,
            // sem ler as flags de quem nao seria desenhado de qualquer jeito (item no chao, flecha, tipo desligado) —
            // depois do mundo inteiro desenhado, essa leitura e uma falta de cache por entidade.
            int color;
            if (kind == Entities.Kind.PLAYER) {
                if (!c.players) {
                    continue;
                }
                color = 0;
            } else if (kind == Entities.Kind.HOSTILE) {
                if (c.entities == 2 || c.entities == 3) {
                    continue;
                }
                color = HOSTILE;
            } else if (kind == Entities.Kind.PASSIVE) {
                if (c.entities == 1 || c.entities == 3) {
                    continue;
                }
                color = PASSIVE;
            } else {
                continue;
            }
            if (e == self || Entities.isInvisible(e)) {
                continue;
            }
            if (kind == Entities.Kind.PLAYER) {
                players.add(e);
                continue;
            }
            if (!place(e, half)) {
                continue;
            }
            int x = Math.round(half + sx);
            int y = Math.round(half + sy);
            int a = fade(Entities.y(e, 1));
            Render.rect(x - 2, y - 2, 4, 4, a << 24);
            Render.rect(x - 1, y - 1, 2, 2, a << 24 | color);
            drawn++;
        }
        if (log) {
            System.out.println("[mods] minimapa: " + drawn + " marcador(es) de mob, " + players.size() + " jogador(es); vistas: "
                    + seen);
        }
        // Jogadores por cima de tudo, sempre como ponto (modo de PvP: rosto atrapalharia a leitura).
        for (Object p : players) {
            if (!place(p, half)) {
                continue;
            }
            int x = Math.round(half + sx);
            int y = Math.round(half + sy);
            int a = fade(Entities.y(p, 1));
            Render.rect(x - 2, y - 2, 5, 5, a << 24);
            Render.rect(x - 1, y - 1, 3, 3, a << 24 | playerColor(Entities.name(p)));
        }
    }

    /** Posicao interpolada da entidade na tela (sx/sy); false fora do mapa. */
    private boolean place(Object e, float half) {
        toScreen(Entities.x(e, partial) - px, Entities.z(e, partial) - pz);
        return inside(half);
    }

    /** A cor que o Combat da a este jogador (meu cla, aliado, inimigo ou sem cla); sem a integracao, branco. */
    private int playerColor(String nick) {
        return c.combat ? CombatLink.color(nick) : NEUTRAL;
    }

    // ---- o jogador e o norte ----

    /** Seta do jogador no centro: para cima com o mapa girando; senao, para onde ele olha. */
    private void arrow(float half) {
        double phi = c.rotate || self == null ? 0 : Math.atan2(sin, cos) + Math.PI;
        arrowShape(half, phi, 1.45F, 0xFF000000);
        arrowShape(half, phi, 1.0F, 0xFFFFFFFF);
    }

    private void arrowShape(float half, double phi, float k, int argb) {
        // Ponta, esquerda, entalhe, direita: a ordem de giro que o jogo desenha de frente.
        float[] xs = {0, -3.5F, 0, 3.5F};
        float[] ys = {-5, 3.5F, 1.5F, 3.5F};
        double cp = Math.cos(phi);
        double sp = Math.sin(phi);
        for (int i = 0; i < 4; i++) {
            double x = xs[i] * k;
            double y = ys[i] * k;
            fx[i] = (float) (half + x * cp - y * sp);
            fy[i] = (float) (half + x * sp + y * cp);
        }
        Render.fan(fx, fy, 4, argb);
    }

    private void north(float half) {
        float nx;
        float ny;
        if (c.rotate && self != null) {
            toScreen(0, -bpp); // um pixel para o norte
            float len = (float) Math.sqrt(sx * sx + sy * sy);
            nx = sx / len;
            ny = sy / len;
        } else {
            nx = 0;
            ny = -1;
        }
        float r = half - 5;
        if (c.shape == 0) {
            r = r / Math.max(Math.abs(nx), Math.abs(ny)); // na borda do quadrado
        }
        Render.text("N", half + nx * r - 2.5F, half + ny * r - 4, 0xFFFFFF, true);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    @Override
    void addOptions(List<Option> out) {
        out.add(Option.cycle(T.tr("minimap.mode"), T.all(MODES), () -> Math.max(0, Math.min(2, c.mode)), i -> c.mode = i));
        out.add(Option.cycle(T.tr("minimap.shape"), T.all(SHAPES), () -> c.shape == 1 ? 1 : 0, i -> c.shape = i));
        out.add(Option.cycle(T.tr("minimap.size"), SIZE_NAMES, this::sizeIndex, i -> c.size = SIZES[i]));
        out.add(Option.cycle(T.tr("minimap.zoom"), T.all(ZOOM_NAMES), this::zoomIndex, i -> c.zoom = ZOOMS[i]));
        out.add(Option.toggle(T.tr("minimap.rotate"), () -> c.rotate, v -> c.rotate = v));
        out.add(Option.toggle(T.tr("minimap.players"), () -> c.players, v -> c.players = v));
        out.add(Option.cycle(T.tr("minimap.entities"), T.all(ENTITIES), () -> Math.max(0, Math.min(3, c.entities)), i -> c.entities = i));
        out.add(Option.toggle(T.tr("minimap.spawners"), () -> c.spawners, v -> c.spawners = v));
        out.add(Option.toggle(T.tr("minimap.relationColors"), () -> c.combat, v -> c.combat = v));
        out.add(Option.toggle(T.tr("minimap.coords"), () -> c.coords, v -> c.coords = v));
        out.add(Option.toggle(T.tr("minimap.caves"), () -> c.caves, v -> c.caves = v));
    }
}
