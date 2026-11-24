package com.omnimine.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.omnimine.Omnimine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Fissures de minage vanilla posees DIRECTEMENT SUR LE MODELE d'un mob : on refait dessiner le mob par son propre
 * rendu (au meme endroit, dans la meme pose), mais chaque sommet passe par le meme " decalque " que celui qui colle
 * les fissures sur un bloc qu'on mine (SheetedDecalTextureGenerator) et finit dans le type de rendu vanilla des
 * fissures (RenderType.crumbling). Resultat : les fissures epousent la tete, le corps et les membres du mob.
 * Si quelque chose echoue, on le note une fois et on rend la main (FxMining dessine alors une boite de secours).
 */
public final class EntityCracks {
    private static final int FULL_LIGHT = 15728880;
    private static final RenderType[] TYPES = new RenderType[10];

    /** Tampon a nous (jamais celui de Minecraft, pour ne rien deregler s'il arrive un souci). */
    private static MultiBufferSource.BufferSource buffer;
    private static boolean broken;

    /** Un consommateur de sommets qui ignore tout : pour les parties du mob qui ne sont pas des surfaces (texte, laisse...). */
    private static final VertexConsumer DISCARD = new DiscardConsumer();

    private static RenderType type(int stage) {
        int s = Math.max(0, Math.min(9, stage));
        if (TYPES[s] == null) {
            TYPES[s] = RenderType.crumbling(new ResourceLocation("minecraft", "textures/block/destroy_stage_" + s + ".png"));
        }
        return TYPES[s];
    }

    /** Taille en blocs d'une image de fissures sur ce mob : plus le mob est grand, plus les fissures le sont. */
    private static float blocksPerTile(Entity e) {
        return Mth.clamp(e.getBbHeight() * 0.55F, 0.6F, 1.4F);
    }

    /**
     * Dessine les fissures du stade `stage` (0 a 9) sur le mob `e`. Renvoie false si ce n'est pas possible (l'appelant
     * dessine alors une boite de fissures a la place).
     */
    public static boolean draw(VfxDraw d, Entity e, int stage, float partial) {
        if (broken || e == null || e.isRemoved()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (buffer == null) {
                buffer = MultiBufferSource.immediate(new BufferBuilder(256));
            }
            EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(e);
            if (renderer == null) {
                return false;
            }
            // pile a nous, copie de celle du monde : un souci en plein rendu ne peut pas la deregler
            PoseStack world = d.poseStack();
            PoseStack ps = new PoseStack();
            ps.last().pose().set(world.last().pose());
            ps.last().normal().set(world.last().normal());

            // exactement ce que fait Minecraft pour dessiner une entite : position interpolee, relative a la camera
            double x = Mth.lerp(partial, e.xOld, e.getX()) - d.cam.x;
            double y = Mth.lerp(partial, e.yOld, e.getY()) - d.cam.y;
            double z = Mth.lerp(partial, e.zOld, e.getZ()) - d.cam.z;
            float yaw = Mth.lerp(partial, e.yRotO, e.getYRot());
            Vec3 offset = renderer.getRenderOffset(e, partial);
            ps.translate(x + offset.x, y + offset.y, z + offset.z);

            // le decalque calcule les fissures a partir de cette position de reference (aux pieds du mob)
            final Matrix4f basePose = new Matrix4f(ps.last().pose());
            final Matrix3f baseNormal = new Matrix3f(ps.last().normal());
            final float textureScale = 1.0F / blocksPerTile(e);

            RenderType crack = type(stage);
            final VertexConsumer target = buffer.getBuffer(crack);
            MultiBufferSource source = requested -> isSurface(requested)
                    ? new SheetedDecalTextureGenerator(target, basePose, baseNormal, textureScale)
                    : DISCARD;

            renderer.render(e, yaw, partial, ps, source, FULL_LIGHT);
            buffer.endBatch(crack);
            return true;
        } catch (RuntimeException ex) {
            broken = true;
            Omnimine.LOGGER.error("Omnimine : fissures sur le modele du mob impossibles, boite de secours utilisee", ex);
            return false;
        }
    }

    /** Seules les vraies surfaces du mob recoivent des fissures (pas le texte du nom, la laisse, les halos...). */
    private static boolean isSurface(RenderType requested) {
        VertexFormat format = requested.format();
        return requested.mode() == VertexFormat.Mode.QUADS
                && (format == DefaultVertexFormat.NEW_ENTITY || format == DefaultVertexFormat.BLOCK);
    }

    private static final class DiscardConsumer extends DefaultedVertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {}
    }

    private EntityCracks() {}
}
