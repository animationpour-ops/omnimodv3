package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import com.omnimine.util.BoltShape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effets du minage : fissures vanilla qui progressent, absorption, trou dans le ciel, dissolution d'une structure,
 * eclair gele (rendu vanilla) et sa destruction, ceremonie des structures evoluees.
 */
public final class FxMining {

    // =====================================================================================================
    // Fissures de minage : les fissures VANILLA (destroy_stage_0..9), celles qui apparaissent sur un bloc qu'on mine
    // =====================================================================================================

    public static final class MineCrack extends Fx {
        /** Numero de la sorte de cible (MineTargets.Kind.ordinal()) : 0 mob, 1 soleil, 2 lune, 3 eclair. */
        private static final int KIND_MOB = 0;
        private static final int KIND_BOLT = 3;

        private Vec3 center;
        private double width;
        private double height;
        private int kind;
        private float progress;
        /** Graine de forme de l'eclair mine (la meme que celle de son dessin vanilla). */
        private int boltSeed;

        public MineCrack(VfxPacket p) {
            super(p);
            read(p);
        }

        private void read(VfxPacket p) {
            center = new Vec3(p.x, p.y, p.z);
            kind = (int) Math.round(p.z2);
            if (kind == KIND_BOLT) {
                // un eclair : le paquet donne son pied (x, y, z) et la graine de sa forme (x2) ; aucune taille
                boltSeed = (int) Math.round(p.x2);
                width = 1.0D;
                height = 1.0D;
            } else {
                width = Math.max(0.4D, p.x2);
                height = Math.max(0.4D, p.y2);
            }
            progress = Rnd.clamp01(p.param);
        }

        /** Nouveau paquet pour la meme cible : on met a jour la progression sur place. */
        public void refresh(VfxPacket p) {
            read(p);
            duration = Math.max(duration, age + Math.max(2, p.duration));
        }

        private boolean sky() {
            return kind == 1 || kind == 2;
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            int stage = Math.min(9, (int) (progress * 10.0F));
            if (sky()) {
                // le soleil / la lune : l'image de fissures recouvre le disque
                d.type(VfxRenderTypes.crack(stage));
                Vec3 dir = kind == 1 ? FxUtil.sunDir(partial) : FxUtil.moonDir(partial);
                double dist = FxUtil.skyDistance();
                d.crackFacing(d.cam.add(dir.scale(dist)), dist * width * 0.5D / 90.0D);
            } else if (kind == KIND_MOB) {
                renderMob(d, stage, partial);
            } else if (kind == KIND_BOLT) {
                // un eclair : les fissures forment un manchon qui suit tout son tronc en zigzag (meme forme que le dessin)
                d.type(VfxRenderTypes.crack(stage));
                Vec3[] trunk = BoltShape.trunk(boltSeed, center);
                for (int i = 0; i < trunk.length - 1; i++) {
                    d.crackTube(trunk[i], trunk[i + 1], 0.42D + 0.06D * i, 0.42D + 0.06D * (i + 1), 1.2F);
                }
            } else {
                // autre : une boite de fissures autour du point vise
                d.type(VfxRenderTypes.crack(stage));
                double half = width * 0.5D;
                d.crackBox(new AABB(center.x - half, center.y - half, center.z - half, center.x + half,
                        center.y + half, center.z + half), (float) width);
            }
        }

        /** Un mob : les fissures sont posees sur son modele ; a defaut (echec du rendu), sur sa boite. */
        private void renderMob(VfxDraw d, int stage, float partial) {
            Entity e = followed();
            if (e != null && EntityCracks.draw(d, e, stage, partial)) {
                return;
            }
            d.type(VfxRenderTypes.crack(stage));
            AABB box;
            if (e != null) {
                // la boite du mob, a sa position interpolee (sinon elle saccade et decolle du mob qui bouge)
                box = e.getBoundingBox().move(e.getPosition(partial).subtract(e.position()));
            } else {
                box = new AABB(center.x - width * 0.5D, center.y - height * 0.5D, center.z - width * 0.5D,
                        center.x + width * 0.5D, center.y + height * 0.5D, center.z + width * 0.5D);
            }
            d.crackBox(box.inflate(0.04D), 1.0F);
        }
    }

    // =====================================================================================================
    // Absorption : la chose minee est aspiree vers le joueur
    // =====================================================================================================

    public static final class Absorb extends Fx {
        public Absorb(VfxPacket p) {
            super(p);
        }

        /** Point de la trajectoire en spirale entre a et b (s de 0 a 1). */
        private Vec3 head(Vec3 a, Vec3 b, float s, double swirl, int salt) {
            Vec3 axis = b.subtract(a);
            Vec3[] bs = FxUtil.basis(axis.lengthSqr() < 1.0E-6D ? new Vec3(0, 1, 0) : axis);
            double ph = s * 9.0D + Rnd.f(seed, salt) * 6.28D;
            double amp = swirl * Math.sin(Math.PI * Math.min(1.0F, s)) * (1.0D - s);
            return a.add(axis.scale(s)).add(bs[0].scale(Math.cos(ph) * amp)).add(bs[1].scale(Math.sin(ph) * amp));
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 end = pos(partial).add(0.0D, 1.0D, 0.0D);
            Vec3 start = origin;
            float flight = duration * 0.72F;
            float x = Rnd.clamp01(t / flight);
            float s = x * x;
            float size = Math.min(param, 36.0F);
            float hs = 0.45F + size * 0.9F * (1.0F - s) * (1.0F - s);
            double swirl = 1.2D + Math.min(size, 20.0F) * 0.12D;

            if (t < flight) {
                Vec3 h = head(start, end, s, swirl, 1);
                // queue lumineuse
                d.type(VfxRenderTypes.LINE_ADD);
                Vec3 prevPt = h;
                for (int j = 1; j <= 26; j++) {
                    float sj = s - j * 0.014F * (0.6F + x);
                    if (sj < 0.0F) {
                        break;
                    }
                    Vec3 pj = head(start, end, sj, swirl, 1);
                    float f = 1.0F - j / 27.0F;
                    d.ribbon(prevPt, pj, hs * 0.55F * f, hs * 0.55F * (f - 0.04F), color, 0.55F * f, 0.5F * f);
                    prevPt = pj;
                }
                d.type(VfxRenderTypes.GLOW_ADD);
                d.glow(h, hs * 1.4F, color, 0.5F);
                d.glow(h, hs * 0.6F, 0xFFFFFF, 0.9F);
                for (int i = 0; i < 18; i++) {
                    double ang = t * 0.35D + i * 0.35D;
                    double rr = hs * (0.5D + 0.4D * Rnd.f(seed, 20 + i));
                    Vec3[] bs = FxUtil.basis(end.subtract(start));
                    Vec3 m = h.add(bs[0].scale(Math.cos(ang + i) * rr)).add(bs[1].scale(Math.sin(ang + i) * rr));
                    d.glow(m, 0.35F + hs * 0.05F, color, 0.7F);
                }
                d.type(VfxRenderTypes.STAR_ADD);
                d.glow(h, hs * 0.9F, t * 0.3F, 0xFFFFFF, 0.9F);
            }

            // eclat quand la chose entre dans le joueur
            float u = Rnd.clamp01((t - flight) / Math.max(1.0F, duration - flight));
            if (t >= flight && u < 1.0F) {
                float a = 1.0F - u;
                d.type(VfxRenderTypes.LINE_ADD);
                d.ring(end.add(0, -0.9D, 0), AXIS_X, AXIS_Z, 0.4D + 3.2D * easeOut(u), 0.5D * a + 0.1D, color, a * 0.9F, 40);
                d.ring(end, d.left, d.up, 0.3D + 1.8D * easeOut(u), 0.25D * a + 0.05D, 0xFFFFFF, a, 40);
                for (int i = 0; i < 16; i++) {
                    Vec3 dir = sphere(seed, 50 + i);
                    Vec3 p0 = end.add(dir.scale(0.4D + 1.2D * easeOut(u)));
                    Vec3 p1 = end.add(dir.scale(0.9D + 3.4D * easeOut(u)));
                    d.ribbon(p0, p1, 0.0F, 0.11F, color, 0.0F, a);
                }
                d.type(VfxRenderTypes.GLOW_ADD);
                d.glow(end, 3.5F * a, color, 0.8F * a);
                d.glow(end, 1.5F * a, 0xFFFFFF, a);
            }
        }

        @Override
        public float flash(Vec3 cam, float t) {
            float flight = duration * 0.72F;
            return 0.3F * (1.0F - Rnd.ramp(t, flight, flight + 6.0F)) * Rnd.ramp(t, flight - 2.0F, flight)
                    * falloff(cam, pos(0.0F), 5.0F);
        }

        @Override
        public int flashColor() {
            return FxUtil.mix(color, 0xFFFFFF, 0.5F);
        }
    }

    // =====================================================================================================
    // Trou dans le ciel : le soleil ou la lune ont ete mines
    // =====================================================================================================

    private static final double SUN_HOLE_ANGLE = Math.toRadians(13.0D);
    private static final double MOON_HOLE_ANGLE = Math.toRadians(10.5D);

    public static final class SkyHole extends Fx {
        private final int which;
        private Vec3 fixed;

        public SkyHole(VfxPacket p) {
            super(p);
            this.which = p.param >= 0.5F ? 1 : 0;
            this.fixed = fixedOf(p);
        }

        private static Vec3 fixedOf(VfxPacket p) {
            Vec3 v = new Vec3(p.x2, p.y2, p.z2);
            return v.lengthSqr() > 0.25D ? v.normalize() : null;
        }

        public int which() {
            return which;
        }

        /** Un nouveau paquet pour le meme trou : on le prolonge, l'animation ne repart pas. */
        public void refresh(VfxPacket p) {
            fixed = fixedOf(p);
            duration = Math.max(duration, age + Math.max(2, p.duration));
        }

        private Vec3 dir(float partial) {
            if (fixed != null) {
                return fixed;
            }
            return which == 0 ? FxUtil.sunDir(partial) : FxUtil.moonDir(partial);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 dir = dir(partial);
            if (dir.y < -0.55D) {
                return;
            }
            double dist = FxUtil.skyDistance();
            double radius = dist * Math.tan(which == 0 ? SUN_HOLE_ANGLE : MOON_HOLE_ANGLE);
            float open = easeOut(Rnd.ramp(t, 0.0F, 26.0F));
            float fade = 1.0F - Rnd.ramp(t, duration - 8.0F, duration);
            Vec3 c = d.cam.add(dir.scale(dist));
            Vec3[] b = FxUtil.basis(dir);
            int rim = which == 0 ? 0xFF8F1F : 0xBBD6FF;
            int core = which == 0 ? 0xFFE2A8 : 0xFFFFFF;
            float pulse = 0.78F + 0.22F * (float) Math.sin(t * 0.13F);
            double r0 = radius * open;
            int sd = 7000 + which * 131;
            if (r0 < 1.0D) {
                return;
            }

            // le vide
            d.type(VfxRenderTypes.GLOW_DARK);
            d.glow(c, (float) (r0 * 3.4D), 0x000000, 0.5F * fade);
            d.type(VfxRenderTypes.DISC_DARK);
            d.glow(c, (float) (r0 * 2.0D), 0x000000, 0.98F * fade);

            // lueur de couronne
            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(c, (float) (r0 * 2.7D), rim, 0.2F * pulse * fade);
            d.glow(c, (float) (r0 * 5.5D), rim, 0.06F * fade);

            d.type(VfxRenderTypes.LINE_ADD);
            // anneau de bord
            d.ring(c, b[0], b[1], r0, radius * 0.11D * (0.85D + 0.15D * Math.sin(t * 0.2D)), rim, 0.95F * pulse * fade, 96);
            d.ring(c, b[0], b[1], r0 * 1.004D, radius * 0.035D, core, 0.95F * fade, 96);

            // fissures qui partent du bord et zebrent le ciel
            int arms = 18;
            for (int k = 0; k < arms; k++) {
                double a = k * (Math.PI * 2.0D / arms) + Rnd.s(sd, k) * 0.12D;
                float grow = easeOut(Rnd.ramp(t, 3.0F + k * 0.5F, 22.0F + k * 0.5F));
                if (grow <= 0.0F) {
                    continue;
                }
                double len = radius * (0.7D + 2.6D * Rnd.f(sd, 40 + k)) * grow;
                Vec3 dv = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                Vec3 pv = b[0].scale(-Math.sin(a)).add(b[1].scale(Math.cos(a)));
                float flick = 0.55F + 0.45F * (float) Math.sin(t * 0.35F + k * 1.7F);
                Vec3 prev = c.add(dv.scale(r0 * 0.99D));
                double wob = 0.0D;
                for (int j = 1; j <= 8; j++) {
                    double f = j / 8.0D;
                    wob = wob * 0.7D + Rnd.s(sd + k, j) * 0.09D * radius;
                    Vec3 nxt = c.add(dv.scale(r0 * 0.99D + len * f)).add(pv.scale(wob * Math.min(1.0D, f * 2.0D)));
                    float w0 = (float) (radius * 0.05D * (1.0D - (j - 1) / 8.0D));
                    float w1 = (float) (radius * 0.05D * (1.0D - j / 8.0D));
                    float al = 0.9F * flick * fade;
                    d.ribbon(prev, nxt, w0, w1, rim, al, al * (float) (1.0D - f * 0.7D));
                    d.ribbon(prev, nxt, w0 * 0.4F, w1 * 0.4F, core, al, al * (float) (1.0D - f * 0.7D));
                    prev = nxt;
                }
            }
            // rayons de couronne (comme une eclipse)
            for (int k = 0; k < 28; k++) {
                double a = Rnd.f(sd, 80 + k) * Math.PI * 2.0D;
                Vec3 dv = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                double l0 = r0 * 1.05D;
                double l1 = r0 * (1.15D + 0.9D * Rnd.f(sd, 120 + k));
                float al = 0.3F * pulse * fade * (0.6F + 0.4F * (float) Math.sin(t * 0.2F + k));
                d.ribbon(c.add(dv.scale(l0)), c.add(dv.scale(l1)), (float) (radius * 0.05D), 0.0F, rim, al, 0.0F);
            }

            // braises qui s'echappent du bord
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 36; i++) {
                float ph = fract(t * 0.012F + Rnd.f(sd, 200 + i));
                double ang = Rnd.f(sd, 240 + i) * Math.PI * 2.0D + ph * 0.5D;
                double rr = r0 * (1.05D + 2.8D * ph);
                Vec3 p = c.add(b[0].scale(Math.cos(ang) * rr)).add(b[1].scale(Math.sin(ang) * rr));
                d.glow(p, (float) (radius * 0.07D * (1.0F - ph)), t * 0.1F + i, i % 3 == 0 ? 0xFFFFFF : rim,
                        (1.0F - ph) * 0.9F * fade);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.55F * (1.0F - Rnd.ramp(t, 0.0F, 36.0F));
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.4F * (1.0F - Rnd.ramp(t, 0.0F, 9.0F));
        }

        @Override
        public int flashColor() {
            return which == 0 ? 0xFFC070 : 0xDDEBFF;
        }
    }

    /** Le ciel se referme : le disque noir vole en eclats et l'astre revient dans une explosion de lumiere. */
    public static final class SkyReturn extends Fx {
        private final int which;
        private final Vec3 fixed;

        public SkyReturn(VfxPacket p) {
            super(p);
            this.which = p.param >= 0.5F ? 1 : 0;
            Vec3 v = new Vec3(p.x2, p.y2, p.z2);
            this.fixed = v.lengthSqr() > 0.25D ? v.normalize() : null;
        }

        public int which() {
            return which;
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 dir = fixed != null ? fixed : (which == 0 ? FxUtil.sunDir(partial) : FxUtil.moonDir(partial));
            double dist = FxUtil.skyDistance();
            double radius = dist * Math.tan(which == 0 ? SUN_HOLE_ANGLE : MOON_HOLE_ANGLE);
            Vec3 c = d.cam.add(dir.scale(dist));
            Vec3[] b = FxUtil.basis(dir);
            int rim = which == 0 ? 0xFFB040 : 0xBBD6FF;
            int core = which == 0 ? 0xFFF0C8 : 0xFFFFFF;
            int sd = 7300 + which * 97;
            float shrink = 1.0F - easeOut(Rnd.ramp(t, 0.0F, 16.0F));
            float boom = easeOut(Rnd.ramp(t, 4.0F, 34.0F));
            float fadeOut = 1.0F - Rnd.ramp(t, 40.0F, duration);

            if (shrink > 0.01F) {
                d.type(VfxRenderTypes.DISC_DARK);
                d.glow(c, (float) (radius * 2.0D * shrink), 0x000000, 0.98F);
            }

            // eclats sombres du disque brise
            d.type(VfxRenderTypes.LINE_DARK);
            for (int i = 0; i < 60; i++) {
                float life = Rnd.ramp(t, 0.0F, 26.0F);
                if (life >= 1.0F) {
                    break;
                }
                double a = Rnd.f(sd, i) * Math.PI * 2.0D;
                Vec3 dv = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                double r = radius * (0.9D + 3.2D * easeOut(life) * (0.4D + 0.6D * Rnd.f(sd, 70 + i)));
                Vec3 p0 = c.add(dv.scale(r));
                Vec3 p1 = c.add(dv.scale(r + radius * 0.22D));
                d.ribbon(p0, p1, (float) (radius * 0.05D), (float) (radius * 0.03D), 0x000000, 0.9F * (1.0F - life),
                        0.9F * (1.0F - life));
            }

            d.type(VfxRenderTypes.LINE_ADD);
            // ondes de choc
            for (int k = 0; k < 3; k++) {
                float tk = t - k * 4.0F;
                if (tk < 0.0F) {
                    continue;
                }
                float al = (1.0F - Rnd.ramp(tk, 10.0F, 40.0F)) * 0.9F;
                d.ring(c, b[0], b[1], radius * (0.9D + 7.0D * easeOut(tk / 36.0F)), radius * (0.16D + 0.1D * k),
                        k == 0 ? core : rim, al, 96);
            }
            // rayons
            for (int k = 0; k < 30; k++) {
                double a = Rnd.f(sd, 150 + k) * Math.PI * 2.0D;
                Vec3 dv = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                double len = radius * (1.5D + 4.5D * Rnd.f(sd, 190 + k)) * easeOut(Rnd.ramp(t, 2.0F, 20.0F));
                float al = 0.7F * (1.0F - Rnd.ramp(t, 12.0F, 60.0F));
                d.ribbon(c.add(dv.scale(radius * 0.4D)), c.add(dv.scale(radius * 0.4D + len)),
                        (float) (radius * 0.09D), 0.0F, rim, al, 0.0F);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(c, (float) (radius * (2.2D + 7.0D * boom)), rim, 0.85F * (1.0F - boom * 0.6F) * fadeOut);
            d.glow(c, (float) (radius * (1.2D + 2.5D * boom)), 0xFFFFFF, 0.9F * (1.0F - boom) * fadeOut);
            d.glow(c, (float) (radius * 4.0D), core, 0.25F * (1.0F - Rnd.ramp(t, 20.0F, 60.0F)));
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.4F * (1.0F - Rnd.ramp(t, 0.0F, 24.0F));
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.45F * (1.0F - Rnd.ramp(t, 0.0F, 10.0F));
        }

        @Override
        public int flashColor() {
            return which == 0 ? 0xFFEBC0 : 0xDDEEFF;
        }
    }

    // =====================================================================================================
    // Dissolution d'une structure
    // =====================================================================================================

    public static final class Dissolve extends Fx {
        private final double hx;
        private final double hy;
        private final double hz;

        public Dissolve(VfxPacket p) {
            super(p);
            this.hx = Math.max(2.0D, p.x2);
            this.hy = Math.max(3.0D, p.y2);
            this.hz = Math.max(2.0D, p.z2);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            float u = Rnd.clamp01(t / duration);
            float fade = 1.0F - Rnd.ramp(t, duration - 10.0F, duration);
            Vec3 base = origin;
            int light = FxUtil.mix(color, 0xFFFFFF, 0.55F);

            d.type(VfxRenderTypes.LINE_ADD);
            // ondes de choc au sol
            for (int k = 0; k < 3; k++) {
                float tk = t - k * 7.0F;
                if (tk < 0.0F) {
                    continue;
                }
                double r = Math.max(hx, hz) * (0.2D + 1.1D * easeOut(tk / 50.0F));
                float al = (1.0F - Rnd.ramp(tk, 6.0F, 50.0F)) * 0.8F;
                d.ring(base.add(0, 0.2D, 0), AXIS_X, AXIS_Z, r, 1.6D + k * 0.6D, k == 0 ? light : color, al, 72);
            }
            // cadre au sol qui pulse
            float pulse = 0.5F + 0.5F * (float) Math.sin(t * 0.4F);
            Vec3 c00 = base.add(-hx, 0.3D, -hz);
            Vec3 c10 = base.add(hx, 0.3D, -hz);
            Vec3 c11 = base.add(hx, 0.3D, hz);
            Vec3 c01 = base.add(-hx, 0.3D, hz);
            float fa = (0.35F + 0.4F * pulse) * fade;
            d.ribbon(c00, c10, 0.9F, 0.9F, color, fa, fa);
            d.ribbon(c10, c11, 0.9F, 0.9F, color, fa, fa);
            d.ribbon(c11, c01, 0.9F, 0.9F, color, fa, fa);
            d.ribbon(c01, c00, 0.9F, 0.9F, color, fa, fa);

            // piliers de lumiere qui jaillissent au hasard
            for (int i = 0; i < 40; i++) {
                float tb = Rnd.f(seed, 2000 + i) * Math.max(8.0F, duration - 14.0F);
                float age2 = t - tb;
                if (age2 < 0.0F || age2 > 12.0F) {
                    continue;
                }
                float al = (1.0F - age2 / 12.0F) * fade;
                Vec3 p = base.add(Rnd.s(seed, 2100 + i) * hx, 0.0D, Rnd.s(seed, 2200 + i) * hz);
                double h = hy * (0.5D + 1.0D * Rnd.f(seed, 2300 + i)) * easeOut(age2 / 5.0F);
                d.pillar(p, h, 1.4F + 1.2F * Rnd.f(seed, 2400 + i), color, 0.7F * al, 0.0F);
                d.pillar(p, h * 0.9D, 0.5F, 0xFFFFFF, 0.5F * al, 0.0F);
            }

            // grande colonne finale au centre
            float fin = Rnd.ramp(u, 0.82F, 1.0F);
            if (fin > 0.0F) {
                d.pillar(base, hy * 2.5D, (float) (3.0D + Math.min(hx, hz) * 0.15D) * fin * (1.0F - fin * 0.6F), light,
                        0.8F * (1.0F - fin), 0.0F);
            }

            // particules de lumiere qui montent
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 160; i++) {
                float tb = Rnd.f(seed, i) * Math.max(8.0F, duration - 20.0F);
                float life = 22.0F + Rnd.f(seed, 300 + i) * 26.0F;
                float a2 = t - tb;
                if (a2 < 0.0F || a2 > life) {
                    continue;
                }
                float f = a2 / life;
                Vec3 p = base.add(Rnd.s(seed, 400 + i) * hx,
                        Rnd.f(seed, 500 + i) * hy + a2 * (0.1D + 0.2D * Rnd.f(seed, 600 + i)),
                        Rnd.s(seed, 700 + i) * hz);
                d.glow(p, 0.5F + 0.9F * Rnd.f(seed, 800 + i), t * 0.15F + i, i % 4 == 0 ? 0xFFFFFF : color,
                        (float) Math.sin(Math.PI * f) * fade);
            }
            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(base.add(0, hy * 0.5D, 0), (float) (Math.max(hx, hz) * 1.4D), color, 0.10F * fade);
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.45F * (1.0F - Rnd.ramp(t, 0.0F, duration)) * falloff(cam, origin, 90.0F);
        }
    }

    // =====================================================================================================
    // Eclair gele et sa destruction : c'est le VRAI eclair de l'orage, dessine par le rendu de foudre de Minecraft
    // =====================================================================================================

    /** Le tronc decoupe en petits morceaux (`per` par segment). */
    private static Vec3[] subdivide(Vec3[] pts, int per) {
        Vec3[] out = new Vec3[(pts.length - 1) * per + 1];
        for (int i = 0; i < pts.length - 1; i++) {
            for (int k = 0; k < per; k++) {
                out[i * per + k] = FxUtil.lerp(pts[i], pts[i + 1], k / (double) per);
            }
        }
        out[out.length - 1] = pts[pts.length - 1];
        return out;
    }

    /** Point situe a la fraction s (0..1) de la longueur d'une ligne brisee. */
    private static Vec3 along(Vec3[] pts, float s) {
        float f = Rnd.clamp01(s) * (pts.length - 1);
        int i = Math.min(pts.length - 2, (int) f);
        return FxUtil.lerp(pts[i], pts[i + 1], f - i);
    }

    public static final class FrozenBolt extends Fx {
        private final Vec3[] trunk;

        public FrozenBolt(VfxPacket p) {
            super(p);
            this.trunk = BoltShape.trunk(p.seed, new Vec3(p.x, p.y, p.z));
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            float remaining = duration - t;
            float warn = 1.0F - Rnd.ramp(remaining, 0.0F, 50.0F);
            float a = Rnd.ramp(t, 0.0F, 4.0F) * Rnd.ramp(remaining, 0.0F, 2.0F);
            if (a <= 0.0F) {
                return;
            }
            Vec3 base = origin;

            // 1. le vrai eclair vanilla, fige ; il clignote dans les dernieres secondes avant de retomber
            boolean on = warn <= 0.0F || Math.sin(t * 2.3F * (1.0F + warn)) > -0.25D;
            if (on) {
                d.vanillaBolt(base, seed, partial);
                d.vanillaBolt(base, seed, partial);
            }

            // 2. de la lumiere qui suit la vraie forme de l'eclair
            float pulse = 0.5F + 0.5F * (float) Math.sin(t * 0.35F);
            d.type(VfxRenderTypes.LINE_ADD);
            for (int i = 0; i < trunk.length - 1; i++) {
                float h = 1.0F - i / (float) trunk.length * 0.6F;
                d.ribbon(trunk[i], trunk[i + 1], 1.5F, 1.5F, color, 0.22F * a * h, 0.22F * a * h);
            }
            d.ring(base.add(0, 0.1D, 0), AXIS_X, AXIS_Z, 1.5D + 0.3D * pulse, 0.45D, color, 0.7F * a, 40);
            d.ring(base.add(0, 0.12D, 0), AXIS_X, AXIS_Z, 3.0D - 1.2D * warn, 0.2D, 0xFFFFFF, 0.45F * a, 48);

            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(base.add(0, 0.5D, 0), 6.0F + 2.0F * warn, color, 0.5F * a);
            d.glow(base.add(0, 0.5D, 0), 2.2F, 0xFFFFFF, 0.75F * a);
            d.glow(trunk[3], 14.0F, color, 0.16F * a);
            d.glow(trunk[6], 18.0F, color, 0.12F * a);
            // etincelles qui grimpent le long de l'eclair
            for (int i = 0; i < 6; i++) {
                float s = fract(t * (0.011F + 0.004F * warn) + i / 6.0F);
                Vec3 p = along(trunk, s);
                d.glow(p, 1.3F, color, 0.7F * a);
                d.glow(p, 0.5F, 0xFFFFFF, a);
            }

            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 26; i++) {
                float s = 0.02F + 0.5F * Rnd.f(seed, 3700 + i);
                Vec3 p = along(trunk, s).add(Rnd.s(seed, 3800 + i) * 2.2D, Rnd.s(seed, 3900 + i) * 0.8D,
                        Rnd.s(seed, 4000 + i) * 2.2D);
                float tw = 0.5F + 0.5F * (float) Math.sin(t * 0.3F + i);
                d.glow(p, 0.4F + 0.5F * tw, t * 0.05F + i, 0xFFFFFF, 0.8F * tw * a);
            }
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.4F * (1.0F - Rnd.ramp(t, 0.0F, 6.0F)) * falloff(cam, origin, 90.0F);
        }

        @Override
        public int flashColor() {
            return 0xE8F2FF;
        }

        @Override
        public float shake(Vec3 cam, float t) {
            float remaining = duration - t;
            return 0.12F * (1.0F - Rnd.ramp(remaining, 0.0F, 30.0F)) * falloff(cam, origin, 60.0F);
        }
    }

    public static final class BoltShatter extends Fx {
        private final Vec3[] trunk;
        private final Vec3[] dense;

        public BoltShatter(VfxPacket p) {
            super(p);
            this.trunk = BoltShape.trunk(p.seed, new Vec3(p.x, p.y, p.z));
            this.dense = subdivide(trunk, 4);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 base = origin;
            float flash = 1.0F - Rnd.ramp(t, 0.0F, 7.0F);

            // l'eclair vanilla s'embrase juste avant d'eclater
            int passes = flash > 0.66F ? 3 : flash > 0.33F ? 2 : flash > 0.0F ? 1 : 0;
            for (int k = 0; k < passes; k++) {
                d.vanillaBolt(base, seed, partial);
            }

            d.type(VfxRenderTypes.LINE_ADD);
            // morceaux de l'eclair qui volent en eclats
            float ts = t / 20.0F;
            for (int i = 0; i < dense.length - 1; i++) {
                float life = 0.55F + 0.4F * Rnd.f(seed, 10 + i);
                if (ts > life) {
                    continue;
                }
                float f = ts / life;
                Vec3 out = new Vec3(Rnd.s(seed, 100 + i), Rnd.s(seed, 200 + i) * 0.3D + 0.2D, Rnd.s(seed, 300 + i))
                        .normalize().scale((3.0D + 9.0D * Rnd.f(seed, 400 + i)) * ts);
                Vec3 g = new Vec3(0.0D, -6.0D * ts * ts, 0.0D);
                Vec3 p0 = dense[i].add(out).add(g);
                Vec3 p1 = dense[i + 1].add(out.scale(1.0D + 0.2D * Rnd.f(seed, 500 + i))).add(g);
                d.ribbon(p0, p1, 0.9F * (1.0F - f), 0.8F * (1.0F - f), i % 3 == 0 ? 0xFFFFFF : color,
                        0.9F * (1.0F - f), 0.9F * (1.0F - f));
            }
            // onde au sol
            for (int k = 0; k < 2; k++) {
                float tk = t - k * 3.0F;
                if (tk < 0.0F) {
                    continue;
                }
                d.ring(base.add(0, 0.15D, 0), AXIS_X, AXIS_Z, 1.0D + 12.0D * easeOut(tk / 22.0F),
                        1.2D * (1.0F - tk / 34.0F) + 0.2D, k == 0 ? 0xFFFFFF : color,
                        (1.0F - Rnd.ramp(tk, 3.0F, 26.0F)) * 0.9F, 56);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            d.glow(base.add(0, 0.6D, 0), 18.0F * flash + 3.0F, color, 0.8F * flash);
            d.glow(base.add(0, 0.6D, 0), 7.0F * flash, 0xFFFFFF, flash);

            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 60; i++) {
                float life = 0.5F + Rnd.f(seed, 600 + i) * 0.6F;
                if (ts > life) {
                    continue;
                }
                Vec3 dir = sphere(seed, 700 + i);
                dir = new Vec3(dir.x, Math.abs(dir.y) * 0.9D + 0.2D, dir.z).normalize();
                Vec3 p = base.add(dir.scale((4.0D + 12.0D * Rnd.f(seed, 800 + i)) * ts)).add(0.0D, -4.0D * ts * ts, 0.0D);
                d.glow(p, 0.55F * (1.0F - ts / life), t * 0.2F + i, 0xFFFFFF, 1.0F - ts / life);
            }
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.6F * (1.0F - Rnd.ramp(t, 0.0F, 16.0F)) * falloff(cam, origin, 60.0F);
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.5F * (1.0F - Rnd.ramp(t, 0.0F, 6.0F)) * falloff(cam, origin, 35.0F);
        }

        @Override
        public int flashColor() {
            return 0xDDEEFF;
        }
    }

    // =====================================================================================================
    // Ceremonie d'une structure evoluee
    // =====================================================================================================

    public static final class Reward extends Fx {
        public Reward(VfxPacket p) {
            super(p);
        }

        @Override
        public void render(VfxDraw d, float t, float partial) {
            Vec3 base = pos(partial);
            int gold = 0xFFD76A;
            int tint = FxUtil.mix(color, gold, 0.4F);
            float rise = easeOut(Rnd.ramp(t, 6.0F, 26.0F));
            float fade = 1.0F - Rnd.ramp(t, 52.0F, duration);
            Vec3 chest = base.add(0.0D, 1.0D, 0.0D);

            d.type(VfxRenderTypes.LINE_ADD);
            // des fils de lumiere convergent vers le joueur
            float conv = Rnd.ramp(t, 0.0F, 14.0F);
            if (conv < 1.0F) {
                for (int i = 0; i < 30; i++) {
                    Vec3 dir = sphere(seed, i);
                    double r = 7.0D * (1.0F - easeIn(conv));
                    Vec3 p1 = chest.add(dir.scale(r));
                    Vec3 p0 = chest.add(dir.scale(r + 1.8D));
                    d.ribbon(p0, p1, 0.0F, 0.12F, i % 2 == 0 ? tint : 0xFFFFFF, 0.0F, 0.9F * (1.0F - conv * 0.5F));
                }
            }
            // colonne de lumiere
            if (t > 8.0F) {
                float w = 2.6F * rise * (1.0F - 0.3F * Rnd.ramp(t, 20.0F, 60.0F));
                d.pillar(base, 46.0D * rise, w, tint, 0.8F * fade, 0.1F * fade);
                d.pillar(base, 46.0D * rise, w * 0.4F, 0xFFFFFF, 0.9F * fade, 0.15F * fade);
            }
            // trois anneaux tournants qui montent
            for (int k = 0; k < 3; k++) {
                float rk = Rnd.ramp(t, 8.0F + k * 3.0F, 30.0F + k * 3.0F);
                if (rk <= 0.0F) {
                    continue;
                }
                double y = 0.15D + easeOut(rk) * (1.2D + k * 1.0D);
                double rad = 1.3D + k * 0.55D + 0.2D * Math.sin(t * 0.2D + k);
                double spin = t * (0.14D + 0.05D * k) * (k % 2 == 0 ? 1.0D : -1.0D);
                d.arc(base.add(0, y, 0), AXIS_X, AXIS_Z, rad, 0.22D, spin, spin + Math.PI * 1.4D, k == 1 ? 0xFFFFFF : tint,
                        0.9F * fade, 36);
                d.arc(base.add(0, y, 0), AXIS_X, AXIS_Z, rad, 0.22D, spin + Math.PI, spin + Math.PI * 1.7D, tint,
                        0.6F * fade, 24);
            }
            // onde au sol quand la structure s'ouvre
            for (int k = 0; k < 3; k++) {
                float tk = t - 12.0F - k * 5.0F;
                if (tk < 0.0F) {
                    continue;
                }
                d.ring(base.add(0, 0.12D, 0), AXIS_X, AXIS_Z, 0.6D + 9.0D * easeOut(tk / 30.0F),
                        0.9D * (1.0F - tk / 44.0F) + 0.15D, k == 0 ? 0xFFFFFF : tint, (1.0F - Rnd.ramp(tk, 6.0F, 36.0F)) * 0.9F, 56);
            }

            d.type(VfxRenderTypes.GLOW_ADD);
            float burst = (1.0F - Rnd.ramp(t, 12.0F, 30.0F)) * Rnd.ramp(t, 8.0F, 12.0F);
            d.glow(chest, 9.0F * burst + 2.0F * rise, tint, 0.8F * burst + 0.3F * fade * rise);
            d.glow(chest, 4.0F * burst, 0xFFFFFF, burst);

            // etoiles en spirale qui s'elevent
            d.type(VfxRenderTypes.STAR_ADD);
            for (int i = 0; i < 40; i++) {
                float ph = fract(t * 0.022F + Rnd.f(seed, 100 + i));
                double ang = t * 0.16D + i * 2.399963D;
                double rr = 0.9D + 1.2D * Rnd.f(seed, 200 + i) + 0.8D * ph;
                Vec3 p = base.add(Math.cos(ang) * rr, 0.1D + ph * 7.0D, Math.sin(ang) * rr);
                float al = (float) Math.sin(Math.PI * ph) * fade * Rnd.ramp(t, 6.0F, 12.0F);
                d.glow(p, 0.4F + 0.5F * Rnd.f(seed, 300 + i), t * 0.2F + i, i % 3 == 0 ? 0xFFFFFF : tint, al);
            }
            // gerbe d'etincelles au moment de l'ouverture
            float ts = Math.max(0.0F, (t - 12.0F) / 20.0F);
            if (ts > 0.0F && ts < 1.0F) {
                for (int i = 0; i < 50; i++) {
                    Vec3 dir = sphere(seed, 400 + i);
                    dir = new Vec3(dir.x, Math.abs(dir.y) * 0.8D + 0.3D, dir.z).normalize();
                    Vec3 p = chest.add(dir.scale((3.0D + 7.0D * Rnd.f(seed, 500 + i)) * easeOut(ts)))
                            .add(0.0D, -2.5D * ts * ts, 0.0D);
                    d.glow(p, 0.5F * (1.0F - ts), t * 0.3F + i, i % 2 == 0 ? 0xFFFFFF : gold, 1.0F - ts);
                }
            }
        }

        @Override
        public float flash(Vec3 cam, float t) {
            return 0.5F * Rnd.ramp(t, 8.0F, 11.0F) * (1.0F - Rnd.ramp(t, 11.0F, 22.0F)) * falloff(cam, origin, 8.0F);
        }

        @Override
        public int flashColor() {
            return 0xFFF2C0;
        }

        @Override
        public float shake(Vec3 cam, float t) {
            return 0.25F * Rnd.ramp(t, 8.0F, 11.0F) * (1.0F - Rnd.ramp(t, 11.0F, 30.0F)) * falloff(cam, origin, 30.0F);
        }
    }

    private FxMining() {}
}
