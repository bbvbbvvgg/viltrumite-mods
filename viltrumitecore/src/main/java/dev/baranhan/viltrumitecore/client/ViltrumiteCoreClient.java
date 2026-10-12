package dev.baranhan.viltrumitecore.client;

import dev.baranhan.viltrumitecore.client.gui.ViltrumiteAbilityScreen;
import dev.baranhan.viltrumitecore.client.regulus.RegulusClient;
import dev.baranhan.viltrumitecore.config.ViltrumiteCameraConfig;
import dev.baranhan.viltrumitecore.config.ViltrumiteClientConfig;
import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.BarrageStateC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.BlockToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.ChopC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.DashToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.GrabToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.PunchC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.SpeedToggleC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.ThunderclapC2SPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
   modid = "viltrumitecore",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public class ViltrumiteCoreClient {
   public static boolean isWorldRendering = false;
   public static UUID iAmBeingGrabbedBy = null;
   public static Vec3 exactHandPos = null;
   public static Vec3 exactVelocity = Vec3.ZERO;
   public static Vec3 prevHandPos = null;
   public static Vec3 currentHandPos = null;
   public static CameraType preGrabPerspective = null;
   public static boolean[] heroKeyDown = new boolean[8];

   @SubscribeEvent
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static void onAddLayers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
      for (String skin : event.getSkins()) {
         if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
            renderer.addLayer(new dev.baranhan.viltrumitecore.client.homelander.HomelanderEyesLayer(renderer));
            renderer.addLayer(new dev.baranhan.viltrumitecore.client.ironman.ReactorGlowLayer(renderer));
            renderer.addLayer(new dev.baranhan.viltrumitecore.client.ironman.IronManSkinLayer(renderer));
            renderer.addLayer(new dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer(renderer));
            dev.baranhan.viltrumitecore.client.ironman.mark.MarkVisuals.addLayers(renderer);
            dev.baranhan.viltrumitecore.client.ironman.mark.sig.SignatureVisuals.addLayers(renderer);
         }
      }
   }

   @SubscribeEvent
   public static void onRegisterReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(dev.baranhan.viltrumitecore.client.ironman.IronManSuitTextures.INSTANCE);
      event.registerReloadListener(dev.baranhan.viltrumitecore.client.ironman.NanoDamageVisuals.INSTANCE);
      event.registerReloadListener(dev.baranhan.viltrumitecore.client.ironman.mark.MarkSkins.INSTANCE);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         RegulusClient.registerSkins();
         dev.baranhan.viltrumitecore.client.homelander.HomelanderClient.registerSkins();
         dev.baranhan.viltrumitecore.client.ironman.IronManClient.registerSkins();
         dev.baranhan.viltrumitecore.client.reinhard.ReinhardClient.init();
         CosmeticLoader.init();
         ViltrumiteCameraConfig.load();
         ViltrumitePostProcessingConfig.load();
         ViltrumiteClientConfig.load();
      });
   }

   @SubscribeEvent
   public static void onKeyRegister(RegisterKeyMappingsEvent event) {
      AbilityInputManager.registerKeys(event);
   }

   @EventBusSubscriber(
      modid = "viltrumitecore",
      value = {Dist.CLIENT},
      bus = Bus.FORGE
   )
   public static class ForgeClientEvents {
      @SubscribeEvent
      public static void onClientTick(ClientTickEvent event) {
         Minecraft client = Minecraft.getInstance();
         if (event.phase == Phase.START) {
            dev.baranhan.viltrumitecore.client.hero.AirJumpClient.beforeTick(client);
            if (ViltrumiteCoreClient.iAmBeingGrabbedBy != null && ViltrumiteCoreClient.exactHandPos != null && client.player != null) {
               if (ViltrumiteCoreClient.currentHandPos == null) {
                  ViltrumiteCoreClient.currentHandPos = ViltrumiteCoreClient.exactHandPos;
                  ViltrumiteCoreClient.prevHandPos = ViltrumiteCoreClient.exactHandPos;
               } else {
                  ViltrumiteCoreClient.prevHandPos = ViltrumiteCoreClient.currentHandPos;
                  ViltrumiteCoreClient.currentHandPos = ViltrumiteCoreClient.exactHandPos;
               }

               client.player.setDeltaMovement(Vec3.ZERO);
               client.player.fallDistance = 0.0F;
            } else {
               ViltrumiteCoreClient.currentHandPos = null;
               ViltrumiteCoreClient.prevHandPos = null;
            }
         }

         if (event.phase == Phase.END) {
            if (client.player == null) {
               return;
            }

            AbilityInputManager.tick(client);
            if (client.player instanceof ViltrumiteCorePlayer corePlayer) {
               if (TargetLockManager.lockedTarget != null && corePlayer.getGrabbedTarget() == TargetLockManager.lockedTarget) {
                  TargetLockManager.toggleLock(client);
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:dash")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.DASH) && !corePlayer.isBlocking() && ViltrumiteCoreClient.iAmBeingGrabbedBy == null) {
                     corePlayer.startDash();
                     CoreMessages.sendToServer(new DashToggleC2SPacket());
                  } else if (dev.baranhan.viltrumitecore.hero.HeroRegistry.get(client.player).ownsAbility(dev.baranhan.viltrumitecore.hero.LegacyKit.DASH)) {
                     // Owned but not allowed right now (e.g. grounded): local refusal click.
                     client.player.playNotifySound(net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.6F);
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:punch")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.PUNCH)
                     && corePlayer.getPunchTicks() <= 0
                     && corePlayer.getChopTicks() <= 0
                     && corePlayer.getThunderclapTicks() <= 0
                     && !corePlayer.isBlocking()
                     && !corePlayer.isBarraging()
                     && corePlayer.getBarrageTicks() == 0
                     && corePlayer.getPunchCooldown() <= 0) {
                     boolean shouldBeRight = corePlayer.isTryingToGrab() || corePlayer.getGrabbedTarget() != null;
                     boolean isLeft = !shouldBeRight && client.level.random.nextBoolean();
                     corePlayer.setLeftArmPunch(isLeft);
                     corePlayer.setPunchTicks(20);
                     CoreMessages.sendToServer(new PunchC2SPacket(isLeft));
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:grab")) {
                  boolean isLeftPunching = corePlayer.getPunchTicks() > 0 && corePlayer.isLeftArmPunch();
                  boolean isLeftChopping = corePlayer.getChopTicks() > 0 && corePlayer.isLeftChop();
                  boolean isThunderclapping = corePlayer.getThunderclapTicks() > 0;
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.GRAB)
                     && ViltrumiteCoreClient.iAmBeingGrabbedBy == null
                     && !corePlayer.isBlocking()
                     && !isLeftPunching
                     && !isLeftChopping
                     && !isThunderclapping
                     && !corePlayer.isBarraging()
                     && corePlayer.getBarrageTicks() == 0) {
                     if (corePlayer.getGrabbedTarget() != null) {
                        corePlayer.releaseTarget();
                     } else if (corePlayer.isTryingToGrab()) {
                        corePlayer.setTryingToGrab(false);
                     } else {
                        corePlayer.setTryingToGrab(true);
                     }

                     CoreMessages.sendToServer(new GrabToggleC2SPacket());
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:block")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.BLOCK)
                     && !corePlayer.isTryingToGrab()
                     && !corePlayer.isDashing()
                     && corePlayer.getPunchTicks() <= 0
                     && corePlayer.getChopTicks() <= 0
                     && corePlayer.getThunderclapTicks() <= 0
                     && !corePlayer.isBarraging()
                     && corePlayer.getBarrageTicks() == 0
                     && corePlayer.getBlockCooldown() <= 0) {
                     corePlayer.setBlocking(!corePlayer.isBlocking());
                     CoreMessages.sendToServer(new BlockToggleC2SPacket());
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:lock")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.LOCK)) {
                     TargetLockManager.toggleLock(client);
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:speed")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.SPEED)) {
                     if (client.player instanceof ViltrumiteFlightPlayer flightPlayer && flightPlayer.getFlightState() != FlightState.NONE) {
                        return;
                     }

                     CoreMessages.sendToServer(new SpeedToggleC2SPacket());
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:chop")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.CHOP)
                     && corePlayer.getChopTicks() <= 0
                     && corePlayer.getPunchTicks() <= 0
                     && corePlayer.getThunderclapTicks() <= 0
                     && !corePlayer.isBlocking()
                     && !corePlayer.isBarraging()
                     && corePlayer.getBarrageTicks() == 0) {
                     boolean shouldBeRight = corePlayer.isTryingToGrab() || corePlayer.getGrabbedTarget() != null;
                     boolean isLeft = !shouldBeRight && client.level.random.nextBoolean();
                     int chopType = client.level.random.nextInt(2);
                     corePlayer.setLeftChop(isLeft);
                     corePlayer.setChopType(chopType);
                     corePlayer.setChopTicks(20);
                     CoreMessages.sendToServer(new ChopC2SPacket(isLeft, chopType));
                  }
               }

               while (AbilityInputManager.consumeAbilityKeyPress(client.player, "viltrumite:thunderclap")) {
                  if (dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.THUNDERCLAP)
                     && corePlayer.getThunderclapTicks() <= 0
                     && corePlayer.getPunchTicks() <= 0
                     && corePlayer.getChopTicks() <= 0
                     && !corePlayer.isBlocking()
                     && !corePlayer.isBarraging()
                     && corePlayer.getBarrageTicks() == 0) {
                     corePlayer.setThunderclapTicks(20);
                     CoreMessages.sendToServer(new ThunderclapC2SPacket());
                  }
               }

               boolean isBarrageKeyDown = AbilityInputManager.isAbilityKeyDown(client.player, "viltrumite:barrage");
               boolean canBarrage = dev.baranhan.viltrumitecore.hero.LegacyKit.allows(client.player, dev.baranhan.viltrumitecore.hero.LegacyKit.BARRAGE)
                  && !corePlayer.isBlocking()
                  && !corePlayer.isDashing()
                  && corePlayer.getChopTicks() <= 0
                  && corePlayer.getPunchTicks() <= 0
                  && corePlayer.getThunderclapTicks() <= 0
                  && !corePlayer.isTryingToGrab()
                  && corePlayer.getGrabbedTarget() == null
                  && corePlayer.getBarrageTicks() >= 0
                  && corePlayer.getBarrageCooldown() <= 0;
               boolean shouldBeBarraging = isBarrageKeyDown && canBarrage;
               if (corePlayer.isBarraging() != shouldBeBarraging) {
                  corePlayer.setBarraging(shouldBeBarraging);
                  CoreMessages.sendToServer(new BarrageStateC2SPacket(shouldBeBarraging));
               }

               while (AbilityInputManager.abilityMenuKey.consumeClick()) {
                  if (dev.baranhan.viltrumitecore.hero.HeroRegistry.get(client.player).hasAbilityPanel(client.player) && client.screen == null) {
                     client.setScreen(new ViltrumiteAbilityScreen());
                  }
               }
            }

            // Hero slot keys (any hero): send press/release edges for the hero's own slots.
            {
               dev.baranhan.viltrumitecore.hero.HeroDefinition hero = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(client.player);
               String[] heroSlots = hero.heroInputSlots();
               for (int i = 0; i < heroSlots.length && i < ViltrumiteCoreClient.heroKeyDown.length; i++) {
                  dev.baranhan.viltrumitecore.hero.HeroAction action = hero.heroActionFor(heroSlots[i]);
                  if (action == null) {
                     continue;
                  }

                  boolean down = AbilityInputManager.isAbilityKeyDown(client.player, heroSlots[i]);
                  if (down != ViltrumiteCoreClient.heroKeyDown[i]) {
                     ViltrumiteCoreClient.heroKeyDown[i] = down;
                     CoreMessages.sendToServer(new dev.baranhan.viltrumitecore.network.packet.HeroInputC2SPacket(action, down));
                  }
               }
            }

            if (client.player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer
               && heroPlayer.getHeroId() == dev.baranhan.viltrumitecore.hero.HeroId.REGULUS) {
               // The Viltrumite fist, recoloured for Regulus: same client start
               // (local punch timer for the animation) and the same packet.
               while (AbilityInputManager.consumeAbilityKeyPress(client.player, dev.baranhan.viltrumitecore.hero.regulus.RegulusAbilities.PUNCH)) {
                  dev.baranhan.viltrumitecore.ability.ViltrumiteAbility punch = dev.baranhan.viltrumitecore.ability.ViltrumiteAbilities.get(dev.baranhan.viltrumitecore.hero.regulus.RegulusAbilities.PUNCH);
                  if (client.player instanceof ViltrumiteCorePlayer regulusCore
                     && (punch == null || !punch.isGrey(client.player))
                     && regulusCore.getChopTicks() <= 0
                     && ViltrumiteCoreClient.iAmBeingGrabbedBy == null) {
                     boolean isLeft = client.level.random.nextBoolean();
                     regulusCore.setLeftArmPunch(isLeft);
                     regulusCore.setPunchTicks(20);
                     CoreMessages.sendToServer(new PunchC2SPacket(isLeft));
                  }
               }
            }

            // Super jump (any hero with superJumpVelocity > 0): one press, instant launch.
            while (AbilityInputManager.superJumpKey.consumeClick()) {
               if (dev.baranhan.viltrumitecore.client.hero.SuperJumpClient.tryLaunch(client.player)) {
                  CoreMessages.sendToServer(new dev.baranhan.viltrumitecore.network.packet.HeroInputC2SPacket(dev.baranhan.viltrumitecore.hero.HeroAction.JUMP, true));
               }
            }

            // Air jumps (any hero with airJumps > 0): predicted here, validated by HeroAirJump.
            dev.baranhan.viltrumitecore.client.hero.AirJumpClient.tick(client);
            TargetLockManager.tickClient(client);
            if (ViltrumiteCoreClient.iAmBeingGrabbedBy != null) {
               if (ViltrumiteCoreClient.preGrabPerspective == null) {
                  ViltrumiteCoreClient.preGrabPerspective = client.options.getCameraType();
               }

               if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                  client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
               }
            } else if (ViltrumiteCoreClient.preGrabPerspective != null) {
               client.options.setCameraType(ViltrumiteCoreClient.preGrabPerspective);
               ViltrumiteCoreClient.preGrabPerspective = null;
            }
         }
      }

      @SubscribeEvent
      public static void onRenderLevel(RenderLevelStageEvent event) {
         Minecraft client = Minecraft.getInstance();
         if (event.getStage() == Stage.AFTER_SKY) {
            ViltrumiteCoreClient.isWorldRendering = true;
            if (client.player == null) {
               return;
            }

            if (ViltrumiteCoreClient.iAmBeingGrabbedBy != null && ViltrumiteCoreClient.prevHandPos != null && ViltrumiteCoreClient.currentHandPos != null) {
               float partialTicks = event.getPartialTick();
               double lerpX = Mth.lerp((double)partialTicks, ViltrumiteCoreClient.prevHandPos.x, ViltrumiteCoreClient.currentHandPos.x);
               double lerpY = Mth.lerp((double)partialTicks, ViltrumiteCoreClient.prevHandPos.y, ViltrumiteCoreClient.currentHandPos.y);
               double lerpZ = Mth.lerp((double)partialTicks, ViltrumiteCoreClient.prevHandPos.z, ViltrumiteCoreClient.currentHandPos.z);
               double neckOffset = (double)client.player.getBbHeight() * 0.85;
               double finalY = lerpY - neckOffset;
               client.player.setPos(lerpX, finalY, lerpZ);
               client.player.setDeltaMovement(ViltrumiteCoreClient.exactVelocity);
               client.player.fallDistance = 0.0F;
               client.player.xo = lerpX;
               client.player.yo = finalY;
               client.player.zo = lerpZ;
               client.player.xOld = lerpX;
               client.player.yOld = finalY;
               client.player.zOld = lerpZ;
               Player grabber = client.level.getPlayerByUUID(ViltrumiteCoreClient.iAmBeingGrabbedBy);
               if (grabber != null) {
                  grabber.setDeltaMovement(ViltrumiteCoreClient.exactVelocity);
               }
            }
         }

         if (event.getStage() == Stage.AFTER_LEVEL) {
            ViltrumiteCoreClient.isWorldRendering = false;
         }
      }
   }
}
