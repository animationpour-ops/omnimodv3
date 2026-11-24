package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import net.minecraft.world.phys.Vec3;

/** Effets des bottes lunaires : double saut (croissant), plongeon (comete) et impact lunaire. */
public final class FxMoon {

    private static final int ROCK = 0x6A707C;

    /** Saut lunaire : un croissant de lumiere sous les pieds, des plumes de poussiere d'etoiles. */
    public static final class Leap extends Fx {
        public Leap(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 c = origin.add(0.0D, 0.1D, 0.0D);
            float out = 1.0F - Rnd.ramp(t, 6.0F, duration);
            float ex = easeOut(t / 12.0F);
            boolean second = param >= 1.5F;
            int cool = second ? 0xFFFFFF : color;

            d.type(VfxRenderTypes.LINE_ADD);
            // croissant sous les pieds : deux arcs decales
            double spin = t * 0.12D * (second ? -1.0D : 1.0D);
            d.arc(c, AXIS_X, AXIS_Z, 0.9D + 1.1D * ex, 0.34D * out + 0.08D, spin, spin + Math.PI * 1.25D, cool,
                    0.95F * out, 28);
            d.arc(c, AXIS_X, AXIS_Z, 0.65D + 0.8D * ex, 0.2D * out + 0.05D, spin + 0.3D, spin + Math.PI * 1.05D, color,
                    0.7F * out, 24);
            // onde au sol
            d.ring(c, AXIS_X, AXIS_Z, 0.5D + 3.2D * ex, 0.35D * out + 0.05D, color, 0.7F * out, 36);
            // plumes de lumiere qui retombent doucement
            for (int i = 0; i < 12; i++) {
                double ang = Rnd.f(seed, i) * Math.PI * 2.0D;
                double rr = 0.2D + 0.9D * Rnd.f(seed, 20 + i);
                Vec3 top = c.add(Math.cos(ang) * rr, 0.2D, Math.sin(ang) * rr);
                double len = (1.6D + 2.4D * Rnd.f(seed, 40 + i)) * easeOut(t / 8.0F);
                d.ribbon(top, top.add(Math.cos(ang) * 0.3D, -len, Math.sin(ang) * 0.3D), 0.22F, 0.0F,
                        i % 2 == 0 ? color : 0xFFFFFF, 0.8F * out, 0.0F);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(t, 0.0F, 9.0F);
            d.glow(c.add(0, 0.3D, 0), 5.0F * f + 0.5F, color, 0.7F * f + 0.1F * out);
            d.glow(c.add(0, 0.3D, 0), 2.0F * f, 0xFFFFFF, f);

            d.type(VfxRenderTypes.STAR_ADD);
            float ts = t / 26.0F;
            for (int i = 0; i < 28; i++) {
                float life = 0.5F + 0.5F * Rnd.f(seed, 100 + i);
                if (ts > life) {
                    continue;
                }
                double ang = Rnd.f(seed, 120 + i) * Math.PI * 2.0D;
                double rr = (0.3D + 2.2D * Rnd.f(seed, 140 + i)) * easeOut(ts * 2.0F);
                Vec3 p = c.add(Math.cos(ang) * rr, 0.4D + 0.6D * Rnd.f(seed, 160 + i) - 1.2D * ts * ts, Math.sin(ang) * rr);
                d.glow(p, 0.5F * (1.0F - ts / life), t * 0.2F + i, i % 3 == 0 ? 0xFFFFFF : color, 1.0F - ts / life);
            }
        }
    }

    /**
     * Plongeon (param 0) : une comete qui suit le joueur. Impact (param 1) : le sol se fend, des eclats de lune
     * volent, des croissants de lumiere balayent le terrain.
     */
    public static final class Slam extends Fx {
        public Slam(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            if (param < 0.5F) {
                dive(d, t, partial);
            } else {
                impact(d, t);
            }
        }

        // ------------------------------------------------------------------ plongeon

        private void dive(VfxDraw d, float t, float partial) {
            Vec3 feet = pos(partial);
            Vec3 body = feet.add(0.0D, 1.0D, 0.0D);
            float in = Rnd.ramp(t, 0.0F, 4.0F);
            float out = 1.0F - Rnd.ramp(t, duration - 4.0F, duration);
            float a = in * out;

            d.type(VfxRenderTypes.LINE_ADD);
            // queue de comete (vers le haut, le joueur tombe)
            Vec3 tail = body.add(0.0D, 15.0D, 0.0D);
            d.ribbon(body, tail, 1.7F, 0.0F, color, 0.85F * a, 0.0F);
            d.ribbon(body, body.add(0.0D, 11.0D, 0.0D), 0.7F, 0.0F, 0xFFFFFF, 0.95F * a, 0.0F);
            // trois helices
            for (int strand = 0; strand < 3; strand++) {
                Vec3 prev = body;
                for (int j = 1; j <= 16; j++) {
                    float s = j / 16.0F;
                    double ang = t * 0.55D + strand * 2.094D + s * 6.0D;
                    double r = 0.35D + 0.9D * s;
                    Vec3 p = body.add(Math.cos(ang) * r, s * 13.0D, Math.sin(ang) * r);
                    float al = 0.9F * (1.0F - s) * a;
                    d.ribbon(prev, p, 0.28F * (1.0F - s), 0.28F * (1.0F - (j + 1) / 16.0F), strand == 0 ? 0xFFFFFF : color, al,
                            al * 0.9F);
                    prev = p;
                }
            }
            // anneaux de vitesse qui montent le long du sillage
            for (int k = 0; k < 6; k++) {
                float ph = fract(t * 0.16F + k / 6.0F);
                double y = 0.6D + ph * 12.0D;
                d.ring(feet.add(0.0D, y, 0.0D), AXIS_X, AXIS_Z, 0.9D + 1.6D * ph, 0.16D, color, 0.7F * (1.0F - ph) * a, 28);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(body, 3.6F, color, 0.55F * a);
            d.glow(body, 1.7F, 0xFFFFFF, 0.9F * a);
            d.glow(feet.add(0.0D, -0.2D, 0.0D), 2.8F, 0xFFFFFF, 0.5F * a);

            // poussiere d'etoiles qui file vers le haut
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 26; i++) {
                float ph = fract(t * 0.12F + Rnd.f(seed, i));
                double ang = Rnd.f(seed, 40 + i) * Math.PI * 2.0D;
                double rr = 0.4D + 1.3D * Rnd.f(seed, 60 + i);
                Vec3 p = body.add(Math.cos(ang) * rr, ph * 14.0D, Math.sin(ang) * rr);
                d.glow(p, 0.5F * (1.0F - ph) + 0.1F, t * 0.2F + i, i % 3 == 0 ? 0xFFFFFF : color, (1.0F - ph) * a);
            }
        }

        // -------------------------------------------------------------------- impact

        private void impact(VfxDraw d, float t) {
            Vec3 g = origin.add(0.0D, 0.15D, 0.0D);
            float fadeAll = 1.0F - Rnd.ramp(t, duration - 14.0F, duration);

            d.type(VfxRenderTypes.LINE_ADD);
            // ondes de choc
            for (int k = 0; k < 4; k++) {
                float tk = t - k * 2.5F;
                if (tk < 0.0F) {
                    continue;
                }
                d.ring(g, AXIS_X, AXIS_Z, 1.0D + (10.0D + 2.0D * k) * easeOut(tk / 20.0F),
                        1.6D * (1.0F - tk / 30.0F) + 0.2D, k == 0 ? 0xFFFFFF : color,
                        (1.0F - Rnd.ramp(tk, 4.0F, 30.0F)) * 0.9F, 80);
            }
            // trois croissants de lune qui balayent le terrain
            for (int k = 0; k < 3; k++) {
                float tk = t - 2.0F - k * 2.0F;
                if (tk < 0.0F) {
                    continue;
                }
                double rad = 1.5D + 11.0D * easeOut(tk / 22.0F);
                double a0 = k * (Math.PI * 2.0D / 3.0D) + tk * 0.04D;
                d.arc(g.add(0, 0.05D, 0), AXIS_X, AXIS_Z, rad, 0.9D * (1.0F - tk / 30.0F) + 0.15D, a0, a0 + 2.2D,
                        k == 1 ? 0xFFFFFF : color, (1.0F - Rnd.ramp(tk, 6.0F, 28.0F)) * 0.95F, 40);
            }
            // rayons de gravite : seismogramme qui part du centre
            for (int i = 0; i < 20; i++) {
                double ang = i * Math.PI * 2.0D / 20.0D + Rnd.s(seed, i) * 0.1D;
                Vec3 dv = new Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
                double len = (4.0D + 9.0D * Rnd.f(seed, 20 + i)) * easeOut(t / 10.0F);
                float al = (1.0F - Rnd.ramp(t, 4.0F, 30.0F)) * 0.8F;
                d.ribbon(g.add(dv.scale(1.0D)), g.add(dv.scale(1.0D + len)), 0.35F, 0.0F, color, al, 0.0F);
            }
            // colonne de lumiere pale
            float w = 4.0F * (1.0F - easeOut(Rnd.ramp(t, 0.0F, 16.0F))) + 0.8F;
            d.pillar(g, 34.0D, w, color, 0.75F * fadeAll * (1.0F - Rnd.ramp(t, 8.0F, 30.0F)), 0.0F);
            d.pillar(g, 30.0D, w * 0.4F, 0xFFFFFF, 0.9F * fadeAll * (1.0F - Rnd.ramp(t, 6.0F, 24.0F)), 0.0F);

            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(t, 0.0F, 14.0F);
            d.glow(g.add(0, 1.0D, 0), 22.0F * f + 3.0F, color, 0.8F * f + 0.1F * fadeAll);
            d.glow(g.add(0, 1.0D, 0), 9.0F * f, 0xFFFFFF, f);

            // poussiere lunaire grise qui monte
            d.type(VfxRenderTypes.GLOW_DARK);
            for (int i = 0; i < 18; i++) {
                float ts = t - Rnd.f(seed, 200 + i) * 4.0F;
                if (ts < 0.0F || ts > 36.0F) {
                    continue;
                }
                double ang = Rnd.f(seed, 220 + i) * Math.PI * 2.0D;
                double rr = (1.0D + 7.0D * Rnd.f(seed, 240 + i)) * easeOut(ts / 20.0F);
                Vec3 p = g.add(Math.cos(ang) * rr, 0.3D + ts * 0.12D * (0.5D + Rnd.f(seed, 260 + i)), Math.sin(ang) * rr);
                d.glow(p, 3.0F + 4.0F * (ts / 36.0F), ROCK, 0.4F * (1.0F - ts / 36.0F));
            }
            // eclats de roche lunaire
            d.type(VfxRenderTypes.DISC_DARK);
            float ts = t / 20.0F;
            for (int i = 0; i < 46; i++) {
                float life = 0.7F + 0.9F * Rnd.f(seed, 300 + i);
                if (ts > life) {
                    continue;
                }
                d.glow(rock(g, i, ts), 0.35F + 0.5F * Rnd.f(seed, 380 + i), t * 0.3F + i, ROCK, 0.95F * (1.0F - 0.6F * ts / life));
            }

            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 90; i++) {
                float life = 0.5F + 0.7F * Rnd.f(seed, 400 + i);
                if (ts > life) {
                    continue;
                }
                d.glow(rock(g, 500 + i, ts), 0.55F * (1.0F - ts / life), t * 0.25F + i, i % 3 == 0 ? 0xFFFFFF : color,
                        1.0F - ts / life);
            }
        }

        /** Trajectoire d'un eclat : lance en l'air avec une faible gravite (lunaire). */
        private Vec3 rock(Vec3 g, int i, float ts) {
            Vec3 dir = sphere(seed, i);
            dir = new Vec3(dir.x, Math.abs(dir.y) * 1.2D + 0.35D, dir.z).normalize();
            double speed = 5.0D + Rnd.f(seed, i + 900) * 14.0D;
            return g.add(dir.scale(speed * ts)).add(0.0D, -5.0D * ts * ts, 0.0D);
        }

        @Override
        public float shake(Vec3 cam, float t) {
            if (param < 0.5F) {
                return 0.15F * falloff(cam, pos(0.0F), 30.0F);
            }
            return 1.1F * (1.0F - Rnd.ramp(t, 0.0F, 26.0F)) * falloff(cam, origin, 70.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return param < 0.5F ? 0.0F : 0.8F * (1.0F - Rnd.ramp(t, 0.0F, 7.0F)) * falloff(cam, origin, 45.0F);
        }

        @Override
        public int flashColor() {
            return 0xE4EEFF;
        }
    }

    private FxMoon() {}
}
