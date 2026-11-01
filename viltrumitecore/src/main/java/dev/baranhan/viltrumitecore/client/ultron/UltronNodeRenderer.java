package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A network node: plinth, pylon and the one module of its kind (the other modules are hidden sub-trees).
 * Under construction the build clip follows the build share beneath a faint red hologram of the finished node
 * and a scanning ring; online the module's idle loop runs (the boot clip after completion or a reboot, the
 * sensor's eye tracking the viewer), disabled it sags and sputters, destroyed it collapses. Emissive by power;
 * the shield dome (flaring on hits, orange while a bulwark feeds it), the fortify ring, the relocation beacon
 * and the viewer's own hack arc are additive.
 */
public class UltronNodeRenderer extends EntityRenderer<UltronNodeEntity> {
   /** Shield dome centre above the plinth and radius (blocks); UltronVfx aims shield hits with these. */
   public static final float DOME_Y = 2.2F;
   public static final float DOME_R = 3.0F;
   static final double GLOW_LOD = 192.0;
   private static final String[] MODULES = {"mod_core", "mod_uplink", "mod_foundry", "mod_bulwark", "mod_sensor", "mod_repair"};
   /** The module's bright point (orb) per kind. */
   private static final String[] FOCUS = {"core_heart", "beacon", "mod_foundry", "crystal", "eyeball", "repair_ring"};
   private static final float[] FOCUS_UP = {0.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F};
   private static final float TOP = 66.0F / 16.0F;
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f focus = new Vector3f();
   private boolean focusFound;
   private String focusBone = "";
   private float focusUp;

   public UltronNodeRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 1.3F;
   }

   @Override
   public ResourceLocation getTextureLocation(UltronNodeEntity e) {
      return UltronAssets.NODE.texture();
   }

   @Override
   public boolean shouldRender(UltronNodeEntity e, Frustum frustum, double x, double y, double z) {
      // the module and the dome reach well past the small hit box
      return e.shouldRender(x, y, z) && frustum.isVisible(e.getBoundingBoxForCulling().inflate(DOME_R, 1.0, DOME_R));
   }

   @Override
   public void render(UltronNodeEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(UltronAssets.NODE.geo());
      if (model == null) {
         return;
      }
      UltronNodeKind kind = e.nodeKind();
      UltronNodeState state = e.nodeState();
      float age = e.tickCount + partial;
      float since = age - e.clientStateStart;
      float build = state == UltronNodeState.BUILDING ? e.buildShare() : 1.0F;
      boolean shadow = ShaderCompat.isShadowPass();
      this.pose(model, e, kind, state, age, since, build, partial);
      this.inverse.set(stack.last().pose()).invert();
      this.focusFound = false;
      this.focusBone = FOCUS[kind.ordinal()];
      this.focusUp = FOCUS_UP[kind.ordinal()];
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(UltronAssets.NODE.texture())), light,
         OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, shadow ? null : this::hook);
      if (!shadow && dist <= GLOW_LOD) {
         float g = glow(e, state, age, since, build);
         if (g > 0.01F) {
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(UltronAssets.NODE.glow())), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
         }
         if (state == UltronNodeState.BUILDING && build < 0.995F) {
            this.hologram(model, e, kind, age, build, stack, buffers);
         }
      }
      stack.popPose();
      if (!shadow && dist <= GLOW_LOD) {
         this.extras(e, kind, state, age, since, build, partial, stack.last().pose(), buffers.getBuffer(RenderType.lightning()));
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   // ------------------------------------------------------------------ posing

   private void pose(BakedGeoModel model, UltronNodeEntity e, UltronNodeKind kind, UltronNodeState state, float age, float since, float build,
      float partial) {
      model.resetBones();
      for (int i = 0; i < MODULES.length; i++) {
         if (i != kind.ordinal()) {
            UltronUnitRenderer.hideTree(model.getBone(MODULES[i]), true);
         }
      }
      float seconds = age / 20.0F + e.nodeId() * 0.61F;
      Animation idle = clip("idle_" + kind.key());
      switch (state) {
         case BUILDING -> {
            Animation clip = clip("build");
            if (clip != null) {
               AnimationController.overlay(clip, model, build * clip.lengthSeconds());
            }
         }
         case ONLINE -> {
            boolean powered = e.nodeFlag(UltronNodeEntity.F_POWERED);
            if (idle != null) {
               // cut off from the core the module idles at a crawl
               AnimationController.overlay(idle, model, loop(powered ? seconds : seconds * 0.3F, idle));
            }
            Animation boot = clip("boot");
            float t = since / 20.0F;
            if (boot != null && t >= 0.0F && t < boot.lengthSeconds()) {
               UltronUnitRenderer.over(boot, model, t, smooth((float)(boot.lengthSeconds() - t) / 0.25F));
            }
            if (kind == UltronNodeKind.SENSOR) {
               this.track(model, e, partial, powered ? 1.0F : 0.3F);
            }
         }
         case DISABLED -> {
            if (idle != null) {
               AnimationController.overlay(idle, model, loop(seconds * 0.15F, idle));
            }
            Animation down = clip("disabled");
            if (down != null) {
               UltronUnitRenderer.over(down, model, loop(age / 20.0F, down), smooth(since / 10.0F));
            }
         }
         case DESTROYED -> {
            Animation down = clip("disabled");
            if (down != null) {
               AnimationController.overlay(down, model, loop(age / 20.0F, down));
            }
            Animation collapse = clip("collapse");
            if (collapse != null) {
               AnimationController.overlay(collapse, model, Mth.clamp(since / 20.0F, 0.0F, (float)collapse.lengthSeconds()));
            }
         }
         default -> {
         }
      }
      Animation hit = clip("hit");
      float sinceHit = age - e.clientHitTick;
      if (hit != null && state != UltronNodeState.DESTROYED && sinceHit >= 0.0F && sinceHit < hit.lengthSeconds() * 20.0F) {
         AnimationController.additive(hit, model, sinceHit / 20.0F, 1.0F);
      }
   }

   /** The sensor's eye follows the viewer within its sight (yaw on the gimbal, pitch on the eye). */
   private void track(BakedGeoModel model, UltronNodeEntity e, float partial, float weight) {
      Player viewer = Minecraft.getInstance().player;
      GeoBone gimbal = model.getBone("gimbal");
      GeoBone eye = model.getBone("eyeball");
      if (viewer == null || gimbal == null || eye == null) {
         return;
      }
      Vec3 at = e.getPosition(partial).add(0.0, 54.5 / 16.0, 0.0);
      Vec3 to = viewer.getEyePosition(partial).subtract(at);
      double flat = Math.sqrt(to.x * to.x + to.z * to.z);
      if (flat < 0.5 || to.lengthSqr() > 48.0 * 48.0) {
         return;
      }
      float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
      double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
      double px = to.x / flat, pz = to.z / flat;
      float turn = (float)Math.atan2(fz * px - fx * pz, fx * px + fz * pz);
      float lift = (float)Mth.clamp(Math.atan2(to.y, flat), -0.6, 0.8);
      gimbal.rotY += turn * weight;
      eye.rotX += lift * weight;
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      if (!this.focusFound && bone.name.equals(this.focusBone)) {
         this.point.set(bone.pivotX / 16.0F, (bone.pivotY + this.focusUp) / 16.0F, bone.pivotZ / 16.0F, 1.0F);
         stack.last().pose().transform(this.point);
         this.inverse.transform(this.point);
         this.focus.set(this.point.x, this.point.y, this.point.z);
         this.focusFound = true;
      }
   }

   /** The finished node as a faint red projection over the frame being built (slightly inset: built parts hide it). */
   private void hologram(BakedGeoModel model, UltronNodeEntity e, UltronNodeKind kind, float age, float build, PoseStack stack,
      MultiBufferSource buffers) {
      model.resetBones();
      for (int i = 0; i < MODULES.length; i++) {
         if (i != kind.ordinal()) {
            UltronUnitRenderer.hideTree(model.getBone(MODULES[i]), true);
         }
      }
      int tick = (int)(age * 1.5F) + e.getId() * 5;
      float flicker = tick % 13 == 0 ? 0.35F : 1.0F;
      float a = (0.16F + 0.06F * Mth.sin(age * 0.25F)) * (1.0F - build * 0.6F) * flicker;
      stack.pushPose();
      stack.translate(0.0F, TOP * 0.5F, 0.0F);
      stack.scale(0.985F, 0.985F, 0.985F);
      stack.translate(0.0F, -TOP * 0.5F, 0.0F);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityTranslucentEmissive(UltronAssets.NODE.texture())), LightTexture.FULL_BRIGHT,
         OverlayTexture.NO_OVERLAY, 1.0F, 0.24F, 0.2F, a, null);
      stack.popPose();
   }

   /** Emissive strength by state: powered pulse, unpowered trickle, boot stutter, disabled sputter, collapse fade. */
   static float glow(UltronNodeEntity e, UltronNodeState state, float age, float since, float build) {
      int tick = (int)(age * 1.3F) + e.getId() * 7;
      float g;
      switch (state) {
         case BUILDING -> g = (0.15F + 0.55F * build) * (tick % 19 == 0 ? 0.5F : 1.0F);
         case ONLINE -> {
            if (e.nodeFlag(UltronNodeEntity.F_POWERED)) {
               g = 0.82F + 0.14F * Mth.sin(age * 0.11F + e.getId());
            } else {
               g = tick % 23 < 2 ? 0.55F : 0.22F;
            }
            if (e.hullShare() < 0.35F && (tick % 7 == 0 || tick % 11 == 0)) {
               g *= 0.3F;
            }
            if (since >= 0.0F && since < 30.0F) {
               g *= smooth(since / 30.0F) * ((tick & 3) == 0 ? 0.4F : 1.0F);
            }
         }
         case DISABLED -> g = tick % 9 < 2 ? 0.5F : 0.07F;
         case DESTROYED -> g = (1.0F - smooth(since / 30.0F)) * ((tick & 3) == 0 ? 0.15F : 0.75F);
         default -> g = 0.0F;
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }

   // ------------------------------------------------------------------ additive extras (entity-relative)

   private void extras(UltronNodeEntity e, UltronNodeKind kind, UltronNodeState state, float age, float since, float build, float partial, Matrix4f m,
      VertexConsumer vc) {
      Vector3f left = this.entityRenderDispatcher.camera.getLeftVector();
      Vector3f up = this.entityRenderDispatcher.camera.getUpVector();
      Vector3f flatA = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f flatB = new Vector3f(0.0F, 0.0F, 1.0F);
      boolean powered = e.nodeFlag(UltronNodeEntity.F_POWERED);
      if (state == UltronNodeState.BUILDING) {
         // construction scan plane climbing with the build share
         float y = TOP * build + 0.08F * Mth.sin(age * 0.3F);
         float r = 1.25F + 0.2F * Mth.sin(age * 0.17F);
         GlowDraw.ring(vc, m, new Vector3f(0.0F, y, 0.0F), flatA, flatB, r * 0.8F, r, 0.7F, 24, 255, 60, 45, 70);
         GlowDraw.ring(vc, m, new Vector3f(0.0F, 0.06F, 0.0F), flatA, flatB, 1.5F, 1.75F, 0.5F, 24, 255, 50, 40, (int)(40 + 30 * build));
         if (e.nodeFlag(UltronNodeEntity.F_RELOCATION)) {
            this.relocation(e, age, m, vc);
         }
      }
      if (this.focusFound) {
         this.module(e, kind, state, age, since, powered, m, vc, left, up);
      }
      if (state == UltronNodeState.ONLINE && e.shieldShare() > 0.01F) {
         this.dome(e, age, partial, m, vc);
      }
      if (state.standing() && e.nodeFlag(UltronNodeEntity.F_FORTIFIED)) {
         float p = (age * 0.025F + e.getId() * 0.13F) % 1.0F;
         GlowDraw.ring(vc, m, new Vector3f(0.0F, 0.05F, 0.0F), flatA, flatB, DOME_R + 0.2F, DOME_R + 0.45F, 0.5F, 32, 255, 80, 50, 60);
         GlowDraw.ring(vc, m, new Vector3f(0.0F, 0.05F + p * 1.4F, 0.0F), flatA, flatB, DOME_R + 0.15F, DOME_R + 0.32F, 0.5F, 32, 255, 80, 50,
            (int)(70 * (1.0F - p)));
      }
      UltronStateS2CPacket s = UltronClientState.get();
      if (s != null && s.hackNode() == e.nodeId() && s.hack() > 0.0F && state.standing()) {
         this.arc(vc, m, new Vector3f(0.0F, 0.12F, 0.0F), 1.9F, s.hack(), 0.16F, age);
      }
   }

   private void module(UltronNodeEntity e, UltronNodeKind kind, UltronNodeState state, float age, float since, boolean powered, Matrix4f m,
      VertexConsumer vc, Vector3f left, Vector3f up) {
      int tick = (int)age + e.getId() * 3;
      switch (state) {
         case ONLINE -> {
            float boot = since >= 0.0F && since < 30.0F ? smooth(since / 30.0F) : 1.0F;
            float live = (powered ? 1.0F : 0.35F) * boot;
            switch (kind) {
               case CORE -> {
                  float beat = beat(age / 20.0F * (powered ? 1.1F : 0.5F));
                  GlowDraw.billboard(vc, m, this.focus, left, up, 0.75F + 0.35F * beat, 0.25F, 255, 50, 35, (int)(150 * live * (0.6F + 0.4F * beat)));
                  GlowDraw.billboard(vc, m, this.focus, left, up, 0.3F, 0.5F, 255, 200, 180, (int)(200 * live));
               }
               case UPLINK -> {
                  boolean blink = (tick % 20) < 4;
                  GlowDraw.billboard(vc, m, this.focus, left, up, blink ? 0.7F : 0.35F, 0.3F, 255, 40, 30, (int)((blink ? 220 : 90) * live));
               }
               case FOUNDRY -> GlowDraw.billboard(vc, m, this.focus, left, up, 0.55F, 0.3F, 255, 110, 50, (int)(70 * live * (0.8F + 0.2F
                  * Mth.sin(age * 0.5F))));
               case BULWARK -> {
                  GlowDraw.billboard(vc, m, this.focus, left, up, 0.6F + 0.08F * Mth.sin(age * 0.2F), 0.3F, 255, 90, 60, (int)(130 * live));
                  GlowDraw.billboard(vc, m, this.focus, left, up, 0.22F, 0.5F, 255, 220, 200, (int)(180 * live));
               }
               case SENSOR -> GlowDraw.billboard(vc, m, this.focus, left, up, 0.5F + 0.05F * Mth.sin(age * 0.9F), 0.3F, 255, 30, 25, (int)(170 * live));
               case REPAIR -> GlowDraw.ring(vc, m, this.focus, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), 0.3F, 0.55F, 0.5F, 24, 255, 120,
                  70, (int)(90 * live * (0.7F + 0.3F * Mth.sin(age * 0.3F))));
            }
         }
         case DISABLED -> {
            if (tick % 9 < 2) {
               GlowDraw.billboard(vc, m, this.focus, left, up, 0.4F, 0.3F, 255, 70, 50, 110);
            }
         }
         case DESTROYED -> {
            float f = 1.0F - smooth(since / 16.0F);
            if (f > 0.02F) {
               GlowDraw.billboard(vc, m, this.focus, left, up, 1.2F * (1.0F + since * 0.05F), 0.2F, 255, 120, 60, (int)(160 * f));
            }
         }
         default -> {
         }
      }
   }

   /** The shield dome: latitude bands and slowly turning meridians, brighter as it holds, flaring when struck. */
   private void dome(UltronNodeEntity e, float age, float partial, Matrix4f m, VertexConsumer vc) {
      float share = e.shieldShare();
      float hit = Mth.clamp(1.0F - (age - e.clientShieldHitTick) / 10.0F, 0.0F, 1.0F);
      boolean fed = e.nodeFlag(UltronNodeEntity.F_BULWARK);
      int r = 255, g = fed ? 110 : 55, b = fed ? 60 : 45;
      float base = 14.0F + 22.0F * share + 120.0F * hit;
      Vector3f flatA = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f flatB = new Vector3f(0.0F, 0.0F, 1.0F);
      float[] lats = {-12.0F, 12.0F, 34.0F, 54.0F, 72.0F};
      for (int i = 0; i < lats.length; i++) {
         float lat = lats[i] * Mth.DEG_TO_RAD;
         float rr = DOME_R * Mth.cos(lat);
         float y = DOME_Y + DOME_R * Mth.sin(lat);
         float shimmer = 0.75F + 0.25F * Mth.sin(age * 0.15F + i * 1.7F);
         GlowDraw.ring(vc, m, new Vector3f(0.0F, y, 0.0F), flatA, flatB, rr - 0.06F, rr + 0.04F, 0.6F, 40, r, g, b, (int)(base * shimmer));
      }
      Vector3f cam = this.toCam(e, partial);
      int meridians = 6;
      float spin = age * 0.006F + e.getId();
      Vector3f p0 = new Vector3f();
      Vector3f p1 = new Vector3f();
      for (int k = 0; k < meridians; k++) {
         float lon = spin + k * Mth.TWO_PI / meridians;
         float cx = Mth.cos(lon), cz = Mth.sin(lon);
         for (int j = 0; j < 7; j++) {
            float l0 = (-12.0F + j * 13.0F) * Mth.DEG_TO_RAD;
            float l1 = (-12.0F + (j + 1) * 13.0F) * Mth.DEG_TO_RAD;
            p0.set(cx * DOME_R * Mth.cos(l0), DOME_Y + DOME_R * Mth.sin(l0), cz * DOME_R * Mth.cos(l0));
            p1.set(cx * DOME_R * Mth.cos(l1), DOME_Y + DOME_R * Mth.sin(l1), cz * DOME_R * Mth.cos(l1));
            int a = (int)(base * 0.8F);
            GlowDraw.ribbon(vc, m, p0, p1, new Vector3f(cam).sub(p0), 0.05F, 0.05F, r, g, b, a, a);
         }
      }
      if (hit > 0.0F) {
         Vector3f c = new Vector3f(0.0F, DOME_Y, 0.0F);
         GlowDraw.billboard(vc, m, c, this.entityRenderDispatcher.camera.getLeftVector(), this.entityRenderDispatcher.camera.getUpVector(), DOME_R * 1.05F,
            0.85F, r, g, b, (int)(40 * hit));
      }
   }

   /** A relocated foundry being rushed: a tall faint beacon with rising markers so players can find it. */
   private void relocation(UltronNodeEntity e, float age, Matrix4f m, VertexConsumer vc) {
      Vector3f cam = this.toCam(e, 1.0F);
      Vector3f base = new Vector3f(0.0F, TOP, 0.0F);
      Vector3f top = new Vector3f(0.0F, TOP + 28.0F, 0.0F);
      GlowDraw.ribbon(vc, m, base, top, cam, 0.35F, 0.12F, 255, 70, 40, 90, 0);
      Vector3f flatA = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f flatB = new Vector3f(0.0F, 0.0F, 1.0F);
      for (int i = 0; i < 3; i++) {
         float p = (age * 0.02F + i / 3.0F) % 1.0F;
         GlowDraw.ring(vc, m, new Vector3f(0.0F, TOP + p * 20.0F, 0.0F), flatA, flatB, 0.6F, 0.9F, 0.5F, 20, 255, 80, 50, (int)(90 * (1.0F - p)));
      }
   }

   /** The viewer's own hack progress as a ground arc around the node (red to white as it fills). */
   private void arc(VertexConsumer vc, Matrix4f m, Vector3f c, float radius, float progress, float width, float age) {
      Vector3f upAxis = new Vector3f(0.0F, 1.0F, 0.0F);
      int segs = Math.max(2, (int)(48 * Mth.clamp(progress, 0.0F, 1.0F)));
      float span = Mth.TWO_PI * Mth.clamp(progress, 0.0F, 1.0F);
      Vector3f p0 = new Vector3f();
      Vector3f p1 = new Vector3f();
      int g = (int)(60 + 180 * progress);
      for (int i = 0; i < segs; i++) {
         float a0 = -Mth.HALF_PI + span * i / segs;
         float a1 = -Mth.HALF_PI + span * (i + 1) / segs;
         p0.set(c.x + Mth.cos(a0) * radius, c.y, c.z + Mth.sin(a0) * radius);
         p1.set(c.x + Mth.cos(a1) * radius, c.y, c.z + Mth.sin(a1) * radius);
         GlowDraw.ribbon(vc, m, p0, p1, upAxis, width, width, 255, g, g - 20, 200, 200);
      }
      GlowDraw.ring(vc, m, c, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), radius - 0.1F, radius + 0.1F, 0.5F, 40, 255, 40, 30,
         (int)(30 + 10 * Mth.sin(age * 0.5F)));
   }

   private Vector3f toCam(UltronNodeEntity e, float partial) {
      Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
      return new Vector3f((float)(cam.x - Mth.lerp(partial, e.xo, e.getX())), (float)(cam.y - Mth.lerp(partial, e.yo, e.getY())),
         (float)(cam.z - Mth.lerp(partial, e.zo, e.getZ())));
   }

   /** Heart-beat envelope: two quick pulses each second. */
   private static float beat(float seconds) {
      float t = seconds - (float)Math.floor(seconds);
      float a = Math.max(0.0F, 1.0F - Math.abs(t - 0.1F) / 0.08F);
      float b = Math.max(0.0F, 1.0F - Math.abs(t - 0.3F) / 0.1F) * 0.7F;
      return Math.max(a, b);
   }

   @Nullable
   private static Animation clip(String name) {
      return AnimCache.animation(UltronAssets.NODE.anim(), name);
   }

   private static double loop(float seconds, Animation clip) {
      double len = Math.max(0.05, clip.lengthSeconds());
      double t = seconds % len;
      return t < 0.0 ? t + len : t;
   }

   private static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}
