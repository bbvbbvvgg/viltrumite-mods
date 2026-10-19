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
         ReinhardMotion.reset(player);
         ReinhardRecovery.reset(player);
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
      float[] motion=ReinhardMotion.sample(player,player.isCrouching(),player.isSprinting(),
         !player.onGround() && !player.isPassenger() && swim<.1F && !player.isFallFlying(),player.getDeltaMovement().y>0,Util.getNanos());
      applyClips(body, pose, StanceBlend.mix(weights, ReinhardView.mode(snapshot)), weights[ReinhardMode.KNIGHT.ordinal()], swim,
         player.walkAnimation.position(partialTick) * ReinhardPoses.SWIM_SECONDS_PER_UNIT, parry, ReinhardPoses.reidClip(reidPhase, reidTicks),
         player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer core ? ReinhardPoses.chopClip(core.getChopTicks(), core.isLeftChop(), partialTick) : null, motion);
      applyMelee(body, player, snapshot, partialTick);
      applyHead(body, player, partialTick);
      ReinhardRecovery.body(player,body,recoveryKey(snapshot),Util.getNanos());
      org.joml.Matrix4f[] equipment=ReinhardSword.equipment(body,reidPhase,reidTicks,
         snapshot.actionId()==ReinhardAbility.SHEATH_STRIKE.ordinal(),actionTicks(player,snapshot,partialTick),
         weights[ReinhardMode.HOLY_SWORD.ordinal()]);
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

         GeoBone hand = body.getBone(ReinhardAssets.RIGHT_ARM);
         if (hand != null) {
            ReinhardSword.drawEquipment(stack,equipment,buffers,light,overlay);
         }
         heldItems(player, body, ReinhardView.reid(snapshot), ReinhardPoses.action(snapshot.actionId()), ReinhardView.mode(snapshot),stack, buffers, light);
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
         ReinhardRecovery.reset(player);
         return;
      }

      event.setCanceled(true);
      if (!ReinhardPoses.firstPersonArmVisible(ReinhardView.mode(snapshot), event.getArm(), snapshot.heroFlags())) {
         return;
      }

      PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      ModelPart arm = event.getArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
      PlayerGeoLayer.Part part = new PlayerGeoLayer.Part(ReinhardAssets.ARMS,
         List.of(PlayerGeoLayer.Pass.cutout(ReinhardAssets.TEXTURE), PlayerGeoLayer.Pass.glow(ReinhardAssets.GLOW)),
         geo -> poseFirstPersonElbows(geo,player,snapshot));
      boolean flick = event.getArm() == player.getMainArm();
      boolean actionArm = event.getArm() == HumanoidArm.RIGHT;
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
      double cinematicTicks = actionTicks(player, snapshot, Minecraft.getInstance().getFrameTime());
      boolean physicalLeft = event.getArm() == HumanoidArm.LEFT;
      ReinhardFirstPersonPose.Offset action = cinematic == ReinhardAbility.WORLD_CUT
         ? ReinhardFirstPersonPose.worldCut(cinematicTicks, physicalLeft)
         : cinematic == ReinhardAbility.WORLD_ERASURE
            ? ReinhardFirstPersonPose.worldErasure(cinematicTicks, physicalLeft)
            : actionArm ? firstPersonAction(player, snapshot) : ReinhardFirstPersonPose.Offset.NONE;
      if (actionArm && action == ReinhardFirstPersonPose.Offset.NONE) {
         // Stage 4: the Reid draw / sheathe pull.
         ReinhardPoses.ReidPhase reid = ReinhardView.reid(snapshot);
         action = ReinhardFirstPersonPose.reid(reid, dev.baranhan.viltrumitecore.client.reinhard.reid.ReidClock.ticks(player, reid, Minecraft.getInstance().getFrameTime()));
      }
      if (!actionArm && cinematic == null) action = ReinhardFirstPersonPose.support(
         ReinhardPoses.action(snapshot.actionId()),cinematicTicks,snapshot.actionLength());
      action = ReinhardRecovery.firstPerson(player,physicalLeft,action,recoveryKey(snapshot),Util.getNanos());
      boolean moved = !action.equals(ReinhardFirstPersonPose.Offset.NONE);
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
      if (event.getArm() == HumanoidArm.RIGHT && ReinhardRecovery.visible(player,ReinhardView.reid(snapshot) != ReinhardPoses.ReidPhase.SHEATHED
         || snapshot.actionId() == ReinhardAbility.SHEATH_STRIKE.ordinal(),Util.getNanos())) {
         PoseStack handPose = new PoseStack();
         arm.translateAndRotate(handPose);
         BakedGeoModel arms = AnimCache.model(ReinhardAssets.ARMS);
         if (arms != null) {
            // Same conversion as PlayerGeoLayer; the grip must follow the lower arm, not the shoulder.
            handPose.translate(5/16F,22/16F,0);
            handPose.scale(-1,-1,1);
            handPose.mulPoseMatrix(ReinhardGrip.cameraHand(arms));
         } else {
            handPose.mulPoseMatrix(HeldItemAnchor.vanilla(HumanoidArm.RIGHT));
            handPose.translate(0,ReinhardSword.HAND_GUARD_PX/16,0);
            handPose.scale(ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE);
         }
         // Belt is outside the FP view. The blade enters along the sheath axis, then settles into the palm.
         org.joml.Matrix4f belt = new org.joml.Matrix4f(handPose.last().pose()).translate(-.5F,-1.4F,.7F);
         ReinhardPoses.ReidPhase phase = ReinhardView.reid(snapshot);
         ReinhardSword.drawAttached(ReinhardRecovery.firstPersonWeaponKey(player),event.getPoseStack(),belt,
            new org.joml.Matrix4f(handPose.last().pose()),phase,
            dev.baranhan.viltrumitecore.client.reinhard.reid.ReidClock.ticks(player,phase,Minecraft.getInstance().getFrameTime()),
            snapshot.actionId()==ReinhardAbility.SHEATH_STRIKE.ordinal(),cinematicTicks,recoveryKey(snapshot),
            event.getMultiBufferSource(),event.getPackedLight(),OverlayTexture.NO_OVERLAY,true);
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
      ReinhardAbility action = ReinhardPoses.action(snapshot.actionId());
      boolean actionArm = action != null && action != ReinhardAbility.REID && arm == HumanoidArm.RIGHT;
      if (!actionArm && ReinhardPoses.drawsHeldItem(arm, ReinhardView.reid(snapshot), action,ReinhardView.mode(snapshot))) return;

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
      double ticks = actionTicks(player, snapshot, partial);

      return ReinhardFirstPersonPose.of(all[id], snapshot.variant(), Math.max(0.0, ticks), snapshot.actionLength());
   }

   private static double actionTicks(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partial) {
      return ReinhardPlayback.actionTicks(snapshot.actionElapsed(), snapshot.actionLength(), partial,
         0);
   }

   /**
    * Stance (old → new mode blended by {@link StanceBlend}), the walk over the limbs by swing,
    * Knight's left arm held behind the back over the walk (by the Knight stance weight),
    * crouch/sit added like vanilla's model code, then the action overlay, then the parry
    * reaction weighted by its real-time pulse.
    */
   static void applyClips(BakedGeoModel model, ReinhardPoses.Pose pose, StanceBlend.Mix mix, float knightWeight, float swim, double swimSeconds,
      ParryPulse.Sample parry, @javax.annotation.Nullable ReinhardPoses.Clip reid, @javax.annotation.Nullable ReinhardPoses.Clip chop) {
      applyClips(model, pose, mix, knightWeight, swim, swimSeconds, parry, reid, chop, false);
   }

   static void applyClips(BakedGeoModel model, ReinhardPoses.Pose pose, StanceBlend.Mix mix, float knightWeight, float swim, double swimSeconds,
      ParryPulse.Sample parry, @Nullable ReinhardPoses.Clip reid, @Nullable ReinhardPoses.Clip chop, boolean sprint) {
      applyClips(model,pose,mix,knightWeight,swim,swimSeconds,parry,reid,chop,
         new float[]{pose.body()==ReinhardPoses.BodyState.CROUCH?1:0,sprint?1:0,0,0});
   }

   static void applyClips(BakedGeoModel model, ReinhardPoses.Pose pose, StanceBlend.Mix mix, float knightWeight, float swim, double swimSeconds,
      ParryPulse.Sample parry, @Nullable ReinhardPoses.Clip reid, @Nullable ReinhardPoses.Clip chop, float[] motion) {
      Animation to = clip(ReinhardPoses.stance(mix.to()));
      if (mix.weights()!=null) {
         model.resetBones(); float sum=0;
         for(ReinhardMode mode:ReinhardMode.values()) {
            float weight=mix.weights()[mode.ordinal()]; if(weight<=0)continue;
            Animation stance=clip(ReinhardPoses.stance(mode));
            ReinhardLayers.overlay(stance,model,ReinhardPlayback.time(stance,pose.stanceSeconds()),weight/(sum+weight));
            sum+=weight;
         }
      } else if (mix.weight() >= StanceBlend.SETTLED || mix.from() == mix.to()) {
         AnimationController.seek(to, model, ReinhardPlayback.time(to, pose.stanceSeconds()));
      } else {
         Animation from = clip(ReinhardPoses.stance(mix.from()));
         AnimationController.blend(model, from, ReinhardPlayback.time(from, pose.stanceSeconds()), to, ReinhardPlayback.time(to, pose.stanceSeconds()), mix.weight());
      }

      Animation walk = AnimCache.animation(ReinhardAssets.ANIMATIONS, WALK);
      if (walk != null) {
         ReinhardLayers.overlay(walk, model, ReinhardPlayback.time(walk, pose.walkSeconds()), pose.walk());
      }

      Animation run=AnimCache.animation(ReinhardAssets.ANIMATIONS,"run");
      if(run!=null) ReinhardLayers.overlay(run,model,ReinhardPlayback.time(run,pose.walkSeconds()),pose.walk()*motion[1]);
      for(int i=2;i<4;i++) {
         Animation air=AnimCache.animation(ReinhardAssets.ANIMATIONS,i==2?"air_rise":"air_fall");
         if(air!=null) ReinhardLayers.overlay(air,model,0,motion[i]);
      }

      Animation hold = AnimCache.animation(ReinhardAssets.ANIMATIONS, KNIGHT_HOLD);
      if (hold != null) {
         ReinhardLayers.overlay(hold, model, ReinhardPlayback.time(hold, pose.stanceSeconds()), knightWeight);
      }

      Animation stroke = swim > 0.0F ? AnimCache.animation(ReinhardAssets.ANIMATIONS, SWIM) : null;
      if (stroke != null) {
         // HumanoidModel lerps the arms and legs to the crawl stroke by swimAmount.
         ReinhardLayers.overlay(stroke, model, ReinhardPlayback.time(stroke, swimSeconds), swim);
      }

      String additive = switch (pose.body()) {
         case CROUCH -> null;
         case SIT -> SIT;
         default -> null;
      };
      Animation state = additive == null ? null : AnimCache.animation(ReinhardAssets.ANIMATIONS, additive);
      if (state != null) {
         AnimationController.additive(state, model, 0.0, 1.0F);
      }

      // Add crouch after all combat layers below: their authored torso rotations must not erase it.
      Animation reidClip = reid == null ? null : AnimCache.animation(ReinhardAssets.ANIMATIONS, reid.animation());
      boolean drawnIdle = reid != null && ReinhardClips.IDLE_HOLY_DRAWN.equals(reid.animation());
      if (reidClip != null && drawnIdle) {
         ReinhardLayers.overlay(reidClip, model, ReinhardPlayback.time(reidClip, reid.seconds()));
      }

      ReinhardPoses.Clip overlay = pose.overlay();
      if (overlay != null) {
         Animation clip = AnimCache.animation(ReinhardAssets.ANIMATIONS, overlay.animation());
         if (clip != null) {
            boolean held = overlay.animation().equals(ReinhardClips.GRAB_HOLD)
               || overlay.animation().equals(ReinhardClips.KICK_TOSS);
            boolean continuation = overlay.animation().equals(ReinhardClips.CATCH)
               || overlay.animation().equals(ReinhardClips.REFLECT) || overlay.animation().equals(ReinhardClips.THROW)
               || overlay.animation().equals(ReinhardClips.KICK);
            float weight = held ? 1 : continuation ? ReinhardPlayback.exit(overlay.seconds(), clip.lengthSeconds())
               : ReinhardPlayback.weight(overlay.seconds(), clip.lengthSeconds());
            ReinhardLayers.overlay(clip, model, ReinhardPlayback.time(clip, overlay.seconds()), weight);
         }
      }

      // Draw/sheathe override an action; DRAWN idle is a base below the action.
      if (reidClip != null && !drawnIdle) {
         ReinhardLayers.overlay(reidClip, model, ReinhardPlayback.time(reidClip, reid.seconds()));
      }

      // Stage 4: Lethal Chop (model mixins do not run for the replaced body).
      Animation chopClip = chop == null ? null : AnimCache.animation(ReinhardAssets.ANIMATIONS, chop.animation());
      if (chopClip != null) {
         ReinhardLayers.overlay(chopClip, model, chop.seconds());
      }

      Animation parryClip = parry.weight() > 0.0F ? AnimCache.animation(ReinhardAssets.ANIMATIONS, PARRY) : null;
      if (parryClip != null) {
         ReinhardLayers.overlay(parryClip, model, parry.seconds(), parry.weight());
      }
      Animation crouch=AnimCache.animation(ReinhardAssets.ANIMATIONS,CROUCH);
      if(crouch!=null && motion[0]>0) AnimationController.additive(crouch,model,0,motion[0]);
   }

   /** Vanilla melee still owns damage, reach, cooldown, mining and item interactions. */
   private static void applyMelee(BakedGeoModel model, AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick) {
      if (snapshot.actionId() >= 0 || ReinhardView.reid(snapshot) == ReinhardPoses.ReidPhase.DRAWING
         || ReinhardView.reid(snapshot) == ReinhardPoses.ReidPhase.SHEATHING) return;
      float swing = player.getAttackAnim(partialTick);
      if (swing <= 0) return;
      HumanoidArm arm = player.swingingArm == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
      Animation clip = AnimCache.animation(ReinhardAssets.ANIMATIONS, arm == HumanoidArm.LEFT ? "melee_left" : "melee_right");
      if (clip != null) ReinhardLayers.overlay(clip, model, swing * clip.lengthSeconds(),
         ReinhardPlayback.weight(swing * clip.lengthSeconds(), clip.lengthSeconds()));
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
      float swing = sitting || !player.onGround() ? 0.0F : player.walkAnimation.speed(partialTick);
      ReinhardPoses.Input input = new ReinhardPoses.Input(ReinhardView.mode(snapshot), snapshot.heroFlags(), snapshot.actionId(), snapshot.actionElapsed(),
         snapshot.actionLength(), partialTick, swing, player.walkAnimation.position(partialTick), (player.tickCount + partialTick) / 20.0, player.getPose(), sitting,
         snapshot.variant(), 0);
      return ReinhardPoses.pose(input);
   }

   private static int recoveryKey(HeroPublicSnapshot snapshot) {
      return snapshot.actionId() >= 0 ? snapshot.actionId()+1 : -1-ReinhardView.mode(snapshot).ordinal();
   }

   private static void poseFirstPersonElbows(BakedGeoModel geo, AbstractClientPlayer player, HeroPublicSnapshot snapshot) {
      geo.resetBones();
      ReinhardPoses.Clip action = snapshot.actionId()<0 ? null : ReinhardClips.clip(ReinhardPoses.action(snapshot.actionId()),snapshot.variant(),
         actionTicks(player,snapshot,Minecraft.getInstance().getFrameTime()));
      if (action == null) {
         ReinhardPoses.ReidPhase phase=ReinhardView.reid(snapshot);
         action=ReinhardPoses.reidClip(phase,dev.baranhan.viltrumitecore.client.reinhard.reid.ReidClock.ticks(
            player,phase,Minecraft.getInstance().getFrameTime()));
      }
      if (action != null) {
         Animation clip = AnimCache.animation(ReinhardAssets.ANIMATIONS,action.animation());
         if (clip != null) {
            float fade = (snapshot.actionId()==ReinhardAbility.CATCH.ordinal() || snapshot.actionId()==ReinhardAbility.REFLECT.ordinal())
               ? (float)Math.max(0,Math.min(1,(snapshot.actionLength()-actionTicks(player,snapshot,Minecraft.getInstance().getFrameTime()))/2)) : 1;
            java.util.Map<String,Animation.BoneAnimation> elbows=new java.util.HashMap<>();
            for(String name:new String[]{"right_forearm","left_forearm"})
               if(clip.boneAnimations().containsKey(name)) elbows.put(name,clip.boneAnimations().get(name));
            Animation forearms=new Animation(clip.name(),clip.lengthSeconds(),clip.loopType(),elbows,java.util.List.of());
            ReinhardLayers.overlay(forearms,geo,ReinhardPlayback.time(clip,action.seconds()),fade);
         }
      }
      // Idle has a relaxed elbow; action channels replace it. Arms keep the authored UVs.
      for (GeoBone bone : geo.topLevelBones()) bone.scaleX=1.13F;
      if (action == null) for (String name : new String[]{"right_forearm","left_forearm"}) {
         GeoBone elbow=geo.getBone(name);
         if (elbow!=null) elbow.rotX=(float)Math.toRadians(7);
      }
      ReinhardRecovery.arms(player,geo,recoveryKey(snapshot),Util.getNanos());
   }

   /** Moves the stack from the model frame to a top-level bone's pivot frame (same order as AnimRenderer). */
   static void boneFrame(PoseStack stack, GeoBone bone) {
      if (bone.parent != null) {
         boneFrame(stack,bone.parent);
         stack.translate(-bone.parent.pivotX/16F,-bone.parent.pivotY/16F,-bone.parent.pivotZ/16F);
      }
      localBoneFrame(stack,bone);
   }

   private static void localBoneFrame(PoseStack stack, GeoBone bone) {
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      stack.mulPose(Axis.YP.rotation(bone.rotY));
      stack.mulPose(Axis.XP.rotation(bone.rotX));
      stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
   }

   /** Preserve the shoulder-space item anchor while following the articulated elbow. */
   static void handFrame(PoseStack stack, BakedGeoModel body, GeoBone shoulder) {
      boneFrame(stack, shoulder);
      GeoBone elbow = body.getBone((shoulder.name.equals(ReinhardAssets.RIGHT_ARM) || shoulder.name.equals("armorRightArm")) ? "right_forearm" : "left_forearm");
      if (elbow == null) return;
      stack.translate(-shoulder.pivotX / 16.0F, -shoulder.pivotY / 16.0F, -shoulder.pivotZ / 16.0F);
      localBoneFrame(stack, elbow);
      stack.translate((shoulder.pivotX - elbow.pivotX) / 16.0F, (shoulder.pivotY - elbow.pivotY) / 16.0F,
         (shoulder.pivotZ - elbow.pivotZ) / 16.0F);
   }

   private static void heldItems(AbstractClientPlayer player, BakedGeoModel body, ReinhardPoses.ReidPhase reid, @Nullable ReinhardAbility action, ReinhardMode mode,PoseStack stack, MultiBufferSource buffers, int light) {
      for (HumanoidArm arm : HumanoidArm.values()) {
         ItemStack item = player.getItemInHand(player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
         GeoBone bone = body.getBone(arm == HumanoidArm.RIGHT ? ReinhardAssets.RIGHT_ARM : ReinhardAssets.LEFT_ARM);
         if (item.isEmpty() || bone == null || !ReinhardPoses.drawsHeldItem(arm, reid, action,mode)) {
            continue;
         }

         stack.pushPose();
         handFrame(stack, body, bone);
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
