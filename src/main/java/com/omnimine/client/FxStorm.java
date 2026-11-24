package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import net.minecraft.world.phys.Vec3;

/** Effets de l'armure d'eclair : decharge statique (plastron) et trainee electrique (jambieres). */
public final class FxStorm {

    /** Decharge statique : une sphere d'eclairs jaillit du porteur quand on le frappe. */
    public static final class Nova extends Fx {
        public Nova(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 c = followed() != null ? pos(partial).add(0.0D, 1.0D, 0.0D) : origin;
            float radius = Math.max(2.0F, param);
            // le dessin est prevu pour 8 blocs : plus grand, il s'epaissit et se garnit en proportion
            float rs = Math.max(1.0F, radius / 8.0F);
            float ex = easeOut(t / 10.0F);
            float out = 1.0F - Rnd.ramp(t, 6.0F, duration);
            double r = 0.5D + radius * ex;
            double w = (0.45D * out + 0.08D) * rs;

            d.type(VfxRenderTypes.LINE_ADD);
            // trois anneaux orthogonaux dessinent la sphere d'energie
            d.ring(c, AXIS_X, AXIS_Z, r, w, color, 0.9F * out, 72);
            d.ring(c, AXIS_X, AXIS_Y, r, w, color, 0.7F * out, 72);
            d.ring(c, AXIS_Y, AXIS_Z, r, w, color, 0.7F * out, 72);
            double r2 = 0.3D + radius * easeOut((t - 2.0F) / 10.0F);
            if (t > 2.0F) {
                d.ring(c, AXIS_X, AXIS_Z, r2, 0.18D * out + 0.04D, 0xFFFFFF, 0.9F * out, 72);
                d.ring(c, d.left, d.up, r2, 0.15D * out + 0.04D, 0xFFFFFF, 0.7F * out, 72);
            }
            // onde au sol
            d.ring(c.add(0.0D, -0.95D, 0.0D), AXIS_X, AXIS_Z, 0.5D + radius * 1.1D * easeOut(t / 12.0F),
                    0.6D * out + 0.1D, color, 0.7F * out, 72);
            // eclairs qui partent dans toutes les directions (se renouvellent toutes les 2 ticks)
            if (t < 18.0F) {
                int step = (int) (t / 2.0F);
                float a = 1.0F - Rnd.ramp(t, 8.0F, 18.0F);
                int bolts = Math.round(14.0F * rs);
                for (int i = 0; i < bolts; i++) {
                    Vec3 dir = sphere(seed + step * 17, i);
                    double len = radius * (0.55D + 0.45D * Rnd.f(seed + step, 40 + i)) * ex;
                    d.lightning(c, c.add(dir.scale(len)), seed + i * 31 + step * 7, 4, 0.2F, 0.11F * rs, color, a,
                            i % 3 == 0 ? 1 : 0);
                }
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(t, 0.0F, 10.0F);
            d.glow(c, (9.0F * f + 1.0F) * rs, color, 0.8F * f + 0.1F * out);
            d.glow(c, 4.0F * f * rs, 0xFFFFFF, f);

            d.type(VfxRenderTypes.STAR_ADD);
            float ts = t / 20.0F;
            for (int i = 0; i < 40; i++) {
                float life = 0.4F + 0.5F * Rnd.f(seed, 200 + i);
                if (ts > life) {
                    continue;
                }
                Vec3 dir = sphere(seed, 100 + i);
                Vec3 p = c.add(dir.scale((2.0D + radius * 0.9D * Rnd.f(seed, 300 + i)) * easeOut(ts * 2.0F)));
                d.glow(p, 0.5F * (1.0F - ts / life), t * 0.3F + i, i % 2 == 0 ? 0xFFFFFF : color, 1.0F - ts / life);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.9F * (1.0F - Rnd.ramp(t, 0.0F, 14.0F)) * falloff(cam, origin, 40.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.3F * (1.0F - Rnd.ramp(t, 0.0F, 5.0F)) * falloff(cam, origin, 6.0F);
        }

        @Override
        public int flashColor() {
            return 0xDDEEFF;
        }
    }

    /** Trainee electrique derriere un joueur qui court : un arc au ras du sol et de petites fourches. */
    public static final class Zap extends Fx {
        public Zap(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 dir = new Vec3(target.x, 0.0D, target.z);
            dir = dir.lengthSqr() < 1.0E-4D ? AXIS_X : dir.normalize();
            Vec3 side = dir.cross(AXIS_Y).normalize();
            Vec3 p0 = origin.add(0.0D, 0.15D, 0.0D);
            float f = easeOut(t / 4.0F);
            float a = 1.0F - Rnd.ramp(t, 5.0F, duration);
            int step = (int) (t / 2.0F);

            d.type(VfxRenderTypes.LINE_ADD);
            Vec3 back = p0.subtract(dir.scale(6.0D * f));
            d.lightning(p0.add(0.0D, 0.7D, 0.0D), back.add(0.0D, 0.1D, 0.0D), seed + step, 4, 0.18F, 0.1F, color, a, 2);
            d.lightning(p0.add(0.0D, 0.3D, 0.0D), back, seed + 91 + step, 3, 0.2F, 0.07F, 0xFFFFFF, a * 0.8F, 0);
            for (int i = 0; i < 3; i++) {
                Vec3 anchor = p0.subtract(dir.scale(1.0D + i * 1.8D * f));
                double s = (i % 2 == 0 ? 1.0D : -1.0D) * (1.2D + 0.5D * Rnd.f(seed, i));
                d.lightning(anchor.add(0.0D, 0.5D, 0.0D), anchor.add(side.scale(s)).add(0.0D, 0.04D, 0.0D),
                        seed + 7 * i + step, 3, 0.2F, 0.06F, color, a * 0.8F, 0);
            }
            d.ring(p0.add(0, -0.1D, 0), AXIS_X, AXIS_Z, 0.4D + 1.3D * easeOut(t / 8.0F), 0.18D * a + 0.04D, color,
                    0.7F * a, 28);

            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(p0.add(0, 0.4D, 0), 2.4F * a, color, 0.7F * a);
            d.glow(p0.add(0, 0.4D, 0), 1.0F * a, 0xFFFFFF, a);
            for (int i = 1; i <= 4; i++) {
                d.glow(p0.subtract(dir.scale(i * 1.2D * f)).add(0, 0.3D, 0), 1.2F * a / i + 0.2F * a, color,
                        0.5F * a / i);
            }

            d.type(VfxRenderTypes.STAR_ADD);
            float ts = t / 16.0F;
            for (int i = 0; i < 9; i++) {
                float life = 0.4F + 0.5F * Rnd.f(seed, 20 + i);
                if (ts > life) {
                    continue;
                }
                Vec3 sd = sphere(seed, 30 + i);
                Vec3 p = p0.subtract(dir.scale(Rnd.f(seed, 50 + i) * 3.0D))
                        .add(sd.x * 1.4D * ts, 0.3D + Math.abs(sd.y) * 2.2D * ts - 3.0D * ts * ts, sd.z * 1.4D * ts);
                d.glow(p, 0.35F * (1.0F - ts / life), t * 0.4F + i, i % 2 == 0 ? 0xFFFFFF : color, 1.0F - ts / life);
            }
        }
    }

    private FxStorm() {}
}
