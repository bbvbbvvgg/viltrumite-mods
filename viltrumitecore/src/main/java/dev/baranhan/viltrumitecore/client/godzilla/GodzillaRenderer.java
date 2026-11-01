package dev.baranhan.viltrumitecore.client.godzilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.client.ultron.UltronUnitRenderer;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaMove;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The kaiju. Three levels of detail by camera distance (with hysteresis): the full model close up, a lighter one
 * across the district and a silhouette across the city (and in shader shadow passes). Every frame the model is
 * posed from the synced state in layers: the idle breathing, the walk on the synced gait phase (weighted by the
 * walk amount, so the feet plant on the server's footfalls), the current move's clip cross-faded out of the
 * previous one (mirrored variants for the left / right tail strike and stomp), then the procedural layers: the
 * lagging tail chain from the entity's per-segment yaws (the hit zones follow the same chain), the hips twist of
 * the tail strike with the upper body counter-turning, and the head / neck tracking the synced look. The fall,
 * the lying and the rise roll the whole body onto its left side about the same pivot the server rolls the hit
 * zones about.
 *
 * <p>Two passes: the scale atlas, then the emissive atlas for the dorsal plates, the throat and the eyes only,
 * tinted per bone: during the breath charge the glow front runs up the plates from the tail tip to the head
 * ({@link GodzillaRules#glowFront}, {@link GodzillaRules#plateGlow}), the throat fills with light, the beam
 * pulses up the spine while it fires and the plates cool down tail-first after it. The beam, the mouth flare,
 * the gathering energy, the plate sparks and a soft bloom over the back are own additive geometry
 * ({@link GlowDraw}), no vanilla beam or particles. The mouth (posed) is published to the entity for the effects
 * and the audio. Client only.
 */
public class GodzillaRenderer extends EntityRenderer<GodzillaEntity> {
   public static final ResourceLocation TEXTURE = rl("textures/entity/godzilla/godzilla.png");
   public static final ResourceLocation GLOW = rl("textures/entity/godzilla/godzilla_glow.png");
   public static final ResourceLocation ANIM = rl("animations/godzilla/godzilla.animation.json");
   public static final ResourceLocation[] GEO = {rl("geo/godzilla/godzilla.geo.json"), rl("geo/godzilla/godzilla_lod1.geo.json"),
      rl("geo/godzilla/godzilla_lod2.geo.json")};
   /** Camera distance (blocks, to the chest) beyond which LOD1 / LOD2 draw, and the hysteresis band. */
   public static final float LOD1_DISTANCE = 200.0F;
   public static final float LOD2_DISTANCE = 420.0F;
   public static final float LOD_BAND = 14.0F;
   /** Beyond this the cheap extras (beam spiral, sparks, gathering streaks) are skipped. */
   public static final float DETAIL_DISTANCE = 300.0F;
   /** Ticks a move cross-fades out of the previous one. */
   public static final float CROSSFADE = 5.0F;
   /** Geo px per HEIGHT fraction (the model is MODEL_HEIGHT blocks tall before the scale). */
   static final float F = GodzillaRules.MODEL_HEIGHT * 16.0F;
   /** The beam origin in the head bone's frame (render coordinates, blocks before the scale). */
   private static final Vector3f MOUTH = new Vector3f(0.0F, (GodzillaRules.NECK_PIVOT[1] + GodzillaRules.MOUTH_OFFSET[1]) * F / 16.0F,
      -(GodzillaRules.NECK_PIVOT[2] + GodzillaRules.MOUTH_OFFSET[2]) * F / 16.0F);
   private static final int MAX_SPARKS = 28;

   private final Info[] infos = new Info[3];
   private final Matrix4f inverse = new Matrix4f();
   private final Matrix4f headPose = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private boolean headFound;
   @Nullable
   private Info current;
   private int litPlates;
   private final Vector3f[] lit = new Vector3f[64];
   private final float[] litLevel = new float[64];

   public GodzillaRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
      for (int i = 0; i < this.lit.length; i++) {
         this.lit[i] = new Vector3f();
      }
   }

   static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }

   @Override
   public ResourceLocation getTextureLocation(GodzillaEntity entity) {
      return TEXTURE;
   }

   /** Pure: the level of detail at a camera distance, keeping the last one inside the hysteresis band. */
   public static int lodFor(double distance, int last) {
      int raw = distance < LOD1_DISTANCE ? 0 : distance < LOD2_DISTANCE ? 1 : 2;
      if (last < 0 || raw == last) {
         return raw;
      }
      double edge = raw > last ? (last == 0 ? LOD1_DISTANCE : LOD2_DISTANCE) : (last == 2 ? LOD2_DISTANCE : LOD1_DISTANCE);
      return Math.abs(distance - edge) < LOD_BAND ? last : raw;
   }

   /** Pure: the clip a move plays (the left / right variants of the tail strike and the stomp by its argument). */
   public static String clipName(GodzillaMove move, int arg) {
      if ((move == GodzillaMove.TAIL || move == GodzillaMove.STOMP) && arg < 0) {
         return move.clip + "_m";
      }
      return move.clip;
   }

   /** Pure: moves that hand over to the next one without easing back to the stance at their end. */
   public static boolean holdsEnd(GodzillaMove move) {
      return move == GodzillaMove.CHARGE || move == GodzillaMove.FIRE || move == GodzillaMove.FALL || move == GodzillaMove.DOWN
         || move == GodzillaMove.DEATH;
   }

   @Override
   public void render(GodzillaEntity e, float entityYaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      double x = Mth.lerp(partial, e.xo, e.getX());
      double y = Mth.lerp(partial, e.yo, e.getY());
      double z = Mth.lerp(partial, e.zo, e.getZ());
      Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
      double dy = cam.y - (y + GodzillaRules.HEIGHT * 0.45);
      double dist = Math.sqrt((cam.x - x) * (cam.x - x) + dy * dy + (cam.z - z) * (cam.z - z));
      boolean shadow = ShaderCompat.isShadowPass();
      int lod;
      if (shadow) {
         lod = 2;
      } else {
         lod = lodFor(dist, e.clientLod);
         e.clientLod = lod;
      }
      Info info = this.info(lod);
      if (info == null) {
         return;
      }
      this.current = info;
      float age = e.tickCount + partial;
      this.pose(info, e, partial, age);
      this.inverse.set(stack.last().pose()).invert();
      this.headFound = false;
      this.litPlates = 0;
      stack.pushPose();
      this.transform(stack, e, partial);
      int lit = light(e, x, y, z, light);
      AnimRenderer.render(info.model, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), lit, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F,
         1.0F, 1.0F, shadow ? null : this::hook);
      if (!shadow) {
         this.glowLevels(info, e, partial, age);
         VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
         for (GeoBone bone : info.model.topLevelBones()) {
            this.glowTree(bone, stack, glow, info);
         }
      }
      stack.popPose();
      if (shadow) {
         return;
      }
      Vector3f mouth = null;
      Vector3f mouthDir = null;
      if (this.headFound) {
         mouth = this.toRel(this.headPose, MOUTH.x, MOUTH.y, MOUTH.z);
         Vector3f ahead = this.toRel(this.headPose, MOUTH.x, MOUTH.y, MOUTH.z - 1.0F);
         mouthDir = ahead.sub(mouth, new Vector3f());
         if (mouthDir.lengthSquared() > 1.0E-6F) {
            mouthDir.normalize();
         }
         e.clientMouth = new Vec3(x + mouth.x, y + mouth.y, z + mouth.z);
         e.clientMouthDir = new Vec3(mouthDir.x, mouthDir.y, mouthDir.z);
         e.clientMouthTick = e.tickCount;
      }
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      this.effects(e, partial, age, dist, stack.last().pose(), vc, mouth, mouthDir);
   }

   @Nullable
   private Info info(int lod) {
      for (int k = lod; k >= 0; k--) {
         BakedGeoModel model = AnimCache.model(GEO[k]);
         if (model == null) {
            continue;
         }
         if (this.infos[k] == null || this.infos[k].model != model) {
            this.infos[k] = new Info(model);
         }
         return this.infos[k];
      }
      return null;
   }

   /** Light of the body: the brighter of the feet and the chest (a tower's shadow at the feet does not blacken it). */
   private static int light(GodzillaEntity e, double x, double y, double z, int feet) {
      int chest = LevelRenderer.getLightColor(e.level(), BlockPos.containing(x, y + GodzillaRules.HEIGHT * 0.6, z));
      return LightTexture.pack(Math.max(LightTexture.block(feet), LightTexture.block(chest)), Math.max(LightTexture.sky(feet), LightTexture.sky(chest)));
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      if (this.current != null && bone == this.current.head) {
         this.headPose.set(stack.last().pose());
         this.headFound = true;
      }
   }

   private Vector3f toRel(Matrix4f pose, float px, float py, float pz) {
      this.point.set(px, py, pz, 1.0F);
      pose.transform(this.point);
      this.inverse.transform(this.point);
      return new Vector3f(this.point.x, this.point.y, this.point.z);
   }

   // ------------------------------------------------------------------ pose

   private Animation clip(String name) {
      return AnimCache.animation(ANIM, name);
   }

   private void pose(Info info, GodzillaEntity e, float partial, float age) {
      BakedGeoModel model = info.model;
      model.resetBones();
      Animation idle = this.clip("idle");
      if (idle != null) {
         UltronUnitRenderer.over(idle, model, loop(age / 20.0F, idle), 1.0F);
      }
      float walk = smooth(Mth.lerp(partial, e.clientWalkO, e.clientWalk));
      Animation walkClip = this.clip("walk");
      if (walkClip != null && walk > 0.01F) {
         double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
         UltronUnitRenderer.over(walkClip, model, (gait - Math.floor(gait)) * walkClip.lengthSeconds(), walk);
      }
      GodzillaMove move = e.move();
      float since = e.moveTime(partial);
      float in = smooth(since / CROSSFADE);
      if (in < 1.0F && e.clientPrevMove != GodzillaMove.NONE) {
         float prevTicks = e.clientMoveStart - e.clientPrevStart + since;
         this.playMove(model, e.clientPrevMove, e.clientPrevArg, prevTicks / 20.0F, 1.0F);
      }
      if (move != GodzillaMove.NONE) {
         this.playMove(model, move, e.moveArg(), since / 20.0F, in);
      }
      // procedural layers
      float tilt = GodzillaRules.fallTilt(move, since);
      float body = e.renderYaw(partial);
      float twist = move == GodzillaMove.TAIL ? GodzillaRules.hipsTwist(since, e.moveArg() >= 0 ? 1 : -1) : 0.0F;
      if (twist != 0.0F) {
         deg(info.hips, 0.0F, twist, 0.0F);
         deg(info.waist, 0.0F, -twist * 0.45F, 0.0F);
         deg(info.chest, 0.0F, -twist * 0.35F, 0.0F);
         deg(info.neck, 0.0F, -twist * 0.2F, 0.0F);
      }
      float prev = body + twist;
      for (int k = 0; k < info.tail.length; k++) {
         float yk = e.tailYaw(k, partial);
         deg(info.tail[k], 0.0F, Mth.wrapDegrees(yk - prev), 0.0F);
         prev = yk;
      }
      float look = 1.0F - tilt;
      if (look > 0.01F) {
         float yaw = Mth.lerp(partial, e.clientHeadYawO, e.clientHeadYaw) * look;
         float pitch = Mth.lerp(partial, e.clientHeadPitchO, e.clientHeadPitch) * look;
         deg(info.chest, -pitch * 0.12F, yaw * 0.15F, 0.0F);
         deg(info.neck, -pitch * 0.33F, yaw * 0.35F, 0.0F);
         deg(info.head, -pitch * 0.55F, yaw * 0.5F, 0.0F);
      }
   }

   /** One move's clip at a time in seconds, weighted, with its own ease back to the stance at the end. */
   private void playMove(BakedGeoModel model, GodzillaMove move, int arg, float t, float weight) {
      Animation clip = this.clip(clipName(move, arg));
      if (clip == null || weight <= 0.001F) {
         return;
      }
      double len = clip.lengthSeconds();
      double time;
      float out = 1.0F;
      if (move == GodzillaMove.FIRE || move == GodzillaMove.DOWN || clip.loopType() == Animation.LoopType.LOOP) {
         time = loop(t, clip);
      } else {
         time = Math.min(t, len);
         if (!holdsEnd(move)) {
            out = smooth((float)((len - t) / 0.25));
         }
      }
      UltronUnitRenderer.over(clip, model, time, weight * out);
   }

   private void transform(PoseStack stack, GodzillaEntity e, float partial) {
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - e.renderYaw(partial)));
      float tilt = GodzillaRules.fallTilt(e.move(), e.moveTime(partial));
      if (tilt > 0.001F) {
         float p = GodzillaRules.FALL_PIVOT * GodzillaRules.HEIGHT;
         stack.translate(-p, 0.0F, 0.0F);
         stack.mulPose(Axis.ZP.rotationDegrees(tilt * GodzillaRules.FALL_ROLL));
         stack.translate(p, 0.0F, 0.0F);
      }
      float s = GodzillaRules.modelScale();
      stack.scale(s, s, s);
   }

   // ------------------------------------------------------------------ glow

   /** Pure: plate glow at spine position s for a move at tick t (rage: a faint ember pulse between breaths). */
   public static float plateLevel(GodzillaMove move, float t, float s, boolean rage, float age) {
      float g;
      switch (move) {
         case CHARGE -> g = GodzillaRules.plateGlow(s, GodzillaRules.glowFront(t)) * (0.86F + 0.14F * Mth.sin(age * 1.9F + s * 23.0F));
         case FIRE -> g = 0.78F + 0.22F * Mth.sin(age * 0.85F - s * 14.0F);
         case BREATH_END -> g = 1.0F - smooth((t - (1.0F - s) * 8.0F) / 22.0F);
         default -> g = 0.0F;
      }
      if (rage) {
         g = Math.max(g, 0.12F + 0.07F * Mth.sin(age * 0.11F + s * 7.0F));
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }

   /** Pure: the throat's glow for a move at tick t. */
   public static float mouthLevel(GodzillaMove move, float t) {
      return switch (move) {
         case CHARGE -> GodzillaRules.mouthGlow(t);
         case FIRE -> 1.0F;
         case BREATH_END -> 1.0F - smooth(t / 10.0F);
         default -> 0.0F;
      };
   }

   private void glowLevels(Info info, GodzillaEntity e, float partial, float age) {
      GodzillaMove move = e.move();
      float t = e.moveTime(partial);
      boolean rage = e.rage();
      float dying = move == GodzillaMove.DEATH ? 1.0F - smooth(t / 70.0F) : 1.0F;
      for (int i = 0; i < info.plateS.length; i++) {
         info.level[i] = plateLevel(move, t, info.plateS[i], rage, age) * dying;
      }
      float mouth = mouthLevel(move, t);
      float eyes = (rage ? 0.75F : 0.5F) + 0.4F * mouth;
      info.level[info.plateS.length] = Mth.clamp(eyes, 0.0F, 1.0F) * dying;
      info.level[info.plateS.length + 1] = mouth;
   }

   private void glowTree(GeoBone bone, PoseStack stack, VertexConsumer vc, Info info) {
      if (!info.carries.contains(bone)) {
         return;
      }
      stack.pushPose();
      applyBone(bone, stack);
      Integer idx = info.index.get(bone);
      if (idx != null && !bone.hidden) {
         float g = info.level[idx];
         if (g > 0.004F) {
            for (GeoBone.Cube cube : bone.cubes) {
               cube(cube, stack, vc, g, g, g);
            }
            if (idx < info.plateS.length && g > 0.35F && this.litPlates < this.lit.length) {
               Vector3f tip = info.tip[idx];
               this.point.set(tip.x, tip.y, tip.z, 1.0F);
               stack.last().pose().transform(this.point);
               this.inverse.transform(this.point);
               this.lit[this.litPlates].set(this.point.x, this.point.y, this.point.z);
               this.litLevel[this.litPlates] = g;
               this.litPlates++;
            }
         }
      }
      for (GeoBone child : bone.children) {
         this.glowTree(child, stack, vc, info);
      }
      stack.popPose();
   }

   /** The bone transform of {@link AnimRenderer} (kept in step with it: the glow pass must sit exactly on the body). */
   static void applyBone(GeoBone bone, PoseStack stack) {
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }
      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      if (bone.rotZ != 0.0F) {
         stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      }
      if (bone.rotY != 0.0F) {
         stack.mulPose(Axis.YP.rotation(bone.rotY));
      }
      if (bone.rotX != 0.0F) {
         stack.mulPose(Axis.XP.rotation(bone.rotX));
      }
      if (bone.scaleX != 1.0F || bone.scaleY != 1.0F || bone.scaleZ != 1.0F) {
         stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
      }
      stack.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
   }

   private static void cube(GeoBone.Cube cube, PoseStack stack, VertexConsumer vc, float r, float g, float b) {
      boolean rotated = cube.hasRotation();
      if (rotated) {
         stack.pushPose();
         stack.translate(cube.pivot().x, cube.pivot().y, cube.pivot().z);
         if (cube.rotation().z != 0.0F) {
            stack.mulPose(Axis.ZP.rotation(cube.rotation().z));
         }
         if (cube.rotation().y != 0.0F) {
            stack.mulPose(Axis.YP.rotation(cube.rotation().y));
         }
         if (cube.rotation().x != 0.0F) {
            stack.mulPose(Axis.XP.rotation(cube.rotation().x));
         }
         stack.translate(-cube.pivot().x, -cube.pivot().y, -cube.pivot().z);
      }
      PoseStack.Pose pose = stack.last();
      Matrix4f position = pose.pose();
      Matrix3f normalMatrix = pose.normal();
      Vector3f normal = new Vector3f();
      for (GeoBone.Quad quad : cube.quads()) {
         normal.set(quad.normal()).mul(normalMatrix);
         for (GeoBone.Vertex v : quad.vertices()) {
            vc.vertex(position, v.x(), v.y(), v.z()).color(r, g, b, 1.0F).uv(v.u(), v.v()).overlayCoords(OverlayTexture.NO_OVERLAY)
               .uv2(LightTexture.FULL_BRIGHT).normal(normal.x, normal.y, normal.z).endVertex();
         }
      }
      if (rotated) {
         stack.popPose();
      }
   }

   // ------------------------------------------------------------------ additive effects

   private void effects(GodzillaEntity e, float partial, float age, double dist, Matrix4f m, VertexConsumer vc, @Nullable Vector3f mouth,
      @Nullable Vector3f mouthDir) {
      GodzillaMove move = e.move();
      float t = e.moveTime(partial);
      float mg = mouthLevel(move, t);
      boolean detail = dist < DETAIL_DISTANCE;
      Vector3f left = this.entityRenderDispatcher.camera.getLeftVector();
      Vector3f up = this.entityRenderDispatcher.camera.getUpVector();
      // bloom over the lit back and sparks crawling over the plates
      if (this.litPlates > 0) {
         float sum = 0.0F;
         for (int i = 0; i < this.litPlates; i++) {
            sum += this.litLevel[i];
         }
         float avg = sum / this.litPlates;
         int step = Math.max(1, this.litPlates / 6);
         for (int i = 0; i < this.litPlates; i += step) {
            GlowDraw.billboard(vc, m, this.lit[i], left, up, 11.0F + 3.0F * avg, 0.15F, 50, 130, 255, (int)(36 * this.litLevel[i]));
         }
         if (detail) {
            int frame = (int)(age * 1.5F);
            int sparks = 0;
            for (int i = 0; i < this.litPlates && sparks < MAX_SPARKS; i++) {
               long h = mix(frame * 31L + i * 7919L + e.getId());
               if ((h & 0xFF) < 70) {
                  float ox = ((h >> 8 & 0xFF) / 255.0F - 0.5F) * 3.0F;
                  float oy = ((h >> 16 & 0xFF) / 255.0F - 0.2F) * 2.5F;
                  float oz = ((h >> 24 & 0xFF) / 255.0F - 0.5F) * 3.0F;
                  Vector3f p = new Vector3f(this.lit[i]).add(ox, oy, oz);
                  float r = 0.5F + (h >> 32 & 0xFF) / 255.0F * 1.2F;
                  GlowDraw.billboard(vc, m, p, left, up, r, 0.45F, 160, 215, 255, (int)(190 * this.litLevel[i]));
                  sparks++;
               }
            }
         }
      }
      if (mouth == null) {
         return;
      }
      // energy gathering in the mouth through the last charge stage, the flare while it fires
      if (mg > 0.01F) {
         float flick = 0.9F + 0.1F * Mth.sin(age * 4.1F);
         GlowDraw.billboard(vc, m, mouth, left, up, (2.0F + 6.0F * mg) * flick, 0.3F, 70, 160, 255, (int)(150 * mg));
         GlowDraw.billboard(vc, m, mouth, left, up, (0.8F + 2.2F * mg) * flick, 0.6F, 225, 245, 255, (int)(230 * mg));
         if (detail && move == GodzillaMove.CHARGE && mouthDir != null) {
            Vector3f a = new Vector3f();
            Vector3f b = new Vector3f();
            basis(mouthDir, a, b);
            for (int i = 0; i < 7; i++) {
               float ph = (age * 0.09F + i * 0.37F) % 1.0F;
               float ang = i * 2.39F + age * 0.05F;
               float r = 12.0F * (1.0F - ph) + 2.0F;
               Vector3f from = new Vector3f(mouth).add(new Vector3f(a).mul(Mth.cos(ang) * r)).add(new Vector3f(b).mul(Mth.sin(ang) * r))
                  .add(new Vector3f(mouthDir).mul(2.0F * (1.0F - ph)));
               Vector3f to = new Vector3f(from).lerp(mouth, 0.45F);
               GlowDraw.ribbon(vc, m, from, to, this.toCam(e, partial, from), 0.25F, 0.12F, 120, 200, 255, 0, (int)(170 * mg * ph));
            }
         }
      }
      if (e.flag(GodzillaEntity.FLAG_BEAM)) {
         Vec3 endWorld = lerp(partial, e.clientBeamEndO, e.clientBeamEnd);
         Vector3f end = new Vector3f((float)(endWorld.x - Mth.lerp(partial, e.xo, e.getX())), (float)(endWorld.y - Mth.lerp(partial, e.yo, e.getY())),
            (float)(endWorld.z - Mth.lerp(partial, e.zo, e.getZ())));
         float grow = move == GodzillaMove.FIRE ? smooth(t / 4.0F) : move == GodzillaMove.BREATH_END ? 1.0F - smooth(t / 6.0F) : 1.0F;
         this.beam(e, partial, age, m, vc, mouth, end, grow, e.rage(), detail, left, up);
      }
   }

   /** The atomic breath: a blue haze, a bright body, a white core, a spiral of energy around it, flares at both ends. */
   private void beam(GodzillaEntity e, float partial, float age, Matrix4f m, VertexConsumer vc, Vector3f from, Vector3f to, float grow, boolean rage,
      boolean detail, Vector3f left, Vector3f up) {
      if (grow <= 0.01F) {
         return;
      }
      float flick = (0.9F + 0.06F * Mth.sin(age * 3.3F) + 0.04F * Mth.sin(age * 7.9F)) * grow;
      float wide = rage ? 1.3F : 1.0F;
      Vector3f mid = new Vector3f(from).lerp(to, 0.5F);
      Vector3f cam = this.toCam(e, partial, mid);
      GlowDraw.ribbon(vc, m, from, to, cam, 5.5F * wide * flick, 7.0F * wide * flick, 40, 110, 255, 70, 50);
      GlowDraw.ribbon(vc, m, from, to, cam, 2.6F * wide * flick, 3.2F * wide * flick, 90, 180, 255, 170, 140);
      GlowDraw.ribbon(vc, m, from, to, cam, 1.0F * wide * flick, 1.3F * wide * flick, 230, 248, 255, 255, 230);
      if (detail) {
         Vector3f dir = new Vector3f(to).sub(from);
         float len = dir.length();
         if (len > 1.0F) {
            dir.div(len);
            Vector3f a = new Vector3f();
            Vector3f b = new Vector3f();
            basis(dir, a, b);
            int n = Math.min(40, Math.max(8, (int)(len / 6.0F)));
            for (int strand = 0; strand < 2; strand++) {
               Vector3f last = null;
               for (int i = 0; i <= n; i++) {
                  float f = i / (float)n;
                  float ang = f * len * 0.11F - age * 0.7F + strand * Mth.PI;
                  float r = (2.6F + 0.6F * Mth.sin(f * 20.0F + age)) * wide * grow;
                  Vector3f p = new Vector3f(from).add(new Vector3f(dir).mul(f * len)).add(new Vector3f(a).mul(Mth.cos(ang) * r))
                     .add(new Vector3f(b).mul(Mth.sin(ang) * r));
                  if (last != null) {
                     GlowDraw.ribbon(vc, m, last, p, this.toCam(e, partial, p), 0.45F, 0.45F, 120, 200, 255, 120, 120);
                  }
                  last = p;
               }
            }
         }
      }
      GlowDraw.billboard(vc, m, from, left, up, 6.0F * wide * flick, 0.3F, 80, 170, 255, 220);
      GlowDraw.billboard(vc, m, from, left, up, 2.6F * wide * flick, 0.55F, 235, 250, 255, 240);
      GlowDraw.billboard(vc, m, to, left, up, 13.0F * wide * flick, 0.25F, 70, 150, 255, 190);
      GlowDraw.billboard(vc, m, to, left, up, 6.0F * wide * flick, 0.5F, 255, 200, 140, 150);
      GlowDraw.billboard(vc, m, to, left, up, 3.0F * wide * flick, 0.6F, 255, 250, 240, 230);
   }

   private Vector3f toCam(GodzillaEntity e, float partial, Vector3f p) {
      Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
      return new Vector3f((float)(cam.x - Mth.lerp(partial, e.xo, e.getX())) - p.x, (float)(cam.y - Mth.lerp(partial, e.yo, e.getY())) - p.y,
         (float)(cam.z - Mth.lerp(partial, e.zo, e.getZ())) - p.z);
   }

   // ------------------------------------------------------------------ helpers

   private static Vec3 lerp(float partial, Vec3 a, Vec3 b) {
      return new Vec3(Mth.lerp(partial, a.x, b.x), Mth.lerp(partial, a.y, b.y), Mth.lerp(partial, a.z, b.z));
   }

   private static void basis(Vector3f n, Vector3f a, Vector3f b) {
      Vector3f ref = Math.abs(n.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
      n.cross(ref, a).normalize();
      n.cross(a, b).normalize();
   }

   private static long mix(long h) {
      h ^= h >>> 33;
      h *= 0xff51afd7ed558ccdL;
      h ^= h >>> 33;
      h *= 0xc4ceb9fe1a85ec53L;
      h ^= h >>> 33;
      return h;
   }

   /** Adds Blockbench-convention degrees (the clip convention) to a bone. */
   static void deg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.rotX += (float)Math.toRadians(-x);
         bone.rotY += (float)Math.toRadians(-y);
         bone.rotZ += (float)Math.toRadians(z);
      }
   }

   static double loop(float seconds, Animation clip) {
      float len = (float)Math.max(0.05, clip.lengthSeconds());
      double t = seconds % len;
      return t < 0.0 ? t + len : t;
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   /** Spine position (0 tail tip .. 1 head) of a plate bone named plate_SSSS_..., or -1. */
   public static float plateS(String bone) {
      if (!bone.startsWith("plate_") || bone.length() < 10) {
         return -1.0F;
      }
      try {
         return Integer.parseInt(bone.substring(6, 10)) / 1000.0F;
      } catch (NumberFormatException ex) {
         return -1.0F;
      }
   }

   /** Bones of one LOD the renderer drives, the emissive bones and their tips. */
   private static final class Info {
      final BakedGeoModel model;
      final GeoBone hips;
      final GeoBone waist;
      final GeoBone chest;
      final GeoBone neck;
      final GeoBone head;
      final GeoBone[] tail = new GeoBone[GodzillaRules.TAIL_SEGMENTS];
      final float[] plateS;
      final Vector3f[] tip;
      final float[] level;
      final Map<GeoBone, Integer> index = new IdentityHashMap<>();
      final Set<GeoBone> carries = Collections.newSetFromMap(new IdentityHashMap<>());

      Info(BakedGeoModel model) {
         this.model = model;
         this.hips = model.getBone("hips");
         this.waist = model.getBone("waist");
         this.chest = model.getBone("chest");
         this.neck = model.getBone("neck");
         this.head = model.getBone("head");
         for (int k = 0; k < this.tail.length; k++) {
            this.tail[k] = model.getBone("tail_" + k);
         }
         List<GeoBone> plates = new ArrayList<>();
         for (GeoBone b : model.allBones()) {
            if (plateS(b.name) >= 0.0F) {
               plates.add(b);
            }
         }
         this.plateS = new float[plates.size()];
         this.tip = new Vector3f[plates.size()];
         for (int i = 0; i < plates.size(); i++) {
            GeoBone b = plates.get(i);
            this.plateS[i] = plateS(b.name);
            this.tip[i] = tipOf(b);
            this.index.put(b, i);
         }
         this.level = new float[plates.size() + 2];
         GeoBone eyes = model.getBone("eyes");
         GeoBone mouth = model.getBone("mouth_glow");
         if (eyes != null) {
            this.index.put(eyes, plates.size());
         }
         if (mouth != null) {
            this.index.put(mouth, plates.size() + 1);
         }
         for (GeoBone b : this.index.keySet()) {
            for (GeoBone p = b; p != null; p = p.parent) {
               this.carries.add(p);
            }
         }
      }

      /** The highest vertex of a plate (its tip, where the sparks crawl), in the bone's frame. */
      private static Vector3f tipOf(GeoBone b) {
         Vector3f best = new Vector3f(b.pivotX / 16.0F, b.pivotY / 16.0F, b.pivotZ / 16.0F);
         float top = -Float.MAX_VALUE;
         Vector3f p = new Vector3f();
         for (GeoBone.Cube c : b.cubes) {
            Matrix4f r = new Matrix4f();
            if (c.hasRotation()) {
               r.translate(c.pivot()).rotateZ(c.rotation().z).rotateY(c.rotation().y).rotateX(c.rotation().x).translate(-c.pivot().x, -c.pivot().y,
                  -c.pivot().z);
            }
            for (GeoBone.Quad q : c.quads()) {
               for (GeoBone.Vertex v : q.vertices()) {
                  r.transformPosition(v.x(), v.y(), v.z(), p);
                  if (p.y > top) {
                     top = p.y;
                     best.set(p);
                  }
               }
            }
         }
         return best;
      }
   }
}
