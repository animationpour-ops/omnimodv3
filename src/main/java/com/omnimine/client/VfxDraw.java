package com.omnimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Primitives de dessin des effets : halos, rubans, anneaux, eclairs. Tout est en coordonnees monde. */
public final class VfxDraw {
    /** Eclair factice (jamais ajoute au monde) qui sert a appeler le rendu de foudre de Minecraft. */
    private static LightningBolt dummyBolt;

    private final MultiBufferSource.BufferSource buffers;
    private final PoseStack poseStack;
    private final Matrix4f pose;
    public final Vec3 cam;
    public final Vec3 left;
    public final Vec3 up;
    public final Vec3 look;
    private VertexConsumer vc;
    /** Agrandissement courant (1 = normal) autour du point (sx, sy, sz). */
    private float scale = 1.0F;
    private double sx;
    private double sy;
    private double sz;

    public VfxDraw(MultiBufferSource.BufferSource buffers, PoseStack poseStack, Camera camera) {
        this.buffers = buffers;
        this.poseStack = poseStack;
        this.pose = poseStack.last().pose();
        this.cam = camera.getPosition();
        Vector3f l = camera.getLeftVector();
        Vector3f u = camera.getUpVector();
        Vector3f f = camera.getLookVector();
        this.left = new Vec3(l.x(), l.y(), l.z());
        this.up = new Vec3(u.x(), u.y(), u.z());
        this.look = new Vec3(f.x(), f.y(), f.z());
    }

    /**
     * Agrandit (ou reduit) tout ce qui est dessine ensuite autour de `origin` : halos, rubans, anneaux, eclairs
     * grossissent ensemble. s = 1 : taille normale. Ne touche ni aux fissures ni a l'eclair vanilla.
     */
    public void setScale(Vec3 origin, float s) {
        this.scale = s;
        if (origin != null) {
            this.sx = origin.x;
            this.sy = origin.y;
            this.sz = origin.z;
        }
    }

    /** La pile de transformations du rendu du monde (origine = camera) : sert a redessiner un mob par son propre rendu. */
    public PoseStack poseStack() {
        return poseStack;
    }

    /** Choisit le type de rendu (et termine le lot precedent). */
    public VfxDraw type(RenderType type) {
        this.vc = buffers.getBuffer(type);
        return this;
    }

    public void flush() {
        buffers.endBatch(VfxRenderTypes.DISC_DARK);
        buffers.endBatch(VfxRenderTypes.GLOW_DARK);
        buffers.endBatch(VfxRenderTypes.LINE_DARK);
        buffers.endBatch(VfxRenderTypes.GLOW_ADD);
        buffers.endBatch(VfxRenderTypes.LINE_ADD);
        buffers.endBatch(VfxRenderTypes.STAR_ADD);
        buffers.endBatch(VfxRenderTypes.GLOW_ADD_NODEPTH);
        for (RenderType crack : VfxRenderTypes.CRACKS) {
            buffers.endBatch(crack);
        }
    }

    // ------------------------------------------------------------------ foudre vanilla

    /**
     * Dessine un VRAI eclair avec le rendu de la foudre de Minecraft (LightningBoltRenderer), dont le pied est en
     * `base`. `seed` fixe la forme (c'est le meme tirage que celui de BoltShape cote serveur).
     */
    public void vanillaBolt(Vec3 base, long seed, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (dummyBolt == null || dummyBolt.level() != mc.level) {
            dummyBolt = EntityType.LIGHTNING_BOLT.create(mc.level);
        }
        if (dummyBolt == null) {
            return;
        }
        dummyBolt.seed = seed;
        mc.getEntityRenderDispatcher().render(dummyBolt, base.x - cam.x, base.y - cam.y, base.z - cam.z, 0.0F,
                partial, poseStack, buffers, 15728880);
        buffers.endBatch(RenderType.lightning());
    }

    // ------------------------------------------------------------------ fissures de minage vanilla

    private void cv(double x, double y, double z, float u, float v) {
        vc.vertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .uv(u, v).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
    }

    /**
     * Fissures vanilla sur les six faces d'une boite (le type de rendu doit etre VfxRenderTypes.crack(stade)).
     * `blocksPerTile` = taille en blocs d'une image de fissures (1 = comme sur un bloc).
     */
    public void crackBox(AABB b, float blocksPerTile) {
        float k = 1.0F / Math.max(0.05F, blocksPerTile);
        float x0 = (float) b.minX * k;
        float x1 = (float) b.maxX * k;
        float y0 = (float) b.minY * k;
        float y1 = (float) b.maxY * k;
        float z0 = (float) b.minZ * k;
        float z1 = (float) b.maxZ * k;
        // dessus et dessous
        cv(b.minX, b.maxY, b.minZ, x0, z0);
        cv(b.maxX, b.maxY, b.minZ, x1, z0);
        cv(b.maxX, b.maxY, b.maxZ, x1, z1);
        cv(b.minX, b.maxY, b.maxZ, x0, z1);
        cv(b.minX, b.minY, b.minZ, x0, z0);
        cv(b.maxX, b.minY, b.minZ, x1, z0);
        cv(b.maxX, b.minY, b.maxZ, x1, z1);
        cv(b.minX, b.minY, b.maxZ, x0, z1);
        // nord et sud
        cv(b.minX, b.minY, b.minZ, x0, y1);
        cv(b.maxX, b.minY, b.minZ, x1, y1);
        cv(b.maxX, b.maxY, b.minZ, x1, y0);
        cv(b.minX, b.maxY, b.minZ, x0, y0);
        cv(b.minX, b.minY, b.maxZ, x0, y1);
        cv(b.maxX, b.minY, b.maxZ, x1, y1);
        cv(b.maxX, b.maxY, b.maxZ, x1, y0);
        cv(b.minX, b.maxY, b.maxZ, x0, y0);
        // ouest et est
        cv(b.minX, b.minY, b.minZ, z0, y1);
        cv(b.minX, b.minY, b.maxZ, z1, y1);
        cv(b.minX, b.maxY, b.maxZ, z1, y0);
        cv(b.minX, b.maxY, b.minZ, z0, y0);
        cv(b.maxX, b.minY, b.minZ, z0, y1);
        cv(b.maxX, b.minY, b.maxZ, z1, y1);
        cv(b.maxX, b.maxY, b.maxZ, z1, y0);
        cv(b.maxX, b.maxY, b.minZ, z0, y0);
    }

    /** Une image de fissures vanilla, face a la camera, centree en c, de demi-cote `half` (soleil, lune). */
    public void crackFacing(Vec3 c, double half) {
        Vec3 ax = left.scale(half);
        Vec3 ay = up.scale(half);
        Vec3 p0 = c.subtract(ax).subtract(ay);
        Vec3 p1 = c.add(ax).subtract(ay);
        Vec3 p2 = c.add(ax).add(ay);
        Vec3 p3 = c.subtract(ax).add(ay);
        cv(p0.x, p0.y, p0.z, 0.0F, 1.0F);
        cv(p1.x, p1.y, p1.z, 1.0F, 1.0F);
        cv(p2.x, p2.y, p2.z, 1.0F, 0.0F);
        cv(p3.x, p3.y, p3.z, 0.0F, 0.0F);
    }

    private static final double[][] TUBE_CORNERS = {{1.0D, 1.0D}, {-1.0D, 1.0D}, {-1.0D, -1.0D}, {1.0D, -1.0D}};

    /**
     * Fissures vanilla sur un manchon a section carree qui epouse le segment [a, b] (l'eclair qu'on mine) : demi-cote
     * wa en a et wb en b. `blocksPerTile` = taille en blocs d'une image de fissures.
     */
    public void crackTube(Vec3 a, Vec3 b, double wa, double wb, float blocksPerTile) {
        Vec3 axis = b.subtract(a);
        double len = axis.length();
        if (len < 1.0E-4D) {
            return;
        }
        axis = axis.scale(1.0D / len);
        Vec3 helper = Math.abs(axis.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = axis.cross(helper).normalize();
        Vec3 upv = side.cross(axis).normalize();
        float k = 1.0F / Math.max(0.05F, blocksPerTile);
        float vEnd = (float) len * k;
        for (int i = 0; i < 4; i++) {
            double[] c0 = TUBE_CORNERS[i];
            double[] c1 = TUBE_CORNERS[(i + 1) % 4];
            Vec3 o0 = side.scale(c0[0]).add(upv.scale(c0[1]));
            Vec3 o1 = side.scale(c1[0]).add(upv.scale(c1[1]));
            Vec3 a0 = a.add(o0.scale(wa));
            Vec3 a1 = a.add(o1.scale(wa));
            Vec3 b0 = b.add(o0.scale(wb));
            Vec3 b1 = b.add(o1.scale(wb));
            float uEnd = (float) (2.0D * Math.max(wa, wb)) * k;
            cv(a0.x, a0.y, a0.z, 0.0F, 0.0F);
            cv(a1.x, a1.y, a1.z, uEnd, 0.0F);
            cv(b1.x, b1.y, b1.z, uEnd, vEnd);
            cv(b0.x, b0.y, b0.z, 0.0F, vEnd);
        }
    }

    private void v(Vec3 p, float u, float v, float r, float g, float b, float a) {
        double x = p.x;
        double y = p.y;
        double z = p.z;
        if (scale != 1.0F) {
            x = sx + (x - sx) * scale;
            y = sy + (y - sy) * scale;
            z = sz + (z - sz) * scale;
        }
        vc.vertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .uv(u, v).color(r, g, b, a).endVertex();
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }

    /** Halo qui regarde toujours la camera. */
    public void glow(Vec3 p, float size, int rgb, float alpha) {
        glow(p, size, 0.0F, rgb, alpha);
    }

    public void glow(Vec3 p, float size, float rot, int rgb, float alpha) {
        if (alpha <= 0.002F || size <= 0.0F) {
            return;
        }
        float hs = size * 0.5F;
        double c = Math.cos(rot) * hs;
        double s = Math.sin(rot) * hs;
        Vec3 ax = left.scale(c).add(up.scale(s));
        Vec3 ay = left.scale(-s).add(up.scale(c));
        float r = red(rgb);
        float g = green(rgb);
        float b = blue(rgb);
        v(p.subtract(ax).subtract(ay), 0, 0, r, g, b, alpha);
        v(p.add(ax).subtract(ay), 1, 0, r, g, b, alpha);
        v(p.add(ax).add(ay), 1, 1, r, g, b, alpha);
        v(p.subtract(ax).add(ay), 0, 1, r, g, b, alpha);
    }

    /** Ruban entre a et b qui se tourne vers la camera (eclairs, faisceaux, plumes). */
    public void ribbon(Vec3 a, Vec3 b, float widthA, float widthB, int rgb, float alphaA, float alphaB) {
        if (alphaA <= 0.002F && alphaB <= 0.002F) {
            return;
        }
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 side = dir.cross(cam.subtract(mid));
        side = side.lengthSqr() < 1.0E-10D ? left : side.normalize();
        Vec3 sa = side.scale(widthA * 0.5D);
        Vec3 sb = side.scale(widthB * 0.5D);
        float r = red(rgb);
        float g = green(rgb);
        float bl = blue(rgb);
        v(a.subtract(sa), 0, 0, r, g, bl, alphaA);
        v(a.add(sa), 1, 0, r, g, bl, alphaA);
        v(b.add(sb), 1, 1, r, g, bl, alphaB);
        v(b.subtract(sb), 0, 1, r, g, bl, alphaB);
    }

    /** Pilier vertical de lumiere : un ruban qui tourne autour de l'axe Y pour faire face a la camera. */
    public void pillar(Vec3 base, double height, float width, int rgb, float alphaBottom, float alphaTop) {
        Vec3 toCam = new Vec3(cam.x - base.x, 0, cam.z - base.z);
        Vec3 side = toCam.lengthSqr() < 1.0E-8D ? new Vec3(1, 0, 0)
                : new Vec3(-toCam.z, 0, toCam.x).normalize();
        Vec3 s = side.scale(width * 0.5D);
        Vec3 top = base.add(0, height, 0);
        float r = red(rgb);
        float g = green(rgb);
        float b = blue(rgb);
        v(base.subtract(s), 0, 0, r, g, b, alphaBottom);
        v(base.add(s), 1, 0, r, g, b, alphaBottom);
        v(top.add(s), 1, 1, r, g, b, alphaTop);
        v(top.subtract(s), 0, 1, r, g, b, alphaTop);
    }

    /** Anneau plat dans le plan (u, v), u et v unitaires et perpendiculaires. */
    public void ring(Vec3 c, Vec3 u, Vec3 vv, double radius, double width, int rgb, float alpha, int segs) {
        arc(c, u, vv, radius, width, 0.0D, Math.PI * 2.0D, rgb, alpha, segs);
    }

    /** Arc d'anneau entre deux angles (radians), l'epaisseur est lissee sur la largeur par la texture. */
    public void arc(Vec3 c, Vec3 u, Vec3 vv, double radius, double width, double a0, double a1,
                    int rgb, float alpha, int segs) {
        if (alpha <= 0.002F || radius <= 0.0D) {
            return;
        }
        float r = red(rgb);
        float g = green(rgb);
        float b = blue(rgb);
        double in = Math.max(0.0D, radius - width * 0.5D);
        double out = radius + width * 0.5D;
        double step = (a1 - a0) / segs;
        Vec3 prevIn = null;
        Vec3 prevOut = null;
        for (int i = 0; i <= segs; i++) {
            double ang = a0 + step * i;
            Vec3 dir = u.scale(Math.cos(ang)).add(vv.scale(Math.sin(ang)));
            Vec3 pin = c.add(dir.scale(in));
            Vec3 pout = c.add(dir.scale(out));
            if (prevIn != null) {
                v(prevIn, 0, 0, r, g, b, alpha);
                v(prevOut, 1, 0, r, g, b, alpha);
                v(pout, 1, 1, r, g, b, alpha);
                v(pin, 0, 1, r, g, b, alpha);
            }
            prevIn = pin;
            prevOut = pout;
        }
    }

    /** Eclair : deplacement du point milieu, en trois couches (halo, couleur, coeur blanc). */
    public void lightning(Vec3 a, Vec3 b, int seed, int levels, float spread, float width, int rgb, float alpha,
                          int branches) {
        Vec3[] pts = bolt(a, b, seed, levels, spread);
        drawBolt(pts, width, rgb, alpha);
        for (int k = 0; k < branches; k++) {
            int idx = 1 + (int) (Rnd.f(seed, 900 + k) * (pts.length - 3));
            Vec3 from = pts[idx];
            Vec3 main = b.subtract(a);
            Vec3 rnd = new Vec3(Rnd.s(seed, 910 + k), Rnd.s(seed, 920 + k) * 0.6D - 0.4D, Rnd.s(seed, 930 + k));
            Vec3 dir = main.normalize().add(rnd.scale(1.1D)).normalize();
            double len = main.length() * Rnd.range(seed, 940 + k, 0.12F, 0.3F);
            Vec3[] sub = bolt(from, from.add(dir.scale(len)), seed + 77 * (k + 1), Math.max(2, levels - 2), spread);
            drawBolt(sub, width * 0.55F, rgb, alpha * 0.85F);
        }
    }

    /** Dessine une ligne brisee deja calculee comme un eclair (halo, couleur, coeur blanc). */
    public void strip(Vec3[] pts, float width, int rgb, float alpha) {
        drawBolt(pts, width, rgb, alpha);
    }

    private static Vec3[] bolt(Vec3 a, Vec3 b, int seed, int levels, float spread) {
        int n = 2;
        Vec3[] pts = new Vec3[(1 << levels) + 1];
        pts[0] = a;
        pts[1] = b;
        double amp = a.distanceTo(b) * spread;
        int salt = 0;
        for (int l = 0; l < levels; l++) {
            Vec3[] next = new Vec3[(n - 1) * 2 + 1];
            for (int i = 0; i < n - 1; i++) {
                Vec3 p0 = pts[i];
                Vec3 p1 = pts[i + 1];
                Vec3 mid = p0.add(p1).scale(0.5D);
                Vec3 off = new Vec3(Rnd.s(seed, salt++), Rnd.s(seed, salt++), Rnd.s(seed, salt++)).scale(amp);
                next[i * 2] = p0;
                next[i * 2 + 1] = mid.add(off);
            }
            next[next.length - 1] = pts[n - 1];
            System.arraycopy(next, 0, pts, 0, next.length);
            n = next.length;
            amp *= 0.55D;
        }
        Vec3[] out = new Vec3[n];
        System.arraycopy(pts, 0, out, 0, n);
        return out;
    }

    private void drawBolt(Vec3[] pts, float width, int rgb, float alpha) {
        for (int i = 0; i < pts.length - 1; i++) {
            ribbon(pts[i], pts[i + 1], width * 5.0F, width * 5.0F, rgb, alpha * 0.35F, alpha * 0.35F);
        }
        for (int i = 0; i < pts.length - 1; i++) {
            ribbon(pts[i], pts[i + 1], width * 2.2F, width * 2.2F, rgb, alpha * 0.9F, alpha * 0.9F);
        }
        for (int i = 0; i < pts.length - 1; i++) {
            ribbon(pts[i], pts[i + 1], width, width, 0xFFFFFF, alpha, alpha);
        }
    }
}
