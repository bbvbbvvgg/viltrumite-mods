package dev.baranhan.viltrumitecore.hero.thor.scene;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Collision-safe placement of a body a scene moves (C-B2, decision 64: real
 * collisions with surfaces). The wanted point is pulled back toward the
 * holder until the body's box is free and the straight line from the
 * holder's chest to the body crosses no block; nothing ever goes through a
 * wall. Side-neutral: the server places the body with it and every client
 * draws the held body at the same corrected point (ThorSceneView).
 */
public final class SceneSpace {
   /** Samples between the wanted point and the holder's side. */
   static final int STEPS = 8;
   /** Gap kept between the two bodies. */
   static final double SKIN = 0.05;

   private SceneSpace() {
   }

   /** The world checks a placement needs (pure tests fake them). */
   public interface Probe {
      /** Is the body's box free at these feet? */
      boolean free(Vec3 feet);

      /** How far (0..1 of the way) from {@code from} to {@code to} until a block is hit; 1 = clear. */
      double reach(Vec3 from, Vec3 to);
   }

   /**
    * Pure: the body's feet nearest to {@code wanted} on the segment from the
    * holder's side ({@code near}) to {@code wanted} that is free and that the
    * holder's chest sees, or null when even the near point is blocked.
    *
    * @param chest     the holder's chest (start of the line of sight)
    * @param near      the closest the body may be to the holder (feet)
    * @param wanted    the posed point (feet)
    * @param halfHeight half the body's height (the line of sight aims at its centre)
    */
   @Nullable
   public static Vec3 place(Probe probe, Vec3 chest, Vec3 near, Vec3 wanted, double halfHeight) {
      Vec3 lift = new Vec3(0.0, halfHeight, 0.0);
      for (int i = STEPS; i >= 0; i--) {
         Vec3 feet = near.lerp(wanted, i / (double)STEPS);
         // The holder's chest must see the body's centre: a block on that line means the body is behind or inside a wall.
         if (probe.reach(chest, feet.add(lift)) < 1.0) {
            continue;
         }

         if (probe.free(feet)) {
            return feet;
         }
      }

      return null;
   }

   /** Server and client: a held body's corrected feet for {@code wanted}, or null when there is no free point at all. */
   @Nullable
   public static Vec3 placeHeld(Level level, Entity holder, Entity body, Vec3 holderFeet, float holderYaw, Vec3 wanted) {
      Vec3 chest = holderFeet.add(0.0, holder.getBbHeight() * 0.6, 0.0);
      Vec3 forward = SceneFrame.forward(holderYaw);
      Vec3 near = new Vec3(holderFeet.x, wanted.y, holderFeet.z).add(forward.scale(holder.getBbWidth() * 0.5 + body.getBbWidth() * 0.5 + SKIN));
      return place(probe(level, body), chest, near, wanted, body.getBbHeight() * 0.5);
   }

   /** The Minecraft probe for one body. */
   public static Probe probe(Level level, Entity body) {
      return new Probe() {
         @Override
         public boolean free(Vec3 feet) {
            return boxFree(level, body, feet);
         }

         @Override
         public double reach(Vec3 from, Vec3 to) {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
            if (hit.getType() == HitResult.Type.MISS) {
               return 1.0;
            }

            double length = from.distanceTo(to);
            return length < 1.0E-6 ? 1.0 : from.distanceTo(hit.getLocation()) / length;
         }
      };
   }

   /** Is the body's box free (blocks, world border) at these feet? */
   public static boolean boxFree(Level level, Entity body, Vec3 feet) {
      AABB box = body.getBoundingBox().move(feet.subtract(body.position()));
      return level.getWorldBorder().isWithinBounds(box) && level.noCollision(body, box);
   }
}
