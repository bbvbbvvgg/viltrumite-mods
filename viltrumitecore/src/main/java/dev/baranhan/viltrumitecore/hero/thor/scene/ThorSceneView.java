package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.scene.SceneFrame.Offset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The client's copy of the running scenes, keyed by Thor's entity id; filled
 * by the scene packet, aged by the client tick. Side-neutral (no client
 * classes) so the shared held-target hooks can read it on the client;
 * always empty on a dedicated server.
 */
public final class ThorSceneView {
   /** One received scene and the client tick it arrived at. */
   public record Live(SceneSync sync, long receivedAt) {
   }

   private static final Map<Integer, Live> SCENES = new ConcurrentHashMap<>();
   private static long clientTicks;

   private ThorSceneView() {
   }

   public static void accept(SceneSync sync) {
      if (sync.end()) {
         SCENES.remove(sync.thorId());
      } else {
         SCENES.put(sync.thorId(), new Live(sync, clientTicks));
      }
   }

   public static void clientTick() {
      clientTicks++;
   }

   public static void clear() {
      SCENES.clear();
   }

   public static long clientTicks() {
      return clientTicks;
   }

   @Nullable
   public static Live of(int thorId) {
      return SCENES.get(thorId);
   }

   public static Map<Integer, Live> all() {
      return SCENES;
   }

   /** The scene in which this entity is the victim, or null. */
   @Nullable
   public static Live victimOf(int entityId) {
      for (Live live : SCENES.values()) {
         if (live.sync().victimId() == entityId) {
            return live;
         }
      }
      return null;
   }

   /** Scene tick now (fractional with the frame's partial tick). */
   public static double tick(Live live, float partial) {
      return live.sync().tick() + (clientTicks - live.receivedAt()) + partial;
   }

   /** Client: the entity this Thor holds right now, or -1. */
   public static int heldId(Player holder) {
      Live live = SCENES.get(holder.getId());
      return live == null || !holds(live.sync()) ? -1 : live.sync().victimId();
   }

   /** Does this sync keep the victim in Thor's hands (hold, action, fatality)? */
   public static boolean holds(SceneSync sync) {
      return switch (sync.kind()) {
         case GRAB -> sync.grabStage() == GrabFlow.Stage.HOLD || sync.grabStage() == GrabFlow.Stage.ACTION;
         case FATALITY -> sync.victimId() >= 0;
         case TELEPORT -> false;
      };
   }

   /** Client anchor of a held body: the same functions the server uses. */
   @Nullable
   public static Vec3 heldPosition(Player holder, Entity target, float partial) {
      Live live = SCENES.get(holder.getId());
      if (live == null || !holds(live.sync()) || live.sync().victimId() != target.getId()) {
         return null;
      }

      SceneSync sync = live.sync();
      double tick = tick(live, partial);
      if (sync.kind() == SceneSync.Kind.FATALITY) {
         FatalityPlan plan = sync.plan();
         return plan == null ? null : plan.victimAt(Math.min(tick, plan.length()));
      }

      float yaw = Mth.rotLerp(partial, holder.yRotO, holder.getYRot());
      Offset offset = GrabPose.at(sync.grabStage(), GrabAction.byId(sync.variant()), tick, target.getBbWidth(), target.getBbHeight());
      return SceneFrame.world(holder.getPosition(partial), yaw, offset);
   }
}
