package com.omnimine.server;

import com.omnimine.OmniConfig;
import com.omnimine.data.MobKind;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModSounds;
import com.omnimine.util.TargetUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Le pouvoir de chacun des huit mobs mines (clic droit) : petits a recuperer, enormes a lancer. */
public final class MobPowers {

    public static boolean use(MobKind kind, ServerLevel level, ServerPlayer p) {
        return switch (kind) {
            case SKELETON -> boneStorm(level, p);
            case CREEPER -> blast(level, p);
            case ZOMBIE -> rot(level, p);
            case SPIDER -> web(level, p);
            case GUARDIAN -> laser(level, p);
            case VILLAGER -> sonic(level, p);
            case IRON_GOLEM -> quake(level, p);
            case ENDERMAN -> warp(level, p);
        };
    }

    private static boolean destructive() {
        return OmniConfig.TERRAIN_DESTRUCTION.get();
    }

    // ------------------------------------------------------------- squelette

    /**
     * Tempete d'os : pendant trois secondes une pluie d'os s'abat sur une zone de 11 blocs de rayon. Chaque os qui
     * se plante fait un petit cratere ; la zone finit dans une grosse explosion.
     */
    private static boolean boneStorm(ServerLevel level, ServerPlayer p) {
        final double radius = 11.0D;
        Vec3 c = TargetUtil.groundTarget(p, 48.0D);
        VfxPacket.of(VfxType.BONE_RAIN, c).param((float) radius).color(MobKind.SKELETON.color).duration(84).send(level);
        ModSounds.play(level, c, SoundEvents.SKELETON_SHOOT, 4.0F, 0.6F);
        ModSounds.play(level, c, ModSounds.WHIRL, 3.0F, 1.4F);
        Abilities.shield(p, 90);
        ServerEffects.repeat(level, 3, 22, i -> {
            for (LivingEntity e : Abilities.around(level, p, c, radius)) {
                Abilities.hurt(level, e, p, 8.0F);
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.4D, 1.0D, 0.4D));
            }
            for (int k = 0; k < 2; k++) {
                double ang = level.random.nextDouble() * Math.PI * 2.0D;
                double rad = Math.sqrt(level.random.nextDouble()) * radius;
                double x = c.x + Math.cos(ang) * rad;
                double z = c.z + Math.sin(ang) * rad;
                Vec3 hit = new Vec3(x, Abilities.surface(level, x, z, c.y), z);
                Abilities.boom(level, p, hit, 1.3F, 2.3D);
                ModSounds.play(level, hit, SoundEvents.BONE_BLOCK_BREAK, 1.6F, 0.8F + level.random.nextFloat() * 0.6F);
            }
        });
        ServerEffects.delay(level, 70, () -> {
            for (LivingEntity e : Abilities.around(level, p, c, radius + 2.0D)) {
                Abilities.hurt(level, e, p, 20.0F);
                Abilities.knock(e, c, 1.6D, 1.0D);
            }
            Abilities.boom(level, p, c, 4.0F, 6.0D);
            ModSounds.play(level, c, ModSounds.BOOM, 6.0F, 1.0F);
        });
        return true;
    }

    // --------------------------------------------------------------- creeper

    /**
     * Explosion verte : la zone se charge, puis une explosion de puissance 14 (la TNT fait 4) creuse un cratere
     * de 11 blocs, suivie d'une couronne de huit explosions secondaires.
     */
    private static boolean blast(ServerLevel level, ServerPlayer p) {
        Vec3 c = TargetUtil.groundTarget(p, 40.0D);
        VfxPacket.of(VfxType.BLAST, c).color(MobKind.CREEPER.color).scale(2.0F).duration(80).send(level);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 4.0F, 0.6F);
        ServerEffects.delay(level, 24, () -> Abilities.shield(p, 110));
        ServerEffects.delay(level, 28, () -> {
            Abilities.boom(level, p, c.add(0.0D, 0.5D, 0.0D), 14.0F, 11.0D);
            ModSounds.play(level, c, ModSounds.BOOM, 10.0F, 0.7F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0F, 0.5F);
            for (LivingEntity e : Abilities.around(level, p, c, 24.0D)) {
                Abilities.knock(e, c, 2.4D, 1.2D);
            }
            ServerEffects.repeat(level, 2, 8, i -> {
                double ang = (i + level.random.nextDouble() * 0.6D) * Math.PI * 2.0D / 8.0D;
                double rad = 9.0D + level.random.nextDouble() * 5.0D;
                double x = c.x + Math.cos(ang) * rad;
                double z = c.z + Math.sin(ang) * rad;
                Vec3 q = new Vec3(x, Abilities.surface(level, x, z, c.y), z);
                Abilities.boom(level, p, q, 5.0F, 5.0D);
                ModSounds.play(level, q, ModSounds.BOOM, 5.0F, 0.9F + level.random.nextFloat() * 0.3F);
            });
        });
        return true;
    }

    // ---------------------------------------------------------------- zombie

    /**
     * Souffle de peste : un cone de 22 blocs de brume putride. Les ennemis prennent de gros degats, le Wither, la
     * faiblesse et la lenteur ; la vegetation du cone (feuilles, herbe, cultures) fane ; tu te soignes.
     */
    private static boolean rot(ServerLevel level, ServerPlayer p) {
        final double length = 22.0D;
        final int pulses = 14;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        VfxPacket.of(VfxType.ROT, eye).to(eye.add(look.scale(length))).color(MobKind.ZOMBIE.color).duration(70).send(level);
        ModSounds.play(level, eye, SoundEvents.ZOMBIE_AMBIENT, 3.0F, 0.5F);
        ModSounds.play(level, eye, SoundEvents.ZOMBIE_VILLAGER_CURE, 2.0F, 0.6F);
        ServerEffects.repeat(level, 4, pulses, i -> {
            Vec3 mid = eye.add(look.scale(length * 0.5D));
            int infected = 0;
            for (LivingEntity e : Abilities.around(level, p, mid, 18.0D)) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5D, 0).subtract(eye);
                double dist = to.length();
                if (dist > length || dist < 0.4D || to.normalize().dot(look) < 0.86D) {
                    continue;
                }
                Abilities.hurt(level, e, p, 6.0F);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 2));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 2));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                infected++;
            }
            if (infected > 0) {
                p.heal(Math.min(6, infected * 2));
            }
            if (destructive()) {
                witherPlants(level, eye, look, length * i / pulses, length * (i + 1) / pulses);
            }
        });
        return true;
    }

    /** Fait faner la vegetation du cone entre les distances d0 et d1 : plantes, feuilles, cultures, herbe. */
    private static void witherPlants(ServerLevel level, Vec3 eye, Vec3 look, double d0, double d1) {
        Vec3 mid = eye.add(look.scale((d0 + d1) * 0.5D));
        int r = (int) Math.ceil(d1 * 0.62D + (d1 - d0) * 0.5D);
        BlockPos c = BlockPos.containing(mid);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) {
                        continue;
                    }
                    boolean leaves = s.is(BlockTags.LEAVES);
                    boolean grass = s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH);
                    if (!leaves && !grass && !s.is(BlockTags.FLOWERS) && !s.is(BlockTags.CROPS)
                            && !s.is(BlockTags.SAPLINGS) && !s.is(Blocks.GRASS) && !s.is(Blocks.TALL_GRASS)
                            && !s.is(Blocks.FERN) && !s.is(Blocks.LARGE_FERN) && !s.is(Blocks.VINE)) {
                        continue;
                    }
                    Vec3 to = Vec3.atCenterOf(m).subtract(eye);
                    double dist = to.length();
                    if (dist < d0 || dist >= d1 || dist < 0.5D || to.scale(1.0D / dist).dot(look) < 0.86D) {
                        continue;
                    }
                    if (grass) {
                        level.setBlock(m.immutable(), Blocks.DIRT.defaultBlockState(), 2 | 16);
                    } else {
                        Abilities.erase(level, m.immutable());
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------- araignee

    /** Toile geante : 260 fils s'abattent sur 10 blocs de rayon, les ennemis sont attires, engluees et mordus. */
    private static boolean web(ServerLevel level, ServerPlayer p) {
        final double radius = 10.0D;
        Vec3 c = TargetUtil.groundTarget(p, 36.0D);
        VfxPacket.of(VfxType.WEB, c).to(p.getEyePosition().add(0, -0.3D, 0)).param((float) radius + 0.5F)
                .color(MobKind.SPIDER.color).duration(130).send(level);
        ModSounds.play(level, c, SoundEvents.SPIDER_AMBIENT, 3.0F, 0.6F);
        ModSounds.play(level, c, SoundEvents.WOOL_PLACE, 3.0F, 0.5F);
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < 260; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0D;
            double rad = Math.sqrt(level.random.nextDouble()) * radius;
            BlockPos pos = BlockPos.containing(c.x + Math.cos(ang) * rad, c.y, c.z + Math.sin(ang) * rad)
                    .above(level.random.nextInt(4));
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
                placed.add(pos);
            }
        }
        ServerEffects.delay(level, 240, () -> {
            for (BlockPos pos : placed) {
                if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        });
        ServerEffects.repeat(level, 5, 12, i -> {
            for (LivingEntity e : Abilities.around(level, p, c, radius + 1.0D)) {
                Abilities.pull(e, c.add(0, 1.0D, 0), 0.55D, 0.05D);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4));
                if (i == 0) {
                    Abilities.hurt(level, e, p, 14.0F);
                } else if (i == 6) {
                    Abilities.hurt(level, e, p, 10.0F);
                }
            }
        });
        return true;
    }

    // ---------------------------------------------------------------- gardien

    /**
     * Rayon du gardien : il se charge puis perce tout sur 60 blocs. Un tunnel de plus de 6 blocs de large est
     * creuse dans le decor, les ennemis prennent 40 degats, et le bout du rayon explose.
     */
    private static boolean laser(ServerLevel level, ServerPlayer p) {
        final double step = 5.0D;
        final int steps = 12;
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 end = eye.add(dir.scale(step * steps));
        VfxPacket.of(VfxType.LASER, eye).to(end).color(MobKind.GUARDIAN.color).param(2.3F).duration(68).send(level);
        ModSounds.play(level, eye, SoundEvents.GUARDIAN_ATTACK, 3.0F, 0.6F);
        ModSounds.play(level, eye, ModSounds.LASER, 4.0F, 1.0F);
        ServerEffects.delay(level, 30, () -> ServerEffects.repeat(level, 3, steps, k -> {
            double s0 = Math.max(2.0D, k * step);
            double s1 = (k + 1) * step;
            Vec3 a = eye.add(dir.scale(s0));
            Vec3 b = eye.add(dir.scale(s1));
            AABB box = new AABB(a, b).inflate(4.0D);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Abilities.validTarget(p, x))) {
                if (e.getBoundingBox().inflate(2.0D).clip(a, b).isPresent()) {
                    Abilities.hurt(level, e, p, 40.0F);
                    e.setSecondsOnFire(8);
                }
            }
            if (destructive()) {
                // on ne creuse pas a moins de 5 blocs du joueur (il ne tombe pas dans son propre tunnel)
                for (double t = Math.max(s0, 5.0D); t <= s1; t += 1.2D) {
                    Abilities.eraseSphere(level, eye.add(dir.scale(t)), 3.2D);
                }
            }
            ModSounds.play(level, b, SoundEvents.GUARDIAN_ATTACK, 2.5F, 1.2F);
            if (k == steps - 1) {
                Abilities.boom(level, p, b, 5.0F, 6.0D);
                ModSounds.play(level, b, ModSounds.BOOM, 6.0F, 0.9F);
            }
        }));
        return true;
    }

    // -------------------------------------------------------------- villageois

    /**
     * Cri du village : trois ondes sonores jusqu'a 20 blocs repoussent tout, brisent le verre, les feuilles et
     * les plantes, et font cracher des emeraudes aux monstres.
     */
    private static boolean sonic(ServerLevel level, ServerPlayer p) {
        Vec3 c = p.position().add(0, 1.0D, 0);
        VfxPacket.of(VfxType.SONIC, c).follow(p).color(MobKind.VILLAGER.color).duration(60).send(level);
        ServerEffects.repeat(level, 5, 3, i -> {
            double inner = i == 0 ? 0.0D : 7.0D + (i - 1) * 6.5D;
            double outer = 7.0D + i * 6.5D;
            ModSounds.play(level, c, SoundEvents.VILLAGER_NO, 4.0F, 0.5F + i * 0.25F);
            ModSounds.play(level, c, ModSounds.PULSE, 4.0F, 0.8F + i * 0.2F);
            for (LivingEntity e : Abilities.around(level, p, c, outer)) {
                if (e.position().distanceTo(c) < inner) {
                    continue;
                }
                Abilities.hurt(level, e, p, 14.0F);
                Abilities.knock(e, c, 3.0D, 1.0D);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
                if (e instanceof Enemy) {
                    level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.5D, e.getZ(),
                            new ItemStack(Items.EMERALD, 1 + level.random.nextInt(3))));
                }
            }
            if (destructive()) {
                shatterFragile(level, c, outer);
            }
        });
        return true;
    }

    /** Brise tout ce qui est fragile dans une sphere : verre, vitres, feuilles, plantes, torches. */
    private static void shatterFragile(ServerLevel level, Vec3 c, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos center = BlockPos.containing(c);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (!Abilities.fragile(level, m, s) || level.getBlockEntity(m) != null) {
                        continue;
                    }
                    if (s.getDestroySpeed(level, m) > 0.0F && !s.is(BlockTags.LEAVES)) {
                        level.destroyBlock(m.immutable(), false);
                    } else {
                        Abilities.erase(level, m.immutable());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- golem de fer

    /**
     * Frappe du golem : le sol se fend en douze failles de 12 blocs de profondeur jusqu'a 16 blocs, des dizaines de
     * blocs sont arraches et projetes en l'air, tout ce qui est dans la zone est catapulte.
     */
    private static boolean quake(ServerLevel level, ServerPlayer p) {
        final double radius = 16.0D;
        Vec3 c = p.position();
        VfxPacket.of(VfxType.QUAKE, c).color(MobKind.IRON_GOLEM.color).param((float) radius + 1.0F).duration(72).send(level);
        ModSounds.play(level, c, SoundEvents.IRON_GOLEM_ATTACK, 3.0F, 0.5F);
        ServerEffects.delay(level, 8, () -> {
            ModSounds.play(level, c, ModSounds.QUAKE, 8.0F, 0.8F);
            ModSounds.play(level, c, ModSounds.BOOM, 6.0F, 0.7F);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 2, true, false));
            for (LivingEntity e : Abilities.around(level, p, c, radius)) {
                Abilities.hurt(level, e, p, 40.0F);
                Abilities.knock(e, c, 1.4D, 0.0D);
                e.setDeltaMovement(e.getDeltaMovement().x, 2.0D, e.getDeltaMovement().z);
                e.hurtMarked = true;
            }
            if (destructive()) {
                splitGround(level, c, radius);
                launchBlocks(level, c, radius - 2.0D);
            }
        });
        return true;
    }

    /** Douze failles en etoile : une tranchee d'un bloc de large, profonde de 12 blocs pres du centre. */
    private static void splitGround(ServerLevel level, Vec3 c, double radius) {
        final int cracks = 12;
        for (int k = 0; k < cracks; k++) {
            double ang = k * Math.PI * 2.0D / cracks + (level.random.nextDouble() - 0.5D) * 0.25D;
            for (double dist = 1.5D; dist <= radius; dist += 1.0D) {
                double wob = (level.random.nextDouble() - 0.5D) * 1.2D;
                double x = c.x + Math.cos(ang) * dist - Math.sin(ang) * wob;
                double z = c.z + Math.sin(ang) * dist + Math.cos(ang) * wob;
                int depth = Math.max(5, (int) (12.0D - dist * 0.4D));
                BlockPos col = BlockPos.containing(x, c.y + 2.0D, z);
                int top = -1;
                for (int dy = 0; dy < 7; dy++) {
                    if (!level.getBlockState(col.below(dy)).isAir()) {
                        top = dy;
                        break;
                    }
                }
                if (top < 0) {
                    continue;
                }
                for (int dy = top; dy < top + depth; dy++) {
                    BlockPos g = col.below(dy);
                    BlockState s = level.getBlockState(g);
                    if (Abilities.erasable(level, g, s)) {
                        Abilities.erase(level, g);
                    }
                }
            }
        }
    }

    /** Arrache jusqu'a 90 blocs du sol et les envoie en l'air : ils retombent comme de vrais blocs. */
    private static void launchBlocks(ServerLevel level, Vec3 c, double radius) {
        for (int i = 0; i < 90; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0D;
            double rad = 2.0D + level.random.nextDouble() * Math.max(1.0D, radius - 2.0D);
            double x = c.x + Math.cos(ang) * rad;
            double z = c.z + Math.sin(ang) * rad;
            BlockPos g = BlockPos.containing(x, Abilities.surface(level, x, z, c.y) - 1.0D, z);
            BlockState s = level.getBlockState(g);
            if (!s.canOcclude() || !Abilities.erasable(level, g, s) || !s.getFluidState().isEmpty()) {
                continue;
            }
            FallingBlockEntity block = FallingBlockEntity.fall(level, g, s);
            double out = 0.15D + level.random.nextDouble() * 0.35D;
            block.setDeltaMovement(Math.cos(ang) * out, 0.8D + level.random.nextDouble() * 0.9D, Math.sin(ang) * out);
            block.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- enderman

    /**
     * Passage de l'ombre : tu te teleportes a 50 blocs vers ta visee en creusant un tunnel dans tout ce qui est sur le
     * chemin ; les ennemis traverses prennent 30 degats et ceux de l'arrivee sont repousses.
     */
    private static boolean warp(ServerLevel level, ServerPlayer p) {
        final double dist = 50.0D;
        Vec3 from = p.position();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 feet;
        if (destructive()) {
            // aucun mur n'arrete le passage : il est creuse
            feet = eye.add(look.scale(dist)).subtract(0, p.getEyeHeight(), 0);
        } else {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(dist)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(dist))
                    : hit.getLocation().subtract(look.scale(0.9D));
            feet = end.subtract(0, p.getEyeHeight(), 0);
            for (int i = 0; i < 8 && !level.noCollision(p, p.getBoundingBox().move(feet.subtract(from))); i++) {
                feet = feet.subtract(look.scale(0.6D)).add(0, 0.25D, 0);
            }
        }
        if (feet.distanceTo(from) < 2.0D) {
            return false;
        }
        AABB box = new AABB(from, feet).inflate(3.5D);
        Vec3 a = from.add(0, 1.0D, 0);
        Vec3 b = feet.add(0, 1.0D, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> Abilities.validTarget(p, x))) {
            if (e.getBoundingBox().inflate(2.4D).clip(a, b).isPresent()) {
                Abilities.hurt(level, e, p, 30.0F);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            }
        }
        if (destructive()) {
            // tunnel de 4 blocs de large (on commence a 3 blocs pour ne pas creuser sous les pieds du joueur)
            for (double t = 3.0D; t <= dist - 1.0D; t += 1.0D) {
                Abilities.eraseSphere(level, eye.add(look.scale(t)), 2.2D);
            }
            // de la place pour le joueur a l'arrivee (le sol sous ses pieds reste)
            Abilities.eraseSphere(level, feet.add(0, 1.3D, 0), 1.5D);
        }
        VfxPacket.of(VfxType.WARP, a).to(b).color(MobKind.ENDERMAN.color).param(3.0F).duration(44).send(level);
        ModSounds.play(level, from, SoundEvents.ENDERMAN_TELEPORT, 3.0F, 0.8F);
        ModSounds.play(level, from, ModSounds.BLINK, 3.0F, 1.1F);
        p.teleportTo(feet.x, feet.y, feet.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        p.hurtMarked = true;
        // l'arrivee peut etre en plein ciel : pas de degats de chute
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, true, false));
        ModSounds.play(level, feet, SoundEvents.ENDERMAN_TELEPORT, 3.0F, 1.2F);
        Vec3 arrive = feet.add(0, 1.0D, 0);
        for (LivingEntity e : Abilities.around(level, p, arrive, 7.0D)) {
            Abilities.hurt(level, e, p, 20.0F);
            Abilities.knock(e, arrive, 1.8D, 0.8D);
        }
        return true;
    }

    private MobPowers() {}
}
