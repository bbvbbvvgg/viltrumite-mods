package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerBoneMap;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.mark.Mark42Parts;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.client.mixin.ItemInHandRendererInvoker;
import dev.baranhan.viltrumitecore.client.reinhard.NameTagRules;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterExit;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterSuitUp;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Mark 48 on the player (rework spec §2–3, Thor rule §1.3.8–9). While the machine is the body (ACTIVE,
 * EXITING, and the suit-up once the torso closed) the vanilla render is cancelled and this class owns all of it:
 * the name tag, invisibility (translucent for teammates, outline only when glowing), the shadow radius, the
 * shader shadow pass (no emissive), the held items in the fists, the armour, the emissive mask and the thruster
 * flames. Poses come only from clips (HulkbusterClips); third person and the first-person arms read the same
 * per-player {@link ActionClock}. EXITING draws the opening shell at the frozen exit origin and facing
 * (HulkbusterExit) and lets vanilla draw Tony on his step-out path once the chest is open.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterRenderer {
   /** Sind px to blocks: 68 px stand 3.4 blocks (spec §2). */
   static final float SCALE = HulkbusterRules.MODEL_SCALE;
   /** Vanilla PlayerRenderer shadow radius, and the giant's (scaled by the hitbox width). */
   static final float SHADOW = 0.5F;
   static final float BIG_SHADOW = SHADOW * HulkbusterRules.WIDTH_SCALE;
   static final float TRANSLUCENT_ALPHA = 0.15F;
   static final float HEAD_YAW_LIMIT = 70.0F;
   static final float HEAD_PITCH_LIMIT = 45.0F;
   /** Item in a fist: scale of a vanilla held item in the giant's hand. */
   static final float ITEM_SCALE = 1.7F;
   private static final Map<AbstractClientPlayer, Clocks> CLOCKS = new WeakHashMap<>();

   private HulkbusterRenderer() {
   }

   /** One per player: the move clock (shared by FP and TP) and the motion-state clock. */
   static final class Clocks {
      final ActionClock move = new ActionClock();
      final ActionClock motion = new ActionClock();
   }

   static Clocks clocks(AbstractClientPlayer player) {
      return CLOCKS.computeIfAbsent(player, p -> new Clocks());
   }

   // ------------------------------------------------------------------ third person

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }

      HeroPublicSnapshot snapshot = IronManView.of(player);
      HulkbusterLayer.Phase phase = snapshot == null ? HulkbusterLayer.Phase.NONE : HulkbusterView.phase(snapshot);
      boolean shell = HulkbusterView.big(phase) || HulkbusterScene.bodyClosed(snapshot, phase);
      shadow(event.getRenderer(), shell && phase != HulkbusterLayer.Phase.EXITING);
      if (!shell) {
         if (phase == HulkbusterLayer.Phase.NONE) {
            CLOCKS.remove(player);
         }

         return;
      }

      BakedGeoModel body = AnimCache.model(HulkbusterAssets.BODY);
      if (body == null) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      float partialTick = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      if (phase == HulkbusterLayer.Phase.EXITING) {
         // The empty shell is a world object from the exit start on (HulkbusterShells: same model, frozen origin and
         // facing, it carries on after the exit). The player renderer only places Tony: sealed in until the chest is
         // open, then on HulkbusterExit.tony. A world-space offset before vanilla's body rotation, so it follows the
         // frozen facing and not world +Z, whatever the network position lags.
         double exitTick = Math.min(HulkbusterLayer.EXIT_TICKS, snapshot.actionElapsed() + partialTick);
         if (exitTick < HulkbusterExit.TONY_SHOWN) {
            event.setCanceled(true);
            return;
         }

         Vec3 origin = snapshot.actionTarget() != null ? snapshot.actionTarget() : player.getPosition(partialTick);
         Vec3 renderPos = player.getPosition(partialTick);
         Vec3 tony = tonyAt(snapshot, origin, exitTick);
         event.getPoseStack().translate(tony.x - renderPos.x, tony.y - renderPos.y, tony.z - renderPos.z);
         return;
      }

      // The machine carries the name tag; vanilla draws nothing.
      nameTag(player, event.getRenderer(), stack, buffers, light, partialTick);
      event.setCanceled(true);
      ReinhardPoses.Passes passes = ReinhardPoses.passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player),
         mc.shouldEntityAppearGlowing(player));
      if (passes.body() || passes.outline()) {
         poseBody(body, player, snapshot, phase, partialTick);
         float flames = HulkbusterScene.bodyClosed(snapshot, phase) ? 0.0F : flames(snapshot);
         int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
         stack.pushPose();
         stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)));
         if (player.deathTime > 0) {
            float dying = Math.min(1.0F, Mth.sqrt((player.deathTime + partialTick - 1.0F) / 20.0F * 1.6F));
            stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
         }

         stack.scale(SCALE, SCALE, SCALE);
         // In the suit-up the reactor warms with the plates and flashes at the end (HulkbusterSuitUp.reactor).
         float glow = HulkbusterScene.bodyClosed(snapshot, phase) ? HulkbusterSuitUp.reactor(HulkbusterScene.assemble(snapshot) + partialTick)
            : glow(snapshot, player.tickCount + partialTick);
         drawModel(body, stack, buffers, light, overlay, passes, glow, flames, player.tickCount, HulkbusterView.damageStage(snapshot));
         if (passes.extras()) {
            heldItems(player, body, stack, buffers, light);
         }

         stack.popPose();
      }
   }

   /** Where vanilla Tony is drawn during the exit (feet, world). */
   static Vec3 tonyAt(HeroPublicSnapshot snapshot, Vec3 origin, double exitTick) {
      int word = HulkbusterView.sceneWord(snapshot);
      Vec3 spot = HulkbusterExit.spot(origin, HulkbusterWire.sceneYaw(word), HulkbusterWire.exitStepOut(word));
      return HulkbusterExit.tony(origin, spot, exitTick);
   }

   /** The armour, the emissive mask and the flames of a posed model (shared with the suit-up scene). */
   static void drawModel(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, int light, int overlay, ReinhardPoses.Passes passes, float glow,
      float flames, int tick, int damageStage) {
      hideFlames(model, true);
      hideDamage(model, damageStage);
      if (passes.body()) {
         RenderType type = passes.translucent() ? RenderType.entityTranslucent(HulkbusterAssets.TEXTURE) : RenderType.entityCutoutNoCull(HulkbusterAssets.TEXTURE);
         AnimRenderer.render(model, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F, null);
      }

      boolean shadowPass = ShaderCompat.isShadowPass();
      if (passes.outline() && !shadowPass) {
         // Vanilla draws an invisible glowing entity with RenderType.outline only.
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.outline(HulkbusterAssets.TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }

      if (!passes.extras() || shadowPass) {
         return;
      }

      // Emissive passes cast no shadow (animation-system §7) and never show on an invisible machine.
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(HulkbusterAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         glow, glow, glow, 1.0F, null);
      if (flames > 0.01F) {
         hideFlames(model, false);
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(Mark42Parts.fireTexture(tick / 2))), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, flames, flames, flames, 1.0F, null);
         hideFlames(model, true);
         hideDamage(model, damageStage);
      }
   }

   /** Armour passes hide the flame bones; the flame pass hides everything else. */
   static void hideFlames(BakedGeoModel model, boolean armour) {
      for (GeoBone bone : model.allBones()) {
         boolean flame = bone.name.startsWith(HulkbusterAssets.FLAME_PREFIX);
         bone.hidden = armour ? flame : !flame;
      }
   }

   /** Damage stages (spec §2): plates fall off by bones (the bone and its children). */
   static void hideDamage(BakedGeoModel model, int stage) {
      for (int s = 0; s < Math.min(stage, HulkbusterAssets.DAMAGE_PLATES.length); s++) {
         for (String name : HulkbusterAssets.DAMAGE_PLATES[s]) {
            hideTree(model.getBone(name));
         }
      }
   }

   static void hideTree(@Nullable GeoBone bone) {
      if (bone == null) {
         return;
      }

      bone.hidden = true;
      for (GeoBone child : bone.children) {
         hideTree(child);
      }
   }

   /** Poses the body for this frame: the clips (or the scene's assembly clip), then the head follows the look. */
   static void poseBody(BakedGeoModel body, AbstractClientPlayer player, HeroPublicSnapshot snapshot, HulkbusterLayer.Phase phase, float partialTick) {
      if (HulkbusterScene.bodyClosed(snapshot, phase)) {
         HulkbusterScene.poseFrame(body, snapshot, partialTick);
         return;
      }

      if (phase == HulkbusterLayer.Phase.EXITING) {
         Animation exit = AnimCache.animation(HulkbusterAssets.ANIMATIONS, "exit");
         if (exit != null) {
            AnimationController.seek(exit, body, Math.min(exit.lengthSeconds(), (snapshot.actionElapsed() + partialTick) / 20.0));
         } else {
            body.resetBones();
         }

         return;
      }

      applyClips(body, pose(player, snapshot, partialTick), false);
      applyHead(body, player, partialTick);
   }

   // ------------------------------------------------------------------ clips

   /** This frame's clip layers for a player (the per-player clocks; FP and TP share them). */
   static HulkbusterClips.Pose pose(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick) {
      Clocks clocks = clocks(player);
      double clientTicks = player.tickCount + partialTick;
      HulkMove move = HulkbusterView.move(snapshot);
      MovePhase phase = HulkbusterView.movePhase(snapshot);
      double moveTicks = clocks.move.sample(move == null ? -1 : snapshot.actionId(), snapshot.extraCooldowns().length > HulkbusterWire.MOVE_VARIANT
         ? snapshot.extraCooldowns()[HulkbusterWire.MOVE_VARIANT] : 0, snapshot.actionElapsed(), snapshot.actionLength(), clientTicks);
      HulkbusterMotion.State state = HulkbusterView.motion(snapshot);
      double motionTicks = clocks.motion.sample(state.ordinal(), HulkbusterView.direction(snapshot), HulkbusterView.motionTicks(snapshot), 0, clientTicks);
      return HulkbusterClips.select(new HulkbusterClips.Input(move, phase, HulkbusterView.step(snapshot), moveTicks, state, motionTicks,
         HulkbusterView.direction(snapshot), AnimRenderer.time(partialTick) / 20.0, player.walkAnimation.speed(partialTick),
         player.walkAnimation.position(partialTick), player.isSprinting()));
   }

   /**
    * Idle, gait, motion, move. {@code firstPerson}: the camera arms keep the elbows, hands, piston and fingers of
    * the clips but not the shoulder swing (it would throw the arm into the lens); the thrust is added separately.
    */
   static void applyClips(BakedGeoModel model, HulkbusterClips.Pose pose, boolean firstPerson) {
      applyClips(model, pose, firstPerson, HulkbusterRenderer::clip);
   }

   /** Same, with the clip lookup given (the first-person coverage test reads the parsed clip file). */
   static void applyClips(BakedGeoModel model, HulkbusterClips.Pose pose, boolean firstPerson, java.util.function.Function<String, Animation> clips) {
      Animation idle = clips.apply(pose.idle().clip());
      if (idle != null) {
         AnimationController.seek(idle, model, pose.idle().seconds());
      } else {
         model.resetBones();
      }

      if (!firstPerson) {
         layer(model, pose.gait(), clips);
      }

      layer(model, pose.motion(), clips);
      layer(model, pose.move(), clips);
      if (firstPerson) {
         for (String name : new String[]{"shoulder_r", "shoulder_l", "upper_arm_r", "upper_arm_l"}) {
            GeoBone bone = model.getBone(name);
            if (bone != null) {
               bone.resetToDefault();
            }
         }
      }
   }

   private static void layer(BakedGeoModel model, @Nullable HulkbusterClips.Layer layer, java.util.function.Function<String, Animation> clips) {
      Animation animation = layer == null ? null : clips.apply(layer.clip());
      if (animation != null) {
         AnimationController.overlay(animation, model, layer.seconds(), layer.weight());
      }
   }

   @Nullable
   static Animation clip(String name) {
      return AnimCache.animation(HulkbusterAssets.ANIMATIONS, name);
   }

   /** The head follows the look (yaw relative to the body, pitch), limited; BakedGeoModel turns are negated. */
   static void applyHead(BakedGeoModel body, AbstractClientPlayer player, float partialTick) {
      GeoBone head = body.getBone(HulkbusterAssets.HEAD);
      if (head == null) {
         return;
      }

      float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)),
         -HEAD_YAW_LIMIT, HEAD_YAW_LIMIT);
      float pitch = Mth.clamp(Mth.lerp(partialTick, player.xRotO, player.getXRot()), -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT);
      head.rotY += (float)Math.toRadians(-yaw);
      head.rotX += (float)Math.toRadians(-pitch);
   }

   /** Emissive strength: steady, the reactor breathing with the damage stage flicker (spec §2, §9). */
   static float glow(HeroPublicSnapshot snapshot, double ticks) {
      int stage = HulkbusterView.damageStage(snapshot);
      if (stage == 0) {
         return 1.0F;
      }

      double flicker = Math.sin(ticks * (stage == 1 ? 0.7 : 1.9)) * Math.sin(ticks * 0.31);
      return (float)Mth.clamp(0.85 + (stage == 1 ? 0.12 : 0.35) * flicker, 0.35, 1.0);
   }

   /** Thruster flame strength from the motion state and the move (dash, jet, hover, dive, the ultimate). */
   static float flames(HeroPublicSnapshot snapshot) {
      HulkMove move = HulkbusterView.move(snapshot);
      if (move == HulkMove.JET_RAM || move == HulkMove.HERO_LANDING && HulkbusterView.movePhase(snapshot) == MovePhase.INTRO) {
         return 1.0F;
      }

      int ticks = HulkbusterView.motionTicks(snapshot);
      return switch (HulkbusterView.motion(snapshot)) {
         case JET_CHARGE -> Math.min(0.6F, 0.15F + ticks / (float)HulkbusterRules.JET_CHARGE_MAX * 0.45F);
         case JET_RISE, DIVE -> 1.0F;
         case HOVER -> 0.8F;
         case DASH -> 0.9F;
         case DASH_WINDUP -> 0.4F;
         default -> 0.0F;
      };
   }

   // ------------------------------------------------------------------ vanilla duties

   /** The giant's shadow: the player renderer is shared, so the radius is set on every player's render. */
   private static void shadow(PlayerRenderer renderer, boolean big) {
      ((EntityRendererNameTagInvoker)renderer).viltrumitecore$setShadowRadius(big ? BIG_SHADOW : SHADOW);
   }

   /** Forge's name tag step of EntityRenderer.render (skipped by the cancel), through vanilla renderNameTag. */
   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partialTick) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker)renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partialTick);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (NameTagRules.show(result == Event.Result.DENY, result == Event.Result.ALLOW, invoker.viltrumitecore$shouldShowName(player))) {
         Component content = event.getContent();
         invoker.viltrumitecore$renderNameTag(player, content, stack, buffers, light);
      }
   }

   /** Held items in the fists (right fist = right hand bone fist_r, left = the grabbing hand). */
   private static void heldItems(AbstractClientPlayer player, BakedGeoModel body, PoseStack stack, MultiBufferSource buffers, int light) {
      for (HumanoidArm arm : HumanoidArm.values()) {
         ItemStack item = player.getItemInHand(player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
         GeoBone bone = body.getBone(arm == HumanoidArm.RIGHT ? HulkbusterAssets.FIST_R : HulkbusterAssets.HAND_L);
         if (item.isEmpty() || bone == null) {
            continue;
         }

         boolean left = arm == HumanoidArm.LEFT;
         stack.pushPose();
         boneFrame(stack, bone);
         // Into the palm: down the fist, the blade forward (Bedrock axes, the model is in Sind px).
         stack.translate(0.0F, -4.0F / 16.0F, -1.5F / 16.0F);
         stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(180.0F));
         stack.scale(ITEM_SCALE / SCALE, ITEM_SCALE / SCALE, ITEM_SCALE / SCALE);
         Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, item,
            left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, stack, buffers, light);
         stack.popPose();
      }
   }

   /** Moves the stack from the model frame to a bone's pivot frame (same order as AnimRenderer). */
   static void boneFrame(PoseStack stack, GeoBone bone) {
      if (bone.parent != null) {
         boneFrame(stack, bone.parent);
         stack.translate(-bone.parent.pivotX / 16.0F, -bone.parent.pivotY / 16.0F, -bone.parent.pivotZ / 16.0F);
      }

      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      stack.mulPose(Axis.YP.rotation(bone.rotY));
      stack.mulPose(Axis.XP.rotation(bone.rotX));
      stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
   }

   // ------------------------------------------------------------------ first person

   /**
    * First person inside the machine: both giant arms replace the vanilla hands (items stay in their slots and
    * are drawn in the fists in third person). Each hand's event draws its arm through vanilla renderPlayerArm,
    * which fires {@link #onRenderArm}.
    */
   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      AbstractClientPlayer player = mc.player;
      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null || !HulkbusterView.inside(snapshot)) {
         return;
      }

      event.setCanceled(true);
      if (player.isInvisible()) {
         return;
      }

      HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
      PoseStack stack = event.getPoseStack();
      stack.pushPose();
      HulkbusterFirstPerson.place(stack, arm == HumanoidArm.RIGHT);
      ((ItemInHandRendererInvoker)mc.getEntityRenderDispatcher().getItemInHandRenderer())
         .viltrumite$renderPlayerArm(stack, event.getMultiBufferSource(), event.getPackedLight(), 0.0F, 0.0F, arm);
      stack.popPose();
   }

   /**
    * Spec 12: lift arms inside the machine. A scene that poses the arms in the world (Thor's Mjolnir lift) gets the
    * Mark 48 first-person arms on the posed vanilla arm parts instead of the pilot's skin. False outside the machine:
    * the caller then uses the Iron Man hand texture.
    */
   public static boolean drawLiftArms(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, PoseStack stack, MultiBufferSource buffers, int light) {
      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null || !HulkbusterView.inside(snapshot)) {
         return false;
      }

      BakedGeoModel geo = AnimCache.model(HulkbusterAssets.FP_ARMS);
      if (geo == null) {
         return false;
      }

      // Rest clips only: the lift scene owns the arm pose, no combat push.
      geo.resetBones();
      hideFlames(geo, true);
      for (boolean right : new boolean[]{true, false}) {
         GeoBone root = geo.getBone(right ? HulkbusterAssets.SHOULDER_R : HulkbusterAssets.SHOULDER_L);
         if (root == null) {
            continue;
         }

         stack.pushPose();
         PlayerGeoLayer.partFrame(stack, right ? model.rightArm : model.leftArm, right ? PlayerBoneMap.Part.RIGHT_ARM : PlayerBoneMap.Part.LEFT_ARM);
         AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.entityCutoutNoCull(HulkbusterAssets.TEXTURE)), light,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
         if (!ShaderCompat.isShadowPass()) {
            float glow = glow(snapshot, player.tickCount);
            AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.eyes(HulkbusterAssets.GLOW)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F);
         }

         stack.popPose();
      }

      return true;
   }

   @SubscribeEvent
   public static void onRenderArm(RenderArmEvent event) {
      AbstractClientPlayer player = event.getPlayer();
      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null || !HulkbusterView.inside(snapshot)) {
         return;
      }

      event.setCanceled(true);
      BakedGeoModel geo = AnimCache.model(HulkbusterAssets.FP_ARMS);
      if (geo == null) {
         return;
      }

      boolean right = event.getArm() == HumanoidArm.RIGHT;
      PlayerRenderer renderer = (PlayerRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      ModelPart arm = right ? model.rightArm : model.leftArm;
      float oldX = arm.x, oldY = arm.y, oldZ = arm.z, oldXRot = arm.xRot, oldYRot = arm.yRot, oldZRot = arm.zRot;
      // RenderArmEvent fires before renderHand's setupAnim: set the camera rest pose, never read the stale one.
      HulkbusterFirstPerson.Rest rest = HulkbusterFirstPerson.rest(right);
      arm.x = rest.x();
      arm.y = rest.y();
      arm.z = rest.z();
      arm.xRot = rest.xRot();
      arm.yRot = rest.yRot();
      arm.zRot = rest.zRot();
      float partialTick = Minecraft.getInstance().getFrameTime();
      HulkbusterClips.Pose pose = pose(player, snapshot, partialTick);
      applyClips(geo, pose, true);
      double moveTicks = clocks(player).move.sample(HulkbusterView.move(snapshot) == null ? -1 : snapshot.actionId(),
         HulkbusterView.extra(snapshot, HulkbusterWire.MOVE_VARIANT), snapshot.actionElapsed(), snapshot.actionLength(), player.tickCount + partialTick);
      float push = HulkbusterClips.firstPersonPush(HulkbusterView.move(snapshot), HulkbusterView.movePhase(snapshot), HulkbusterView.step(snapshot), moveTicks, right);
      if (push != 0.0F) {
         // Along the arm (model +Y after the arm's rotation): forward into the screen.
         Vector3f along = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(new Vector3f(0.0F, push, 0.0F));
         arm.x += along.x;
         arm.y += along.y;
         arm.z += along.z;
      }

      GeoBone root = geo.getBone(right ? HulkbusterAssets.SHOULDER_R : HulkbusterAssets.SHOULDER_L);
      if (root != null) {
         PoseStack stack = event.getPoseStack();
         MultiBufferSource buffers = event.getMultiBufferSource();
         stack.pushPose();
         PlayerGeoLayer.partFrame(stack, arm, right ? PlayerBoneMap.Part.RIGHT_ARM : PlayerBoneMap.Part.LEFT_ARM);
         hideFlames(geo, true);
         AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.entityCutoutNoCull(HulkbusterAssets.TEXTURE)), event.getPackedLight(),
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
         if (!ShaderCompat.isShadowPass()) {
            float glow = glow(snapshot, player.tickCount + partialTick);
            AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.eyes(HulkbusterAssets.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               glow, glow, glow, 1.0F);
            float flames = flames(snapshot);
            if (flames > 0.01F) {
               hideFlames(geo, false);
               AnimRenderer.renderBone(root, stack, buffers.getBuffer(RenderType.eyes(Mark42Parts.fireTexture(player.tickCount / 2))), LightTexture.FULL_BRIGHT,
                  OverlayTexture.NO_OVERLAY, flames, flames, flames, 1.0F);
               hideFlames(geo, true);
            }
         }

         stack.popPose();
      }

      arm.x = oldX;
      arm.y = oldY;
      arm.z = oldZ;
      arm.xRot = oldXRot;
      arm.yRot = oldYRot;
      arm.zRot = oldZRot;
   }
}
