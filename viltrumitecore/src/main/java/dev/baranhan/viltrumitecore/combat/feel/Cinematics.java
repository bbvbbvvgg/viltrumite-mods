package dev.baranhan.viltrumitecore.combat.feel;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.CinematicS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server API of the cinematic camera (wave1-22). Hero-agnostic: any hero, mob or world event
 * calls it once at the start of its ultimate. The server never slows down and never waits for
 * the scene; it only sends one packet to the caster and to the players near the cast. The client
 * decides whether it may take the camera (comfort settings, another hero's own camera, opt-in to
 * watch other players' scenes).
 *
 * <pre>
 * Cinematics.play(caster, new ResourceLocation("viltrumitecore", "orbit"), victim);
 * Cinematics.play(caster, scene, null);   // orbit the caster himself
 * </pre>
 */
public final class Cinematics {
   /** Players within this radius of the caster also receive the scene (they see it only if they opted in). */
   public static final double RANGE = 48.0;

   private Cinematics() {
   }

   /**
    * Start a cinematic scene for {@code caster} and every player within {@link #RANGE}. {@code focus}
    * is the subject orbited by the built-in shot; {@code null} (or an entity from another level) means
    * the caster himself. {@code sceneId} picks the scene (task T4 will load JSON scenes; today the
    * client plays one built-in orbit). Server side only; a client call is ignored.
    */
   public static void play(ServerPlayer caster, ResourceLocation sceneId, @Nullable Entity focus) {
      if (caster == null || !(caster.level() instanceof ServerLevel level)) {
         return;
      }
      int focusId = focus != null && focus.level() == level ? focus.getId() : caster.getId();
      CinematicS2CPacket packet = new CinematicS2CPacket(caster.getId(), sceneId, focusId);
      Vec3 at = caster.position();
      double r2 = RANGE * RANGE;
      for (ServerPlayer viewer : level.players()) {
         if (viewer == caster || viewer.position().distanceToSqr(at) <= r2) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }
}
