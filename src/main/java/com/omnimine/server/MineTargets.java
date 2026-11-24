package com.omnimine.server;

import com.omnimine.data.MobKind;
import com.omnimine.data.StructureKind;
import com.omnimine.item.BedrockPickaxeItem;
import com.omnimine.util.BoltShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Trouve ce que le joueur est en train de viser avec la pioche : mob, soleil, lune, eclair, structure ou bedrock. */
public final class MineTargets {

    public enum Kind { MOB, SUN, MOON, BOLT, STRUCTURE, BEDROCK }

    /** Une cible de minage. {@link #key} identifie la cible : si elle change, le minage repart de zero. */
    public static final class Target {
        public final Kind kind;
        public final String key;
        public final int mineTicks;
        public final int color;
        public final Component name;
        public Vec3 center;
        public float size = 1.0F;
        public Entity entity;
        public MobKind mob;
        public FrozenBolts.Bolt bolt;
        public StructureKind structure;
        public StructureStart start;
        public BlockPos pos;

        Target(Kind kind, String key, int mineTicks, int color, Component name, Vec3 center) {
            this.kind = kind;
            this.key = key;
            this.mineTicks = mineTicks;
            this.color = color;
            this.name = name;
            this.center = center;
        }
    }

    public static final double MOB_REACH = 5.0D;
    public static final double STRUCTURE_REACH = 6.0D;

    /** Temps de minage d'un bloc de bedrock, en ticks (comme un bloc tres dur). */
    public static final int BEDROCK_TICKS = 70;
    public static final double BEDROCK_REACH = 5.0D;

    /**
     * Ce que vise le joueur. Pioche en bedrock : mobs, eclairs, ciel, bedrock, et (accroupi) structures. Pioche en
     * diamant / netherite avec Bedrock Breaker : seulement le bedrock.
     */
    public static Target pick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        ItemStack held = p.getMainHandItem();
        boolean omni = BedrockPickaxeItem.hasOmni(held);
        boolean breaker = BedrockPickaxeItem.canBreakBedrock(held);
        if (!omni && !breaker) {
            return null;
        }
        boolean sneaking = p.isShiftKeyDown();
        Target t = null;
        if (omni) {
            if (sneaking) {
                t = structure(level, p);
            } else {
                t = mob(level, p);
                if (t == null) {
                    t = bolt(level, p);
                }
            }
        }
        if (t == null && breaker) {
            t = bedrock(level, p);
        }
        if (t == null && omni && !sneaking) {
            t = sky(level, p);
        }
        return t;
    }

    // ----------------------------------------------------------------- bedrock

    /** Un bloc de bedrock a portee de bras. */
    private static Target bedrock(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(BEDROCK_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
            return null;
        }
        Target t = new Target(Kind.BEDROCK, "bedrock:" + pos.asLong(), BEDROCK_TICKS, 0x8A8A96,
                Component.translatable("block.minecraft.bedrock"), Vec3.atCenterOf(pos));
        t.pos = pos;
        t.size = 1.5F;
        return t;
    }

    // ------------------------------------------------------------------ mobs

    private static Target mob(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(MOB_REACH));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(MOB_REACH)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, limit, box,
                e -> e.isAlive() && !e.isSpectator() && e.isPickable() && MobKind.of(e) != null);
        if (hit == null) {
            return null;
        }
        Entity e = hit.getEntity();
        MobKind kind = MobKind.of(e);
        if (kind == null) {
            return null;
        }
        Target t = new Target(Kind.MOB, "mob:" + e.getUUID(), kind.mineTicks, kind.color, e.getName(),
                e.position().add(0, e.getBbHeight() * 0.5D, 0));
        t.entity = e;
        t.mob = kind;
        t.size = Math.max(e.getBbHeight(), e.getBbWidth() * 1.4F);
        return t;
    }

    // ------------------------------------------------------------------ ciel

    private static Target sky(ServerLevel level, ServerPlayer p) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        Vec3 look = p.getViewVector(1.0F);
        if (look.y < 0.0D) {
            return null;
        }
        Vec3 eye = p.getEyePosition();
        // il faut voir le ciel dans cette direction
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(80.0D)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) {
            return null;
        }
        Vec3 sun = SkyBodies.sunDir(level);
        if (!SkyBodies.sunGone() && sun.y > -0.02D && look.dot(sun) > Math.cos(Math.toRadians(9.0D))) {
            Target t = new Target(Kind.SUN, "sun", 140, 0xFFB030, Component.translatable("mine.omnimine.sun"),
                    eye.add(sun.scale(90.0D)));
            t.size = 34.0F;
            return t;
        }
        Vec3 moon = sun.scale(-1.0D);
        if (!SkyBodies.moonGone() && moon.y > -0.02D && look.dot(moon) > Math.cos(Math.toRadians(7.0D))) {
            Target t = new Target(Kind.MOON, "moon", 120, 0xBFD8FF, Component.translatable("mine.omnimine.moon"),
                    eye.add(moon.scale(90.0D)));
            t.size = 24.0F;
            return t;
        }
        return null;
    }

    // ---------------------------------------------------------------- eclairs

    /** Distance maximale entre le regard et le tronc de l'eclair (le dessin vanilla fait ~1 a 2 blocs de large). */
    private static final double BOLT_TOLERANCE = 2.4D;
    private static final double BOLT_REACH = 72.0D;

    private static Target bolt(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(BOLT_REACH));
        FrozenBolts.Bolt best = null;
        double bestDist = BOLT_TOLERANCE;
        Vec3 bestPoint = null;
        Vec3[] closest = new Vec3[1];
        for (FrozenBolts.Bolt b : FrozenBolts.in(level)) {
            Vec3[] trunk = b.trunk;
            for (int i = 0; i < trunk.length - 1; i++) {
                double dist = BoltShape.distance(eye, end, trunk[i], trunk[i + 1], closest);
                if (dist < bestDist && closest[0] != null) {
                    bestDist = dist;
                    best = b;
                    bestPoint = closest[0];
                }
            }
        }
        if (best == null) {
            return null;
        }
        Target t = new Target(Kind.BOLT, "bolt:" + best.id, 25, 0xBFE8FF, Component.translatable("mine.omnimine.bolt"),
                bestPoint);
        t.bolt = best;
        t.size = 4.0F;
        return t;
    }

    // -------------------------------------------------------------- structures

    private static Target structure(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(STRUCTURE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        for (StructureKind kind : StructureKind.values()) {
            for (ResourceKey<Structure> key : kind.keys) {
                StructureStart start = startAt(level, pos, key);
                if (start == null || !StructureDissolve.available(level, kind, start)) {
                    continue;
                }
                Target t = new Target(Kind.STRUCTURE, "structure:" + StructureDissolve.id(kind, start),
                        kind.mineTicks, kind.color, Component.translatable("mine.omnimine.structure." + kind.id),
                        Vec3.atCenterOf(pos));
                t.structure = kind;
                t.start = start;
                t.pos = pos;
                t.size = 3.0F;
                return t;
            }
        }
        return null;
    }

    /** La structure dont une piece contient ce bloc, ou null. Seul endroit qui appelle l'API des structures. */
    static StructureStart startAt(ServerLevel level, BlockPos pos, ResourceKey<Structure> key) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, key);
        return start != null && start.isValid() ? start : null;
    }

    private MineTargets() {}
}
