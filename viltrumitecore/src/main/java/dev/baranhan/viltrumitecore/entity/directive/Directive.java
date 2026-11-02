package dev.baranhan.viltrumitecore.entity.directive;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * One instruction written into a body: which rules ({@link DirectiveEffect} bits), who wrote it, an optional subject
 * entity, point and vector, and the game time it ends. Expiry is absolute game time, so a saved directive keeps its
 * remaining time across unloads and restarts without a ticking counter; {@link MobDirectives#MAX_TICKS} caps every
 * directive so nothing is permanent.
 *
 * @param id       the instruction id that produced it (lang key {@code book.viltrumitecore.instruction.<id>})
 * @param effects  bit set of {@link DirectiveEffect}
 * @param flags    end conditions ({@link #BREAK_ON_HURT}, {@link #BREAK_ON_AUTHOR_HIT}, {@link #RESTORE_ON_HEAVY})
 * @param sealed   ordinals of hero actions this directive seals ({@code null} = none); players only
 */
public record Directive(String id, int effects, UUID author, @Nullable UUID subject, @Nullable BlockPos point, @Nullable Vec3 vector,
   long expiresAt, int flags, @Nullable int[] sealed) {
   /** Ends when anybody hurts the body (calm, stand still). */
   public static final int BREAK_ON_HURT = 1;
   /** Ends when the author hurts the body (a mob told to follow does not stay friendly while beaten). */
   public static final int BREAK_ON_AUTHOR_HIT = 2;
   /** A ripped page comes back on a heavy hit or at low health (canon: when Rohan is badly hurt). */
   public static final int RESTORE_ON_HEAVY = 4;

   public boolean has(DirectiveEffect effect) {
      return effect.in(this.effects);
   }

   public boolean flag(int bit) {
      return (this.flags & bit) != 0;
   }

   public boolean expired(long gameTime) {
      return gameTime >= this.expiresAt;
   }

   public int remaining(long gameTime) {
      return (int)Math.max(0L, Math.min(Integer.MAX_VALUE, this.expiresAt - gameTime));
   }

   public boolean seals(int actionOrdinal) {
      if (this.sealed == null) {
         return false;
      }
      for (int s : this.sealed) {
         if (s == actionOrdinal || s == -1) {
            return true;
         }
      }
      return false;
   }

   public Directive withExpiry(long expiresAt) {
      return new Directive(this.id, this.effects, this.author, this.subject, this.point, this.vector, expiresAt, this.flags, this.sealed);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("Id", this.id);
      tag.putInt("Effects", this.effects);
      tag.putUUID("Author", this.author);
      if (this.subject != null) {
         tag.putUUID("Subject", this.subject);
      }
      if (this.point != null) {
         tag.putLong("Point", this.point.asLong());
      }
      if (this.vector != null) {
         tag.putDouble("VX", this.vector.x);
         tag.putDouble("VY", this.vector.y);
         tag.putDouble("VZ", this.vector.z);
      }
      tag.putLong("Until", this.expiresAt);
      tag.putInt("Flags", this.flags);
      if (this.sealed != null) {
         tag.putIntArray("Sealed", this.sealed);
      }
      return tag;
   }

   @Nullable
   public static Directive load(CompoundTag tag) {
      if (!tag.hasUUID("Author") || !tag.contains("Id")) {
         return null;
      }
      return new Directive(tag.getString("Id"), tag.getInt("Effects"), tag.getUUID("Author"), tag.hasUUID("Subject") ? tag.getUUID("Subject") : null,
         tag.contains("Point") ? BlockPos.of(tag.getLong("Point")) : null,
         tag.contains("VX") ? new Vec3(tag.getDouble("VX"), tag.getDouble("VY"), tag.getDouble("VZ")) : null,
         tag.getLong("Until"), tag.getInt("Flags"), tag.contains("Sealed") ? tag.getIntArray("Sealed") : null);
   }
}
