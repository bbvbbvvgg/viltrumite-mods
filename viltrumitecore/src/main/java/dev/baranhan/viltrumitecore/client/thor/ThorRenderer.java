package dev.baranhan.viltrumitecore.client.thor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerBoneMap;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.client.mixin.LivingEntityRendererInvoker;
import dev.baranhan.viltrumitecore.client.mixin.PlayerRendererInvoker;
import dev.baranhan.viltrumitecore.client.reinhard.HeldItemAnchor;
import dev.baranhan.viltrumitecore.client.reinhard.NameTagRules;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Thor rework R1 (spec §1–§2): Thor's own geo body replaces the vanilla player in third person
 * and owns everything vanilla drew there: the body (cutout / invisible-translucent / glowing
 * outline, shadow pass), Mjölnir in the hand or on the belt (cutout + rune glow), held items at
 * the fists, the name tag. The vanilla setupRotations is invoked (sleep, death, elytra, swim and
 * the flight tilt injected into it); the vanilla model is still posed every frame and bridged
 * onto the geo bones (ThorBodyPose). First person: the new body's camera arms (RenderArmEvent)
 * with Mjölnir on their grip, and the lift (anyone on a planted hammer) drawn in view space from
 * the same rig as third person (RenderHandEvent). Client only.
 * <p>Not drawn on Thor's body (like Reinhard and the Hulkbuster): vanilla armor / elytra / cape /
 * stuck-arrow layers; items still work.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ThorRenderer {
   static final float TRANSLUCENT_ALPHA = 0.15F;
   /** Visual height (blocks) of thor.geo.json; inventory previews shrink it to a player's 1.875. */
   static final float BODY_HEIGHT = 3.2F;
   static final float GUI_SCALE = 1.875F / BODY_HEIGHT;
   /** Held items scale with the fist (vanilla items in a 3.2-block hand). */
   static final float ITEM_SCALE = 1.5F;
   /** Swimming: vanilla shifts its 1.875-block body by 1 along the spine; Thor's is longer. */
   static final float SWIM_EXTRA_SHIFT = -(BODY_HEIGHT - 1.875F) * 0.5F;
   /** PlayerRenderer.getRenderOffset while crouching. */
   static final double CROUCH_OFFSET = -0.125;
   /** First-person forearm rest bend (degrees). */
   static final float FP_ELBOW = 6.0F;

   private ThorRenderer() {
   }

   // ------------------------------------------------------------------ third person

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }

      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         return;
      }

      BakedGeoModel body = AnimCache.model(ThorAssets.BODY);
      if (body == null) {
         return;
      }

      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      PlayerRenderer renderer = event.getRenderer();
      float partialTick = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      nameTag(player, renderer, stack, buffers, light, partialTick);
      Passes passes = passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player), mc.shouldEntityAppearGlowing(player));
      if (!passes.body() && !passes.outline()) {
         return;
      }

      boolean gui = gui();
      ThorLift.Frame lift = gui ? null : ThorLift.frame(player, partialTick, Util.getNanos(), true);
      float[] yaws = vanillaYaws(player, partialTick);
      float bob = ((LivingEntityRendererInvoker)renderer).viltrumitecore$getBob(player, partialTick);
      pose(body, player, renderer, partialTick, yaws, bob, lift);
      stack.pushPose();
      if (gui) {
         stack.scale(GUI_SCALE, GUI_SCALE, GUI_SCALE);
      }

      if (lift != null) {
         Vec3 shift = lift.root().subtract(player.getPosition(partialTick));
         stack.translate(shift.x, shift.y + crouchCancel(player, lift), shift.z);
      }

      if (player.hasPose(Pose.SLEEPING)) {
         Direction bed = player.getBedOrientation();
         if (bed != null) {
            float shift = player.getEyeHeight(Pose.STANDING) - 0.1F;
            stack.translate(-bed.getStepX() * shift, 0.0F, -bed.getStepZ() * shift);
         }
      }

      ((LivingEntityRendererInvoker)renderer).viltrumitecore$setupRotations(player, stack, bob, lift != null ? lift.yaw() : yaws[0], partialTick);
      if (player.isVisuallySwimming()) {
         stack.translate(0.0F, SWIM_EXTRA_SHIFT, 0.0F);
      }

      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      boolean shadow = ShaderCompat.isShadowPass();
      if (passes.body()) {
         RenderType type = passes.translucent() ? RenderType.entityTranslucent(ThorAssets.TEXTURE) : RenderType.entityCutoutNoCull(ThorAssets.TEXTURE);
         AnimRenderer.render(body, stack, null, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F,
            passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F, null);
      }

      if (passes.outline() && !shadow) {
         // Vanilla draws an invisible glowing entity with RenderType.outline only.
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(ThorAssets.TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }

      if (passes.extras()) {
         MjolnirPlace place = ThorView.place(snapshot, player);
         if (place == MjolnirPlace.HAND) {
            drawHeldMjolnir(player, partialTick, stack, locator(body, ThorAssets.MJOLNIR_GRIP), buffers, light, overlay);
         } else if (place == MjolnirPlace.BELT) {
            drawMjolnir(stack, locator(body, ThorAssets.MJOLNIR_BELT_BONE), ThorAssets.MJOLNIR_BELT, buffers, light, overlay);
         }

         heldItems(player, body, place == MjolnirPlace.HAND, stack, buffers, light);
      }

      stack.popPose();
   }

   /**
    * The whole third-person pose: vanilla model setup (as LivingEntityRenderer.render does it, so
    * every model mixin runs), bridged onto the geo, base clips, then the lift.
    */
   static void pose(BakedGeoModel body, AbstractClientPlayer player, PlayerRenderer renderer, float partialTick, float[] yaws, float bob,
      @Nullable ThorLift.Frame lift) {
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      ((PlayerRendererInvoker)renderer).viltrumitecore$setModelProperties(player);
      model.attackTime = player.getAttackAnim(partialTick);
      boolean sitting = player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit();
      model.riding = sitting;
      model.young = player.isBaby();
      float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
      float netHeadYaw = yaws[1];
      if (LivingEntityRenderer.isEntityUpsideDown(player)) {
         pitch = -pitch;
         netHeadYaw = -netHeadYaw;
      }

      float speed = 0.0F, position = 0.0F;
      if (!sitting && player.isAlive()) {
         speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
         position = player.walkAnimation.position(partialTick);
      }

      model.prepareMobModel(player, position, speed, partialTick);
      model.setupAnim(player, position, speed, bob, netHeadYaw, pitch);
      ThorBodyPose.bridge(body, ThorBodyPose.capture(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg),
         lift == null || lift.weight() < 0.5F);
      ThorBodyPose.baseClips(body, player.isCrouching() ? 1.0F : 0.0F, sitting ? 1.0F : 0.0F);
      applyLift(body, player, lift);
   }

   /** Lift squat + both arms on the handle (same in third and first person). */
   static void applyLift(BakedGeoModel body, AbstractClientPlayer player, @Nullable ThorLift.Frame lift) {
      if (lift == null) {
         return;
      }

      ThorLift.poseThor(body, lift.grip(), renderRoot(player, lift), lift.yaw(), lift.seconds(), lift.weight());
   }

   /**
    * Where the model's origin really is: the lift root plus PlayerRenderer's crouch offset. Thor's
    * own body cancels that offset as the lift blends in ({@link #crouchCancel}): its squat clip
    * stands on the ground itself.
    */
   static Vec3 renderRoot(AbstractClientPlayer player, ThorLift.Frame lift) {
      if (!player.isCrouching()) {
         return lift.root();
      }

      double offset = lift.thorBody() ? CROUCH_OFFSET * (1.0F - lift.weight()) : CROUCH_OFFSET;
      return lift.root().add(0.0, offset, 0.0);
   }

   /** Third person: the part of the dispatcher's crouch offset Thor's lift takes back (see {@link #renderRoot}). */
   static double crouchCancel(AbstractClientPlayer player, @Nullable ThorLift.Frame lift) {
      return lift != null && lift.thorBody() && player.isCrouching() ? -CROUCH_OFFSET * lift.weight() : 0.0;
   }

   /** LivingEntityRenderer.render's body yaw and net head yaw (with the riding clamp). */
   static float[] vanillaYaws(LivingEntity entity, float partialTick) {
      float body = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
      float head = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
      float net = head - body;
      boolean sitting = entity.isPassenger() && entity.getVehicle() != null && entity.getVehicle().shouldRiderSit();
      if (sitting && entity.getVehicle() instanceof LivingEntity vehicle) {
         body = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
         float wrapped = Mth.clamp(Mth.wrapDegrees(head - body), -85.0F, 85.0F);
         body = head - wrapped;
         if (wrapped * wrapped > 2500.0F) {
            body += wrapped * 0.2F;
         }

         net = head - body;
      }

      return new float[]{body, net};
   }

   /** A locator bone's frame (model space, origin on its pivot). */
   static Matrix4f locator(BakedGeoModel body, String bone) {
      GeoBone b = body.getBone(bone);
      return b == null ? new Matrix4f() : GeoPose.pivotFrame(b);
   }

   private static final String SPIN_BONE = "MjolnirBody";

   /**
    * Mjölnir in the hand (both views), R4 on the R1 body: in flight only the MjolnirBody bone whirls
    * about the fist (MjolnirSpin, FX id {@code thor:hammer_spin}), followed by translucent ghost
    * copies as motion blur; the arm pose is never touched. The solid draw records the hammer
    * anchors (ThorAnchors) on the real posed frame.
    */
   static void drawHeldMjolnir(AbstractClientPlayer player, float partialTick, PoseStack stack, Matrix4f at, MultiBufferSource buffers, int light,
         int overlay) {
      FlightState flight = player instanceof ViltrumiteFlightPlayer flyer ? flyer.getFlightState() : FlightState.NONE;
      MjolnirSpin.State spin = MjolnirSpin.update(player, player.tickCount + (double)partialTick, MjolnirSpin.targetRps(flight, true));
      if (!spin.spinning()) {
         drawMjolnir(stack, at, ThorAssets.MJOLNIR_HAND, buffers, light, overlay, null, 1.0F, player);
         return;
      }

      float angle = spin.angle;
      float rps = spin.rps;
      drawMjolnir(stack, at, ThorAssets.MJOLNIR_HAND, buffers, light, overlay, angle, 1.0F, player);
      for (int i = 1; i <= MjolnirSpin.GHOSTS; i++) {
         float alpha = MjolnirSpin.ghostAlpha(rps, i);
         if (alpha >= 0.02F) {
            drawMjolnir(stack, at, ThorAssets.MJOLNIR_HAND, buffers, light, overlay, MjolnirSpin.ghostAngle(angle, rps, i), alpha, null);
         }
      }
   }

   private static void spinBody(BakedGeoModel geo, float angle) {
      GeoBone body = geo.getBone(SPIN_BONE);
      if (body == null) {
         return;
      }

      float[] pose = MjolnirSpin.bodyPose(angle);
      body.rotX = body.initRotX + pose[0];
      body.posX = pose[1];
      body.posY = pose[2];
      body.posZ = pose[3];
   }

   /** The unchanged Mjölnir model ({@code mjolnir} subtree of its hand / belt geo) with its grip on {@code at}. */
   static void drawMjolnir(PoseStack stack, Matrix4f at, ResourceLocation geo, MultiBufferSource buffers, int light, int overlay) {
      drawMjolnir(stack, at, geo, buffers, light, overlay, null, 1.0F, null);
   }

   /**
    * As above; {@code spinAngle} turns MjolnirBody (R4 spin), {@code alpha} below 1 draws a lit
    * translucent ghost (never in the shadow pass), a non-null {@code anchorOf} records its anchors.
    */
   static void drawMjolnir(PoseStack stack, Matrix4f at, ResourceLocation geo, MultiBufferSource buffers, int light, int overlay,
         @Nullable Float spinAngle, float alpha, @Nullable AbstractClientPlayer anchorOf) {
      BakedGeoModel model = AnimCache.model(geo);
      GeoBone hammer = model == null ? null : model.getBone(ThorAssets.MJOLNIR_BONE);
      if (hammer == null) {
         return;
      }

      boolean shadow = ShaderCompat.isShadowPass();
      if (alpha < 1.0F && shadow) {
         return;
      }

      model.resetBones();
      if (spinAngle != null) {
         spinBody(model, spinAngle);
      }

      stack.pushPose();
      stack.mulPoseMatrix(at);
      stack.translate(-hammer.pivotX / 16.0F, -hammer.pivotY / 16.0F, -hammer.pivotZ / 16.0F);
      if (anchorOf != null) {
         ThorAnchors.capture(anchorOf, stack, hammer);
      }

      if (alpha < 1.0F) {
         AnimRenderer.renderBone(hammer, stack, buffers.getBuffer(RenderType.entityTranslucent(ThorAssets.MJOLNIR_TEXTURE)), light, overlay,
            1.0F, 1.0F, 1.0F, alpha);
         AnimRenderer.renderBone(hammer, stack, buffers.getBuffer(RenderType.eyes(ThorAssets.MJOLNIR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, alpha);
         stack.popPose();
         return;
      }

      AnimRenderer.renderBone(hammer, stack, buffers.getBuffer(RenderType.entityCutoutNoCull(ThorAssets.MJOLNIR_TEXTURE)), light, overlay,
         1.0F, 1.0F, 1.0F, 1.0F);
      if (!shadow) {
         AnimRenderer.renderBone(hammer, stack, buffers.getBuffer(RenderType.eyes(ThorAssets.MJOLNIR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
      }

      stack.popPose();
   }

   /**
    * Items in the physical hands at the fist locators (vanilla ItemInHandLayer's offsets from the
    * vanilla hand point). Mjölnir in the right hand hides that hand's item render (not the slot).
    */
   private static void heldItems(AbstractClientPlayer player, BakedGeoModel body, boolean hammerInRight, PoseStack stack, MultiBufferSource buffers,
      int light) {
      for (HumanoidArm arm : HumanoidArm.values()) {
         if (arm == HumanoidArm.RIGHT && hammerInRight) {
            continue;
         }

         ItemStack item = player.getItemInHand(player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
         if (item.isEmpty()) {
            continue;
         }

         boolean left = arm == HumanoidArm.LEFT;
         stack.pushPose();
         stack.mulPoseMatrix(itemFrame(body, arm));
         Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, item,
            left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, stack, buffers, light);
         stack.popPose();
      }
   }

   /** Model-space frame of a held item: the fist locator, scaled, then vanilla's offsets from its hand point (1 px in, 9 px down the arm). */
   static Matrix4f itemFrame(BakedGeoModel body, HumanoidArm arm) {
      float side = arm == HumanoidArm.LEFT ? -1.0F : 1.0F;
      return locator(body, arm == HumanoidArm.LEFT ? ThorAssets.LEFT_GRIP : ThorAssets.MJOLNIR_GRIP).scale(ITEM_SCALE)
         .translate(-side / 16.0F, 9.0F / 16.0F, 0.0F).mul(HeldItemAnchor.local(arm));
   }

   /** Render passes, as Reinhard's: visible = everything; invisible but seen = translucent body; else only a glowing outline. */
   record Passes(boolean body, boolean translucent, boolean outline, boolean extras) {
   }

   static Passes passes(boolean invisible, boolean visibleToViewer, boolean glowing) {
      if (!invisible) {
         return new Passes(true, false, false, true);
      }

      return visibleToViewer ? new Passes(true, true, false, false) : new Passes(false, false, glowing, false);
   }

   /** Inventory / GUI previews use an orthographic projection. */
   private static boolean gui() {
      return RenderSystem.getProjectionMatrix().m33() > 0.5F && !ShaderCompat.isShadowPass();
   }

   /** Forge's name tag step of EntityRenderer.render (skipped by the cancel), through vanilla renderNameTag. */
   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partialTick) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker)renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partialTick);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (NameTagRules.show(result == Event.Result.DENY, result == Event.Result.ALLOW, invoker.viltrumitecore$shouldShowName(player))) {
         invoker.viltrumitecore$renderNameTag(player, event.getContent(), stack, buffers, light);
      }
   }

   // ------------------------------------------------------------------ vanilla bodies on the handle

   /** Per render: whether {@link #onVanillaLiftPre} pushed a pose that {@link #onVanillaLiftPost} must pop. */
   private static final java.util.Map<AbstractClientPlayer, Boolean> PUSHED = new java.util.WeakHashMap<>();

   /**
    * A vanilla-bodied lifter squares up to the handle: the whole render turns to face it and slides
    * to the grip distance (visual only). Runs last, so a render another hero cancelled is left alone.
    */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onVanillaLiftPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player) || ThorView.of(player) != null || gui()) {
         return;
      }

      float partialTick = event.getPartialTick();
      ThorLift.Frame lift = ThorLift.frame(player, partialTick, Util.getNanos(), false);
      if (lift == null) {
         return;
      }

      PoseStack stack = event.getPoseStack();
      stack.pushPose();
      Vec3 shift = lift.root().subtract(player.getPosition(partialTick));
      stack.translate(shift.x, shift.y, shift.z);
      float body = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
      stack.mulPose(Axis.YP.rotationDegrees(body - lift.yaw()));
      PUSHED.put(player, Boolean.TRUE);
   }

   @SubscribeEvent
   public static void onVanillaLiftPost(RenderPlayerEvent.Post event) {
      if (event.getEntity() instanceof AbstractClientPlayer player && PUSHED.remove(player) != null) {
         event.getPoseStack().popPose();
      }
   }

   /** ThorPoser (setupAnim tail): a vanilla lifter's bend and arms on R3's palm anchors. */
   static boolean poseVanillaLift(PlayerModel<?> model, AbstractClientPlayer player) {
      if (ThorView.of(player) != null) {
         return false;
      }

      float partialTick = Minecraft.getInstance().getFrameTime();
      ThorLift.Frame lift = ThorLift.frame(player, partialTick, Util.getNanos(), false);
      if (lift == null) {
         return false;
      }

      poseVanilla(model, player, lift);
      return true;
   }

   private static void poseVanilla(PlayerModel<?> model, AbstractClientPlayer player, ThorLift.Frame lift) {
      Vec3 root = renderRoot(player, lift);
      float tremble = Mth.sin((float)lift.seconds() * 46.0F) * 0.035F * lift.weight();
      ThorLift.poseVanilla(model, ThorLift.toVanillaModel(lift.grip().upperPalm(), root, lift.yaw()),
         ThorLift.toVanillaModel(lift.grip().lowerPalm(), root, lift.yaw()), lift.weight(), tremble);
   }

   // ------------------------------------------------------------------ first person

   /**
    * Thor's camera arms (thor_arms.geo.json) replace the vanilla arm; Mjölnir in the hand sits on
    * their grip. RenderArmEvent fires inside renderPlayerArm, so the authored FP rows
    * (ThorFirstPerson / MjolnirFirstPerson at renderArmWithItem) still move them.
    */
   @SubscribeEvent
   public static void onRenderArm(RenderArmEvent event) {
      AbstractClientPlayer player = event.getPlayer();
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         return;
      }

      event.setCanceled(true);
      PlayerRenderer renderer = (PlayerRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
      PlayerModel<AbstractClientPlayer> model = renderer.getModel();
      boolean left = event.getArm() == HumanoidArm.LEFT;
      ModelPart arm = left ? model.leftArm : model.rightArm;
      PartPose saved = arm.storePose();
      // RenderArmEvent fires before renderHand's setupAnim: the shared model still holds the last
      // third-person pose of whichever player was drawn. Set the camera rest pose, never read it.
      armRest(arm, left);
      PlayerGeoLayer.Part part = new PlayerGeoLayer.Part(ThorAssets.ARMS, List.of(PlayerGeoLayer.Pass.cutout(ThorAssets.TEXTURE)), geo -> {
         for (String name : new String[]{ThorAssets.RIGHT_FOREARM, ThorAssets.LEFT_FOREARM}) {
            GeoBone elbow = geo.getBone(name);
            if (elbow != null) {
               elbow.rotX = (float)Math.toRadians(FP_ELBOW);
            }
         }
      });
      PoseStack stack = event.getPoseStack();
      PlayerGeoLayer.renderArmPart(stack, event.getMultiBufferSource(), event.getPackedLight(), model, arm, part);
      if (!left && ThorView.place(snapshot, player) == MjolnirPlace.HAND) {
         BakedGeoModel arms = AnimCache.model(ThorAssets.ARMS);
         if (arms != null) {
            stack.pushPose();
            PlayerGeoLayer.partFrame(stack, arm, PlayerBoneMap.Part.RIGHT_ARM);
            drawHeldMjolnir(player, Minecraft.getInstance().getFrameTime(), stack, locator(arms, ThorAssets.MJOLNIR_GRIP),
               event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            stack.popPose();
         }
      }

      arm.loadPose(saved);
   }

   /** The arm ModelPart as renderHand leaves it (setupAnim at age 0, EMPTY pose, xRot 0). */
   static void armRest(ModelPart arm, boolean left) {
      arm.x = left ? 5.0F : -5.0F;
      arm.y = 2.0F;
      arm.z = 0.0F;
      arm.xRot = 0.0F;
      arm.yRot = 0.0F;
      arm.zRot = left ? -0.1F : 0.1F;
   }

   /**
    * First person on a planted hammer (anyone): both arms of the third-person lift rig drawn in view
    * space, so the palms are on the real handle in the world. Both hand passes are taken over; the
    * main-hand pass draws the two arms.
    */
   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) {
         return;
      }

      boolean thorBody = ThorView.of(player) != null;
      float partialTick = event.getPartialTick();
      ThorLift.Frame lift = ThorLift.frame(player, partialTick, Util.getNanos(), thorBody);
      if (lift == null) {
         return;
      }

      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
         return;
      }

      PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
      PoseStack stack = event.getPoseStack();
      stack.pushPose();
      stack.mulPoseMatrix(viewFromWorld(mc.gameRenderer.getMainCamera(), player, partialTick));
      Vec3 root = renderRoot(player, lift);
      Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
      stack.translate(root.x - camera.x, root.y - camera.y, root.z - camera.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - lift.yaw()));
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      if (thorBody) {
         BakedGeoModel body = AnimCache.model(ThorAssets.BODY);
         if (body != null) {
            body.resetBones();
            ThorBodyPose.baseClips(body, player.isCrouching() ? 1.0F : 0.0F, 0.0F);
            applyLift(body, player, lift);
            GeoBone torso = body.getBone(ThorAssets.BODY_BONE);
            for (String name : new String[]{ThorAssets.RIGHT_ARM, ThorAssets.LEFT_ARM}) {
               GeoBone arm = body.getBone(name);
               if (arm != null && torso != null) {
                  stack.pushPose();
                  stack.mulPoseMatrix(GeoPose.frame(torso));
                  AnimRenderer.renderBone(arm, stack, buffers.getBuffer(RenderType.entityCutoutNoCull(ThorAssets.TEXTURE)), light,
                     OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                  stack.popPose();
               }
            }
         }
      } else {
         PlayerModel<AbstractClientPlayer> model = renderer.getModel();
         ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, model.rightSleeve, model.leftSleeve};
         PartPose[] saved = new PartPose[parts.length];
         for (int i = 0; i < parts.length; i++) {
            saved[i] = parts[i].storePose();
            parts[i].resetPose();
         }

         poseVanilla(model, player, lift);
         model.rightSleeve.copyFrom(model.rightArm);
         model.leftSleeve.copyFrom(model.leftArm);
         stack.scale(-1.0F, -1.0F, 1.0F);
         stack.scale(ThorLift.VANILLA_SCALE, ThorLift.VANILLA_SCALE, ThorLift.VANILLA_SCALE);
         stack.translate(0.0F, -ThorLift.VANILLA_LIFT, 0.0F);
         ResourceLocation skin = renderer.getTextureLocation(player);
         model.rightArm.render(stack, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
         model.leftArm.render(stack, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
         model.rightSleeve.render(stack, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
         model.leftSleeve.render(stack, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
         for (int i = 0; i < parts.length; i++) {
            parts[i].loadPose(saved[i]);
         }
      }

      stack.popPose();
   }

   /**
    * View space of the hand pass from world space (relative to the camera position): the camera
    * turn GameRenderer gives the level, with renderHandsWithItems' arm sway undone (the event
    * stack carries it; a world-anchored arm must not sway).
    */
   static Matrix4f viewFromWorld(Camera camera, LocalPlayer player, float partialTick) {
      float swayX = (player.getViewXRot(partialTick) - Mth.lerp(partialTick, player.xBobO, player.xBob)) * 0.1F;
      float swayY = (player.getViewYRot(partialTick) - Mth.lerp(partialTick, player.yBobO, player.yBob)) * 0.1F;
      return new Matrix4f().rotateY((float)Math.toRadians(-swayY)).rotateX((float)Math.toRadians(-swayX))
         .rotate(new Quaternionf().rotationX((float)Math.toRadians(camera.getXRot())))
         .rotate(new Quaternionf().rotationY((float)Math.toRadians(camera.getYRot() + 180.0F)));
   }
}
