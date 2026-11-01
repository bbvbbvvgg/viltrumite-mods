package dev.baranhan.viltrumitecore.client.ironman.legion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.ironman.ThrusterFlames;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkExtras;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkParts;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkTextures;
import dev.baranhan.viltrumitecore.client.ironman.mark.sig.SignatureVisuals;
import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionMove;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionProfile;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSteering;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Iron Legion suit (design §7). One pose owner for the whole body: the
 * empty-suit shell in the mark skin is reset and posed right before drawing,
 * in this order (animation-system §5): base flight loop (hover ↔ cruise by
 * throttle, loop time wrapped here), phase clip (brake, per-mark ready,
 * takeoff, crash), the synced move overlay (missiles, signature, dodge,
 * land), procedural aim of the arms and head at the synced target
 * (quaternion slerp by a real-time weight), then additive recoil/hit. The
 * whole body leans, banks and bobs about its centre from the interpolated
 * motion. Phase and move start come from synced entity data, so every
 * client plays the same beat. Flames, the mark's own parts and its
 * signature hardware copy the posed shell bones. Palm, chest and shoulder
 * anchors of the posed arms are kept for the VFX of the same frame.
 */
public final class LegionSuitRenderer extends EntityRenderer<LegionSuitEntity> {
   private static final ResourceLocation SHELL = new ResourceLocation("viltrumitecore", "geo/ironman/marks/empty_suit.geo.json");
   static final ResourceLocation CLIPS = new ResourceLocation("viltrumitecore", "animations/ironman/legion_suit.animation.json");
   private static final float BODY_SCALE = 0.9375F;
   /** Lean / bank / spin pivot above the feet (blocks, before scale). */
   private static final float CENTRE = 0.95F;
   private static final WeakHashMap<LegionSuitEntity, Drive> DRIVES = new WeakHashMap<>();
   private static final Map<Integer, Anchors> ANCHORS = new HashMap<>();
   private static final String[] TOP = {"armorhead", "armorbody", "armorrightarm", "armorleftarm", "armorrightleg", "armorleftleg"};

   public LegionSuitRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.35F;
   }

   /** World anchors of the posed suit (palms, reactor, right shoulder) drawn this frame. */
   public record Anchors(Vec3 rightPalm, Vec3 leftPalm, Vec3 chest, Vec3 shoulder, int tick) {
   }

   /** Anchors of the last drawn frame of this suit if it is still current, else null (VFX fall back to the body points). */
   @Nullable
   public static Anchors anchors(LegionSuitEntity suit) {
      Anchors anchors = ANCHORS.get(suit.getId());
      return anchors != null && Math.abs(anchors.tick() - suit.tickCount) <= 1 ? anchors : null;
   }

   static void clear() {
      DRIVES.clear();
      ANCHORS.clear();
   }

   static void forget(int id) {
      ANCHORS.remove(id);
   }

   @Override
   public boolean shouldRender(LegionSuitEntity suit, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
      // The far sky glint of the arrival is drawn by LegionVfx; the body itself culls normally.
      return super.shouldRender(suit, frustum, x, y, z);
   }

   @Override
   public void render(LegionSuitEntity suit, float entityYaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel shell = AnimCache.model(SHELL);
      MarkId mark = suit.mark();
      if (shell == null || mark == null) {
         return;
      }

      boolean shadow = ShaderCompat.isShadowPass();
      Drive drive = DRIVES.computeIfAbsent(suit, s -> new Drive());
      float ticks = suit.tickCount + partialTick;
      if (!shadow) {
         drive.update(suit, partialTick);
      }

      LegionProfile profile = LegionProfile.of(mark);
      boolean silent = profile != null && profile.silent();
      boolean camo = suit.flag(LegionSuitEntity.FLAG_CAMO);
      boolean critical = suit.flag(LegionSuitEntity.FLAG_CRITICAL);
      LegionPhase phase = suit.phase();
      pose(shell, suit, drive, mark, phase, ticks, partialTick);

      Vec3 at = suit.getPosition(partialTick);
      PoseStack world = new PoseStack();
      world.translate(at.x, at.y, at.z);
      stack.pushPose();
      transform(stack, suit, drive, phase, ticks);
      transform(world, suit, drive, phase, ticks);
      if (!shadow) {
         ANCHORS.put(suit.getId(), anchors(shell, world.last().pose(), suit.tickCount));
      }

      int overlay = LivingEntityRenderer.getOverlayCoords(suit, 0.0F);
      float alpha = camo ? Mth.clamp(drive.camo, 0.08F, 1.0F) : 1.0F;
      ResourceLocation skin = MarkTextures.skin(mark);
      RenderType body = alpha < 1.0F ? RenderType.entityTranslucent(skin) : RenderType.entityCutoutNoCull(skin);
      AnimRenderer.render(shell, stack, null, buffers.getBuffer(body), light, overlay, 1.0F, 1.0F, 1.0F, alpha, null);
      float glow = glow(suit, phase, critical, camo, ticks);
      if (!shadow && glow > 0.01F) {
         // a suit Ultron overrode burns red until it is handed back
         boolean ultron = dev.baranhan.viltrumitecore.worldevent.ultron.UltronCapture.controlled(suit);
         AnimRenderer.render(shell, stack, null, buffers.getBuffer(RenderType.eyes(MarkTextures.glow(mark))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            glow, ultron ? glow * 0.22F : glow, ultron ? glow * 0.18F : glow, 1.0F, null);
      }

      ResourceLocation extrasGeo = MarkExtras.geo(mark);
      BakedGeoModel extras = extrasGeo == null ? null : AnimCache.model(extrasGeo);
      if (extras != null && alpha >= 0.5F) {
         extras.resetBones();
         if (mark == MarkId.MARK_7) {
            MarkExtras.openFlaps(extras, drive.flaps);
         }

         copyTop(shell, extras);
         MarkParts.draw(extras, stack, buffers, MarkExtras.passes(mark), light, 1.0F);
      }

      if (!shadow) {
         signatureParts(suit, drive, mark, camo, ticks, shell, stack, buffers, light);
         if (!silent && !camo) {
            flames(suit, drive, phase, critical, ticks, shell, stack, buffers);
         }
      }

      stack.popPose();
      super.render(suit, entityYaw, partialTick, stack, buffers, light);
   }

   @Override
   public ResourceLocation getTextureLocation(LegionSuitEntity suit) {
      return MarkTextures.skin(suit.mark() == null ? MarkId.MARK_7 : suit.mark());
   }

   // --- pose ---

   /** Shell pose of this frame. Pure function of the synced clocks and the drive's smoothed weights. */
   private static void pose(BakedGeoModel shell, LegionSuitEntity suit, Drive drive, MarkId mark, LegionPhase phase, float ticks, float partialTick) {
      Animation hover = clip("hover");
      Animation cruise = clip("cruise");
      double seconds = ticks / 20.0;
      if (hover != null && cruise != null) {
         AnimationController.blend(shell, hover, wrap(seconds, hover), cruise, wrap(seconds, cruise), drive.cruise);
      } else {
         shell.resetBones();
      }

      float age = suit.clientPhaseAge() + partialTick;
      switch (phase) {
         case BRAKE -> overlay(shell, "brake", age, LegionRules.BRAKE_TICKS + 6, 2.0F, 0.0F);
         case READY -> overlay(shell, "ready_" + mark.key(), age, LegionRules.READY_TICKS, 2.0F, 5.0F);
         case LEAVE -> overlay(shell, "takeoff", age, 1000, 2.0F, 0.0F);
         case CRASH -> {
            Animation crash = clip("crash");
            if (crash != null) {
               AnimationController.overlay(crash, shell, wrap(age / 20.0, crash), Mth.clamp(age / 3.0F, 0.0F, 1.0F));
            }
         }
         default -> {
         }
      }

      LegionMove move = suit.clientMove;
      float moveAge = suit.tickCount - suit.clientMoveStart + partialTick;
      boolean additive = move == LegionMove.FIRE_RIGHT || move == LegionMove.FIRE_LEFT || move == LegionMove.FIRE_BOTH || move == LegionMove.HIT;
      boolean moveOn = move != LegionMove.NONE && move != LegionMove.READY && moveAge >= 0.0F && moveAge < move.length() && phase != LegionPhase.CRASH;
      if (moveOn && !additive) {
         overlay(shell, move.clip(), moveAge, move.length(), 2.0F, 4.0F);
      }

      // Aim: owned by the arms after the clips, except while a move owns both arms.
      boolean armsOwned = moveOn && (move == LegionMove.MISSILES || move == LegionMove.SIGNATURE && mark != MarkId.MARK_42);
      if (drive.aimRight > 0.001F && !armsOwned) {
         aimArm(shell.getBone("armorrightarm"), drive, drive.aimRight);
      }

      if (drive.aimLeft > 0.001F && !armsOwned) {
         aimArm(shell.getBone("armorleftarm"), drive, drive.aimLeft);
      }

      look(shell.getBone("armorhead"), drive);
      if (moveOn && additive) {
         Animation clip = clip(move.clip());
         if (clip != null) {
            AnimationController.additive(clip, shell, Math.min(moveAge / 20.0, clip.lengthSeconds()), 1.0F);
         }
      }
   }

   /** One-shot clip over the base with a fade in/out (ticks); {@code length} is the move's synced length. */
   private static void overlay(BakedGeoModel shell, String name, float age, float length, float fadeIn, float fadeOut) {
      Animation clip = clip(name);
      if (clip == null || age < 0.0F || age > length) {
         return;
      }

      float in = fadeIn <= 0.0F ? 1.0F : Mth.clamp(age / fadeIn, 0.0F, 1.0F);
      float out = fadeOut <= 0.0F ? 1.0F : Mth.clamp((length - age) / fadeOut, 0.0F, 1.0F);
      AnimationController.overlay(clip, shell, Math.min(age / 20.0, clip.lengthSeconds()), Math.min(in, out));
   }

   /**
    * Arm at the target: bone X lifts the arm forward (+90° = level), bone Y
    * turns it to the suit's left (Minecraft yaw grows to the right, hence the
    * minus). The body lean is compensated so the palm stays on the target.
    */
   private static void aimArm(@Nullable GeoBone arm, Drive drive, float weight) {
      if (arm == null) {
         return;
      }

      float x = (90.0F - drive.aimPitch + drive.lean) * Mth.DEG_TO_RAD;
      float y = -Mth.clamp(drive.aimYaw, -80.0F, 80.0F) * Mth.DEG_TO_RAD;
      slerpTo(arm, x, y, 0.0F, weight);
   }

   private static void look(@Nullable GeoBone head, Drive drive) {
      if (head == null || drive.look <= 0.001F) {
         return;
      }

      float x = Mth.clamp(-drive.lookPitch + drive.lean, -55.0F, 60.0F) * Mth.DEG_TO_RAD;
      float y = -Mth.clamp(drive.lookYaw, -70.0F, 70.0F) * Mth.DEG_TO_RAD;
      slerpTo(head, x, y, 0.0F, drive.look);
   }

   /** Quaternion slerp from the bone's current rotation to the target (renderer order Z·Y·X). */
   private static void slerpTo(GeoBone bone, float x, float y, float z, float weight) {
      Quaternionf from = new Quaternionf().rotationZYX(bone.rotZ, bone.rotY, bone.rotX);
      Quaternionf to = new Quaternionf().rotationZYX(z, y, x);
      Vector3f euler = from.slerp(to, Mth.clamp(weight, 0.0F, 1.0F)).getEulerAnglesZYX(new Vector3f());
      bone.rotX = euler.x;
      bone.rotY = euler.y;
      bone.rotZ = euler.z;
   }

   /** Loop time of a clip (seek/overlay/blend do not wrap LOOP clips, animation-system §4). */
   static double wrap(double seconds, Animation clip) {
      double length = Math.max(1.0E-3, clip.lengthSeconds());
      return ((seconds % length) + length) % length;
   }

   @Nullable
   private static Animation clip(String name) {
      return AnimCache.animation(CLIPS, name);
   }

   // --- body transform ---

   /** Yaw, bob, then lean / bank / crash tumble about the body centre, then player scale. Same on the draw and the anchor stacks. */
   private static void transform(PoseStack stack, LegionSuitEntity suit, Drive drive, LegionPhase phase, float ticks) {
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - drive.yaw));
      float bob = phase == LegionPhase.CRASH ? 0.0F : (1.0F - drive.cruise) * 0.06F * Mth.sin(ticks * 0.16F + suit.getId());
      stack.translate(0.0F, CENTRE + bob, 0.0F);
      if (phase == LegionPhase.CRASH) {
         float age = suit.clientPhaseAge() + Mth.frac(ticks);
         stack.mulPose(Axis.YP.rotationDegrees(age * 23.0F));
         stack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.5F) * 35.0F));
      }

      // Forward lean tips the head toward -Z (the suit's front): a negative turn about X.
      stack.mulPose(Axis.XP.rotationDegrees(-drive.lean));
      stack.mulPose(Axis.ZP.rotationDegrees(drive.bank));
      stack.translate(0.0F, -CENTRE, 0.0F);
      stack.scale(BODY_SCALE, BODY_SCALE, BODY_SCALE);
   }

   // --- anchors (VFX of the same frame) ---

   private static Anchors anchors(BakedGeoModel shell, Matrix4f world, int tick) {
      GeoBone right = shell.getBone("armorrightarm");
      GeoBone left = shell.getBone("armorleftarm");
      GeoBone body = shell.getBone("armorbody");
      return new Anchors(
         palm(world, right),
         palm(world, left),
         point(world, body, 0.0F, 19.5F, -2.6F),
         right == null ? point(world, body, 3.0F, 24.5F, 0.5F) : point(world, body, right.pivotX * 0.75F, 24.5F, 0.5F),
         tick);
   }

   /** Palm centre at the end of the arm, half a pixel outward of the bone pivot line. */
   private static Vec3 palm(Matrix4f world, @Nullable GeoBone arm) {
      if (arm == null) {
         return point(world, null, 0.0F, 12.0F, 0.0F);
      }

      return point(world, arm, arm.pivotX + Math.signum(arm.pivotX) * 1.0F, arm.pivotY - 11.0F, arm.pivotZ);
   }

   /** A point in shell pixels through the bone's own turn (top-level bones: pivot, Z·Y·X, scale) and the body transform. */
   private static Vec3 point(Matrix4f world, @Nullable GeoBone bone, float px, float py, float pz) {
      Matrix4f m = new Matrix4f(world);
      if (bone != null) {
         m.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
         m.rotateZ(bone.rotZ).rotateY(bone.rotY).rotateX(bone.rotX);
         m.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
         m.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
      }

      Vector4f v = m.transform(new Vector4f(px / 16.0F, py / 16.0F, pz / 16.0F, 1.0F));
      return new Vec3(v.x, v.y, v.z);
   }

   // --- parts ---

   /** Copies the posed top-level shell bones onto another geo's top-level bones of the same name (flames use camelCase). */
   private static void copyTop(BakedGeoModel from, BakedGeoModel to) {
      for (GeoBone target : to.topLevelBones()) {
         GeoBone source = from.getBone(target.name.toLowerCase(Locale.ROOT));
         if (source == null) {
            continue;
         }

         target.rotX = source.rotX - source.initRotX + target.initRotX;
         target.rotY = source.rotY - source.initRotY + target.initRotY;
         target.rotZ = source.rotZ - source.initRotZ + target.initRotZ;
         target.posX = source.posX;
         target.posY = source.posY;
         target.posZ = source.posZ;
      }
   }

   private static void signatureParts(LegionSuitEntity suit, Drive drive, MarkId mark, boolean camo, float ticks, BakedGeoModel shell, PoseStack stack,
      MultiBufferSource buffers, int light) {
      List<PlayerGeoLayer.Part> parts = new ArrayList<>(2);
      SignatureVisuals.suitParts(mark, suit.flag(LegionSuitEntity.FLAG_LASER), camo, drive.camoRipple, suit.flag(LegionSuitEntity.FLAG_FIST_AWAY),
         drive.blast, drive.turret, -Mth.clamp(drive.aimYaw, -75.0F, 75.0F) * Mth.DEG_TO_RAD, (drive.lean - drive.aimPitch) * Mth.DEG_TO_RAD, ticks, parts);
      for (PlayerGeoLayer.Part part : parts) {
         BakedGeoModel geo = AnimCache.model(part.geo());
         if (geo == null) {
            continue;
         }

         geo.resetBones();
         if (part.pose() != null) {
            part.pose().accept(geo);
         }

         copyTop(shell, geo);
         MarkParts.draw(geo, stack, buffers, part.passes(), light, 1.0F);
      }
   }

   /** Feet and palm thrusters; damaged suits sputter, a crashing one coughs out. */
   private static void flames(LegionSuitEntity suit, Drive drive, LegionPhase phase, boolean critical, float ticks, BakedGeoModel shell, PoseStack stack,
      MultiBufferSource buffers) {
      float throttle = Mth.lerp(Mth.frac(ticks), suit.clientThrottleO, suit.clientThrottle);
      float feet;
      float palms;
      switch (phase) {
         case APPROACH, LEAVE -> {
            feet = 2.2F + 1.0F * throttle;
            palms = 1.0F + 0.6F * throttle;
         }
         case BRAKE -> {
            feet = 2.4F;
            palms = 1.9F;
         }
         case CRASH -> {
            feet = sputter(suit, ticks, 0.35F) ? 0.9F : 0.0F;
            palms = 0.0F;
         }
         default -> {
            ThrusterFlames.Lengths lengths = ThrusterFlames.lengths(drive.cruise > 0.5F ? ThrusterFlames.Mode.CRUISE : ThrusterFlames.Mode.HOVER, throttle);
            feet = lengths.feet();
            palms = lengths.palms();
         }
      }

      if (critical && phase != LegionPhase.CRASH && !sputter(suit, ticks, 0.75F)) {
         feet *= 0.25F;
         palms = 0.0F;
      }

      float time = ticks + (suit.getId() % 16) * 3.7F;
      ResourceLocation texture = ThrusterFlames.texture((int)(time * 0.75F));
      List<PlayerGeoLayer.Pass> passes = List.of(PlayerGeoLayer.Pass.glow(texture, 0.45F, 0.8F, 1.0F), PlayerGeoLayer.Pass.glow(texture, 0.8F, 0.9F, 1.0F));
      if (feet > 0.01F) {
         BakedGeoModel geo = AnimCache.model(ThrusterFlames.FEET);
         if (geo != null) {
            geo.resetBones();
            copyTop(shell, geo);
            ThrusterFlames.flicker(geo, feet, time, "flameRight", "flameLeft");
            MarkParts.draw(geo, stack, buffers, passes, LightTexture.FULL_BRIGHT, 1.0F);
         }
      }

      if (palms > 0.01F) {
         BakedGeoModel geo = AnimCache.model(ThrusterFlames.PALMS);
         if (geo != null) {
            geo.resetBones();
            copyTop(shell, geo);
            ThrusterFlames.flicker(geo, palms, time, "flameRight", "flameLeft");
            // An arm on the target shows its repulsor, not a thruster.
            hideFlame(geo, "flameRight", drive.aimRight > 0.5F);
            hideFlame(geo, "flameLeft", drive.aimLeft > 0.5F);
            MarkParts.draw(geo, stack, buffers, passes, LightTexture.FULL_BRIGHT, 1.0F);
         }
      }
   }

   private static void hideFlame(BakedGeoModel geo, String name, boolean hidden) {
      GeoBone bone = geo.getBone(name);
      if (bone != null && hidden) {
         bone.scaleX = 0.0F;
         bone.scaleY = 0.0F;
         bone.scaleZ = 0.0F;
      }
   }

   /** Deterministic engine stutter: the same on every client for the same suit and tick. */
   static boolean sputter(Entity suit, float ticks, float on) {
      int step = (int)(ticks / 2.0F);
      int hash = (step * 73856093) ^ (suit.getId() * 19349663);
      return ((hash >>> 8) & 0xFF) / 255.0F < on;
   }

   private static float glow(LegionSuitEntity suit, LegionPhase phase, boolean critical, boolean camo, float ticks) {
      if (camo) {
         return 0.0F;
      }

      float pulse = 0.85F + 0.15F * Mth.sin(ticks * 0.15F + suit.getId());
      if (phase == LegionPhase.CRASH) {
         return sputter(suit, ticks, 0.3F) ? 0.6F : 0.05F;
      }

      if (critical) {
         return sputter(suit, ticks, 0.7F) ? pulse * 0.8F : 0.15F;
      }

      return pulse;
   }

   /**
    * Per-suit smoothed motion state. Weights ease with real time
    * ({@code 1 - exp(-k·dt)}, dt bounded); one update per frame (a repeated
    * timestamp or the shadow pass does not advance it).
    */
   private static final class Drive {
      float lastSeconds = Float.NaN;
      float yaw = Float.NaN;
      float lean;
      float bank;
      float cruise;
      float aimRight;
      float aimLeft;
      float aimYaw;
      float aimPitch;
      float look;
      float lookYaw;
      float lookPitch;
      float camo = 1.0F;
      float camoRipple;
      float turret;
      float blast;
      float flaps;
      float lastYaw;

      void update(LegionSuitEntity suit, float partialTick) {
         float now = RealTime.seconds();
         float dt = Float.isNaN(this.lastSeconds) ? 1.0F : Mth.clamp(now - this.lastSeconds, 0.0F, 0.1F);
         this.lastSeconds = now;
         float bodyYaw = Mth.rotLerp(partialTick, suit.yRotO, suit.getYRot());
         if (Float.isNaN(this.yaw)) {
            this.yaw = bodyYaw;
            this.lastYaw = bodyYaw;
            dt = 1.0F;
         }

         this.yaw = bodyYaw;
         LegionPhase phase = suit.phase();
         Vec3 velocity = new Vec3(suit.getX() - suit.xo, suit.getY() - suit.yo, suit.getZ() - suit.zo);
         Vec3 forward = LegionSteering.forward(bodyYaw);
         double ahead = velocity.x * forward.x + velocity.z * forward.z;
         double side = velocity.x * -forward.z + velocity.z * forward.x;
         float yawRate = dt > 0.0F ? Mth.wrapDegrees(bodyYaw - this.lastYaw) / Math.max(dt * 20.0F, 1.0E-3F) : 0.0F;
         this.lastYaw = bodyYaw;
         float leanTarget;
         float bankTarget;
         float cruiseTarget;
         switch (phase) {
            case APPROACH -> {
               leanTarget = 72.0F;
               bankTarget = Mth.clamp(-yawRate * 2.5F, -35.0F, 35.0F);
               cruiseTarget = 1.0F;
            }
            case BRAKE -> {
               leanTarget = -28.0F;
               bankTarget = 0.0F;
               cruiseTarget = 0.0F;
            }
            case READY -> {
               leanTarget = 0.0F;
               bankTarget = 0.0F;
               cruiseTarget = 0.0F;
            }
            case LEAVE -> {
               leanTarget = 15.0F;
               bankTarget = 0.0F;
               cruiseTarget = 1.0F;
            }
            case CRASH -> {
               leanTarget = 40.0F;
               bankTarget = 0.0F;
               cruiseTarget = 0.0F;
            }
            default -> {
               float speed = (float)Math.max(0.0, ahead);
               leanTarget = Mth.clamp((float)ahead * 85.0F, -18.0F, 70.0F);
               bankTarget = Mth.clamp((float)side * -60.0F - yawRate * 1.5F, -32.0F, 32.0F);
               cruiseTarget = Mth.clamp((speed - 0.25F) / 0.45F, 0.0F, 1.0F);
            }
         }

         float k = ease(dt, 6.0F);
         this.lean = Mth.lerp(k, this.lean, leanTarget);
         this.bank = Mth.lerp(ease(dt, 5.0F), this.bank, bankTarget);
         this.cruise = Mth.lerp(ease(dt, 4.0F), this.cruise, cruiseTarget);
         Entity target = suit.aimId() >= 0 ? suit.level().getEntity(suit.aimId()) : null;
         boolean fighting = phase == LegionPhase.ACTIVE && target != null;
         if (fighting) {
            Vec3 from = suit.getPosition(partialTick).add(0.0, 1.35, 0.0);
            Vec3 to = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.55, 0.0).subtract(from);
            float yawTo = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitchTo = (float)(-(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
            this.aimYaw = Mth.wrapDegrees(yawTo - bodyYaw);
            this.aimPitch = pitchTo;
            this.lookYaw = this.aimYaw;
            this.lookPitch = this.aimPitch;
         } else {
            this.lookYaw = Mth.lerp(ease(dt, 4.0F), this.lookYaw, 0.0F);
            this.lookPitch = Mth.lerp(ease(dt, 4.0F), this.lookPitch, phase == LegionPhase.APPROACH || this.cruise > 0.5F ? -10.0F : 4.0F);
         }

         LegionMove move = suit.clientMove;
         float moveAge = suit.tickCount - suit.clientMoveStart + partialTick;
         boolean recent = moveAge >= 0.0F && moveAge < 30.0F;
         boolean left = recent && (move == LegionMove.FIRE_LEFT || move == LegionMove.FIRE_BOTH);
         MarkId mark = suit.mark();
         boolean twoHand = mark == MarkId.MARK_42 || mark == MarkId.IRON_HEART_MK3;
         this.aimRight = Mth.lerp(ease(dt, 9.0F), this.aimRight, fighting && !suit.flag(LegionSuitEntity.FLAG_PULSE) ? 1.0F : 0.0F);
         this.aimLeft = Mth.lerp(ease(dt, 9.0F), this.aimLeft, fighting && (left || twoHand) && !suit.flag(LegionSuitEntity.FLAG_PULSE) ? 1.0F : 0.0F);
         // The head always looks: at the target in a fight, else along the flight.
         this.look = Mth.lerp(ease(dt, 6.0F), this.look, phase == LegionPhase.CRASH ? 0.0F : 1.0F);
         boolean camo = suit.flag(LegionSuitEntity.FLAG_CAMO);
         this.camo = Mth.lerp(ease(dt, 3.0F), this.camo, camo ? 0.12F : 1.0F);
         float speed = (float)velocity.length();
         this.camoRipple = 0.12F + 0.5F * Mth.clamp(speed * 4.0F, 0.0F, 1.0F) + (recent && move == LegionMove.FIRE_RIGHT ? 0.3F : 0.0F);
         this.turret = Mth.lerp(ease(dt, 12.0F), this.turret, suit.flag(LegionSuitEntity.FLAG_GUN) ? 1.0F : 0.0F);
         this.blast = Mth.lerp(ease(dt, 10.0F), this.blast, suit.flag(LegionSuitEntity.FLAG_BOOST) ? 1.0F : 0.0F);
         boolean missiles = recent && move == LegionMove.MISSILES && moveAge < LegionMove.MISSILES.length();
         this.flaps = Mth.lerp(ease(dt, 10.0F), this.flaps, missiles ? 1.0F : 0.0F);
      }

      private static float ease(float dt, float rate) {
         return 1.0F - (float)Math.exp(-rate * dt);
      }
   }

   /** Names of the shell's top-level bones (tests check the clips only use these). */
   static String[] topBones() {
      return TOP.clone();
   }
}
