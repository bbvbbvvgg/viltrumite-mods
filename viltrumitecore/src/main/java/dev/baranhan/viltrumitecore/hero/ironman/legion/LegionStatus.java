package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * What the owner's HUD shows about his Legion (design §8): per suit the mark,
 * durability, phase, stance and role; for the squad the order, priority,
 * call cooldown and the JARVIS events of this update. Wire format is the
 * packet's; ordinals are clamped on read.
 */
public record LegionStatus(boolean active, int order, int priority, int cooldown, int events, List<Suit> suits) {
   public static final LegionStatus NONE = new LegionStatus(false, LegionOrder.GUARD.ordinal(), 0, 0, 0, List.of());

   /** One suit line of the HUD. */
   public record Suit(int mark, float fraction, int phase, int stance, int role, boolean engaged) {
      @Nullable
      public MarkId markId() {
         return MarkId.byId(this.mark);
      }

      public LegionPhase phaseValue() {
         return LegionPhase.byId(this.phase);
      }

      public LegionTactics.Stance stanceValue() {
         LegionTactics.Stance[] values = LegionTactics.Stance.values();
         return this.stance >= 0 && this.stance < values.length ? values[this.stance] : LegionTactics.Stance.GUARD;
      }

      public LegionRole roleValue() {
         LegionRole[] values = LegionRole.values();
         return this.role >= 0 && this.role < values.length ? values[this.role] : LegionRole.VANGUARD;
      }
   }

   public LegionOrder orderValue() {
      LegionOrder order = LegionOrder.byId(this.order);
      return order == null ? LegionOrder.GUARD : order;
   }

   public LegionPriority priorityValue() {
      LegionPriority[] values = LegionPriority.values();
      return this.priority >= 0 && this.priority < values.length ? values[this.priority] : LegionPriority.NEAREST;
   }

   public boolean has(int event) {
      return (this.events & event) != 0;
   }

   public static LegionStatus of(LegionSquad squad) {
      List<Suit> suits = new ArrayList<>(squad.suits.size());
      for (LegionSuitEntity suit : squad.suits) {
         LegionTactics.Assignment a = squad.plan(suit.slot());
         LegionProfile profile = LegionProfile.of(suit.mark());
         LegionTactics.Stance stance = a == null ? LegionTactics.Stance.GUARD : a.stance();
         LegionRole role = a == null ? profile.role() : a.role();
         suits.add(new Suit(suit.mark().ordinal(), suit.fraction(), suit.phase().ordinal(), stance.ordinal(), role.ordinal(), a != null && a.hasTarget()));
      }

      return new LegionStatus(squad.active(), squad.order.ordinal(), squad.priority.ordinal(), squad.cooldown, squad.events, suits);
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeBoolean(this.active);
      buffer.writeByte(this.order);
      buffer.writeByte(this.priority);
      buffer.writeVarInt(Math.max(0, this.cooldown));
      buffer.writeByte(this.events);
      buffer.writeByte(Math.min(LegionRules.MAX_SUITS, this.suits.size()));
      for (int i = 0; i < Math.min(LegionRules.MAX_SUITS, this.suits.size()); i++) {
         Suit suit = this.suits.get(i);
         buffer.writeByte(suit.mark);
         buffer.writeFloat(suit.fraction);
         buffer.writeByte(suit.phase);
         buffer.writeByte(suit.stance);
         buffer.writeByte(suit.role);
         buffer.writeBoolean(suit.engaged);
      }
   }

   public static LegionStatus read(FriendlyByteBuf buffer) {
      boolean active = buffer.readBoolean();
      int order = buffer.readByte();
      int priority = buffer.readByte();
      int cooldown = buffer.readVarInt();
      int events = buffer.readByte() & 0xFF;
      int n = Math.max(0, Math.min(LegionRules.MAX_SUITS, buffer.readByte()));
      List<Suit> suits = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         int mark = buffer.readByte();
         float fraction = buffer.readFloat();
         int phase = buffer.readByte();
         int stance = buffer.readByte();
         int role = buffer.readByte();
         boolean engaged = buffer.readBoolean();
         suits.add(new Suit(mark, Float.isFinite(fraction) ? Math.max(0.0F, Math.min(1.0F, fraction)) : 0.0F, phase, stance, role, engaged));
      }

      return new LegionStatus(active, order, priority, cooldown, events, List.copyOf(suits));
   }
}
