package dev.baranhan.viltrumitecore.hero.sinclair.ability;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.sinclair.HexMarks;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SparkEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Prestige, the wand (spec §5.0): hold LMB with an empty main hand for bursts of 2 sparks 3 t apart,
 * 9 t between bursts, 12 bursts per magazine, 32 t automatic reload. The assistant on stage fires
 * along with each spark.
 */
public final class Prestige {
   /** From one burst start to the next: (shots - 1) × 3 + 9. */
   public static final int BURST_PERIOD = (SinclairRules.BURST_SHOTS - 1) * SinclairRules.BURST_SHOT_INTERVAL + SinclairRules.BURST_INTERVAL;

   private Prestige() {
   }

   public static boolean canShoot(ServerPlayer player, SinclairState state) {
      return player.getMainHandItem().isEmpty() && !HexMarks.hexed(player) && !HeroDamage.isAnchored(player) && state.move == null && player.isAlive();
   }

   /** Server, every tick. */
   public static void tick(ServerPlayer player, SinclairState state) {
      state.sinceShot = Math.min(999, state.sinceShot + 1);
      if (state.reload > 0 && --state.reload == 0) {
         state.magazine = SinclairRules.MAGAZINE;
      }

      if (state.burstCooldown > 0) {
         state.burstCooldown--;
      }

      if (state.burstShotsLeft > 0) {
         if (!canShoot(player, state)) {
            state.burstShotsLeft = 0;
         } else if (state.burstShotTimer > 0) {
            state.burstShotTimer--;
         }

         if (state.burstShotsLeft > 0 && state.burstShotTimer == 0) {
            fire(player, state);
         }

         return;
      }

      if (state.triggerHeld && state.burstCooldown == 0 && state.reload == 0 && state.magazine > 0 && canShoot(player, state)) {
         state.magazine--;
         state.burstShotsLeft = SinclairRules.BURST_SHOTS;
         state.burstShotTimer = 0;
         state.burstCooldown = BURST_PERIOD;
         fire(player, state);
      }
   }

   private static void fire(ServerPlayer player, SinclairState state) {
      state.burstShotsLeft--;
      state.burstShotTimer = SinclairRules.BURST_SHOT_INTERVAL;
      state.sinceShot = 0;
      state.shots++;
      Vec3 from = Stagecraft.wandTip(player);
      Vec3 aim = Stagecraft.aimPoint(player, SinclairRules.SPARK_RANGE);
      Vec3 dir = aim.subtract(from);
      if (dir.lengthSqr() < 1.0E-4) {
         dir = player.getLookAngle();
      }

      spawn(player, from, Stagecraft.spread(dir, SinclairRules.SPARK_SPREAD_DEGREES, player.getRandom()), false);
      SinclairSounds.play(player.level(), from, SinclairSounds.WAND_SHOT, 0.7F, 0.95F + player.getRandom().nextFloat() * 0.12F);
      AssistantEntity assistant = Stagecraft.onStage(player, state);
      if (assistant != null) {
         Vec3 hand = assistant.handPoint();
         Vec3 to = aim.subtract(hand);
         if (to.lengthSqr() > 1.0E-4) {
            spawn(player, hand, Stagecraft.spread(to, SinclairRules.SPARK_SPREAD_DEGREES, player.getRandom()), true);
            assistant.gesture(AssistantEntity.ATTACK, 6);
         }
      }

      if (state.burstShotsLeft == 0 && state.magazine == 0) {
         state.reload = SinclairRules.RELOAD_TICKS;
         SinclairSounds.play(player.level(), player.position().add(0.0, 1.2, 0.0), SinclairSounds.WAND_RELOAD, 0.8F, 1.0F);
      }
   }

   private static void spawn(ServerPlayer player, Vec3 from, Vec3 dir, boolean assistant) {
      SparkEntity spark = SinclairEntities.SPARK.get().create(player.level());
      if (spark == null) {
         return;
      }

      spark.setFromAssistant(assistant);
      spark.launch(player, from, dir.scale(SinclairRules.SPARK_SPEED));
      player.level().addFreshEntity(spark);
   }

   /** §5.0: the swap refills the wand at once. */
   public static void refill(SinclairState state) {
      state.magazine = SinclairRules.MAGAZINE;
      state.reload = 0;
   }
}
