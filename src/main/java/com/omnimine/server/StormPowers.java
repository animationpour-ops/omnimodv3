package com.omnimine.server;

import com.omnimine.Omnimine;
import com.omnimine.OmniConfig;
import com.omnimine.item.LightningArmorItem;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModItems;
import com.omnimine.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eclairs : piece d'armure donnee par un eclair mine, et les trois pouvoirs de l'armure d'eclair.
 * Casque : foudre en chaine a chaque coup (jusqu'a 10 cibles, chacune frappee et soufflee).
 * Plastron : decharge statique de 14 blocs quand on te frappe, avec des foudres qui explosent autour de toi.
 * Jambieres : course foudroyante (Vitesse IV en sprint + 60 % permanents), qui defonce tout ce qui est devant.
 */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class StormPowers {
    public static final int COLOR = 0xBFE8FF;
    private static final Map<UUID, Long> NEXT_CHAIN = new HashMap<>();
    private static final Map<UUID, Long> NEXT_NOVA = new HashMap<>();

    // ------------------------------------------------------------ eclair mine : piece d'armure

    /** La prochaine piece d'armure donnee par un eclair mine : une piece qu'on n'a pas encore, sinon au hasard. */
    public static Item nextPiece(ServerPlayer p) {
        List<Item> all = List.of(ModItems.LIGHTNING_HELMET.get(), ModItems.LIGHTNING_CHESTPLATE.get(),
                ModItems.LIGHTNING_LEGGINGS.get());
        List<Item> missing = new ArrayList<>();
        for (Item it : all) {
            boolean has = false;
            for (ItemStack s : p.getInventory().items) {
                has |= s.is(it);
            }
            for (ItemStack s : p.getInventory().armor) {
                has |= s.is(it);
            }
            if (!has) {
                missing.add(it);
            }
        }
        List<Item> pool = missing.isEmpty() ? all : missing;
        return pool.get(p.getRandom().nextInt(pool.size()));
    }

    // ------------------------------------------------------- casque : foudre en chaine

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        DamageSource src = event.getSource();
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        // casque : un coup au corps a corps declenche la foudre en chaine
        if (src.is(DamageTypes.PLAYER_ATTACK) && src.getEntity() instanceof ServerPlayer p
                && LightningArmorItem.wears(p, EquipmentSlot.HEAD)
                && level.getGameTime() >= NEXT_CHAIN.getOrDefault(p.getUUID(), 0L)) {
            NEXT_CHAIN.put(p.getUUID(), level.getGameTime() + 14L);
            event.setAmount(event.getAmount() + 12.0F);
            chain(level, p, victim);
        }
        // plastron : decharge statique quand le porteur est frappe par une creature
        if (victim instanceof ServerPlayer wearer && LightningArmorItem.wears(wearer, EquipmentSlot.CHEST)
                && src.getEntity() instanceof LivingEntity && src.getEntity() != wearer
                && level.getGameTime() >= NEXT_NOVA.getOrDefault(wearer.getUUID(), 0L)) {
            NEXT_NOVA.put(wearer.getUUID(), level.getGameTime() + 40L);
            nova(level, wearer);
        }
    }

    private static Vec3 center(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5D, 0);
    }

    /** La foudre frappe la premiere cible, puis saute de proche en proche (9 fois, 13 blocs) en soufflant chacune. */
    private static void chain(ServerLevel level, ServerPlayer p, LivingEntity first) {
        Vec3 ground = first.position();
        VfxPacket.of(VfxType.SMITE, ground).param(1.0F).color(COLOR).duration(26).send(level);
        ModSounds.play(level, ground, ModSounds.THUNDER, 4.0F, 1.2F);
        zap(level, p, first, 16.0F);
        Abilities.boom(level, p, center(first), 2.2F, 0.0D);
        List<LivingEntity> hit = new ArrayList<>();
        hit.add(first);
        LivingEntity cur = first;
        for (int i = 0; i < 9; i++) {
            LivingEntity next = null;
            double best = 1.0E9D;
            for (LivingEntity e : Abilities.around(level, p, center(cur), 13.0D)) {
                double d = e.distanceToSqr(cur);
                if (!hit.contains(e) && d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            hit.add(next);
            LivingEntity a = cur;
            LivingEntity b = next;
            ServerEffects.delay(level, 3 * (i + 1), () -> {
                VfxPacket.of(VfxType.ARC, center(a)).to(center(b)).color(COLOR).duration(12).send(level);
                ModSounds.play(level, center(b), ModSounds.ZAP, 2.5F, 1.0F + level.random.nextFloat() * 0.4F);
                zap(level, p, b, 16.0F);
                Abilities.boom(level, p, center(b), 2.2F, 0.0D);
            });
            cur = next;
        }
    }

    private static void zap(ServerLevel level, ServerPlayer p, LivingEntity e, float damage) {
        if (!e.isAlive()) {
            return;
        }
        Abilities.hurt(level, e, p, damage);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
    }

    // ------------------------------------------------------- plastron : decharge statique

    /** Decharge : tout est projete et foudroye dans 14 blocs, puis huit foudres explosent au hasard autour. */
    private static void nova(ServerLevel level, ServerPlayer wearer) {
        Vec3 c = wearer.position().add(0, 1.0D, 0);
        VfxPacket.of(VfxType.NOVA, c).follow(wearer).color(COLOR).param(14.0F).duration(32).send(level);
        ModSounds.play(level, c, ModSounds.THUNDER, 6.0F, 1.4F);
        ModSounds.play(level, c, ModSounds.PULSE, 4.0F, 1.6F);
        for (LivingEntity e : Abilities.around(level, wearer, c, 14.0D)) {
            Abilities.hurt(level, e, wearer, 30.0F);
            Abilities.knock(e, c, 3.2D, 1.0D);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
        }
        for (int i = 0; i < 8; i++) {
            ServerEffects.delay(level, 2 + 2 * i, () -> {
                double ang = level.random.nextDouble() * Math.PI * 2.0D;
                double rad = 4.0D + level.random.nextDouble() * 9.0D;
                double x = c.x + Math.cos(ang) * rad;
                double z = c.z + Math.sin(ang) * rad;
                Vec3 q = new Vec3(x, Abilities.surface(level, x, z, c.y - 1.0D), z);
                VfxPacket.of(VfxType.SMITE, q).param(0.5F).color(COLOR).duration(22).send(level);
                ModSounds.play(level, q, ModSounds.THUNDER, 2.5F, 1.3F);
                Abilities.boom(level, wearer, q, 2.5F, 2.2D);
            });
        }
    }

    /** Le plastron rend insensible a la foudre. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().is(DamageTypes.LIGHTNING_BOLT) && event.getEntity() instanceof ServerPlayer p
                && LightningArmorItem.wears(p, EquipmentSlot.CHEST)) {
            event.setCanceled(true);
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, true, false));
            p.clearFire();
        }
    }

    // ------------------------------------------------------ jambieres : course foudroyante

    /**
     * Les jambieres donnent +60 % de vitesse en permanence (attribut, voir LightningArmorItem) ; en sprint s'y
     * ajoute Vitesse IV, et le porteur devient un projectile : il foudroie ce qu'il frole, defonce les murs devant
     * lui et attire la foudre sur l'ennemi le plus proche.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)
                || !LightningArmorItem.wears(sp, EquipmentSlot.LEGS) || !sp.isSprinting()) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        int tick = sp.tickCount;
        if (tick % 4 == 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 3, true, false));
        }
        Vec3 dir = sp.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (tick % 3 == 0) {
            VfxPacket.of(VfxType.ZAP, sp.position()).to(dir).color(COLOR).scale(1.8F).duration(16).send(level);
        }
        if (tick % 6 == 0) {
            ModSounds.play(level, sp.position(), ModSounds.ZAP, 1.6F, 0.9F + level.random.nextFloat() * 0.5F);
        }
        if (OmniConfig.TERRAIN_DESTRUCTION.get()) {
            smash(level, sp);
        }
        if (tick % 4 == 0) {
            Vec3 c = sp.position().add(0, 0.9D, 0);
            for (LivingEntity e : Abilities.around(level, sp, c, 3.5D)) {
                Abilities.hurt(level, e, sp, 14.0F);
                Abilities.knock(e, sp.position(), 2.2D, 0.6D);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                e.setSecondsOnFire(3);
            }
        }
        // toutes les 0,75 s la foudre tombe sur l'ennemi le plus proche
        if (tick % 15 == 0) {
            LivingEntity target = null;
            double best = 1.0E9D;
            for (LivingEntity e : Abilities.around(level, sp, sp.position(), 16.0D)) {
                double d = e.distanceToSqr(sp);
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
            if (target != null) {
                VfxPacket.of(VfxType.MINI, target.position()).color(COLOR).scale(1.8F).duration(16).send(level);
                ModSounds.play(level, target.position(), ModSounds.THUNDER, 2.5F, 1.5F);
                zap(level, sp, target, 22.0F);
                Abilities.boom(level, sp, center(target), 2.5F, 0.0D);
            }
        }
    }

    /**
     * Le sprinteur defonce tout ce qui se trouve devant lui : une zone de trois blocs de large et trois de haut,
     * de 0,6 a 2,4 blocs devant lui (jamais le sol sous ses pieds). Pas de drops : il passe, c'est tout.
     */
    private static void smash(ServerLevel level, ServerPlayer sp) {
        Vec3 look = sp.getLookAngle();
        Vec3 h = new Vec3(look.x, 0.0D, look.z);
        if (h.lengthSqr() < 1.0E-4D) {
            return;
        }
        h = h.normalize();
        Vec3 side = new Vec3(-h.z, 0.0D, h.x);
        int baseY = (int) Math.floor(sp.getY() + 0.05D);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int smashed = 0;
        for (double ahead = 0.6D; ahead <= 2.4D; ahead += 0.6D) {
            for (double off = -0.9D; off <= 0.9D; off += 0.9D) {
                double x = sp.getX() + h.x * ahead + side.x * off;
                double z = sp.getZ() + h.z * ahead + side.z * off;
                for (int dy = 0; dy < 3; dy++) {
                    m.set((int) Math.floor(x), baseY + dy, (int) Math.floor(z));
                    BlockState s = level.getBlockState(m);
                    if (Abilities.erasable(level, m, s)) {
                        Abilities.erase(level, m.immutable());
                        smashed++;
                    }
                }
            }
        }
        if (smashed > 0 && sp.tickCount % 2 == 0) {
            ModSounds.play(level, sp.position().add(h.scale(1.5D)), SoundEvents.GENERIC_EXPLODE, 0.8F,
                    1.4F + level.random.nextFloat() * 0.4F);
        }
    }

    private StormPowers() {}
}
