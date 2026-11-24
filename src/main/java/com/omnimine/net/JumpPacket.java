package com.omnimine.net;

import com.omnimine.server.MoonPowers;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client -> serveur : le joueur appuie sur saut en l'air (double saut lunaire). */
public class JumpPacket {

    public JumpPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static JumpPacket decode(FriendlyByteBuf buf) {
        return new JumpPacket();
    }

    public static void handle(JumpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) {
                MoonPowers.airJump(player);
            }
        });
        c.setPacketHandled(true);
    }
}
