package dev.baranhan.viltrumitecore.client.dio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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
 * The World in the level (first and third person, every viewer). Clips come
 * from TheWorldClips on the server action clock (contact key = hit tick) with
 * short blends between them; placement is a frame-rate independent spring
 * toward a StandMotion offset, clamped against blocks by a ray from DIO.
 * Procedural layers on top: breathing, head tracking of DIO's look, lean by
 * velocity, summon ghosts, barrage afterimage arms and an emissive pass.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class TheWorldRenderer {
   public static final ResourceLocation GEO = rl("geo/dio/the_world.geo.json");
   public static final ResourceLocation TEXTURE = rl("textures/entity/dio/the_world.png");
   public static final ResourceLocation GLOW = rl("textures/entity/dio/the_world_glow.png");
   /** 40 model units tall → about 2.2 blocks: a head over DIO. */
   public static final float SCALE = 0.9F;
   private static final Set<String> ARM_BONES = Set.of("shoulder_R", "arm_R", "forearm_R", "hand_R", "shoulder_L", "arm_L", "forearm_L", "hand_L");
   private static final Set<String> FIRST_PERSON_HIDDEN = Set.of("head", "neck", "hose_R", "hose_L");
   private static final Map<Integer, Stand> STANDS = new HashMap<>();
   private static ClientLevel lastLevel;

   private TheWorldRenderer() {
   }

   private static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }

   /** Per-DIO render state (render thread only). */
   static final class Stand {
      Vec3 pos;
      float yaw;
      float alpha;
      long lastNanos = System.nanoTime();
      final ActionClock actionClock = new ActionClock();
      final ActionClock standClock = new ActionClock();
      TheWorldClips.Clip clip = TheWorldClips.Clip.IDLE;
      double clipTicks;
      @Nullable
      TheWorldClips.Clip previous;
      double previousTicks;
      double blendStart;
      double lastCombat = -1000.0;
      float headYaw, headPitch;
      float lean;
      int lastPhase;
      @Nullable
      Vec3 handL, handR;
      double lastContactClip = -1.0;
   }

   @Nullable
   public static Vec3 position(int playerId) {
      Stand stand = STANDS.get(playerId);
      return stand == null || stand.alpha < 0.05F ? null : stand.pos;
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
         HeroPublicSnapshot snapshot = DioView.of(player);
         if (snapshot == null) {
            STANDS.remove(player.getId());
            continue;
         }

         Stand stand = STANDS.computeIfAbsent(player.getId(), id -> new Stand());
         if (update(stand, player, snapshot, partial, client) && !player.isInvisible()) {
            render(stand, model, player, snapshot, partial, camera, stack, buffers, toWorld, client);
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

   private static boolean firstPerson(Player player, Minecraft client) {
      return player == client.player && client.options.getCameraType().isFirstPerson();
   }

   /** Advances clocks, clip, alpha and placement; false when nothing is to be drawn. */
   private static boolean update(Stand stand, Player player, HeroPublicSnapshot snapshot, float partial, Minecraft client) {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - stand.lastNanos) / 1.0E9F);
      stand.lastNanos = now;
      double clientTicks = player.tickCount + (double)partial;
      HeroAction action = DioView.action(snapshot);
      boolean ult = action == HeroAction.ROAD_ROLLER;
      boolean cast = action == HeroAction.TIME_STOP;
      int phase = DioView.standPhase(snapshot);
      int actionPhase = action == HeroAction.PRIMARY_ATTACK ? snapshot.extraCooldown(DioRules.EX_COMBO_STEP)
         : snapshot.heroFlag(DioRules.FLAG_FINISHER) ? 1 : 0;
      double t = stand.actionClock.sample(snapshot.actionId(), actionPhase, snapshot.actionElapsed(), snapshot.actionLength(), clientTicks);
      double standT = stand.standClock.sample(phase == DioRules.STAND_NONE ? -1 : phase, 0, DioView.standElapsed(snapshot), 0, clientTicks);

      // Opacity: the summon/dismiss phases own it; casts and the ult bring the stand out on their own.
      float target;
      if (ult || cast) {
         target = 1.0F;
         stand.alpha += (target - stand.alpha) * StandMotion.follow(14.0F, dt);
      } else if (phase == DioRules.STAND_SUMMONING || phase == DioRules.STAND_DISMISSING) {
         stand.alpha = StandMotion.phaseAlpha(phase, standT);
      } else if (phase == DioRules.STAND_OUT) {
         stand.alpha += (1.0F - stand.alpha) * StandMotion.follow(18.0F, dt);
      } else if (stand.lastPhase == DioRules.STAND_OUT) {
         // Cleanup (death, hero change): gone at once, no dismiss clip.
         stand.alpha = 0.0F;
      } else {
         stand.alpha += (0.0F - stand.alpha) * StandMotion.follow(16.0F, dt);
      }

      stand.lastPhase = phase;
      if (stand.alpha < 0.01F) {
         stand.pos = null;
         stand.previous = null;
         return false;
      }

      // Clip choice and transitions.
      boolean combatRecent = clientTicks - stand.lastCombat < 60.0;
      TheWorldClips.Choice choice = TheWorldClips.choose(action, t, phase, standT, snapshot.extraCooldown(DioRules.EX_COMBO_STEP),
         snapshot.heroFlag(DioRules.FLAG_FINISHER), snapshot.heroFlag(DioRules.FLAG_GUARD), clientTicks, combatRecent);
      boolean restart = choice.clip() == stand.clip && !choice.clip().loop && choice.ticks() + 1.0 < stand.clipTicks;
      if (choice.clip() != stand.clip || restart) {
         stand.previous = stand.clip;
         stand.previousTicks = stand.clipTicks;
         stand.blendStart = clientTicks;
         stand.clip = choice.clip();
         stand.lastContactClip = -1.0;
      }

      if (choice.clip().attack() || choice.clip() == TheWorldClips.Clip.GUARD) {
         stand.lastCombat = clientTicks;
      }

      stand.clipTicks = choice.ticks();

      // Placement.
      StandMotion.Mode mode = mode(stand.clip, action, t, firstPerson(player, client));
      float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
      float viewYaw = player.getViewYRot(partial);
      float faceYaw = mode.facesView() ? viewYaw : bodyYaw;
      float rollerYaw = RoadRollerScene.yaw(player.getId());
      if ((mode == StandMotion.Mode.ROLLER || mode == StandMotion.Mode.LEAP) && !Float.isNaN(rollerYaw)) {
         faceYaw = rollerYaw;
      }

      Vec3 feet = player.getPosition(partial);
      double[] off = StandMotion.offset(mode, faceYaw);
      double bob = mode == StandMotion.Mode.IDLE ? Math.sin(clientTicks * 0.09) * 0.05 : 0.0;
      Vec3 goal = feet.add(off[0], off[1] + bob, off[2]);
      if (phase == DioRules.STAND_DISMISSING) {
         // Drawn back into DIO's back.
         double u = Math.min(1.0, standT / DioRules.DISMISS_TICKS);
         goal = goal.lerp(feet.add(0.0, 0.1, 0.0), u * u);
      } else if (phase == DioRules.STAND_SUMMONING) {
         double u = Math.min(1.0, standT / DioRules.SUMMON_TICKS);
         goal = feet.lerp(goal, 0.35 + 0.65 * u);
      }

      goal = clampToWalls(player, feet, goal);
      if (stand.pos == null || stand.pos.distanceToSqr(goal) > 64.0) {
         stand.pos = goal;
         stand.yaw = faceYaw;
      } else {
         float k = StandMotion.follow(mode.rate, dt);
         stand.pos = stand.pos.lerp(goal, k);
         stand.yaw = Mth.rotLerp(StandMotion.follow(mode.facesView() ? 26.0F : 6.0F, dt), stand.yaw, faceYaw);
      }

      // Head follows DIO's look with a lag, lean follows his speed.
      float lookYaw = Mth.clamp(Mth.wrapDegrees(viewYaw - stand.yaw), -55.0F, 55.0F);
      float lookPitch = Mth.clamp(player.getViewXRot(partial), -40.0F, 40.0F);
      float h = StandMotion.follow(10.0F, dt);
      stand.headYaw += (lookYaw - stand.headYaw) * h;
      stand.headPitch += (lookPitch - stand.headPitch) * h;
      Vec3 v = player.getDeltaMovement();
      double fx = -Math.sin(Math.toRadians(stand.yaw)), fz = Math.cos(Math.toRadians(stand.yaw));
      float forwardSpeed = (float)(v.x * fx + v.z * fz);
      stand.lean += (Mth.clamp(forwardSpeed * 1.6F, -0.25F, 0.4F) - stand.lean) * StandMotion.follow(5.0F, dt);
      return true;
   }

   private static StandMotion.Mode mode(TheWorldClips.Clip clip, @Nullable HeroAction action, double t, boolean firstPerson) {
      if (action == HeroAction.ROAD_ROLLER) {
         return t >= DioRules.ROLLER_SLAM ? StandMotion.Mode.ROLLER : StandMotion.Mode.LEAP;
      }

      if (clip == TheWorldClips.Clip.TIME_STOP) {
         return StandMotion.Mode.TIME_STOP;
      }

      if (clip == TheWorldClips.Clip.GUARD) {
         return StandMotion.Mode.GUARD;
      }

      if (clip.attack() || action == HeroAction.MUDA_BARRAGE || action == HeroAction.PRIMARY_ATTACK) {
         return firstPerson ? StandMotion.Mode.ATTACK_FIRST_PERSON : StandMotion.Mode.ATTACK;
      }

      return StandMotion.Mode.IDLE;
   }

   /** Keeps the stand on DIO's side of walls: a ray at chest height from DIO toward the goal. */
   private static Vec3 clampToWalls(Player player, Vec3 feet, Vec3 goal) {
      Vec3 from = feet.add(0.0, 1.2, 0.0);
      Vec3 to = goal.add(0.0, 1.2, 0.0);
      Vec3 dir = to.subtract(from);
      double len = dir.length();
      if (len < 1.0E-3) {
         return goal;
      }

      Vec3 reach = from.add(dir.scale((len + 0.35) / len));
      BlockHitResult hit = player.level().clip(new ClipContext(from, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return goal;
      }

      double keep = Math.max(0.0, hit.getLocation().distanceTo(from) - 0.35);
      Vec3 clamped = from.add(dir.scale(keep / len));
      return new Vec3(clamped.x, goal.y, clamped.z);
   }

   private static void render(Stand stand, BakedGeoModel model, Player player, HeroPublicSnapshot snapshot, float partial, Camera camera, PoseStack stack,
      MultiBufferSource.BufferSource buffers, Matrix4f toWorld, Minecraft client) {
      double clientTicks = player.tickCount + (double)partial;
      pose(stand, model, clientTicks, stand.clipTicks);
      boolean fp = firstPerson(player, client);
      Vec3 cam = camera.getPosition();
      float alpha = stand.alpha;
      if (fp) {
         alpha *= stand.clip.attack() || stand.clip == TheWorldClips.Clip.GUARD || stand.clip == TheWorldClips.Clip.ROLLER_BARRAGE ? 0.86F : 0.6F;
      } else {
         alpha *= StandMotion.cameraFade(cam.distanceTo(stand.pos.add(0.0, 1.3, 0.0)));
      }

      BlockPos at = BlockPos.containing(stand.pos.add(0.0, 1.4, 0.0));
      int packed = LevelRenderer.getLightColor(player.level(), at);
      int light = LightTexture.pack(Math.max(LightTexture.block(packed), 9), LightTexture.sky(packed));
      if (fp) {
         hide(model, FIRST_PERSON_HIDDEN, true);
      }

      // Main body, captured fist positions for the contact flashes.
      stack.pushPose();
      root(stack, stand.pos, stand.yaw, cam, 1.0F);
      Vec3[] fists = new Vec3[2];
      AnimRenderer.BoneHook hook = (bone, pose, src, l, o) -> {
         if (bone.name.equals("hand_L") || bone.name.equals("hand_R")) {
            Vector4f p = new Vector4f(bone.pivotX / 16.0F, (bone.pivotY - 1.8F) / 16.0F, bone.pivotZ / 16.0F, 1.0F);
            pose.last().pose().transform(p);
            toWorld.transform(p);
            fists[bone.name.equals("hand_L") ? 0 : 1] = new Vec3(p.x + cam.x, p.y + cam.y, p.z + cam.z);
         }
      };
      RenderType body = alpha >= 0.995F ? RenderType.entityCutoutNoCull(TEXTURE) : RenderType.entityTranslucent(TEXTURE);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(body), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha, hook);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, alpha, alpha,
         alpha, alpha, null);
      stack.popPose();
      stand.handL = fists[0];
      stand.handR = fists[1];
      contacts(stand);

      // Summon / dismiss: three ghost frames stutter around the body.
      int phase = DioView.standPhase(snapshot);
      if ((phase == DioRules.STAND_SUMMONING || phase == DioRules.STAND_DISMISSING) && !fp) {
         long seed = (long)Math.floor(clientTicks / 2.0) * 31L + player.getId();
         java.util.Random random = new java.util.Random(seed);
         float ghost = 0.22F * (1.0F - stand.alpha * 0.7F);
         for (int i = 0; i < 3; i++) {
            Vec3 jitter = new Vec3((random.nextFloat() - 0.5F) * 0.3F, (random.nextFloat() - 0.5F) * 0.16F, (random.nextFloat() - 0.5F) * 0.3F);
            stack.pushPose();
            root(stack, stand.pos.add(jitter), stand.yaw + (random.nextFloat() - 0.5F) * 10.0F, cam, 1.0F + (random.nextFloat() - 0.5F) * 0.06F);
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, 1.0F, 0.86F, 0.55F, ghost, null);
            stack.popPose();
         }
      }

      // Barrages: four extra arms (two frames back in the loop, spread sideways), golden and see-through.
      if (stand.clip == TheWorldClips.Clip.BARRAGE || stand.clip == TheWorldClips.Clip.ROLLER_BARRAGE) {
         hideExcept(model, ARM_BONES);
         double rx = -Math.cos(Math.toRadians(stand.yaw)), rz = -Math.sin(Math.toRadians(stand.yaw));
         for (int i = 1; i <= 2; i++) {
            double loopT = stand.clip.wrap(stand.clipTicks - i * 1.35);
            pose(stand, model, clientTicks, loopT);
            hideExcept(model, ARM_BONES);
            for (int side = -1; side <= 1; side += 2) {
               double spread = side * 0.11 * i;
               stack.pushPose();
               root(stack, stand.pos.add(rx * spread, 0.05 * i * side, rz * spread), stand.yaw + side * 6.0F * i, cam, 1.0F);
               AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), LightTexture.FULL_BRIGHT,
                  OverlayTexture.NO_OVERLAY, 1.0F, 0.9F, 0.55F, alpha * (0.42F / i), null);
               stack.popPose();
            }
         }
      }

      resetHidden(model);
   }

   /** Root transform: world position, facing yaw (model faces -Z), scale. */
   private static void root(PoseStack stack, Vec3 pos, float yaw, Vec3 cam, float extraScale) {
      stack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      float s = SCALE * extraScale;
      stack.scale(s, s, s);
   }

   /** Clip pose (blended out of the previous clip) plus the procedural layers. */
   private static void pose(Stand stand, BakedGeoModel model, double clientTicks, double clipTicks) {
      Animation anim = AnimCache.animation(rl(stand.clip.file()), stand.clip.id);
      float w = stand.previous == null ? 1.0F : (float)Math.min(1.0, (clientTicks - stand.blendStart) / TheWorldClips.blendIn(stand.clip));
      w = w * w * (3.0F - 2.0F * w);
      if (anim == null) {
         model.resetBones();
      } else if (w >= 1.0F || stand.previous == null) {
         AnimationController.seek(anim, model, clipTicks / 20.0);
         stand.previous = null;
      } else {
         Animation from = AnimCache.animation(rl(stand.previous.file()), stand.previous.id);
         if (from == null) {
            AnimationController.seek(anim, model, clipTicks / 20.0);
         } else {
            AnimationController.blend(model, from, stand.previousTicks / 20.0, anim, clipTicks / 20.0, w);
         }
      }

      // Breathing (chest and shoulders), only where the clip leaves room for it.
      float breath = (float)Math.sin(clientTicks * 0.105);
      float calm = stand.clip.attack() ? 0.25F : 1.0F;
      add(model, "chest", -0.025F * breath * calm, 0.0F, 0.0F);
      add(model, "shoulder_R", 0.0F, 0.0F, 0.02F * breath * calm);
      add(model, "shoulder_L", 0.0F, 0.0F, -0.02F * breath * calm);
      // Head tracking: bone rotY > 0 turns left, so a look to the right subtracts.
      add(model, "head", (float)Math.toRadians(-stand.headPitch * 0.6F), (float)Math.toRadians(-stand.headYaw * 0.75F), 0.0F);
      add(model, "neck", (float)Math.toRadians(-stand.headPitch * 0.25F), (float)Math.toRadians(-stand.headYaw * 0.2F), 0.0F);
      if (stand.clip.attack()) {
         // Strikes follow the look up and down from the waist.
         add(model, "waist", (float)Math.toRadians(-Mth.clamp(stand.headPitch * 0.5F, -25.0F, 25.0F)), 0.0F, 0.0F);
      }

      add(model, "root", -stand.lean, 0.0F, 0.0F);
   }

   private static void add(BakedGeoModel model, String name, float x, float y, float z) {
      GeoBone bone = model.getBone(name);
      if (bone != null) {
         bone.rotX += x;
         bone.rotY += y;
         bone.rotZ += z;
      }
   }

   /** Fist flashes on the frame a strike clip crosses its contact key (the server hit tick). */
   private static void contacts(Stand stand) {
      int contact = TheWorldClips.contactTick(stand.clip);
      if (contact < 0 || stand.handL == null || stand.handR == null) {
         return;
      }

      double now = stand.clipTicks;
      double before = stand.lastContactClip;
      stand.lastContactClip = now;
      if (before < 0.0) {
         before = now;
         if (!stand.clip.loop && now >= contact && now - contact < 1.0) {
            before = contact - 0.01;
         }
      }

      Vec3 forward = new Vec3(-Math.sin(Math.toRadians(stand.yaw)), 0.0, Math.cos(Math.toRadians(stand.yaw)));
      if (stand.clip.loop) {
         // Barrage loops: left at 0, right at 2; a wrap is a crossing of 0.
         if (now < before) {
            DioFxClient.fist(stand.handL, forward, 0.7F);
            DioFxClient.barrageSpark(stand.handL, forward);
         } else if (before < 2.0 && now >= 2.0) {
            DioFxClient.fist(stand.handR, forward, 0.7F);
            DioFxClient.barrageSpark(stand.handR, forward);
         }

         return;
      }

      if (before < contact && now >= contact) {
         boolean left = stand.clip == TheWorldClips.Clip.JAB;
         float power = stand.clip == TheWorldClips.Clip.HOOK || stand.clip == TheWorldClips.Clip.FINISHER ? 1.5F : 1.0F;
         DioFxClient.fist(left ? stand.handL : stand.handR, forward, power);
      }
   }

   private static void hide(BakedGeoModel model, Set<String> names, boolean hidden) {
      for (String name : names) {
         GeoBone bone = model.getBone(name);
         if (bone != null) {
            bone.hidden = hidden;
         }
      }
   }

   /** Hides every bone but the arm chains (fingers ride with their hand bones' children). */
   private static void hideExcept(BakedGeoModel model, Set<String> keep) {
      for (GeoBone bone : model.allBones()) {
         bone.hidden = !keep.contains(bone.name) && !isFinger(bone.name);
      }
   }

   private static boolean isFinger(String name) {
      return name.startsWith("finger_") || name.startsWith("thumb_");
   }

   private static void resetHidden(BakedGeoModel model) {
      for (GeoBone bone : model.allBones()) {
         bone.hidden = false;
      }
   }
}
