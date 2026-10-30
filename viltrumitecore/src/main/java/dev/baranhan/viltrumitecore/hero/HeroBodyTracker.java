package dev.baranhan.viltrumitecore.hero;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Both sides, every player tick: keeps the scaled hero body in step with the hero.
 * <ul>
 * <li>Dimensions are refreshed when {@link HeroBodySize#of} changes and on the first sight of a
 * scaled body (login, respawn, a new client-side entity after a dimension change). Leaving the hero
 * changes the scale back to 1, so the box returns to vanilla the next tick.</li>
 * <li>The body's reach and step bonuses ({@link HeroDefinition#bodyReachBonus},
 * {@link HeroDefinition#bodyStepBonus}) are transient attribute modifiers with fixed ids, removed
 * when the bonus is 0 (never saved; a fresh entity gets them again on its first tick).</li>
 * </ul>
 * Entity identity keys: the integrated server and the client have different player objects.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroBodyTracker {
   static final UUID ENTITY_REACH_ID = UUID.fromString("5f0c6b1e-2d47-4a51-9c3e-7a1b8e40d201");
   static final UUID BLOCK_REACH_ID = UUID.fromString("5f0c6b1e-2d47-4a51-9c3e-7a1b8e40d202");
   static final UUID STEP_ID = UUID.fromString("5f0c6b1e-2d47-4a51-9c3e-7a1b8e40d203");
   private static final Map<Player, HeroBodySize.Scale> LAST = Collections.synchronizedMap(new WeakHashMap<>());
   /** A failed refresh for this scale and pose should not spin every tick. */
   private static final Map<Player, HealKey> GAVE_UP = Collections.synchronizedMap(new WeakHashMap<>());

   private HeroBodyTracker() {
   }

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         sync(event.player);
      }
   }

   public static void sync(Player player) {
      HeroBodySize.Scale scale = HeroBodySize.of(player);
      HeroBodySize.Scale previous = LAST.put(player, scale);
      Pose pose = player.getPose();
      HealKey key = new HealKey(scale, pose);
      boolean matches = bodyMatches(player, pose, scale);
      if (shouldRefresh(previous, scale, matches, key.equals(GAVE_UP.get(player)))) {
         player.refreshDimensions();
         if (bodyMatches(player, pose, scale)) GAVE_UP.remove(player);
         else GAVE_UP.put(player, key);
      } else if (matches) {
         GAVE_UP.remove(player);
      }

      HeroDefinition hero = player instanceof HeroPlayer ? HeroRegistry.get(player) : null;
      double reach = hero == null ? 0.0 : hero.bodyReachBonus(player);
      double step = hero == null ? 0.0 : hero.bodyStepBonus(player);
      modifier(player, ForgeMod.ENTITY_REACH.get(), ENTITY_REACH_ID, "Hero body entity reach", reach);
      modifier(player, ForgeMod.BLOCK_REACH.get(), BLOCK_REACH_ID, "Hero body block reach", reach);
      modifier(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, "Hero body step", step);
   }

   /** Live dimensions and eye height must match the current hero snapshot, not just the cached scale. */
   private static boolean bodyMatches(Player player, Pose pose, HeroBodySize.Scale scale) {
      return HeroBodySize.matches(player.getBbWidth(), player.getBbHeight(), player.getEyeHeight(),
         player.getDimensions(pose), player.getEyeHeight(pose), pose, scale);
   }

   static boolean shouldRefresh(@Nullable HeroBodySize.Scale previous, HeroBodySize.Scale now,
                                boolean bodyMatches, boolean healGaveUp) {
      return needsRefresh(previous, now) || !bodyMatches && !healGaveUp;
   }

   private record HealKey(HeroBodySize.Scale scale, Pose pose) {}

   /** First sight of a vanilla body needs nothing; any other first sight or change refreshes. */
   static boolean needsRefresh(@Nullable HeroBodySize.Scale previous, HeroBodySize.Scale now) {
      return previous == null ? !now.vanilla() : !previous.equals(now);
   }

   private static void modifier(Player player, Attribute attribute, UUID id, String name, double amount) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance == null) {
         return;
      }

      AttributeModifier current = instance.getModifier(id);
      boolean wanted = amount != 0.0 && Double.isFinite(amount);
      if (current != null && (!wanted || current.getAmount() != amount)) {
         instance.removeModifier(id);
         current = null;
      }

      if (wanted && current == null) {
         instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
      }
   }
}
