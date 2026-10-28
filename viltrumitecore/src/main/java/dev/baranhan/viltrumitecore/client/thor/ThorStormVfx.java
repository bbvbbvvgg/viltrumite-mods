package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.ElectricVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatTimings;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * State-driven Thor FX, drawn by every client from the synced snapshot (no
 * packets): {@code thor:wrath_storm} (WRATH build-up and long tail,
 * {@link WrathStormCurve}), the ELECTRIC_STORM cloud, and
 * {@code thor:charge_aura} (arcs over the arm and the hammer, sparks off the
 * head while Thor is charged). Called once per client tick per Thor.
 */
public final class ThorStormVfx {
   /** Cloud base above the ground under Thor (he is 3.2 blocks tall). */
   static final double CLOUD_HEIGHT = 18.0;
   private static final double SHAKE_RANGE = 48.0;
   private static final Map<Player, Wrath> WRATHS = new WeakHashMap<>();
   private static final Random RANDOM = new Random();
   private static boolean listening;

   private ThorStormVfx() {
   }

   static void init() {
      if (!listening) {
         listening = true;
         HeroImpactFx.addPresetListener(ThorStormVfx::onPreset);
      }
   }

   /** One Thor, one client tick. */
   static void tick(ClientLevel level, Player player, HeroPublicSnapshot snapshot, @Nullable ThorMove move) {
      long now = level.getGameTime();
      boolean inHand = ThorView.place(snapshot, player) == MjolnirPlace.HAND;
      Wrath wrath = WRATHS.get(player);
      if (move == ThorMove.WRATH) {
         ThorMoveDefinition definition = ThorCombatTimings.of(ThorMove.WRATH);
         int length = snapshot.actionLength() > 0 ? snapshot.actionLength() : definition.length();
         long start = now - Math.max(0, snapshot.actionElapsed());
         if (wrath == null || start - wrath.start > wrath.curve.contact()) {
            wrath = new Wrath(start, new WrathStormCurve(definition.intro(), length), RANDOM.nextLong());
            WRATHS.put(player, wrath);
         } else if (now - wrath.start < wrath.curve.contact()) {
            // Resync to the server clock during the build-up.
            wrath.start = start;
         }
      }

      if (wrath != null) {
         float elapsed = now - wrath.start;
         if (wrath.curve.phase(elapsed) == WrathStormCurve.Phase.DONE) {
            WRATHS.remove(player);
         } else {
            wrath(level, player, wrath, elapsed, inHand);
         }
      }

      if (move == ThorMove.ELECTRIC_STORM) {
         storm(level, player, now, inHand);
      }

      boolean charged = ThorFlags.mode(snapshot.heroFlags()) == ThorMode.ELECTRIC;
      if (charged || charging(move)) {
         aura(level, player, now, inHand, charging(move) ? 2 : 1);
      }
   }

   static void clear() {
      WRATHS.clear();
   }

   /** Moves that gather charge in the hammer before they fire. */
   static boolean charging(@Nullable ThorMove move) {
      return move == ThorMove.LIGHTNING_CALL || move == ThorMove.ELECTRIC_WAVE || move == ThorMove.ELECTRIC_STORM || move == ThorMove.WRATH;
   }

   // --- thor:wrath_storm ---

   private static void wrath(ClientLevel level, Player player, Wrath wrath, float elapsed, boolean inHand) {
      WrathStormCurve curve = wrath.curve;
      WrathStormCurve.Phase phase = curve.phase(elapsed);
      Vec3 feet = player.position();
      Vec3 base = wrath.strike != null && elapsed >= curve.contact() ? wrath.strike : feet;
      Vec3 cloud = base.add(0.0, CLOUD_HEIGHT, 0.0);
      float build = curve.build(elapsed);
      ElectricVfx.cloud(wrath, cloud, curve.radius(elapsed), curve.density(elapsed), RANDOM.nextFloat() < 0.15F ? curve.cloudFlash(elapsed) : 0.0F);
      float rumble = curve.rumble(elapsed);
      if (rumble > 0.0F) {
         CameraShake.addAt(feet, rumble, SHAKE_RANGE);
      }

      ThorAnchors.Anchor anchor = ThorAnchors.of(player, 0.0F);
      Vec3 head = inHand ? anchor.head() : anchor.grip();
      int tick = (int)elapsed;
      int interval = curve.arcInterval(elapsed);
      long seed = wrath.seed * 31L + tick;
      if (interval > 0 && tick % interval == 0) {
         Random random = new Random(seed);
         float radius = curve.radius(elapsed);
         Vec3 from = cloud.add((random.nextDouble() - 0.5) * radius * 1.1, -1.5 - random.nextDouble() * 2.0, (random.nextDouble() - 0.5) * radius * 1.1);
         if (phase == WrathStormCurve.Phase.TAIL) {
            // Aftershocks: cloud to ground around the strike point, thinning out.
            Vec3 ground = base.add((random.nextDouble() - 0.5) * 12.0, 0.0, (random.nextDouble() - 0.5) * 12.0);
            ElectricVfx.arc(from, ground, 0.06F + 0.05F * (1.0F - curve.tail(elapsed)), 7, 2, seed);
            ElectricVfx.lightPool(ground, 4.0F, 0.5F, 7, seed);
         } else {
            float width = curve.arcWidth(elapsed);
            ElectricVfx.arc(from, head, width, 6 + Math.round(4 * build), 2, seed);
            ElectricVfx.crackle(head, 0.3F + 0.4F * build, 2 + Math.round(4 * build), 8, seed + 1);
            ElectricVfx.sparks(head, 4 + Math.round(14 * build), 0.08F + 0.12F * build, 14, seed + 2);
            ElectricVfx.lightPool(feet, 4.0F + 6.0F * build, 0.35F + 0.8F * build, 6, seed);
            if (build > 0.5F) {
               // The charge runs down the arm into the body.
               ElectricVfx.arc(anchor.grip(), shoulder(player), 0.03F + 0.03F * build, 5, 1, seed + 3);
            }
         }
      }

      if (curve.hammerBolts(elapsed) && tick % 6 == 0) {
         Random random = new Random(seed ^ 0x9E3779B97F4A7C15L);
         Vec3 top = cloud.add((random.nextDouble() - 0.5) * 4.0, 0.0, (random.nextDouble() - 0.5) * 4.0);
         ElectricVfx.bolt(top, head, 0.14F + 0.08F * build, 2, 10, seed + 5);
         ElectricVfx.skyFlash(cloud, curve.radius(elapsed) * 1.4F, 8, seed + 5);
         CameraShake.addAt(feet, 0.5F + 0.6F * build, SHAKE_RANGE);
      }

      if (curve.crawls(elapsed) && tick % 7 == 0) {
         float reach = phase == WrathStormCurve.Phase.TAIL ? 9.0F * (1.0F - curve.tail(elapsed)) + 3.0F : 2.5F + 5.0F * build;
         ElectricVfx.crawl(level, base.add(0.0, 0.05, 0.0), reach, phase == WrathStormCurve.Phase.TAIL ? 6 : 4, 16, 0.05F, seed + 7);
      }
   }

   /** The strike preset arrived: the tail of the nearest WRATH in its build-up / strike follows the strike point. */
   private static void onPreset(FxPreset preset, Vec3 at, Vec3 target) {
      if (preset != ThorFx.WRATH_STRIKE) {
         return;
      }

      Wrath best = null;
      double bestDistance = Double.MAX_VALUE;
      for (Iterator<Map.Entry<Player, Wrath>> it = WRATHS.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<Player, Wrath> entry = it.next();
         double distance = entry.getKey().position().distanceToSqr(target);
         if (entry.getValue().strike == null && distance < bestDistance) {
            best = entry.getValue();
            bestDistance = distance;
         }
      }

      if (best != null) {
         best.strike = at;
      }
   }

   // --- ELECTRIC_STORM ---

   private static void storm(ClientLevel level, Player player, long now, boolean inHand) {
      Vec3 cloud = player.position().add(0.0, CLOUD_HEIGHT, 0.0);
      ElectricVfx.cloud(STORM_KEYS.computeIfAbsent(player, p -> new Object()), cloud, 14.0F, 0.85F, RANDOM.nextFloat() < 0.1F ? 0.7F : 0.0F);
      CameraShake.addAt(player.position(), 0.12F, SHAKE_RANGE);
      if (now % 5 == 0) {
         ThorAnchors.Anchor anchor = ThorAnchors.of(player, 0.0F);
         long seed = RANDOM.nextLong();
         Vec3 from = cloud.add((RANDOM.nextDouble() - 0.5) * 10.0, -2.0, (RANDOM.nextDouble() - 0.5) * 10.0);
         ElectricVfx.arc(from, inHand ? anchor.head() : anchor.grip(), 0.08F, 7, 2, seed);
         ElectricVfx.sparks(inHand ? anchor.head() : anchor.grip(), 8, 0.14F, 14, seed + 1);
      }
   }

   private static final Map<Player, Object> STORM_KEYS = new WeakHashMap<>();

   // --- thor:charge_aura ---

   private static void aura(ClientLevel level, Player player, long now, boolean inHand, int intensity) {
      ThorAnchors.Anchor anchor = ThorAnchors.of(player, 0.0F);
      long seed = RANDOM.nextLong();
      Random random = new Random(seed);
      double scale = player.getBbHeight() / 1.8;
      if (inHand && now % 3 == 0) {
         // Micro arcs jumping over the hammer head, sparks dripping off it.
         Vec3 head = anchor.head();
         Vec3 from = head.add(jitter(random, 0.22 * scale));
         ElectricVfx.arc(from, from.add(jitter(random, 0.45 * scale)), 0.022F * intensity, 4, 1, seed);
         ElectricVfx.sparks(head, 2 * intensity, 0.07F, 12, seed + 1);
      }

      if (now % (inHand ? 7 : 5) == 0) {
         // A discharge over the arm: hand to shoulder (both fists without the hammer).
         ElectricVfx.arc(anchor.grip().add(jitter(random, 0.06 * scale)), shoulder(player).add(jitter(random, 0.08 * scale)), 0.02F * intensity, 4, 1,
            seed + 2);
         if (!inHand) {
            Vec3 left = mirror(player, anchor.grip());
            ElectricVfx.arc(left, mirror(player, shoulder(player)), 0.02F * intensity, 4, 1, seed + 3);
            ElectricVfx.sparks(anchor.grip(), intensity, 0.05F, 10, seed + 4);
         }
      }

      if (intensity > 1 && now % 9 == 0) {
         ElectricVfx.crawl(level, player.position().add(0.0, 0.05, 0.0), (float)(1.6 * scale), 3, 10, 0.03F, seed + 5);
      }
   }

   static Vec3 shoulder(Player player) {
      double scale = player.getBbHeight() / 1.8;
      double yaw = Math.toRadians(player.yBodyRot);
      return player.position().add(-Math.cos(yaw) * 0.34 * scale, 1.36 * scale, -Math.sin(yaw) * 0.34 * scale);
   }

   /** The same point on the other side of the body (left arm). */
   static Vec3 mirror(Player player, Vec3 point) {
      double yaw = Math.toRadians(player.yBodyRot);
      Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
      double side = point.subtract(player.position()).dot(right);
      return point.subtract(right.scale(2.0 * side));
   }

   private static Vec3 jitter(Random random, double size) {
      return new Vec3((random.nextDouble() - 0.5) * 2.0 * size, (random.nextDouble() - 0.5) * 2.0 * size, (random.nextDouble() - 0.5) * 2.0 * size);
   }

   /** One WRATH as this client saw it: its own clock so the tail outlives the move. */
   private static final class Wrath {
      long start;
      final WrathStormCurve curve;
      final long seed;
      @Nullable
      Vec3 strike;

      Wrath(long start, WrathStormCurve curve, long seed) {
         this.start = start;
         this.curve = curve;
         this.seed = seed;
      }
   }
}
