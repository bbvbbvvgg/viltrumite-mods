package dev.baranhan.viltrumitecore.client.ironman.mark.sig;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.ThrusterFlames;
import dev.baranhan.viltrumitecore.client.ironman.mark.Mark42Parts;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkExtras;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.IronManVariant;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SuitPart;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Client entry of the seven mark signatures (spec §13): geo parts on the body
 * (Mark 7 emitters, Mark 42 glove, Mark 15 shimmer shell, Mark 39 booster,
 * War Machine turret), the rocket fist entity renderer. Beams, tracers and
 * casings are in {@link SignatureVfx}, the poses in IronManPoser and IronManFirstPerson.
 */
public final class SignatureVisuals {
   private static final String TEXTURE_DIR = "textures/entity/ironman/marks/";
   static final ResourceLocation LASER = geo("laser_emitters");
   static final ResourceLocation BOOSTER = geo("booster");
   static final ResourceLocation BLAST = geo("booster_blast");
   static final ResourceLocation TURRET = geo("turret");
   /** Mark 42 right gauntlet piece (Sind): stays on the hand until the rocket fist launches. */
   static final ResourceLocation GLOVE = Mark42Parts.geo(SuitPart.FOURTEEN.get(5));
   static final ResourceLocation CAMO = geo("camo_shell");

   /** Sind mk6 laser on the right forearm; the pack gives it no lights texture. */
   private static final List<PlayerGeoLayer.Pass> LASER_PASSES = List.of(cutout("laser"));
   /** Satsu Mark 39 jetpack and War Machine turret: drawn with the raw Satsu suit textures. */
   private static final List<PlayerGeoLayer.Pass> BOOSTER_PASSES = MarkExtras.passes(MarkId.MARK_39);
   private static final List<PlayerGeoLayer.Pass> TURRET_PASSES = MarkExtras.passes(MarkId.WAR_MACHINE_MK2);
   private static final ResourceLocation TURRET_CLIPS = new ResourceLocation("viltrumitecore", "animations/ironman/marks/war_machine_turret.animation.json");
   /** Satsu start_on clip: the gun swings over the shoulder by 0.83 s. */
   private static final double TURRET_UNFOLD_SECONDS = 0.8333;
   /** Turret unfold 0..1 and the frame time it was last stepped, per player (render thread). */
   private static final WeakHashMap<AbstractClientPlayer, float[]> TURRET_OPEN = new WeakHashMap<>();
   /** Turret travel limits (degrees): wider turns or pitches push the gun into the head or the shoulder. */
   private static final float TURRET_MAX_YAW = 75.0F;
   private static final float TURRET_MAX_UP = 50.0F;
   private static final float TURRET_MAX_DOWN = 40.0F;
   private static final List<PlayerGeoLayer.Pass> GLOVE_PASSES = Mark42Parts.passes(SuitPart.FOURTEEN.get(5));

   private SignatureVisuals() {
   }

   public static void init() {
   }

   public static void addLayers(PlayerRenderer renderer) {
   }

   public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ViltrumiteEntities.ROCKET_FIST.get(), RocketFistRenderer::new);
   }

   public static void collectParts(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      if (!IronManView.worn(snapshot)) {
         return;
      }

      MarkId mark = IronManVariant.mark(snapshot.variant());
      if (mark == null) {
         return;
      }

      int flags = snapshot.heroFlags();
      boolean active = IronManFlags.is(flags, IronManFlags.Field.SIGNATURE_ACTIVE);
      switch (mark) {
         case MARK_7 -> out.add(new PlayerGeoLayer.Part(LASER, LASER_PASSES, active ? SignatureVisuals::laserOut : null));
         case MARK_42 -> {
            if (!active) {
               out.add(new PlayerGeoLayer.Part(GLOVE, GLOVE_PASSES));
            }
         }
         case MARK_15 -> {
            if (IronManFlags.is(flags, IronManFlags.Field.MARK_CAMO) && !firstPerson) {
               out.add(new PlayerGeoLayer.Part(CAMO, List.of(camoPass(player))));
            }
         }
         case MARK_39 -> {
            if (!firstPerson) {
               out.add(new PlayerGeoLayer.Part(BOOSTER, BOOSTER_PASSES));
               if (active) {
                  out.add(blast(snapshot, partialTick, player));
               }
            }
         }
         case WAR_MACHINE_MK2 -> {
            float open = turretOpen(player, active, partialTick);
            float yaw = turretYaw(player, partialTick) * open;
            float pitch = turretPitch(player, partialTick) * open;
            out.add(new PlayerGeoLayer.Part(TURRET, TURRET_PASSES, geo -> {
               Animation unfold = AnimCache.animation(TURRET_CLIPS, "start_on");
               if (unfold != null && open > 0.0F) {
                  AnimationController.seek(unfold, geo, open * TURRET_UNFOLD_SECONDS);
               }

               GeoBone turret = geo.getBone("turret");
               if (turret != null) {
                  // Gimbal: pitch about the body's right axis first, then yaw (renderBone applies X, then Y).
                  turret.rotX = pitch;
                  turret.rotY = yaw;
               }
            }));
         }
         default -> {
         }
      }
   }

   /**
    * Stage 6 seam: the same signature hardware on a body that is not a player
    * (an Iron Legion suit). The caller copies its posed shell bones onto each
    * part's top-level bones after {@code pose} ran. {@code blast} is the
    * Mark 39 booster flame length 0..1 (0 = off), {@code open} the War Machine
    * turret unfold 0..1 and {@code turretYaw}/{@code turretPitch} its gimbal
    * in radians (bone signs, see {@link #turretYaw}).
    */
   public static void suitParts(MarkId mark, boolean active, boolean camo, float camoAlpha, boolean fistAway, float blast, float open,
      float turretYaw, float turretPitch, float time, List<PlayerGeoLayer.Part> out) {
      switch (mark) {
         case MARK_7 -> out.add(new PlayerGeoLayer.Part(LASER, LASER_PASSES, active ? SignatureVisuals::laserOut : null));
         case MARK_42 -> {
            if (!fistAway) {
               out.add(new PlayerGeoLayer.Part(GLOVE, GLOVE_PASSES));
            }
         }
         case MARK_15 -> {
            if (camo) {
               out.add(new PlayerGeoLayer.Part(CAMO, List.of(new PlayerGeoLayer.Pass(texture("camo_ripple"), PlayerGeoLayer.Pass.Kind.GLOW, 0.75F, 0.92F, 1.0F,
                  Mth.clamp(camoAlpha, 0.0F, 1.0F)))));
            }
         }
         case MARK_39 -> {
            out.add(new PlayerGeoLayer.Part(BOOSTER, BOOSTER_PASSES));
            if (blast > 0.01F) {
               ResourceLocation texture = ThrusterFlames.texture((int)(time * 0.75F));
               List<PlayerGeoLayer.Pass> passes = List.of(PlayerGeoLayer.Pass.glow(texture, 0.85F, 0.95F, 1.0F), PlayerGeoLayer.Pass.glow(texture, 0.8F, 0.9F, 1.0F));
               out.add(new PlayerGeoLayer.Part(BLAST, passes, geo -> {
                  GeoBone flame = geo.getBone("boosterBlast");
                  if (flame != null) {
                     flame.scaleY = Mth.clamp(blast, 0.0F, 1.0F) * (0.9F + 0.1F * Mth.sin(time * 2.3F));
                  }
               }));
            }
         }
         case WAR_MACHINE_MK2 -> out.add(new PlayerGeoLayer.Part(TURRET, TURRET_PASSES, geo -> {
            Animation unfold = AnimCache.animation(TURRET_CLIPS, "start_on");
            if (unfold != null && open > 0.0F) {
               AnimationController.seek(unfold, geo, open * TURRET_UNFOLD_SECONDS);
            }

            GeoBone turret = geo.getBone("turret");
            if (turret != null) {
               turret.rotX = Mth.clamp(turretPitch, -TURRET_MAX_DOWN * Mth.DEG_TO_RAD, TURRET_MAX_UP * Mth.DEG_TO_RAD) * open;
               turret.rotY = Mth.clamp(turretYaw, -TURRET_MAX_YAW * Mth.DEG_TO_RAD, TURRET_MAX_YAW * Mth.DEG_TO_RAD) * open;
            }
         }));
         default -> {
         }
      }
   }

   /** Booster flame: a cone that shrinks over the blast timeline, flickering like the thrusters. */
   private static PlayerGeoLayer.Part blast(HeroPublicSnapshot snapshot, float partialTick, AbstractClientPlayer player) {
      float progress = IronManView.progress(snapshot, HeroAction.SIGNATURE, partialTick);
      float length = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
      float time = player.tickCount + partialTick;
      ResourceLocation texture = ThrusterFlames.texture((int)(time * 0.75F));
      List<PlayerGeoLayer.Pass> passes = List.of(PlayerGeoLayer.Pass.glow(texture, 0.85F, 0.95F, 1.0F), PlayerGeoLayer.Pass.glow(texture, 0.8F, 0.9F, 1.0F));
      return new PlayerGeoLayer.Part(BLAST, passes, geo -> {
         GeoBone flame = geo.getBone("boosterBlast");
         if (flame != null) {
            float flicker = 0.9F + 0.1F * Mth.sin(time * 2.3F);
            flame.scaleY = length * flicker;
            flame.hidden = length <= 0.01F;
         }
      });
   }

   /** Ripple strength: faint in hover, stronger while moving or attacking (spec §13.4). */
   private static PlayerGeoLayer.Pass camoPass(AbstractClientPlayer player) {
      float speed = (float)Mth.clamp(player.getDeltaMovement().horizontalDistance() * 4.0, 0.0, 1.0);
      float alpha = 0.12F + 0.5F * speed + (player.swinging ? 0.3F : 0.0F);
      return new PlayerGeoLayer.Pass(texture("camo_ripple"), PlayerGeoLayer.Pass.Kind.GLOW, 0.75F, 0.92F, 1.0F, alpha);
   }

   /** Sind mk6laser clip at full charge: the emitter rises out of the cover (Java pixels: y down, here y up). */
   private static void laserOut(BakedGeoModel geo) {
      GeoBone laser = geo.getBone("laser");
      GeoBone emitter = geo.getBone("emitter");
      GeoBone cover = geo.getBone("coverLayer");
      if (laser != null) {
         laser.rotZ = laser.initRotZ + (float)Math.toRadians(10.0);
         laser.posX = -0.75F;
      }

      if (emitter != null) {
         emitter.posY = 1.0F;
      }

      if (cover != null) {
         cover.posX = 0.45F;
      }
   }

   /** Smoothed turret unfold: the folded gun on the back swings out while the signature fires. */
   private static float turretOpen(AbstractClientPlayer player, boolean active, float partialTick) {
      float[] open = TURRET_OPEN.computeIfAbsent(player, p -> new float[]{0.0F, Float.NaN});
      float seconds = dev.baranhan.viltrumitecore.client.anim.RealTime.seconds();
      float dt = Float.isNaN(open[1]) ? 0.0F : Math.min(0.1F, seconds - open[1]);
      open[1] = seconds;
      open[0] = Mth.lerp(1.0F - (float)Math.exp(-12.0F * dt), open[0], active ? 1.0F : 0.0F);
      return open[0];
   }

   /**
    * Turret yaw (radians) relative to the body: the head turn, read from the
    * interpolated rotations (the synced aim point updates at 20 Hz and made
    * the gun jitter). A positive bone rotY turns the barrel to the wearer's
    * left in the world (the two x/y flips of PlayerGeoLayer and the entity
    * renderer cancel), Minecraft yaw grows to the right: hence the minus.
    */
   static float turretYaw(AbstractClientPlayer player, float partialTick) {
      float head = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
      float body = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
      return -Mth.clamp(Mth.wrapDegrees(head - body), -TURRET_MAX_YAW, TURRET_MAX_YAW) * Mth.DEG_TO_RAD;
   }

   /** Turret pitch (radians): a positive bone rotX lifts the barrel, Minecraft pitch grows downwards. */
   static float turretPitch(AbstractClientPlayer player, float partialTick) {
      return -Mth.clamp(player.getViewXRot(partialTick), -TURRET_MAX_UP, TURRET_MAX_DOWN) * Mth.DEG_TO_RAD;
   }

   private static PlayerGeoLayer.Pass cutout(String name) {
      return PlayerGeoLayer.Pass.cutout(texture(name));
   }

   private static PlayerGeoLayer.Pass glow(String name, float alpha) {
      return new PlayerGeoLayer.Pass(texture(name), PlayerGeoLayer.Pass.Kind.GLOW, 1.0F, 1.0F, 1.0F, alpha);
   }

   private static ResourceLocation texture(String name) {
      return new ResourceLocation("viltrumitecore", TEXTURE_DIR + name + ".png");
   }

   private static ResourceLocation geo(String name) {
      return new ResourceLocation("viltrumitecore", "geo/ironman/marks/" + name + ".geo.json");
   }
}
