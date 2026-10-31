package dev.baranhan.viltrumitecore.hero.ironman.veronica;

import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSpec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * What the Veronica suit menu shows (spec §12.3): every mark's location,
 * durability and cooldown, the Hulkbuster card (Stage 5) and the pod's time
 * left. Built on the server; the client never decides availability.
 *
 * @param hulkbuster -1 = not available, {@link #HULKBUSTER_ON} = on Tony, 0 = ready, &gt;0 = cooldown ticks
 */
public record VeronicaView(int[] location, float[] durability, int[] cooldown, int wornMark, int hulkbuster, int podTicksLeft) {
   /** Choice id of the Hulkbuster card (marks use their ordinal). */
   public static final int HULKBUSTER = 100;
   /** {@link #hulkbuster()} value while the Hulkbuster is on Tony. */
   public static final int HULKBUSTER_ON = -2;

   /** {@code podTicksLeft} of a pod that stays (no time limit). */
   public static final int POD_STAYS = -1;

   public static VeronicaView of(MarkRoster roster, int hulkbuster, int podTicksLeft) {
      int n = MarkId.count();
      int[] location = new int[n];
      float[] durability = new float[n];
      int[] cooldown = new int[n];
      int worn = -1;
      for (MarkId id : MarkId.values()) {
         location[id.ordinal()] = roster.location(id).ordinal();
         durability[id.ordinal()] = roster.durability(id);
         cooldown[id.ordinal()] = roster.cooldown(id);
         if (roster.location(id) == MarkLocation.WORN) {
            worn = id.ordinal();
         }
      }

      return new VeronicaView(location, durability, cooldown, worn, hulkbuster, podTicksLeft);
   }

   public MarkLocation location(MarkId id) {
      int i = id.ordinal() < this.location.length ? this.location[id.ordinal()] : 0;
      return i >= 0 && i < MarkLocation.values().length ? MarkLocation.values()[i] : MarkLocation.STORED;
   }

   public float durability(MarkId id) {
      return id.ordinal() < this.durability.length ? this.durability[id.ordinal()] : 0.0F;
   }

   public float durabilityFraction(MarkId id) {
      return Math.max(0.0F, Math.min(1.0F, this.durability(id) / MarkSpec.of(id).durability()));
   }

   public int cooldown(MarkId id) {
      return id.ordinal() < this.cooldown.length ? this.cooldown[id.ordinal()] : 0;
   }

   /** Same rule as MarkRoster.choosable (display only; the server re-checks). */
   public boolean choosable(MarkId id) {
      MarkLocation location = this.location(id);
      return this.cooldown(id) <= 0 && this.durability(id) > 0.0F && (location == MarkLocation.STORED || location == MarkLocation.EMPTY);
   }

   /** Same rule as MarkRoster.legionCallable (display only; the server re-checks). */
   public boolean legionCallable(MarkId id) {
      return this.location(id) == MarkLocation.STORED && this.cooldown(id) <= 0 && this.durability(id) > 0.0F;
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.location.length);
      for (int i = 0; i < this.location.length; i++) {
         buffer.writeByte(this.location[i]);
         buffer.writeFloat(this.durability[i]);
         buffer.writeVarInt(this.cooldown[i]);
      }

      buffer.writeVarInt(this.wornMark + 1);
      buffer.writeVarInt(this.hulkbuster + 2);
      buffer.writeVarInt(this.podTicksLeft);
   }

   public static VeronicaView read(FriendlyByteBuf buffer) {
      int n = Math.max(0, Math.min(32, buffer.readVarInt()));
      int[] location = new int[n];
      float[] durability = new float[n];
      int[] cooldown = new int[n];
      for (int i = 0; i < n; i++) {
         location[i] = buffer.readByte();
         float d = buffer.readFloat();
         durability[i] = Float.isFinite(d) ? d : 0.0F;
         cooldown[i] = buffer.readVarInt();
      }

      int worn = buffer.readVarInt() - 1;
      int hulkbuster = buffer.readVarInt() - 2;
      int pod = buffer.readVarInt();
      return new VeronicaView(location, durability, cooldown, worn, hulkbuster, pod);
   }
}
