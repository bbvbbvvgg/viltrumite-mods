package dev.baranhan.viltrumitecore.hero.sinclair.act;

import dev.baranhan.viltrumitecore.hero.BodyIsolation;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCinema;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server half of the copy cinematic (design 2026-11-02 §2): a <b>local</b> stop of exactly the two
 * participants. Both are held by the world {@link ControlManager} as FREEZE records under one effect id,
 * so the stop never competes with DIO's time stop, a World Pause or any other control: if either body is
 * already controlled (or refuses control) the scene does not start and the copy goes on without the
 * cinematic. Hits on a held body queue like any FREEZE and land, capped, when the scene lets go.
 * Clients draw the camera and the card from Sinclair's synced snapshot (move COPY, controlTargetId).
 */
public final class CopyScene {
   private final UUID effect = UUID.randomUUID();
   private final UUID performer;
   private final UUID volunteer;
   private final int volunteerId;
   private boolean held;

   private CopyScene(ServerPlayer player, LivingEntity target) {
      this.performer = player.getUUID();
      this.volunteer = target.getUUID();
      this.volunteerId = target.getId();
   }

   /** Pure gate (tested): every condition under which the cinematic may start. */
   public static boolean mayStart(boolean enabled, boolean bothAlive, boolean sameLevel, boolean anyControlled, boolean anyPaused) {
      return enabled && bothAlive && sameLevel && !anyControlled && !anyPaused;
   }

   /**
    * Try to stage the cinematic for a read card. Returns null (plain copy, nothing changed) when it is off,
    * when either body is already held by anything, or when the hold cannot be taken for both.
    */
   @Nullable
   public static CopyScene tryStart(ServerPlayer player, @Nullable LivingEntity target) {
      if (target == null || !(player.level() instanceof ServerLevel level)) {
         return null;
      }

      ControlManager manager = ControlManager.get(level);
      boolean controlled = manager.isControlled(player) || manager.isControlled(target);
      boolean paused = WorldPause.isPaused(player) || WorldPause.isPaused(target) || BodyIsolation.active(player) || BodyIsolation.active(target);
      if (!mayStart(SinclairCinema.enabled(player.getServer()), player.isAlive() && target.isAlive(), target.level() == level, controlled, paused)) {
         return null;
      }

      CopyScene scene = new CopyScene(player, target);
      long until = level.getGameTime() + CopyCinematic.LENGTH + CopyCinematic.FREEZE_SLACK;
      if (!manager.tryAcquire(target, player.getUUID(), scene.effect, ControlKind.FREEZE, until)) {
         return null;
      }

      if (!manager.tryAcquire(player, player.getUUID(), scene.effect, ControlKind.FREEZE, until)) {
         manager.release(target.getUUID(), scene.effect, ReleaseReason.INTERRUPTED);
         return null;
      }

      scene.held = true;
      return scene;
   }

   public int volunteerId() {
      return this.volunteerId;
   }

   /**
    * One tick of the scene. Returns false when the scene lost a participant (the volunteer died, left, changed
    * dimension, or a hold was taken away): the caller ends the scene early.
    */
   public boolean tick(ServerPlayer player, int t) {
      LivingEntity target = this.target(player);
      if (!this.held || target == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
         return false;
      }

      ControlManager manager = ControlManager.get(level);
      if (!manager.holdsFreeze(player.getUUID(), this.effect) || !manager.holdsFreeze(target.getUUID(), this.effect)) {
         return false;
      }

      Vec3 mid = player.getEyePosition().lerp(target.getEyePosition(), 0.5);
      if (t == 0) {
         SinclairSounds.play(level, mid, SinclairSounds.COPY_STING, 0.8F, 1.0F);
      } else if (t == CopyCinematic.UNFOLD_START) {
         SinclairSounds.play(level, mid, SinclairSounds.COPY_UNFOLD, 1.0F, 1.0F);
      } else if (t == CopyCinematic.FOLD_START) {
         SinclairSounds.play(level, Stagecraft.leftHand(player), SinclairSounds.COPY_FOLD, 1.0F, 1.0F);
      } else if (t == CopyCinematic.CAMERA_OUT) {
         SinclairSounds.play(level, mid, SinclairSounds.COPY_RETURN, 0.8F, 1.0F);
      }

      return true;
   }

   @Nullable
   private LivingEntity target(ServerPlayer player) {
      Entity entity = player.level().getEntity(this.volunteerId);
      return entity instanceof LivingEntity living && living.isAlive() && living.getUUID().equals(this.volunteer) ? living : null;
   }

   /** Let both go (end, interrupt, death, logout, dimension change). Idempotent. */
   public void stop(ServerPlayer player, ReleaseReason reason) {
      if (!this.held) {
         return;
      }

      this.held = false;
      for (ServerLevel level : player.getServer() == null ? java.util.List.<ServerLevel>of() : player.getServer().getAllLevels()) {
         ControlManager manager = ControlManager.get(level);
         manager.release(this.volunteer, this.effect, reason);
         manager.release(this.performer, this.effect, reason);
      }
   }
}
