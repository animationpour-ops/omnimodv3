package com.omnimine.net;

import com.omnimine.server.MiningManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> serveur : le joueur maintient le clic gauche avec la pioche (envoye a chaque tick tant que c'est le cas). */
public class MineHoldPacket {

    public MineHoldPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static MineHoldPacket decode(FriendlyByteBuf buf) {
        return new MineHoldPacket();
    }

    public static void handle(MineHoldPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) {
                MiningManager.hold(player);
            }
        });
        c.setPacketHandled(true);
    }
}
