package dev.baranhan.viltrumitecore.world.collapse;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Forge wiring of structural collapses: the level tick, explosions as damage, lifecycle and /collapse. */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class CollapseEvents {
   private CollapseEvents() {
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
         CollapseDirector.tick(level);
      }
   }

   /**
    * Blocks an explosion removes count as structural damage (after other listeners trimmed the list, so a
    * protected area stays protected). Inside a cause scope the block writes are recorded by the journal
    * hook already, so only scope-less explosions (meteor impact, TNT, creepers, Chitauri ordnance) are
    * recorded here.
    */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDetonate(ExplosionEvent.Detonate event) {
      if (!(event.getLevel() instanceof ServerLevel level) || event.getAffectedBlocks().isEmpty()) {
         return;
      }
      Explosion explosion = event.getExplosion();
      CollapseCause cause = causeOf(explosion);
      CollapseDirector.noteCause(level, cause);
      if (StructuralDamage.current() == null) {
         StructuralDamage.recordExplosion(level, event.getAffectedBlocks(), cause);
      }
      if (cause == CollapseCause.METEOR) {
         StructuralDamage.impact(level, explosion.getPosition(), cause);
      }
   }

   static CollapseCause causeOf(Explosion explosion) {
      CollapseCause scoped = StructuralDamage.current();
      if (scoped != null) {
         return scoped;
      }
      Entity e = explosion.getDirectSourceEntity();
      if (e == null) {
         e = explosion.getExploder();
      }
      if (e instanceof MeteorEntity) {
         return CollapseCause.METEOR;
      }
      if (e != null && e.getClass().getName().startsWith("dev.baranhan.viltrumitecore.entity.chitauri.")) {
         return CollapseCause.CHITAURI;
      }
      return CollapseCause.EXPLOSION;
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof ServerLevel level) {
         CollapseDirector.abortAll(level);
         StructuralDamage.forget(level.dimension());
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      CollapseDirector.abortAll(null);
      StructuralDamage.clearAll();
   }

   @SubscribeEvent
   public static void onCommands(RegisterCommandsEvent event) {
      CollapseCommands.register(event.getDispatcher());
   }
}
