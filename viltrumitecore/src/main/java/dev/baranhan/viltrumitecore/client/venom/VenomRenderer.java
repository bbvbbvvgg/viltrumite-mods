package dev.baranhan.viltrumitecore.client.venom;

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
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Venom replaces the player model with his own body (venom.geo.json) whenever the symbiote has the body: in the form
 * and from the transformation's GROW stage (VenomView.look); before that (and at the end of the reverse) the human
 * player is drawn and VenomCoatLayer spreads the coat over him. Passes: the glossy black atlas, the white marks
 * (translucent, they form at the FACE stage), the faint eye sheen, the living pulse (strands that throb blue-violet,
 * brighter in rage and where he was hit), then the procedural tendrils: idle strands off the back, rage tendrils,
 * the lash tendrils of the combo, shell spikes, the tendril line to its end (shot, pull, zip, web line) and the
 * tendrils wrapped round a held body. The boss draws through {@link #drawBody}. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class VenomRenderer {
   /** Geometry height in blocks (tools/assets/venom_model.py: 34 px, the hunch included). */
   static final float GEO_HEIGHT = 34.0F / 16.0F;
   static final float TRANSLUCENT_ALPHA = 0.15F;
   static final float ITEM_SCALE = 1.35F;
   public static final Vector3f FP_OFFSET = new Vector3f(0.0F, -1.58F, -0.30F);
   public static final float FP_SCALE = 0.8F;
   private static final Map<LivingEntity, float[]> RAGE_LERP = new WeakHashMap<>();

   private VenomRenderer() {
   }

   /** Everything the body pass needs, for a player or the boss. */
   public record Frame(LivingEntity e, int flags, @Nullable VenomMove move, double ticks, VenomView.Look look, @Nullable Vec3 tendrilEnd,
      int targetId, float hunger) {
   }

   /** The husk sits slumped on the ground: lowered while it is drawn (pushed last, popped in Post). */
   static final float HUSK_DROP = 0.6F;
   private static final java.util.Set<Entity> LOWERED = java.util.Collections.newSetFromMap(new WeakHashMap<>());

   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
   public static void onHuskPre(RenderPlayerEvent.Pre event) {
      HeroPublicSnapshot s = VenomView.of(event.getEntity());
      if (s != null && VenomView.flag(s, VenomFlags.POSSESSING) && !event.getEntity().isPassenger()) {
         event.getPoseStack().pushPose();
         event.getPoseStack().translate(0.0F, -HUSK_DROP, 0.0F);
         LOWERED.add(event.getEntity());
      }
   }

   @SubscribeEvent
   public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
      if (LOWERED.remove(event.getEntity())) {
         event.getPoseStack().popPose();
      }
   }

   @SubscribeEvent
   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
         return;
      }
      HeroPublicSnapshot s = VenomView.of(player);
      if (s == null) {
         VenomPose.reset(player);
         return;
      }
      float partial = event.getPartialTick();
      VenomMove move = VenomView.move(s);
      double ticks = VenomPose.actionTicks(player, s, partial);
      VenomView.Look look = VenomView.look(VenomView.flag(s, VenomFlags.FORM), move, ticks);
      if (VenomView.flag(s, VenomFlags.POSSESSING) || !look.geo()) {
         return; // the human (with the coat layer / the husk's slump)
      }
      BakedGeoModel body = AnimCache.model(VenomView.BODY);
      if (body == null) {
         return;
      }
      event.setCanceled(true);
      Minecraft mc = Minecraft.getInstance();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      nameTag(player, event.getRenderer(), stack, buffers, light, partial);
      ReinhardPoses.Passes passes = ReinhardPoses.passes(player.isInvisible(), mc.player != null && !player.isInvisibleTo(mc.player),
         mc.shouldEntityAppearGlowing(player));
      if (!passes.body() && !passes.outline()) {
         return;
      }
      boolean wall = VenomView.flag(s, VenomFlags.WALL) || player == mc.player && VenomCrawlClient.clinging();
      VenomPose.pose(body, VenomPose.third(player), player, s.heroFlags(), move, ticks, partial, wall, false);
      applyHead(body, player, partial);
      ItemStack held = player.getMainHandItem();
      boolean showItem = !held.isEmpty() && s.actionId() < 0 && !VenomView.flag(s, VenomFlags.HOLDING);
      if (showItem) {
         VenomPose.addDeg(body.getBone("right_arm"), -14, 0, 4);
         VenomPose.addDeg(body.getBone("right_forearm"), -26, 0, 0);
      }
      Frame f = new Frame(player, s.heroFlags(), move, ticks, look, s.actionTarget(), s.controlTargetId(), VenomView.hunger(s));
      stack.pushPose();
      if (gui()) {
         float k = 1.875F / bodyHeight(player, s.heroFlags(), partial);
         stack.scale(k, k, k);
      }
      setupRotations(player, stack, partial);
      Matrix4f[] hand = new Matrix4f[1];
      drawBody(body, f, stack, buffers, light, LivingEntityRenderer.getOverlayCoords(player, 0.0F), partial, passes, hand,
         bodyHeight(player, s.heroFlags(), partial));
      if (passes.extras() && showItem && hand[0] != null) {
         stack.pushPose();
         stack.last().pose().set(hand[0]);
         GeoBone h = body.getBone("right_hand");
         stack.translate(h.pivotX / 16.0F, h.pivotY / 16.0F - 0.2F, h.pivotZ / 16.0F - 0.04F);
         stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(180.0F));
         float k = ITEM_SCALE / (bodyHeight(player, s.heroFlags(), partial) / GEO_HEIGHT);
         stack.scale(k, k, k);
         mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, stack,
            buffers, light);
         stack.popPose();
      }
      stack.popPose();
   }

   /** The rendered body height: the form's, the rage's (smoothed so the growth never pops). */
   static float bodyHeight(LivingEntity e, int flags, float partial) {
      float[] r = RAGE_LERP.computeIfAbsent(e, k -> new float[]{VenomFlags.has(flags, VenomFlags.RAGE) ? 1 : 0, 0});
      float target = VenomFlags.has(flags, VenomFlags.RAGE) ? 1 : 0;
      int tick = e.tickCount;
      if ((int)r[1] != tick) {
         r[1] = tick;
         r[0] += (target - r[0]) * 0.25F;
      }
      return 1.8F * Mth.lerp(r[0], VenomRules.BODY_HEIGHT_SCALE, VenomRules.RAGE_HEIGHT_SCALE);
   }

   /**
    * Draws the posed body at the stack's origin (already rotated to the body's yaw): scale, the passes, the tendrils.
    * {@code hand[0]} receives the right hand's matrix.
    */
   public static void drawBody(BakedGeoModel body, Frame f, PoseStack stack, MultiBufferSource buffers, int light, int overlay, float partial,
      ReinhardPoses.Passes passes, Matrix4f[] hand, float bodyHeight) {
      Matrix4f base = new Matrix4f(stack.last().pose());
      float scale = bodyHeight / GEO_HEIGHT;
      // the growth (transformation GROW stage): from the human's height and a narrow frame to his own proportions
      float g = f.look().growth();
      float k = Mth.lerp(g, 1.8F / bodyHeight, 1.0F);
      float kw = Mth.lerp(g, 0.74F, 1.0F);
      stack.pushPose();
      stack.scale(scale * k * kw, scale * k, scale * k * kw);
      Matrix4f[] torso = new Matrix4f[1];
      Matrix4f[] handL = new Matrix4f[1];
      Matrix4f[] handR = new Matrix4f[1];
      Matrix4f[] jaw = new Matrix4f[1];
      Matrix3f[] torsoN = new Matrix3f[1];
      AnimRenderer.BoneHook hook = (bone, pose, b, l, o) -> {
         switch (bone.name) {
            case "right_hand" -> {
               hand[0] = new Matrix4f(pose.last().pose());
               handR[0] = palm(pose, bone);
            }
            case "left_hand" -> handL[0] = palm(pose, bone);
            case "torso" -> {
               torso[0] = new Matrix4f(pose.last().pose());
               torsoN[0] = new Matrix3f(pose.last().normal());
            }
            case "jaw" -> jaw[0] = new Matrix4f(pose.last().pose());
            default -> {
            }
         }
      };
      boolean shadow = ShaderCompat.isShadowPass();
      float alpha = passes.translucent() ? TRANSLUCENT_ALPHA : 1.0F;
      if (passes.body()) {
         RenderType skin = passes.translucent() ? RenderType.entityTranslucent(VenomView.SKIN) : RenderType.entityCutoutNoCull(VenomView.SKIN);
         AnimRenderer.render(body, stack, buffers, buffers.getBuffer(skin), light, overlay, 1, 1, 1, alpha, hook);
         float marks = f.look().marks() * alpha;
         if (marks > 0.01F) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.entityTranslucent(VenomView.MARKS)), light, overlay, 1, 1, 1, marks,
               null);
         }
      }
      if (passes.outline() && !shadow) {
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.outline(VenomView.SKIN)), light, overlay, 1, 1, 1, 1, null);
      }
      double seconds = (f.e().tickCount + partial) / 20.0;
      boolean rage = VenomFlags.has(f.flags(), VenomFlags.RAGE);
      if (passes.extras() && !shadow) {
         float eye = f.look().marks() * alpha * (rage ? 0.85F : 0.55F);
         if (eye > 0.01F) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(VenomView.EYES)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               eye, eye, eye, 1.0F, null);
         }
         float p = VenomView.pulse(seconds, rage, f.e().hurtTime) * alpha * (0.4F + 0.6F * g);
         if (VenomFlags.has(f.flags(), VenomFlags.SPIDER_NEAR)) {
            // the symbiote knows its old host: it bristles (a faster, colder throb)
            p = Math.max(p, 0.3F + 0.25F * (float)Math.abs(Math.sin(seconds * 9.0)));
         }
         if (p > 0.01F) {
            AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(VenomView.PULSE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               p * 0.55F, p * 0.6F, p, 1.0F, null);
         }
      }
      stack.popPose();
      if (passes.body() && !passes.translucent()) {
         tendrils(f, stack, buffers, light, overlay, partial, seconds, base, torso[0], torsoN[0], handL[0], handR[0], jaw[0]);
      }
   }

   // --- tendrils -----------------------------------------------------------------------------------------

   private static void tendrils(Frame f, PoseStack stack, MultiBufferSource buffers, int light, int overlay, float partial, double seconds,
      Matrix4f base, @Nullable Matrix4f torso, @Nullable Matrix3f torsoN, @Nullable Matrix4f handL, @Nullable Matrix4f handR, @Nullable Matrix4f jaw) {
      var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VenomView.TENDRIL));
      int flags = f.flags();
      VenomMove move = f.move();
      boolean rage = VenomFlags.has(flags, VenomFlags.RAGE);
      boolean shell = VenomFlags.has(flags, VenomFlags.SHELL);
      if (torso != null && torsoN != null) {
         stack.pushPose();
         stack.last().pose().set(torso);
         stack.last().normal().set(torsoN);
         PoseStack.Pose pose = stack.last();
         // roots on the back (model space, blocks; -z = front, so the back is +z)
         int count = 2;
         float len = 0.5F, amp = 0.12F;
         if (VenomPose.combat(flags, move)) {
            count = 3;
            len = 0.75F;
         }
         if (rage) {
            count = 6;
            len = 1.3F;
            amp = 0.3F;
         }
         if (move == VenomMove.TRANSFORM && f.ticks() < VenomRules.STAGE_POSE) {
            count = 6;
            len = 0.5F + 1.2F * f.look().growth();
            amp = 0.45F;
         } else if (move == VenomMove.REFORM || move == VenomMove.RECOIL) {
            count = 6;
            len = move == VenomMove.RECOIL ? 0.9F : 0.6F;
            amp = 0.5F;
         } else if (move == VenomMove.REVERT) {
            count = 4;
            len = 0.9F * (1 - (float)f.ticks() / VenomView.REVERT_GEO_END);
            amp = 0.4F;
         }
         if (shell) {
            // the hardened shell: short stiff spikes, no lazy strands
            for (int i = 0; i < 8; i++) {
               double a = i / 8.0 * Math.PI * 2;
               Vector3f root = new Vector3f((float)Math.cos(a) * 0.26F, 1.32F + (float)Math.sin(a) * 0.18F, 0.2F);
               Vector3f dir = new Vector3f((float)Math.cos(a) * 0.7F, (float)Math.sin(a) * 0.5F + 0.2F, 0.8F).normalize();
               VenomTendrils.draw(pose, vc, new Vector3f[]{root, new Vector3f(root).add(new Vector3f(dir).mul(0.28F))}, 0.05F, 0.02F, light, overlay, 1, 1, 1,
                  1);
            }
         } else if (len > 0.05F) {
            for (int i = 0; i < count; i++) {
               float side = i % 2 == 0 ? 1 : -1;
               float row = i / 2;
               Vector3f root = new Vector3f(side * (0.1F + 0.05F * row), 1.38F - 0.1F * row, 0.22F);
               Vector3f dir = new Vector3f(side * (0.55F + 0.15F * row), 0.45F - 0.25F * row, 0.75F).normalize();
               Vector3f sideV = new Vector3f(dir).cross(0, 1, 0).normalize();
               Vector3f[] pts = VenomTendrils.wave(root, dir, sideV, len * (1 - 0.12F * row), seconds, i * 1.7F, amp, 9);
               VenomTendrils.draw(pose, vc, pts, 0.055F - 0.006F * row, 0.018F, light, overlay, 1, 1, 1, 1);
            }
         }
         if (move != null && move.isLash()) {
            lash(pose, vc, move, f.ticks(), seconds, light, overlay);
         }
         stack.popPose();
      }
      // the line: hand -> its end (shot / pull / zip / wrapped round a web line)
      Vec3 end = f.tendrilEnd();
      Vec3 origin = f.e().getPosition(partial);
      Matrix4f inv = new Matrix4f(base).invert();
      if (end != null && VenomFlags.has(flags, VenomFlags.TENDRIL) && handR != null) {
         Vector3f from = local(inv, handR, 0, 0, 0);
         Vector3f to = new Vector3f((float)(end.x - origin.x), (float)(end.y - origin.y), (float)(end.z - origin.z));
         float reach = move == VenomMove.TENDRIL_SHOT ? (float)Mth.clamp(f.ticks() / 3.0, 0.1, 1.0) : 1.0F;
         float sag = move == VenomMove.ZIP || move == VenomMove.PULL ? 0.0F : 0.04F;
         stack.pushPose();
         stack.last().pose().set(base);
         VenomTendrils.draw(stack.last(), vc, VenomTendrils.line(from, to, reach, seconds, sag, 14), 0.06F, 0.035F, light, overlay, 1, 1, 1, 1);
         stack.popPose();
      }
      // a held body: tendrils from both hands wound round it
      if (VenomFlags.has(flags, VenomFlags.HOLDING) && f.targetId() >= 0 && f.e().level() != null) {
         Entity t = f.e().level().getEntity(f.targetId());
         if (t != null) {
            Vec3 c = t.getPosition(partial).add(0, t.getBbHeight() * 0.5, 0);
            Vector3f to = new Vector3f((float)(c.x - origin.x), (float)(c.y - origin.y), (float)(c.z - origin.z));
            stack.pushPose();
            stack.last().pose().set(base);
            for (Matrix4f h : new Matrix4f[]{handL, handR}) {
               if (h == null) {
                  continue;
               }
               Vector3f from = local(inv, h, 0, 0, 0);
               VenomTendrils.draw(stack.last(), vc, VenomTendrils.line(from, to, 1.0F, seconds, 0.02F, 10), 0.05F, 0.03F, light, overlay, 1, 1, 1, 1);
            }
            float r = Math.max(0.3F, t.getBbWidth() * 0.62F);
            for (int ring = 0; ring < 3; ring++) {
               Vector3f[] pts = new Vector3f[10];
               float y0 = (ring - 1) * t.getBbHeight() * 0.28F;
               for (int i = 0; i < pts.length; i++) {
                  double a = i / (double)(pts.length - 1) * Math.PI * 1.8 + ring * 1.3 + seconds * 0.8;
                  pts[i] = new Vector3f(to).add((float)Math.cos(a) * r, y0 + i * 0.03F, (float)Math.sin(a) * r);
               }
               VenomTendrils.draw(stack.last(), vc, pts, 0.045F, 0.03F, light, overlay, 1, 1, 1, 1);
            }
            stack.popPose();
         }
      }
      // the tongue tip lashing out of the maw during a roar is part of the geo; the drool strands hang from the jaw
      if (jaw != null && (rage || move == VenomMove.RAGE || move == VenomMove.TRANSFORM && f.ticks() >= VenomRules.STAGE_POSE)) {
         stack.pushPose();
         stack.last().pose().set(jaw);
         for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 0.09F : -0.07F;
            Vector3f top = new Vector3f(x, 1.58F, -0.36F);
            float sway = (float)Math.sin(seconds * 3 + i) * 0.03F;
            VenomTendrils.draw(stack.last(), vc, new Vector3f[]{top, new Vector3f(x + sway, 1.46F, -0.37F), new Vector3f(x + sway * 1.5F, 1.36F, -0.36F)},
               0.012F, 0.006F, light, overlay, 0.85F, 0.85F, 0.95F, 1);
         }
         stack.popPose();
      }
   }

   /** The two heavy lash tendrils from the shoulders: sweep (an arc in front), impale (a thrust), slam (overhead, down). */
   private static void lash(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vc, VenomMove move, double ticks, double seconds, int light,
      int overlay) {
      float t = (float)Mth.clamp(ticks / move.length, 0, 1);
      float c = (float)move.contact / move.length;
      for (int i = 0; i < 2; i++) {
         float side = i == 0 ? 1 : -1;
         Vector3f root = new Vector3f(side * 0.32F, 1.42F, 0.05F);
         Vector3f dir;
         float len;
         if (move == VenomMove.LASH_SWEEP) {
            float a = (float)Mth.lerp(smooth(t / Math.max(0.01F, c + 0.15F)), 1.2 * side, -1.4 * side);
            dir = new Vector3f((float)Math.sin(a), 0.05F, -(float)Math.cos(a));
            len = 3.2F * (float)Math.sin(Math.PI * Math.min(1, t * 1.25));
         } else if (move == VenomMove.LASH_IMPALE) {
            float e = (float)smooth(t / Math.max(0.01F, c));
            dir = new Vector3f(side * 0.15F, 0.05F - 0.1F * e, -1.0F);
            len = 0.4F + 3.4F * e * (t > c + 0.25F ? (1 - (t - c - 0.25F) / (1 - c - 0.25F)) : 1);
         } else {
            float up = (float)smooth(t / Math.max(0.01F, c * 0.7F));
            float down = (float)smooth((t - c * 0.7F) / Math.max(0.01F, c * 0.3F));
            float pitch = Mth.lerp(down, 1.3F * up, -0.6F);
            dir = new Vector3f(side * 0.25F, (float)Math.sin(pitch), -(float)Math.cos(pitch));
            len = 2.8F * (float)Math.min(1, t * 2.0) * (t > 0.85F ? (1 - (t - 0.85F) / 0.15F) : 1);
         }
         if (len < 0.05F) {
            continue;
         }
         dir.normalize();
         Vector3f sideV = new Vector3f(dir).cross(0, 1, 0);
         if (sideV.lengthSquared() < 1.0E-4F) {
            sideV.set(1, 0, 0);
         }
         sideV.normalize();
         Vector3f[] pts = VenomTendrils.wave(root, dir, sideV, len, seconds, i * 2.3F, 0.18F, 12);
         VenomTendrils.draw(pose, vc, pts, 0.09F, 0.035F, light, overlay, 1, 1, 1, 1);
      }
   }

   /** The palm of a hand bone: its pivot (the wrist) and a little down towards the claws. */
   static Matrix4f palm(PoseStack pose, GeoBone bone) {
      return new Matrix4f(pose.last().pose()).translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F - 0.12F, bone.pivotZ / 16.0F);
   }

   /** A point of a bone's frame (model px / 16) in the body's unrotated frame (world offset from its feet). */
   static Vector3f local(Matrix4f inverseBase, Matrix4f bone, float x, float y, float z) {
      Vector4f p = new Vector4f(x, y, z, 1).mul(bone).mul(inverseBase);
      return new Vector3f(p.x, p.y, p.z);
   }

   // --- first person -------------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      AbstractClientPlayer player = mc.player;
      HeroPublicSnapshot s = VenomView.of(player);
      if (s == null || VenomView.flag(s, VenomFlags.POSSESSING)) {
         return;
      }
      float partial = event.getPartialTick();
      VenomMove move = VenomView.move(s);
      double ticks = VenomPose.actionTicks(player, s, partial);
      VenomView.Look look = VenomView.look(VenomView.flag(s, VenomFlags.FORM), move, ticks);
      if (!look.geo()) {
         return;
      }
      boolean ours = s.actionId() >= 0 || VenomView.flag(s, VenomFlags.HOLDING) || player.getMainHandItem().isEmpty();
      if (!ours) {
         return;
      }
      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
         return;
      }
      BakedGeoModel arms = AnimCache.model(VenomView.ARMS);
      if (arms == null) {
         return;
      }
      VenomPose.pose(arms, VenomPose.first(player), player, s.heroFlags(), move, ticks, partial, false, true);
      PoseStack stack = event.getPoseStack();
      MultiBufferSource buffers = event.getMultiBufferSource();
      int light = event.getPackedLight();
      stack.pushPose();
      stack.translate(FP_OFFSET.x, FP_OFFSET.y, FP_OFFSET.z);
      float k = FP_SCALE * Mth.lerp(look.growth(), 0.8F, 1.0F);
      stack.scale(k, k, k);
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(VenomView.SKIN)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1,
         1, null);
      float p = VenomView.pulse((player.tickCount + partial) / 20.0, VenomView.flag(s, VenomFlags.RAGE), player.hurtTime) * 0.7F;
      AnimRenderer.render(arms, stack, null, buffers.getBuffer(RenderType.eyes(VenomView.PULSE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         p * 0.55F, p * 0.6F, p, 1.0F, null);
      stack.popPose();
   }

   // --- shared -------------------------------------------------------------------------------------------

   static void setupRotations(LivingEntity e, PoseStack stack, float partial) {
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      if (e.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) {
         Direction bed = e.getBedOrientation();
         if (bed != null) {
            float shift = e.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1F;
            stack.translate(-bed.getStepX() * shift, 0.0F, -bed.getStepZ() * shift);
         }
         stack.mulPose(Axis.YP.rotationDegrees(bed != null ? ReinhardPoses.sleepYaw(bed) : bodyYaw));
         stack.mulPose(Axis.ZP.rotationDegrees(90.0F));
         stack.mulPose(Axis.YP.rotationDegrees(270.0F));
         return;
      }
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      if (e.deathTime > 0) {
         float dying = Math.min(1.0F, Mth.sqrt((e.deathTime + partial - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }
      float swim = e.getSwimAmount(partial);
      if (e.isFallFlying()) {
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.elytraPitch(e.getFallFlyingTicks() + partial, e.getXRot(), e.isAutoSpinAttack())));
      } else if (swim > 0.0F) {
         boolean inWater = e.isInWater();
         stack.mulPose(Axis.XP.rotationDegrees(ReinhardPoses.swimPitch(swim, inWater, e.getXRot())));
         if (e.isVisuallySwimming()) {
            stack.translate(0.0F, -1.0F, 0.3F);
         }
      }
   }

   static void applyHead(BakedGeoModel body, LivingEntity e, float partial) {
      GeoBone head = body.getBone("head");
      if (head == null) {
         return;
      }
      float yaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot));
      float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
      head.rotY += (float)Math.toRadians(-Mth.clamp(yaw, -70, 70));
      head.rotX += (float)Math.toRadians(-Mth.clamp(pitch, -55, 60));
   }

   static boolean gui() {
      return RenderSystem.getProjectionMatrix().m33() > 0.5F && !ShaderCompat.isShadowPass();
   }

   static double smooth(double t) {
      t = Math.max(0, Math.min(1, t));
      return t * t * (3 - 2 * t);
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
