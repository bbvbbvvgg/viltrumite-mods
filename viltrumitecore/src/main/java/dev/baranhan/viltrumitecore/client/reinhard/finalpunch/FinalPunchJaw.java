package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchTargets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The jaw THE FINAL PUNCH knocks off: a separate client-only piece cut from the target's own
 * texture (player skin / villager / iron golem layouts, {@link FinalPunchTargets.Jaw}) that holds
 * through the hit-stop, flies off spinning, bounces and lies on the ground; plus the wound left on
 * the target's face. Both are visual only and temporary ({@link FinalPunchRules#JAW_TICKS}),
 * capped, the same on every client (seeded by the scene), and cleared on logout / level change.
 */
public final class FinalPunchJaw {
   static final int MAX = 12;
   static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

   /** Texture layout of one jaw: head UV origin, head size and the jaw's share (px). */
   record Layout(int texW, int texH, int headW, int headH, int headD, int jawH, int jawD) {
      static Layout of(FinalPunchTargets.Jaw jaw) {
         return switch (jaw) {
            case PLAYER_SKIN -> new Layout(64, 64, 8, 8, 8, 2, 5);
            case VILLAGER -> new Layout(64, 64, 8, 10, 8, 3, 5);
            case GOLEM -> new Layout(128, 128, 8, 10, 8, 3, 6);
            case GENERIC -> new Layout(16, 16, 8, 8, 8, 2, 5);
         };
      }
   }

   static final class Piece {
      final ResourceLocation texture;
      final Layout layout;
      final FinalPunchTargets.Jaw jaw;
      final boolean metal;
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      final Vec3 launch;
      float yaw;
      float pitch;
      float prevPitch;
      float roll;
      float prevRoll;
      float spinPitch;
      float spinRoll;
      int hold;
      int age;
      boolean resting;

      Piece(ResourceLocation texture, FinalPunchTargets.Jaw jaw, boolean metal, Vec3 pos, Vec3 launch, float yaw, float spinPitch, float spinRoll, int hold) {
         this.texture = texture;
         this.jaw = jaw;
         this.layout = Layout.of(jaw);
         this.metal = metal;
         this.pos = pos;
         this.prev = pos;
         this.vel = Vec3.ZERO;
         this.launch = launch;
         this.yaw = yaw;
         this.spinPitch = spinPitch;
         this.spinRoll = spinRoll;
         this.hold = hold;
      }
   }

   static final class Wound {
      final int targetId;
      final ResourceLocation texture;
      final Layout layout;
      final FinalPunchTargets.Jaw jaw;
      final boolean metal;
      final float headHeight;
      int age;

      Wound(int targetId, ResourceLocation texture, FinalPunchTargets.Jaw jaw, boolean metal, float headHeight) {
         this.targetId = targetId;
         this.texture = texture;
         this.jaw = jaw;
         this.layout = Layout.of(jaw);
         this.metal = metal;
         this.headHeight = headHeight;
      }
   }

   private static final List<Piece> PIECES = new ArrayList<>();
   private static final List<Wound> WOUNDS = new ArrayList<>();

   private FinalPunchJaw() {
   }

   /** Pure: the jaw's launch after the hit-stop (forward along the punch, up, a seeded side). */
   static Vec3 launchVelocity(Vec3 dir, long seed) {
      Vec3 flat = new Vec3(dir.x, 0, dir.z);
      flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
      Vec3 side = new Vec3(-flat.z, 0, flat.x);
      double s = (FinalPunchCues.hash(seed, 11) - 0.5) * 0.3;
      return flat.scale(0.42).add(0, 0.5, 0).add(side.scale(s));
   }

   static ResourceLocation texture(@Nullable LivingEntity target, FinalPunchTargets.Jaw jaw) {
      if (jaw == FinalPunchTargets.Jaw.GENERIC || target == null) {
         return jaw == FinalPunchTargets.Jaw.GOLEM ? new ResourceLocation("minecraft", "textures/entity/iron_golem/iron_golem.png")
            : jaw == FinalPunchTargets.Jaw.VILLAGER ? new ResourceLocation("minecraft", "textures/entity/villager/villager.png") : WHITE;
      }
      if (target instanceof AbstractClientPlayer player) return player.getSkinTextureLocation();
      try {
         EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(target);
         ResourceLocation location = renderer.getTextureLocation(target);
         return location == null ? WHITE : location;
      } catch (RuntimeException error) {
         return WHITE;
      }
   }

   static void spawn(FinalPunchClient.Scene scene, @Nullable LivingEntity target, Vec3 at, Vec3 dir) {
      while (PIECES.size() >= MAX) PIECES.remove(0);
      while (WOUNDS.size() >= MAX) WOUNDS.remove(0);
      ResourceLocation texture = texture(target, scene.jaw);
      boolean metal = scene.kind == FinalPunchTargets.Kind.GOLEM;
      Layout layout = Layout.of(scene.jaw);
      Vec3 flat = new Vec3(dir.x, 0, dir.z);
      flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
      // The jaw point is the chin's front; the piece's centre sits half its depth behind, half its height up.
      Vec3 centre = at.add(flat.scale(layout.jawD() / 32.0)).add(0, layout.jawH() / 32.0, 0);
      float faceYaw = scene.yaw + 180.0F;
      float roll = (float) ((FinalPunchCues.hash(scene.seed, 12) - 0.5) * 22.0);
      PIECES.add(new Piece(texture, scene.jaw, metal, centre, launchVelocity(dir, scene.seed), faceYaw, -26.0F, roll, FinalPunchRules.HIT_STOP_TICKS));
      if (target != null) {
         WOUNDS.removeIf(w -> w.targetId == target.getId());
         FinalPunchTargets.Entry entry = FinalPunchTargets.of(target);
         float head = entry != null ? entry.headHeight() : (float) (at.y - target.getY());
         WOUNDS.add(new Wound(target.getId(), texture, scene.jaw, metal, Mth.clamp(head, 0.5F, 3.0F)));
      }
   }

   static void tick(ClientLevel level, boolean paused) {
      if (paused) return;
      for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
         Piece p = it.next();
         p.prev = p.pos;
         p.prevPitch = p.pitch;
         p.prevRoll = p.roll;
         if (++p.age > FinalPunchRules.JAW_TICKS) {
            it.remove();
            continue;
         }
         if (p.hold > 0) {
            // Hit-stop: it has just come loose, barely moving.
            p.hold--;
            p.pos = p.pos.add(p.launch.normalize().scale(0.012));
            if (p.hold == 0) p.vel = p.launch;
            continue;
         }
         if (p.resting) continue;
         p.vel = p.vel.scale(0.985).add(0, -0.045, 0);
         Vec3 next = p.pos.add(p.vel);
         double half = p.layout.jawH() / 32.0;
         if (solid(level, new Vec3(next.x, p.pos.y, next.z))) {
            p.vel = new Vec3(-p.vel.x * 0.4, p.vel.y, -p.vel.z * 0.4);
            next = p.pos.add(0, p.vel.y, 0);
         }
         double floor = floor(level, next, half);
         if (!Double.isNaN(floor) && next.y - half < floor) {
            next = new Vec3(next.x, floor + half, next.z);
            p.vel = new Vec3(p.vel.x * 0.55, -p.vel.y * 0.3, p.vel.z * 0.55);
            p.spinPitch *= 0.5F;
            p.spinRoll *= 0.5F;
            if (p.vel.lengthSqr() < 0.004) {
               p.resting = true;
               p.vel = Vec3.ZERO;
               // Lies flat: the nearest upright or upside-down pitch, no roll.
               p.pitch = Math.round(p.pitch / 180.0F) * 180.0F;
               p.roll = 0;
            }
         }
         p.pos = next;
         if (!p.resting) {
            p.pitch += p.spinPitch;
            p.roll += p.spinRoll;
         }
      }
      for (Iterator<Wound> it = WOUNDS.iterator(); it.hasNext(); ) {
         Wound w = it.next();
         if (++w.age > FinalPunchRules.JAW_TICKS || !(level.getEntity(w.targetId) instanceof LivingEntity)) it.remove();
      }
   }

   private static boolean solid(ClientLevel level, Vec3 at) {
      BlockPos pos = BlockPos.containing(at);
      VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
      return !shape.isEmpty() && at.y - pos.getY() < shape.max(net.minecraft.core.Direction.Axis.Y) && at.y - pos.getY() >= shape.min(net.minecraft.core.Direction.Axis.Y);
   }

   /** The top of the ground under {@code at} (this block or the one below), NaN = none near. */
   private static double floor(ClientLevel level, Vec3 at, double half) {
      for (int dy = 0; dy >= -1; dy--) {
         BlockPos pos = BlockPos.containing(at.x, at.y - half, at.z).offset(0, dy, 0);
         VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
         if (!shape.isEmpty()) return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
      }
      return Double.NaN;
   }

   public static void clear() {
      PIECES.clear();
      WOUNDS.clear();
   }

   static int pieces() {
      return PIECES.size();
   }

   // ------------------------------------------------------------------ render

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || (PIECES.isEmpty() && WOUNDS.isEmpty())) return;
      if (dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) return;
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) return;
      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      for (Piece p : PIECES) {
         Vec3 pos = p.prev.lerp(p.pos, partial);
         if (pos.distanceToSqr(cam) > 64 * 64) continue;
         float shrink = p.age > FinalPunchRules.JAW_TICKS - 20 ? Math.max(0.0F, (FinalPunchRules.JAW_TICKS - p.age - partial) / 20.0F) : 1.0F;
         if (shrink <= 0) continue;
         stack.pushPose();
         stack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
         stack.mulPose(Axis.YP.rotationDegrees(-p.yaw));
         stack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, p.prevPitch, p.pitch)));
         stack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial, p.prevRoll, p.roll)));
         stack.scale(shrink, shrink, shrink);
         int light = LevelRenderer.getLightColor(level, BlockPos.containing(pos));
         box(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(p.texture)), p.layout, p.jaw, light, false, p.metal, 0.0F);
         stack.popPose();
      }
      for (Wound w : WOUNDS) {
         if (!(level.getEntity(w.targetId) instanceof LivingEntity target) || target.isRemoved() || target.deathTime > 0 && !FinalPunchClient.holdsPose(target)) continue;
         if (target == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
         Vec3 feet = target.getPosition(partial);
         if (feet.distanceToSqr(cam) > 64 * 64) continue;
         stack.pushPose();
         stack.translate(feet.x - cam.x, feet.y + w.headHeight - cam.y, feet.z - cam.z);
         stack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, target.yHeadRotO, target.yHeadRot)));
         stack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, target.xRotO, target.getXRot())));
         // From the neck pivot to the jaw's place: front half of the head's bottom slab.
         Layout l = w.layout;
         stack.translate(0, l.jawH() / 32.0, (l.headD() - l.jawD()) / 32.0);
         int light = LevelRenderer.getLightColor(level, BlockPos.containing(feet.add(0, w.headHeight, 0)));
         box(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(w.texture)), l, w.jaw, light, true, w.metal, 0.25F);
         stack.popPose();
      }
      buffers.endBatch();
   }

   /**
    * The jaw box (front at +Z local, centred), UVs from the head's bottom rows. {@code wound}
    * draws the torn stump instead (every face tinted raw / dark metal), inflated by {@code grow} px.
    */
   private static void box(PoseStack stack, VertexConsumer vc, Layout l, FinalPunchTargets.Jaw jaw, int light, boolean wound, boolean metal, float grow) {
      float w = (l.headW() + grow) / 16.0F;
      float h = (l.jawH() + grow) / 16.0F;
      float d = (l.jawD() + grow) / 16.0F;
      float x0 = -w / 2, x1 = w / 2, y0 = -h / 2, y1 = h / 2, z0 = -d / 2, z1 = d / 2;
      int hw = l.headW(), hh = l.headH(), hd = l.headD();
      float tw = l.texW(), th = l.texH();
      // Head UV origin (0, 0) for every vanilla layout; rows of the jaw = the bottom jawH rows.
      float vTop = hd + hh - l.jawH();
      float vBot = hd + hh;
      boolean generic = jaw == FinalPunchTargets.Jaw.GENERIC;
      int[] skin = generic ? new int[]{224, 172, 140} : new int[]{255, 255, 255};
      int[] raw = metal ? new int[]{70, 70, 76} : new int[]{150, 22, 18};
      int[] face = wound ? (metal ? new int[]{48, 48, 54} : new int[]{92, 10, 10}) : skin;
      Matrix4f m = stack.last().pose();
      Matrix3f n = stack.last().normal();
      // Front (+Z): u from hd .. hd+hw (u min = the person's right = local +X).
      quad(vc, m, n, light, face, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, hd / tw, vTop / th, (hd + hw) / tw, vBot / th, 0, 0, 1);
      // Bottom (−Y): the head's underside.
      quad(vc, m, n, light, face, x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0, (hd + hw) / tw, 0, (hd + 2 * hw) / tw, l.jawD() / th, 0, -1, 0);
      // Sides: the front jawD columns of each side region.
      quad(vc, m, n, light, face, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, (hd - l.jawD()) / tw, vTop / th, hd / tw, vBot / th, 1, 0, 0);
      quad(vc, m, n, light, face, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, (hd + hw) / tw, vTop / th, (hd + hw + l.jawD()) / tw, vBot / th, -1, 0, 0);
      // The torn faces (top and back): raw flesh / bare metal, from the skin's own colours.
      int[] cut = wound ? face : raw;
      quad(vc, m, n, light, cut, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, (hd + hw) / tw, 0, (hd + 2 * hw) / tw, l.jawD() / th, 0, 1, 0);
      quad(vc, m, n, light, cut, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, hd / tw, vTop / th, (hd + hw) / tw, vBot / th, 0, 0, -1);
   }

   private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int[] c,
      float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
      float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
      vertex(vc, m, n, light, c, ax, ay, az, u0, v0, nx, ny, nz);
      vertex(vc, m, n, light, c, bx, by, bz, u1, v0, nx, ny, nz);
      vertex(vc, m, n, light, c, cx, cy, cz, u1, v1, nx, ny, nz);
      vertex(vc, m, n, light, c, dx, dy, dz, u0, v1, nx, ny, nz);
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int[] c, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
      vc.vertex(m, x, y, z).color(c[0], c[1], c[2], 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
   }
}
