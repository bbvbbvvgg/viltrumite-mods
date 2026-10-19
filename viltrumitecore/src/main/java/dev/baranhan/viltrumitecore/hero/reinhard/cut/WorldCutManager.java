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
   /** True while this level still owns the cast (a dropped cast must not lock the caster). */
   public boolean contains(WorldCut cut) { return cuts.contains(cut); }
   public void tick(ServerLevel level) { advance(level.getGameTime()); }
   public void advance(long gameTime) { cuts.removeIf(cut -> cut.advance(gameTime)); }
   public int size() { return cuts.size(); }
   public static void clearAll() { INSTANCES.clear(); }
   @SubscribeEvent public static void onUnload(LevelEvent.Unload event) {
      if(event.getLevel() instanceof ServerLevel level) INSTANCES.remove(level);
   }
}
