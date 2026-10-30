package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterExit;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterSuitUp;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The empty Mark 48 shell on every client (fix spec 2026-10-28 item 2): from the exit start it is a world object at
 * the frozen origin and facing (HulkbusterExit, on the wire as the action target and the exit word), posed by the
 * {@code exit} clip on the server exit clock; the player renderer only places Tony. When the exit ends normally the
 * same shell carries on as a remnant on the client clock (no hand-over between two renderers, no vanishing):
 * under open sky Veronica takes it (modules detach one by one, arms → legs → torso, and fly up on thruster plumes),
 * otherwise it stands 10 s and crumbles into plates. The server plays the sounds and the crumble shards
 * (HulkbusterExit.shellTick); this class draws the model with the existing geo renderer and the HulkbusterVfx motes.
 * A client that joins later does not see an old remnant (cosmetic only).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterShells {
   /** Remnants at once (oldest dropped). */
   static final int MAX_REMNANTS = 16;
   /** An exit counts as finished (not a break) when it was last seen this close to its end. */
   static final int FINISHED_FROM = HulkbusterLayer.EXIT_TICKS - 5;
   /** Model bones of the three Veronica modules (subtrees); every other bone belongs to the torso. */
   static final String[] ARM_ROOTS = {HulkbusterAssets.SHOULDER_L, HulkbusterAssets.SHOULDER_R};
   static final String[] LEG_ROOTS = {"thigh_l", "thigh_r"};
   private static final Map<Integer, Exit> EXITS = new HashMap<>();
   private static final List<Remnant> REMNANTS = new ArrayList<>();
   private static final Map<BakedGeoModel, Map<GeoBone, Integer>> GROUPS = new WeakHashMap<>();
   /** The level the remnants belong to (a dimension change or a new world drops them). */
   @Nullable
   private static ClientLevel owner;

   private HulkbusterShells() {
   }

   /** A running exit as last seen (the snapshot can be gone the frame the exit ends). */
   private static final class Exit {
      Vec3 origin;
      float yaw;
      boolean veronica;
      int damage;
      int elapsed;
      int lastVfx = -1;
   }

   /** The empty shell after the exit, on the client clock. */
   private static final class Remnant {
      final Vec3 origin;
      final float yaw;
      final boolean veronica;
      final int damage;
      int age;

      Remnant(Exit exit) {
         this.origin = exit.origin;
         this.yaw = exit.yaw;
         this.veronica = exit.veronica;
         this.damage = exit.damage;
      }
   }

   // ---------------------------------------------------------------- state

   /** Reads every player's exit; a normally finished exit becomes a remnant at once (also called from the render). */
   private static void sync(ClientLevel level) {
      Set<Integer> live = new HashSet<>();
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null || HulkbusterView.phase(snapshot) != HulkbusterLayer.Phase.EXITING) {
            continue;
         }

         live.add(player.getId());
         Exit exit = EXITS.computeIfAbsent(player.getId(), id -> new Exit());
         int word = HulkbusterView.sceneWord(snapshot);
         exit.origin = snapshot.actionTarget() != null ? snapshot.actionTarget() : exit.origin != null ? exit.origin : player.position();
         exit.yaw = HulkbusterWire.sceneYaw(word);
         exit.veronica = HulkbusterWire.exitVeronica(word);
         exit.damage = HulkbusterView.damageStage(snapshot);
         exit.elapsed = snapshot.actionElapsed();
      }

      Iterator<Map.Entry<Integer, Exit>> it = EXITS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Exit> entry = it.next();
         if (live.contains(entry.getKey())) {
            continue;
         }

         Exit exit = entry.getValue();
         it.remove();
         if (exit.elapsed >= FINISHED_FROM && exit.origin != null && stillOutside(level, entry.getKey())) {
            if (REMNANTS.size() >= MAX_REMNANTS) {
               REMNANTS.remove(0);
            }

            REMNANTS.add(new Remnant(exit));
         }
      }
   }

   /** The exit ended with the player still here and not dead (a break or a death draws its own debris). */
   private static boolean stillOutside(ClientLevel level, int id) {
      return level.getEntity(id) instanceof Player player && player.isAlive();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         EXITS.clear();
         REMNANTS.clear();
         return;
      }

      if (mc.isPaused()) {
         return;
      }

      if (owner != level) {
         owner = level;
         EXITS.clear();
         REMNANTS.clear();
      }

      sync(level);
      for (Exit exit : EXITS.values()) {
         exitVfx(exit);
      }

      Iterator<Remnant> it = REMNANTS.iterator();
      while (it.hasNext()) {
         Remnant remnant = it.next();
         remnantVfx(remnant);
         if (++remnant.age >= HulkbusterExit.shellLife(remnant.veronica)) {
            it.remove();
         }
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      EXITS.clear();
      REMNANTS.clear();
      owner = null;
   }

   // ---------------------------------------------------------------- anchors (shell-local: side right +, up, forward +)

   static Vec3 at(Vec3 origin, float yaw, double side, double up, double fwd) {
      Vec3 f = HulkbusterSuitUp.forward(yaw);
      Vec3 right = new Vec3(-f.z, 0.0, f.x);
      return origin.add(right.scale(side)).add(0.0, up, 0.0).add(f.scale(fwd));
   }

   // ---------------------------------------------------------------- VFX

   private static void exitVfx(Exit exit) {
      int t = exit.elapsed;
      if (t == exit.lastVfx) {
         return;
      }

      exit.lastVfx = t;
      Vec3 f = HulkbusterSuitUp.forward(exit.yaw);
      Vec3 right = new Vec3(-f.z, 0.0, f.x);
      if (t < HulkbusterExit.VENT_END) {
         // Hydraulic hiss: steam out of the knees, the elbows, the shoulders and the neck.
         HulkbusterVfx.steam(at(exit.origin, exit.yaw, 0.45, 1.05, 0.25), right.add(f).normalize(), 0.7F);
         HulkbusterVfx.steam(at(exit.origin, exit.yaw, -0.45, 1.05, 0.25), right.scale(-1.0).add(f).normalize(), 0.7F);
         if ((t & 1) == 0) {
            HulkbusterVfx.steam(at(exit.origin, exit.yaw, 1.05, 2.0, -0.1), right, 0.6F);
            HulkbusterVfx.steam(at(exit.origin, exit.yaw, -1.05, 2.0, -0.1), right.scale(-1.0), 0.6F);
         } else {
            HulkbusterVfx.steam(at(exit.origin, exit.yaw, 0.0, 2.95, -0.1), new Vec3(0.0, 1.0, 0.0), 0.8F);
            HulkbusterVfx.steam(at(exit.origin, exit.yaw, 0.8, 2.65, -0.3), new Vec3(0.0, 1.0, 0.0).add(right).normalize(), 0.5F);
            HulkbusterVfx.steam(at(exit.origin, exit.yaw, -0.8, 2.65, -0.3), new Vec3(0.0, 1.0, 0.0).subtract(right).normalize(), 0.5F);
         }
      } else if (t == HulkbusterExit.VENT_END + 2) {
         // The chest doors unlatch.
         HulkbusterVfx.dockSparks(at(exit.origin, exit.yaw, 0.55, 2.4, 0.6), 0.6F);
         HulkbusterVfx.dockSparks(at(exit.origin, exit.yaw, -0.55, 2.4, 0.6), 0.6F);
         HulkbusterVfx.steam(at(exit.origin, exit.yaw, 0.0, 2.3, 0.6), f, 1.2F);
      } else if (t == HulkbusterExit.STEP_START + 8) {
         // The knee lands.
         Vec3 knee = at(exit.origin, exit.yaw, -0.45, 0.0, 0.7);
         HulkbusterVfx.dust(knee, 1.0F);
         CameraShake.addAt(knee, 0.25F, 10.0);
      } else if (t == HulkbusterExit.CLOSE_START + 6) {
         HulkbusterVfx.steam(at(exit.origin, exit.yaw, 0.0, 2.4, 0.6), f, 0.8F);
      }
   }

   private static void remnantVfx(Remnant r) {
      Vec3 f = HulkbusterSuitUp.forward(r.yaw);
      int t = r.age;
      if (r.veronica) {
         for (int i = 0; i < HulkbusterExit.MODULES; i++) {
            int since = t - HulkbusterExit.MODULE_LAUNCH[i];
            if (since < -6 || since > HulkbusterExit.MODULE_FLIGHT) {
               continue;
            }

            double rise = HulkbusterExit.moduleRise(since);
            for (Vec3 nozzle : nozzles(r, i)) {
               Vec3 n = nozzle.add(0.0, rise, 0.0);
               if (since < 0) {
                  // Thrusters light up before the module lets go.
                  HulkbusterVfx.exhaust(n, new Vec3(0.0, -1.0, 0.0), 0.25F);
               } else {
                  HulkbusterVfx.exhaust(n, new Vec3(0.0, -1.0, 0.0), since < 8 ? 1.0F : 0.75F);
               }
            }

            if (since == 0) {
               // The module unlatches: sparks and a puff of steam at its joints.
               Vec3 joint = switch (i) {
                  case HulkbusterExit.MODULE_ARMS -> at(r.origin, r.yaw, 0.0, 2.7, 0.0);
                  case HulkbusterExit.MODULE_LEGS -> at(r.origin, r.yaw, 0.0, 1.75, 0.0);
                  default -> at(r.origin, r.yaw, 0.0, 2.2, 0.0);
               };
               HulkbusterVfx.dockSparks(joint.add(f.scale(0.3)), 0.8F);
               HulkbusterVfx.steam(joint, f, 1.0F);
               HulkbusterVfx.dust(r.origin, 0.5F);
            }
         }
      } else if (t < HulkbusterExit.CRUMBLE_AT) {
         // Dead machine: now and then a spark out of a joint, a thin wisp.
         if (t % 23 == 0) {
            HulkbusterVfx.dockSparks(at(r.origin, r.yaw, (t / 23 % 2 == 0 ? 1.0 : -1.0) * 0.95, 2.6, 0.0), 0.35F);
         }
      } else if (t == HulkbusterExit.CRUMBLE_AT) {
         HulkbusterVfx.plates(at(r.origin, r.yaw, 0.0, 2.4, 0.0), 1.2, 1.4F);
         HulkbusterVfx.plates(at(r.origin, r.yaw, 0.95, 1.8, 0.0), 0.6, 0.8F);
         HulkbusterVfx.plates(at(r.origin, r.yaw, -0.95, 1.8, 0.0), 0.6, 0.8F);
         HulkbusterVfx.plates(at(r.origin, r.yaw, 0.0, 0.9, 0.0), 0.8, 0.8F);
      } else if (t == HulkbusterExit.CRUMBLE_AT + 14) {
         HulkbusterVfx.dust(r.origin, 1.6F);
         CameraShake.addAt(r.origin, 0.3F, 12.0);
      } else if (t == HulkbusterExit.CRUMBLE_LIFE - 2) {
         HulkbusterVfx.plates(at(r.origin, r.yaw, 0.0, 0.3, 0.0), 1.4, 1.0F);
         HulkbusterVfx.dust(r.origin, 1.2F);
      }
   }

   /** Thruster nozzles of a module at rest (world). */
   private static Vec3[] nozzles(Remnant r, int module) {
      return switch (module) {
         case HulkbusterExit.MODULE_ARMS -> new Vec3[]{at(r.origin, r.yaw, 1.05, 1.0, 0.1), at(r.origin, r.yaw, -1.05, 1.0, 0.1)};
         case HulkbusterExit.MODULE_LEGS -> new Vec3[]{at(r.origin, r.yaw, 0.42, 0.05, -0.05), at(r.origin, r.yaw, -0.42, 0.05, -0.05)};
         default -> new Vec3[]{at(r.origin, r.yaw, 0.38, 2.15, -0.6), at(r.origin, r.yaw, -0.38, 2.15, -0.6)};
      };
   }

   // ---------------------------------------------------------------- render

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      if (owner != level) {
         return;
      }

      sync(level);
      if (EXITS.isEmpty() && REMNANTS.isEmpty()) {
         return;
      }

      BakedGeoModel body = AnimCache.model(HulkbusterAssets.BODY);
      Animation exitClip = AnimCache.animation(HulkbusterAssets.ANIMATIONS, "exit");
      if (body == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      for (Map.Entry<Integer, Exit> entry : EXITS.entrySet()) {
         Exit exit = entry.getValue();
         if (exit.origin == null) {
            continue;
         }

         HeroPublicSnapshot snapshot = level.getEntity(entry.getKey()) instanceof Player p ? IronManView.of(p) : null;
         double tick = Math.min(HulkbusterLayer.EXIT_TICKS, (snapshot != null ? snapshot.actionElapsed() : exit.elapsed) + partialTick);
         // The owner's camera sits in the helmet until Tony steps out: the first-person Mark 48 arms show the machine.
         if (entry.getKey() == mc.player.getId() && mc.options.getCameraType().isFirstPerson() && HulkbusterExit.big((int)tick)) {
            continue;
         }

         pose(body, exitClip, tick / 20.0);
         draw(body, stack, buffers, level, cameraPos, exit.origin, exit.yaw, HulkbusterExit.reactor(tick), exit.damage, -1, Vec3.ZERO, 0.0F, 0.0F, 1.0F);
      }

      for (Remnant r : REMNANTS) {
         double t = r.age + partialTick;
         pose(body, exitClip, exitClip == null ? 0.0 : exitClip.lengthSeconds());
         if (r.veronica) {
            for (int i = 0; i < HulkbusterExit.MODULES; i++) {
               double since = t - HulkbusterExit.MODULE_LAUNCH[i];
               double rise = HulkbusterExit.moduleRise(since);
               if (rise > 90.0) {
                  continue;
               }

               // A slight sway once it flies; the reactor lights while the thrusters burn.
               float sway = since > 0 ? (float)Math.sin(since * 0.35 + i) * Math.min(1.0F, (float)since / 10.0F) * 4.0F : 0.0F;
               float glow = since > -6 ? 0.7F : 0.12F;
               draw(body, stack, buffers, level, cameraPos, r.origin, r.yaw, glow, r.damage, i, new Vec3(0.0, rise, 0.0), sway, 0.0F, 1.0F);
            }
         } else {
            double crumble = Math.max(0.0, t - HulkbusterExit.CRUMBLE_AT) / HulkbusterExit.CRUMBLE_TICKS;
            if (crumble <= 0.0) {
               float flicker = (r.age / 3 % 7 == 0) ? 0.2F : 0.1F;
               draw(body, stack, buffers, level, cameraPos, r.origin, r.yaw, flicker, r.damage, -1, Vec3.ZERO, 0.0F, 0.0F, 1.0F);
               continue;
            }

            // The plates fall apart: the arms drop sideways, the torso pitches forward and down, the legs buckle;
            // the last third the pieces sink into the dust (scale), then the server's shards are all that is left.
            double fall = Math.min(1.0, crumble * 1.6);
            fall = fall * fall;
            float shrink = (float)Mth.clamp(1.0 - (crumble - 0.66) * 3.0, 0.02, 1.0);
            for (int i = 0; i < HulkbusterExit.MODULES; i++) {
               Vec3 drop;
               float roll;
               float pitch;
               switch (i) {
                  case HulkbusterExit.MODULE_ARMS -> {
                     drop = new Vec3(0.0, -1.5 * fall, 0.0);
                     roll = 0.0F;
                     pitch = (float)(-25.0 * fall);
                  }
                  case HulkbusterExit.MODULE_LEGS -> {
                     drop = new Vec3(0.0, -0.4 * fall, 0.0);
                     roll = 0.0F;
                     pitch = (float)(10.0 * fall);
                  }
                  default -> {
                     drop = new Vec3(0.0, -1.3 * fall, 0.0);
                     roll = 0.0F;
                     pitch = (float)(55.0 * fall);
                  }
               }

               draw(body, stack, buffers, level, cameraPos, r.origin, r.yaw, 0.05F, r.damage, i, drop, roll, pitch, shrink);
            }
         }
      }

      buffers.endBatch();
      body.resetBones();
   }

   private static void pose(BakedGeoModel body, @Nullable Animation clip, double seconds) {
      if (clip == null) {
         body.resetBones();
      } else {
         AnimationController.seek(clip, body, Math.min(clip.lengthSeconds(), Math.max(0.0, seconds)));
      }
   }

   /**
    * Draws the shell (module -1) or one Veronica module of it, offset in the world ({@code lift}), turned about the
    * feet ({@code sway} roll, {@code pitch} forward) and scaled about the module's centre ({@code scale}).
    */
   private static void draw(BakedGeoModel body, PoseStack stack, MultiBufferSource buffers, ClientLevel level, Vec3 cameraPos, Vec3 origin, float yaw,
      float glow, int damage, int module, Vec3 lift, float sway, float pitch, float scale) {
      Vec3 at = origin.add(lift);
      int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.add(0.0, 1.5, 0.0)));
      mask(body, module, damage);
      stack.pushPose();
      stack.translate(at.x - cameraPos.x, at.y - cameraPos.y, at.z - cameraPos.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      double pivot = module == HulkbusterExit.MODULE_LEGS ? 0.0 : module == HulkbusterExit.MODULE_ARMS ? 2.7 : 1.9;
      stack.translate(0.0, pivot, 0.0);
      if (sway != 0.0F) {
         stack.mulPose(Axis.ZP.rotationDegrees(sway));
      }

      if (pitch != 0.0F) {
         stack.mulPose(Axis.XP.rotationDegrees(pitch));
      }

      if (scale != 1.0F) {
         stack.scale(scale, scale, scale);
      }

      stack.translate(0.0, -pivot, 0.0);
      stack.scale(HulkbusterRenderer.SCALE, HulkbusterRenderer.SCALE, HulkbusterRenderer.SCALE);
      AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(HulkbusterAssets.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
         1.0F, 1.0F, 1.0F, 1.0F, null);
      if (!dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) {
         AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.eyes(HulkbusterAssets.GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F, null);
      }

      stack.popPose();
   }

   /** Bone groups per model: 0 arms, 1 legs, 2 torso; flames always hidden, damaged plates hidden by stage. */
   private static void mask(BakedGeoModel body, int module, int damage) {
      Map<GeoBone, Integer> groups = GROUPS.computeIfAbsent(body, HulkbusterShells::groups);
      for (GeoBone bone : body.allBones()) {
         boolean flame = bone.name.startsWith(HulkbusterAssets.FLAME_PREFIX);
         bone.hidden = flame || module >= 0 && groups.getOrDefault(bone, HulkbusterExit.MODULE_TORSO) != module;
      }

      for (int s = 0; s < Math.min(damage, HulkbusterAssets.DAMAGE_PLATES.length); s++) {
         for (String name : HulkbusterAssets.DAMAGE_PLATES[s]) {
            HulkbusterRenderer.hideTree(body.getBone(name));
         }
      }
   }

   private static Map<GeoBone, Integer> groups(BakedGeoModel body) {
      Map<GeoBone, Integer> group = new HashMap<>();
      for (String name : ARM_ROOTS) {
         mark(body.getBone(name), HulkbusterExit.MODULE_ARMS, group);
      }

      for (String name : LEG_ROOTS) {
         mark(body.getBone(name), HulkbusterExit.MODULE_LEGS, group);
      }

      return group;
   }

   private static void mark(@Nullable GeoBone bone, int module, Map<GeoBone, Integer> group) {
      if (bone == null) {
         return;
      }

      group.put(bone, module);
      for (GeoBone child : bone.children) {
         mark(child, module, group);
      }
   }
}
