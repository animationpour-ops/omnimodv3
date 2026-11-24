package com.omnimine.server;

import com.omnimine.OmniConfig;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModSounds;
import com.omnimine.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Les deux pouvoirs de l'epee du soleil : eruption a chaque coup, rayon solaire orbital au clic droit. */
public final class SunPowers {
    public static final int COLOR = 0xFFA82E;
    private static final int CHARGE = 30;
    private static final int TOTAL = 66;

    /**
     * Pouvoir 1 : chaque coup embrase la cible et declenche une eruption solaire de 7 blocs : degats, flammes,
     * projection, et des feux qui se propagent au decor. Le porteur ne craint pas le feu.
     */
    public static void hit(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        Vec3 t = target.position().add(0, target.getBbHeight() * 0.5D, 0);
        target.setSecondsOnFire(15);
        Abilities.hurt(level, target, attacker, 10.0F);
        Abilities.knock(target, attacker.position(), 0.9D, 0.5D);
        attacker.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0, true, false));
        VfxPacket.of(VfxType.SUN_FLARE, t).color(COLOR).scale(2.0F).duration(26).send(level);
        ModSounds.play(level, t, ModSounds.FLAME, 2.2F, 0.9F + level.random.nextFloat() * 0.3F);
        level.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 1.4F);
        for (LivingEntity e : Abilities.around(level, attacker, t, 7.0D)) {
            if (e == target) {
                continue;
            }
            e.setSecondsOnFire(10);
            Abilities.hurt(level, e, attacker, 16.0F);
            Abilities.knock(e, t, 1.4D, 0.8D);
        }
        if (OmniConfig.TERRAIN_DESTRUCTION.get()) {
            igniteGround(level, t, 5.0D, 10);
        }
    }

    /** Pouvoir 2 : un rayon du soleil tombe sur la cible (jusqu'a 96 blocs), puis un cratere en fusion reste en feu. */
    public static boolean beam(ServerLevel level, ServerPlayer p, ItemStack stack, InteractionHand hand) {
        Vec3 target = TargetUtil.groundTarget(p, 96.0D);
        VfxPacket.of(VfxType.SUN_BEAM, target).duration(TOTAL).color(COLOR).scale(1.65F).send(level);
        ModSounds.play(level, p.position(), ModSounds.FORGE_CHARGE, 3.0F, 1.5F);
        ModSounds.play(level, target, ModSounds.SOLAR, 10.0F, 1.0F);
        ServerEffects.add(level, new Beam(p, target));
        return true;
    }

    private static final class Beam implements ServerEffects.Timed {
        private final ServerPlayer owner;
        private final Vec3 c;
        private int t;

        Beam(ServerPlayer owner, Vec3 c) {
            this.owner = owner;
            this.c = c;
        }

        @Override
        public boolean tick(ServerLevel level) {
            t++;
            if (t == CHARGE - 4) {
                Abilities.shield(owner, 100);
            }
            if (t == CHARGE) {
                impact(level);
            } else if (t > CHARGE && t % 4 == 0) {
                for (LivingEntity e : Abilities.around(level, owner, c, 14.0D)) {
                    e.setSecondsOnFire(8);
                    Abilities.hurt(level, e, owner, 9.0F);
                }
            }
            return t >= TOTAL;
        }

        private void impact(ServerLevel level) {
            for (LivingEntity e : Abilities.around(level, owner, c, 18.0D)) {
                e.setSecondsOnFire(20);
                Abilities.hurt(level, e, owner, 90.0F);
                Abilities.knock(e, c, 3.5D, 1.5D);
            }
            ModSounds.play(level, c, ModSounds.BOOM, 10.0F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0F, 0.5F);
            Abilities.boom(level, owner, c.add(0.0D, 1.0D, 0.0D), 9.0F, 0.0D);
            if (OmniConfig.TERRAIN_DESTRUCTION.get()) {
                crater(level, c);
            }
        }
    }

    /** Cratere de 15 blocs : le coeur est vaporise, le pourtour devient magma et braises, le tout en feu. */
    private static void crater(ServerLevel level, Vec3 c) {
        BlockPos center = BlockPos.containing(c);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = 15;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    // bord irregulier : la distance est un peu truquee au hasard
                    double d2 = (dx * dx + dy * dy * 1.4D + dz * dz) * (0.88D + level.random.nextDouble() * 0.24D);
                    if (d2 > r * r) {
                        continue;
                    }
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir() || !Abilities.erasable(level, m, s)) {
                        continue;
                    }
                    if (d2 <= 110.0D) {
                        Abilities.erase(level, m.immutable());
                    } else if (dy <= 0 && level.random.nextFloat() < 0.35F) {
                        level.setBlock(m.immutable(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
        // braises sur tout le pourtour (elles se propagent aux arbres et aux maisons)
        igniteGround(level, c, 16.0D, 170);
    }

    /** Allume `count` feux au hasard sur le sol autour de c, dans un rayon donne. */
    private static void igniteGround(ServerLevel level, Vec3 c, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0D;
            double rad = level.random.nextDouble() * radius;
            double x = c.x + Math.cos(ang) * rad;
            double z = c.z + Math.sin(ang) * rad;
            BlockPos above = BlockPos.containing(x, Abilities.surface(level, x, z, c.y), z);
            if (level.getBlockState(above).isAir()) {
                BlockState fire = BaseFireBlock.getState(level, above);
                if (!fire.isAir()) {
                    level.setBlock(above, fire, 3);
                }
            }
        }
    }

    private SunPowers() {}
}
