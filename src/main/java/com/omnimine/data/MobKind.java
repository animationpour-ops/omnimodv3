package com.omnimine.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Les huit mobs que la pioche sait miner. Chacun donne un item avec un seul petit pouvoir. */
public enum MobKind {
    SKELETON("skeleton", EntityType.SKELETON, 0xE6E6D2, 50, 20 * 6),
    CREEPER("creeper", EntityType.CREEPER, 0x58E04A, 50, 20 * 9),
    ZOMBIE("zombie", EntityType.ZOMBIE, 0x7CB85A, 45, 20 * 7),
    SPIDER("spider", EntityType.SPIDER, 0xD03A3A, 45, 20 * 8),
    GUARDIAN("guardian", EntityType.GUARDIAN, 0x3FE0D0, 70, 20 * 10),
    VILLAGER("villager", EntityType.VILLAGER, 0x39D36B, 40, 20 * 7),
    IRON_GOLEM("iron_golem", EntityType.IRON_GOLEM, 0xD9D9E6, 110, 20 * 9),
    ENDERMAN("enderman", EntityType.ENDERMAN, 0xB04CFF, 80, 20 * 6);

    public final String id;
    public final EntityType<?> type;
    /** Couleur des effets visuels. */
    public final int color;
    /** Temps de minage de base, en ticks. */
    public final int mineTicks;
    /** Recharge du pouvoir, en ticks. */
    public final int cooldown;

    MobKind(String id, EntityType<?> type, int color, int mineTicks, int cooldown) {
        this.id = id;
        this.type = type;
        this.color = color;
        this.mineTicks = mineTicks;
        this.cooldown = cooldown;
    }

    public static MobKind of(Entity e) {
        for (MobKind k : values()) {
            if (e.getType() == k.type) {
                return k;
            }
        }
        return null;
    }
}
