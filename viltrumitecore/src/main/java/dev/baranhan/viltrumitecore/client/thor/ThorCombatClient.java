package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Session B client: registers the combat clips, gives the local first-person
 * view a vanilla arm swing per strike (third person plays ThorClips), adds
 * sparks around an electric Thor, and darkens only the local sky near a
 * storm or the ultimate (#83, #137: never the world weather).
 */
public final class ThorCombatClient {
   private static final Object SKY_OWNER = new Object();
   private static final Map<Player, Integer> LAST_STRIKE = new WeakHashMap<>();
   private static final double SKY_RANGE = 96.0;

   private ThorCombatClient() {
   }

   public static void init() {
      ThorCombatClips.registerAll();
      MinecraftForge.EVENT_BUS.addListener(ThorCombatClient::onClientTick);
   }

   private static void onClientTick(TickEvent.ClientTickEvent event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
         return;
      }

      boolean stormy = false;
      for (Player player : minecraft.level.players()) {
         HeroPublicSnapshot snapshot = ThorView.of(player);
         if (snapshot == null) {
            continue;
         }

         ThorMove move = ThorView.move(snapshot);
         if ((move == ThorMove.ELECTRIC_STORM || move == ThorMove.WRATH) && player.distanceTo(minecraft.player) <= SKY_RANGE) {
            stormy = true;
         }

         if (ThorFlags.mode(snapshot.heroFlags()) == ThorMode.ELECTRIC && player.tickCount % 4 == 0) {
            sparks(minecraft, player);
         }

         if (player == minecraft.player) {
            swing(minecraft, player, snapshot, move);
         }
      }

      if (stormy) {
         SkyTint.INSTANCE.push(SKY_OWNER, 5, () -> new SkyTint.Tint(0.55F, 0.6F, 0.8F, 0.45F, 1.0F));
      } else {
         SkyTint.INSTANCE.remove(SKY_OWNER);
      }
   }

   private static void sparks(Minecraft minecraft, Player player) {
      float yaw = (float)Math.toRadians(player.yBodyRot);
      Vec3 hand = player.position().add(-Math.cos(yaw) * 0.4, 0.9, -Math.sin(yaw) * 0.4);
      var random = player.getRandom();
      minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, hand.x + (random.nextDouble() - 0.5) * 0.3, hand.y + random.nextDouble() * 0.3,
         hand.z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.02, 0.0);
   }

   /** First person only: one vanilla swing per new strike pose (the clips are third-person). */
   private static void swing(Minecraft minecraft, Player player, HeroPublicSnapshot snapshot, ThorMove move) {
      boolean strike = move == ThorMove.HAMMER_COMBO || move == ThorMove.FIST_COMBO || move == ThorMove.HAMMER_STRIKE || move == ThorMove.ELECTRIC_COMBO;
      int key = strike && snapshot.resource() > 0 ? snapshot.actionId() * 4096 + ThorView.step(snapshot) * 64 + snapshot.resource() : 0;
      Integer previous = LAST_STRIKE.put(player, key);
      if (key == 0 || previous != null && previous == key || !minecraft.options.getCameraType().isFirstPerson()) {
         return;
      }

      boolean left;
      if (move == ThorMove.ELECTRIC_COMBO) {
         left = snapshot.resource() == ThorCombatClips.LEFT_HAND;
      } else {
         ThorStrike pose = ThorStrike.byPose(snapshot.resource());
         left = pose != null && pose.hand() == ThorStrike.Hand.LEFT;
      }

      player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, false);
   }
}
