package com.omnimine.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Un effet visuel a jouer cote client. Construit avec des setters chaines. */
public class VfxPacket {
    /** Duree (en ticks) de la chute du meteore avant l'impact, partagee serveur/client. */
    public static final int METEOR_FLIGHT = 34;

    public VfxType type = VfxType.CANCEL;
    public double x, y, z;
    public double x2, y2, z2;
    public int duration = 20;
    /** Entite suivie par l'effet (-1 = aucune). */
    public int entityId = -1;
    public int color = 0xFFFFFF;
    public float param = 1.0F;
    /** Agrandissement de l'effet autour de son point d'origine (1 = taille normale). */
    public float scale = 1.0F;
    public int seed;

    public VfxPacket() {}

    public static VfxPacket of(VfxType type, Vec3 pos) {
        VfxPacket p = new VfxPacket();
        p.type = type;
        p.x = pos.x;
        p.y = pos.y;
        p.z = pos.z;
        p.seed = (int) (Math.random() * Integer.MAX_VALUE);
        return p;
    }

    /** Demande au client d'arreter les effets de ce type qui ont cette graine (ou suivent cette entite). */
    public static VfxPacket cancel(VfxType what, int seed, Vec3 pos) {
        VfxPacket p = of(VfxType.CANCEL, pos);
        p.param = what.ordinal();
        p.seed = seed;
        return p;
    }

    public VfxPacket seed(int value) {
        this.seed = value;
        return this;
    }

    public VfxPacket to(Vec3 pos) {
        this.x2 = pos.x;
        this.y2 = pos.y;
        this.z2 = pos.z;
        return this;
    }

    public VfxPacket duration(int ticks) {
        this.duration = ticks;
        return this;
    }

    public VfxPacket follow(Entity entity) {
        this.entityId = entity.getId();
        return this;
    }

    public VfxPacket color(int rgb) {
        this.color = rgb;
        return this;
    }

    public VfxPacket param(float value) {
        this.param = value;
        return this;
    }

    /** Agrandit tout l'effet autour de son origine (les degats serveur ont grandi, le dessin suit). */
    public VfxPacket scale(float value) {
        this.scale = value;
        return this;
    }

    /** Envoie l'effet a tous les joueurs proches de sa position de depart. */
    public void send(ServerLevel level) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, 200.0D, level.dimension())),
                this);
    }

    /** Envoie l'effet a tous les joueurs de la dimension (ciel, effets enormes). */
    public void sendAll(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), this);
    }

    /** Envoie l'effet a un seul joueur. */
    public void sendTo(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    // ---- codec reseau ----

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(x2);
        buf.writeDouble(y2);
        buf.writeDouble(z2);
        buf.writeVarInt(duration);
        buf.writeVarInt(entityId + 1);
        buf.writeInt(color);
        buf.writeFloat(param);
        buf.writeFloat(scale);
        buf.writeInt(seed);
    }

    public static VfxPacket decode(FriendlyByteBuf buf) {
        VfxPacket p = new VfxPacket();
        p.type = VfxType.byId(buf.readByte());
        p.x = buf.readDouble();
        p.y = buf.readDouble();
        p.z = buf.readDouble();
        p.x2 = buf.readDouble();
        p.y2 = buf.readDouble();
        p.z2 = buf.readDouble();
        p.duration = buf.readVarInt();
        p.entityId = buf.readVarInt() - 1;
        p.color = buf.readInt();
        p.param = buf.readFloat();
        p.scale = buf.readFloat();
        p.seed = buf.readInt();
        return p;
    }

    public static void handle(VfxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.omnimine.client.VfxManager.receive(msg)));
        c.setPacketHandled(true);
    }
}
