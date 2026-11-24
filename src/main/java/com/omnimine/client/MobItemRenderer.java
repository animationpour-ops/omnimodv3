package com.omnimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.omnimine.Omnimine;
import com.omnimine.data.MobKind;
import com.omnimine.item.MobItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dessine l'essence d'un mob comme un MINIATURE 3D du mob : on fait dessiner le vrai modele vanilla du mob (celui
 * du creeper, du golem...) par son propre rendu, sur une copie factice qui n'existe jamais dans le monde, reduite
 * pour tenir dans l'objet. Dans l'inventaire il est vu de trois quarts ; au sol et dans un cadre il tourne.
 */
public final class MobItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static MobItemRenderer instance;

    private final Map<MobKind, Entity> dummies = new EnumMap<>(MobKind.class);
    private ClientLevel dummyLevel;
    private boolean failed;

    public static MobItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new MobItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    private MobItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    /** La copie factice du mob (jamais ajoutee au monde), recreee quand on change de monde. */
    private Entity dummy(MobKind kind, ClientLevel level) {
        if (level != dummyLevel) {
            dummies.clear();
            dummyLevel = level;
        }
        Entity entity = dummies.get(kind);
        if (entity == null) {
            entity = kind.type.create(level);
            if (entity != null) {
                dummies.put(kind, entity);
            }
        }
        return entity;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
            int light, int overlay) {
        if (failed || !(stack.getItem() instanceof MobItem item)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        Entity entity = dummy(item.kind, level);
        if (entity == null) {
            return;
        }
        float partial = mc.getFrameTime();
        float time = level.getGameTime() + partial;
        // le mob fait tourner son animation de repos (bras qui bougent, yeux...) sans jamais etre ajoute au monde
        entity.tickCount = (int) time;

        // on le reduit pour qu'il tienne dans environ 82 % du cube de l'objet
        float size = Math.max(entity.getBbHeight(), entity.getBbWidth() * 1.15F);
        float scale = 0.82F / Math.max(0.3F, size);
        float yaw;
        float pitch = 0.0F;
        switch (context) {
            case GUI -> {
                yaw = 150.0F;
                pitch = 8.0F;
            }
            case GROUND, FIXED -> yaw = time * 3.0F;
            default -> yaw = 0.0F;
        }

        poseStack.pushPose();
        try {
            // l'origine est le coin du cube de l'objet : on se place au centre, en bas du mob
            poseStack.translate(0.5D, 0.5D - entity.getBbHeight() * scale * 0.5D, 0.5D);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.scale(scale, scale, scale);
            EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
            renderer.render(entity, 0.0F, partial, poseStack, buffer, light);
        } catch (RuntimeException e) {
            failed = true;
            Omnimine.LOGGER.error("Omnimine : impossible de dessiner l'essence en 3D, objet masque", e);
        } finally {
            poseStack.popPose();
        }
    }
}
