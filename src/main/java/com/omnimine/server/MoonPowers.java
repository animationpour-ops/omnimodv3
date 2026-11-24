package com.omnimine.server;

import com.omnimine.OmniConfig;
import com.omnimine.Omnimine;
import com.omnimine.item.LunarBootsItem;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModSounds;
import com.omnimine.util.TargetUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Les deux pouvoirs des bottes lunaires. Pouvoir 1 : gravite lunaire et double saut (deux sauts en l'air).
 * Pouvoir 2 : s'accroupir en l'air pour plonger et provoquer un impact lunaire.
 */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class MoonPowers {
    public static final int COLOR = 0xBFD8FF;
    private static final Map<UUID, Integer> AIR_JUMPS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_JUMP = new HashMap<>();
    private static final Set<UUID> SLAMMING = new HashSet<>();
    private static final Set<UUID> SNEAKING = new HashSet<>();

    /** Appele quand le client signale un appui sur saut en l'air. */
    public static void airJump(ServerPlayer p) {
        UUID id = p.getUUID();
        if (!LunarBootsItem.wornBy(p) || p.onGround() || p.isInWaterOrBubble() || p.isInLava()
                || p.getAbilities().flying || p.isFallFlying() || SLAMMING.contains(id)) {
            return;
        }
        int used = AIR_JUMPS.getOrDefault(id, 0);
        if (used >= 2 || p.tickCount - LAST_JUMP.getOrDefault(id, -100) < 6) {
            return;
        }
        AIR_JUMPS.put(id, used + 1);
        LAST_JUMP.put(id, p.tickCount);
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 dm = p.getDeltaMovement();
        p.setDeltaMovement(dm.x * 0.6D + look.x * 0.9D, 1.3D, dm.z * 0.6D + look.z * 0.9D);
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        VfxPacket.of(VfxType.MOON_LEAP, p.position()).color(COLOR).scale(1.5F).duration(26).param(used).send(level);
        ModSounds.play(level, p.position(), ModSounds.LUNAR, 2.2F, 1.0F + used * 0.25F);
        // chaque saut projette tout ce qui est autour de toi
        for (LivingEntity e : Abilities.around(level, p, p.position().add(0, 0.5D, 0), 6.0D)) {
            Abilities.hurt(level, e, p, 12.0F);
            Abilities.knock(e, p.position(), 1.6D, 0.9D);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) {
            return;
        }
        UUID id = sp.getUUID();
        if (!LunarBootsItem.wornBy(sp)) {
            SLAMMING.remove(id);
            AIR_JUMPS.remove(id);
            SNEAKING.remove(id);
            return;
        }
        ServerLevel level = sp.serverLevel();
        boolean diving = SLAMMING.contains(id);
        if (sp.tickCount % 20 == 0 && !diving) {
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false));
            sp.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 3, true, false));
        }
        if (sp.onGround()) {
            AIR_JUMPS.remove(id);
        }
        boolean sneak = sp.isShiftKeyDown();
        boolean edge = sneak && !SNEAKING.contains(id);
        if (sneak) {
            SNEAKING.add(id);
        } else {
            SNEAKING.remove(id);
        }

        if (diving) {
            if (sp.onGround() || sp.isInWaterOrBubble() || sp.isInLava()) {
                SLAMMING.remove(id);
                impact(level, sp);
            } else {
                Vec3 dm = sp.getDeltaMovement();
                sp.setDeltaMovement(dm.x * 0.3D, Math.min(dm.y, -2.4D), dm.z * 0.3D);
                sp.hurtMarked = true;
                sp.fallDistance = 0.0F;
            }
        } else if (edge && !sp.onGround() && !sp.isInWaterOrBubble() && !sp.isInLava() && !sp.isFallFlying()
                && !sp.getAbilities().flying) {
            Vec3 ground = TargetUtil.groundBelow(level, sp.position(), sp);
            if (sp.getY() - ground.y >= 3.0D) {
                SLAMMING.add(id);
                sp.removeEffect(MobEffects.SLOW_FALLING);
                sp.setDeltaMovement(0.0D, -2.6D, 0.0D);
                sp.hurtMarked = true;
                VfxPacket.of(VfxType.MOON_SLAM, sp.position()).follow(sp).param(0.0F).color(COLOR).scale(1.4F).duration(90).send(level);
                ModSounds.play(level, sp.position(), ModSounds.WHIRL, 2.0F, 0.6F);
            }
        }
    }

    /**
     * Impact lunaire : une onde de 16 blocs (60 degats), un cratere de 8 blocs, une explosion et une couronne de
     * petits cratères. Le porteur ne subit rien.
     */
    private static void impact(ServerLevel level, ServerPlayer p) {
        Vec3 pos = p.position();
        VfxPacket.of(VfxType.CANCEL, pos).follow(p).param(VfxType.MOON_SLAM.ordinal()).send(level);
        VfxPacket.of(VfxType.MOON_SLAM, pos).param(1.0F).color(COLOR).scale(1.8F).duration(48).send(level);
        ModSounds.play(level, pos, ModSounds.QUAKE, 8.0F, 0.9F);
        ModSounds.play(level, pos, ModSounds.BOOM, 8.0F, 0.8F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 4.0F, 0.5F);
        Abilities.shield(p, 40);
        for (LivingEntity e : Abilities.around(level, p, pos.add(0, 1.0D, 0), 16.0D)) {
            Abilities.hurt(level, e, p, 60.0F);
            Abilities.knock(e, pos, 3.0D, 1.2D);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
        }
        Abilities.boom(level, p, pos.add(0, 0.5D, 0), 7.0F, 0.0D);
        if (OmniConfig.TERRAIN_DESTRUCTION.get()) {
            Abilities.crater(level, pos.add(0, -1.0D, 0), 8.0D);
            for (int i = 0; i < 7; i++) {
                double ang = (i + level.random.nextDouble() * 0.7D) * Math.PI * 2.0D / 7.0D;
                double rad = 9.0D + level.random.nextDouble() * 5.0D;
                double x = pos.x + Math.cos(ang) * rad;
                double z = pos.z + Math.sin(ang) * rad;
                Vec3 q = new Vec3(x, Abilities.surface(level, x, z, pos.y), z);
                Abilities.boom(level, p, q, 2.5F, 2.8D);
            }
        }
        p.fallDistance = 0.0F;
    }

    /** Les bottes annulent les degats de chute. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (LunarBootsItem.wornBy(event.getEntity())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    private MoonPowers() {}
}
