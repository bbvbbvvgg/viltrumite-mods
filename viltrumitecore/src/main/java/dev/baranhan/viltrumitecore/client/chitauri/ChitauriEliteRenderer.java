package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriCaptor;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriCommander;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriHeavy;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Elite Chitauri infantry (Captor, Heavy, Commander): each its own geo, atlas, glow and three clip files
 * ({@code <unit>}, {@code <unit>_states}, {@code <unit>_special}), layered the soldier's way and then the unit's own:
 * idle (the commander plants its glaive when it stands still) → walk → run by gait phase, turning on the spot, falling
 * and landing, the knockback stagger, the jump out of a carrier; upper-body aim / shot / reload after a burst / melee;
 * the specials driven by the entity's event timers (captor capture → carry → drop; heavy slam, vent, the chest plate
 * breaking off and its stagger; commander signal, command, glaive sweep and stagger); the additive hurt flinch; death or
 * the hive-severed collapse. Head and chest follow the look except during full-body specials. Client only.
 */
public class ChitauriEliteRenderer<T extends ChitauriSoldier> extends EntityRenderer<T> {
   /** Per-unit look: model, atlases, clip files, render scale and the colour of its weapon energy. */
   public record Profile(String unit, float scale, float shadow, int r, int g, int b, float chargeTicks) {
      ResourceLocation geo() {
         return ChitauriAssets.rl("geo/chitauri/" + this.unit + ".geo.json");
      }
   }

   public static final Profile CAPTOR = new Profile("captor", 1.0F, 0.45F, 60, 230, 200, 16.0F);
   public static final Profile HEAVY = new Profile("heavy", 1.25F, 0.7F, 255, 138, 48, 30.0F);
   public static final Profile COMMANDER = new Profile("commander", 1.1F, 0.5F, 165, 105, 255, 20.0F);

   /** Client-side bookkeeping a frame needs but the entity does not keep (shots in the burst, knockback, reload). */
   static final class Track {
      int lastShot = -1000;
      int shots;
      int reloadStart = -1000;
      int knockStart = -1000;
      int seenTick = -1;
      float turn;
   }

   private static final Map<ChitauriSoldier, Track> TRACKS = new WeakHashMap<>();
   private final Profile profile;
   private final ResourceLocation geo;
   private final ResourceLocation tex;
   private final ResourceLocation glowTex;
   private final ResourceLocation anim;
   private final ResourceLocation states;
   private final ResourceLocation special;
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f muzzle = new Vector3f();
   private boolean muzzleFound;

   public ChitauriEliteRenderer(EntityRendererProvider.Context context, Profile profile) {
      super(context);
      this.profile = profile;
      this.shadowRadius = profile.shadow();
      this.geo = profile.geo();
      this.tex = ChitauriAssets.unitTex(profile.unit(), false);
      this.glowTex = ChitauriAssets.unitTex(profile.unit(), true);
      this.anim = ChitauriAssets.unitAnim(profile.unit(), "");
      this.states = ChitauriAssets.unitAnim(profile.unit(), "_states");
      this.special = ChitauriAssets.unitAnim(profile.unit(), "_special");
   }

   @Override
   public ResourceLocation getTextureLocation(T entity) {
      return this.tex;
   }

   @Override
   public void render(T e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(this.geo);
      if (model == null) {
         return;
      }
      ChitauriPortalClient.Plane portal = ChitauriPortalClient.plane();
      if (portal != null && e.getVehicle() != null && !portal.inFront(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()) + 1.0,
            Mth.lerp(partial, e.zo, e.getZ()), 1.6)) {
         return;
      }
      float age = e.tickCount + partial;
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      Track track = track(e);
      this.pose(model, e, track, partial, age, dying);
      float alpha = dying < 0 ? 1.0F : 1.0F - ChitauriSoldierRenderer.smooth((dying - (ChitauriSoldier.DEATH_TICKS - 14)) / 12.0F);
      if (alpha <= 0.01F) {
         return;
      }
      this.inverse.set(stack.last().pose()).invert();
      this.muzzleFound = false;
      stack.pushPose();
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      float s = this.profile.scale();
      stack.scale(s, s, s);
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(this.tex) : RenderType.entityCutoutNoCull(this.tex);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, this::hook);
      boolean shadowPass = ShaderCompat.isShadowPass();
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      if (!shadowPass && dist <= ChitauriSoldierRenderer.GLOW_LOD) {
         float g = glow(e, age, dying) * alpha;
         if (g > 0.01F) {
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(this.glowTex)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
         }
      }
      stack.popPose();
      if (this.muzzleFound) {
         if (!shadowPass) {
            double x = Mth.lerp(partial, e.xo, e.getX());
            double y = Mth.lerp(partial, e.yo, e.getY());
            double z = Mth.lerp(partial, e.zo, e.getZ());
            e.clientMuzzle = new Vec3(x + this.muzzle.x, y + this.muzzle.y, z + this.muzzle.z);
            e.clientMuzzleTick = e.tickCount;
         }
         float charge = this.charge(e, age);
         if (!shadowPass && charge > 0.0F && dying < 0) {
            this.chargeOrb(stack, buffers, charge, age);
         }
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      if (!this.muzzleFound && "muzzle".equals(bone.name)) {
         this.point.set(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, 1.0F);
         stack.last().pose().transform(this.point);
         this.inverse.transform(this.point);
         this.muzzle.set(this.point.x, this.point.y, this.point.z);
         this.muzzleFound = true;
      }
   }

   private void chargeOrb(PoseStack stack, MultiBufferSource buffers, float charge, float age) {
      Camera camera = this.entityRenderDispatcher.camera;
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      Profile p = this.profile;
      float pulse = 0.85F + 0.15F * Mth.sin(age * 1.7F);
      float core = (0.04F + 0.08F * charge) * pulse * p.scale();
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * 4.2F, 0.15F, p.r(), p.g(), p.b(), (int)(110 * charge));
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * 1.6F, 0.45F, (p.r() + 255) / 2, (p.g() + 255) / 2, (p.b() + 255) / 2, (int)(200 * charge));
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core, 0.7F, 245, 245, 245, (int)(255 * charge));
   }

   /** Pure: weapon charge 0..1 over the unit's charge time. */
   float charge(ChitauriSoldier e, float age) {
      if (!e.charging() || age - e.clientShootStart < 3.0F) {
         return 0.0F;
      }
      return Mth.clamp((age - e.clientChargeStart) / this.profile.chargeTicks(), 0.15F, 1.0F);
   }

   /** Pure: glow intensity (the soldier's curve, plus the unit's own flares: tether, vents, signal). */
   static float glow(ChitauriSoldier e, float age, float dying) {
      float g = ChitauriSoldierRenderer.glow(e, age, dying);
      if (dying >= 0.0F) {
         return g;
      }
      float flare = 0.0F;
      if (e instanceof ChitauriCaptor c) {
         flare = window(age - c.clientTetherStart, 14.0F) + (c.carrying() ? 0.1F : 0.0F);
      } else if (e instanceof ChitauriHeavy h) {
         flare = window(age - h.clientVentStart, 28.0F) * 0.6F + window(age - h.clientSlamStart, 34.0F) * 0.4F;
      } else if (e instanceof ChitauriCommander c) {
         flare = window(age - c.clientSignalStart, ChitauriCommander.SIGNAL_TICKS) * 0.5F;
      }
      return Mth.clamp(g + flare, 0.0F, 1.0F);
   }

   /** Pure: 1 inside a [0, len) window with soft edges, 0 outside. */
   static float window(float since, float len) {
      if (since < 0.0F || since >= len) {
         return 0.0F;
      }
      return ChitauriSoldierRenderer.smooth(since / 3.0F) * ChitauriSoldierRenderer.smooth((len - since) / 6.0F);
   }

   static Track track(ChitauriSoldier e) {
      Track t = TRACKS.computeIfAbsent(e, k -> new Track());
      if (t.seenTick != e.tickCount) {
         t.seenTick = e.tickCount;
         if (e.clientShootStart != t.lastShot && e.clientShootStart > t.lastShot) {
            t.lastShot = e.clientShootStart;
            t.shots++;
         }
         if (t.shots >= 2 && e.tickCount - t.lastShot > 10 && !e.charging()) {
            t.reloadStart = e.tickCount;
            t.shots = 0;
         }
         double hs = Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo));
         if (e.hurtTime == 9 && hs > 0.3 && e.tickCount - t.knockStart > 18) {
            t.knockStart = e.tickCount;
         }
         float rate = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO);
         t.turn += (Mth.clamp(rate / 8.0F, -1.0F, 1.0F) - t.turn) * 0.35F;
      }
      return t;
   }

   /** One-shot layer: plays a clip from {@code since} ticks with soft in/out; returns the weight used (0 when off). */
   static float once(BakedGeoModel model, @Nullable Animation clip, float since, float fadeIn, float fadeOut, float scale) {
      if (clip == null || since < 0.0F || since >= clip.lengthSeconds() * 20.0F) {
         return 0.0F;
      }
      double sec = since / 20.0;
      float w = ChitauriSoldierRenderer.smooth((float)(sec / Math.max(0.001F, fadeIn)))
         * ChitauriSoldierRenderer.smooth((float)((clip.lengthSeconds() - sec) / Math.max(0.001F, fadeOut))) * scale;
      if (w > 0.005F) {
         AnimationController.overlay(clip, model, sec, w);
      }
      return w;
   }

   @Nullable
   private Animation clip(String name) {
      Animation a = AnimCache.animation(this.anim, name);
      if (a == null) {
         a = AnimCache.animation(this.states, name);
      }
      if (a == null) {
         a = AnimCache.animation(this.special, name);
      }
      return a;
   }

   void pose(BakedGeoModel model, T e, Track track, float partial, float age, float dying) {
      model.resetBones();
      float move = Mth.lerp(partial, e.clientMoveO, e.clientMove);
      float walkW = ChitauriSoldier.walkWeight(move);
      float aim = Mth.lerp(partial, e.clientAimO, e.clientAim);
      Animation idle = this.clip("idle");
      if (idle != null) {
         AnimationController.overlay(idle, model, (age / 20.0 + e.getId() * 0.31) % idle.lengthSeconds(), 1.0F);
      }
      if (e instanceof ChitauriCommander) {
         Animation planted = this.clip("idle_planted");
         float w = (1.0F - walkW) * (1.0F - Mth.clamp(aim * 2.0F, 0.0F, 1.0F)) * (e.mounted() ? 0.0F : 1.0F);
         if (planted != null && w > 0.01F) {
            AnimationController.overlay(planted, model, (age / 20.0 + e.getId() * 0.23) % planted.lengthSeconds(), w);
         }
      }
      if (dying >= 0.0F) {
         Animation clip = this.clip(e.severed() ? "sever" : "death");
         if (clip != null) {
            AnimationController.overlay(clip, model, Math.min(dying / 20.0, clip.lengthSeconds()), ChitauriSoldierRenderer.smooth(dying / 3.0F));
         }
         this.armour(model, e, age);
         return;
      }
      boolean mounted = e.mounted();
      float full = 0.0F; // weight of full-body specials: the look is damped by it
      if (mounted) {
         Animation clip = this.clip("mounted");
         if (clip != null) {
            AnimationController.overlay(clip, model, (age / 20.0 + e.getId() * 0.17) % clip.lengthSeconds(), 1.0F);
         }
      } else {
         double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
         double phase = gait - Math.floor(gait);
         float runW = ChitauriSoldier.runWeight(move);
         Animation walk = this.clip("walk");
         Animation run = this.clip("run");
         if (walk != null && walkW > 0.01F) {
            AnimationController.overlay(walk, model, phase * walk.lengthSeconds(), walkW);
         }
         if (run != null && walkW * runW > 0.01F) {
            AnimationController.overlay(run, model, phase * run.lengthSeconds(), walkW * runW);
         }
         // turning on the spot: the feet step round when the body yaws without walking
         float turnW = Math.abs(track.turn) * (1.0F - walkW);
         Animation turn = this.clip(track.turn > 0 ? "turn_r" : "turn_l");
         if (turn != null && turnW > 0.05F) {
            AnimationController.overlay(turn, model, (age / 20.0) % turn.lengthSeconds(), Math.min(1.0F, turnW * 1.4F));
         }
         float air = Mth.clamp((e.clientAirTicks + partial - 3.0F) / 5.0F, 0.0F, 1.0F);
         Animation fall = this.clip("fall");
         if (fall != null && air > 0.01F) {
            AnimationController.overlay(fall, model, (age / 20.0) % fall.lengthSeconds(), air);
         }
         once(model, this.clip("land"), age - e.clientLandStart, 0.02F, 0.15F, 1.0F);
         full = Math.max(full, once(model, this.clip("knockback"), age - track.knockStart, 0.04F, 0.2F, 1.0F));
         full = Math.max(full, once(model, this.clip("disembark"), age - disembarkStart(e), 0.02F, 0.15F, 1.0F));
      }
      // upper body: aim hold, the shot, the reload after a burst, the melee strike
      Animation melee = this.clip("melee");
      float sinceMelee = age - e.clientMeleeStart;
      boolean meleeOn = !mounted && melee != null && sinceMelee >= 0.0F && sinceMelee < melee.lengthSeconds() * 20.0F;
      boolean busy = this.specialBusy(e, age);
      Animation hold = mounted ? this.clip("mounted_shoot") : this.clip("aim");
      if (hold != null && aim > 0.01F && !meleeOn && !busy) {
         AnimationController.overlay(hold, model, mounted ? 0.02 : (age / 20.0) % hold.lengthSeconds(), aim);
      }
      Animation shot = mounted ? hold : this.clip("shoot");
      float sinceShot = age - e.clientShootStart;
      if (e instanceof ChitauriCaptor c && c.clientTetherStart > e.clientShootStart) {
         sinceShot = age - c.clientTetherStart;
      }
      if (!busy) {
         once(model, shot, sinceShot, 0.001F, 0.08F, Math.max(aim, 0.6F));
         if (!mounted && sinceShot > 6.0F) {
            once(model, this.clip("reload"), age - track.reloadStart, 0.08F, 0.12F, 1.0F);
         }
      }
      if (meleeOn) {
         once(model, melee, sinceMelee, 0.05F, 0.1F, 1.0F);
      }
      if (!mounted) {
         full = Math.max(full, this.specials(model, e, age));
      }
      Animation hurt = this.clip("hurt");
      if (hurt != null && e.hurtTime > 0) {
         AnimationController.additive(hurt, model, Math.min(hurt.lengthSeconds(), (10 - e.hurtTime + partial) / 20.0), 1.0F);
      }
      this.armour(model, e, age);
      // look
      float look = 1.0F - full;
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw), -55.0F, 55.0F) * look;
      float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -45.0F, 45.0F) * look;
      float carry = aim * 0.55F;
      ChitauriSoldierRenderer.deg(model.getBone("torso"), 0.0F, headYaw * 0.15F, 0.0F);
      ChitauriSoldierRenderer.deg(model.getBone("chest"), pitch * carry, headYaw * 0.2F, 0.0F);
      ChitauriSoldierRenderer.deg(model.getBone("head"), pitch * (1.0F - carry) * 0.8F, headYaw * 0.65F, 0.0F);
   }

   private static int disembarkStart(ChitauriSoldier e) {
      if (e instanceof ChitauriCaptor c) {
         return c.clientDisembarkStart;
      }
      if (e instanceof ChitauriHeavy h) {
         return h.clientDisembarkStart;
      }
      if (e instanceof ChitauriCommander c) {
         return c.clientDisembarkStart;
      }
      return -1000;
   }

   /** True while a special owns the arms (no aim hold or shot layered over it). */
   private boolean specialBusy(T e, float age) {
      if (e instanceof ChitauriCaptor c) {
         return age - c.clientCaptureStart < ChitauriCaptor.CAPTURE_TICKS || c.carrying() || age - c.clientDropStart < 14.0F;
      }
      if (e instanceof ChitauriHeavy h) {
         return age - h.clientSlamStart < ChitauriHeavy.SLAM_TICKS || age - h.clientBreakStart < 18.0F;
      }
      if (e instanceof ChitauriCommander c) {
         return age - c.clientSignalStart < ChitauriCommander.SIGNAL_TICKS || age - c.clientSweepStart < 16.0F || age - c.clientCommandStart < 20.0F;
      }
      return false;
   }

   /** The unit's own clips; returns the strongest full-body weight. */
   private float specials(BakedGeoModel model, T e, float age) {
      float full = 0.0F;
      if (e instanceof ChitauriCaptor c) {
         full = Math.max(full, once(model, this.clip("capture"), age - c.clientCaptureStart, 0.03F, 0.2F, 1.0F));
         Animation carry = this.clip("carry");
         if (carry != null && c.carrying()) {
            float in = ChitauriSoldierRenderer.smooth((age - c.clientCaptureStart - ChitauriCaptor.CAPTURE_TICKS + 6.0F) / 6.0F);
            AnimationController.overlay(carry, model, (age / 20.0) % carry.lengthSeconds(), in);
         }
         full = Math.max(full, once(model, this.clip("drop"), age - c.clientDropStart, 0.03F, 0.15F, 1.0F));
      } else if (e instanceof ChitauriHeavy h) {
         full = Math.max(full, once(model, this.clip("slam"), age - h.clientSlamStart, 0.04F, 0.2F, 1.0F));
         once(model, this.clip("vent"), age - h.clientVentStart, 0.1F, 0.2F, 1.0F);
         full = Math.max(full, once(model, this.clip("armor_break"), age - h.clientBreakStart, 0.02F, 0.15F, 1.0F) * 0.6F);
         full = Math.max(full, once(model, this.clip("stagger"), age - h.clientStaggerStart, 0.03F, 0.2F, 1.0F));
      } else if (e instanceof ChitauriCommander c) {
         full = Math.max(full, once(model, this.clip("signal"), age - c.clientSignalStart, 0.08F, 0.25F, 1.0F));
         once(model, this.clip("command"), age - c.clientCommandStart, 0.06F, 0.15F, 1.0F);
         full = Math.max(full, once(model, this.clip("sweep"), age - c.clientSweepStart, 0.03F, 0.12F, 1.0F));
         full = Math.max(full, once(model, this.clip("stagger"), age - c.clientStaggerStart, 0.03F, 0.2F, 1.0F));
      }
      return full;
   }

   /** The heavy's chest plate: flies off in the break clip, then stays gone. */
   private void armour(BakedGeoModel model, T e, float age) {
      if (e instanceof ChitauriHeavy h && h.armorBroken()) {
         Animation brk = this.clip("armor_break");
         float since = age - h.clientBreakStart;
         if (brk == null || since < 0.0F || since >= brk.lengthSeconds() * 20.0F) {
            GeoBone plate = model.getBone("armor_chest");
            if (plate != null) {
               plate.hidden = true;
            }
         }
      }
   }
}
