package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import net.minecraft.world.phys.Vec3;

/** Les huit petits pouvoirs des mobs mines : pluie d'os, explosion, peste, toile, rayon, cri, seisme, passage. */
public final class FxMobs {

    // =====================================================================================================
    // Squelette : tempete d'os
    // =====================================================================================================

    public static final class BoneRain extends Fx {
        public BoneRain(VfxPacket p) {
            super(p);
        }

        /** Point d'impact de l'os i pendant son cycle `cyc`. */
        private Vec3 land(Vec3 g, double radius, int i, int cyc) {
            int sd = seed + cyc * 977 + i * 13;
            double ang = Rnd.f(sd, 1) * Math.PI * 2.0D;
            double rr = Math.sqrt(Rnd.f(sd, 2)) * radius;
            return g.add(Math.cos(ang) * rr, 0.0D, Math.sin(ang) * rr);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 g = origin;
            double radius = Math.max(2.0F, param);
            // plus la zone est grande, plus il tombe d'os (et plus ils sont gros)
            int count = (int) (90.0D * Math.max(1.0D, radius * radius / 49.0D));
            double bz = Math.max(1.0D, radius / 7.0D);
            float bf = (float) bz;
            float a = Rnd.ramp(t, 0.0F, 6.0F) * (1.0F - Rnd.ramp(t, duration - 12.0F, duration));
            int bone = color;

            d.type(VfxRenderTypes.LINE_ADD);
            // cercle de la zone, tirets qui tournent
            d.ring(g.add(0, 0.1D, 0), AXIS_X, AXIS_Z, radius, 0.3D, bone, 0.6F * a, 72);
            for (int k = 0; k < 10; k++) {
                double a0 = t * 0.06D + k * (Math.PI / 5.0D);
                d.arc(g.add(0, 0.12D, 0), AXIS_X, AXIS_Z, radius * 0.72D, 0.3D, a0, a0 + 0.45D, 0xFFFFFF, 0.5F * a, 8);
            }
            // des os qui tombent du ciel
            for (int i = 0; i < count; i++) {
                float period = 12.0F + 12.0F * Rnd.f(seed, i);
                float tt = t + Rnd.f(seed, i + 200) * period;
                int cyc = (int) (tt / period);
                float ph = (tt - cyc * period) / period;
                Vec3 land = land(g, radius, i, cyc);
                Vec3 pos = land.add(0.0D, 26.0D * bz * (1.0D - ph), 0.0D);
                int sd = seed + cyc * 977 + i * 13;
                Vec3 tilt = new Vec3(Rnd.s(sd, 3) * 0.35D, 1.0D, Rnd.s(sd, 4) * 0.35D).normalize();
                d.ribbon(pos, pos.add(tilt.scale(1.5D * bz)), 0.24F * bf, 0.14F * bf, bone, 0.95F * a, 0.95F * a);
                d.ribbon(pos.add(tilt.scale(0.15D * bz)), pos.add(tilt.scale(1.35D * bz)), 0.1F * bf, 0.06F * bf, 0xFFFFFF, a, a);
                d.ribbon(pos.add(tilt.scale(1.5D * bz)), pos.add(tilt.scale(5.5D * bz)), 0.12F * bf, 0.0F, bone, 0.4F * a, 0.0F);
                // poussiere d'os a l'endroit ou l'os precedent s'est plante
                float puffAge = ph * period;
                if (cyc > 0 && puffAge < 6.0F) {
                    Vec3 old = land(g, radius, i, cyc - 1);
                    float f = puffAge / 6.0F;
                    d.ring(old.add(0, 0.1D, 0), AXIS_X, AXIS_Z, (0.2D + 1.0D * f) * bz, 0.22D * (1.0F - f) + 0.04D, 0xFFFFFF,
                            0.7F * (1.0F - f) * a, 14);
                }
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            for (int i = 0; i < count; i++) {
                float period = 12.0F + 12.0F * Rnd.f(seed, i);
                float tt = t + Rnd.f(seed, i + 200) * period;
                int cyc = (int) (tt / period);
                float ph = (tt - cyc * period) / period;
                if (cyc > 0 && ph * period < 3.0F) {
                    Vec3 old = land(g, radius, i, cyc - 1);
                    d.glow(old.add(0, 0.3D, 0), 1.6F * bf * (1.0F - ph * period / 3.0F), bone, 0.7F * a);
                }
            }
            d.glow(g.add(0, 1.0D, 0), (float) radius * 2.2F, bone, 0.07F * a);

            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 40; i++) {
                float ph = fract(t * 0.05F + Rnd.f(seed, 400 + i));
                double ang = Rnd.f(seed, 420 + i) * Math.PI * 2.0D;
                double rr = Math.sqrt(Rnd.f(seed, 440 + i)) * radius;
                Vec3 p = g.add(Math.cos(ang) * rr, 0.2D + ph * 2.5D, Math.sin(ang) * rr);
                d.glow(p, 0.45F * (1.0F - ph), t * 0.2F + i, 0xFFFFFF, (1.0F - ph) * 0.7F * a);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.1F * Rnd.ramp(t, 0.0F, 6.0F) * (1.0F - Rnd.ramp(t, duration - 12.0F, duration)) * falloff(cam, origin, 28.0F);
        }
    }

    // =====================================================================================================
    // Creeper : explosion
    // =====================================================================================================

    public static final class Blast extends Fx {
        private static final int CHARGE = 28;

        public Blast(VfxPacket p) {
            super(p);
        }

        private Vec3 spark(Vec3 c, int i, float ts) {
            Vec3 dir = sphere(seed, i);
            dir = new Vec3(dir.x, Math.abs(dir.y) * 1.0D + 0.2D, dir.z).normalize();
            double speed = 6.0D + Rnd.f(seed, i + 500) * 16.0D;
            return c.add(dir.scale(speed * ts)).add(0.0D, -8.0D * ts * ts, 0.0D);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 g = origin;
            Vec3 c = g.add(0.0D, 1.2D, 0.0D);
            float ch = Rnd.clamp01(t / CHARGE);
            float boom = t - CHARGE;
            int pale = FxUtil.mix(color, 0xFFFFFF, 0.6F);

            if (boom < 0.0F) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(t * (0.5F + 0.03F * t));
                d.type(VfxRenderTypes.LINE_ADD);
                float ra = Rnd.ramp(t, 0.0F, 5.0F);
                d.ring(g.add(0, 0.1D, 0), AXIS_X, AXIS_Z, 7.0D - 0.6D * ch, 0.35D, color, (0.45F + 0.5F * pulse) * ra, 80);
                d.ring(g.add(0, 0.1D, 0), AXIS_X, AXIS_Z, 3.6D * (1.0F - 0.4F * ch), 0.22D, 0xFFFFFF, 0.5F * pulse * ra, 56);
                for (int i = 0; i < 24; i++) {
                    float ph = fract(t * 0.07F + Rnd.f(seed, i));
                    Vec3 dir = sphere(seed, i);
                    double r = 6.0D * (1.0F - ph);
                    d.ribbon(c.add(dir.scale(r + 1.2D)), c.add(dir.scale(r)), 0.0F, 0.14F, color, 0.0F, 0.9F * ch);
                }
                d.type(VfxRenderTypes.GLOW_ADD);
                float s = (1.5F + 4.0F * easeIn(ch)) * (0.85F + 0.15F * pulse);
                d.glow(c, s, color, 0.4F + 0.5F * ch);
                d.glow(c, s * 0.45F, 0xFFFFFF, 0.5F * ch + 0.2F * pulse);
                return;
            }

            float fadeAll = 1.0F - Rnd.ramp(boom, 22.0F, duration - CHARGE);

            d.type(VfxRenderTypes.LINE_ADD);
            // sphere de choc : trois anneaux orthogonaux
            double r = 1.0D + 10.0D * easeOut(boom / 12.0F);
            double w = 1.2D * (1.0F - boom / 22.0F) + 0.2D;
            float sa = (1.0F - Rnd.ramp(boom, 3.0F, 22.0F)) * 0.9F;
            d.ring(c, AXIS_X, AXIS_Z, r, w, color, sa, 72);
            d.ring(c, AXIS_X, AXIS_Y, r, w, color, sa * 0.8F, 72);
            d.ring(c, AXIS_Y, AXIS_Z, r, w, color, sa * 0.8F, 72);
            d.ring(c, d.left, d.up, r * 0.92D, w * 0.5D, 0xFFFFFF, sa, 72);
            // onde au sol
            for (int k = 0; k < 2; k++) {
                float tk = boom - k * 3.0F;
                if (tk < 0.0F) {
                    continue;
                }
                d.ring(g.add(0, 0.15D, 0), AXIS_X, AXIS_Z, 1.0D + 13.0D * easeOut(tk / 14.0F),
                        2.2D * (1.0F - tk / 24.0F) + 0.3D, k == 0 ? pale : color, (1.0F - Rnd.ramp(tk, 4.0F, 24.0F)) * 0.9F, 96);
            }
            // colonne
            float cw = 5.0F * (1.0F - Rnd.ramp(boom, 0.0F, 28.0F)) + 0.5F;
            d.pillar(g, 24.0D * easeOut(boom / 10.0F), cw, color, 0.7F * (1.0F - Rnd.ramp(boom, 8.0F, 30.0F)), 0.15F);
            d.pillar(g, 20.0D * easeOut(boom / 10.0F), cw * 0.4F, 0xFFFFFF, 0.9F * (1.0F - Rnd.ramp(boom, 6.0F, 24.0F)), 0.1F);
            // rayons d'explosion
            for (int i = 0; i < 16; i++) {
                Vec3 dir = sphere(seed, 700 + i);
                double len = (4.0D + 8.0D * Rnd.f(seed, 720 + i)) * easeOut(boom / 6.0F);
                d.ribbon(c, c.add(dir.scale(len)), 0.45F, 0.0F, i % 2 == 0 ? 0xFFFFFF : color,
                        0.9F * (1.0F - Rnd.ramp(boom, 0.0F, 10.0F)), 0.0F);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(boom, 0.0F, 18.0F);
            d.glow(c, 18.0F * f + 2.0F, color, 0.9F * (1.0F - Rnd.ramp(boom, 2.0F, 26.0F)));
            d.glow(c, 9.0F * (1.0F - Rnd.ramp(boom, 0.0F, 10.0F)), 0xFFFFFF, 1.0F - Rnd.ramp(boom, 0.0F, 10.0F));

            // fumee sombre qui monte
            d.type(VfxRenderTypes.GLOW_DARK);
            for (int i = 0; i < 16; i++) {
                float ts = boom - Rnd.f(seed, 800 + i) * 4.0F;
                if (ts < 0.0F || ts > 40.0F) {
                    continue;
                }
                double ang = Rnd.f(seed, 820 + i) * Math.PI * 2.0D;
                double rr = (1.0D + 5.0D * Rnd.f(seed, 840 + i)) * easeOut(ts / 16.0F);
                Vec3 p = c.add(Math.cos(ang) * rr, 0.3D + ts * 0.18D * (0.5D + Rnd.f(seed, 860 + i)), Math.sin(ang) * rr);
                d.glow(p, 3.5F + 5.0F * (ts / 40.0F), 0x1E2A1C, 0.5F * (1.0F - ts / 40.0F) * fadeAll);
            }

            d.type(VfxRenderTypes.STAR_ADD);
            float ts = boom / 22.0F;
            for (int i = 0; i < 90; i++) {
                float life = 0.4F + 0.6F * Rnd.f(seed, 600 + i);
                if (ts > life) {
                    continue;
                }
                d.glow(spark(c, i, ts), 0.8F * (1.0F - ts / life), t * 0.25F + i, i % 3 == 0 ? 0xFFFFFF : color,
                        1.0F - ts / life);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            float boom = t - CHARGE;
            if (boom < 0.0F) {
                return 0.12F * Rnd.clamp01(t / CHARGE) * falloff(cam, origin, 40.0F);
            }
            return 1.3F * (1.0F - Rnd.ramp(boom, 0.0F, 28.0F)) * falloff(cam, origin, 70.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            float boom = t - CHARGE;
            return boom < 0.0F ? 0.0F : 0.95F * (1.0F - Rnd.ramp(boom, 0.0F, 8.0F)) * falloff(cam, origin, 55.0F);
        }

        @Override
        public int flashColor() {
            return 0xE8FFE0;
        }
    }

    // =====================================================================================================
    // Zombie : souffle de peste
    // =====================================================================================================

    public static final class Rot extends Fx {
        public Rot(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 o = origin;
            Vec3 axis = target.subtract(origin);
            double len = axis.length();
            if (len < 0.5D) {
                return;
            }
            Vec3 dir = axis.scale(1.0D / len);
            Vec3[] bs = FxUtil.basis(dir);
            float a = Rnd.ramp(t, 0.0F, 6.0F) * (1.0F - Rnd.ramp(t, duration - 14.0F, duration));
            // le cone est dessine pour 13 blocs ; plus long, il s'elargit et se garnit en proportion
            double k = Math.max(1.0D, len / 13.0D);
            float kf = (float) k;
            int puffs = (int) Math.min(220.0D, 80.0D * k * k * 0.75D);
            Vec3[] pos = new Vec3[puffs];
            float[] size = new float[puffs];
            float[] al = new float[puffs];
            for (int i = 0; i < puffs; i++) {
                float period = 26.0F + 14.0F * Rnd.f(seed, i);
                float tt = t + Rnd.f(seed, i + 200) * period;
                int cyc = (int) (tt / period);
                float ph = (tt - cyc * period) / period;
                int sd = seed + cyc * 313 + i * 7;
                double radius = (0.25D + 5.0D * ph) * k;
                double ang = Rnd.f(sd, 1) * Math.PI * 2.0D;
                double rr = Math.sqrt(Rnd.f(sd, 2)) * radius;
                pos[i] = o.add(dir.scale(len * ph)).add(bs[0].scale(Math.cos(ang) * rr)).add(bs[1].scale(Math.sin(ang) * rr))
                        .add(0.0D, 0.5D * ph, 0.0D);
                size[i] = (1.2F + 4.0F * ph) * kf;
                al[i] = (float) Math.sin(Math.PI * ph) * a;
            }
            d.type(VfxRenderTypes.GLOW_DARK);
            for (int i = 0; i < puffs; i++) {
                d.glow(pos[i], size[i], 0x2F4A22, 0.5F * al[i]);
            }
            d.type(VfxRenderTypes.GLOW_ADD);
            for (int i = 0; i < puffs; i++) {
                d.glow(pos[i], size[i] * 0.8F, i % 3 == 0 ? 0xC8E060 : color, 0.32F * al[i]);
            }
            float f = 1.0F - Rnd.ramp(t, 0.0F, 10.0F);
            d.glow(o.add(dir.scale(1.4D)), (3.2F * f + 0.5F) * kf, color, 0.7F * f);
            // gouttes de peste et spores
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 40; i++) {
                float ph = fract(t * 0.03F + Rnd.f(seed, 400 + i));
                double ang = Rnd.f(seed, 420 + i) * Math.PI * 2.0D;
                double radius = (0.2D + 4.6D * ph) * k;
                double rr = Math.sqrt(Rnd.f(seed, 440 + i)) * radius;
                Vec3 p = o.add(dir.scale(len * ph)).add(bs[0].scale(Math.cos(ang) * rr)).add(bs[1].scale(Math.sin(ang) * rr))
                        .add(0.0D, -1.5D * ph * ph, 0.0D);
                d.glow(p, 0.35F * kf, t * 0.1F + i, i % 2 == 0 ? 0xD8F070 : color, (float) Math.sin(Math.PI * ph) * 0.9F * a);
            }
        }
    }

    // =====================================================================================================
    // Araignee : toile geante
    // =====================================================================================================

    public static final class Web extends Fx {
        public Web(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 g = origin.add(0.0D, 0.15D, 0.0D);
            double radius = Math.max(2.0F, param);
            Vec3 anchor = target;
            float grow = easeOut(Rnd.ramp(t, 4.0F, 18.0F));
            float out = 1.0F - Rnd.ramp(t, duration - 25.0F, duration);
            int silk = 0xEEF0FF;
            double vib = 1.0D + 0.025D * Math.sin(t * 1.3D) * (1.0F - Rnd.ramp(t, 20.0F, 60.0F));

            d.type(VfxRenderTypes.LINE_ADD);
            // fils lances depuis le joueur
            float shoot = Rnd.ramp(t, 0.0F, 7.0F);
            if (shoot > 0.0F && t < 18.0F) {
                for (int i = 0; i < 7; i++) {
                    double ang = Rnd.f(seed, i) * Math.PI * 2.0D;
                    Vec3 end = g.add(Math.cos(ang) * radius * 0.8D, 0.0D, Math.sin(ang) * radius * 0.8D);
                    Vec3 prev = anchor;
                    for (int j = 1; j <= 8; j++) {
                        double s = j / 8.0D * shoot;
                        Vec3 p = FxUtil.lerp(anchor, end, s).add(0.0D, Math.sin(Math.PI * s) * 2.5D, 0.0D);
                        d.ribbon(prev, p, 0.09F, 0.09F, silk, 0.85F * (1.0F - Rnd.ramp(t, 10.0F, 18.0F)),
                                0.85F * (1.0F - Rnd.ramp(t, 10.0F, 18.0F)));
                        prev = p;
                    }
                }
            }
            // brins radiaux au sol
            int strands = 12;
            for (int k = 0; k < strands; k++) {
                double ang = k * Math.PI * 2.0D / strands;
                Vec3 dv = new Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
                d.ribbon(g, g.add(dv.scale(radius * grow * vib)), 0.13F, 0.07F, silk, 0.9F * out, 0.7F * out);
            }
            // anneaux de la toile : polygones creux entre les brins
            for (int j = 1; j <= 5; j++) {
                double rj = radius * j / 5.0D * grow * vib;
                Vec3 prevP = g.add(rj, 0.0D, 0.0D);
                for (int k = 1; k <= strands; k++) {
                    double ang = k * Math.PI * 2.0D / strands;
                    double angMid = (k - 0.5D) * Math.PI * 2.0D / strands;
                    Vec3 mid = g.add(Math.cos(angMid) * rj * 0.95D, 0.0D, Math.sin(angMid) * rj * 0.95D);
                    Vec3 p = g.add(Math.cos(ang) * rj, 0.0D, Math.sin(ang) * rj);
                    d.ribbon(prevP, mid, 0.09F, 0.09F, silk, 0.8F * out, 0.8F * out);
                    d.ribbon(mid, p, 0.09F, 0.09F, silk, 0.8F * out, 0.8F * out);
                    prevP = p;
                }
            }
            // fils qui pendent et fils tendus en hauteur
            int hang = (int) (16.0D * Math.max(1.0D, radius * radius / 42.0D));
            for (int i = 0; i < hang; i++) {
                double ang = Rnd.f(seed, 100 + i) * Math.PI * 2.0D;
                double rr = Math.sqrt(Rnd.f(seed, 120 + i)) * radius * 0.9D * grow;
                Vec3 base = g.add(Math.cos(ang) * rr, 0.0D, Math.sin(ang) * rr);
                double h = (1.0D + 2.2D * Rnd.f(seed, 140 + i)) * grow;
                d.ribbon(base, base.add(0.0D, h, 0.0D), 0.07F, 0.03F, silk, 0.6F * out, 0.1F * out);
            }
            // pulsation rouge aux limites
            d.ring(g, AXIS_X, AXIS_Z, radius * grow, 0.25D, color, 0.35F * out * (0.6F + 0.4F * (float) Math.sin(t * 0.4F)), 80);

            d.type(VfxRenderTypes.GLOW_ADD);
            // huit yeux d'araignee rouges
            float eyes = Rnd.ramp(t, 14.0F, 24.0F) * out;
            if (eyes > 0.0F) {
                float blink = 0.6F + 0.4F * (float) Math.sin(t * 0.25F);
                for (int e = 0; e < 8; e++) {
                    double ex = (e % 4 - 1.5D) * 0.55D;
                    double ey = e < 4 ? 1.3D : 0.8D;
                    double ez = (e < 4 ? 0.0D : 0.25D) + (e % 2 == 0 ? 0.0D : 0.2D);
                    Vec3 p = g.add(ex, ey, ez);
                    d.glow(p, e < 4 ? 0.7F : 0.45F, color, 0.9F * eyes * blink);
                    d.glow(p, 0.3F, 0xFFFFFF, 0.8F * eyes * blink);
                }
                d.glow(g.add(0, 1.0D, 0), 5.0F, color, 0.18F * eyes);
            }
            // gouttes de rosee aux croisements
            d.type(VfxRenderTypes.STAR_ADD);
            for (int j = 1; j <= 5; j++) {
                for (int k = 0; k < strands; k += 2) {
                    double ang = k * Math.PI * 2.0D / strands;
                    double rj = radius * j / 5.0D * grow * vib;
                    Vec3 p = g.add(Math.cos(ang) * rj, 0.05D, Math.sin(ang) * rj);
                    float tw = 0.5F + 0.5F * (float) Math.sin(t * 0.2F + j * 2.0F + k);
                    d.glow(p, 0.4F + 0.3F * tw, 0.0F, 0xFFFFFF, 0.8F * tw * out * grow);
                }
            }
        }
    }

    // =====================================================================================================
    // Gardien : rayon
    // =====================================================================================================

    public static final class Laser extends Fx {
        private static final int CHARGE = 30;

        public Laser(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 axis = target.subtract(origin);
            double len = axis.length();
            if (len < 1.0D) {
                return;
            }
            Vec3 dir = axis.scale(1.0D / len);
            Vec3[] bs = FxUtil.basis(dir);
            Vec3 muzzle = origin.add(dir.scale(0.8D)).add(0.0D, -0.25D, 0.0D);
            float ch = Rnd.clamp01(t / CHARGE);
            float fire = t - CHARGE;
            float end = duration - CHARGE;
            int white = 0xFFFFFF;
            // param = largeur du rayon par rapport a l'original (1 = fin, 2,4 = le rayon geant du gardien)
            float wm = Math.max(1.0F, param);

            if (fire < 0.0F) {
                float pulse = 0.6F + 0.4F * (float) Math.sin(t * (0.6F + 0.05F * t));
                d.type(VfxRenderTypes.LINE_ADD);
                for (int k = 0; k < 3; k++) {
                    float ph = fract(t * 0.08F + k / 3.0F);
                    d.ring(muzzle, d.left, d.up, (1.8D * (1.0F - ph) + 0.1D) * wm, 0.1D * wm, color, ch * 0.9F * (0.3F + 0.7F * ph), 40);
                }
                d.ribbon(muzzle, muzzle.add(dir.scale(len)), 0.08F, 0.03F, color, 0.25F * ch * pulse, 0.05F * ch);
                for (int i = 0; i < 18; i++) {
                    float ph = fract(t * 0.06F + Rnd.f(seed, i));
                    Vec3 dv = bs[0].scale(Rnd.s(seed, 20 + i)).add(bs[1].scale(Rnd.s(seed, 40 + i))).add(dir.scale(0.8D))
                            .normalize();
                    d.ribbon(muzzle.add(dv.scale(2.4D * (1.0F - ph) + 0.3D)), muzzle.add(dv.scale(2.4D * (1.0F - ph))), 0.0F,
                            0.1F, color, 0.0F, 0.9F * ch);
                }
                d.type(VfxRenderTypes.GLOW_ADD);
                float s = (0.4F + 2.4F * easeIn(ch)) * (0.85F + 0.15F * pulse) * wm;
                d.glow(muzzle, s, color, 0.5F + 0.5F * ch);
                d.glow(muzzle, s * 0.45F, white, 0.5F * ch + 0.3F * pulse);
                return;
            }

            float shoot = easeOut(Rnd.ramp(fire, 0.0F, 3.0F));
            double len2 = len * shoot;
            float bw = 1.4F * wm * Rnd.ramp(fire, 0.0F, 2.0F) * (1.0F - 0.5F * Rnd.ramp(fire, 16.0F, end))
                    * (1.0F - Rnd.ramp(fire, end - 5.0F, end));
            Vec3 tip = muzzle.add(dir.scale(len2));

            d.type(VfxRenderTypes.LINE_ADD);
            d.ribbon(muzzle, tip, bw * 3.2F, bw * 3.0F, color, 0.35F, 0.3F);
            d.ribbon(muzzle, tip, bw * 1.6F, bw * 1.5F, color, 0.85F, 0.8F);
            d.ribbon(muzzle, tip, bw * 0.6F, bw * 0.55F, white, 1.0F, 0.95F);
            // deux helices d'energie autour du rayon
            for (int s = 0; s < 2; s++) {
                Vec3 prev = muzzle;
                int n = 28;
                for (int j = 1; j <= n; j++) {
                    double dist = len2 * j / n;
                    double ph = dist * 0.9D - t * 0.9D + s * Math.PI;
                    double r = bw * 1.1D + 0.15D;
                    Vec3 p = muzzle.add(dir.scale(dist)).add(bs[0].scale(Math.cos(ph) * r)).add(bs[1].scale(Math.sin(ph) * r));
                    d.ribbon(prev, p, 0.18F * wm, 0.18F * wm, s == 0 ? white : color, 0.8F * Rnd.clamp01(bw), 0.8F * Rnd.clamp01(bw));
                    prev = p;
                }
            }
            // anneaux d'onde qui filent le long du rayon
            for (int k = 0; k < 6; k++) {
                double dist = fract(t * 0.07F + k / 6.0F) * len2;
                d.ring(muzzle.add(dir.scale(dist)), bs[0], bs[1], 0.7D * bw + 0.3D, 0.16D, color, 0.7F * Rnd.clamp01(bw), 28);
            }
            // le front de creusement (le serveur perce par pas de 5 blocs toutes les 3 ticks)
            double front = Math.min(len, 5.0D * (Math.floor(fire / 3.0F) + 1.0D));
            Vec3 fp = muzzle.add(dir.scale(front));
            float fa = Rnd.clamp01(bw);
            d.ring(fp, bs[0], bs[1], 1.7D * wm, 0.3D * wm, white, 0.9F * fa, 32);

            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(muzzle, 3.4F * bw + 0.6F, color, 0.8F * fa);
            d.glow(muzzle, 1.6F * bw, white, fa);
            d.glow(fp, 4.2F * wm, color, 0.8F * fa);
            d.glow(fp, 1.8F * wm, white, fa);
            d.glow(tip, 3.0F * fa * wm, color, 0.6F * fa);

            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 30; i++) {
                float ph = fract(t * 0.2F + Rnd.f(seed, 100 + i));
                Vec3 sd = sphere(seed, 120 + i);
                Vec3 p = fp.add(sd.scale((0.6D + 3.6D * ph) * wm));
                d.glow(p, 0.5F * wm * (1.0F - ph), t * 0.3F + i, i % 2 == 0 ? white : color, (1.0F - ph) * fa);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            float fire = t - CHARGE;
            if (fire < 0.0F) {
                return 0.08F * Rnd.clamp01(t / CHARGE) * falloff(cam, origin, 30.0F);
            }
            return 0.35F * (1.0F - Rnd.ramp(fire, 0.0F, duration - CHARGE)) * falloff(cam, origin, 45.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            float fire = t - CHARGE;
            return fire < 0.0F ? 0.0F : 0.3F * (1.0F - Rnd.ramp(fire, 0.0F, 5.0F)) * falloff(cam, origin, 10.0F);
        }

        @Override
        public int flashColor() {
            return 0xD8FFFA;
        }
    }

    // =====================================================================================================
    // Villageois : cri
    // =====================================================================================================

    public static final class Sonic extends Fx {
        public Sonic(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 c = followed() != null ? pos(partial).add(0.0D, 1.0D, 0.0D) : origin;
            int pale = FxUtil.mix(color, 0xFFFFFF, 0.55F);

            d.type(VfxRenderTypes.LINE_ADD);
            for (int k = 0; k < 7; k++) {
                float tau = t - 3.0F * k;
                if (tau < 0.0F) {
                    continue;
                }
                double r = Math.min(36.0D, 0.8D + 2.1D * tau);
                float al = (1.0F - Rnd.ramp(tau, 0.0F, 22.0F)) * 0.9F;
                double w = 0.8D + 0.1D * tau;
                d.ring(c, AXIS_X, AXIS_Z, r, w, color, al, 72);
                d.ring(c, d.left, d.up, r, w * 0.8D, pale, al * 0.85F, 72);
                d.ring(c, AXIS_X, AXIS_Y, r, w * 0.6D, color, al * 0.5F, 72);
            }
            // lignes de son qui rayonnent
            for (int i = 0; i < 18; i++) {
                float ph = fract(t * 0.07F + Rnd.f(seed, i));
                Vec3 dir = sphere(seed, i);
                double r0 = 0.5D + 34.0D * ph;
                d.ribbon(c.add(dir.scale(r0)), c.add(dir.scale(r0 + 2.6D)), 0.32F, 0.0F, color,
                        (1.0F - ph) * 0.6F * (1.0F - Rnd.ramp(t, 24.0F, duration)), 0.0F);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(t, 0.0F, 12.0F);
            d.glow(c, 11.0F * f + 0.5F, color, 0.8F * f);
            d.glow(c, 4.5F * f, 0xFFFFFF, f);

            // eclats d'emeraude
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 42; i++) {
                int k = i % 7;
                float tau = t - 3.0F * k;
                if (tau < 0.0F || tau > 24.0F) {
                    continue;
                }
                Vec3 sd = sphere(seed, 100 + i);
                double r = Math.min(36.0D, 0.8D + 2.1D * tau);
                d.glow(c.add(sd.scale(r)), 1.1F * (1.0F - tau / 24.0F), t * 0.2F + i, i % 3 == 0 ? 0xFFFFFF : color,
                        1.0F - tau / 24.0F);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.7F * (1.0F - Rnd.ramp(t, 0.0F, 30.0F)) * falloff(cam, origin, 50.0F);
        }
    }

    // =====================================================================================================
    // Golem de fer : seisme
    // =====================================================================================================

    public static final class Quake extends Fx {
        private static final int HIT = 8;
        private static final int CRACKS = 12;
        private static final int MAGMA = 0xFF7A20;

        public Quake(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 g = origin.add(0.0D, 0.12D, 0.0D);
            double radius = Math.max(4.0F, param);
            float imp = t - HIT;

            if (imp < 0.0F) {
                float w = t / HIT;
                d.type(VfxRenderTypes.LINE_ADD);
                d.ring(g, AXIS_X, AXIS_Z, 3.0D * (1.0F - w) + 0.5D, 0.3D, 0xFFFFFF, 0.8F * w + 0.1F, 56);
                for (int i = 0; i < 16; i++) {
                    double ang = Rnd.f(seed, i) * Math.PI * 2.0D;
                    double r = 4.0D * (1.0F - fract(t * 0.14F + Rnd.f(seed, 30 + i)));
                    d.ribbon(g.add(Math.cos(ang) * (r + 0.8D), 0.2D, Math.sin(ang) * (r + 0.8D)),
                            g.add(Math.cos(ang) * r, 0.2D, Math.sin(ang) * r), 0.0F, 0.12F, MAGMA, 0.0F, 0.8F);
                }
                d.type(VfxRenderTypes.GLOW_ADD);
                d.glow(g.add(0, 0.3D, 0), 1.5F + 3.0F * easeIn(w), MAGMA, 0.8F * w);
                return;
            }

            float fadeAll = 1.0F - Rnd.ramp(imp, duration - HIT - 18.0F, duration - HIT);
            float grow = easeOut(imp / 10.0F);
            float cool = 1.0F - Rnd.ramp(imp, 24.0F, 52.0F);

            // 1. tranchees sombres
            d.type(VfxRenderTypes.LINE_DARK);
            Vec3[][] cracks = new Vec3[CRACKS][];
            for (int k = 0; k < CRACKS; k++) {
                double ang = k * Math.PI * 2.0D / CRACKS + Rnd.s(seed, k) * 0.12D;
                Vec3 dv = new Vec3(Math.cos(ang), 0.0D, Math.sin(ang));
                Vec3 perp = new Vec3(-Math.sin(ang), 0.0D, Math.cos(ang));
                Vec3[] pts = new Vec3[11];
                for (int j = 0; j <= 10; j++) {
                    double r = 1.2D + (radius - 1.2D) * grow * j / 10.0D;
                    double wob = Rnd.s(seed + k, j) * 0.6D;
                    pts[j] = g.add(dv.scale(r)).add(perp.scale(wob));
                }
                cracks[k] = pts;
                for (int j = 0; j < 10; j++) {
                    d.ribbon(pts[j], pts[j + 1], 1.7F * (1.0F - j / 12.0F), 1.7F * (1.0F - (j + 1) / 12.0F), 0x000000,
                            0.9F * fadeAll, 0.9F * fadeAll);
                }
            }
            // 2. magma dans les fissures + sous-fissures
            d.type(VfxRenderTypes.LINE_ADD);
            for (int k = 0; k < CRACKS; k++) {
                float flick = 0.75F + 0.25F * (float) Math.sin(t * 0.5F + k * 1.7F);
                Vec3[] pts = cracks[k];
                for (int j = 0; j < 10; j++) {
                    d.ribbon(pts[j], pts[j + 1], 0.8F * (1.0F - j / 12.0F), 0.8F * (1.0F - (j + 1) / 12.0F), MAGMA,
                            0.9F * flick * cool * fadeAll, 0.9F * flick * cool * fadeAll);
                    d.ribbon(pts[j], pts[j + 1], 0.18F, 0.18F, 0xFFE0A0, 0.9F * cool * fadeAll, 0.9F * cool * fadeAll);
                }
                for (int b = 0; b < 2; b++) {
                    int from = b == 0 ? 4 : 7;
                    double rot = (b == 0 ? 0.7D : -0.7D) + Rnd.s(seed, 50 + k) * 0.2D;
                    Vec3 m = pts[10].subtract(pts[0]).normalize();
                    Vec3 bd = new Vec3(m.x * Math.cos(rot) - m.z * Math.sin(rot), 0.0D, m.x * Math.sin(rot) + m.z * Math.cos(rot));
                    double bl = (2.5D + 1.5D * Rnd.f(seed, 70 + k * 2 + b)) * grow * (radius / 10.0D);
                    Vec3 prev = pts[from];
                    for (int j = 1; j <= 4; j++) {
                        Vec3 p = pts[from].add(bd.scale(bl * j / 4.0D)).add(0.0D, 0.0D, 0.0D);
                        d.ribbon(prev, p, 0.3F * (1.0F - j / 5.0F), 0.3F * (1.0F - (j + 1) / 5.0F), MAGMA,
                                0.8F * flick * cool * fadeAll, 0.7F * cool * fadeAll);
                        prev = p;
                    }
                }
            }
            // 3. ondes de choc
            for (int k = 0; k < 3; k++) {
                float tk = imp - k * 3.0F;
                if (tk < 0.0F) {
                    continue;
                }
                d.ring(g, AXIS_X, AXIS_Z, 1.0D + (radius + 1.0D) * easeOut(tk / 16.0F), 1.8D * (1.0F - tk / 26.0F) + 0.25D,
                        k == 0 ? 0xFFFFFF : 0xC8C8D8, (1.0F - Rnd.ramp(tk, 4.0F, 26.0F)) * 0.9F, 80);
            }

            // 4. flash central et gerbes de lumiere
            d.type(VfxRenderTypes.GLOW_ADD);
            float f = 1.0F - Rnd.ramp(imp, 0.0F, 12.0F);
            d.glow(g.add(0, 0.6D, 0), 24.0F * f + 1.0F, MAGMA, 0.8F * f + 0.1F * cool * fadeAll);
            d.glow(g.add(0, 0.6D, 0), 10.0F * f, 0xFFFFFF, f);

            // 5. poussiere qui monte
            d.type(VfxRenderTypes.GLOW_DARK);
            for (int i = 0; i < 44; i++) {
                float ts = imp - Rnd.f(seed, 100 + i) * 4.0F;
                if (ts < 0.0F || ts > 38.0F) {
                    continue;
                }
                double ang = Rnd.f(seed, 120 + i) * Math.PI * 2.0D;
                double rr = (1.0D + (radius - 1.0D) * Rnd.f(seed, 140 + i)) * easeOut(ts / 14.0F);
                Vec3 p = g.add(Math.cos(ang) * rr, 0.3D + ts * 0.1D * (0.5D + Rnd.f(seed, 160 + i)), Math.sin(ang) * rr);
                d.glow(p, 4.5F + 6.0F * (ts / 38.0F), 0x8A8A92, 0.42F * (1.0F - ts / 38.0F));
            }
            // 6. blocs de roche projetes
            d.type(VfxRenderTypes.DISC_DARK);
            float ts = imp / 20.0F;
            for (int i = 0; i < 110; i++) {
                float life = 0.7F + 0.8F * Rnd.f(seed, 200 + i);
                if (ts > life) {
                    continue;
                }
                d.glow(chunk(g, i, ts, radius), 0.5F + 0.9F * Rnd.f(seed, 260 + i), t * 0.3F + i, 0x55555E, 0.95F);
            }
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 60; i++) {
                float life = 0.5F + 0.7F * Rnd.f(seed, 300 + i);
                if (ts > life) {
                    continue;
                }
                d.glow(chunk(g, 500 + i, ts, radius), 0.5F * (1.0F - ts / life), t * 0.25F + i, i % 2 == 0 ? 0xFFFFFF : MAGMA,
                        1.0F - ts / life);
            }
        }

        private Vec3 chunk(Vec3 g, int i, float ts, double radius) {
            double ang = Rnd.f(seed, i + 700) * Math.PI * 2.0D;
            double rr = Rnd.f(seed, i + 800) * radius * 0.9D;
            Vec3 start = g.add(Math.cos(ang) * rr, 0.0D, Math.sin(ang) * rr);
            double vy = 6.0D + 12.0D * Rnd.f(seed, i + 900);
            return start.add(Math.cos(ang) * 2.0D * ts, vy * ts - 14.0D * ts * ts, Math.sin(ang) * 2.0D * ts);
        }

        @Override
        public float shake(Vec3 cam, float t) {
            float imp = t - HIT;
            return imp < 0.0F ? 0.0F : 1.6F * (1.0F - Rnd.ramp(imp, 0.0F, 30.0F)) * falloff(cam, origin, 80.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            float imp = t - HIT;
            return imp < 0.0F ? 0.0F : 0.35F * (1.0F - Rnd.ramp(imp, 0.0F, 5.0F)) * falloff(cam, origin, 25.0F);
        }

        @Override
        public int flashColor() {
            return 0xFFE0C0;
        }
    }

    // =====================================================================================================
    // Enderman : passage de l'ombre
    // =====================================================================================================

    public static final class Warp extends Fx {
        public Warp(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 a = origin;
            Vec3 b = target;
            Vec3 axis = b.subtract(a);
            double len = axis.length();
            if (len < 0.5D) {
                return;
            }
            Vec3 dir = axis.scale(1.0D / len);
            Vec3[] bs = FxUtil.basis(dir);
            // param = largeur du passage par rapport a l'original (le serveur creuse un tunnel)
            float wm = Math.max(1.0F, param);
            float open = easeOut(Rnd.ramp(t, 0.0F, 5.0F));
            float close = 1.0F - Rnd.ramp(t, 8.0F, duration - 4.0F);
            float al = open * close;
            int pale = FxUtil.mix(color, 0xFFFFFF, 0.6F);

            // fumee sombre le long du chemin
            d.type(VfxRenderTypes.GLOW_DARK);
            for (int i = 0; i < 24; i++) {
                double s = (i + 0.5D) / 24.0D;
                Vec3 p = a.add(dir.scale(len * s)).add(bs[0].scale(Rnd.s(seed, i) * 0.8D * wm)).add(bs[1].scale(Rnd.s(seed, 20 + i) * 0.8D * wm));
                d.glow(p, (3.0F + 2.0F * Rnd.f(seed, 40 + i)) * wm, 0x1A0A2A, 0.4F * al);
            }

            d.type(VfxRenderTypes.LINE_ADD);
            d.lightning(a, b, seed, 6, 0.03F, (0.22F * close + 0.05F) * wm, color, al, 3);
            d.lightning(a, b, seed + 1, 5, 0.04F, (0.1F * close + 0.03F) * wm, 0xFFFFFF, 0.8F * al, 0);
            // tourbillon au depart et onde a l'arrivee
            for (int k = 0; k < 3; k++) {
                float ph = fract(t * 0.1F + k / 3.0F);
                float ra = (1.0F - Rnd.ramp(t, 4.0F, 16.0F));
                d.ring(a, d.left, d.up, (3.5D * (1.0F - ph) + 0.1D) * wm, 0.18D * wm, color, 0.9F * ra * ph, 40);
            }
            float ea = 1.0F - Rnd.ramp(t, 2.0F, 18.0F);
            d.ring(b, bs[0], bs[1], (0.5D + 4.5D * easeOut(Rnd.ramp(t, 2.0F, 16.0F))) * wm, 0.4D * ea + 0.05D, pale, 0.9F * ea, 48);
            d.ring(b, AXIS_X, AXIS_Z, (0.5D + 5.5D * easeOut(Rnd.ramp(t, 2.0F, 16.0F))) * wm, 0.3D * ea + 0.05D, color, 0.7F * ea, 48);

            d.type(VfxRenderTypes.GLOW_ADD);
            float fa = 1.0F - Rnd.ramp(t, 0.0F, 12.0F);
            d.glow(a, (5.0F * fa + 0.5F) * wm, color, 0.8F * fa);
            d.glow(a, 2.0F * fa * wm, 0xFFFFFF, fa);
            float fb = Rnd.ramp(t, 1.0F, 3.0F) * (1.0F - Rnd.ramp(t, 3.0F, 18.0F));
            d.glow(b, 7.0F * fb * wm, color, 0.8F * fb);
            d.glow(b, 3.0F * fb * wm, 0xFFFFFF, fb);
            for (int i = 0; i < 24; i++) {
                double s = (i + 0.5D) / 24.0D;
                d.glow(a.add(dir.scale(len * s)), 1.4F * al * wm, color, 0.4F * al);
            }

            // particules de l'End
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 60; i++) {
                double s = Rnd.f(seed, 100 + i);
                float life = 0.5F + 0.5F * Rnd.f(seed, 160 + i);
                float ts = t / 40.0F;
                if (ts > life) {
                    continue;
                }
                Vec3 p = a.add(dir.scale(len * s)).add(bs[0].scale(Rnd.s(seed, 220 + i) * 1.3D * wm))
                        .add(bs[1].scale(Rnd.s(seed, 280 + i) * 1.3D * wm)).add(0.0D, 0.6D * ts, 0.0D);
                float tw = 0.5F + 0.5F * (float) Math.sin(t * 0.4F + i);
                d.glow(p, 0.55F * tw * (1.0F - ts / life) + 0.1F, t * 0.1F + i, i % 3 == 0 ? 0xFFFFFF : color, 0.9F * (1.0F - ts / life));
            }
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.4F * (1.0F - Rnd.ramp(t, 0.0F, 4.0F)) * falloff(cam, origin, 5.0F);
        }

        @Override
        public int flashColor() {
            return 0xE8C8FF;
        }
    }

    private FxMobs() {}
}
