package com.omnimine.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Forme du tronc d'un eclair, calculee avec le MEME tirage aleatoire que le rendu de la foudre de Minecraft
 * (LightningBoltRenderer) : le serveur sait donc ou se trouve l'eclair que le client dessine.
 * Un eclair vanilla = 8 segments de 16 blocs de haut qui partent du point de frappe.
 */
public final class BoltShape {
    /** Hauteur d'un eclair vanilla (8 x 16 blocs). */
    public static final double HEIGHT = 128.0D;
    public static final int SEGMENTS = 8;

    /** Les 9 sommets du tronc, du sol (indice 0 = le point de frappe) jusqu'au sommet (indice 8). */
    public static Vec3[] trunk(long seed, Vec3 base) {
        RandomSource random = RandomSource.create(seed);
        int[] dx = new int[SEGMENTS];
        int[] dz = new int[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            dx[i] = random.nextInt(11) - 5;
            dz[i] = random.nextInt(11) - 5;
        }
        Vec3[] pts = new Vec3[SEGMENTS + 1];
        pts[0] = base;
        double x = 0.0D;
        double z = 0.0D;
        for (int j = 1; j <= SEGMENTS; j++) {
            x -= dx[SEGMENTS - j];
            z -= dz[SEGMENTS - j];
            pts[j] = new Vec3(base.x + x, base.y + 16.0D * j, base.z + z);
        }
        return pts;
    }

    /**
     * Plus courte distance entre le segment [a1,b1] (le regard du joueur) et le segment [a2,b2] (une partie de
     * l'eclair). Le point le plus proche sur [a2,b2] est ecrit dans out[0].
     */
    public static double distance(Vec3 a1, Vec3 b1, Vec3 a2, Vec3 b2, Vec3[] out) {
        Vec3 d1 = b1.subtract(a1);
        Vec3 d2 = b2.subtract(a2);
        Vec3 r = a1.subtract(a2);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);
        double s;
        double t;
        final double eps = 1.0E-9D;
        if (a <= eps && e <= eps) {
            s = 0.0D;
            t = 0.0D;
        } else if (a <= eps) {
            s = 0.0D;
            t = clamp01(f / e);
        } else {
            double c = d1.dot(r);
            if (e <= eps) {
                t = 0.0D;
                s = clamp01(-c / a);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > eps ? clamp01((b * f - c * e) / denom) : 0.0D;
                t = (b * s + f) / e;
                if (t < 0.0D) {
                    t = 0.0D;
                    s = clamp01(-c / a);
                } else if (t > 1.0D) {
                    t = 1.0D;
                    s = clamp01((b - c) / a);
                }
            }
        }
        Vec3 c1 = a1.add(d1.scale(s));
        Vec3 c2 = a2.add(d2.scale(t));
        if (out != null && out.length > 0) {
            out[0] = c2;
        }
        return c1.distanceTo(c2);
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : Math.min(v, 1.0D);
    }

    private BoltShape() {}
}
