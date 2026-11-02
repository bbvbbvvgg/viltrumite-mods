package dev.baranhan.viltrumitecore.client.rohan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.rohan.RohanFlags;
import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Heaven's Door in the level for every viewer. The clip comes from {@link HeavensDoorClips} on Rohan's synced move
 * clock; placement is a frame-rate independent spring toward a {@link HeavensDoorClips.Place} (floating at Rohan's
 * right shoulder, lunging at the body, waiting beside an open book, guarding in front, at his cheek, presenting
 * above him), kept on Rohan's side of walls. Procedural layers: hover bob, head tracking, coat lag. The top hat is
 * a second, translucent pass ("transparent top hat"); appearing and dismissing fade with a paper stutter and
 * sheets ({@link BookPagesRenderer#sheets}).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HeavensDoorRenderer {
   public static final ResourceLocation GEO = rl("geo/rohan/heavens_door.geo.json");
   public static final ResourceLocation TEXTURE = rl("textures/entity/rohan/heavens_door.png");
   public static final ResourceLocation GLOW = rl("textures/entity/rohan/heavens_door_glow.png");
   /** ~24 model units tall: about 1.3 blocks, a small boy floating at Rohan's shoulder. */
   public static final float SCALE = 0.85F;
   private static final Map<Integer, Stand> STANDS = new HashMap<>();
   @Nullable
   private static ClientLevel lastLevel;

   private HeavensDoorRenderer() {
   }

   static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }

   static final class Stand {
      @Nullable
      Vec3 pos;
      float yaw;
      float alpha;
      long lastNanos = System.nanoTime();
      boolean wasOut;
      double shownAt = -1000.0;
      double hiddenAt = -1000.0;
      HeavensDoorClips.Clip clip = HeavensDoorClips.Clip.IDLE;
      double clipTicks;
      @Nullable
      HeavensDoorClips.Clip previous;
      double previousTicks;
      double blendStart;
      float headYaw, headPitch, coat;
      @Nullable
      Vec3 fingertip;
   }

   /** World position of a visible stand's index fingertip (VFX anchor), or null. */
   @Nullable
   public static Vec3 fingertip(int playerId) {
      Stand s = STANDS.get(playerId);
      return s == null || s.alpha < 0.05F ? null : s.fingertip;
   }

   @Nullable
   public static Vec3 position(int playerId) {
      Stand s = STANDS.get(playerId);
      return s == null || s.alpha < 0.05F ? null : s.pos;
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ShaderCompat.isShadowPass()) {
         return;
      }
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         lastLevel = level;
         STANDS.clear();
      }
      if (level == null) {
         return;
      }
      BakedGeoModel model = AnimCache.model(GEO);
      if (model == null) {
         return;
      }
      float partial = event.getPartialTick();
      Camera camera = event.getCamera();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
      Matrix4f toWorld = new Matrix4f(stack.last().pose()).invert();
      boolean drew = false;
      for (Player player : level.players()) {
         HeroPublicSnapshot s = RohanView.of(player);
         if (s == null) {
            STANDS.remove(player.getId());
            continue;
         }
         Stand stand = STANDS.computeIfAbsent(player.getId(), id -> new Stand());
         if (update(stand, player, s, partial, client) && !player.isInvisible()) {
            render(stand, model, player, partial, camera, stack, buffers, toWorld, client);
            drew = true;
         }
      }
      Iterator<Map.Entry<Integer, Stand>> it = STANDS.entrySet().iterator();
      while (it.hasNext()) {
         if (level.getEntity(it.next().getKey()) == null) {
            it.remove();
         }
      }
      if (drew) {
         buffers.endBatch();
      }
   }

   static boolean firstPerson(Player player, Minecraft client) {
      return player == client.player && client.getCameraEntity() == player && client.options.getCameraType().isFirstPerson();
   }

   static float follow(float rate, float dt) {
      return 1.0F - (float)Math.exp(-rate * Math.max(0.0F, dt));
   }

   private static boolean update(Stand stand, Player player, HeroPublicSnapshot s, float partial, Minecraft client) {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - stand.lastNanos) / 1.0E9F);
      stand.lastNanos = now;
      double clientTicks = player.tickCount + (double)partial;
      boolean out = RohanView.standOut(s);
      RohanMove move = RohanView.move(s);
      boolean reading = RohanView.flag(s, RohanFlags.READING);
      if (out && !stand.wasOut) {
         stand.shownAt = clientTicks;
         // a door move brings its own appear clip; outside it the stand unfolds from sheets
         if (move != RohanMove.DOOR) {
            BookPagesRenderer.sheets(player.position().add(0.0, 1.4, 0.0), 6, 0.6F);
         }
      } else if (!out && stand.wasOut) {
         stand.hiddenAt = clientTicks;
         if (stand.pos != null) {
            BookPagesRenderer.sheets(stand.pos.add(0.0, 0.7, 0.0), 5, 0.5F);
         }
      }
      stand.wasOut = out;
      double sinceHidden = clientTicks - stand.hiddenAt;
      boolean dismissing = !out && sinceHidden < HeavensDoorClips.DISMISS_TICKS && stand.pos != null;
      if (!out && !dismissing) {
         stand.alpha = 0.0F;
         stand.pos = null;
         stand.previous = null;
         stand.fingertip = null;
         return false;
      }

      HeavensDoorClips.Choice choice = dismissing
         ? new HeavensDoorClips.Choice(HeavensDoorClips.Clip.DISMISS, sinceHidden, HeavensDoorClips.Place.SHOULDER)
         : HeavensDoorClips.choose(move, s.actionElapsed() + partial, reading, clientTicks - stand.shownAt, clientTicks);
      if (choice.clip() != stand.clip) {
         stand.previous = stand.clip;
         stand.previousTicks = stand.clipTicks;
         stand.blendStart = clientTicks;
         stand.clip = choice.clip();
      }
      stand.clipTicks = choice.ticks();
      if (dismissing) {
         stand.alpha = HeavensDoorClips.dismissAlpha(sinceHidden);
      } else if (choice.clip() == HeavensDoorClips.Clip.APPEAR) {
         stand.alpha = HeavensDoorClips.appearAlpha(choice.ticks());
      } else {
         stand.alpha += (1.0F - stand.alpha) * follow(14.0F, dt);
      }

      Entity target = s.controlTargetId() >= 0 ? player.level().getEntity(s.controlTargetId()) : null;
      float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
      Vec3 feet = player.getPosition(partial);
      Vec3 goal;
      float faceYaw = bodyYaw;
      HeavensDoorClips.Place place = choice.place();
      if ((place == HeavensDoorClips.Place.LUNGE || place == HeavensDoorClips.Place.AT_BOOK) && target == null) {
         place = HeavensDoorClips.Place.SHOULDER;
      }
      switch (place) {
         case LUNGE, AT_BOOK -> {
            Vec3 at = target.getPosition(partial);
            Vec3 toRohan = feet.subtract(at).multiply(1.0, 0.0, 1.0);
            toRohan = toRohan.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : toRohan.normalize();
            Vec3 side = new Vec3(-toRohan.z, 0.0, toRohan.x);
            double reach = target.getBbWidth() * 0.5 + (place == HeavensDoorClips.Place.LUNGE ? 0.55 : 0.85);
            double height = Math.max(0.0, (target instanceof LivingEntity l ? l.getEyeHeight() : target.getBbHeight() * 0.8) - 1.05);
            Vec3 spot = at.add(toRohan.scale(reach)).add(side.scale(place == HeavensDoorClips.Place.AT_BOOK ? 0.5 : 0.0)).add(0.0, height, 0.0);
            if (place == HeavensDoorClips.Place.LUNGE) {
               double u = Math.min(1.0, choice.ticks() / 4.0);
               goal = shoulder(feet, bodyYaw).lerp(spot, u * u * (3.0 - 2.0 * u));
            } else {
               goal = spot;
            }
            Vec3 d = at.subtract(goal);
            faceYaw = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
         }
         case GUARD -> {
            double fx = -Math.sin(Math.toRadians(bodyYaw)), fz = Math.cos(Math.toRadians(bodyYaw));
            goal = feet.add(fx * 0.95, 0.25, fz * 0.95);
         }
         case CHEEK -> {
            double rx = -Math.cos(Math.toRadians(bodyYaw)), rz = -Math.sin(Math.toRadians(bodyYaw));
            double fx = -Math.sin(Math.toRadians(bodyYaw)), fz = Math.cos(Math.toRadians(bodyYaw));
            goal = feet.add(-rx * 0.7 + fx * 0.35, 0.65, -rz * 0.7 + fz * 0.35);
            faceYaw = bodyYaw + 120.0F;
         }
         case PRESENT -> {
            double fx = -Math.sin(Math.toRadians(bodyYaw)), fz = Math.cos(Math.toRadians(bodyYaw));
            goal = feet.add(-fx * 0.45, 1.55 + Math.sin(clientTicks * 0.2) * 0.04, -fz * 0.45);
         }
         default -> goal = shoulder(feet, bodyYaw).add(0.0, Math.sin(clientTicks * 0.11) * 0.06, 0.0);
      }
      if (place != HeavensDoorClips.Place.LUNGE && place != HeavensDoorClips.Place.AT_BOOK) {
         goal = clampToWalls(player, feet, goal);
      }
      if (stand.pos == null || stand.pos.distanceToSqr(goal) > 100.0) {
         stand.pos = goal;
         stand.yaw = faceYaw;
      } else {
         float rate = place == HeavensDoorClips.Place.LUNGE ? 30.0F : place == HeavensDoorClips.Place.GUARD ? 24.0F : 8.0F;
         stand.pos = stand.pos.lerp(goal, follow(rate, dt));
         stand.yaw = Mth.rotLerp(follow(10.0F, dt), stand.yaw, faceYaw);
      }
      float lookYaw = Mth.clamp(Mth.wrapDegrees(player.getViewYRot(partial) - stand.yaw), -50.0F, 50.0F);
      float lookPitch = Mth.clamp(player.getViewXRot(partial), -35.0F, 35.0F);
      if (target != null && (place == HeavensDoorClips.Place.AT_BOOK || place == HeavensDoorClips.Place.LUNGE)) {
         lookYaw = 0.0F;
         lookPitch = 10.0F;
      }
      float h = follow(9.0F, dt);
      stand.headYaw += (lookYaw - stand.headYaw) * h;
      stand.headPitch += (lookPitch - stand.headPitch) * h;
      Vec3 v = player.getDeltaMovement();
      stand.coat += (Mth.clamp((float)v.horizontalDistance() * 2.5F, 0.0F, 0.6F) - stand.coat) * follow(4.0F, dt);
      return true;
   }

   /** Floating at Rohan's right shoulder, a little behind. */
   static Vec3 shoulder(Vec3 feet, float yaw) {
      double rx = -Math.cos(Math.toRadians(yaw)), rz = -Math.sin(Math.toRadians(yaw));
      double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
      return feet.add(rx * 0.62 - fx * 0.3, 0.62, rz * 0.62 - fz * 0.3);
   }

   private static Vec3 clampToWalls(Player player, Vec3 feet, Vec3 goal) {
      Vec3 from = feet.add(0.0, 1.2, 0.0);
      Vec3 to = goal.add(0.0, 0.8, 0.0);
      Vec3 dir = to.subtract(from);
      double len = dir.length();
      if (len < 1.0E-3) {
         return goal;
      }
      Vec3 reach = from.add(dir.scale((len + 0.3) / len));
      BlockHitResult hit = player.level().clip(new ClipContext(from, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return goal;
      }
      double keep = Math.max(0.0, hit.getLocation().distanceTo(from) - 0.3);
      Vec3 c = from.add(dir.scale(keep / len));
      return new Vec3(c.x, goal.y, c.z);
   }

   private static void render(Stand stand, BakedGeoModel model, Player player, float partial, Camera camera, PoseStack stack,
      MultiBufferSource.BufferSource buffers, Matrix4f toWorld, Minecraft client) {
      double clientTicks = player.tickCount + (double)partial;
      pose(stand, model, clientTicks);
      Vec3 cam = camera.getPosition();
      float alpha = stand.alpha;
      boolean fp = firstPerson(player, client);
      // in first person the stand at the shoulder would fill the view: fade it when it is close to the camera
      double dist = cam.distanceTo(stand.pos.add(0.0, 0.8, 0.0));
      alpha *= (float)Math.max(fp ? 0.0 : 0.15, Math.min(1.0, (dist - 0.45) / 0.9));
      if (alpha < 0.02F) {
         resetHidden(model);
         stand.fingertip = null;
         return;
      }
      BlockPos at = BlockPos.containing(stand.pos.add(0.0, 0.9, 0.0));
      int packed = LevelRenderer.getLightColor(player.level(), at);
      int light = LightTexture.pack(Math.max(LightTexture.block(packed), 10), LightTexture.sky(packed));
      Vec3[] tip = new Vec3[1];
      AnimRenderer.BoneHook hook = (bone, pose, src, l, o) -> {
         if (bone.name.equals("finger_R")) {
            Vector4f p = new Vector4f(bone.pivotX / 16.0F, (bone.pivotY - 1.6F) / 16.0F, bone.pivotZ / 16.0F, 1.0F);
            pose.last().pose().transform(p);
            toWorld.transform(p);
            tip[0] = new Vec3(p.x + cam.x, p.y + cam.y, p.z + cam.z);
         }
      };
      GeoBone hat = model.getBone("hat");
      stack.pushPose();
      root(stack, stand.pos, stand.yaw, cam);
      if (hat != null) {
         hat.hidden = true;
      }
      RenderType body = alpha >= 0.995F ? RenderType.entityCutoutNoCull(TEXTURE) : RenderType.entityTranslucent(TEXTURE);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(body), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha, hook);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         alpha, alpha, alpha, alpha, null);
      if (hat != null) {
         // the transparent top hat: only its own cubes, see-through
         for (GeoBone b : model.allBones()) {
            b.hidden = b != hat;
         }
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY,
            0.85F, 0.95F, 1.0F, alpha * 0.42F, null);
      }
      stack.popPose();
      resetHidden(model);
      stand.fingertip = tip[0];
   }

   private static void root(PoseStack stack, Vec3 pos, float yaw, Vec3 cam) {
      stack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      stack.scale(SCALE, SCALE, SCALE);
   }

   private static void pose(Stand stand, BakedGeoModel model, double clientTicks) {
      Animation anim = AnimCache.animation(rl(stand.clip.file()), stand.clip.id);
      float w = stand.previous == null ? 1.0F : (float)Math.min(1.0, (clientTicks - stand.blendStart) / 3.0);
      w = w * w * (3.0F - 2.0F * w);
      if (anim == null) {
         model.resetBones();
      } else if (w >= 1.0F || stand.previous == null) {
         AnimationController.seek(anim, model, stand.clipTicks / 20.0);
         stand.previous = null;
      } else {
         Animation from = AnimCache.animation(rl(stand.previous.file()), stand.previous.id);
         if (from == null) {
            AnimationController.seek(anim, model, stand.clipTicks / 20.0);
         } else {
            AnimationController.blend(model, from, stand.previousTicks / 20.0, anim, stand.clipTicks / 20.0, w);
         }
      }
      float breath = (float)Math.sin(clientTicks * 0.12);
      add(model, "chest", -0.03F * breath, 0.0F, 0.0F);
      add(model, "head", (float)Math.toRadians(-stand.headPitch * 0.6F), (float)Math.toRadians(-stand.headYaw * 0.8F), 0.0F);
      float sway = (float)Math.sin(clientTicks * 0.17) * 0.06F;
      add(model, "coat_back", 0.12F + stand.coat * 0.5F + sway, 0.0F, 0.0F);
      add(model, "coat_R", stand.coat * 0.3F + sway * 0.6F, 0.0F, 0.04F);
      add(model, "coat_L", stand.coat * 0.3F - sway * 0.6F, 0.0F, -0.04F);
   }

   private static void add(BakedGeoModel model, String name, float x, float y, float z) {
      GeoBone bone = model.getBone(name);
      if (bone != null) {
         bone.rotX += x;
         bone.rotY += y;
         bone.rotZ += z;
      }
   }

   private static void resetHidden(BakedGeoModel model) {
      for (GeoBone bone : model.allBones()) {
         bone.hidden = false;
      }
   }

   static boolean isRohan(Player p) {
      return RohanView.of(p) != null;
   }

   static int doorLength() {
      return RohanRules.DOOR_LENGTH;
   }
}
