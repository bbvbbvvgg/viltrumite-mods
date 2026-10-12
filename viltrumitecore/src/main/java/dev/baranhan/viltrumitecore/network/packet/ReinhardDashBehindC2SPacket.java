package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Reinhard's Dash Behind press (stage 3 Task 8): the target the client saw under
 * the crosshair and the feet of the spot it already moved to. Sent once per key
 * edge by {@code DashBehindClient}; this slot never goes through
 * {@link HeroInputC2SPacket}. The server trusts nothing: the slot must sit on the
 * active page, the coordinates must be finite, the target must be under the
 * server's own look ray and the spot is re-resolved by {@link DashBehind} behind
 * the usual gate. Every packet is answered with
 * {@link ReinhardDashBehindAnswerS2CPacket} (the cooldown after the press).
 */
public class ReinhardDashBehindC2SPacket {
   private final int targetId;
   private final double x;
   private final double y;
   private final double z;

   public ReinhardDashBehindC2SPacket(int targetId, Vec3 feet) {
      this.targetId = targetId;
      this.x = feet.x;
      this.y = feet.y;
      this.z = feet.z;
   }

   public ReinhardDashBehindC2SPacket(FriendlyByteBuf buffer) {
      this.targetId = buffer.readVarInt();
      this.x = buffer.readDouble();
      this.y = buffer.readDouble();
      this.z = buffer.readDouble();
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.targetId);
      buffer.writeDouble(this.x);
      buffer.writeDouble(this.y);
      buffer.writeDouble(this.z);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player == null) {
            return;
         }

         int cooldown = 0;
         if (HeroRegistry.get(player) == ReinhardHero.INSTANCE && onActivePage(player, ReinhardAbility.DASH_BEHIND.id())) {
            ReinhardState state = ReinhardState.ensure(player);
            ReinhardActions.handleDashBehind(player, state, new DashBehind.Request(this.targetId, new Vec3(this.x, this.y, this.z)));
            cooldown = state.cooldowns[ReinhardAbility.DASH_BEHIND.ordinal()];
         }

         CoreMessages.sendToPlayer(new ReinhardDashBehindAnswerS2CPacket(cooldown), player);
      });
      context.setPacketHandled(true);
   }

   /** The ability sits in one of the six slots of the active page. */
   static boolean onActivePage(ServerPlayer player, String id) {
      if (!(player instanceof ViltrumiteAbilityUser user)) {
         return false;
      }

      int offset = user.getActivePage() * 6;
      for (int slot = offset; slot < offset + 6 && slot < 18; slot++) {
         if (id.equals(user.getAbilityInSlot(slot))) {
            return true;
         }
      }

      return false;
   }
}
