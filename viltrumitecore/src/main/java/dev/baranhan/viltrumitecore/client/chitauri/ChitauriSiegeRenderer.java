package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSiege;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Siege platform: the hull yaws with the body, the turret with the head and the rail barrel pitches with the look
 * (layered on top of the clips). The state picks the clip — gliding on the move, the anchor pads driving down on deploy,
 * the capacitor caps opening and the coils spinning up through the charge, the recoil and venting of the shot, the
 * crash — with the previous state's pose fading out under the new one. The capacitor core and coils brighten with the
 * charge; once the aim locks a white-hot orb gathers at the muzzle. Client only.
 */
public class ChitauriSiegeRenderer extends EntityRenderer<ChitauriSiege> {
   /** Fraction of the charge after which the aim is locked (and the targeting line is drawn). */
   public static final float LOCK_FRACTION = (ChitauriSiege.CHARGE_TICKS - ChitauriSiege.LOCK_TICKS) / (float)ChitauriSiege.CHARGE_TICKS;

   static final class Track {
      byte cur = -1;
      byte prev = -1;
      int changed = -1000;
      int prevStart = -1000;
      int seenTick = -1;
   }

   private static final Map<ChitauriSiege, Track> TRACKS = new WeakHashMap<>();
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f muzzle = new Vector3f();
   private boolean muzzleFound;

   public ChitauriSiegeRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 1.4F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriSiege entity) {
      return ChitauriAssets.SIEGE_TEX;
   }

   @Override
   public void render(ChitauriSiege e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.SIEGE_GEO);
      if (model == null) {
         return;
      }
      ChitauriPortalClient.Plane portal = ChitauriPortalClient.plane();
      if (portal != null && !portal.inFront(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()) + 0.9,
            Mth.lerp(partial, e.zo, e.getZ()), 2.0)) {
         return;
      }
      float age = e.tickCount + partial;
      Track t = track(e);
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      pose(model, e, t, age, partial, bodyYaw);
      float alpha = dying < 0 ? 1.0F : 1.0F - ChitauriSoldierRenderer.smooth((dying - (ChitauriSiege.DEATH_TICKS - 10)) / 9.0F);
      if (alpha <= 0.01F) {
         return;
      }
      this.inverse.set(stack.last().pose()).invert();
      this.muzzleFound = false;
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(ChitauriAssets.SIEGE_TEX) : RenderType.entityCutoutNoCull(ChitauriAssets.SIEGE_TEX);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, this::hook);
      boolean shadowPass = ShaderCompat.isShadowPass();
      float charge = chargeOf(e.state(), age - t.changed);
      if (!shadowPass) {
         float g = glow(e.state(), charge, age - t.changed, dying, age, e.getId()) * alpha;
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ChitauriAssets.SIEGE_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
      }
      stack.popPose();
      if (!shadowPass && this.muzzleFound && charge > 0.0F && dying < 0) {
         this.orb(stack, buffers, charge, age);
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      if (!this.muzzleFound && "muzzle".equals(bone.name)) {
         this.point.set(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, 1.0F);
         stack.last().pose().transform(this.point);
         this.inverse.transform(this.point);
         this.muzzle.set(this.point.x, this.point.y, this.point.z);
         this.muzzleFound = true;
      }
   }

   private void orb(PoseStack stack, MultiBufferSource buffers, float charge, float age) {
      Camera camera = this.entityRenderDispatcher.camera;
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      float locked = charge >= LOCK_FRACTION ? 1.0F : 0.0F;
      float pulse = 0.85F + 0.15F * Mth.sin(age * (1.2F + 1.6F * charge));
      float core = (0.08F + 0.22F * charge) * pulse;
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * 4.0F, 0.12F, 70, 140, 255, (int)(120 * charge));
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * 1.7F, 0.45F, 150, 205, 255, (int)(210 * charge));
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * (1.0F + 0.4F * locked), 0.7F, 240, 248, 255, (int)(255 * charge));
   }

   static Track track(ChitauriSiege e) {
      Track t = TRACKS.computeIfAbsent(e, k -> new Track());
      if (t.seenTick != e.tickCount) {
         t.seenTick = e.tickCount;
         byte s = e.state();
         if (s != t.cur) {
            t.prev = t.cur;
            t.prevStart = t.changed;
            t.cur = s;
            t.changed = e.tickCount;
         }
      }
      return t;
   }

   static String clipOf(byte state) {
      return switch (state) {
         case ChitauriSiege.DEPLOY -> "deploy";
         case ChitauriSiege.CHARGE -> "charge";
         case ChitauriSiege.FIRE -> "fire";
         case ChitauriSiege.DYING -> "dying";
         default -> "move";
      };
   }

   private static void state(BakedGeoModel model, byte state, float since, float age, float weight) {
      Animation clip = AnimCache.animation(ChitauriAssets.SIEGE_ANIM, clipOf(state));
      if (clip == null || weight <= 0.01F) {
         return;
      }
      double len = clip.lengthSeconds();
      double at = clip.loopType() == Animation.LoopType.LOOP ? (age / 20.0) % len : Mth.clamp(since / 20.0, 0.0, len - 0.001);
      AnimationController.overlay(clip, model, at, weight);
   }

   static void pose(BakedGeoModel model, ChitauriSiege e, Track t, float age, float partial, float bodyYaw) {
      model.resetBones();
      Animation idle = AnimCache.animation(ChitauriAssets.SIEGE_ANIM, "idle");
      if (idle != null) {
         AnimationController.overlay(idle, model, (age / 20.0 + e.getId() * 0.29) % idle.lengthSeconds(), 1.0F);
      }
      byte cur = t.cur < 0 ? e.state() : t.cur;
      float since = age - t.changed;
      float blend = ChitauriSoldierRenderer.smooth(since / 8.0F);
      if (t.prev >= 0 && blend < 1.0F) {
         state(model, t.prev, age - t.prevStart, age, 1.0F);
      }
      // gliding only shows while it actually moves
      float w = cur == ChitauriSiege.MOVE ? Mth.clamp((float)Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo)) / 0.12F, 0.35F, 1.0F) : 1.0F;
      state(model, cur, since, age, (t.prev >= 0 ? blend : 1.0F) * w);
      Animation stagger = AnimCache.animation(ChitauriAssets.SIEGE_ANIM, "stagger");
      float sinceStagger = age - e.clientStaggerStart;
      if (stagger != null && sinceStagger >= 0.0F && sinceStagger < stagger.lengthSeconds() * 20.0F) {
         AnimationController.additive(stagger, model, sinceStagger / 20.0, 1.0F);
      }
      // turret yaw relative to the hull, barrel pitch with the look (a dead platform's turret stays where it was)
      if (cur != ChitauriSiege.DYING) {
         float turret = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
         float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -35.0F, 25.0F);
         ChitauriSoldierRenderer.deg(model.getBone("turret"), 0.0F, turret, 0.0F);
         ChitauriSoldierRenderer.deg(model.getBone("barrel"), pitch, 0.0F, 0.0F);
      }
   }

   /** Pure: charge 0..1 through the CHARGE state (full during the shot itself), 0 otherwise. */
   static float chargeOf(byte state, float since) {
      if (state == ChitauriSiege.CHARGE) {
         return Mth.clamp(since / ChitauriSiege.CHARGE_TICKS, 0.05F, 1.0F);
      }
      if (state == ChitauriSiege.FIRE && since < 3.0F) {
         return 1.0F;
      }
      return 0.0F;
   }

   /** Pure: glow pass intensity: dim idle hum, the charge swelling, a flash on the shot, flicker while it dies. */
   static float glow(byte state, float charge, float since, float dying, float age, int id) {
      float g = 0.6F + 0.06F * Mth.sin(age * 0.2F + id);
      g += 0.4F * charge;
      if (state == ChitauriSiege.FIRE) {
         g += 0.4F * Math.max(0.0F, 1.0F - since / 8.0F);
      }
      if (dying >= 0.0F || state == ChitauriSiege.DYING) {
         g *= ((int)(age * 2.1F + id) & 3) == 0 ? 0.15F : 0.55F;
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }
}
