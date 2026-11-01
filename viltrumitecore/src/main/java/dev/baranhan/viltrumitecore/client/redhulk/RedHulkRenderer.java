package dev.baranhan.viltrumitecore.client.redhulk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.mixin.EntityRendererNameTagInvoker;
import dev.baranhan.viltrumitecore.client.reinhard.NameTagRules;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;

/**
 * The Red Hulk replaces the player model with his own body (red_hulk.geo.json, 2.05 blocks of geometry scaled to
 * the 2.52-block hitbox), drawn in three passes: the skin atlas, the always-lit yellow eyes, and the heat veins whose
 * brightness follows the synced heat (heartbeat pulse, steady in fury, flicker while overheated). First person draws
 * the arms model in camera space at {@link #FP_OFFSET} x {@link #FP_SCALE} whenever the hand is empty or a move runs;
 * with an item in a calm hand the vanilla hand in the Red Hulk skin (HeroSkins) shows it. Client only.
 * <p>Not drawn on his body (like Thor and the Hulkbuster): vanilla armor / elytra / cape layers; items still work.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class RedHulkRenderer {
   /** Geometry height in blocks (32.75 px) and the hitbox height he must fill (1.8 x RedHulkRules.BODY_HEIGHT_SCALE). */
   static final float GEO_HEIGHT = 32.75F / 16.0F;
   static final float BODY_HEIGHT = 1.8F * dev.baranhan.viltrumitecore.hero.redhulk.RedHulkRules.BODY_HEIGHT_SCALE;
   static final float MODEL_SCALE = BODY_HEIGHT / GEO_HEIGHT;
   /** Inventory / GUI previews shrink him back to a player's 1.875. */
   static final float GUI_SCALE = 1.875F / BODY_HEIGHT;
   static final float TRANSLUCENT_ALPHA = 0.15F;
   /** Vanilla items in his fist read small: scale them with the hand. */
   static final float ITEM_SCALE = 1.45F;
   /** Swimming: vanilla shifts its 1.875-block body by 1 along the spine; his is longer. */
   static final float SWIM_EXTRA_SHIFT = -(BODY_HEIGHT - 1.875F) * 0.5F;
   /** Camera-space offset and scale of the first-person arms; keep in sync with tools/assets/red_hulk_clips.py. */
   public static final org.joml.Vector3f FP_OFFSET = new org.joml.Vector3f(0.0F, -1.62F, -0.34F);
   public static final float FP_SCALE = 0.82F;

   private RedHulkRenderer() {
   }

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }
      HeroPublicSnapshot s = RedHulkView.of(player);
      if (s == null) {
         RedHulkPose.reset(player);
         return;
      }
      BakedGeoModel body = AnimCache.model(RedHulkView.BODY);
      if (body == null) {
         return;
      }
      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      float partial = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      nameTag(player, event.getRenderer(), stack, buffers, light, partial);
      ReinhardPoses.Passes passes = ReinhardPoses.passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player),
         mc.shouldEntityAppearGlowing(player));
      if (!passes.body() && !passes.outline()) {
         return;
      }
      boolean gui = gui();
      RedHulkPose.thirdPerson(body, player, s, partial);
      applyHead(body, player, partial);
      ItemStack held = player.getMainHandItem();
      boolean showItem = !held.isEmpty() && s.actionId() < 0 && !RedHulkView.flag(s, RedHulkFlags.HOLDING);
      if (showItem) {
         RedHulkPose.addDeg(body.getBone("right_arm"), -16, 0, 4);
         RedHulkPose.addDeg(body.getBone("right_forearm"), -28, 0, 0);
         RedHulkPose.addDeg(body.getBone("right_fingers"), 0, 0, -70);
      }
      int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
      float swim = player.getSwimAmount(partial);
      stack.pushPose();
      if (gui) {
         stack.scale(GUI_SCALE, GUI_SCALE, GUI_SCALE);
      }
      setupRotations(player, stack, partial, swim);
      stack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
      Matrix4f[] hand = new Matrix4f[1];
      AnimRenderer.BoneHook hook = (bone, pose, b, l, o) -> {
         if (bone.name.equals("right_hand")) {
            hand[0] = new Matrix4f(pose.last().pose());
         }
      };
      boolean shadowPass = ShaderCompat.isShadowPass();
      float alpha = passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F;
      if (passes.body()) {
         RenderType skin = passes.translucent() ? RenderType.entityTranslucent(RedHulkView.SKIN) : RenderType.entityCutoutNoCull(RedHulkView.SKIN);
         AnimRenderer.render(body, stack, buffers, buffers.getBuffer(skin), light, overlay, 1, 1, 1, alpha, hook);
      }
      if (passes.outline() && !shadowPass) {
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(RedHulkView.SKIN)), light, overlay, 1, 1, 1, 1, null);
      }
      if (passes.extras() && !shadowPass) {
         glowPasses(body, stack, buffers, s, player.tickCount + partial, alpha);
      }
      if (passes.extras() && showItem && hand[0] != null) {
         stack.pushPose();
         stack.last().pose().set(hand[0]);
         GeoBone h = body.getBone("right_hand");
         stack.translate(h.pivotX / 16.0F, h.pivotY / 16.0F - 0.2F, h.pivotZ / 16.0F - 0.04F);
         stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(180.0F));
         float k = ITEM_SCALE / MODEL_SCALE;
         stack.scale(k, k, k);
         mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false,
            stack, buffers, light);
         stack.popPose();
      }
      stack.popPose();
   }

   /** The two emissive passes: eyes always, heat veins by the heat (RenderType.eyes is additive; colour = brightness). */
   static void glowPasses(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, HeroPublicSnapshot s, float ticks, float alpha) {
      int heat = RedHulkView.heat(s);
      boolean fury = RedHulkView.furious(s);
      boolean over = RedHulkView.overheated(s);
      float eye = RedHulkView.eyeGlow(heat, fury, over) * alpha;
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(RedHulkView.EYES)), LightTexture.FULL_BRIGHT,
         OverlayTexture.NO_OVERLAY, eye, eye, eye, 1.0F, null);
      float glow = RedHulkView.glow(heat, fury, over, ticks / 20.0) * alpha;
      if (glow > 0.01F) {
         // hotter = whiter: the green and blue channels rise with the heat so the veins go red -> orange -> yellow-white
         float h = RedHulkView.heatFraction(s);
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(RedHulkView.HEAT)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, glow, glow * (0.55F + 0.45F * h), glow * (0.35F + 0.65F * h * h), 1.0F, null);
      }
   }

   /**
    * First person: the arms model whenever the hand is empty, a move runs or a body is held; with an item in the calm hand
    * the vanilla hand (Red Hulk skin via HeroSkins) shows it.
    */
   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      AbstractClientPlayer player = mc.player;
      HeroPublicSnapshot s = RedHulkView.of(player);
      if (s == null) {
         return;
      }
      boolean ours = s.actionId() >= 0 || RedHulkView.flag(s, RedHulkFlags.HOLDING) || player.getMainHandItem().isEmpty();
      if (!ours) {
         return;
      }
      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
         return;
      }
      BakedGeoModel arms = AnimCache.model(RedHulkView.ARMS);
      if (arms == null) {
         return;
      }
      float partial = event.getPartialTick();
      RedHulkPose.firstPerson(arms, player, s, partial);
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      stack.pushPose();
      stack.translate(FP_OFFSET.x, FP_OFFSET.y, FP_OFFSET.z);
      stack.scale(FP_SCALE, FP_SCALE, FP_SCALE);
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(RedHulkView.SKIN)), light, OverlayTexture.NO_OVERLAY,
         1, 1, 1, 1, null);
      float glow = RedHulkView.glow(RedHulkView.heat(s), RedHulkView.furious(s), RedHulkView.overheated(s), (player.tickCount + partial) / 20.0);
      if (glow > 0.01F) {
         float h = RedHulkView.heatFraction(s);
         AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.eyes(RedHulkView.HEAT)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            glow, glow * (0.55F + 0.45F * h), glow * (0.35F + 0.65F * h * h), 1.0F, null);
      }
      stack.popPose();
   }

   static void setupRotations(AbstractClientPlayer player, PoseStack stack, float partial, float swim) {
      float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
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
         float dying = Math.min(1.0F, Mth.sqrt((player.deathTime + partial - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }
      if (player.isFallFlying()) {
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.elytraPitch(player.getFallFlyingTicks() + partial, player.getXRot(), player.isAutoSpinAttack())));
         Vec3 view = player.getViewVector(partial);
         Vec3 motion = player.getDeltaMovementLerped(partial);
         double motionSqr = motion.horizontalDistanceSqr();
         double viewSqr = view.horizontalDistanceSqr();
         if (motionSqr > 0.0 && viewSqr > 0.0) {
            double cos = (motion.x * view.x + motion.z * view.z) / Math.sqrt(motionSqr * viewSqr);
            double side = motion.x * view.z - motion.z * view.x;
            stack.mulPose(Axis.YP.rotation((float)(Math.signum(side) * Math.acos(Mth.clamp(cos, -1.0, 1.0)))));
         }
      } else if (swim > 0.0F) {
         boolean inWater = player.isInWater() || player.isInFluidType((fluid, height) -> player.canSwimInFluidType(fluid));
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.swimPitch(swim, inWater, player.getXRot())));
         if (player.isVisuallySwimming()) {
            stack.translate(0.0F, -1.0F + SWIM_EXTRA_SHIFT, 0.3F);
         }
      }
   }

   static void applyHead(BakedGeoModel body, AbstractClientPlayer player, float partial) {
      GeoBone head = body.getBone("head");
      if (head == null) {
         return;
      }
      float yaw = Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
      float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
      // the short neck turns less than a player's; the torso lean of the clips already tilts the head forward
      head.rotY += (float)Math.toRadians(-Mth.clamp(yaw, -60, 60));
      head.rotX += (float)Math.toRadians(-Mth.clamp(pitch, -50, 60));
   }

   private static boolean gui() {
      return RenderSystem.getProjectionMatrix().m33() > 0.5F && !ShaderCompat.isShadowPass();
   }

   private static void nameTag(AbstractClientPlayer player, PlayerRenderer renderer, PoseStack stack, MultiBufferSource buffers, int light, float partial) {
      EntityRendererNameTagInvoker invoker = (EntityRendererNameTagInvoker)renderer;
      RenderNameTagEvent event = new RenderNameTagEvent(player, player.getDisplayName(), renderer, stack, buffers, light, partial);
      MinecraftForge.EVENT_BUS.post(event);
      Event.Result result = event.getResult();
      if (NameTagRules.show(result == Event.Result.DENY, result == Event.Result.ALLOW, invoker.viltrumitecore$shouldShowName(player))) {
         invoker.viltrumitecore$renderNameTag(player, event.getContent(), stack, buffers, light);
      }
   }
}
