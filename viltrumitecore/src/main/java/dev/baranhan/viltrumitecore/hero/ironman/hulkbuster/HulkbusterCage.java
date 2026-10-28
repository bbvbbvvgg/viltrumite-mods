package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Veronica's containment cage (rework spec §6.7, film canon): a mark on the look (≤ 30 blocks), a shadow
 * warning for 1.5 s, then a 3x3 cage drops. Whoever stands inside is held (FREEZE) up to 3 s or until they
 * break it (its own durability; a superstrong victim breaks it twice as fast). A miss leaves an empty cage
 * for 2 s. One cage per Hulkbuster; it lives in the kit and dies with the suit. The client draws it from the
 * snapshot (actionTarget = the cage point, HulkbusterWire.CAGE = phase and ticks).
 */
public final class HulkbusterCage {
   public enum Phase {
      MARKED,
      HOLDING,
      EMPTY
   }

   public static final double HALF = 1.5;
   public static final double HEIGHT = 3.0;

   public final Vec3 point;
   public Phase phase = Phase.MARKED;
   public int ticks;
   public float durability = HulkbusterRules.CAGE_DURABILITY;
   private final List<UUID[]> held = new ArrayList<>();
   private final List<LivingEntity> trapped = new ArrayList<>();

   private HulkbusterCage(Vec3 point) {
      this.point = point;
   }

   /** Slot 3 contact: mark the point (block or entity under the crosshair, on the ground below it). */
   public static void mark(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      release(player, state, ReleaseReason.NORMAL_END);
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(HulkbusterRules.CAGE_RANGE));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 aim = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
      LivingEntity entity = null;
      double best = Double.MAX_VALUE;
      for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(eye, aim).inflate(1.5),
         e -> e != player && IronManTargets.canHit(player, e))) {
         Vec3 rel = e.getBoundingBox().getCenter().subtract(eye);
         Vec3 dir = aim.subtract(eye).normalize();
         double along = rel.dot(dir);
         if (along > 0.0 && rel.subtract(dir.scale(along)).length() < 1.2 && along < best) {
            best = along;
            entity = e;
         }
      }

      Vec3 spot = entity != null ? entity.position() : aim;
      BlockHitResult down = player.level().clip(new ClipContext(spot.add(0.0, 0.5, 0.0), spot.add(0.0, -24.0, 0.0), ClipContext.Block.COLLIDER,
         ClipContext.Fluid.NONE, player));
      Vec3 ground = down.getType() == HitResult.Type.MISS ? spot : down.getLocation();
      kit.cage = new HulkbusterCage(ground);
      kit.cageCooldown = HulkbusterRules.CAGE_COOLDOWN;
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.CAGE_FALL, ground, 1.0F);
   }

   public static void tick(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      HulkbusterCage cage = kit.cage;
      if (cage == null) {
         return;
      }

      cage.ticks++;
      ServerLevel level = player.serverLevel();
      switch (cage.phase) {
         case MARKED -> {
            if (cage.ticks >= HulkbusterRules.CAGE_DROP_DELAY) {
               cage.land(player);
            }
         }
         case HOLDING -> {
            cage.trapped.removeIf(e -> !e.isAlive() || e.level() != level);
            for (LivingEntity e : cage.trapped) {
               Vec3 keep = new Vec3(cage.point.x, e.getY(), cage.point.z);
               if (e.position().distanceTo(keep) > HALF - 0.4) {
                  Vec3 back = keep.add(e.position().subtract(keep).normalize().scale(HALF - 0.6));
                  e.setPos(back.x, e.getY(), back.z);
               }
            }

            if (cage.trapped.isEmpty() || cage.durability <= 0.0F || cage.ticks >= HulkbusterRules.CAGE_DROP_DELAY + HulkbusterRules.CAGE_HOLD) {
               if (cage.durability <= 0.0F) {
                  HeroFx.shards(player, cage.point.add(0.0, 1.5, 0.0), 0.8F, null, new float[0], null);
               }

               release(player, state, ReleaseReason.NORMAL_END);
            }
         }
         case EMPTY -> {
            if (cage.ticks >= HulkbusterRules.CAGE_DROP_DELAY + HulkbusterRules.CAGE_EMPTY_LINGER) {
               kit.cage = null;
            }
         }
      }
   }

   private void land(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      AABB box = new AABB(this.point.x - HALF, this.point.y, this.point.z - HALF, this.point.x + HALF, this.point.y + HEIGHT, this.point.z + HALF);
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && IronManTargets.canHit(player, e))) {
         if (!HeroRegistry.allowsExternalControl(e, ControlKind.FREEZE)) {
            continue;
         }

         UUID effect = UUID.randomUUID();
         if (ControlManager.get(level).tryAcquire(e, player.getUUID(), effect, ControlKind.FREEZE,
            level.getGameTime() + HulkbusterRules.CAGE_HOLD + 20L)) {
            this.held.add(new UUID[]{e.getUUID(), effect});
            this.trapped.add(e);
            if (e instanceof Player victim && HulkbusterTags.superstrong(victim)) {
               this.durability *= 0.5F;
            }
         }
      }

      this.phase = this.trapped.isEmpty() ? Phase.EMPTY : Phase.HOLDING;
      BlockPos ground = BlockPos.containing(this.point.x, this.point.y - 0.5, this.point.z);
      HeroFx.slam(player, this.point, level.getBlockState(ground), 3.0F);
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.CAGE_LAND, this.point, 1.0F);
   }

   /** A trapped player hits or struggles: the cage loses durability. */
   public static boolean struggle(ServerPlayer victim, IronManState state, float amount) {
      HulkbusterCage cage = state.hulkKit.cage;
      if (cage == null || cage.phase != Phase.HOLDING || !cage.trapped.contains(victim)) {
         return false;
      }

      cage.durability -= amount;
      return true;
   }

   public static void release(ServerPlayer player, IronManState state, ReleaseReason reason) {
      HulkbusterCage cage = state.hulkKit.cage;
      if (cage == null) {
         return;
      }

      ControlManager controls = ControlManager.get(player.serverLevel());
      for (UUID[] pair : cage.held) {
         controls.release(pair[0], pair[1], reason);
      }

      cage.held.clear();
      cage.trapped.clear();
      state.hulkKit.cage = null;
   }

   @Nullable
   public static Vec3 point(IronManState state) {
      return state.hulkKit.cage == null ? null : state.hulkKit.cage.point;
   }

   /** Wire value: phase (bits 12+) and ticks; 0 = no cage. */
   public static int wire(IronManState state) {
      HulkbusterCage cage = state.hulkKit.cage;
      return cage == null ? 0 : (cage.phase.ordinal() + 1) << 12 | Math.min(4095, cage.ticks);
   }
}
