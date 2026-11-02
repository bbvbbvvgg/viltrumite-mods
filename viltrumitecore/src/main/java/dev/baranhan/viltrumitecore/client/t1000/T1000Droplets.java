package dev.baranhan.viltrumitecore.client.t1000;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import dev.baranhan.viltrumitecore.t1000.T1000Form;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Loose liquid metal (design §1.6): the splash out of a fresh wound, the burst of a melt or a heavy blow, the
 * shards of a frozen body shattering, the beads crawling home on the reform. Every drop is a little chrome blob
 * with the body's matcap; it flies with gravity, lands, flattens into a bead and then crawls across the ground back
 * to its T-1000 and merges (or boils away when its owner is gone). Client-only and purely cosmetic: spawned from
 * the synced entity events, so every player sees the same splashes.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = {Dist.CLIENT})
public final class T1000Droplets {
   static final int CAP = 260;
   static final double GRAVITY = 0.055;
   static final int MAX_LIFE = 420;
   private static final List<Drop> DROPS = new ArrayList<>();
   private static final RandomSource RANDOM = RandomSource.create();
   private static final float[] UV = new float[2];
   private static final Vector4f V4 = new Vector4f();
   private static final Vector3f V3 = new Vector3f();
   // blob tessellation: latitude x longitude bands
   private static final int LAT = 5;
   private static final int LON = 8;

   private T1000Droplets() {
   }

   static final class Drop {
      double x;
      double y;
      double z;
      double ox;
      double oy;
      double oz;
      double vx;
      double vy;
      double vz;
      float size;
      int age;
      int owner;
      boolean shard;
      boolean landed;
      /** Shards: ticks until they thaw into a drop. */
      int thaw;
      float spin;
      float spinAxis;
      float fade = 1.0F;
   }

   /** Number of live drops (debug and tests). */
   public static int count() {
      return DROPS.size();
   }

   /** Entity event from {@code T1000Entity.handleEntityEvent} (client main thread). */
   public static void onEvent(T1000Entity e, byte id) {
      switch (id) {
         case T1000Entity.EVENT_SPLASH -> splash(e);
         case T1000Entity.EVENT_BURST -> burst(e, 46, 0.32, false);
         case T1000Entity.EVENT_SHATTER -> burst(e, 56, 0.38, true);
         case T1000Entity.EVENT_RETURN -> gather(e);
         default -> {
         }
      }
   }

   /** World position of a rest-model point (pixels) of the entity, ignoring the pose (good enough for spray). */
   static Vec3 modelToWorld(T1000Entity e, float mx, float my, float mz) {
      float w = e.look().width() * 0.9375F;
      float h = e.look().height() * 0.9375F;
      double lx = -mx / 16.0 * w;
      double ly = (1.501 - my / 16.0) * h;
      double lz = mz / 16.0 * w;
      double a = Math.toRadians(180.0F - e.yBodyRot);
      double c = Math.cos(a);
      double s = Math.sin(a);
      return new Vec3(e.getX() + lx * c + lz * s, e.getY() + ly, e.getZ() - lx * s + lz * c);
   }

   private static void splash(T1000Entity e) {
      List<T1000Entity.Wound> wounds = e.wounds();
      T1000Entity.Wound newest = null;
      for (T1000Entity.Wound w : wounds) {
         if (newest == null || w.start() >= newest.start()) {
            newest = w;
         }
      }
      Vec3 at;
      Vec3 out;
      if (newest != null) {
         at = modelToWorld(e, newest.x(), newest.y(), newest.z());
         Vec3 centre = new Vec3(e.getX(), at.y, e.getZ());
         out = at.subtract(centre);
      } else {
         at = e.position().add(0.0, e.getBbHeight() * 0.6, 0.0);
         out = Vec3.ZERO;
      }
      if (out.lengthSqr() < 1.0E-4) {
         float hy = (float)Math.toRadians(e.lastHitYaw());
         out = new Vec3(Mth.sin(hy), 0.0, -Mth.cos(hy));
      }
      out = out.normalize();
      int n = 9 + RANDOM.nextInt(6);
      for (int i = 0; i < n; i++) {
         double sp = 0.08 + RANDOM.nextDouble() * 0.16;
         Vec3 v = out.scale(sp).add(gauss(0.06), 0.06 + RANDOM.nextDouble() * 0.14, gauss(0.06));
         add(e, at.add(gauss(0.05), gauss(0.05), gauss(0.05)), v, 0.035F + RANDOM.nextFloat() * 0.05F, false);
      }
   }

   private static void burst(T1000Entity e, int n, double speed, boolean shards) {
      double h = Math.max(0.4, e.getBbHeight());
      for (int i = 0; i < n; i++) {
         double y = e.getY() + RANDOM.nextDouble() * h;
         double ang = RANDOM.nextDouble() * Math.PI * 2.0;
         double r = 0.1 + RANDOM.nextDouble() * 0.25;
         Vec3 at = new Vec3(e.getX() + Math.cos(ang) * r, y, e.getZ() + Math.sin(ang) * r);
         double sp = speed * (0.35 + RANDOM.nextDouble() * 0.65);
         Vec3 v = new Vec3(Math.cos(ang) * sp, 0.05 + RANDOM.nextDouble() * speed * 0.7, Math.sin(ang) * sp);
         add(e, at, v, (shards ? 0.05F : 0.04F) + RANDOM.nextFloat() * (shards ? 0.08F : 0.06F), shards);
      }
   }

   /** The reform: beads scattered around the puddle all start crawling in at once. */
   private static void gather(T1000Entity e) {
      int n = 22;
      for (int i = 0; i < n; i++) {
         double ang = RANDOM.nextDouble() * Math.PI * 2.0;
         double r = 1.2 + RANDOM.nextDouble() * 2.2;
         Vec3 at = new Vec3(e.getX() + Math.cos(ang) * r, e.getY() + 0.6, e.getZ() + Math.sin(ang) * r);
         Drop d = add(e, at, new Vec3(0.0, -0.05, 0.0), 0.04F + RANDOM.nextFloat() * 0.05F, false);
         if (d != null) {
            d.age = 6;
         }
      }
   }

   private static Drop add(T1000Entity e, Vec3 at, Vec3 v, float size, boolean shard) {
      if (DROPS.size() >= CAP) {
         DROPS.remove(0);
      }
      Drop d = new Drop();
      d.x = d.ox = at.x;
      d.y = d.oy = at.y;
      d.z = d.oz = at.z;
      d.vx = v.x;
      d.vy = v.y;
      d.vz = v.z;
      d.size = size;
      d.owner = e.getId();
      d.shard = shard;
      d.thaw = shard ? 50 + RANDOM.nextInt(30) : 0;
      d.spin = RANDOM.nextFloat() * 360.0F;
      d.spinAxis = RANDOM.nextFloat() * 6.28F;
      DROPS.add(d);
      return d;
   }

   private static double gauss(double s) {
      return RANDOM.nextGaussian() * s;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END || DROPS.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         DROPS.clear();
         return;
      }
      if (mc.isPaused()) {
         return;
      }
      Iterator<Drop> it = DROPS.iterator();
      while (it.hasNext()) {
         Drop d = it.next();
         if (!step(level, d)) {
            it.remove();
         }
      }
   }

   /** One tick of a drop; false once it merged or evaporated. */
   static boolean step(ClientLevel level, Drop d) {
      d.age++;
      d.ox = d.x;
      d.oy = d.y;
      d.oz = d.z;
      Entity owner = level.getEntity(d.owner);
      T1000Entity t = owner instanceof T1000Entity te && te.isAlive() ? te : null;
      boolean dying = owner instanceof T1000Entity te && te.form() == T1000Form.DYING;
      if (t == null || dying || d.age > MAX_LIFE) {
         // orphaned: the metal dulls and boils away where it lies
         d.fade -= dying ? 0.012F : 0.03F;
         if (d.fade <= 0.0F) {
            return false;
         }
      }
      if (d.shard && d.thaw > 0 && d.landed) {
         if (--d.thaw == 0) {
            d.shard = false;
         }
         return true;
      }
      if (!d.landed) {
         d.vy -= GRAVITY;
         d.vx *= 0.985;
         d.vz *= 0.985;
         d.vy *= 0.985;
         move(level, d);
         return true;
      }
      if (t == null || d.age < 14) {
         return true;
      }
      // crawl home across the ground, faster as it gets going, a little wobble in the path
      double dx = t.getX() - d.x;
      double dz = t.getZ() - d.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      double dy = t.getY() - d.y;
      if (dist < 0.32 + t.getBbWidth() * 0.3 && Math.abs(dy) < 1.6) {
         return false; // merged
      }
      double speed = Math.min(0.16, 0.02 + (d.age - 14) * 0.0025);
      double wob = Math.sin(d.age * 0.35 + d.spinAxis * 3.0) * 0.3;
      double ux = dx / dist;
      double uz = dz / dist;
      double nx = d.x + (ux - uz * wob) * speed;
      double nz = d.z + (uz + ux * wob) * speed;
      BlockPos ahead = BlockPos.containing(nx, d.y + 0.05, nz);
      if (level.getBlockState(ahead).isCollisionShapeFullBlock(level, ahead)) {
         // a step: flow up it if the owner is above, otherwise slide along
         if (dy > 0.3 && !level.getBlockState(ahead.above()).isCollisionShapeFullBlock(level, ahead.above())) {
            d.y = ahead.getY() + 1.0 + 0.01;
            d.x = nx;
            d.z = nz;
         }
         return true;
      }
      d.x = nx;
      d.z = nz;
      BlockPos below = BlockPos.containing(d.x, d.y - 0.08, d.z);
      if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
         d.landed = false; // over an edge: fall again
         d.vx = ux * speed * 0.5;
         d.vz = uz * speed * 0.5;
         d.vy = 0.0;
      }
      return true;
   }

   private static void move(ClientLevel level, Drop d) {
      Vec3 from = new Vec3(d.x, d.y, d.z);
      Vec3 to = new Vec3(d.x + d.vx, d.y + d.vy, d.z + d.vz);
      BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
      if (hit.getType() == HitResult.Type.MISS) {
         d.x = to.x;
         d.y = to.y;
         d.z = to.z;
         if (d.y < level.getMinBuildHeight() - 4) {
            d.fade = 0.0F;
         }
         return;
      }
      Vec3 at = hit.getLocation();
      Direction face = hit.getDirection();
      d.x = at.x + face.getStepX() * 0.01;
      d.y = at.y + face.getStepY() * 0.01;
      d.z = at.z + face.getStepZ() * 0.01;
      if (face == Direction.UP) {
         d.landed = true;
         d.vx = d.vy = d.vz = 0.0;
      } else if (face == Direction.DOWN) {
         d.vy = -d.vy * 0.2;
      } else {
         // a wall: the drop sticks for a moment and runs down it
         d.vx = face.getStepX() * 0.01;
         d.vz = face.getStepZ() * 0.01;
         d.vy = Math.min(d.vy, 0.0) * 0.3;
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      DROPS.clear();
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || DROPS.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      float pt = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      RenderType type = RenderType.entityTranslucentCull(T1000Matcap.texture());
      VertexConsumer vc = buffers.getBuffer(type);
      Matrix4f m = stack.last().pose();
      Matrix3f nm = stack.last().normal();
      for (Drop d : DROPS) {
         double x = Mth.lerp(pt, d.ox, d.x);
         double y = Mth.lerp(pt, d.oy, d.y);
         double z = Mth.lerp(pt, d.oz, d.z);
         if (cam.distanceToSqr(x, y, z) > 64.0 * 64.0) {
            continue;
         }
         int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.1, z));
         blob(vc, m, nm, d, (float)(x - cam.x), (float)(y - cam.y), (float)(z - cam.z), light, pt);
      }
      buffers.endBatch(type);
   }

   /** A small chrome blob: stretched along its flight, flattened into a bead on the ground, a faceted shard. */
   private static void blob(VertexConsumer vc, Matrix4f m, Matrix3f nm, Drop d, float cx, float cy, float cz, int light, float pt) {
      float s = d.size;
      float sx = s;
      float sy = s;
      float sz = s;
      float ex = 0.0F;
      float ey = 0.0F;
      float ez = 0.0F;
      if (!d.landed) {
         // stretch along the velocity
         float v = (float)Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
         float st = Math.min(2.2F, v * 9.0F);
         if (v > 1.0E-4F) {
            ex = (float)(d.vx / v) * st * s;
            ey = (float)(d.vy / v) * st * s;
            ez = (float)(d.vz / v) * st * s;
         }
      } else {
         float spread = d.shard ? 1.0F : 1.0F + Math.min(0.5F, d.age * 0.02F);
         sx = s * 1.35F * spread;
         sz = s * 1.35F * spread;
         sy = d.shard ? s : s * 0.42F;
         cy += sy * 0.6F;
         // crawling beads pulse like they pull themselves along
         sx *= 1.0F + 0.12F * Mth.sin((d.age + pt) * 0.7F + d.spinAxis);
      }
      float tr = 0.93F;
      float tg = 0.96F;
      float tb = 1.0F;
      if (d.shard) {
         tr = 0.84F;
         tg = 0.94F;
         tb = 1.0F;
      }
      float dull = 1.0F - d.fade;
      tr *= 1.0F - 0.5F * dull;
      tg *= 1.0F - 0.5F * dull;
      tb *= 1.0F - 0.45F * dull;
      float alpha = Mth.clamp(d.fade, 0.0F, 1.0F);
      int lat = d.shard ? 2 : LAT;
      int lon = d.shard ? 4 : LON;
      float rot = d.shard ? (float)Math.toRadians(d.spin + (d.landed ? 0.0F : (d.age + pt) * 23.0F)) : 0.0F;
      float cr = Mth.cos(rot);
      float sr = Mth.sin(rot);
      for (int a = 0; a < lat; a++) {
         for (int b = 0; b < lon; b++) {
            corner(vc, m, nm, a, b, lat, lon, cx, cy, cz, sx, sy, sz, ex, ey, ez, cr, sr, light, tr, tg, tb, alpha);
            corner(vc, m, nm, a + 1, b, lat, lon, cx, cy, cz, sx, sy, sz, ex, ey, ez, cr, sr, light, tr, tg, tb, alpha);
            corner(vc, m, nm, a + 1, b + 1, lat, lon, cx, cy, cz, sx, sy, sz, ex, ey, ez, cr, sr, light, tr, tg, tb, alpha);
            corner(vc, m, nm, a, b + 1, lat, lon, cx, cy, cz, sx, sy, sz, ex, ey, ez, cr, sr, light, tr, tg, tb, alpha);
         }
      }
   }

   private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f nm, int a, int b, int lat, int lon, float cx, float cy, float cz, float sx, float sy,
      float sz, float ex, float ey, float ez, float cr, float sr, int light, float tr, float tg, float tb, float alpha) {
      float th = Mth.PI * a / lat;
      float ph = Mth.TWO_PI * b / lon;
      float ux = Mth.sin(th) * Mth.cos(ph);
      float uy = Mth.cos(th);
      float uz = Mth.sin(th) * Mth.sin(ph);
      // rotate (shards tumble), squash, stretch the trailing half along the flight
      float rx = ux * cr - uy * sr;
      float ry = ux * sr + uy * cr;
      float px = rx * sx;
      float py = ry * sy;
      float pz = uz * sz;
      float trail = Math.max(0.0F, -(rx * nz(ex, ey, ez, 0) + ry * nz(ex, ey, ez, 1) + uz * nz(ex, ey, ez, 2)));
      px -= ex * trail;
      py -= ey * trail;
      pz -= ez * trail;
      V4.set(cx + px, cy + py, cz + pz, 1.0F);
      m.transform(V4);
      V3.set(rx / Math.max(0.2F, sx / sy), ry, uz / Math.max(0.2F, sz / sy));
      nm.transform(V3);
      V3.normalize();
      T1000Matcap.uv(V4.x, V4.y, V4.z, V3.x, V3.y, V3.z, UV);
      vc.vertex(V4.x, V4.y, V4.z, tr, tg, tb, alpha, UV[0], UV[1], OverlayTexture.NO_OVERLAY, light, V3.x, V3.y, V3.z);
   }

   private static float nz(float ex, float ey, float ez, int c) {
      float l = (float)Math.sqrt(ex * ex + ey * ey + ez * ez);
      if (l < 1.0E-5F) {
         return 0.0F;
      }
      return (c == 0 ? ex : c == 1 ? ey : ez) / l;
   }
}
