package dev.baranhan.viltrumitecore.client.captain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerAction;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerProjectileEntity;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The five summoned Avengers. Iron Man, Thor, Widow and Hawkeye share the player-shaped rig (Widow on the slim
 * arms) with their own skin and gear mesh (helmet + faceplate hinge, cape + Mjolnir, hair + pistols, bow +
 * quiver); Hulk has his own model. Locomotion blends idle/walk/run (fly for an airborne Iron Man / Thor), the
 * synced action plays its clip on the action clock and fades out when it ends. Client only.
 */
public class AvengerRenderer extends EntityRenderer<AvengerEntity> {
   private static final int N = AvengerKind.values().length;
   private static final ResourceLocation RIG = CaptainView.rl("geo/avengers/avenger.geo.json");
   private static final ResourceLocation RIG_SLIM = CaptainView.rl("geo/avengers/avenger_slim.geo.json");
   private static final ResourceLocation HULK = CaptainView.rl("geo/avengers/hulk.geo.json");
   private static final ResourceLocation[] SKIN = new ResourceLocation[N];
   private static final ResourceLocation[] GEAR = new ResourceLocation[N];
   private static final ResourceLocation[] GEAR_TEX = new ResourceLocation[N];
   private static final ResourceLocation[] GLOW = new ResourceLocation[N];
   private static final ResourceLocation[] ANIM = new ResourceLocation[N];
   static final double RELEASE_TICKS = 4.0;

   static {
      for (AvengerKind kind : AvengerKind.values()) {
         int i = kind.ordinal();
         String k = kind.key();
         ANIM[i] = CaptainView.rl("animations/avengers/" + k + ".animation.json");
         SKIN[i] = CaptainView.rl("textures/entity/avengers/" + k + ".png");
         if (kind == AvengerKind.HULK) {
            GLOW[i] = CaptainView.rl("textures/entity/avengers/hulk_glow.png");
         } else {
            GEAR[i] = CaptainView.rl("geo/avengers/" + k + "_gear.geo.json");
            GEAR_TEX[i] = CaptainView.rl("textures/entity/avengers/" + k + "_gear.png");
            GLOW[i] = CaptainView.rl("textures/entity/avengers/" + k + "_gear_glow.png");
         }
      }
   }

   public AvengerRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.5F;
   }

   @Override
   public ResourceLocation getTextureLocation(AvengerEntity entity) {
      return SKIN[entity.kind().ordinal()];
   }

   static float scale(AvengerKind kind) {
      return switch (kind) {
         case HULK, THOR -> 1.0F;
         case IRON_MAN -> 0.97F;
         default -> 0.9375F;
      };
   }

   @Override
   public void render(AvengerEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      AvengerKind kind = e.kind();
      int i = kind.ordinal();
      BakedGeoModel body = AnimCache.model(kind == AvengerKind.HULK ? HULK : kind == AvengerKind.BLACK_WIDOW ? RIG_SLIM : RIG);
      BakedGeoModel gear = GEAR[i] == null ? null : AnimCache.model(GEAR[i]);
      if (body == null) {
         return;
      }
      if (e.getBoundingBox().inflate(0.2).contains(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
         return; // the camera inside the body would fill the screen
      }
      float age = e.tickCount + partial;
      Handoff handoff = handoff(e, partial);
      pose(body, e, kind, age, partial, handoff);
      if (gear != null) {
         // the gear mesh has its own bones (Mjolnir, cape, bow, faceplate, pistols, hair): it plays the same clips
         pose(gear, e, kind, age, partial, handoff);
         props(gear, e, kind, age);
      }
      int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
      float s = scale(kind);
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
      if (e.deathTime > 0) {
         float dying = Math.min(1.0F, Mth.sqrt((e.deathTime + partial - 1.0F) / 20.0F * 1.6F));
         stack.mulPose(Axis.ZP.rotationDegrees(dying * 90.0F));
      }
      stack.scale(s, s, s);
      AnimRenderer.render(body, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN[i])), light, overlay, 1, 1, 1, 1, null);
      boolean shadow = ShaderCompat.isShadowPass();
      if (gear != null) {
         AnimRenderer.render(gear, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(GEAR_TEX[i])), light, overlay, 1, 1, 1, 1, null);
      }
      if (!shadow) {
         BakedGeoModel glowModel = gear != null ? gear : body;
         float g = kind == AvengerKind.IRON_MAN ? 0.85F + 0.15F * Mth.sin(age * 0.3F) : 0.9F;
         AnimRenderer.render(glowModel, stack, null, buffers.getBuffer(RenderType.eyes(GLOW[i])), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            g, g, g, 1.0F, null);
      }
      stack.popPose();
      if (!shadow && e.tickCount != this.lastFx) {
         this.lastFx = e.tickCount;
         ambient(e, kind);
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private int lastFx = -1;

   /** Action clock in ticks (client start of the synced action). */
   static double actionAge(AvengerEntity e, float partial) {
      return e.tickCount - e.clientActionStart + partial;
   }

   /** The action that was cut by a new one: its frozen clip time and how far its fade-out is (0..1). */
   record Handoff(AvengerAction action, double seconds, float weight) {
   }

   private static final class Track {
      int start = Integer.MIN_VALUE;
      int action;
      AvengerAction prev = AvengerAction.NONE;
      double prevSeconds;
      int switchedAt;
   }

   private static final Map<AvengerEntity, Track> TRACKS = new WeakHashMap<>();
   static final double HANDOFF_TICKS = 4.0;

   /** Cross-fade between consecutive actions instead of snapping from one clip into the next. */
   @Nullable
   static Handoff handoff(AvengerEntity e, float partial) {
      Track t = TRACKS.computeIfAbsent(e, k -> new Track());
      if (t.start != e.clientActionStart || t.action != e.clientAction) {
         if (t.start != Integer.MIN_VALUE && t.action != AvengerAction.NONE.ordinal()) {
            AvengerAction old = AvengerAction.byOrdinal(t.action);
            Animation clip = AnimCache.animation(ANIM[e.kind().ordinal()], old.clip());
            double len = clip == null ? 0 : clip.lengthSeconds() * 20.0;
            double ran = e.tickCount - t.start;
            t.prev = ran <= len + RELEASE_TICKS ? old : AvengerAction.NONE;
            t.prevSeconds = Math.min(ran, len) / 20.0;
         } else {
            t.prev = AvengerAction.NONE;
         }
         t.switchedAt = e.tickCount;
         t.start = e.clientActionStart;
         t.action = e.clientAction;
      }
      if (t.prev == AvengerAction.NONE) {
         return null;
      }
      double since = e.tickCount - t.switchedAt + partial;
      if (since >= HANDOFF_TICKS) {
         t.prev = AvengerAction.NONE;
         return null;
      }
      return new Handoff(t.prev, t.prevSeconds, (float)(1.0 - smooth(since / HANDOFF_TICKS)));
   }

   static void pose(BakedGeoModel model, AvengerEntity e, AvengerKind kind, float age, float partial) {
      pose(model, e, kind, age, partial, null);
   }

   static void pose(BakedGeoModel model, AvengerEntity e, AvengerKind kind, float age, float partial, @Nullable Handoff handoff) {
      ResourceLocation file = ANIM[kind.ordinal()];
      model.resetBones();
      boolean airborne = e.flying() && (kind == AvengerKind.IRON_MAN || kind == AvengerKind.THOR);
      Animation idle = AnimCache.animation(file, airborne && kind == AvengerKind.IRON_MAN ? "fly" : "idle");
      if (idle != null) {
         AnimationController.overlay(idle, model, (age / 20.0) % idle.lengthSeconds(), 1.0F);
      }
      float speed = e.walkAnimation.speed(partial);
      double gait = e.walkAnimation.position(partial) / 9.4315;
      if (!airborne && speed > 0.01F) {
         float run = Mth.clamp((speed - 0.45F) * 3.0F, 0.0F, 1.0F);
         float w = Mth.clamp(speed * 2.0F, 0.0F, 1.0F);
         Animation walk = AnimCache.animation(file, "walk");
         Animation sprint = AnimCache.animation(file, "run");
         if (walk != null && w * (1 - run) > 0.01F) {
            AnimationController.overlay(walk, model, frac(gait) * walk.lengthSeconds(), w * (1 - run));
         }
         if (sprint != null && w * run > 0.01F) {
            AnimationController.overlay(sprint, model, frac(gait * 1.4) * sprint.lengthSeconds(), w * run);
         }
      }
      if (handoff != null) {
         Animation old = AnimCache.animation(file, handoff.action().clip());
         if (old != null) {
            AnimationController.overlay(old, model, handoff.seconds(), handoff.weight());
         }
      }
      AvengerAction action = AvengerAction.byOrdinal(e.clientAction);
      if (action != AvengerAction.NONE) {
         Animation clip = AnimCache.animation(file, action.clip());
         if (clip != null) {
            double ticks = actionAge(e, partial);
            double len = clip.lengthSeconds() * 20.0;
            float w = 1.0F;
            if (ticks > len) {
               w = (float)smooth(1.0 - (ticks - len) / RELEASE_TICKS);
            }
            float in = handoff != null ? (float)smooth(Math.min(1.0, ticks / HANDOFF_TICKS)) : (float)smooth(Math.min(1.0, ticks / 2.0 + 0.3));
            if (w > 0.01F) {
               AnimationController.overlay(clip, model, Math.min(ticks, len) / 20.0, w * in);
            }
         }
      }
      GeoBone head = model.getBone("head");
      if (head != null) {
         float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
         float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw);
         head.rotY += (float)Math.toRadians(-Mth.clamp(headYaw, -60, 60));
         head.rotX += (float)Math.toRadians(-Mth.lerp(partial, e.xRotO, e.getXRot()) * 0.6F);
      }
      // cape lag (Thor)
      float sway = (float)Math.sin(age * 0.12) * 3.0F;
      CaptainPose.addDeg(model.getBone("cape"), speed * 40 + sway, 0, 0);
      CaptainPose.addDeg(model.getBone("cape_lower"), speed * 18 + sway * 0.5F, 0, 0);
   }

   /** Prop visibility: Mjolnir while thrown, Widow's pistols holstered on arrival, Hawkeye's nocked arrow. */
   static void props(BakedGeoModel gear, AvengerEntity e, AvengerKind kind, float age) {
      AvengerAction action = AvengerAction.byOrdinal(e.clientAction);
      double ticks = e.tickCount - e.clientActionStart;
      switch (kind) {
         case THOR -> hide(gear, "hammer", hammerThrown(e));
         case BLACK_WIDOW -> {
            boolean holstered = e.phase() == AvengerEntity.PHASE_ARRIVAL || action == AvengerAction.CIRCLE && ticks < 12;
            hide(gear, "right_gun", holstered);
            hide(gear, "left_gun", holstered);
         }
         case HAWKEYE -> {
            boolean nocked = switch (action) {
               case SHOOT, SPECIAL_1, SPECIAL_2, SPECIAL_3 -> ticks < 30;
               case CIRCLE -> ticks >= 8 && ticks < 76;
               default -> false;
            };
            hide(gear, "arrow", !nocked);
         }
         default -> {
         }
      }
   }

   static void hide(BakedGeoModel model, String bone, boolean hidden) {
      GeoBone b = model.getBone(bone);
      if (b != null) {
         b.hidden = b.hidden || hidden;
      }
   }

   /** Mjolnir is in the air (its projectile exists): the hand is empty until it flies back. */
   static boolean hammerThrown(AvengerEntity e) {
      return !e.level().getEntitiesOfClass(AvengerProjectileEntity.class, e.getBoundingBox().inflate(48.0),
         p -> p.type() == AvengerProjectileEntity.HAMMER && p.shooterId() == e.getId()).isEmpty();
   }

   /** Iron Man's boot/palm thrusters while flying; Thor's static while airborne. */
   private static void ambient(AvengerEntity e, AvengerKind kind) {
      var level = e.level();
      AvengerAction action = AvengerAction.byOrdinal(e.clientAction);
      if (kind == AvengerKind.IRON_MAN && (e.flying() || action == AvengerAction.ARRIVE || action == AvengerAction.DEPART)) {
         double yaw = Math.toRadians(e.yBodyRot);
         Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.13);
         for (int s = -1; s <= 1; s += 2) {
            Vec3 foot = e.position().add(side.scale(s));
            level.addParticle(ParticleTypes.SMALL_FLAME, foot.x, foot.y - 0.05, foot.z, 0, -0.12, 0);
         }
         if (e.tickCount % 3 == 0) {
            level.addParticle(ParticleTypes.SMOKE, e.getX(), e.getY() - 0.3, e.getZ(), 0, -0.05, 0);
         }
      } else if (kind == AvengerKind.THOR && e.flying() && e.tickCount % 2 == 0) {
         level.addParticle(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1.0, e.getZ(), 0, 0, 0);
      }
   }

   static double frac(double v) {
      return v - Math.floor(v);
   }

   static double smooth(double t) {
      t = Math.max(0, Math.min(1, t));
      return t * t * (3 - 2 * t);
   }

   @Nullable
   static BakedGeoModel model(AvengerKind kind) {
      return AnimCache.model(kind == AvengerKind.HULK ? HULK : kind == AvengerKind.BLACK_WIDOW ? RIG_SLIM : RIG);
   }
}
