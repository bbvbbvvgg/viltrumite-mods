package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.entity.chitauri.LeviathanTrail;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Leviathan: the head at the entity centre and ten scaled body segments plus the tail drawn at the trail pieces the
 * entity computes (server and client run the same trail, so the hitboxes match), each oriented along its neighbours
 * with a travelling side/vertical wave on top of the path, the roll of a stagger or the death spin passed down the body
 * with a lag, per-segment fin / spike swim clips with a phase offset, jaw clips on a bite or roar, the skull turning to
 * its focus, a red hit flash, failing lights when it dies and a fade of the corpse. Pieces still behind the portal plane
 * are not drawn, so it really comes out of the portal. Client only.
 */
public class ChitauriLeviathanRenderer extends EntityRenderer<ChitauriLeviathan> {
   private static final int PIECES = ChitauriLeviathan.PIECES;
   private final double[][] pos = new double[PIECES + 1][3];

   public ChitauriLeviathanRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriLeviathan entity) {
      return ChitauriAssets.HEAD_TEX;
   }

   @Override
   public void render(ChitauriLeviathan e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel head = AnimCache.model(ChitauriAssets.HEAD_GEO);
      BakedGeoModel segment = AnimCache.model(ChitauriAssets.SEGMENT_GEO);
      BakedGeoModel tail = AnimCache.model(ChitauriAssets.TAIL_GEO);
      if (head == null || segment == null || tail == null) {
         return;
      }
      float age = e.tickCount + partial;
      byte state = e.state();
      float sinceState = age - e.clientStateStart;
      float alpha = state == ChitauriLeviathan.FADING ? 1.0F - smooth(sinceState / ChitauriLeviathan.FADE_TICKS) : 1.0F;
      if (alpha <= 0.01F) {
         return;
      }
      double ox = Mth.lerp(partial, e.xo, e.getX());
      double oy = Mth.lerp(partial, e.yo, e.getY());
      double oz = Mth.lerp(partial, e.zo, e.getZ());
      // pos[0] = head centre, pos[1..PIECES] = segments then the tail
      this.pos[0][0] = ox;
      this.pos[0][1] = oy + ChitauriLeviathan.CENTRE_Y;
      this.pos[0][2] = oz;
      for (int i = 0; i < PIECES; i++) {
         for (int k = 0; k < 3; k++) {
            this.pos[i + 1][k] = Mth.lerp(partial, e.pieceO[i][k], e.piece[i][k]);
         }
      }
      float wave = state == ChitauriLeviathan.ALIVE ? 1.0F : state == ChitauriLeviathan.FALLING ? 1.0F - smooth(sinceState / 40.0F) : 0.0F;
      if (wave > 0.0F) {
         undulate(this.pos, age, wave * (1.0F + Math.min(1.0F, e.staggerTicks() / 20.0F)));
      }
      float roll = Mth.lerp(partial, e.clientRollO, e.clientRoll);
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      float glow = ShaderCompat.isShadowPass() ? 0.0F : glow(state, sinceState, e.staggerTicks(), age) * alpha;
      ChitauriPortalClient.Plane portal = ChitauriPortalClient.plane();
      // head
      if (portal == null || portal.inFront(this.pos[0][0], this.pos[0][1], this.pos[0][2], 3.0)) {
         poseHead(head, e, age, partial, state);
         float headYaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
         float headPitch = -Mth.lerp(partial, e.xRotO, e.getXRot());
         this.draw(head, stack, buffers, ox, oy, oz, 0, headYaw, headPitch, roll, 1.0F, ChitauriAssets.HEAD_TEX, ChitauriAssets.HEAD_GLOW, light, overlay, alpha, glow);
      }
      // body and tail, each facing the piece in front of it
      for (int i = 1; i <= PIECES; i++) {
         if (portal != null && !portal.inFront(this.pos[i][0], this.pos[i][1], this.pos[i][2], 1.2)) {
            continue;
         }
         double[] front = this.pos[i - 1];
         double[] back = i < PIECES ? this.pos[i + 1] : this.pos[i];
         double fx = front[0] - back[0];
         double fy = front[1] - back[1];
         double fz = front[2] - back[2];
         if (i == PIECES) {
            fx = front[0] - this.pos[i][0];
            fy = front[1] - this.pos[i][1];
            fz = front[2] - this.pos[i][2];
         }
         double h = Math.sqrt(fx * fx + fz * fz);
         if (h * h + fy * fy < 1.0E-6) {
            continue;
         }
         float pieceYaw = (float)(Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
         float piecePitch = (float)(Mth.atan2(fy, h) * Mth.RAD_TO_DEG);
         float pieceRoll = roll * Math.max(0.0F, 1.0F - i * 0.06F);
         float scale = LeviathanTrail.scale(i - 1);
         boolean isTail = i == PIECES;
         BakedGeoModel model = isTail ? tail : segment;
         poseBody(model, isTail, age, i - 1, state);
         this.draw(model, stack, buffers, ox, oy, oz, i, pieceYaw, piecePitch, pieceRoll, scale, isTail ? ChitauriAssets.TAIL_TEX : ChitauriAssets.SEGMENT_TEX,
            isTail ? ChitauriAssets.TAIL_GLOW : ChitauriAssets.SEGMENT_GLOW, light, overlay, alpha, glow * (isTail ? 0.8F : 1.0F));
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void draw(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, double ox, double oy, double oz, int index, float yawDeg, float pitchDeg, float rollDeg,
      float scale, ResourceLocation tex, ResourceLocation glowTex, int light, int overlay, float alpha, float glow) {
      stack.pushPose();
      stack.translate(this.pos[index][0] - ox, this.pos[index][1] - oy, this.pos[index][2] - oz);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yawDeg));
      stack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
      stack.mulPose(Axis.ZP.rotationDegrees(-rollDeg));
      stack.scale(scale, scale, scale);
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, null);
      if (glow > 0.01F) {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(glowTex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            glow, glow, glow, 1.0F, null);
      }
      stack.popPose();
   }

   /**
    * Pure: a travelling wave on top of the trail: side sway and a smaller vertical heave, zero at the neck and growing
    * to the tail, so the body swims instead of sliding along its path like a train.
    */
   static void undulate(double[][] p, float age, float strength) {
      int n = p.length;
      for (int i = 1; i < n; i++) {
         double fx = p[i - 1][0] - p[i][0];
         double fz = p[i - 1][2] - p[i][2];
         double h = Math.sqrt(fx * fx + fz * fz);
         if (h < 1.0E-4) {
            continue;
         }
         double sx = -fz / h;
         double sz = fx / h;
         double amp = 0.55 * strength * i / (double)(n - 1);
         double ph = age * 0.16 - i * 0.72;
         double side = Math.sin(ph) * amp;
         double heave = Math.cos(ph * 0.8 + 0.6) * amp * 0.35;
         p[i][0] += sx * side;
         p[i][1] += heave;
         p[i][2] += sz * side;
      }
   }

   static void poseHead(BakedGeoModel head, ChitauriLeviathan e, float age, float partial, byte state) {
      ResourceLocation file = ChitauriAssets.LEVIATHAN_ANIM;
      head.resetBones();
      boolean alive = state == ChitauriLeviathan.ALIVE;
      Animation base = AnimCache.animation(file, alive ? "head_idle" : "head_dead");
      if (base != null) {
         AnimationController.overlay(base, head, (age / 20.0 + e.getId() * 0.13) % base.lengthSeconds(), 1.0F);
      }
      if (alive) {
         play(head, AnimCache.animation(file, "head_roar"), age - e.clientRoarStart, 0.25);
         play(head, AnimCache.animation(file, "head_bite"), age - e.clientBiteStart, 0.1);
         Animation hurt = AnimCache.animation(file, "head_hurt");
         if (hurt != null && e.hurtTime > 0) {
            AnimationController.additive(hurt, head, Math.min(hurt.lengthSeconds(), (10 - e.hurtTime + partial) / 20.0), 1.0F);
         }
         float lookYaw = Mth.lerp(partial, e.clientLookYawO, e.clientLookYaw);
         float lookPitch = Mth.lerp(partial, e.clientLookPitchO, e.clientLookPitch);
         ChitauriSoldierRenderer.deg(head.getBone("skull"), -lookPitch, lookYaw, 0.0F);
      }
   }

   private static void play(BakedGeoModel model, Animation clip, float since, double fade) {
      if (clip == null || since < 0.0F) {
         return;
      }
      double sec = since / 20.0;
      if (sec < clip.lengthSeconds()) {
         AnimationController.overlay(clip, model, sec, smooth((float)(sec / 0.08)) * smooth((float)((clip.lengthSeconds() - sec) / fade)));
      }
   }

   static void poseBody(BakedGeoModel model, boolean tail, float age, int index, byte state) {
      model.resetBones();
      boolean alive = state == ChitauriLeviathan.ALIVE;
      String name = (tail ? "tail_" : "segment_") + (alive ? "swim" : "dead");
      Animation clip = AnimCache.animation(ChitauriAssets.LEVIATHAN_ANIM, name);
      if (clip != null) {
         double sec = age / 20.0 + index * 0.17;
         AnimationController.overlay(clip, model, sec - Math.floor(sec / clip.lengthSeconds()) * clip.lengthSeconds(), 1.0F);
      }
   }

   /** Pure: light of eyes and collars: steady alive, stutters in a stagger, fails on the fall, dead on the ground. */
   static float glow(byte state, float sinceState, int stagger, float age) {
      switch (state) {
         case ChitauriLeviathan.ALIVE: {
            float g = 0.88F + 0.12F * Mth.sin(age * 0.21F);
            if (stagger > 0 && ((int)(age * 1.3F) & 3) == 0) {
               g *= 0.4F;
            }
            return g;
         }
         case ChitauriLeviathan.FALLING:
            return (0.7F - 0.5F * smooth(sinceState / 50.0F)) * (((int)(age * 2.1F) & 3) == 0 ? 0.3F : 1.0F);
         case ChitauriLeviathan.CRASHED:
            return sinceState < 40.0F && ((int)(age * 1.7F) & 7) == 0 ? 0.35F : 0.0F;
         default:
            return 0.0F;
      }
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}
