package dev.baranhan.viltrumitecore.hero.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** A level owns casts, never the caster. Server stop / level unload drop unpersisted scenes. */
@EventBusSubscriber(modid="viltrumitecore")
public final class WorldCutManager {
   private static final Map<ServerLevel,WorldCutManager> INSTANCES=new WeakHashMap<>();
   private final List<WorldCut> cuts=new ArrayList<>();
   public static WorldCutManager get(ServerLevel level) { return INSTANCES.computeIfAbsent(level,l -> new WorldCutManager()); }
   public void submit(WorldCut cut) { cuts.add(cut); }
   public void tick(ServerLevel level) { advance(level.getGameTime()); }
   public void advance(long gameTime) { cuts.removeIf(cut -> cut.advance(gameTime)); }
   public int size() { return cuts.size(); }
   /** The level goes away: its unpersisted casts end here and stop keeping their casters busy. */
   public void dropAll() { cuts.forEach(WorldCut::drop); cuts.clear(); }
   public static void clearAll() { INSTANCES.values().forEach(WorldCutManager::dropAll); INSTANCES.clear(); }
   @SubscribeEvent public static void onUnload(LevelEvent.Unload event) {
      if(event.getLevel() instanceof ServerLevel level) {
         WorldCutManager manager=INSTANCES.remove(level);
         if(manager!=null) manager.dropAll();
      }
   }
}
