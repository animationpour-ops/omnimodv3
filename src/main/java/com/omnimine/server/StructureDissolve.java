package com.omnimine.server;

import com.omnimine.OmniConfig;
import com.omnimine.data.StructureKind;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

/**
 * Dissolution d'une structure minee : on retire, piece par piece et de haut en bas, tous les blocs qui ne sont pas
 * du terrain naturel. Le terrain autour reste intact.
 */
public final class StructureDissolve {
    /** Structures deja dissoutes (ou vides) : on ne peut plus les miner. Non persistant. */
    private static final Set<String> GONE = new HashSet<>();
    private static final Map<String, Long> INTACT_UNTIL = new HashMap<>();

    public static String id(StructureKind kind, StructureStart start) {
        BoundingBox b = start.getBoundingBox();
        return kind.id + ":" + b.minX() + ":" + b.minZ();
    }

    /** La structure contient-elle encore assez de blocs a dissoudre ? */
    public static boolean available(ServerLevel level, StructureKind kind, StructureStart start) {
        String id = id(kind, start);
        if (GONE.contains(id)) {
            return false;
        }
        long now = level.getGameTime();
        Long until = INTACT_UNTIL.get(id);
        if (until != null && now < until) {
            return true;
        }
        if (count(level, kind, start, 6) >= 6) {
            INTACT_UNTIL.put(id, now + 100L);
            return true;
        }
        GONE.add(id);
        return false;
    }

    /** Un bloc de structure (a retirer) : ni air, ni liquide, ni terrain naturel. */
    static boolean isStructureBlock(StructureKind kind, BlockState s) {
        if (s.isAir() || s.getBlock() instanceof LiquidBlock || s.canBeReplaced()) {
            return false;
        }
        if (kind == StructureKind.BASTION && s.is(Blocks.BLACKSTONE)) {
            return true;
        }
        return !(s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.DIRT)
                || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.NYLIUM)
                || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.LEAVES) || s.is(Tags.Blocks.ORES)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL));
    }

    private static int count(ServerLevel level, StructureKind kind, StructureStart start, int limit) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox b = piece.getBoundingBox();
            for (int y = b.maxY(); y >= b.minY(); y--) {
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        m.set(x, y, z);
                        if (level.isLoaded(m) && isStructureBlock(kind, level.getBlockState(m)) && ++n >= limit) {
                            return n;
                        }
                    }
                }
            }
        }
        return n;
    }

    /** Lance la dissolution (la structure est marquee comme vide tout de suite). */
    public static void start(ServerLevel level, ServerPlayer by, StructureKind kind, StructureStart start, BlockPos hit) {
        GONE.add(id(kind, start));
        List<StructurePiece> pieces = new ArrayList<>(start.getPieces());
        pieces.sort(Comparator.comparingDouble(pc -> pc.getBoundingBox().getCenter().distSqr(hit)));
        long volume = 0L;
        for (StructurePiece pc : pieces) {
            BoundingBox b = pc.getBoundingBox();
            volume += (long) b.getXSpan() * b.getYSpan() * b.getZSpan();
        }
        int perTick = OmniConfig.DISSOLVE_BLOCKS_PER_TICK.get();
        int ticks = (int) Math.max(40L, Math.min(220L, volume / (perTick * 6L) + 30L));
        ServerEffects.add(level, new Job(kind, pieces, perTick));

        BoundingBox whole = start.getBoundingBox();
        BlockPos c = whole.getCenter();
        VfxPacket.of(VfxType.DISSOLVE, new Vec3(c.getX() + 0.5D, whole.minY(), c.getZ() + 0.5D))
                .to(new Vec3(Math.min(60.0D, whole.getXSpan() * 0.5D), Math.min(50.0D, whole.getYSpan() * 1.0D),
                        Math.min(60.0D, whole.getZSpan() * 0.5D)))
                .duration(ticks).color(kind.color).send(level);
    }

    private static final class Job implements ServerEffects.Timed {
        private final StructureKind kind;
        private final List<StructurePiece> pieces;
        private final int perTick;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        private int pieceIndex;
        private long cursor;
        private int containers;

        Job(StructureKind kind, List<StructurePiece> pieces, int perTick) {
            this.kind = kind;
            this.pieces = pieces;
            this.perTick = perTick;
        }

        @Override
        public boolean tick(ServerLevel level) {
            int scan = perTick * 6;
            int changes = 0;
            while (pieceIndex < pieces.size() && scan > 0 && changes < perTick) {
                BoundingBox b = pieces.get(pieceIndex).getBoundingBox();
                long sx = b.getXSpan();
                long sz = b.getZSpan();
                long total = sx * b.getYSpan() * sz;
                while (cursor < total && scan > 0 && changes < perTick) {
                    long c = cursor++;
                    scan--;
                    int x = b.minX() + (int) (c % sx);
                    int z = b.minZ() + (int) ((c / sx) % sz);
                    int y = b.maxY() - (int) (c / (sx * sz));
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(m);
                    if (!isStructureBlock(kind, s)) {
                        continue;
                    }
                    remove(level, s);
                    changes++;
                }
                if (cursor >= total) {
                    pieceIndex++;
                    cursor = 0L;
                }
            }
            return pieceIndex >= pieces.size();
        }

        private void remove(ServerLevel level, BlockState s) {
            if (level.getBlockEntity(m) != null && containers++ < 300) {
                level.destroyBlock(m.immutable(), true);
                return;
            }
            BlockState replacement = kind.water && m.getY() < level.getSeaLevel()
                    ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(m, replacement, 18);
        }
    }

    private StructureDissolve() {}
}
