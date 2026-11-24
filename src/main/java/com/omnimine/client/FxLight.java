package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import net.minecraft.world.phys.Vec3;

/** Effets de foudre : eclair du ciel, arcs electriques, petit eclair. */
public final class FxLight {

    /** Foudre divine : eclair du ciel, onde de choc, gerbe d'etincelles, tremblement et flash. */
    public static final class Smite extends Fx {
        public Smite(VfxPacket p) {
            super(p);
        }

        private Vec3 spark(Vec3 ground, int i, float ts, float s) {
            Vec3 dir = sphere(seed, i);
            dir = new Vec3(dir.x, Math.abs(dir.y) * 0.9D + 0.15D, dir.z).normalize();
            double speed = (5.0D + Rnd.f(seed, i + 500) * 11.0D) * Math.sqrt(s);
            return ground.add(dir.scale(speed * ts)).add(0, -4.0D * ts * ts, 0);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            float s = param;
            Vec3 ground = origin;

            d.type(VfxRenderTypes.LINE_ADD);
            if (t < 14.0F) {
                float a = 1.0F - Rnd.ramp(t, 5.0F, 14.0F);
                int sd = seed + (int) (t / 2.0F);
                Vec3 top = ground.add(Rnd.s(sd, 1) * 6.0D, 85.0D, Rnd.s(sd, 2) * 6.0D);
                d.lightning(top, ground, sd, 7, 0.16F, 0.45F * s, color, a, 6);
                d.pillar(ground, 85.0D, 3.4F * s * (1.0F - Rnd.ramp(t, 0.0F, 14.0F)), color, 0.55F * a, 0.12F * a);
            }
            for (int k = 0; k < 2; k++) {
                float tk = t - k * 4.0F;
                if (tk < 0.0F) {
                    continue;
                }
                double r = 1.0D + 10.0D * s * easeOut(tk / 20.0F);
                float a = (1.0F - Rnd.ramp(tk, 4.0F, 24.0F)) * 0.9F;
                d.ring(ground.add(0, 0.12, 0), AXIS_X, AXIS_Z, r, 1.4D * (1.0F - tk / 34.0F) + 0.3D,
                        k == 0 ? color : 0xFFFFFF, a, 56);
            }
            float ts = t / 20.0F;
            for (int i = 0; i < 70; i++) {
                float life = 0.5F + Rnd.f(seed, i + 600) * 0.6F;
                if (ts > life) {
                    continue;
                }
                float a = 1.0F - ts / life;
                d.ribbon(spark(ground, i, Math.max(0.0F, ts - 0.07F), s), spark(ground, i, ts, s),
                        0.0F, 0.16F * s, color, 0.0F, a);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            float flash = 1.0F - Rnd.ramp(t, 0.0F, 13.0F);
            d.glow(ground.add(0, 0.6, 0), 15.0F * s * flash, color, 0.85F * flash);
            d.glow(ground.add(0, 0.6, 0), 6.0F * s * flash, 0xFFFFFF, flash);

            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 70; i++) {
                float life = 0.5F + Rnd.f(seed, i + 600) * 0.6F;
                if (ts > life) {
                    continue;
                }
                d.glow(spark(ground, i, ts, s), 0.5F * s * (1.0F - ts / life), t * 0.2F + i, 0xFFFFFF,
                        1.0F - ts / life);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.9F * param * (1.0F - Rnd.ramp(t, 0.0F, 18.0F)) * falloff(cam, origin, 70.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.7F * param * (1.0F - Rnd.ramp(t, 0.0F, 7.0F)) * falloff(cam, origin, 45.0F);
        }

        @Override
        public int flashColor() {
            return 0xDDEEFF;
        }
    }

    /** Arc electrique entre deux points (chaine d'eclairs). */
    public static final class Arc extends Fx {
        public Arc(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            float a = 1.0F - Rnd.ramp(t, 8.0F, duration);
            int sd = seed + (int) (t / 2.0F);
            d.type(VfxRenderTypes.LINE_ADD);
            d.lightning(origin, target, sd, 5, 0.14F, 0.16F, color, a, 2);
            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(target, 2.6F * a, color, 0.8F * a);
            d.glow(origin, 2.0F * a, color, 0.6F * a);
        }
    }

    /** Petit eclair sur un coup d'epee. */
    public static final class Mini extends Fx {
        public Mini(VfxPacket p) {
            super(p);
        }

        private Vec3 spark(int i, float ts) {
            Vec3 dir = sphere(seed, i);
            dir = new Vec3(dir.x, Math.abs(dir.y) + 0.2D, dir.z).normalize();
            double speed = 3.0D + Rnd.f(seed, i + 500) * 5.0D;
            return origin.add(0, 0.8, 0).add(dir.scale(speed * ts)).add(0, -3.0D * ts * ts, 0);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            float a = 1.0F - Rnd.ramp(t, 4.0F, duration);
            Vec3 hit = origin.add(0, 0.9, 0);
            d.type(VfxRenderTypes.LINE_ADD);
            int sd = seed + (int) (t / 2.0F);
            d.lightning(hit.add(Rnd.s(sd, 1) * 1.5D, 18.0D, Rnd.s(sd, 2) * 1.5D), hit, sd, 5, 0.15F, 0.16F, color, a, 2);
            d.ring(origin.add(0, 0.1, 0), AXIS_X, AXIS_Z, 0.5D + 2.6D * easeOut(t / 10.0F), 0.4D, color, a * 0.8F, 28);
            float ts = t / 20.0F;
            for (int i = 0; i < 14; i++) {
                if (ts > 0.6F) {
                    break;
                }
                d.ribbon(spark(i, Math.max(0.0F, ts - 0.05F)), spark(i, ts), 0.0F, 0.07F, color, 0.0F, 1.0F - ts / 0.6F);
            }
            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(t, 0.0F, 8.0F);
            d.glow(hit, 3.2F * f, color, 0.9F * f);
            d.glow(hit, 1.4F * f, 0xFFFFFF, f);
        }
    }

    private FxLight() {}
}
