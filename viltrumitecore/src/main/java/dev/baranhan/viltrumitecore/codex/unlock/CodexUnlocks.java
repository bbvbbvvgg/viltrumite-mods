package dev.baranhan.viltrumitecore.codex.unlock;

import dev.baranhan.viltrumitecore.codex.CodexProgress;
import dev.baranhan.viltrumitecore.codex.CodexRecord;
import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.CodexEntryDef;
import dev.baranhan.viltrumitecore.codex.data.CodexUnlockRules;
import dev.baranhan.viltrumitecore.codex.data.UnlockCondition;
import dev.baranhan.viltrumitecore.worldevent.WorldEvent;
import dev.baranhan.viltrumitecore.worldevent.WorldEventManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Opens Codex 2.0 entries from real play (server side): a kill ({@code entity_killed}), a boss or creature
 * the player actually sees nearby ({@code entity_seen}, line of sight, {@value #SEEN_RADIUS} blocks), a
 * world event running in the player's level ({@code world_event}) and the dimension he stands in
 * ({@code dimension}; the change itself is handled by CodexProgress.onDimension). The periodic check runs
 * every {@value #PERIOD} ticks per player and only looks for entity types some closed entry still waits for.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CodexUnlocks {
   static final int PERIOD = 40;
   static final double SEEN_RADIUS = 32.0;

   private CodexUnlocks() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeath(LivingDeathEvent event) {
      if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer killer) || !CodexProgress.isRealPlayer(killer)) {
         return;
      }
      ResourceLocation type = EntityType.getKey(event.getEntity().getType());
      CodexProgress.unlockMatching(killer, UnlockCondition.Type.ENTITY_KILLED, type.toString());
   }

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)) {
         return;
      }
      if ((player.tickCount + player.getId()) % PERIOD != 0 || !CodexProgress.isRealPlayer(player) || !(player.level() instanceof ServerLevel level)) {
         return;
      }
      CodexData data = CodexData.get();
      if (data.entries().isEmpty()) {
         return;
      }
      CodexProgress.unlockMatching(player, UnlockCondition.Type.DIMENSION, level.dimension().location().toString());
      for (WorldEvent worldEvent : WorldEventManager.get(level).activeEvents()) {
         if (worldEvent.type() != null && worldEvent.hasBegun() && !worldEvent.isOver()) {
            CodexProgress.unlockMatching(player, UnlockCondition.Type.WORLD_EVENT, worldEvent.type().id());
         }
      }
      Set<String> waiting = waitingSeen(data.withCondition(UnlockCondition.Type.ENTITY_SEEN), CodexProgress.read(player));
      if (waiting.isEmpty()) {
         return;
      }
      List<Entity> near = level.getEntities(player, player.getBoundingBox().inflate(SEEN_RADIUS),
         e -> e.isAlive() && waiting.contains(EntityType.getKey(e.getType()).toString()));
      Set<String> seen = new HashSet<>();
      for (Entity entity : near) {
         String key = EntityType.getKey(entity.getType()).toString();
         if (!seen.contains(key) && player.hasLineOfSight(entity)) {
            seen.add(key);
            CodexProgress.unlockMatching(player, UnlockCondition.Type.ENTITY_SEEN, key);
         }
      }
   }

   /** Entity types that some still-closed entry waits to be seen. */
   static Set<String> waitingSeen(List<CodexEntryDef> entries, CodexRecord record) {
      Set<String> out = new HashSet<>();
      for (CodexEntryDef entry : entries) {
         if (!CodexUnlockRules.isUnlocked(entry, record)) {
            for (UnlockCondition c : entry.unlock()) {
               if (c.type() == UnlockCondition.Type.ENTITY_SEEN) {
                  out.add(c.target());
               }
            }
         }
      }
      return out;
   }
}
