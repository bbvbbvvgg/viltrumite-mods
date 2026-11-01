package dev.baranhan.viltrumitecore.world.collapse;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Structural collapse events on {@code MinecraftForge.EVENT_BUS}, always posted on the server thread.
 * Subscribe with {@code @SubscribeEvent void on(CityCollapseEvent.Finished e)}; no dependency in the other
 * direction exists, so a listener mod or branch needs only this package. Order for one collapse:
 * {@link Started} (cancelable) -> {@link Collapsed} -> {@link Impact} -> {@link Finished}. {@code Finished} is
 * always posted once for a started collapse, also when it is aborted (level unload, server stop, command).
 */
public abstract class CityCollapseEvent extends Event {
   private final ServerLevel level;
   private final CollapseInfo info;

   protected CityCollapseEvent(ServerLevel level, CollapseInfo info) {
      this.level = level;
      this.info = info;
   }

   public ServerLevel getLevel() {
      return this.level;
   }

   public CollapseInfo info() {
      return this.info;
   }

   /**
    * The analysis accepted a section; nothing is removed yet. {@code affectedBlocks} = planned section.
    * Cancel to keep the structure (a protected zone): the damage is consumed and the region cools down.
    */
   @Cancelable
   public static final class Started extends CityCollapseEvent {
      public Started(ServerLevel level, CollapseInfo info) {
         super(level, info);
      }
   }

   /** Phase 3: the section came down. {@code affectedBlocks} = blocks really removed (shed + main fall). */
   public static final class Collapsed extends CityCollapseEvent {
      public Collapsed(ServerLevel level, CollapseInfo info) {
         super(level, info);
      }
   }

   /**
    * The falling section hit the ground: impact damage was applied, rubble placed, weak blocks broken.
    * The best moment for dust, fires, smoke and blackouts in {@code info().landing()}.
    */
   public static final class Impact extends CityCollapseEvent {
      public Impact(ServerLevel level, CollapseInfo info) {
         super(level, info);
      }
   }

   /**
    * Phase 4 ended (dust settled) or the collapse was aborted. {@code affectedBlocks} = every block the
    * collapse changed, with {@code originalStates}: the complete record for restoration systems.
    */
   public static final class Finished extends CityCollapseEvent {
      private final boolean aborted;

      public Finished(ServerLevel level, CollapseInfo info, boolean aborted) {
         super(level, info);
         this.aborted = aborted;
      }

      public boolean aborted() {
         return this.aborted;
      }
   }
}
