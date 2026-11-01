package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroSubtitles;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronBossRules;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ultron Prime. On the ground: idle, the combat stance (the overdrive stance in the last stage), walk and run on the
 * gait; in the air the stance blends into the hover and flight loops with the back jets and the boot repulsors
 * burning. Over the moves: the armour flare (pauldrons and jets open, face plates snarl) as he charges the eye beam
 * and the palm blast and through the overdrive, the face talking while his line is on screen, trails on the striking
 * fists. The eyes always glow; the eye beam runs from both eyes to where the server aims it.
 */
public class UltronPrimeRenderer extends UltronUnitRenderer<UltronPrime> {
   private static final String[] FLAMES = {"jetflame_r", "jetflame_l", "sole_r", "sole_l"};
   private static final float[][] DOWN = {{0.0F, -1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}};
   private boolean overdrive;
   private float talk;
   private float talkAge = -1000.0F;

   public UltronPrimeRenderer(EntityRendererProvider.Context context) {
      super(context, UltronAssets.PRIME, 0.6F, new String[]{"muzzle_r", "muzzle_l", "lid_r", "lid_l"}, FLAMES, FLAMES, DOWN, null,
         List.of("head", "chest", "abdomen", "hips", "pauldron_r", "pauldron_l", "arm_r", "forearm_r", "arm_l", "forearm_l", "leg_r", "shin_r", "leg_l",
            "shin_l", "jet_r", "jet_l"));
   }

   @Override
   protected String clipFor(UltronMove move) {
      return move == UltronMove.HEAVY_HIT ? "stagger" : move.clip;
   }

   @Override
   protected Animation clip(String name) {
      return super.clip(this.overdrive && name.equals("combat_idle") ? "overdrive_idle" : name);
   }

   @Override
   protected void pose(BakedGeoModel model, UltronPrime e, float partial, float age, float dying) {
      this.overdrive = e.clientStage == UltronBossRules.Stage.OVERDRIVE;
      super.pose(model, e, partial, age, dying);
   }

   private static float flight(UltronPrime e, float partial) {
      return smooth(Mth.lerp(partial, e.clientHoldO, e.clientHold));
   }

   @Override
   protected void base(BakedGeoModel model, UltronPrime e, float partial, float age, float speed, float combat) {
      float h = flight(e, partial);
      if (h < 0.995F) {
         super.base(model, e, partial, age, speed, this.overdrive ? 1.0F : combat);
      }
      if (h > 0.005F) {
         Animation hover = this.clip("hover");
         if (hover != null) {
            over(hover, model, loop(age / 20.0F, hover), h);
         }
         Animation fly = this.clip("fly");
         float flyW = smooth((speed - 0.08F) / 0.3F);
         if (fly != null && flyW > 0.01F) {
            over(fly, model, loop(age / 20.0F, fly), h * flyW);
         }
      }
   }

   @Override
   protected void extraPose(BakedGeoModel model, UltronPrime e, float partial, float age) {
      float flare = this.overdrive ? 0.55F + 0.2F * Mth.sin(age * 0.25F) : 0.0F;
      float t = e.clientMoveSeconds(partial);
      if (t >= 0.0F) {
         float len = e.clientMove.length / 20.0F;
         float out = smooth((len - t) / 0.3F);
         switch (e.clientMove) {
            case PRIME_BEAM -> flare = Math.max(flare, smooth(t / 0.75F) * out);
            case PRIME_PALM_BLAST -> flare = Math.max(flare, 0.5F * smooth(t / 0.7F) * out);
            case PRIME_SUMMON, PRIME_ABSORB -> flare = Math.max(flare, 0.8F * smooth(t / 0.4F) * out);
            default -> {
            }
         }
      }
      Animation clip = this.clip("flare");
      if (clip != null && flare > 0.01F) {
         AnimationController.additive(clip, model, 0.0, Mth.clamp(flare, 0.0F, 1.0F));
      }
      boolean speaking = e.isAlive() && HeroSubtitles.speakingEntity() == e.getId();
      float dt = age - this.talkAge;
      this.talk = dt < 0.0F || dt > 10.0F ? (speaking ? 1.0F : 0.0F) : this.talk + ((speaking ? 1.0F : 0.0F) - this.talk) * Math.min(1.0F, dt * 0.3F);
      this.talkAge = age;
      Animation talking = this.clip("talk");
      if (talking != null && this.talk > 0.01F) {
         AnimationController.additive(talking, model, loop(age / 20.0F, talking), this.talk);
      }
   }

   @Override
   protected void look(BakedGeoModel model, UltronPrime e, float yaw, float pitch) {
      deg(model.getBone("chest"), pitch * 0.12F, yaw * 0.18F, 0.0F);
      deg(model.getBone("neck_lower"), pitch * 0.18F, yaw * 0.22F, 0.0F);
      deg(model.getBone("neck_upper"), pitch * 0.2F, yaw * 0.25F, 0.0F);
      deg(model.getBone("head"), pitch * 0.4F, yaw * 0.35F, 0.0F);
   }

   @Override
   protected void glowPose(BakedGeoModel model, UltronPrime e, float partial, float age) {
      if (flight(e, partial) < 0.3F || !e.isAlive()) {
         for (String name : FLAMES) {
            GeoBone bone = model.getBone(name);
            if (bone != null) {
               bone.hidden = true;
            }
         }
      }
   }

   @Override
   protected void transform(PoseStack stack, UltronPrime e, float partial, float age) {
      super.transform(stack, e, partial, age);
      float h = flight(e, partial);
      if (h > 0.01F && e.isAlive()) {
         this.attitude(stack, e, partial, 28.0F, 0.8F * h, 0.7F * h);
      }
   }

   @Override
   protected float glow(UltronPrime e, float age, float dying) {
      float g = super.glow(e, age, dying);
      return this.overdrive && dying < 0.0F ? Math.min(1.0F, g + 0.15F + 0.1F * Mth.sin(age * 0.5F)) : g;
   }

   private static float thrust(UltronPrime e, float partial) {
      float speed = Mth.lerp(partial, e.clientSpeedO, e.clientSpeed);
      float accel = Mth.lerp(partial, e.clientAccelO, e.clientAccel);
      float t = 0.45F + speed * 1.1F + Math.max(0.0F, accel) * 1.4F;
      float m = e.clientMoveSeconds(partial);
      if (m >= 0.0F && (e.clientMove == UltronMove.PRIME_DIVE || e.clientMove == UltronMove.PRIME_DASH || e.clientMove == UltronMove.PRIME_ASCEND)) {
         t += 0.6F;
      }
      return Mth.clamp(t, 0.3F, 1.5F);
   }

   @Override
   protected void extras(UltronPrime e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
      Matrix4f m = stack.last().pose();
      float g = this.glow(e, age, dying);
      Vector3f eyeR = this.anchor(A_HEAD);
      Vector3f eyeL = this.anchor(A_TOOL);
      int eyeA = (int)((this.overdrive ? 150 : 110) * g);
      float eyeR0 = this.overdrive ? 0.13F + 0.03F * Mth.sin(age * 0.6F) : 0.1F;
      if (eyeR != null) {
         this.orb(vc, m, eyeR, eyeR0, 0.3F, 255, 40, 30, eyeA);
      }
      if (eyeL != null) {
         this.orb(vc, m, eyeL, eyeR0, 0.3F, 255, 40, 30, eyeA);
      }
      if (dying >= 0.0F) {
         return;
      }
      float h = flight(e, partial);
      if (h > 0.3F) {
         float thrust = thrust(e, partial);
         for (int i = 0; i < FLAMES.length; i++) {
            if (this.nozzleFound[i]) {
               boolean jet = i < 2;
               this.flame(e, partial, vc, m, this.nozzleAt[i], this.nozzleDir[i], (jet ? 0.5F : 0.4F) + (jet ? 0.65F : 0.45F) * thrust,
                  jet ? 0.13F : 0.1F, (h - 0.3F) / 0.7F, age + i * 3.1F);
            }
         }
      }
      float t = e.clientMoveSeconds(partial);
      UltronMove move = t < 0.0F ? UltronMove.NONE : e.clientMove;
      boolean fist = false;
      if (move == UltronMove.PRIME_JAB_CROSS || move == UltronMove.PRIME_UPPERCUT || move == UltronMove.PRIME_BACKHAND || move == UltronMove.PRIME_SLAM) {
         int tick = (int)(t * 20.0F);
         for (int c = 0; c < 60; c++) {
            if (move.contact(c) && tick >= c - 3 && tick <= c + 1) {
               fist = true;
            }
         }
      }
      this.swing(e, A_RIGHT, partial, age, stack, vc, fist, 3.0F, 0.22F, 255, 60, 40);
      this.swing(e, A_LEFT, partial, age, stack, vc, fist, 3.0F, 0.22F, 255, 60, 40);
      Vector3f palm = this.anchor(A_RIGHT);
      if (move == UltronMove.PRIME_PALM_BLAST && palm != null) {
         float hit = move.firstContact() / 20.0F;
         if (t >= 0.2F && t < hit) {
            float k = (t - 0.2F) / (hit - 0.2F);
            this.orb(vc, m, palm, 0.15F + 0.4F * k, 0.3F, 255, 50, 30, (int)(100 + 140 * k));
            this.orb(vc, m, palm, 0.06F + 0.14F * k, 0.6F, 255, 230, 215, (int)(230 * k));
         } else if (t >= hit && t < hit + 0.25F) {
            float f = 1.0F - (t - hit) / 0.25F;
            this.orb(vc, m, palm, 1.0F * f, 0.25F, 255, 100, 70, (int)(240 * f));
         }
      }
      if (move == UltronMove.PRIME_BEAM && t < 0.8F) {
         float k = smooth(t / 0.75F);
         for (Vector3f eye : new Vector3f[]{eyeR, eyeL}) {
            if (eye != null) {
               this.orb(vc, m, eye, 0.12F + 0.35F * k, 0.35F, 255, 50, 35, (int)(120 + 120 * k));
            }
         }
      }
      if (e.flag(UltronUnit.FLAG_BEAM)) {
         this.beam(e, partial, age, m, vc, eyeR, eyeL);
      }
      if (this.overdrive) {
         this.crackle(e, partial, age, m, vc);
      }
   }

   /** The eye beam: from both eyes to the point the server last aimed it at (or straight ahead). */
   private void beam(UltronPrime e, float partial, float age, Matrix4f m, VertexConsumer vc, @Nullable Vector3f eyeR, @Nullable Vector3f eyeL) {
      Vec3 world = UltronVfx.beamEnd(e.getId(), partial);
      Vector3f end;
      if (world != null) {
         end = rel(e, partial, world);
      } else {
         Vec3 look = e.getViewVector(partial);
         Vector3f from = eyeR != null ? eyeR : new Vector3f(0.0F, e.getEyeHeight(), 0.0F);
         end = new Vector3f((float)look.x, (float)look.y, (float)look.z).mul((float)UltronPrime.BEAM_RANGE).add(from);
      }
      float flick = 0.9F + 0.1F * Mth.sin(age * 5.3F);
      for (Vector3f eye : new Vector3f[]{eyeR, eyeL}) {
         if (eye == null) {
            continue;
         }
         Vector3f cam = this.toCam(e, partial, eye);
         GlowDraw.ribbon(vc, m, eye, end, cam, 0.28F * flick, 0.5F * flick, 255, 30, 20, 90, 60);
         GlowDraw.ribbon(vc, m, eye, end, cam, 0.12F * flick, 0.2F * flick, 255, 90, 60, 190, 150);
         GlowDraw.ribbon(vc, m, eye, end, cam, 0.045F, 0.07F, 255, 235, 225, 255, 220);
         this.orb(vc, m, eye, 0.35F * flick, 0.35F, 255, 70, 50, 220);
      }
      this.orb(vc, m, end, 1.1F * flick, 0.3F, 255, 80, 50, 200);
      this.orb(vc, m, end, 0.45F, 0.6F, 255, 230, 210, 230);
   }

   /** Overdrive: red arcs crawling over the body. */
   private void crackle(UltronPrime e, float partial, float age, Matrix4f m, VertexConsumer vc) {
      int frame = (int)(age * 0.7F);
      long seed = frame * 341873128712L + e.getId() * 132897987541L;
      java.util.Random r = new java.util.Random(seed);
      float h = e.getBbHeight();
      for (int arc = 0; arc < 3; arc++) {
         Vector3f p = new Vector3f((r.nextFloat() - 0.5F) * 0.9F, h * (0.25F + 0.6F * r.nextFloat()), (r.nextFloat() - 0.5F) * 0.9F);
         for (int seg = 0; seg < 4; seg++) {
            Vector3f q = new Vector3f(p).add((r.nextFloat() - 0.5F) * 0.5F, (r.nextFloat() - 0.5F) * 0.5F, (r.nextFloat() - 0.5F) * 0.5F);
            Vector3f cam = this.toCam(e, partial, p);
            GlowDraw.ribbon(vc, m, p, q, cam, 0.05F, 0.03F, 255, 60, 40, 170, 120);
            GlowDraw.ribbon(vc, m, p, q, cam, 0.015F, 0.01F, 255, 220, 200, 230, 200);
            p = q;
         }
      }
   }
}
