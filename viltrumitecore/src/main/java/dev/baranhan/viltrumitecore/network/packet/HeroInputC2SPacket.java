package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.HeroAirJump;
import dev.baranhan.viltrumitecore.hero.HeroSuperJump;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Bounded hero input edges: action ordinal + pressed. The server re-validates
 * the action against the sender's hero, equipment page and action lock.
 */
public class HeroInputC2SPacket {
   private final int actionOrdinal;
   private final boolean pressed;

   public HeroInputC2SPacket(HeroAction action, boolean pressed) {
      this.actionOrdinal = action.ordinal();
      this.pressed = pressed;
   }

   public HeroInputC2SPacket(FriendlyByteBuf buffer) {
      this.actionOrdinal = buffer.readByte();
      this.pressed = buffer.readBoolean();
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeByte(this.actionOrdinal);
      buffer.writeBoolean(this.pressed);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player == null) {
            return;
         }

         HeroAction action = HeroAction.byId(this.actionOrdinal);
         if (action == null) {
            return;
         }

         // Mouse-only actions come through HeldInputs alone (claim, held-repeat guard, release pairing).
         if (!acceptsKeyEdge(HeroRegistry.get(player), action)) {
            return;
         }

         // A body held by an anchoring hold presses nothing (releases still pass).
         if (this.pressed && dev.baranhan.viltrumitecore.hero.HeroHeldTargets.anchored(player)) {
            return;
         }

         if (action == HeroAction.JUMP) {
            if (this.pressed) {
               HeroSuperJump.onInput(player);
            }
            return;
         }

         if (action == HeroAction.AIR_STEP) {
            if (this.pressed) {
               HeroAirJump.onInput(player);
            }
            return;
         }

         // A press must come from an equipped slot on the active page; releases always pass
         // so active channels can stop.
         if (this.pressed && !equipped(player, action)) {
            return;
         }

         HeroRegistry.get(player).handleInput(player, action, this.pressed);
      });
      context.setPacketHandled(true);
   }

   /** False for an action the hero only takes through a claimed mouse button (HeldInputs). */
   static boolean acceptsKeyEdge(dev.baranhan.viltrumitecore.hero.HeroDefinition hero, HeroAction action) {
      return !hero.mouseOnly(action);
   }

   /** True when no hero slot maps to the action, or one that does sits on the active page. */
   static boolean equipped(ServerPlayer player, HeroAction action) {
      dev.baranhan.viltrumitecore.hero.HeroDefinition hero = HeroRegistry.get(player);
      boolean slotBound = false;
      for (String id : hero.heroInputSlots()) {
         if (hero.heroActionFor(id) != action) {
            continue;
         }

         slotBound = true;
         if (player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser user) {
            int offset = user.getActivePage() * 6;
            for (int slot = offset; slot < offset + 6 && slot < 18; slot++) {
               if (id.equals(user.getAbilityInSlot(slot))) {
                  return true;
               }
            }
         }
      }

      return !slotBound;
   }
}
