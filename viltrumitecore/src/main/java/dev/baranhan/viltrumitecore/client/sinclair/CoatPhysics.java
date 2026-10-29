package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Secondary motion of cloth, hair and ears (spec §3, §5.3, §5.4): Sinclair's two-segment coat tails
 * (speed, turning, falling, crouching, sharp gestures), lapels and vest hem (torso lean), Savannah's
 * three dress tiers, smoke trail and hair, the hex rabbit's ears. Springs run on real time from the
 * entity's interpolated motion; added on top of the clip pose, never replacing it.
 * Angles use the clip (Bedrock JSON) convention: x- swings a hanging part forward.
 */
public final class CoatPhysics {
   private static final Map<Entity, Cloth> CLOTH = new WeakHashMap<>();

   private CoatPhysics() {
   }

   /** Sinclair body (player or decoy). {@code kick} > 0 = a sharp gesture this frame (shot, move start). */
   public static void sinclair(BakedGeoModel model, Entity entity, float bodyYaw, float partialTick, boolean crouching, float kick) {
      Cloth c = cloth(entity, bodyYaw, partialTick);
      if (kick > 0.0F) {
         c.a.kick(kick);
         c.b.kick(kick * 1.4F);
      }

      float flare = Mth.clamp(0.9F * c.forward * Math.abs(c.forward), -12.0F, 55.0F) + Mth.clamp(-c.vertical * 5.0F, -6.0F, 50.0F) + (crouching ? 10.0F : 0.0F);
      float sway = Mth.clamp(-c.side * 5.0F - c.yawRate * 0.05F, -30.0F, 30.0F);
      float tail = c.a.step(flare, c.dt);
      float tail2 = c.b.step(tail * 0.65F + flare * 0.25F, c.dt);
      float roll = c.c.step(sway, c.dt);
      GeoBone body = model.getBone("body");
      float lean = body == null ? 0.0F : (float) -Math.toDegrees(body.rotX);
      float lapel = c.d.step(Mth.clamp(-0.7F * lean + Mth.clamp(c.forward * 1.2F, 0.0F, 8.0F), -40.0F, 10.0F), c.dt);
      float hem = c.e.step(Mth.clamp(-0.5F * lean, -25.0F, 6.0F), c.dt);
      add(model, "coat_tail_right", tail, 0.0F, roll);
      add(model, "coat_tail_left", tail, 0.0F, -roll);
      add(model, "coat_tail_right2", tail2 - tail * 0.35F, 0.0F, roll * 0.5F);
      add(model, "coat_tail_left2", tail2 - tail * 0.35F, 0.0F, -roll * 0.5F);
      add(model, "lapel_right", lapel, 0.0F, 0.0F);
      add(model, "lapel_left", lapel, 0.0F, 0.0F);
      add(model, "vest_hem", hem, 0.0F, 0.0F);
      add(model, "medallion", lapel * 0.6F, 0.0F, roll * 0.3F);
   }

   /** Savannah: dress tiers lag in a chain, the smoke trail streams behind, hair follows the head. */
   public static void savannah(BakedGeoModel model, Entity entity, float bodyYaw, float partialTick) {
      Cloth c = cloth(entity, bodyYaw, partialTick);
      float stream = Mth.clamp(c.forward * 6.0F, -20.0F, 45.0F) + Mth.clamp(-c.vertical * 4.0F, -10.0F, 25.0F);
      float sway = Mth.clamp(-c.side * 6.0F - c.yawRate * 0.06F, -30.0F, 30.0F);
      float t1 = c.a.step(stream * 0.3F, c.dt);
      float t2 = c.b.step(t1 * 0.7F + stream * 0.3F, c.dt);
      float t3 = c.c.step(t2 * 0.8F + stream * 0.35F, c.dt);
      float roll = c.d.step(sway, c.dt);
      float hair = c.e.step(Mth.clamp(c.forward * 4.0F - c.vertical * 3.0F, -15.0F, 40.0F), c.dt);
      add(model, "tier1", t1, 0.0F, roll * 0.3F);
      add(model, "tier2", t2 - t1 * 0.5F, 0.0F, roll * 0.3F);
      add(model, "tier3", t3 - t2 * 0.5F, 0.0F, roll * 0.4F);
      add(model, "trail", t3 * 0.6F, 0.0F, roll * 0.5F);
      add(model, "trail2", t3 * 0.5F, 0.0F, roll * 0.6F);
      add(model, "trail3", t3 * 0.4F, 0.0F, roll * 0.7F);
      add(model, "hair", hair * 0.4F, 0.0F, roll * 0.2F);
      add(model, "hair_mid", hair * 0.6F, 0.0F, roll * 0.3F);
      add(model, "hair_tip", hair * 0.8F, 0.0F, roll * 0.4F);
   }

   /** The hex rabbit: long ears flop on hops and turns (two segments each). */
   public static void rabbit(BakedGeoModel model, Entity entity, float bodyYaw, float partialTick) {
      Cloth c = cloth(entity, bodyYaw, partialTick);
      float flop = Mth.clamp(c.vertical * 7.0F + c.forward * 3.0F, -45.0F, 45.0F);
      float ear = c.a.step(flop, c.dt);
      float tip = c.b.step(ear * 0.8F, c.dt);
      float roll = c.c.step(Mth.clamp(-c.yawRate * 0.06F - c.side * 5.0F, -30.0F, 30.0F), c.dt);
      add(model, "ear_right", ear, 0.0F, roll);
      add(model, "ear_left", ear, 0.0F, -roll);
      add(model, "ear_right2", tip - ear * 0.3F, 0.0F, roll * 0.5F);
      add(model, "ear_left2", tip - ear * 0.3F, 0.0F, -roll * 0.5F);
      add(model, "tail", -ear * 0.3F, 0.0F, 0.0F);
   }

   public static void forget(Entity entity) {
      CLOTH.remove(entity);
   }

   /** Adds clip-convention degrees to a bone (BakedGeoModel stores -x, -y, +z radians). */
   static void add(BakedGeoModel model, String name, float rx, float ry, float rz) {
      GeoBone bone = model.getBone(name);
      if (bone != null) {
         bone.rotX -= (float) Math.toRadians(rx);
         bone.rotY -= (float) Math.toRadians(ry);
         bone.rotZ += (float) Math.toRadians(rz);
      }
   }

   private static Cloth cloth(Entity entity, float bodyYaw, float partialTick) {
      Cloth c = CLOTH.computeIfAbsent(entity, e -> new Cloth());
      c.sample(entity, bodyYaw, partialTick, Util.getNanos());
      return c;
   }

   /** Per-entity motion sample and five springs (meaning per model). */
   static final class Cloth {
      final Spring a = new Spring(90.0F, 9.0F, 80.0F);
      final Spring b = new Spring(60.0F, 6.5F, 90.0F);
      final Spring c = new Spring(70.0F, 7.0F, 45.0F);
      final Spring d = new Spring(140.0F, 12.0F, 50.0F);
      final Spring e = new Spring(110.0F, 10.0F, 50.0F);
      long lastNanos = -1L;
      Vec3 lastPos;
      float lastYaw;
      /** Body-space speed, blocks per second: forward, right, up; yaw rate in degrees per second. */
      float forward;
      float side;
      float vertical;
      float yawRate;
      float dt;

      void sample(Entity entity, float bodyYaw, float partialTick, long now) {
         Vec3 pos = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()), Mth.lerp(partialTick, entity.zo, entity.getZ()));
         if (this.lastNanos < 0L) {
            this.lastNanos = now;
            this.lastPos = pos;
            this.lastYaw = bodyYaw;
            this.dt = 0.0F;
            return;
         }

         float dt = (now - this.lastNanos) / 1.0E9F;
         this.dt = dt;
         if (dt < 1.0E-4F) {
            return;
         }

         if (dt > Spring.MAX_DT) {
            this.forward = this.side = this.vertical = this.yawRate = 0.0F;
         } else {
            Vec3 v = pos.subtract(this.lastPos).scale(1.0 / dt);
            double rad = Math.toRadians(bodyYaw);
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            float f = (float) (v.x * fx + v.z * fz);
            float s = (float) (v.x * -fz + v.z * fx);
            float k = Math.min(1.0F, dt * 12.0F);
            this.forward += (f - this.forward) * k;
            this.side += (s - this.side) * k;
            this.vertical += ((float) v.y - this.vertical) * k;
            this.yawRate += (Mth.wrapDegrees(bodyYaw - this.lastYaw) / dt - this.yawRate) * k;
         }

         this.lastNanos = now;
         this.lastPos = pos;
         this.lastYaw = bodyYaw;
      }
   }
}
