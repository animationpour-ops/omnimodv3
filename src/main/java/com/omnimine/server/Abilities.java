package com.omnimine.server;

import com.omnimine.OmniConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

/** Outils communs aux pouvoirs : cibles valides, degats, recul, effacement de blocs, aimant a objets. */
public final class Abilities {

    public static boolean validTarget(Entity owner, Entity e) {
        if (!(e instanceof LivingEntity le) || e == owner || !le.isAlive() || e.isSpectator()
                || e instanceof ArmorStand) {
            return false;
        }
        return !(e instanceof TamableAnimal pet && owner instanceof LivingEntity o && pet.isOwnedBy(o));
    }

    public static List<LivingEntity> around(ServerLevel level, Entity owner, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> validTarget(owner, x))) {
            double r = radius + e.getBbWidth() * 0.5;
            if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= r * r) {
                out.add(e);
            }
        }
        return out;
    }

    public static void hurt(ServerLevel level, LivingEntity target, Entity owner, float damage) {
        DamageSource src = owner instanceof LivingEntity
                ? level.damageSources().indirectMagic(owner, owner)
                : level.damageSources().magic();
        target.invulnerableTime = 0;
        target.hurt(src, damage);
    }

    public static void knock(Entity e, Vec3 from, double horizontal, double vertical) {
        Vec3 d = e.position().subtract(from);
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 1.0E-4) {
            d = new Vec3(1, 0, 0);
        }
        d = d.normalize().scale(horizontal);
        e.setDeltaMovement(e.getDeltaMovement().add(d.x, vertical, d.z));
        e.hurtMarked = true;
    }

    /** Tire une entite vers un point. */
    public static void pull(Entity e, Vec3 to, double strength, double lift) {
        Vec3 d = to.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
        if (d.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 v = d.normalize().scale(strength);
        e.setDeltaMovement(e.getDeltaMovement().scale(0.5D).add(v.x, v.y + lift, v.z));
        e.hurtMarked = true;
        e.fallDistance = 0.0F;
    }

    /** Un bloc peut-il etre efface ? (ni incassable, ni coffre/four...). */
    public static boolean erasable(ServerLevel level, BlockPos p, BlockState s) {
        return !s.isAir() && s.getDestroySpeed(level, p) >= 0.0F && level.getBlockEntity(p) == null;
    }

    /** Efface un bloc sans drop ni particule (le Neant l'avale). */
    public static void erase(ServerLevel level, BlockPos p) {
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    /** Efface une sphere de blocs. Renvoie le nombre de blocs effaces. */
    public static int eraseSphere(ServerLevel level, Vec3 center, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (erasable(level, p, s)) {
                        erase(level, p);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Rend le joueur insensible un instant (ses propres explosions ne le blessent pas). */
    public static void shield(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 4, true, false));
    }

    /**
     * Hauteur du sol (premier bloc plein qui a de l'air au-dessus) pres de nearY, a la verticale de (x, z).
     * Renvoie nearY si rien n'est trouve.
     */
    public static double surface(ServerLevel level, double x, double z, double nearY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos((int) Math.floor(x), (int) Math.floor(nearY) + 6,
                (int) Math.floor(z));
        for (int i = 0; i < 18; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.isAir() && level.getBlockState(m.above()).isAir()) {
                return m.getY() + 1.0D;
            }
            m.move(0, -1, 0);
        }
        return nearY;
    }

    /**
     * Une vraie explosion Minecraft (souffle, degats, recul, son) PLUS un cratere irregulier creuse par le mod.
     * Le cratere n'est creuse que si la config autorise la destruction du decor, et ne fait pas tomber d'objets
     * (une explosion enorme a la TNT ferait apparaitre des milliers d'objets et ferait ramer le jeu).
     */
    public static void boom(ServerLevel level, Entity source, Vec3 c, float power, double craterRadius) {
        level.explode(source, c.x, c.y, c.z, power, Level.ExplosionInteraction.NONE);
        if (craterRadius > 0.0D && OmniConfig.TERRAIN_DESTRUCTION.get()) {
            crater(level, c, craterRadius);
        }
    }

    /** Cratere : sphere dont le bord est un peu dechiquete, sans objets au sol. Renvoie le nombre de blocs effaces. */
    public static int crater(ServerLevel level, Vec3 center, double radius) {
        int r = (int) Math.ceil(radius * 1.15D);
        BlockPos c = BlockPos.containing(center);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double edge = radius * (0.88D + level.random.nextDouble() * 0.27D);
                    if (dx * dx + dy * dy + dz * dz > edge * edge) {
                        continue;
                    }
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (erasable(level, m, s)) {
                        erase(level, m.immutable());
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Un bloc fragile : verre, feuilles, plantes, torches... (tout ce qui casse instantanement). */
    public static boolean fragile(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return false;
        }
        return s.is(Tags.Blocks.GLASS) || s.is(Tags.Blocks.GLASS_PANES) || s.is(BlockTags.LEAVES)
                || s.getDestroySpeed(level, p) == 0.0F;
    }

    /** Fait voler tous les objets au sol d'une zone jusque dans l'inventaire du joueur. */
    public static void magnet(ServerLevel level, ServerPlayer player, AABB area) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            item.setPos(player.getX(), player.getY() + 0.4D, player.getZ());
            item.setDeltaMovement(Vec3.ZERO);
            item.setPickUpDelay(0);
        }
    }

    private Abilities() {}
}
