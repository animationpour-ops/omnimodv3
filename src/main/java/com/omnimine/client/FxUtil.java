package com.omnimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Petits outils communs aux effets : direction des astres, reperes, chemins courbes. */
public final class FxUtil {

    /**
     * Distance a laquelle on dessine les astres : au-dela de tout le terrain visible, mais avant le plan de
     * coupe lointain (4 x la distance d'affichage).
     */
    public static double skyDistance() {
        int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        return Math.max(90.0D, 64.0D * chunks * 0.8D);
    }

    /** Direction du soleil dans le ciel (meme formule que le serveur et que le rendu vanilla). */
    public static Vec3 sunDir(float partial) {
        Minecraft mc = Minecraft.getInstance();
        float timeOfDay = mc.level != null ? mc.level.getTimeOfDay(partial) : 0.0F;
        double th = timeOfDay * (Math.PI * 2.0D);
        return new Vec3(-Math.sin(th), Math.cos(th), 0.0D);
    }

    public static Vec3 moonDir(float partial) {
        return sunDir(partial).scale(-1.0D);
    }

    /** Deux vecteurs unitaires perpendiculaires a dir et entre eux. */
    public static Vec3[] basis(Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 ref = Math.abs(d.y) < 0.95D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 u = d.cross(ref).normalize();
        Vec3 v = d.cross(u).normalize();
        return new Vec3[] {u, v};
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    /** Melange deux couleurs RGB (t = 0 : a, t = 1 : b). */
    public static int mix(int a, int b, float t) {
        t = Rnd.clamp01(t);
        int r = (int) (((a >> 16) & 0xFF) * (1.0F - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1.0F - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1.0F - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private FxUtil() {}
}
