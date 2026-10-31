package dev.baranhan.viltrumitecore.entity.chitauri;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/**
 * One hitbox of the Leviathan (snout, body segment or tail). Moved by the head every tick on both
 * sides; hits go to the head with the part's damage factor. Not saved, never collides.
 */
public class LeviathanPart extends PartEntity<ChitauriLeviathan> {
   public static final int SNOUT = -1;
   public final int index;
   public final float damageFactor;
   private final EntityDimensions size;

   public LeviathanPart(ChitauriLeviathan parent, int index, float width, float height, float damageFactor) {
      super(parent);
      this.index = index;
      this.damageFactor = damageFactor;
      this.size = EntityDimensions.scalable(width, height);
      this.refreshDimensions();
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
