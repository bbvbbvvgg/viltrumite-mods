package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import net.minecraft.Util;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Reinhard's whole-body model replaces the player in third person (spec §8, stage 1b Task 3).
 * The vanilla render is always cancelled for Reinhard, invisible included, so a glowing invisible
 * Reinhard outlines as the knight, never as a vanilla player. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReinhardRenderer {
   /** Vanilla PlayerRenderer.scale: a 32 px model stands 1.875 blocks like a player. */
   static final float PLAYER_SCALE = 0.9375F;
   /** Vanilla alpha of an invisible player seen by a spectator or teammate. */
   static final float TRANSLUCENT_ALPHA = 0.15F;
   static final String WALK = "walk";
   static final String KNIGHT_HOLD = "knight_left_arm_hold";
   static final String CROUCH = "crouch";
   static final String SIT = "sit";
   static final String SWIM = "swim";
   static final String PARRY = "parry";
   /** First-person parry flick of the main arm at full pulse weight (degrees, right arm; mirrored for the left). */
   static final float FP_PARRY_PITCH = -28.0F;
   static final float FP_PARRY_YAW = 14.0F;
   static final float FP_PARRY_ROLL = 8.0F;
   /** Fallback stance while a stance clip is missing: every bone at rest. */
   private static final Animation REST = new Animation("rest", 1.0, Animation.LoopType.LOOP, Map.of(), List.of());

   private ReinhardRenderer() {
   }

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }

      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (snapshot == null) {
         StanceBlend.reset(player);
         ParryPulse.THIRD_PERSON.reset(player);
         return;
      }

      BakedGeoModel body = AnimCache.model(ReinhardAssets.BODY);
      if (body == null) {
         return;
      }

      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      float partialTick = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      nameTag(player, event.getRenderer(), stack, buffers, light, partialTick);
      ReinhardPoses.Passes passes = ReinhardPoses.passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player),
         mc.shouldEntityAppearGlowing(player));
      if (!passes.body() && !passes.outline()) {
         return;
      }

      ReinhardPoses.Pose pose = pose(player, snapshot, partialTick);
      float[] weights = StanceBlend.weights(player, ReinhardView.mode(snapshot), Util.getNanos());
      float swim = player.getSwimAmount(partialTick);
      ParryPulse.Sample parry = ParryPulse.THIRD_PERSON.sample(player, ReinhardFlags.has(snapshot.heroFlags(), ReinhardFlags.PARRY_POSE), Util.getNanos());
      ReinhardPoses.ReidPhase reidPhase = ReinhardView.reid(snapshot);
      double reidTicks = dev.baranhan.viltrumitecore.client.reinhard.reid.ReidClock.ticks(player, reidPhase, partialTick);
      applyClips(body, pose, StanceBlend.mix(weights, ReinhardView.mode(snapshot)), weights[ReinhardMode.KNIGHT.ordinal()], swim,
         player.walkAnimation.position(partialTick) * ReinhardPoses.SWIM_SECONDS_PER_UNIT, parry, ReinhardPoses.reidClip(reidPhase, reidTicks),
         player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer core ? ReinhardPoses.chopClip(core.getChopTicks(), core.isLeftChop(), partialTick) : null);
      applyHead(body, player, partialTick);
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      stack.pushPose();
      setupRotations(player, stack, partialTick, swim);
      stack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
      if (passes.body()) {
         RenderType type = passes.translucent() ? RenderType.entityTranslucent(ReinhardAssets.TEXTURE) : RenderType.entityCutoutNoCull(ReinhardAssets.TEXTURE);
         float alpha = passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F;
         AnimRenderer.render(body, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, null);
      }

      if (passes.outline() && !ShaderCompat.isShadowPass()) {
         // Vanilla draws an invisible glowing entity with RenderType.outline only (the buffer source is the outline source).
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(ReinhardAssets.TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }

      if (passes.extras()) {
         if (!ShaderCompat.isShadowPass()) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(ReinhardAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               1.0F, 1.0F, 1.0F, 1.0F, null);
         }

         ReinhardSword.drawSheathed(stack, body, buffers, light, overlay, ReinhardPoses.bladeOut(reidPhase, reidTicks),
            reidPhase != ReinhardPoses.ReidPhase.DRAWN);
         if (reidPhase == ReinhardPoses.ReidPhase.DRAWN) {
            // Stage 4: Reid in the right hand (the vanilla item of that hand is hidden by drawsHeldItem).
            GeoBone hand = body.getBone(ReinhardAssets.RIGHT_ARM);
            if (hand != null) {
               stack.pushPose();
               boneFrame(stack, hand);
               stack.mulPoseMatrix(HeldItemAnchor.local(HumanoidArm.RIGHT));
               ReinhardSword.drawInHand(stack, buffers, light, overlay);
               stack.popPose();
            }
         }
         heldItems(player, body, ReinhardView.reid(snapshot), ReinhardPoses.cinematicAction(snapshot.actionId()), stack, buffers, light);
      }

      stack.popPose();
   }

   /**
    * First person: the model's arms (with glow) at vanilla's rest position replace the vanilla arm;
    * an arm the stance hides (Knight's left arm) is not drawn. Mixins that move the PoseStack before
    * the arm still apply because RenderArmEvent fires inside renderPlayerArm.
    */
   @SubscribeEvent
   public static void onRenderArm(RenderArmEvent event) {
      AbstractClientPlayer player = event.getPlayer();
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (snapshot == null) {
         return;
      }

      event.setCanceled(true);
      if (!ReinhardPoses.firstPersonArmVisible(ReinhardView.mode(snapshot), event.getArm(), snapshot.heroFlags())) {
         return;
      }

      PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      ModelPart arm = event.getArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
      // Pose null: PlayerGeoLayer resets the bones, i.e. the arm follows vanilla's first-person arm exactly.
      PlayerGeoLayer.Part part = new PlayerGeoLayer.Part(ReinhardAssets.ARMS,
         List.of(PlayerGeoLayer.Pass.cutout(ReinhardAssets.TEXTURE), PlayerGeoLayer.Pass.glow(ReinhardAssets.GLOW)), null);
      boolean flick = event.getArm() == player.getMainArm();
      // Sampled for the main arm only: the off arm's event must not reset the pulse.
      float w = flick ? ParryPulse.FIRST_PERSON.sample(player, ReinhardFlags.has(snapshot.heroFlags(), ReinhardFlags.PARRY_POSE), Util.getNanos()).weight() : 0.0F;
      float oldX = arm.xRot;
      float oldY = arm.yRot;
      float oldZ = arm.zRot;
      if (w > 0.0F) {
         // Blessing of the Holy Sword: a short block flick of the main arm (stage 2 Task 2).
         float m = event.getArm() == HumanoidArm.LEFT ? -1.0F : 1.0F;
         arm.xRot += (float) Math.toRadians(FP_PARRY_PITCH * w);
         arm.yRot += (float) Math.toRadians(FP_PARRY_YAW * w * m);
         arm.zRot += (float) Math.toRadians(FP_PARRY_ROLL * w * m);
      }

      ReinhardAbility cinematic = ReinhardPoses.cinematicAction(snapshot.actionId());
      double cinematicTicks = snapshot.actionElapsed() + Minecraft.getInstance().getFrameTime();
      boolean physicalLeft = event.getArm() == HumanoidArm.LEFT;
      ReinhardFirstPersonPose.Offset action = cinematic == ReinhardAbility.WORLD_CUT
         ? ReinhardFirstPersonPose.worldCut(cinematicTicks, physicalLeft)
         : cinematic == ReinhardAbility.WORLD_ERASURE
            ? ReinhardFirstPersonPose.worldErasure(cinematicTicks, physicalLeft)
            : flick ? firstPersonAction(player, snapshot) : ReinhardFirstPersonPose.Offset.NONE;
      if (flick && action == ReinhardFirstPersonPose.Offset.NONE) {
         // Stage 4: the Reid draw / sheathe pull.
         ReinhardPoses.ReidPhase reid = ReinhardView.reid(snapshot);
         action = ReinhardFirstPersonPose.reid(reid, dev.baranhan.viltrumitecore.client.reinhard.reid.ReidClock.ticks(player, reid, Minecraft.getInstance().getFrameTime()));
      }
      boolean moved = action != ReinhardFirstPersonPose.Offset.NONE;
      if (moved) {
         // Stage 3: the ability's first-person pose (jab, catch, ...), mirrored for a left main arm.
         float m = event.getArm() == HumanoidArm.LEFT ? -1.0F : 1.0F;
         event.getPoseStack().pushPose();
         event.getPoseStack().translate(action.x() * m, action.y(), action.z());
         arm.xRot += (float) Math.toRadians(action.pitch());
         arm.yRot += (float) Math.toRadians(action.yaw() * m);
         arm.zRot += (float) Math.toRadians(action.roll() * m);
      }

      PlayerGeoLayer.renderArmPart(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), model, arm, part);
      if (event.getArm() == HumanoidArm.RIGHT && ReinhardView.reid(snapshot) == ReinhardPoses.ReidPhase.DRAWN) {
         // Stage 4: Reid in the first-person right hand (onRenderHand hid the slot's item).
         PoseStack pose = event.getPoseStack();
         pose.pushPose();
         arm.translateAndRotate(pose);
         pose.mulPoseMatrix(HeldItemAnchor.vanilla(HumanoidArm.RIGHT));
         ReinhardSword.drawInHand(pose, event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
         pose.popPose();
      }
      if (moved) {
         event.getPoseStack().popPose();
      }

      arm.xRot = oldX;
      arm.yRot = oldY;
      arm.zRot = oldZ;
   }

   /**
    * Stage 4: while Reid is out of the sheath the right hand holds it: the slot's item there is not
    * drawn (it stays in the slot); vanilla's empty-arm render runs instead, which fires RenderArmEvent
    * ({@link #onRenderArm} draws the geo arm and, when drawn, the blade).
    */
   @SubscribeEvent
   public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      AbstractClientPlayer player = mc.player;
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (snapshot == null) return;
      HumanoidArm arm = ReinhardPoses.physicalArm(player.getMainArm(), event.getHand());
      ReinhardAbility action = ReinhardPoses.cinematicAction(snapshot.actionId());
      if (ReinhardPoses.drawsHeldItem(arm, ReinhardView.reid(snapshot), action)) return;

      event.setCanceled(true);
      if (!player.isInvisible()) {
         ((dev.baranhan.viltrumitecore.client.mixin.ItemInHandRendererInvoker) mc.getEntityRenderDispatcher().getItemInHandRenderer())
            .viltrumite$renderPlayerArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getEquipProgress(),
               event.getHand() == InteractionHand.MAIN_HAND ? event.getSwingProgress() : 0.0F, arm);
      }
   }

   /** The synced action's first-person offset (time like the third-person overlay, hit-stop held). */
   private static ReinhardFirstPersonPose.Offset firstPersonAction(AbstractClientPlayer player, HeroPublicSnapshot snapshot) {
      ReinhardAbility[] all = ReinhardAbility.values();
      int id = snapshot.actionId();
      if (id < 0 || id >= all.length) {
         return ReinhardFirstPersonPose.Offset.NONE;
      }

      float partial = Minecraft.getInstance().getFrameTime();
      double ticks = snapshot.actionElapsed() + partial - dev.baranhan.viltrumitecore.client.render.vfx.HitStop.heldTicks(player, partial);
      if (snapshot.actionLength() > 0) {
         ticks = Math.min(ticks, snapshot.actionLength());
      }

      return ReinhardFirstPersonPose.of(all[id], snapshot.variant(), Math.max(0.0, ticks), snapshot.actionLength());
   }

   /**
    * Stance (old → new mode blended by {@link StanceBlend}), the walk over the limbs by swing,
    * Knight's left arm held behind the back over the walk (by the Knight stance weight),
    * crouch/sit added like vanilla's model code, then the action overlay, then the parry
    * reaction weighted by its real-time pulse.
    */
   static void applyClips(BakedGeoModel model, ReinhardPoses.Pose pose, StanceBlend.Mix mix, float knightWeight, float swim, double swimSeconds,
      ParryPulse.Sample parry, @javax.annotation.Nullable ReinhardPoses.Clip reid, @javax.annotation.Nullable ReinhardPoses.Clip chop) {
      Animation to = clip(ReinhardPoses.stance(mix.to()));
      if (mix.weight() >= StanceBlend.SETTLED || mix.from() == mix.to()) {
         AnimationController.seek(to, model, pose.stanceSeconds());
      } else {
         AnimationController.blend(model, clip(ReinhardPoses.stance(mix.from())), pose.stanceSeconds(), to, pose.stanceSeconds(), mix.weight());
      }

      Animation walk = AnimCache.animation(ReinhardAssets.ANIMATIONS, WALK);
      if (walk != null) {
         AnimationController.overlay(walk, model, pose.walkSeconds(), pose.walk());
      }

      Animation hold = AnimCache.animation(ReinhardAssets.ANIMATIONS, KNIGHT_HOLD);
      if (hold != null) {
         AnimationController.overlay(hold, model, pose.stanceSeconds(), knightWeight);
      }

      Animation stroke = swim > 0.0F ? AnimCache.animation(ReinhardAssets.ANIMATIONS, SWIM) : null;
      if (stroke != null) {
         // HumanoidModel lerps the arms and legs to the crawl stroke by swimAmount.
         AnimationController.overlay(stroke, model, swimSeconds, swim);
      }

      String additive = switch (pose.body()) {
         case CROUCH -> CROUCH;
         case SIT -> SIT;
         default -> null;
      };
      Animation state = additive == null ? null : AnimCache.animation(ReinhardAssets.ANIMATIONS, additive);
      if (state != null) {
         AnimationController.additive(state, model, 0.0, 1.0F);
      }

      Animation reidClip = reid == null ? null : AnimCache.animation(ReinhardAssets.ANIMATIONS, reid.animation());
      boolean drawnIdle = reid != null && ReinhardClips.IDLE_HOLY_DRAWN.equals(reid.animation());
      if (reidClip != null && drawnIdle) {
         AnimationController.overlay(reidClip, model, reid.seconds());
      }

      ReinhardPoses.Clip overlay = pose.overlay();
      if (overlay != null) {
         Animation clip = AnimCache.animation(ReinhardAssets.ANIMATIONS, overlay.animation());
         if (clip != null) {
            AnimationController.overlay(clip, model, overlay.seconds());
         }
      }

      // Draw/sheathe override an action; DRAWN idle is a base below the action.
      if (reidClip != null && !drawnIdle) {
         AnimationController.overlay(reidClip, model, reid.seconds());
      }

      // Stage 4: Lethal Chop (model mixins do not run for the replaced body).
      Animation chopClip = chop == null ? null : AnimCache.animation(ReinhardAssets.ANIMATIONS, chop.animation());
      if (chopClip != null) {
         AnimationController.overlay(chopClip, model, chop.seconds());
      }

      Animation parryClip = parry.weight() > 0.0F ? AnimCache.animation(ReinhardAssets.ANIMATIONS, PARRY) : null;
      if (parryClip != null) {
         AnimationController.overlay(parryClip, model, parry.seconds(), parry.weight());
      }
   }

   private static Animation clip(String name) {
      Animation clip = AnimCache.animation(ReinhardAssets.ANIMATIONS, name);
      return clip != null ? clip : REST;
   }

   /**
    * Vanilla LivingEntityRenderer/PlayerRenderer.setupRotations for a model drawn without the vanilla
    * body: bed (sleep), death tilt, elytra pitch and roll, swim pitch. Model mixins never run here.
    */
   static void setupRotations(AbstractClientPlayer player, PoseStack stack, float partialTick, float swim) {
      float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
      if (player.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
         Direction bed = player.getBedOrientation();
         if (bed != null) {
            float shift = player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1F;
            stack.translate(-bed.getStepX() * shift, 0.0F, -bed.getStepZ() * shift);
         }

         stack.mulPose(Axis.YP.rotationDegrees(bed != null ? ReinhardPoses.sleepYaw(bed) : bodyYaw));
         stack.mulPose(Axis.ZP.rotationDegrees(90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(270.0F));
         return;
      }

      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      if (player.deathTime > 0) {
         float dying = Math.min(1.0F, Mth.sqrt((player.deathTime + partialTick - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }

      if (player.isFallFlying()) {
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.elytraPitch(player.getFallFlyingTicks() + partialTick, player.getXRot(), player.isAutoSpinAttack())));
         Vec3 view = player.getViewVector(partialTick);
         Vec3 motion = player.getDeltaMovementLerped(partialTick);
         double motionSqr = motion.horizontalDistanceSqr();
         double viewSqr = view.horizontalDistanceSqr();
         if (motionSqr > 0.0 && viewSqr > 0.0) {
            double cos = (motion.x * view.x + motion.z * view.z) / Math.sqrt(motionSqr * viewSqr);
            double side = motion.x * view.z - motion.z * view.x;
            stack.mulPose(Axis.YP.rotation((float) (Math.signum(side) * Math.acos(cos))));
         }
      } else if (swim > 0.0F) {
         boolean inWater = player.isInWater() || player.isInFluidType((fluid, height) -> player.canSwimInFluidType(fluid));
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.swimPitch(swim, inWater, player.getXRot())));
         if (player.isVisuallySwimming()) {
            stack.translate(0.0F, -1.0F, 0.3F);
         }
      }
   }

   /** The head follows the look (vanilla netHeadYaw / headPitch); BakedGeoModel turns are negated. */
   static void applyHead(BakedGeoModel body, AbstractClientPlayer player, float partialTick) {
      GeoBone head = body.getBone(ReinhardAssets.HEAD);
      if (head == null) {
         return;
      }

      float yaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
      float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
      head.rotY += (float) Math.toRadians(-yaw);
      head.rotX += (float) Math.toRadians(-pitch);
   }

   static ReinhardPoses.Pose pose(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick) {
      boolean sitting = player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit();
      float swing = sitting ? 0.0F : player.walkAnimation.speed(partialTick);
      ReinhardPoses.Input input = new ReinhardPoses.Input(ReinhardView.mode(snapshot), snapshot.heroFlags(), snapshot.actionId(), snapshot.actionElapsed(),
         snapshot.actionLength(), partialTick, swing, player.walkAnimation.position(partialTick), AnimRenderer.time(partialTick) / 20.0, player.getPose(), sitting,
         snapshot.variant(), dev.baranhan.viltrumitecore.client.render.vfx.HitStop.heldTicks(player, partialTick));
      return ReinhardPoses.pose(input);
   }

   /** Moves the stack from the model frame to a top-level bone's pivot frame (same order as AnimRenderer). */
   static void boneFrame(PoseStack stack, GeoBone bone) {
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      stack.mulPose(Axis.YP.rotation(bone.rotY));
      stack.mulPose(Axis.XP.rotation(bone.rotX));
      stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
   }

   private static void heldItems(AbstractClientPlayer player, BakedGeoModel body, ReinhardPoses.ReidPhase reid, @Nullable ReinhardAbility action, PoseStack stack, MultiBufferSource buffers, int light) {
      for (HumanoidArm arm : HumanoidArm.values()) {
         ItemStack item = player.getItemInHand(player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
         GeoBone bone = body.getBone(arm == HumanoidArm.RIGHT ? ReinhardAssets.RIGHT_ARM : ReinhardAssets.LEFT_ARM);
         if (item.isEmpty() || bone == null || !ReinhardPoses.drawsHeldItem(arm, reid, action)) {
            continue;
         }

         stack.pushPose();
         boneFrame(stack, bone);
         stack.mulPoseMatrix(HeldItemAnchor.local(arm));
         boolean left = arm == HumanoidArm.LEFT;
         Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, item,
            left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, stack, buffers, light);
         stack.popPose();
      }
   }

   /** Forge's name tag step of EntityRenderer.render (skipped by the cancel), through vanilla renderNameTag. */
   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partialTick) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker) renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partialTick);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (NameTagRules.show(result == Event.Result.DENY, result == Event.Result.ALLOW, invoker.viltrumitecore$shouldShowName(player))) {
         invoker.viltrumitecore$renderNameTag(player, event.getContent(), stack, buffers, light);
      }
   }
}
