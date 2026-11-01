package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Shared renderer of Ultron's machines. Every frame the model is posed from the synced state, in layers: the idle
 * (and the combat stance as the unit gets into a fight), walk and run on the gait phase (feet planted, strides from
 * the entity) or hover and flight for flyers, the additive acceleration / braking / turning leans, the timed move
 * (the clip of {@link UltronUnit#clientMove} from its start tick: the server lands damage, sound and effect on the
 * clip's contact frame), the kind's extras, an additive hurt flinch and the head / weapon tracking of the aim
 * target. Dying units play their destruction clip and then break apart into their own parts ({@link UltronVfx}).
 * The metal atlas is drawn first, then the emissive atlas (eyes, cores, vents: brighter in combat, flickering when
 * damaged, going dark as the unit dies). Anchor bones (hands, eyes, tools) are published to the entity for the
 * effects; at critical damage a weak part comes off. Client only.
 */
public abstract class UltronUnitRenderer<T extends UltronUnit> extends EntityRenderer<T> {
   public static final double GLOW_LOD = 128.0;
   public static final int A_RIGHT = 0;
   public static final int A_LEFT = 1;
   public static final int A_HEAD = 2;
   public static final int A_TOOL = 3;
   static final VertexConsumer NULL_SINK = new NullSink();

   protected final UltronAssets.Model assets;
   private final String[] anchorBones;
   private final Set<String> glowOnly;
   private final String[] nozzleBones;
   private final float[][] nozzleAxes;
   @Nullable
   private final String weakPart;
   private final List<String> pieces;
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f[] anchorAt = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
   private final boolean[] anchorFound = new boolean[4];
   protected final Vector3f[] nozzleAt;
   protected final Vector3f[] nozzleDir;
   protected final boolean[] nozzleFound;
   @Nullable
   private Matrix4f weakMatrix;
   @Nullable
   private Map<String, Matrix4f> capture;
   private final SwingTrails swings = new SwingTrails();

   /**
    * @param anchors  bone names for {@link #A_RIGHT}, {@link #A_LEFT}, {@link #A_HEAD}, {@link #A_TOOL} (null: unused)
    * @param glowOnly bones drawn only in the emissive pass (flames, thruster plates)
    * @param nozzles  bones whose pivot and axis are captured each frame (flame cones), with {@code axes} (model px)
    * @param weakPart the part that comes off at critical damage (null: none)
    * @param pieces   the parts the body breaks into at the end of its destruction clip
    */
   protected UltronUnitRenderer(EntityRendererProvider.Context context, UltronAssets.Model assets, float shadow, String[] anchors, String[] glowOnly,
      String[] nozzles, float[][] axes, @Nullable String weakPart, List<String> pieces) {
      super(context);
      this.shadowRadius = shadow;
      this.assets = assets;
      this.anchorBones = Arrays.copyOf(anchors, 4);
      this.glowOnly = Set.of(glowOnly);
      this.nozzleBones = nozzles;
      this.nozzleAxes = axes;
      this.weakPart = weakPart;
      this.pieces = List.copyOf(pieces);
      this.nozzleAt = new Vector3f[nozzles.length];
      this.nozzleDir = new Vector3f[nozzles.length];
      this.nozzleFound = new boolean[nozzles.length];
      for (int i = 0; i < nozzles.length; i++) {
         this.nozzleAt[i] = new Vector3f();
         this.nozzleDir[i] = new Vector3f();
      }
   }

   @Override
   public ResourceLocation getTextureLocation(T entity) {
      return this.assets.texture();
   }

   /** The parts the body breaks into (bone names: each takes its sub-tree up to the next listed part). */
   public List<String> pieces() {
      return this.pieces;
   }

   public UltronAssets.Model assets() {
      return this.assets;
   }

   @Override
   public void render(T e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      if (e.clientShattered) {
         return;
      }
      BakedGeoModel model = AnimCache.model(this.assets.geo());
      if (model == null) {
         return;
      }
      float age = e.tickCount + partial;
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      this.pose(model, e, partial, age, dying);
      boolean shadow = ShaderCompat.isShadowPass();
      this.setGlowOnlyHidden(model, true);
      if (e.clientPartLost && this.weakPart != null) {
         hideTree(model.getBone(this.weakPart), true);
      }
      this.inverse.set(stack.last().pose()).invert();
      Arrays.fill(this.anchorFound, false);
      Arrays.fill(this.nozzleFound, false);
      this.weakMatrix = null;
      stack.pushPose();
      this.transform(stack, e, partial, age);
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0 && e.deathTime <= 0));
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(this.assets.texture())), light, overlay, 1.0F, 1.0F, 1.0F,
         1.0F, shadow ? null : this::hook);
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      if (!shadow && dist <= GLOW_LOD) {
         float g = this.glow(e, age, dying);
         if (g > 0.01F) {
            this.setGlowOnlyHidden(model, false);
            if (e.clientPartLost && this.weakPart != null) {
               hideTree(model.getBone(this.weakPart), true);
            }
            this.glowPose(model, e, partial, age);
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(this.assets.glow())), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
               g, g, g, 1.0F, null);
         }
      }
      stack.popPose();
      if (!shadow) {
         double x = Mth.lerp(partial, e.xo, e.getX());
         double y = Mth.lerp(partial, e.yo, e.getY());
         double z = Mth.lerp(partial, e.zo, e.getZ());
         for (int i = 0; i < 4; i++) {
            if (this.anchorFound[i]) {
               e.clientAnchors[i] = new Vec3(x + this.anchorAt[i].x, y + this.anchorAt[i].y, z + this.anchorAt[i].z);
            }
         }
         e.clientAnchorTick = e.tickCount;
         if (this.weakPart != null && this.weakMatrix != null && !e.clientPartLost && e.isAlive() && e.flag(UltronUnit.FLAG_CRITICAL)) {
            e.clientPartLost = true;
            UltronVfx.partLost(e, this.assets, this.weakPart, this.weakMatrix, new Vec3(x, y, z));
         }
         if (dist <= GLOW_LOD) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            this.extras(e, partial, age, dying, stack, vc);
            if (dying < 0.0F && e.flag(UltronUnit.FLAG_DEFLECTOR)) {
               this.deflector(e, partial, age, stack, vc);
            }
         }
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void setGlowOnlyHidden(BakedGeoModel model, boolean hidden) {
      for (String name : this.glowOnly) {
         GeoBone bone = model.getBone(name);
         if (bone != null) {
            bone.hidden = hidden;
         }
      }
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      String name = bone.name;
      for (int i = 0; i < 4; i++) {
         if (!this.anchorFound[i] && name.equals(this.anchorBones[i])) {
            this.toLocal(bone, stack, 0.0F, 0.0F, 0.0F, this.anchorAt[i]);
            this.anchorFound[i] = true;
         }
      }
      for (int i = 0; i < this.nozzleBones.length; i++) {
         if (!this.nozzleFound[i] && name.equals(this.nozzleBones[i])) {
            float[] ax = this.nozzleAxes[i];
            this.toLocal(bone, stack, 0.0F, 0.0F, 0.0F, this.nozzleAt[i]);
            this.toLocal(bone, stack, ax[0], ax[1], ax[2], this.nozzleDir[i]);
            this.nozzleDir[i].sub(this.nozzleAt[i]);
            if (this.nozzleDir[i].lengthSquared() > 1.0E-8F) {
               this.nozzleDir[i].normalize();
            }
            this.nozzleFound[i] = true;
         }
      }
      if (name.equals(this.weakPart)) {
         this.weakMatrix = new Matrix4f(this.inverse).mul(stack.last().pose());
      }
      if (this.capture != null && this.pieces.contains(name)) {
         this.capture.put(name, new Matrix4f(this.inverse).mul(stack.last().pose()));
      }
   }

   /** A point in the bone's frame (model px from its pivot) in entity-relative world space. */
   private void toLocal(GeoBone bone, PoseStack stack, float dx, float dy, float dz, Vector3f out) {
      this.point.set((bone.pivotX + dx) / 16.0F, (bone.pivotY + dy) / 16.0F, (bone.pivotZ + dz) / 16.0F, 1.0F);
      stack.last().pose().transform(this.point);
      this.inverse.transform(this.point);
      out.set(this.point.x, this.point.y, this.point.z);
   }

   /**
    * Bone matrices of the pieces (entity-relative, body orientation included) at the current frame of the
    * destruction clip: where each part sits when the body breaks apart.
    */
   Map<String, Matrix4f> captureBreakup(T e) {
      BakedGeoModel model = AnimCache.model(this.assets.geo());
      if (model == null) {
         return Map.of();
      }
      float age = e.tickCount + 1.0F;
      this.pose(model, e, 1.0F, age, Math.max(0.01F, (float)e.deathTime));
      Map<String, Matrix4f> out = new HashMap<>();
      PoseStack stack = new PoseStack();
      this.inverse.identity();
      this.capture = out;
      try {
         this.transform(stack, e, 1.0F, age);
         AnimRenderer.render(model, stack, null, NULL_SINK, 0, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, this::hook);
      } finally {
         this.capture = null;
      }
      return out;
   }

   // ------------------------------------------------------------------ posing

   protected Animation clip(String name) {
      return AnimCache.animation(this.assets.anim(), name);
   }

   /** The clip a move plays (a kind may name its reaction differently). */
   protected String clipFor(UltronMove move) {
      return move.clip;
   }

   /** Moves whose clip starts away from the rest pose (assembly): no blend in from the current pose. */
   protected boolean snapsIn(UltronMove move) {
      return move == UltronMove.FABRICATE || move == UltronMove.PRIME_ASSEMBLE;
   }

   /** Full pose for this frame (see the class comment for the layering). */
   protected void pose(BakedGeoModel model, T e, float partial, float age, float dying) {
      model.resetBones();
      if (dying >= 0.0F) {
         this.base(model, e, partial, age, 0.0F, 0.0F);
         Animation death = this.clip("death");
         if (death != null) {
            over(death, model, Math.min(dying / 20.0F, death.lengthSeconds()), smooth(dying / 2.0F));
         }
         this.deathPose(model, e, partial, age, dying);
         return;
      }
      float speed = Mth.lerp(partial, e.clientSpeedO, e.clientSpeed);
      float combat = Mth.lerp(partial, e.clientCombatO, e.clientCombat);
      this.base(model, e, partial, age, speed, combat);
      this.leans(model, e, partial);
      this.move(model, e, partial);
      this.extraPose(model, e, partial, age);
      Animation hurt = this.clip("hurt");
      if (hurt != null && e.hurtTime > 0) {
         AnimationController.additive(hurt, model, Math.min(hurt.lengthSeconds(), (10 - e.hurtTime + partial) / 20.0F), 1.0F);
      }
      this.look(model, e, Mth.lerp(partial, e.clientLookYawO, e.clientLookYaw), Mth.lerp(partial, e.clientLookPitchO, e.clientLookPitch));
   }

   /** Ground stance and gait: idle, combat stance, walk / run on the gait phase, fall and landing. */
   protected void base(BakedGeoModel model, T e, float partial, float age, float speed, float combat) {
      Animation idle = this.clip("idle");
      if (idle != null) {
         over(idle, model, loop(age / 20.0F + e.getId() * 0.37F, idle), 1.0F);
      }
      Animation alert = this.clip("combat_idle");
      if (alert != null && combat > 0.01F) {
         over(alert, model, loop(age / 20.0F + e.getId() * 0.21F, alert), combat);
      }
      float walkW = smooth((speed - 0.012F) / 0.045F);
      float runW = smooth((speed - 0.13F) / 0.12F);
      double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
      double phase = gait - Math.floor(gait);
      Animation walk = this.clip("walk");
      Animation run = this.clip("run");
      if (walk != null && walkW > 0.01F) {
         over(walk, model, phase * walk.lengthSeconds(), walkW);
      }
      if (run != null && walkW * runW > 0.01F) {
         over(run, model, phase * run.lengthSeconds(), walkW * runW);
      }
      float air = Mth.clamp((e.clientAirTicks + partial - 3.0F) / 5.0F, 0.0F, 1.0F);
      Animation fall = this.clip("fall");
      if (fall != null && air > 0.01F) {
         over(fall, model, loop(age / 20.0F, fall), air);
      }
      Animation land = this.clip("land");
      float since = age - e.clientLandStart;
      if (land != null && since >= 0.0F && since < land.lengthSeconds() * 20.0F && e.clientMove != UltronMove.LAND) {
         double sec = since / 20.0;
         over(land, model, sec, smooth((float)((land.lengthSeconds() - sec) / 0.15)));
      }
   }

   /** Flyer stance: the hover loop, the flight loop by speed. */
   protected void hover(BakedGeoModel model, T e, float partial, float age, float speed) {
      Animation hover = this.clip("hover");
      if (hover != null) {
         over(hover, model, loop(age / 20.0F + e.getId() * 0.29F, hover), 1.0F);
      }
      Animation fly = this.clip("fly");
      float flyW = smooth((speed - 0.05F) / 0.22F);
      if (fly != null && flyW > 0.01F) {
         over(fly, model, loop(age / 20.0F + e.getId() * 0.13F, fly), flyW);
      }
   }

   /** Additive acceleration / braking and turn leans. */
   protected void leans(BakedGeoModel model, T e, float partial) {
      float accel = Mth.lerp(partial, e.clientAccelO, e.clientAccel);
      Animation a = this.clip("accel");
      Animation d = this.clip("decel");
      if (a != null && accel > 0.02F) {
         AnimationController.additive(a, model, 0.0, Mth.clamp(accel / 0.3F, 0.0F, 1.0F));
      }
      if (d != null && accel < -0.02F) {
         AnimationController.additive(d, model, 0.0, Mth.clamp(-accel / 0.3F, 0.0F, 1.0F));
      }
      float turn = Mth.lerp(partial, e.clientTurnO, e.clientTurn);
      Animation t = this.clip(turn > 0.0F ? "turn_r" : "turn_l");
      if (t != null && Math.abs(turn) > 0.3F) {
         AnimationController.additive(t, model, 0.0, Mth.clamp(Math.abs(turn) / 10.0F, 0.0F, 1.0F));
      }
   }

   /** The running timed move, blended in from the current pose and back out at its end. */
   protected void move(BakedGeoModel model, T e, float partial) {
      float t = e.clientMoveSeconds(partial);
      if (t < 0.0F) {
         return;
      }
      Animation clip = this.clip(this.clipFor(e.clientMove));
      if (clip == null) {
         return;
      }
      float len = (float)clip.lengthSeconds();
      float in = this.snapsIn(e.clientMove) ? 1.0F : smooth(t / 0.08F);
      over(clip, model, Math.min(t, len), in * smooth((len - t) / 0.12F));
   }

   /** Kind extras over the move (rotors, shield hold, welding, the face while talking). */
   protected void extraPose(BakedGeoModel model, T e, float partial, float age) {
   }

   /** Extras while the destruction clip plays. */
   protected void deathPose(BakedGeoModel model, T e, float partial, float age, float dying) {
   }

   /** The emissive pass only (flame lengths). */
   protected void glowPose(BakedGeoModel model, T e, float partial, float age) {
   }

   /** Head / weapon tracking of the aim (default: humanoid chest, neck, head). */
   protected void look(BakedGeoModel model, T e, float yaw, float pitch) {
      deg(model.getBone("chest"), pitch * 0.15F, yaw * 0.2F, 0.0F);
      deg(model.getBone("neck"), pitch * 0.3F, yaw * 0.3F, 0.0F);
      deg(model.getBone("head"), pitch * 0.5F, yaw * 0.5F, 0.0F);
   }

   /** Body orientation (default: the body yaw). */
   protected void transform(PoseStack stack, T e, float partial, float age) {
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
   }

   /** Flyer attitude: banks into turns, pitches with the climb and the acceleration (about a centre height in px). */
   protected void attitude(PoseStack stack, T e, float partial, float centrePx, float bankScale, float pitchScale) {
      float turn = Mth.lerp(partial, e.clientTurnO, e.clientTurn);
      float accel = Mth.lerp(partial, e.clientAccelO, e.clientAccel);
      double vy = Mth.lerp(partial, e.yOld - e.yo, e.getY() - e.yo);
      float bank = Mth.clamp(-turn * 2.4F * bankScale, -38.0F, 38.0F);
      float pitch = Mth.clamp((float)(e.getY() - e.yo) * 60.0F * pitchScale - accel * 18.0F * pitchScale, -35.0F, 30.0F);
      if (vy != vy) {
         pitch = 0.0F;
      }
      float c = centrePx / 16.0F;
      stack.translate(0.0F, c, 0.0F);
      stack.mulPose(Axis.ZP.rotationDegrees(bank));
      stack.mulPose(Axis.XP.rotationDegrees(pitch));
      stack.translate(0.0F, -c, 0.0F);
   }

   /** Emissive strength: a slow pulse, brighter in combat, flickering when damaged, dark as it dies. */
   protected float glow(T e, float age, float dying) {
      float combat = e.clientCombat;
      float g = 0.8F + 0.08F * Mth.sin(age * 0.12F + e.getId()) + 0.15F * combat;
      int tick = (int)(age * 1.3F) + e.getId() * 7;
      if (e.flag(UltronUnit.FLAG_CRITICAL)) {
         g *= tick % 7 == 0 || tick % 11 == 0 ? 0.2F : 1.0F;
      } else if (e.flag(UltronUnit.FLAG_DAMAGED)) {
         g *= tick % 17 == 0 ? 0.45F : 1.0F;
      }
      if (dying >= 0.0F) {
         float flicker = ((int)(age * 1.7F) + e.getId() & 3) == 0 ? 0.3F : 1.0F;
         g *= (1.0F - smooth(dying / Math.max(1.0F, e.deathTicks() * 0.85F))) * flicker;
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }

   /** Additive extras around the model (flames, shields, charges), entity-relative, in {@code RenderType.lightning}. */
   protected void extras(T e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
   }

   /** Frontal deflector of the adaptation protocol: a faint red hexagon shimmer that flares on hits. */
   private void deflector(T e, float partial, float age, PoseStack stack, VertexConsumer vc) {
      float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
      Vector3f fwd = new Vector3f(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
      Vector3f right = new Vector3f(-Mth.cos(yaw), 0.0F, -Mth.sin(yaw));
      Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
      float h = e.getBbHeight();
      float r = Math.max(0.5F, h * 0.42F);
      Vector3f c = new Vector3f(fwd).mul(e.getBbWidth() * 0.6F + 0.35F).add(0.0F, h * 0.55F, 0.0F);
      float hit = e.hurtTime > 0 ? e.hurtTime / 10.0F : 0.0F;
      int a = (int)(26 + 10 * Mth.sin(age * 0.4F) + 150 * hit);
      Matrix4f m = stack.last().pose();
      GlowDraw.ring(vc, m, c, right, up, r * 0.86F, r, 0.6F, 6, 255, 60, 40, a);
      GlowDraw.ring(vc, m, c, right, up, r * 0.42F, r * 0.5F, 0.6F, 6, 255, 90, 60, a / 2);
      if (hit > 0.0F) {
         GlowDraw.disc(vc, m, c, right, up, r * 0.95F, 0.2F, 6, 255, 70, 50, (int)(70 * hit));
      }
   }

   // ------------------------------------------------------------------ helpers

   /** Entity-relative position of an anchor bone rendered this frame, or null. */
   @Nullable
   protected Vector3f anchor(int which) {
      return this.anchorFound[which] ? this.anchorAt[which] : null;
   }

   /** A world position relative to the entity's interpolated position (the extras' frame). */
   protected static Vector3f rel(Entity e, float partial, Vec3 world) {
      return new Vector3f((float)(world.x - Mth.lerp(partial, e.xo, e.getX())), (float)(world.y - Mth.lerp(partial, e.yo, e.getY())),
         (float)(world.z - Mth.lerp(partial, e.zo, e.getZ())));
   }

   /** From an entity-relative point toward the camera (ribbon orientation). */
   protected Vector3f toCam(Entity e, float partial, Vector3f p) {
      Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
      return new Vector3f((float)(cam.x - Mth.lerp(partial, e.xo, e.getX())) - p.x, (float)(cam.y - Mth.lerp(partial, e.yo, e.getY())) - p.y,
         (float)(cam.z - Mth.lerp(partial, e.zo, e.getZ())) - p.z);
   }

   protected Vector3f camLeft() {
      return this.entityRenderDispatcher.camera.getLeftVector();
   }

   protected Vector3f camUp() {
      return this.entityRenderDispatcher.camera.getUpVector();
   }

   /** Motion trail of an anchor (a striking hand) while {@code on}; the last {@code keep} ticks of its path fade out. */
   protected void swing(T e, int which, float partial, float age, PoseStack stack, VertexConsumer vc, boolean on, float keep, float width, int r, int g,
      int b) {
      Vector3f a = this.anchor(which);
      if (a == null) {
         return;
      }
      Vec3 world = new Vec3(Mth.lerp(partial, e.xo, e.getX()) + a.x, Mth.lerp(partial, e.yo, e.getY()) + a.y, Mth.lerp(partial, e.zo, e.getZ()) + a.z);
      this.swings.draw(e, which, partial, age, stack.last().pose(), vc, this.toCam(e, partial, a), world, on, keep, width, r, g, b);
   }

   /** A soft camera-facing glow (charge orbs, eye flares) at an entity-relative point. */
   protected void orb(VertexConsumer vc, Matrix4f m, Vector3f c, float radius, float core, int r, int g, int b, int a) {
      GlowDraw.billboard(vc, m, c, this.camLeft(), this.camUp(), radius, core, r, g, b, GlowDraw.clamp(a));
   }

   /**
    * A thruster flame from {@code at} along the unit direction {@code dir}: a red-orange sheath, an orange body and a
    * white-hot core, crossed ribbons so it reads from every side, and the glow at the nozzle.
    */
   protected void flame(T e, float partial, VertexConsumer vc, Matrix4f m, Vector3f at, Vector3f dir, float length, float width, float strength,
      float age) {
      if (length <= 0.01F || strength <= 0.01F) {
         return;
      }
      float flicker = 0.85F + 0.15F * Mth.sin(age * 2.7F + at.x * 13.0F) * Mth.sin(age * 1.9F + at.z * 7.0F);
      float len = length * flicker;
      Vector3f tip = new Vector3f(dir).mul(len).add(at);
      Vector3f mid = new Vector3f(dir).mul(len * 0.45F).add(at);
      Vector3f cam = this.toCam(e, partial, at);
      Vector3f side = new Vector3f(dir).cross(cam);
      if (side.lengthSquared() < 1.0E-6F) {
         side.set(1.0F, 0.0F, 0.0F);
      }
      int a = (int)(255 * strength);
      GlowDraw.ribbon(vc, m, at, tip, cam, width * 1.6F, width * 0.3F, 255, 60, 30, a / 3, 0);
      GlowDraw.ribbon(vc, m, at, tip, side.normalize(), width * 1.3F, width * 0.2F, 255, 70, 35, a / 4, 0);
      GlowDraw.ribbon(vc, m, at, mid, cam, width, width * 0.4F, 255, 150, 60, (int)(a * 0.7F), a / 5);
      GlowDraw.ribbon(vc, m, at, mid, cam, width * 0.42F, width * 0.12F, 255, 240, 210, a, a / 4);
      this.orb(vc, m, at, width * 2.2F, 0.3F, 255, 90, 50, (int)(a * 0.5F));
   }

   /** Two unit vectors perpendicular to {@code n} (and to each other). */
   protected static void basis(Vector3f n, Vector3f a, Vector3f b) {
      Vector3f ref = Math.abs(n.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
      n.cross(ref, a).normalize();
      n.cross(a, b).normalize();
   }

   /** Horizontal unit vector the body faces this frame. */
   protected static Vector3f facing(UltronUnit e, float partial) {
      float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
      return new Vector3f(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
   }

   /** Hides (or shows) a bone with its whole sub-tree ({@code hidden} alone only skips the bone's own cubes). */
   public static void hideTree(@Nullable GeoBone bone, boolean hidden) {
      if (bone != null) {
         bone.hidden = hidden;
         for (GeoBone child : bone.children) {
            hideTree(child, hidden);
         }
      }
   }

   /**
    * The clip's bones blended from their current pose by weight (as {@link AnimationController#overlay}, without
    * the snapshot of every bone the weighted variant allocates each call).
    */
   public static void over(@Nullable Animation clip, BakedGeoModel model, double t, float w) {
      if (clip == null || w <= 0.001F) {
         return;
      }
      if (w >= 0.999F) {
         AnimationController.overlay(clip, model, t);
         return;
      }
      for (Animation.BoneAnimation ba : clip.boneAnimations().values()) {
         GeoBone bone = model.getBone(ba.boneName());
         if (bone == null) {
            continue;
         }
         bone.rotX = Mth.lerp(w, bone.rotX, bone.initRotX + ba.rotX().sample(t));
         bone.rotY = Mth.lerp(w, bone.rotY, bone.initRotY + ba.rotY().sample(t));
         bone.rotZ = Mth.lerp(w, bone.rotZ, bone.initRotZ + ba.rotZ().sample(t));
         bone.posX = Mth.lerp(w, bone.posX, ba.posX().sample(t));
         bone.posY = Mth.lerp(w, bone.posY, ba.posY().sample(t));
         bone.posZ = Mth.lerp(w, bone.posZ, ba.posZ().sample(t));
         bone.scaleX = Mth.lerp(w, bone.scaleX, ba.scaleX().sample(t));
         bone.scaleY = Mth.lerp(w, bone.scaleY, ba.scaleY().sample(t));
         bone.scaleZ = Mth.lerp(w, bone.scaleZ, ba.scaleZ().sample(t));
      }
   }

   protected static double loop(float seconds, Animation clip) {
      float len = (float)Math.max(0.05, clip.lengthSeconds());
      double t = seconds % len;
      return t < 0.0 ? t + len : t;
   }

   /** Adds Blockbench-convention degrees (the clip convention) to a bone. */
   protected static void deg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.rotX += (float)Math.toRadians(-x);
         bone.rotY += (float)Math.toRadians(-y);
         bone.rotZ += (float)Math.toRadians(z);
      }
   }

   protected static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   /** Vertex sink for pose captures (nothing is drawn). */
   private static final class NullSink implements VertexConsumer {
      @Override
      public VertexConsumer vertex(double x, double y, double z) {
         return this;
      }

      @Override
      public VertexConsumer color(int r, int g, int b, int a) {
         return this;
      }

      @Override
      public VertexConsumer uv(float u, float v) {
         return this;
      }

      @Override
      public VertexConsumer overlayCoords(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer uv2(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer normal(float x, float y, float z) {
         return this;
      }

      @Override
      public void endVertex() {
      }

      @Override
      public void defaultColor(int r, int g, int b, int a) {
      }

      @Override
      public void unsetDefaultColor() {
      }
   }
}
