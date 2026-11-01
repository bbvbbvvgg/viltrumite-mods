package dev.baranhan.viltrumitecore.city.power.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge events of the city infrastructure, posted on {@code MinecraftForge.EVENT_BUS} on the server thread.
 * Add-ons (Ultron, building collapse, aftermath) listen with {@code @SubscribeEvent}; nothing here references
 * them, and none of them is needed for the grid to work. Not cancellable: the grid has already applied the change.
 */
public abstract class InfrastructureEvent extends Event {
   private final ServerLevel level;

   protected InfrastructureEvent(ServerLevel level) {
      this.level = level;
   }

   public ServerLevel level() {
      return this.level;
   }

   /** The grid took a hit: {@code cells} grid cells will flicker, go dark or lose their transformer. */
   public static final class Damaged extends InfrastructureEvent {
      private final InfrastructureIncident incident;
      private final int cells;
      private final List<BlockPos> transformers;

      public Damaged(ServerLevel level, InfrastructureIncident incident, int cells, List<BlockPos> transformers) {
         super(level);
         this.incident = incident;
         this.cells = cells;
         this.transformers = List.copyOf(transformers);
      }

      public InfrastructureIncident incident() {
         return this.incident;
      }

      /** Grid cells the hit reaches (surge, dark or damaged). */
      public int cells() {
         return this.cells;
      }

      /** Transformers that will blow (sparking, long repair). */
      public List<BlockPos> transformers() {
         return this.transformers;
      }
   }

   /**
    * A district lost power (its first dark cell) or a substation tripped ({@code cascade} true: a failure that
    * spread from a neighbouring substation). {@code incidentId} is -1 when no incident is known.
    */
   public static final class Outage extends InfrastructureEvent {
      private final OutageInfo outage;
      private final boolean cascade;
      private final long incidentId;

      public Outage(ServerLevel level, OutageInfo outage, boolean cascade, long incidentId) {
         super(level);
         this.outage = outage;
         this.cascade = cascade;
         this.incidentId = incidentId;
      }

      public OutageInfo outage() {
         return this.outage;
      }

      public boolean cascade() {
         return this.cascade;
      }

      public long incidentId() {
         return this.incidentId;
      }
   }

   /** Every cell of a district has power again. */
   public static final class Restored extends InfrastructureEvent {
      private final OutageInfo outage;

      public Restored(ServerLevel level, OutageInfo outage) {
         super(level);
         this.outage = outage;
      }

      public OutageInfo outage() {
         return this.outage;
      }
   }

   /** The city emergency changed stage; {@code reason} is the source id of the strongest lease (or "" when none). */
   public static final class AlarmChanged extends InfrastructureEvent {
      private final AlarmStage previous;
      private final AlarmStage stage;
      private final String reason;

      public AlarmChanged(ServerLevel level, AlarmStage previous, AlarmStage stage, String reason) {
         super(level);
         this.previous = previous;
         this.stage = stage;
         this.reason = reason;
      }

      public AlarmStage previous() {
         return this.previous;
      }

      public AlarmStage stage() {
         return this.stage;
      }

      public String reason() {
         return this.reason;
      }
   }
}
