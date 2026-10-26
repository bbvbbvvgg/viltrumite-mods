package dev.baranhan.viltrumitecore.client.reinhard.reid;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.reinhard.ReidDrawChoreo;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardTitanBlade;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Visuals of the Reid draw scene (spec §7.3, REINHARD OVERHAUL item 15), on the light timeline of
 * {@link ReidDrawLight}. The column of light stands exactly on Reinhard: on his interpolated
 * position when this client tracks him (the packet centre otherwise), from the ground under his
 * feet to the clouds. It is three soft additive layers (halo, glow, core), each a camera-facing
 * ribbon with a gaussian-like falloff across its width, segmented in height for the soft foot and
 * tip, the rising pulses and the release flash that runs up the column. Around it: a ground glow
 * draped over the blocks under him, air ripples (the distortion of the air), rising motes, strain
 * dust, the light leaking from the scabbard mouth and along the exposed blade, the release ring
 * that sweeps the ground to the wave radius, dark ground cracks, a dark sky ({@link SkyTint}) and
 * the world hushed except the scene's own sounds ({@link SoundDuck}); a controlled camera shake.
 * The caster's own first-person view gets the column as a shaft over his head and a screen bloom
 * instead of geometry through the camera. Nothing outside the scene range; nothing in the shadow
 * pass of a shader pack.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidDrawSceneVfx {
   public static final int TINT_PRIORITY = 100;
   public static final SkyTint.Tint DARK = new SkyTint.Tint(0.25F, 0.28F, 0.39F, 0.88F, 0.0F);
   public static final float DUCK_TARGET = 0.25F;
   public static final int DUCK_FADE_TICKS = 20;
   /** The scene's own sounds, spared by its duck: the draw cues ({@link ReidDrawAudio}) and the distant echo. */
   public static final Set<ResourceLocation> OWN_SOUNDS = Set.of(
      new ResourceLocation("viltrumitecore", "reinhard.reid_draw_hum"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_draw_koiguchi"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_draw_scrape"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_draw_air"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_draw_release"),
      new ResourceLocation("viltrumitecore", "reinhard.reid_far"));
   private static final int CRACK_LINES = 14;
   private static final float CRACK_STEP = 0.5F;
   private static final float SKY_ABOVE_CLOUDS = 40.0F;
   /** Ground is searched this far below the feet; deeper, the column starts at his feet. */
   private static final int GROUND_SEARCH = 6;
   /** Across a ribbon: offsets (of the half width) and their light, a soft gaussian-like profile. */
   private static final float[] ACROSS = {-1.0F, -0.55F, -0.22F, 0.0F, 0.22F, 0.55F, 1.0F};
   private static final float[] PROFILE = {0.0F, 0.22F, 0.72F, 1.0F, 0.72F, 0.22F, 0.0F};
   /** Halo, glow, core: colour and peak alpha. */
   private static final int[][] LAYER_RGB = {{255, 202, 118}, {255, 234, 186}, {255, 255, 246}};
   private static final float[] LAYER_ALPHA = {0.16F, 0.36F, 0.9F};
   /** Narrowest half width per layer as a fraction of the distance: the column never thins below a pixel or two. */
   private static final float[] MIN_ANGULAR = {0.006F, 0.003F, 0.0016F};
   private static final float[] RIPPLE_TICKS = rippleSchedule();
   private static final int RIPPLE_LIFE = 18;
   private static final Set<Integer> PUSHED = new HashSet<>();
   private static final Set<Integer> DUCKED = new HashSet<>();
   private static ClientLevel lastLevel;

   private ReidDrawSceneVfx() {
   }

   private static Object owner(int casterId) {
      return "reid_draw:" + casterId;
   }

   static boolean inRange(Minecraft client, ReidDrawScene.Active scene) {
      return client.player != null && (client.player.getId() == scene.casterId()
         || client.player.position().distanceTo(scene.center()) <= ReinhardRules.REID_SCENE_RANGE);
   }

   /** The column's axis: the caster's interpolated position when this client tracks him, else the packet centre. */
   static Vec3 center(ClientLevel level, ReidDrawScene.Active scene, float partialTick) {
      Entity entity = level.getEntity(scene.casterId());
      return entity != null && !entity.isRemoved() ? entity.getPosition(partialTick) : scene.center();
   }

   /** Pure: draw ticks at which an air ripple leaves him (faster as the strain nears). */
   static float[] rippleSchedule() {
      List<Float> ticks = new ArrayList<>();
      for (float t = ReidDrawChoreo.KOIGUCHI; ReidDrawLight.ripples(t); t += ReidDrawLight.ripplePeriod(t)) {
         ticks.add(t);
      }

      float[] out = new float[ticks.size()];
      for (int i = 0; i < out.length; i++) {
         out[i] = ticks.get(i);
      }

      return out;
   }

   /** Pure: the dark sky weight at draw tick d, eased in over 1.5 s and out with the light's decay. */
   static float darkness(float d, float fade) {
      return Math.min(1.0F, d / 30.0F) * (1.0F - ReidDrawLight.smooth((d - 112.0F) / 16.0F)) * fade;
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         // Logout or dimension change: scenes of the old level end (SkyTint / SoundDuck clear themselves).
         lastLevel = level;
         ReidDrawScene.clear();
         PUSHED.clear();
         DUCKED.clear();
      }

      if (level == null || client.isPaused()) {
         return;
      }

      long now = level.getGameTime();
      ReidDrawScene.prune(now);
      Set<Integer> live = new HashSet<>();
      for (ReidDrawScene.Active scene : ReidDrawScene.all()) {
         if (!inRange(client, scene)) {
            continue;
         }

         int id = scene.casterId();
         live.add(id);
         float d = ReidDrawScene.tick(scene, now, 0.0F);
         float fade = ReidDrawScene.fade(scene, now, 0.0F);
         boolean own = client.player.getId() == id;
         float power = ReidDrawLight.shake(d) * fade * (own ? ReidDrawLight.CASTER_SHAKE : 1.0F);
         if (power > 0.01F) {
            CameraShake.cinematicAt(center(level, scene, 1.0F), power, ReinhardRules.REID_SCENE_RANGE);
         }

         if (PUSHED.add(id)) {
            SkyTint.INSTANCE.push(owner(id), TINT_PRIORITY, () -> {
               ClientLevel current = Minecraft.getInstance().level;
               ReidDrawScene.Active state = ReidDrawScene.get(id);
               if (current == null || state == null) {
                  return SkyTint.NONE;
               }

               float partial = Minecraft.getInstance().getFrameTime();
               return SkyTint.mix(SkyTint.NONE, DARK, darkness(ReidDrawScene.tick(state, current.getGameTime(), partial),
                  ReidDrawScene.fade(state, current.getGameTime(), partial)));
            });
         }

         // The world comes back as soon as the draw ends; only the scene's own tail keeps sounding.
         if (!scene.ending() && DUCKED.add(id)) {
            SoundDuck.INSTANCE.duck(owner(id), SoundSource.MASTER, DUCK_TARGET, DUCK_FADE_TICKS, OWN_SOUNDS);
         } else if (scene.ending() && DUCKED.remove(id)) {
            SoundDuck.INSTANCE.release(owner(id));
         }
      }

      PUSHED.removeIf(id -> {
         if (live.contains(id)) {
            return false;
         }

         SkyTint.INSTANCE.remove(owner(id));
         if (DUCKED.remove(id)) {
            SoundDuck.INSTANCE.release(owner(id));
         }
         return true;
      });
   }

   /** One scene as seen this frame. */
   private record View(ReidDrawScene.Active scene, float d, float fade, Vec3 center, double ground, double sky,
      @Nullable LivingEntity caster, boolean firstPerson, Map<Long, Double> grounds, ClientLevel level) {
      double groundAt(double x, double z) {
         return ReidDrawSceneVfx.groundAt(this.level, x, this.center.y, z, this.grounds);
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_PARTICLES || dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      long now = level.getGameTime();
      List<View> views = new ArrayList<>();
      for (ReidDrawScene.Active scene : ReidDrawScene.all()) {
         if (!inRange(client, scene)) {
            continue;
         }

         float fade = ReidDrawScene.fade(scene, now, partialTick);
         if (fade <= 0.002F) {
            continue;
         }

         Vec3 center = center(level, scene, partialTick);
         Map<Long, Double> grounds = new HashMap<>();
         double ground = groundAt(level, center.x, center.y, center.z, grounds);
         double foot = Double.isNaN(ground) ? center.y - 1.5 : ground;
         Entity entity = level.getEntity(scene.casterId());
         boolean firstPerson = client.player != null && client.player.getId() == scene.casterId() && client.options.getCameraType().isFirstPerson();
         views.add(new View(scene, ReidDrawScene.tick(scene, now, partialTick), fade, center, foot, skyHeight(level, center),
            entity instanceof LivingEntity living && !entity.isRemoved() ? living : null, firstPerson, grounds, level));
      }

      if (views.isEmpty()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 eye = camera.getPosition();
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.rotateCamera(modelViewStack, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder buffer = tesselator.getBuilder();
      try {
         // Cracks: dark, normal alpha blend (additive would hide them).
         RenderSystem.defaultBlendFunc();
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (View view : views) {
            cracks(buffer, eye, view);
            dust(buffer, eye, camera, view);
         }
         tesselator.end();

         // Light: additive.
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (View view : views) {
            groundGlow(buffer, eye, view);
            releaseRing(buffer, eye, view);
            ripples(buffer, eye, view);
            column(buffer, eye, view);
            motes(buffer, eye, camera, view);
            if (!view.firstPerson()) {
               blade(buffer, eye, camera, view, partialTick);
            }
         }
         tesselator.end();
      } finally {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static double skyHeight(ClientLevel level, Vec3 center) {
      float cloud = level.effects().getCloudHeight();
      double clouds = Float.isNaN(cloud) ? center.y + 100.0 : Math.max(cloud, center.y + 30.0);
      return Math.max(clouds + SKY_ABOVE_CLOUDS, center.y + 70.0);
   }

   /** Top of the ground under (x, z) near {@code feetY}: the first collision top at most one block above the feet,
    * searching {@link #GROUND_SEARCH} blocks down; NaN over a drop. Cached per column for one frame. */
   static double groundAt(ClientLevel level, double x, double feetY, double z, Map<Long, Double> cache) {
      int bx = Mth.floor(x), bz = Mth.floor(z);
      long key = BlockPos.asLong(bx, 0, bz);
      Double cached = cache.get(key);
      if (cached != null) {
         return cached;
      }

      double found = Double.NaN;
      int top = Mth.floor(feetY + 1.0);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int y = top; y >= top - GROUND_SEARCH - 1; y--) {
         pos.set(bx, y, bz);
         VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
         if (!shape.isEmpty()) {
            double h = y + shape.max(Direction.Axis.Y);
            if (h <= feetY + 1.01) {
               found = h;
               break;
            }
         }
      }

      cache.put(key, found);
      return found;
   }

   /** Light at height h (blocks above the foot) of a column of height H: soft foot and tip, rising pulses, the release flash. */
   static float along(double h, double height, float d) {
      float foot = ReidDrawLight.smooth(h / 0.6);
      double tipLength = Math.max(1.0, Math.min(24.0, height * 0.3));
      float tip = 1.0F - ReidDrawLight.smooth((h - (height - tipLength)) / tipLength);
      float fall = (float)(0.5 + 0.5 * Math.exp(-h / 36.0));
      // The pulses climb faster in the strain; their travel stays continuous, so the pattern never jumps.
      double strain = ReidDrawChoreo.STRAIN;
      double travel = d < strain ? d * 0.55 : strain * 0.55 + (d - strain) * 1.1;
      double wave = (h - travel) / 11.0;
      float pulses = 1.0F + 0.28F * (float)Math.pow(0.5 + 0.5 * Math.cos(wave * Math.PI * 2.0), 3.0);
      float flash = 0.0F;
      if (d >= ReidDrawLight.RELEASE - ReidDrawLight.ATTACK) {
         double front = Math.max(0.0, d - ReidDrawLight.RELEASE) * 9.0;
         flash = 2.2F * ReidDrawLight.attack(d) * (float)Math.exp(-Math.pow((h - front) / 8.0, 2.0)) * ReidDrawLight.tail(d);
      }

      return foot * tip * (fall * pulses + flash);
   }

   private static void column(BufferBuilder buffer, Vec3 eye, View view) {
      float beam = Math.min(1.6F, ReidDrawLight.beam(view.d())) * view.fade();
      float reach = ReidDrawLight.reach(view.d());
      if (beam <= 0.004F || reach <= 0.0F) {
         return;
      }

      double bottom = view.ground();
      double height = reach * (view.sky() - bottom);
      float[] widths = ReidDrawLight.widths(view.d());
      double dx = eye.x - view.center().x, dz = eye.z - view.center().z;
      double distance = Math.sqrt(dx * dx + dz * dz);
      if (view.firstPerson() || distance < Math.max(0.35, widths[1] * 0.8)) {
         // Inside the light: a shaft over the head, never a ribbon through the camera.
         shaft(buffer, eye, view, bottom, height, widths, beam);
         return;
      }

      float sx = (float)(-dz / distance), sz = (float)(dx / distance);
      double[] heights = segments(height);
      for (int layer = 0; layer < 3; layer++) {
         float half = Math.max(widths[layer], MIN_ANGULAR[layer] * (float)distance);
         float alpha = LAYER_ALPHA[layer] * Math.min(1.0F, beam) * (float)Math.sqrt(widths[layer] / half)
            * (1.0F + 0.35F * Math.max(0.0F, beam - 1.0F));
         int[] rgb = LAYER_RGB[layer];
         for (int i = 0; i + 1 < heights.length; i++) {
            float a0 = alpha * along(heights[i], height, view.d());
            float a1 = alpha * along(heights[i + 1], height, view.d());
            if (a0 <= 0.002F && a1 <= 0.002F) {
               continue;
            }

            ribbon(buffer, eye, view.center().x, bottom + heights[i], bottom + heights[i + 1], view.center().z, sx, sz, half, rgb, a0, a1);
         }
      }
   }

   /** Heights of the column's segments: dense by the ground and the body, coarse in the sky. */
   static double[] segments(double height) {
      List<Double> out = new ArrayList<>();
      double h = 0.0;
      out.add(0.0);
      while (h < height) {
         h += h < 1.0 ? 0.5 : h < 60.0 ? 3.0 : 9.0;
         out.add(Math.min(h, height));
      }

      double[] array = new double[out.size()];
      for (int i = 0; i < array.length; i++) {
         array[i] = out.get(i);
      }

      return array;
   }

   private static void ribbon(BufferBuilder buffer, Vec3 eye, double x, double y0, double y1, double z, float sx, float sz,
      float half, int[] rgb, float a0, float a1) {
      float cx = (float)(x - eye.x), cz = (float)(z - eye.z);
      float b0 = (float)(y0 - eye.y), b1 = (float)(y1 - eye.y);
      for (int k = 0; k + 1 < ACROSS.length; k++) {
         float u0 = ACROSS[k] * half, u1 = ACROSS[k + 1] * half;
         int p0 = alpha(a0 * PROFILE[k]), p1 = alpha(a0 * PROFILE[k + 1]);
         int q0 = alpha(a1 * PROFILE[k]), q1 = alpha(a1 * PROFILE[k + 1]);
         if (p0 + p1 + q0 + q1 == 0) {
            continue;
         }

         buffer.vertex(cx + sx * u0, b0, cz + sz * u0).color(rgb[0], rgb[1], rgb[2], p0).endVertex();
         buffer.vertex(cx + sx * u1, b0, cz + sz * u1).color(rgb[0], rgb[1], rgb[2], p1).endVertex();
         buffer.vertex(cx + sx * u1, b1, cz + sz * u1).color(rgb[0], rgb[1], rgb[2], q1).endVertex();
         buffer.vertex(cx + sx * u0, b1, cz + sz * u0).color(rgb[0], rgb[1], rgb[2], q0).endVertex();
      }
   }

   /** The column seen from inside: two open tubes from above the head into the sky. */
   private static void shaft(BufferBuilder buffer, Vec3 eye, View view, double bottom, double height, float[] widths, float beam) {
      double start = Math.max(bottom, eye.y + 1.4) - bottom;
      if (start >= height) {
         return;
      }

      double[] heights = segments(height);
      for (int layer = 1; layer < 3; layer++) {
         float radius = layer == 1 ? Math.max(0.6F, widths[1] * 1.3F) : Math.max(0.25F, widths[2] * 2.0F);
         float alpha = LAYER_ALPHA[layer] * 0.45F * Math.min(1.0F, beam);
         int[] rgb = LAYER_RGB[layer];
         for (int i = 0; i + 1 < heights.length; i++) {
            double h0 = Math.max(start, heights[i]), h1 = heights[i + 1];
            if (h1 <= h0) {
               continue;
            }

            float entry0 = ReidDrawLight.smooth((h0 - start) / 3.0), entry1 = ReidDrawLight.smooth((h1 - start) / 3.0);
            int a0 = alpha(alpha * entry0 * along(h0, height, view.d())), a1 = alpha(alpha * entry1 * along(h1, height, view.d()));
            if (a0 + a1 == 0) {
               continue;
            }

            float y0 = (float)(bottom + h0 - eye.y), y1 = (float)(bottom + h1 - eye.y);
            for (int s = 0; s < 14; s++) {
               double t0 = Math.PI * 2.0 * s / 14, t1 = Math.PI * 2.0 * (s + 1) / 14;
               float x0 = (float)(view.center().x - eye.x + Math.cos(t0) * radius), z0 = (float)(view.center().z - eye.z + Math.sin(t0) * radius);
               float x1 = (float)(view.center().x - eye.x + Math.cos(t1) * radius), z1 = (float)(view.center().z - eye.z + Math.sin(t1) * radius);
               buffer.vertex(x0, y0, z0).color(rgb[0], rgb[1], rgb[2], a0).endVertex();
               buffer.vertex(x1, y0, z1).color(rgb[0], rgb[1], rgb[2], a0).endVertex();
               buffer.vertex(x1, y1, z1).color(rgb[0], rgb[1], rgb[2], a1).endVertex();
               buffer.vertex(x0, y1, z0).color(rgb[0], rgb[1], rgb[2], a1).endVertex();
            }
         }
      }
   }

   /** A soft glow draped over the ground under him, wider at the release. */
   private static void groundGlow(BufferBuilder buffer, Vec3 eye, View view) {
      float beam = Math.min(1.0F, ReidDrawLight.beam(view.d())) * view.fade();
      float flash = ReidDrawLight.flash(view.d()) * view.fade();
      float peak = 0.42F * beam + 0.35F * flash;
      if (peak <= 0.004F) {
         return;
      }

      float radius = 1.4F + 2.6F * beam + 5.0F * flash;
      drapedAnnulus(buffer, eye, view, 0.0F, radius, 4, 20, peak, 255, 222, 150, true);
   }

   /** The ring of light that sweeps the ground at the release, out to the wave radius. */
   private static void releaseRing(BufferBuilder buffer, Vec3 eye, View view) {
      float r = ReidDrawLight.wave(view.d());
      if (r <= 0.0F) {
         return;
      }

      float k = r / ReidDrawLight.WAVE_RADIUS;
      float alpha = 0.75F * (float)Math.pow(1.0F - k, 1.2) * view.fade();
      float width = 0.7F + 0.09F * r;
      drapedAnnulus(buffer, eye, view, Math.max(0.0F, r - width), r + width, 2, Math.max(24, (int)(r * 6.0F)), alpha, 255, 238, 196, false);
   }

   /** Rings × segments of quads on the ground; {@code disc}: light falls off from the centre, else it peaks mid-ring. */
   private static void drapedAnnulus(BufferBuilder buffer, Vec3 eye, View view, float inner, float outer, int rings, int segments,
      float peak, int r, int g, int b, boolean disc) {
      double cx = view.center().x, cz = view.center().z;
      for (int ring = 0; ring < rings; ring++) {
         float ra = inner + (outer - inner) * ring / rings, rb = inner + (outer - inner) * (ring + 1) / rings;
         float la = peak * profile(ring / (float)rings, disc), lb = peak * profile((ring + 1) / (float)rings, disc);
         for (int s = 0; s < segments; s++) {
            double t0 = Math.PI * 2.0 * s / segments, t1 = Math.PI * 2.0 * (s + 1) / segments;
            double[][] p = {{cx + Math.cos(t0) * ra, cz + Math.sin(t0) * ra}, {cx + Math.cos(t1) * ra, cz + Math.sin(t1) * ra},
               {cx + Math.cos(t1) * rb, cz + Math.sin(t1) * rb}, {cx + Math.cos(t0) * rb, cz + Math.sin(t0) * rb}};
            float[] light = {la, la, lb, lb};
            double[] ys = new double[4];
            boolean ok = true;
            for (int i = 0; i < 4 && ok; i++) {
               ys[i] = view.groundAt(p[i][0], p[i][1]);
               ok = !Double.isNaN(ys[i]);
            }

            if (!ok || la + lb <= 0.004F) {
               continue;
            }

            for (int i = 0; i < 4; i++) {
               buffer.vertex((float)(p[i][0] - eye.x), (float)(ys[i] + 0.03 - eye.y), (float)(p[i][1] - eye.z)).color(r, g, b, alpha(light[i])).endVertex();
            }
         }
      }
   }

   private static float profile(float u, boolean disc) {
      if (disc) {
         float k = 1.0F - u * u;
         return k * k;
      }

      return (float)Math.sin(Math.PI * u);
   }

   /** Rings of disturbed air leaving his body, faster as the strain nears. */
   private static void ripples(BufferBuilder buffer, Vec3 eye, View view) {
      float build = ReidDrawLight.build(view.d()) * view.fade();
      if (build <= 0.004F) {
         return;
      }

      for (float emitted : RIPPLE_TICKS) {
         float age = view.d() - emitted;
         if (age < 0.0F || age > RIPPLE_LIFE) {
            continue;
         }

         float k = age / RIPPLE_LIFE;
         float radius = 0.6F + 0.36F * age;
         float alpha = 0.2F * (1.0F - k) * (1.0F - k) * Math.min(1.0F, build * 1.6F);
         double y = view.center().y + 1.0 + 0.05 * age;
         ring(buffer, eye, view.center().x, y, view.center().z, radius, 0.12F + 0.03F * age, 40, alpha, 236, 244, 255);
      }
   }

   /** A soft horizontal ring (light peaks mid-width). */
   private static void ring(BufferBuilder buffer, Vec3 eye, double x, double y, double z, float radius, float width, int segments,
      float alpha, int r, int g, int b) {
      float cy = (float)(y - eye.y);
      float[] rs = {radius - width, radius, radius + width};
      int[] as = {0, alpha(alpha), 0};
      for (int s = 0; s < segments; s++) {
         double t0 = Math.PI * 2.0 * s / segments, t1 = Math.PI * 2.0 * (s + 1) / segments;
         for (int band = 0; band < 2; band++) {
            float ra = Math.max(0.0F, rs[band]), rb = rs[band + 1];
            buffer.vertex((float)(x - eye.x + Math.cos(t0) * ra), cy, (float)(z - eye.z + Math.sin(t0) * ra)).color(r, g, b, as[band]).endVertex();
            buffer.vertex((float)(x - eye.x + Math.cos(t1) * ra), cy, (float)(z - eye.z + Math.sin(t1) * ra)).color(r, g, b, as[band]).endVertex();
            buffer.vertex((float)(x - eye.x + Math.cos(t1) * rb), cy, (float)(z - eye.z + Math.sin(t1) * rb)).color(r, g, b, as[band + 1]).endVertex();
            buffer.vertex((float)(x - eye.x + Math.cos(t0) * rb), cy, (float)(z - eye.z + Math.sin(t0) * rb)).color(r, g, b, as[band + 1]).endVertex();
         }
      }
   }

   /** Rising motes around the column; a burst outward at the release. Stateless: positions are a function of time. */
   private static void motes(BufferBuilder buffer, Vec3 eye, Camera camera, View view) {
      float beam = Math.min(1.0F, ReidDrawLight.beam(view.d())) * view.fade();
      int seed = Long.hashCode(view.scene().startGameTime()) * 31 + view.scene().casterId();
      int count = (int)(40 * beam);
      for (int i = 0; i < count; i++) {
         float life = 28.0F + 22.0F * (float)hash(seed, i * 4);
         float age = (view.d() + (float)hash(seed, i * 4 + 1) * life) % life;
         float k = age / life;
         double radius = 0.35 + 2.1 * hash(seed, i * 4 + 2);
         double angle = hash(seed, i * 4 + 3) * Math.PI * 2.0 + age * (i % 2 == 0 ? 0.06 : -0.06);
         double y = view.ground() + 0.2 + age * (0.07 + 0.1 * hash(seed, i * 7 + 5));
         Vec3 at = new Vec3(view.center().x + Math.cos(angle) * radius, y, view.center().z + Math.sin(angle) * radius);
         int alpha = alpha(0.85F * beam * (float)Math.sin(Math.PI * k));
         boolean gold = i % 3 == 0;
         PixelVfx.billboardPixel(buffer, eye, camera, at, gold ? 0.05F : 0.03F, 255, gold ? 214 : 246, gold ? 128 : 232, alpha);
      }

      float since = view.d() - ReidDrawLight.RELEASE;
      if (since >= 0.0F && since < 26.0F) {
         float fade = (1.0F - since / 26.0F) * view.fade();
         for (int i = 0; i < 44; i++) {
            double angle = hash(seed, 900 + i) * Math.PI * 2.0;
            double speed = 4.0 + 7.0 * hash(seed, 950 + i);
            double out = speed * (1.0 - Math.exp(-since / 6.0));
            double y = view.center().y + 0.6 + 2.6 * hash(seed, 990 + i) + 0.04 * since;
            Vec3 at = new Vec3(view.center().x + Math.cos(angle) * out, y, view.center().z + Math.sin(angle) * out);
            PixelVfx.billboardPixel(buffer, eye, camera, at, i % 4 == 0 ? 0.06F : 0.035F, 255, i % 4 == 0 ? 220 : 248, i % 4 == 0 ? 140 : 236,
               alpha(0.9F * fade * fade));
         }
      }
   }

   /** Ground dust kicked up by the strain and the release (normal blend). */
   private static void dust(BufferBuilder buffer, Vec3 eye, Camera camera, View view) {
      float strain = ReidDrawLight.smooth((view.d() - (ReidDrawChoreo.STRAIN - 4)) / 6.0) * (1.0F - ReidDrawLight.smooth((view.d() - 104.0F) / 12.0F));
      if (strain <= 0.01F || Double.isNaN(view.groundAt(view.center().x, view.center().z))) {
         return;
      }

      int seed = Long.hashCode(view.scene().startGameTime()) * 17 + view.scene().casterId();
      for (int i = 0; i < 30; i++) {
         float life = 22.0F + 14.0F * (float)hash(seed, i * 3);
         float age = (view.d() + (float)hash(seed, i * 3 + 1) * life) % life;
         double angle = hash(seed, i * 3 + 2) * Math.PI * 2.0;
         double radius = 0.8 + 2.8 * hash(seed, i * 5 + 7) + 0.03 * age;
         double x = view.center().x + Math.cos(angle) * radius, z = view.center().z + Math.sin(angle) * radius;
         double ground = view.groundAt(x, z);
         if (Double.isNaN(ground)) {
            continue;
         }

         Vec3 at = new Vec3(x, ground + 0.05 + age * 0.035, z);
         PixelVfx.billboardPixel(buffer, eye, camera, at, 0.05F, 92, 84, 74, alpha(0.55F * strain * view.fade() * (float)Math.sin(Math.PI * age / life)));
      }
   }

   /** The light leaking from the scabbard mouth and along the exposed blade (third person only: the clip's blade). */
   private static void blade(BufferBuilder buffer, Vec3 eye, Camera camera, View view, float partialTick) {
      LivingEntity caster = view.caster();
      float light = ReidDrawLight.blade(view.d()) * view.fade();
      if (caster == null || light <= 0.004F || view.d() > ReinhardRules.REID_DRAW_TICKS + 12) {
         return;
      }

      Matrix4f frame = ReinhardTitanBlade.drawFrame(caster, Math.min(view.d(), ReinhardRules.REID_DRAW_TICKS), partialTick, eye);
      if (frame == null) {
         return;
      }

      boolean free = view.d() >= ReidDrawChoreo.RELEASE;
      float from = ReidDrawChoreo.MOUTH, to = free ? 49.58F : ReidDrawChoreo.MOUTH + ReidDrawChoreo.extraction(view.d());
      Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
      for (float y = from; y <= to; y += 2.0F) {
         Vector3f p = frame.transformPosition(new Vector3f(0.0F, y / 16.0F, 0.0F));
         softDot(buffer, p, left, up, 0.07F, alpha(0.5F * light), 255, 250, 228);
      }

      if (!free) {
         Vector3f mouth = frame.transformPosition(new Vector3f(0.0F, to / 16.0F, 0.0F));
         softDot(buffer, mouth, left, up, 0.16F + 0.24F * light, alpha(0.75F * light), 255, 236, 170);
      } else {
         Vector3f tip = frame.transformPosition(new Vector3f(0.0F, 49.0F / 16.0F, 0.0F));
         softDot(buffer, tip, left, up, 0.25F + 0.6F * ReidDrawLight.flash(view.d()), alpha(0.8F * light), 255, 244, 205);
      }
   }

   /** A camera-facing soft dot: bright centre, transparent rim (fan of quads with the centre doubled). */
   private static void softDot(BufferBuilder buffer, Vector3f at, Vector3f left, Vector3f up, float radius, int alpha, int r, int g, int b) {
      if (alpha <= 0) {
         return;
      }

      for (int s = 0; s < 8; s++) {
         double t0 = Math.PI * 2.0 * s / 8, t1 = Math.PI * 2.0 * (s + 1) / 8;
         float c0 = (float)Math.cos(t0) * radius, s0 = (float)Math.sin(t0) * radius;
         float c1 = (float)Math.cos(t1) * radius, s1 = (float)Math.sin(t1) * radius;
         buffer.vertex(at.x, at.y, at.z).color(r, g, b, alpha).endVertex();
         buffer.vertex(at.x + left.x * c0 + up.x * s0, at.y + left.y * c0 + up.y * s0, at.z + left.z * c0 + up.z * s0).color(r, g, b, 0).endVertex();
         buffer.vertex(at.x + left.x * c1 + up.x * s1, at.y + left.y * c1 + up.y * s1, at.z + left.z * c1 + up.z * s1).color(r, g, b, 0).endVertex();
         buffer.vertex(at.x, at.y, at.z).color(r, g, b, alpha).endVertex();
      }
   }

   /** Jagged dark lines radiating from his feet, on the ground near his level. */
   private static void cracks(BufferBuilder buffer, Vec3 eye, View view) {
      float radius = ReidDrawScene.crackRadius(view.d());
      float fade = view.fade() * (1.0F - ReidDrawLight.smooth((view.d() - 116.0F) / 14.0F));
      if (radius <= 0.0F || fade <= 0.01F) {
         return;
      }

      double ox = view.center().x;
      double oz = view.center().z;
      int seed = Long.hashCode(view.scene().startGameTime()) * 31 + view.scene().casterId();
      for (int line = 0; line < CRACK_LINES; line++) {
         double base = (Math.PI * 2.0 * line) / CRACK_LINES + hash(seed, line) * 0.35;
         for (float d = 1.0F; d <= radius; d += CRACK_STEP) {
            double angle = base + Math.sin(d * 0.9 + line) * 0.12 + (hash(seed + line, (int)(d * 2.0F)) - 0.5) * 0.08;
            double x = ox + Math.cos(angle) * d;
            double z = oz + Math.sin(angle) * d;
            double y = view.groundAt(x, z);
            if (Double.isNaN(y)) {
               continue;
            }

            int alpha = alpha(0.82F * fade * Math.min(1.0F, (radius - d + 1.0F) / 1.5F));
            float size = 0.09F + 0.05F * (1.0F - d / ReidDrawScene.CRACK_RADIUS_MAX);
            groundPixel(buffer, eye, x, y + 0.02, z, size, 22, 14, 12, alpha);
         }
      }
   }

   private static void groundPixel(BufferBuilder buffer, Vec3 cameraPos, double x, double y, double z, float size, int r, int g, int b, int alpha) {
      float cx = (float)(x - cameraPos.x);
      float cy = (float)(y - cameraPos.y);
      float cz = (float)(z - cameraPos.z);
      buffer.vertex(cx - size, cy, cz - size).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx + size, cy, cz - size).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx + size, cy, cz + size).color(r, g, b, alpha).endVertex();
      buffer.vertex(cx - size, cy, cz + size).color(r, g, b, alpha).endVertex();
   }

   /** First-person bloom: the caster sees the light rising from below the view; everyone near feels the release flash. */
   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || client.player == null || client.options.hideGui) {
         return;
      }

      float partial = event.getPartialTick();
      long now = level.getGameTime();
      float bottom = 0.0F, flash = 0.0F, edge = 0.0F;
      for (ReidDrawScene.Active scene : ReidDrawScene.all()) {
         if (!inRange(client, scene)) {
            continue;
         }

         float d = ReidDrawScene.tick(scene, now, partial);
         float fade = ReidDrawScene.fade(scene, now, partial);
         boolean own = client.player.getId() == scene.casterId();
         double distance = client.player.getEyePosition(partial).distanceTo(center(level, scene, partial).add(0.0, 1.5, 0.0));
         if (own && client.options.getCameraType().isFirstPerson()) {
            bottom = Math.max(bottom, 0.32F * ReidDrawLight.blade(d) * fade);
         }

         flash = Math.max(flash, 0.5F * ReidDrawLight.flash(d) * fade * (float)Math.max(0.15, 1.0 - distance / 64.0));
         edge = Math.max(edge, 0.3F * Math.min(1.0F, ReidDrawLight.beam(d)) * fade * (float)Math.max(0.0, 1.0 - distance / 10.0));
      }

      if (bottom + flash + edge <= 0.004F) {
         return;
      }

      GuiGraphics gui = event.getGuiGraphics();
      int w = gui.guiWidth(), h = gui.guiHeight();
      RenderSystem.disableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      if (bottom > 0.004F) {
         gui.fillGradient(0, h * 2 / 3, w, h, argb(0, 255, 226, 160), argb(bottom, 255, 226, 160));
      }

      if (edge > 0.004F) {
         gui.setColor(edge, edge * 0.9F, edge * 0.7F, 1.0F);
         gui.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
         gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      if (flash > 0.004F) {
         gui.fill(0, 0, w, h, argb(flash, 255, 246, 222));
      }

      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
   }

   private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

   private static int argb(float alpha, int r, int g, int b) {
      return (alpha(alpha) << 24) | (r << 16) | (g << 8) | b;
   }

   static int alpha(float value) {
      return Mth.clamp((int)(value * 255.0F), 0, 255);
   }

   private static double hash(int seed, int i) {
      int h = seed * 0x9E3779B1 + i * 0x85EBCA6B;
      h ^= h >>> 15;
      h *= 0x2C1B3C6D;
      h ^= h >>> 12;
      return (h & 0xFFFF) / 65535.0;
   }
}
