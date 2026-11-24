package com.omnimine.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.omnimine.Omnimine;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Dessin porte des armures : le modele ET la decoupe de la Netherite vanilla, simplement repeints.
 * Au premier affichage, on lit les vraies textures `minecraft:textures/models/armor/netherite_layer_1/2.png`
 * (casque, plastron, bottes / jambieres) et on les recolore en gardant EXACTEMENT les memes zones transparentes :
 * le visage, les mains, les chevilles... restent visibles, donc le skin du joueur se voit comme avec une armure
 * vanilla. Armure d'eclair : acier bleu electrique avec des veines de foudre animees. Bottes lunaires : argent
 * lunaire qui scintille.
 */
public final class ArmorTextures {
    public static final int LIGHTNING_FRAMES = 24;
    public static final int MOON_FRAMES = 12;

    private static boolean ready;
    private static boolean failed;

    /** Texture portee de l'armure d'eclair : layer 1 = casque et plastron, layer 2 = jambieres. Image selon le tick. */
    public static String lightning(int layer, int tick) {
        if (!ensure()) {
            return vanilla(layer);
        }
        return Omnimine.MODID + ":" + path("lightning", layer, (tick / 2) % LIGHTNING_FRAMES);
    }

    /** Texture portee des bottes lunaires (layer 1). */
    public static String moon(int tick) {
        if (!ensure()) {
            return vanilla(1);
        }
        return Omnimine.MODID + ":" + path("moon", 1, (tick / 5) % MOON_FRAMES);
    }

    private static String path(String name, int layer, int frame) {
        return "textures/models/armor/" + name + "_layer_" + layer + "_f" + frame + ".png";
    }

    /** Repli si la generation echoue : la vraie armure en Netherite. */
    private static String vanilla(int layer) {
        return "minecraft:textures/models/armor/netherite_layer_" + layer + ".png";
    }

    // ------------------------------------------------------------------ generation

    private static boolean ensure() {
        if (ready) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            for (int layer = 1; layer <= 2; layer++) {
                NativeImage base = load(resources, new ResourceLocation("minecraft",
                        "textures/models/armor/netherite_layer_" + layer + ".png"));
                try {
                    float[] range = lumaRange(base);
                    for (int f = 0; f < LIGHTNING_FRAMES; f++) {
                        register("lightning", layer, f, paintLightning(base, range, f));
                    }
                    if (layer == 1) {
                        for (int f = 0; f < MOON_FRAMES; f++) {
                            register("moon", layer, f, paintMoon(base, range, f));
                        }
                    }
                } finally {
                    base.close();
                }
            }
            ready = true;
        } catch (Exception e) {
            failed = true;
            Omnimine.LOGGER.error("Omnimine : impossible de generer la texture d'armure, repli sur la Netherite", e);
        }
        return ready;
    }

    private static NativeImage load(ResourceManager resources, ResourceLocation location) throws IOException {
        Optional<Resource> resource = resources.getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("texture introuvable : " + location);
        }
        try (InputStream in = resource.get().open()) {
            return NativeImage.read(in);
        }
    }

    private static void register(String name, int layer, int frame, NativeImage image) {
        ResourceLocation id = new ResourceLocation(Omnimine.MODID, path(name, layer, frame));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
    }

    // ------------------------------------------------------------------ peinture

    private static int alphaOf(int abgr) {
        return (abgr >>> 24) & 0xFF;
    }

    private static float luma(int abgr) {
        float r = abgr & 0xFF;
        float g = (abgr >> 8) & 0xFF;
        float b = (abgr >> 16) & 0xFF;
        return (0.299F * r + 0.587F * g + 0.114F * b) / 255.0F;
    }

    /** Luminosite basse et haute (5 % / 95 %) des pixels visibles : sert a etaler le dessin vanilla sur toute la palette. */
    private static float[] lumaRange(NativeImage img) {
        float[] values = new float[img.getWidth() * img.getHeight()];
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int px = img.getPixelRGBA(x, y);
                if (alphaOf(px) > 0) {
                    values[n++] = luma(px);
                }
            }
        }
        if (n == 0) {
            return new float[] {0.0F, 1.0F};
        }
        Arrays.sort(values, 0, n);
        float lo = values[(int) (n * 0.05F)];
        float hi = values[Math.min(n - 1, (int) (n * 0.95F))];
        if (hi - lo < 0.02F) {
            hi = lo + 0.02F;
        }
        return new float[] {lo, hi};
    }

    private interface Shader {
        /** Couleur (r, g, b entre 0 et 255) du pixel : n = relief du dessin vanilla (0..1), px/py = position (grille 64x32). */
        float[] color(float n, float px, float py);
    }

    private static NativeImage paint(NativeImage base, float[] range, Shader shader) {
        int w = base.getWidth();
        int h = base.getHeight();
        NativeImage out = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int src = base.getPixelRGBA(x, y);
                int a = alphaOf(src);
                if (a == 0) {
                    out.setPixelRGBA(x, y, 0);
                    continue;
                }
                float n = clamp01((luma(src) - range[0]) / (range[1] - range[0]));
                float[] c = shader.color(n, x * 64.0F / w, y * 32.0F / h);
                int r = clamp255(c[0]);
                int g = clamp255(c[1]);
                int b = clamp255(c[2]);
                out.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return out;
    }

    // ---- armure d'eclair : acier bleu electrique, veines de foudre, eclat qui court sur les plaques

    private static final float[][] STEEL = {
            {0.00F, 7, 12, 34}, {0.30F, 22, 52, 140}, {0.60F, 54, 138, 245}, {0.85F, 170, 226, 255}, {1.00F, 250, 253, 255}};

    private static NativeImage paintLightning(NativeImage base, float[] range, int frame) {
        float tt = (float) (Math.PI * 2.0D * frame / LIGHTNING_FRAMES);
        float ox = 2.4F * (float) Math.cos(tt);
        float oy = 2.4F * (float) Math.sin(tt);
        float breathe = 0.93F + 0.07F * (float) Math.sin(tt);
        float head = frame * 48.0F / LIGHTNING_FRAMES;
        int sparkStep = frame / 2;
        return paint(base, range, (n, px, py) -> {
            float[] c = ramp(STEEL, n);
            // veines de foudre : lignes fines qui derivent en boucle sur toute la planche
            float v1 = ridge(noise(px * 0.11F + ox, py * 0.11F + oy, 11));
            float v2 = ridge(noise(px * 0.11F - oy + 40.0F, py * 0.11F + ox + 17.0F, 29));
            float vein = (float) Math.pow(Math.max(v1, v2 * 0.9F), 16.0D);
            // un eclat qui court en diagonale sur les plaques
            float s = ((px + py * 0.7F) % 48.0F + 48.0F) % 48.0F;
            float dd = Math.abs(s - head);
            dd = Math.min(dd, 48.0F - dd);
            float pulse = (float) Math.exp(-dd * dd / 18.0D) * (0.35F + 0.65F * n);
            // etincelles d'un pixel qui crepitent
            float spark = rnd((int) px, (int) py, 1000 + sparkStep * 7) < 0.007F ? 1.0F : 0.0F;
            for (int k = 0; k < 3; k++) {
                c[k] *= breathe;
            }
            mix(c, new float[] {110, 225, 255}, clamp01(pulse * 0.6F));
            mix(c, new float[] {255, 248, 200}, clamp01(vein * 1.1F + spark));
            return c;
        });
    }

    // ---- bottes lunaires : argent bleute, cratères sombres, etoiles qui scintillent

    private static final float[][] MOON = {
            {0.00F, 58, 70, 112}, {0.35F, 126, 146, 196}, {0.70F, 206, 220, 246}, {1.00F, 255, 255, 255}};

    private static NativeImage paintMoon(NativeImage base, float[] range, int frame) {
        float tt = (float) (Math.PI * 2.0D * frame / MOON_FRAMES);
        return paint(base, range, (n, px, py) -> {
            float[] c = ramp(MOON, n);
            float crater = noise(px * 0.45F, py * 0.45F, 5);
            if (crater < 0.28F) {
                float k = 1.0F - 0.22F * (0.28F - crater) / 0.28F * 2.0F;
                for (int i = 0; i < 3; i++) {
                    c[i] *= Math.max(0.7F, k);
                }
            }
            float glow = 0.5F + 0.5F * (float) Math.sin(tt + noise(px * 0.2F, py * 0.2F, 9) * 6.28F);
            mix(c, new float[] {225, 240, 255}, glow * 0.22F * n);
            float star = rnd((int) px, (int) py, 300 + frame * 3) < 0.012F ? 1.0F : 0.0F;
            mix(c, new float[] {255, 255, 255}, star);
            return c;
        });
    }

    // ------------------------------------------------------------------ petits outils

    private static float[] ramp(float[][] stops, float v) {
        for (int i = 1; i < stops.length; i++) {
            if (v <= stops[i][0]) {
                float t = (v - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                return new float[] {
                        stops[i - 1][1] + (stops[i][1] - stops[i - 1][1]) * t,
                        stops[i - 1][2] + (stops[i][2] - stops[i - 1][2]) * t,
                        stops[i - 1][3] + (stops[i][3] - stops[i - 1][3]) * t};
            }
        }
        float[] last = stops[stops.length - 1];
        return new float[] {last[1], last[2], last[3]};
    }

    /** c devient un melange de c et de to (t = 0 : c, t = 1 : to). */
    private static void mix(float[] c, float[] to, float t) {
        for (int i = 0; i < 3; i++) {
            c[i] += (to[i] - c[i]) * t;
        }
    }

    /** 1 sur la ligne ou le bruit vaut 0,5 : donne des veines fines. */
    private static float ridge(float n) {
        return 1.0F - Math.abs(2.0F * n - 1.0F);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    private static int clamp255(float v) {
        return v < 0.0F ? 0 : Math.min(255, (int) v);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static float rnd(int x, int y, int z) {
        return (hash(x, y, z) & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Bruit de valeur lisse (0..1). */
    private static float noise(float x, float y, int salt) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float fx = x - xi;
        float fy = y - yi;
        fx = fx * fx * (3.0F - 2.0F * fx);
        fy = fy * fy * (3.0F - 2.0F * fy);
        float a = rnd(xi, yi, salt);
        float b = rnd(xi + 1, yi, salt);
        float c = rnd(xi, yi + 1, salt);
        float d = rnd(xi + 1, yi + 1, salt);
        float top = a + (b - a) * fx;
        float bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    private ArmorTextures() {}
}
