package dev.baranhan.viltrumitecore.client.sinclair.stage;

import dev.baranhan.viltrumitecore.client.hero.speech.HeroSubtitles;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.client.sinclair.SinclairView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;

/**
 * Client half of the copy cinematic (design 2026-11-02 §2): finds the scene the local player takes part in
 * (as Sinclair or as the volunteer) from Sinclair's synced snapshot — move {@link SinclairMove#COPY} and the
 * volunteer id — and, while it runs, holds the local input still, hides the vanilla HUD, ducks the world's
 * sound (Sinclair's own cues stay) and draws the letterbox and the card ({@link CopyCardRenderer}). The
 * staged camera is {@link StageCamera}. Bystanders see the scene in the world only (spotlights, the card).
 */
public final class CopyCinematicClient {
   private static final Object DUCK_OWNER = new Object();
   private static final Set<ResourceLocation> OWN_SOUNDS = new HashSet<>();
   private static boolean ducking;

   /** A running scene of the local player. */
   public record Live(Player sinclair, @Nullable Entity volunteer, HeroPublicSnapshot snapshot, boolean asVolunteer, double tick) {
   }

   private CopyCinematicClient() {
   }

   /** The scene the local player is part of this frame, or null. */
   @Nullable
   public static Live live(float partial) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      Player self = mc.player;
      if (level == null || self == null) {
         return null;
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = SinclairView.of(player);
         if (snapshot == null || SinclairView.move(snapshot) != SinclairMove.COPY) {
            continue;
         }

         boolean mine = player == self;
         boolean volunteer = !mine && snapshot.controlTargetId() == self.getId();
         if (!mine && !volunteer) {
            continue;
         }

         double t = SinclairView.moveTicks(player, snapshot, partial);
         if (!CopyCinematic.running(t)) {
            continue;
         }

         Entity target = snapshot.controlTargetId() >= 0 ? level.getEntity(snapshot.controlTargetId()) : null;
         return new Live(player, target, snapshot, volunteer, t);
      }

      return null;
   }

   static boolean active() {
      return live(0.0F) != null;
   }

   public static void onMovement(MovementInputUpdateEvent event) {
      if (!active()) {
         return;
      }

      var input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
      event.getEntity().setDeltaMovement(Vec3.ZERO);
   }

   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (active()) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   /**
    * The vanilla HUD steps aside for the card (chat and sound subtitles stay). The hero speech panel sits where
    * the card is, so it waits while the card is up and shows again for the camera return.
    */
   public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
      if (HeroSubtitles.OVERLAY_ID.equals(event.getOverlay().id().getPath())) {
         Live live = live(event.getPartialTick());
         if (live != null && CopyCinematic.cardScale(live.tick()) > 0.01) {
            event.setCanceled(true);
         }

         return;
      }

      if (event.getOverlay() == VanillaGuiOverlay.CHAT_PANEL.type() || event.getOverlay() == VanillaGuiOverlay.SUBTITLES.type()) {
         return;
      }

      if (active()) {
         event.setCanceled(true);
      }
   }

   public static void onRenderGui(RenderGuiEvent.Post event) {
      Live live = live(event.getPartialTick());
      if (live != null) {
         CopyCardRenderer.render(event.getGuiGraphics(), live, event.getPartialTick());
      }
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      boolean on = Minecraft.getInstance().level != null && active();
      if (on && !ducking) {
         if (OWN_SOUNDS.isEmpty()) {
            for (String name : SinclairSounds.NAMES) {
               OWN_SOUNDS.add(SinclairSounds.location(name));
            }
         }

         SoundDuck.INSTANCE.duck(DUCK_OWNER, SoundSource.MASTER, 0.22F, 4, OWN_SOUNDS);
         ducking = true;
      } else if (!on && ducking) {
         SoundDuck.INSTANCE.release(DUCK_OWNER, 8);
         ducking = false;
      }
   }

   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      if (ducking) {
         SoundDuck.INSTANCE.release(DUCK_OWNER, 1);
         ducking = false;
      }

      StageCamera.reset();
   }
}
