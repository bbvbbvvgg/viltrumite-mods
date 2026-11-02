package dev.baranhan.viltrumitecore.entity.godzilla;

import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/**
 * One hit zone of the kaiju (head, neck, chest, belly, hips, an arm, a leg or a tail segment; index =
 * {@code GodzillaRules.P_*}). Placed by the body every tick on both sides; hits go to the body with the zone's
 * damage factor. Not saved, never collides, never a separate server entity in the entity storage.
 */
public class GodzillaPart extends PartEntity<GodzillaEntity> {
   public final int index;
   public final float damageFactor;
   private final EntityDimensions size;

   public GodzillaPart(GodzillaEntity parent, int index) {
      super(parent);
      this.index = index;
      this.damageFactor = GodzillaRules.PART_FACTOR[index];
      float[] s = GodzillaRules.PART_SIZE[index];
      this.size = EntityDimensions.scalable(s[0] * GodzillaRules.HEIGHT, s[1] * GodzillaRules.HEIGHT);
      this.refreshDimensions();
   }

   public boolean tail() {
      return this.index >= GodzillaRules.P_TAIL;
   }

   @Override
   protected void defineSynchedData() {
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public boolean isPickable() {
      return this.getParent().isAlive();
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      return !this.isInvulnerableTo(source) && this.getParent().hurtFromPart(this, source, amount);
   }

   @Override
   public boolean is(Entity entity) {
      return this == entity || this.getParent() == entity;
   }

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      return this.size;
   }

   @Override
   public boolean shouldBeSaved() {
      return false;
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return this.getParent().isAlliedTo(other);
   }

   @Override
   public ItemStack getPickResult() {
      return ItemStack.EMPTY;
   }

   /** Moves the box to a centre point, keeping the previous position for interpolation. */
   void placeCentre(double cx, double cy, double cz) {
      this.xo = this.getX();
      this.yo = this.getY();
      this.zo = this.getZ();
      this.xOld = this.xo;
      this.yOld = this.yo;
      this.zOld = this.zo;
      this.setPos(cx, cy - this.size.height * 0.5, cz);
   }
}
