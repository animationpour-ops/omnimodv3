package com.omnimine.server;

import com.omnimine.Omnimine;
import com.omnimine.data.Blueprints;
import com.omnimine.data.StructureKind;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModSounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Structures cheat : le clic droit sur une structure evoluee POSE la structure directement dans le monde, devant le
 * joueur, facade tournee vers lui. Le plan vient de Blueprints (genere par gen_structures.py : le meme plan sert a
 * dessiner la miniature 3D de l'objet). La construction monte couche par couche en quelques secondes, avec lumiere et
 * sons ; le butin (les anciens items cheat) est range dans le coffre de la structure.
 */
public final class CheatStructures {
    private static final int TARGET_TICKS = 70;
    private static final int MAX_FOUNDATION = 24;
    private static final int[] LOOT_SLOTS = {13, 12, 14, 11, 15, 10, 16, 9, 17, 4, 22, 3, 5};

    private record Op(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {}

    private static final class Plan {
        int hx;
        int hz;
        int h;
        BlockState foundation = Blocks.COBBLESTONE.defaultBlockState();
        int[] loot;
        final List<Op> ops = new ArrayList<>();
    }

    private static final Map<StructureKind, Plan> PLANS = new EnumMap<>(StructureKind.class);

    // ------------------------------------------------------------------ lecture des plans

    private static Plan plan(StructureKind kind) {
        Plan plan = PLANS.get(kind);
        if (plan == null) {
            plan = parse(kind);
            PLANS.put(kind, plan);
        }
        return plan;
    }

    private static Plan parse(StructureKind kind) {
        Plan plan = new Plan();
        Map<String, BlockState> cache = new HashMap<>();
        for (String raw : Blueprints.plan(kind).split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split(" ");
            switch (t[0]) {
                case "H" -> {
                    plan.hx = Integer.parseInt(t[1]);
                    plan.hz = Integer.parseInt(t[2]);
                    plan.h = Integer.parseInt(t[3]);
                }
                case "B" -> plan.foundation = cache.computeIfAbsent(t[1], CheatStructures::state);
                case "L" -> plan.loot = new int[] {Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])};
                case "F" -> plan.ops.add(new Op(Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]),
                        Integer.parseInt(t[4]), Integer.parseInt(t[5]), Integer.parseInt(t[6]),
                        cache.computeIfAbsent(t[7], CheatStructures::state)));
                default -> {
                }
            }
        }
        return plan;
    }

    /** "minecraft:dark_oak_stairs[facing=north,half=bottom]" -> l'etat de bloc (les proprietes inconnues sont ignorees). */
    static BlockState state(String spec) {
        String id = spec;
        String props = "";
        int open = spec.indexOf('[');
        if (open >= 0) {
            id = spec.substring(0, open);
            props = spec.substring(open + 1, spec.length() - 1);
        }
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || (block == Blocks.AIR && !id.equals("minecraft:air"))) {
            Omnimine.LOGGER.warn("Omnimine : bloc inconnu dans un plan de structure : {}", spec);
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                Property<?> property = block.getStateDefinition().getProperty(p[0]);
                if (property != null && p.length == 2) {
                    state = withValue(state, property, p[1]);
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    /** Les blocs qui s'accrochent a un autre (poses apres les murs) ; le coffre est pose tout a la fin. */
    private static int category(BlockState state) {
        String path = ForgeRegistries.BLOCKS.getKey(state.getBlock()).getPath();
        if (path.equals("chest")) {
            return 2;
        }
        if (path.contains("ladder") || path.contains("torch") || path.contains("lantern") || path.equals("end_rod")
                || path.equals("bell")) {
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ geometrie

    /** Quarts de tour (sens horaire) pour que la facade (sud du plan) regarde le joueur qui regarde vers `facing`. */
    private static int rotationFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3;
        };
    }

    private static Rotation rotation(int quarter) {
        if (quarter == 1) {
            return Rotation.CLOCKWISE_90;
        }
        if (quarter == 2) {
            return Rotation.CLOCKWISE_180;
        }
        if (quarter == 3) {
            return Rotation.COUNTERCLOCKWISE_90;
        }
        return Rotation.NONE;
    }

    private static BlockPos world(BlockPos origin, int quarter, int x, int y, int z) {
        int wx;
        int wz;
        if (quarter == 1) {
            wx = -z;
            wz = x;
        } else if (quarter == 2) {
            wx = -x;
            wz = -z;
        } else if (quarter == 3) {
            wx = z;
            wz = -x;
        } else {
            wx = x;
            wz = z;
        }
        return new BlockPos(origin.getX() + wx, origin.getY() + y, origin.getZ() + wz);
    }

    // ------------------------------------------------------------------ construction

    /**
     * Pose la structure cheat de ce genre devant le joueur. Renvoie false (rien n'est pose, l'objet n'est pas
     * consomme) s'il n'y a pas la place en hauteur.
     */
    public static boolean start(ServerLevel level, ServerPlayer player, StructureKind kind) {
        Plan plan = plan(kind);
        Direction facing = player.getDirection();
        int quarter = rotationFor(facing);
        int ex = quarter % 2 == 0 ? plan.hx : plan.hz;
        int ez = quarter % 2 == 0 ? plan.hz : plan.hx;

        // centre : devant le joueur, la facade a 3 blocs de lui
        int distance = plan.hz + 3;
        int cx = Mth.floor(player.getX()) + facing.getStepX() * distance;
        int cz = Mth.floor(player.getZ()) + facing.getStepZ() * distance;

        // hauteur du sol : mediane de 9 points de l'emprise (les arbres et les trous ne la faussent pas) ;
        // dans une dimension a plafond (Nether), la carte de hauteur donne le plafond : on se cale sur le sol du joueur
        int baseY;
        if (level.dimensionType().hasCeiling()) {
            baseY = player.blockPosition().getY() - 1;
        } else {
            int[] heights = new int[9];
            int n = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    heights[n++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx * ex, cz + dz * ez) - 1;
                }
            }
            Arrays.sort(heights);
            baseY = heights[4];
        }
        BlockPos origin = new BlockPos(cx, baseY, cz);
        if (baseY + plan.h + 3 >= level.getMaxBuildHeight() || baseY - 2 <= level.getMinBuildHeight()) {
            player.displayClientMessage(Component.translatable("message.omnimine.no_room").withStyle(
                    net.minecraft.ChatFormatting.RED), true);
            return false;
        }

        // 1) on degage le volume (arbres, relief, plantes) et l'entree : instantane, sans objets au sol
        clear(level, plan, origin, quarter);
        // 2) fondations sous les parties qui flottent
        foundation(level, plan, origin, quarter);

        // 3) liste des blocs a poser, tries du bas vers le haut puis du centre vers l'exterieur
        Map<Long, BlockState> cells = new HashMap<>();
        for (Op op : plan.ops) {
            for (int x = op.x0(); x <= op.x1(); x++) {
                for (int y = op.y0(); y <= op.y1(); y++) {
                    for (int z = op.z0(); z <= op.z1(); z++) {
                        cells.put(world(origin, quarter, x, y, z).asLong(), op.state());
                    }
                }
            }
        }
        Rotation rot = rotation(quarter);
        List<long[]> order = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
            BlockPos pos = BlockPos.of(e.getKey());
            BlockState state = e.getValue();
            if (state.isAir()) {
                // un trou voulu dans le sol de la structure
                if (pos.getY() == baseY && !level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
                continue;
            }
            int dx = pos.getX() - cx;
            int dz = pos.getZ() - cz;
            long key = (long) category(state) * 100_000_000L + (long) (pos.getY() - baseY + 64) * 100_000L
                    + (dx * dx + dz * dz);
            order.add(new long[] {key, e.getKey(), states.size()});
            states.add(state.rotate(rot));
        }
        order.sort((a, b) -> Long.compare(a[0], b[0]));
        long[] positions = new long[order.size()];
        BlockState[] sorted = new BlockState[order.size()];
        for (int i = 0; i < order.size(); i++) {
            positions[i] = order.get(i)[1];
            sorted[i] = states.get((int) order.get(i)[2]);
        }

        BlockPos lootPos = plan.loot == null ? null : world(origin, quarter, plan.loot[0], plan.loot[1], plan.loot[2]);
        int perTick = Math.max(24, (int) Math.ceil(positions.length / (double) TARGET_TICKS));
        Build build = new Build(kind, player, positions, sorted, perTick, lootPos, new BlockPos(cx, baseY + 1, cz));
        ServerEffects.add(level, build);

        // ceremonie : colonne de lumiere sur l'emplacement, grondement
        Vec3 center = new Vec3(cx + 0.5D, baseY + 1.0D, cz + 0.5D);
        float scale = Mth.clamp(plan.hx / 5.0F, 1.3F, 2.4F);
        VfxPacket.of(VfxType.REWARD, center).color(kind.color).duration(90).scale(scale).send(level);
        ModSounds.play(level, center, ModSounds.REWARD, 4.0F, 0.8F);
        ModSounds.play(level, center, SoundEvents.ANVIL_PLACE, 1.4F, 0.6F);
        return true;
    }

    private static void clear(ServerLevel level, Plan plan, BlockPos origin, int quarter) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -plan.hx; x <= plan.hx; x++) {
            for (int z = -plan.hz; z <= plan.hz; z++) {
                for (int y = 1; y <= plan.h + 1; y++) {
                    clearOne(level, world(origin, quarter, x, y, z), air);
                }
            }
        }
        // devant l'entree : un couloir degage de 2 blocs (le joueur se tient juste derriere, on ne creuse pas sous ses pieds)
        for (int x = -3; x <= 3; x++) {
            for (int z = plan.hz + 1; z <= plan.hz + 2; z++) {
                for (int y = 1; y <= 3; y++) {
                    clearOne(level, world(origin, quarter, x, y, z), air);
                }
            }
        }
    }

    private static void clearOne(ServerLevel level, BlockPos pos, BlockState air) {
        if (!level.getBlockState(pos).isAir()) {
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        }
    }

    /** Sous chaque colonne du sol de la structure : on comble le vide (air, eau, plantes) jusqu'au vrai sol. */
    private static void foundation(ServerLevel level, Plan plan, BlockPos origin, int quarter) {
        boolean[][] floor = new boolean[2 * plan.hx + 1][2 * plan.hz + 1];
        for (Op op : plan.ops) {
            if (op.y0() > 0) {
                continue;
            }
            for (int x = op.x0(); x <= op.x1(); x++) {
                for (int z = op.z0(); z <= op.z1(); z++) {
                    floor[x + plan.hx][z + plan.hz] = !op.state().isAir();
                }
            }
        }
        for (int x = -plan.hx; x <= plan.hx; x++) {
            for (int z = -plan.hz; z <= plan.hz; z++) {
                if (!floor[x + plan.hx][z + plan.hz]) {
                    continue;
                }
                for (int dy = -1; dy >= -MAX_FOUNDATION; dy--) {
                    BlockPos pos = world(origin, quarter, x, dy, z);
                    BlockState below = level.getBlockState(pos);
                    if (below.isAir() || below.canBeReplaced() || !below.getFluidState().isEmpty()) {
                        level.setBlock(pos, plan.foundation, Block.UPDATE_CLIENTS);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    /** La construction en cours : quelques dizaines de blocs par tick, puis le butin. */
    private static final class Build implements ServerEffects.Timed {
        private final StructureKind kind;
        private final ServerPlayer player;
        private final long[] positions;
        private final BlockState[] states;
        private final int perTick;
        private final BlockPos lootPos;
        private final BlockPos center;
        private int next;
        private int ticks;
        private boolean finished;

        Build(StructureKind kind, ServerPlayer player, long[] positions, BlockState[] states, int perTick,
                BlockPos lootPos, BlockPos center) {
            this.kind = kind;
            this.player = player;
            this.positions = positions;
            this.states = states;
            this.perTick = perTick;
            this.lootPos = lootPos;
            this.center = center;
        }

        private void place(ServerLevel level, int from, int to) {
            for (int i = from; i < to; i++) {
                BlockPos pos = BlockPos.of(positions[i]);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                level.setBlock(pos, states[i], Block.UPDATE_ALL);
            }
        }

        @Override
        public boolean tick(ServerLevel level) {
            ticks++;
            int to = Math.min(positions.length, next + perTick);
            if (next < to) {
                place(level, next, to);
                BlockPos at = BlockPos.of(positions[to - 1]);
                if (ticks % 2 == 0) {
                    ModSounds.play(level, Vec3.atCenterOf(at), SoundEvents.STONE_PLACE, 1.4F, 0.7F + 0.3F * (to / (float) positions.length));
                }
                // des etincelles sur les blocs qui viennent d'apparaitre
                int step = Math.max(1, (to - next) / 5);
                for (int i = next; i < to; i += step) {
                    BlockPos p = BlockPos.of(positions[i]);
                    level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, 1, 0.3D, 0.3D,
                            0.3D, 0.02D);
                }
                next = to;
            }
            return next >= positions.length;
        }

        /** Fin (ou arret du serveur en pleine construction) : on termine, on range le butin, on celebre. */
        @Override
        public void end(ServerLevel level) {
            if (finished) {
                return;
            }
            finished = true;
            if (next < positions.length) {
                place(level, next, positions.length);
                next = positions.length;
            }
            fillLoot(level);
            Vec3 c = Vec3.atCenterOf(lootPos != null ? lootPos : center);
            ModSounds.play(level, c, ModSounds.REWARD, 3.0F, 1.1F);
            ModSounds.play(level, c, SoundEvents.TOTEM_USE, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.0D, c.z, 60, 1.5D, 1.0D, 1.5D, 0.4D);
            if (player.isAlive() && !player.isRemoved()) {
                player.displayClientMessage(Component.translatable("message.omnimine.structure_built",
                        Component.translatable("item.omnimine.evolved_" + kind.id)).withStyle(
                        net.minecraft.ChatFormatting.GOLD), true);
            }
        }

        /** Les items cheat vont dans le coffre de la structure ; s'il n'existe plus, ils sont donnes au joueur. */
        private void fillLoot(ServerLevel level) {
            List<ItemStack> items = kind.rewards();
            BlockEntity entity = lootPos == null ? null : level.getBlockEntity(lootPos);
            if (entity instanceof Container container) {
                for (int i = 0; i < items.size(); i++) {
                    int slot = i < LOOT_SLOTS.length ? LOOT_SLOTS[i] : i;
                    if (slot < container.getContainerSize()) {
                        container.setItem(slot, items.get(i));
                    }
                }
                entity.setChanged();
            } else if (player.isAlive() && !player.isRemoved()) {
                for (ItemStack stack : items) {
                    ItemHandlerHelper.giveItemToPlayer(player, stack);
                }
            }
        }
    }

    private CheatStructures() {}
}
