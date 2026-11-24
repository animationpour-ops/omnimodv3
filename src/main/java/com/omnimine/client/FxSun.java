package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import net.minecraft.world.phys.Vec3;

/** Effets de l'epee du soleil : eruption solaire a chaque coup, rayon orbital au clic droit. */
public final class FxSun {

    private static final int FIRE = 0xFF6A10;

    /** Coup de l'epee : une eruption de lumiere et des protuberances solaires. */
    public static final class Flare extends Fx {
        public Flare(VfxPacket p) {
            super(p);
        }

        private Vec3 spark(int i, float ts) {
            Vec3 dir = sphere(seed, i);
            double speed = 3.0D + Rnd.f(seed, i + 500) * 7.0D;
            return origin.add(dir.scale(speed * ts)).add(0.0D, -3.5D * ts * ts, 0.0D);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 c = origin;
            float out = 1.0F - Rnd.ramp(t, 8.0F, duration);

            d.type(VfxRenderTypes.LINE_ADD);
            // rayons du soleil
            for (int i = 0; i < 12; i++) {
                float a = 1.0F - Rnd.ramp(t, 0.0F, 9.0F);
                Vec3 dir = sphere(seed, 100 + i);
                double len = 2.0D + 3.0D * Rnd.f(seed, 120 + i);
                d.ribbon(c, c.add(dir.scale(len * easeOut(t / 5.0F))), 0.28F, 0.0F, i % 2 == 0 ? color : 0xFFFFFF, a,
                        0.0F);
            }
            // protuberances : des arcs de feu qui jaillissent puis retombent
            for (int i = 0; i < 7; i++) {
                double ang = Rnd.f(seed, 10 + i) * Math.PI * 2.0D;
                Vec3 dir = new Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
                double len = 1.8D + 2.2D * Rnd.f(seed, 30 + i);
                double hgt = 1.0D + 1.8D * Rnd.f(seed, 50 + i);
                float grow = easeOut(t / 9.0F);
                float a = (1.0F - Rnd.ramp(t, 9.0F, duration)) * 0.9F;
                Vec3 prev = c;
                for (int j = 1; j <= 8; j++) {
                    double s = j / 8.0D;
                    Vec3 p = c.add(dir.scale(len * s * grow)).add(0.0D, Math.sin(Math.PI * s) * hgt * grow, 0.0D);
                    d.ribbon(prev, p, 0.22F * (1.0F - (float) s * 0.6F), 0.22F * (1.0F - (float) (s + 0.125D) * 0.6F),
                            i % 2 == 0 ? FIRE : color, a, a);
                    prev = p;
                }
            }
            // onde de chaleur
            d.ring(c, d.left, d.up, 0.4D + 3.8D * easeOut(t / 10.0F), 0.5D * out + 0.1D, color, 0.9F * out, 40);
            d.ring(c.add(0, -0.7D, 0), AXIS_X, AXIS_Z, 0.6D + 3.4D * easeOut(t / 12.0F), 0.4D * out + 0.1D, FIRE,
                    0.7F * out, 40);

            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(t, 0.0F, 9.0F);
            d.glow(c, 7.0F * f + 1.0F, color, 0.9F * f + 0.1F * out);
            d.glow(c, 3.0F * f, 0xFFFFFF, f);
            d.glow(c, 2.4F, FIRE, 0.35F * out);

            d.type(VfxRenderTypes.STAR_ADD);
            float ts = t / 20.0F;
            for (int i = 0; i < 30; i++) {
                float life = 0.35F + 0.5F * Rnd.f(seed, 600 + i);
                if (ts > life) {
                    continue;
                }
                d.glow(spark(i, ts), 0.45F * (1.0F - ts / life), t * 0.3F + i, i % 3 == 0 ? 0xFFFFFF : FIRE,
                        1.0F - ts / life);
            }
            d.glow(c, 2.6F * f, t * 0.4F, 0xFFFFFF, f);
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.25F * (1.0F - Rnd.ramp(t, 0.0F, 8.0F)) * falloff(cam, origin, 18.0F);
        }
    }

    /** Rayon solaire orbital : une charge de 30 ticks puis le rayon, la deflagration, le cratere en feu. */
    public static final class Beam extends Fx {
        private static final int CHARGE = 30;

        public Beam(VfxPacket p) {
            super(p);
        }

        private Vec3 spark(Vec3 g, int i, float ts) {
            Vec3 dir = sphere(seed, i);
            dir = new Vec3(dir.x, Math.abs(dir.y) * 1.1D + 0.25D, dir.z).normalize();
            double speed = 6.0D + Rnd.f(seed, i + 500) * 18.0D;
            return g.add(dir.scale(speed * ts)).add(0.0D, -9.0D * ts * ts, 0.0D);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 g = origin;
            int gold = color;
            float ch = Rnd.clamp01(t / CHARGE);
            float imp = t - CHARGE;
            float end = duration - CHARGE;

            d.type(VfxRenderTypes.LINE_ADD);
            // ---- charge : cercle de ciblage, runes, rayons au sol
            float markA = Rnd.ramp(t, 0.0F, 8.0F) * (1.0F - Rnd.ramp(imp, 0.0F, 22.0F));
            if (markA > 0.0F) {
                double spin = t * 0.07D;
                Vec3 gr = g.add(0.0D, 0.1D, 0.0D);
                d.ring(gr, AXIS_X, AXIS_Z, 11.0D - 0.6D * easeIn(ch), 0.35D, gold, 0.9F * markA, 96);
                d.ring(gr, AXIS_X, AXIS_Z, 7.5D, 0.2D, gold, 0.6F * markA, 72);
                d.ring(gr, AXIS_X, AXIS_Z, 3.2D + 1.0D * Math.sin(t * 0.3D), 0.18D, 0xFFFFFF, 0.5F * markA, 48);
                for (int k = 0; k < 8; k++) {
                    double a0 = spin * (k % 2 == 0 ? 1 : -1) + k * (Math.PI / 4.0D);
                    d.arc(gr.add(0, 0.01D, 0), AXIS_X, AXIS_Z, 9.2D, 0.55D, a0, a0 + 0.38D, 0xFFFFFF, 0.8F * markA, 8);
                }
                for (int k = 0; k < 12; k++) {
                    double ang = spin * 0.5D + k * (Math.PI / 6.0D);
                    Vec3 dv = new Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
                    d.ribbon(gr.add(dv.scale(2.0D)), gr.add(dv.scale(10.6D)), 0.2F, 0.2F, gold, 0.4F * markA * ch,
                            0.05F * markA);
                }
            }
            // ---- faisceau fin qui se charge dans le ciel
            if (imp < 0.0F) {
                float w = 0.3F + 1.6F * easeIn(ch);
                float a = 0.15F + 0.7F * easeIn(ch);
                float flick = 0.8F + 0.2F * (float) Math.sin(t * 1.7F);
                d.pillar(g, 140.0D, w, gold, a * flick, a * 0.5F);
                d.pillar(g, 140.0D, w * 0.35F, 0xFFFFFF, a * flick, a * 0.6F);
                // des lignes de lumiere qui convergent vers le point d'impact
                for (int i = 0; i < 24; i++) {
                    float ph = fract(t * 0.045F + Rnd.f(seed, 40 + i));
                    double ang = Rnd.f(seed, 80 + i) * Math.PI * 2.0D;
                    double r = 9.0D * (1.0D - ph);
                    Vec3 p1 = g.add(Math.cos(ang) * r, 0.2D + ph * 2.0D, Math.sin(ang) * r);
                    Vec3 p0 = g.add(Math.cos(ang) * (r + 1.5D), 0.2D + ph * 2.0D, Math.sin(ang) * (r + 1.5D));
                    d.ribbon(p0, p1, 0.0F, 0.18F, gold, 0.0F, 0.9F * ch);
                }
            }

            // ---- impact
            if (imp >= 0.0F) {
                float fadeAll = 1.0F - Rnd.ramp(imp, end - 16.0F, end);
                float collapse = 1.0F - easeOut(Rnd.ramp(imp, 0.0F, 24.0F));
                float w = (9.0F * collapse + 2.4F) * (1.0F - Rnd.ramp(imp, 22.0F, end));
                d.pillar(g, 170.0D, w, FIRE, 0.85F * fadeAll, 0.5F * fadeAll);
                d.pillar(g, 170.0D, w * 0.55F, gold, 0.95F * fadeAll, 0.6F * fadeAll);
                d.pillar(g, 170.0D, w * 0.25F, 0xFFFFFF, fadeAll, 0.8F * fadeAll);
                // ondes de choc
                for (int k = 0; k < 3; k++) {
                    float tk = imp - k * 3.0F;
                    if (tk < 0.0F) {
                        continue;
                    }
                    d.ring(g.add(0, 0.2D, 0), AXIS_X, AXIS_Z, 2.0D + 22.0D * easeOut(tk / 18.0F),
                            3.0D * (1.0F - tk / 26.0F) + 0.4D, k == 0 ? 0xFFFFFF : (k == 1 ? gold : FIRE),
                            (1.0F - Rnd.ramp(tk, 4.0F, 26.0F)) * 0.9F, 96);
                }
                // l'onde se propage aussi en hauteur (dome)
                d.ring(g.add(0, 1.5D, 0), d.left, d.up, 1.0D + 14.0D * easeOut(imp / 12.0F), 1.5D * (1.0F - imp / 18.0F) + 0.2D,
                        gold, (1.0F - Rnd.ramp(imp, 2.0F, 16.0F)) * 0.8F, 64);
                // protuberances geantes
                for (int i = 0; i < 10; i++) {
                    double ang = Rnd.f(seed, 120 + i) * Math.PI * 2.0D;
                    Vec3 dir = new Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
                    double len = 6.0D + 6.0D * Rnd.f(seed, 140 + i);
                    double hgt = 4.0D + 6.0D * Rnd.f(seed, 160 + i);
                    float grow = easeOut(imp / 10.0F);
                    float a = (1.0F - Rnd.ramp(imp, 10.0F, 34.0F)) * 0.9F;
                    Vec3 prev = g;
                    for (int j = 1; j <= 10; j++) {
                        double s = j / 10.0D;
                        Vec3 p = g.add(dir.scale(len * s * grow)).add(0.0D, Math.sin(Math.PI * s) * hgt * grow, 0.0D);
                        d.ribbon(prev, p, 0.6F * (1.0F - (float) s * 0.6F), 0.6F * (1.0F - (float) (s + 0.1D) * 0.6F),
                                i % 2 == 0 ? FIRE : gold, a, a);
                        prev = p;
                    }
                }
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            // soleil qui brille tout en haut
            if (imp < 6.0F) {
                float a = (0.2F + 0.8F * easeIn(ch)) * (1.0F - Rnd.ramp(imp, 0.0F, 6.0F));
                d.glow(g.add(0, 70.0D, 0), 40.0F * a + 10.0F, gold, 0.55F * a);
                d.glow(g.add(0, 70.0D, 0), 16.0F * a, 0xFFFFFF, 0.8F * a);
            }
            if (imp < 0.0F) {
                float pulse = 0.7F + 0.3F * (float) Math.sin(t * (0.6F + 0.8F * ch));
                d.glow(g.add(0, 0.8D, 0), (2.0F + 7.0F * easeIn(ch)) * pulse, gold, 0.5F * ch + 0.15F);
                d.glow(g.add(0, 0.8D, 0), 1.5F + 3.0F * ch, 0xFFFFFF, 0.6F * ch);
            } else {
                float fl = 1.0F - Rnd.ramp(imp, 0.0F, 26.0F);
                d.glow(g.add(0, 1.0D, 0), 36.0F * fl + 8.0F, FIRE, 0.9F * (1.0F - Rnd.ramp(imp, 4.0F, end)));
                d.glow(g.add(0, 1.0D, 0), 20.0F * fl, 0xFFFFFF, fl);
                // flammes qui lechent le cratere
                float fa = Rnd.ramp(imp, 4.0F, 12.0F) * (1.0F - Rnd.ramp(imp, end - 12.0F, end));
                for (int i = 0; i < 26; i++) {
                    float ph = fract(t * 0.06F + Rnd.f(seed, 700 + i));
                    double ang = Rnd.f(seed, 720 + i) * Math.PI * 2.0D;
                    double r = Math.sqrt(Rnd.f(seed, 740 + i)) * 9.0D;
                    Vec3 p = g.add(Math.cos(ang) * r, 0.3D + ph * 4.0D, Math.sin(ang) * r);
                    d.glow(p, 2.2F * (1.0F - ph) + 0.4F, i % 2 == 0 ? FIRE : gold, 0.55F * (1.0F - ph) * fa);
                }
            }

            d.type(VfxRenderTypes.STAR_ADD);
            if (imp >= 0.0F) {
                float ts = imp / 28.0F;
                for (int i = 0; i < 110; i++) {
                    float life = 0.4F + 0.6F * Rnd.f(seed, 600 + i);
                    if (ts > life) {
                        continue;
                    }
                    d.glow(spark(g, i, ts), 0.9F * (1.0F - ts / life), t * 0.2F + i, i % 3 == 0 ? 0xFFFFFF : FIRE,
                            1.0F - ts / life);
                }
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            float imp = t - CHARGE;
            if (imp < 0.0F) {
                return 0.12F * Rnd.clamp01(t / CHARGE) * falloff(cam, origin, 80.0F);
            }
            return 1.2F * (1.0F - Rnd.ramp(imp, 0.0F, 34.0F)) * falloff(cam, origin, 90.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            float imp = t - CHARGE;
            return imp < 0.0F ? 0.0F : 0.95F * (1.0F - Rnd.ramp(imp, 0.0F, 12.0F)) * falloff(cam, origin, 60.0F);
        }

        @Override
        public int flashColor() {
            return 0xFFE3B0;
        }
    }

    private FxSun() {}
}
