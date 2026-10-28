package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.sinclair.Ovation;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rabbit Hex telegraph (spec §5.4): a spectral top hat spins 3 blocks over the ground point for 18 ticks
 * above a violet circle, then everyone in the 3.5 cylinder turns into a rabbit. The entity stands at the
 * ground point; the client draws the hat at +3 and the circle at 0.
 */
public class HexHatEntity extends SinclairMarker {
   /** Tick count of the poof, -1 before (client: burst and the hat dive). */
   private static final EntityDataAccessor<Integer> POOF = SynchedEntityData.defineId(HexHatEntity.class, EntityDataSerializers.INT);
   public static final int AFTER_POOF = 8;
   private int age;

   public HexHatEntity(EntityType<? extends HexHatEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(POOF, -1);
   }

   public void setup(ServerPlayer owner, Vec3 ground) {
      this.bindOwner(owner);
      this.setPos(ground);
      this.setYRot(owner.getYRot());
   }

   public int poofTick() {
      return this.entityData.get(POOF);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         return;
      }

      ServerPlayer owner = SinclairOwned.liveOwner(this, this.owner);
      if (owner == null) {
         this.discard();
         return;
      }

      this.age++;
      if (this.age == SinclairRules.HEX_DELAY) {
         this.poof(owner);
      } else if (this.age >= SinclairRules.HEX_DELAY + AFTER_POOF) {
         this.discard();
      }
   }

   private void poof(ServerPlayer owner) {
      this.entityData.set(POOF, this.tickCount);
      Vec3 at = this.position();
      AABB box = new AABB(at.x - SinclairRules.HEX_RADIUS - 1, at.y - SinclairRules.HEX_VERTICAL - 1, at.z - SinclairRules.HEX_RADIUS - 1,
         at.x + SinclairRules.HEX_RADIUS + 1, at.y + SinclairRules.HEX_VERTICAL + 1, at.z + SinclairRules.HEX_RADIUS + 1);
      int turned = 0;
      for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive())) {
         Vec3 p = target.position();
         if (!SinclairRules.inHexArea(p.x - at.x, p.y - at.y, p.z - at.z) || !SinclairCombat.hexable(owner, target)) {
            continue;
         }

         HexMarkEntity old = dev.baranhan.viltrumitecore.hero.sinclair.HexMarks.markOf(target);
         if (old != null) {
            old.discard();
         }

         HexMarkEntity mark = SinclairEntities.HEX_MARK.get().create(this.level());
         if (mark == null) {
            continue;
         }

         mark.setup(owner, target, SinclairRules.hexTicks(target instanceof Player));
         this.level().addFreshEntity(mark);
         turned++;
      }

      SinclairSounds.play(this.level(), at.add(0.0, 1.0, 0.0), SinclairSounds.HEX_POOF, 1.0F, 1.0F);
      if (turned > 0) {
         Ovation.grant(owner);
      }
   }
}
