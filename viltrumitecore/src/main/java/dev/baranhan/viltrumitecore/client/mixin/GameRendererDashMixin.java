package dev.baranhan.viltrumitecore.client.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.TargetLockManager;
import dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasurePost;
import dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureUniforms;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Shadow;
import dev.baranhan.viltrumitecore.client.render.ViltrumiteShaders;
import dev.baranhan.viltrumitecore.client.render.vfx.BlockVFXManager;
import dev.baranhan.viltrumitecore.client.render.vfx.MeteorImpactVFXManager;
import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import dev.baranhan.viltrumitecore.effect.ViltrumiteEffects;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.item.InfinityGunItem;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumiteflight.client.mixin.PostChainAccessor;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {GameRenderer.class},
   priority = 1500
)
public abstract class GameRendererDashMixin {
   @Shadow private PostChain postEffect;
   @Shadow public abstract void shutdownEffect();

   /** Stop an already-running sonic chain before vanilla processes it this frame. */
   @Inject(method = "render", at = @At("HEAD"))
   private void erasurePriority(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
      if (ErasurePost.active() && postEffect != null && postEffect.getName().equals("viltrumiteflight:shaders/post/sonic_boom.json")) shutdownEffect();
   }

   /** Flight's TAIL hook can otherwise restart sonic even on a frozen player. */
   @Inject(method = "loadEffect", at = @At("HEAD"), cancellable = true)
   private void avoidSonicDuringErasure(ResourceLocation id, CallbackInfo ci) {
      if (ErasurePost.active() && id.toString().equals("viltrumiteflight:shaders/post/sonic_boom.json")) ci.cancel();
   }

   @Unique
   private float currentLockIntensity = 0.0F;
   @Unique
   private boolean wasGrabbing = false;
   @Unique
   private long grabStartTime = 0L;
   @Unique
   private float currentScourgeIntensity = 0.0F;
   @Unique
   private float scourgeTurn = 0.0F;
   @Unique
   private float prevCamYaw = Float.NaN;
   @Unique
   private float prevCamPitch = Float.NaN;
   @Unique
   private float currentRegulusOverheat = 0.0F;
   @Unique
   private float currentRegulusMadness = 0.0F;

   @Inject(
      method = {"renderLevel"},
      at = {@At("TAIL")}
   )
   private void onRenderDashShader(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player instanceof ViltrumiteCorePlayer corePlayer) {
         float dashProgress = corePlayer.getDashProgress(partialTicks);
         float punchIntensity = 0.0F;
         float shakeIntensity = 0.0F;
         int punchTicks = corePlayer.getPunchTicks();
         if (punchTicks > 0) {
            float exactTicks = (float)punchTicks - partialTicks;
            if (exactTicks <= 15.0F && exactTicks > 5.0F) {
               float normalized = (exactTicks - 5.0F) / 10.0F;
               punchIntensity = ViltrumitePostProcessingConfig.INSTANCE.punchEffectMultiplier * normalized * normalized * normalized;
            }

            if (exactTicks <= 15.0F) {
               shakeIntensity = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * exactTicks / 15.0F;
            }
         }

         ItemStack mainHand = minecraft.player.getMainHandItem();
         if (mainHand.getItem() instanceof InfinityGunItem && InfinityGunItem.isFiring(mainHand)) {
            int gunTimer = mainHand.getOrCreateTag().getInt("GunTimer");
            float exactTicksx = (float)gunTimer - partialTicks;
            float elapsed = 14.0F - exactTicksx;
            float gunFade = Mth.clamp(1.0F - elapsed / 5.0F, 0.0F, 1.0F);
            if (gunFade > 0.01F) {
               float gunShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * 0.2F * gunFade;
               shakeIntensity = Math.max(shakeIntensity, gunShake);
            }
         }

         float blockShake = BlockVFXManager.getBlockShakeIntensity();
         shakeIntensity = Math.max(shakeIntensity, blockShake);
         Entity cameraEntityForMeteor = minecraft.getCameraEntity();
         if (cameraEntityForMeteor != null) {
            float rawMeteorShake = MeteorImpactVFXManager.getMeteorShakeIntensity(cameraEntityForMeteor.position());
            if (rawMeteorShake > 0.0F) {
               float finalMeteorShake = rawMeteorShake * ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier;
               shakeIntensity = Math.max(shakeIntensity, finalMeteorShake);
            }
         }

         float chopIntensity = 0.0F;
         int chopType = 0;
         boolean chopIsLeft = false;
         int chopTicks = corePlayer.getChopTicks();
         if (chopTicks > 0) {
            float exactTicksx = (float)chopTicks - partialTicks;
            if (exactTicksx <= 15.0F && exactTicksx > 5.0F) {
               float normalized = (exactTicksx - 5.0F) / 10.0F;
               chopIntensity = normalized * normalized;
            }

            chopType = corePlayer.getChopType();
            chopIsLeft = corePlayer.isLeftChop();
         }

         float barrageIntensity = 0.0F;
         boolean barrageIsLeft = false;
         int barrageTicks = corePlayer.getBarrageTicks();
         if (barrageTicks >= 5) {
            float exactTicksx = (float)(barrageTicks - 1) + partialTicks;
            float punchTime = exactTicksx - 5.0F;
            float cycleProgress = punchTime % 3.0F / 3.0F;
            barrageIsLeft = (int)(punchTime / 3.0F) % 2 != 0;
            float spike = 0.0F;
            if (cycleProgress < 0.66F) {
               spike = cycleProgress / 0.66F;
            } else {
               spike = 1.0F - (cycleProgress - 0.66F) / 0.34F;
            }

            barrageIntensity = spike * spike;
         } else if (barrageTicks < 0) {
            float exactTicksx = (float)barrageTicks + partialTicks;
            float timeSinceRelease = 9.0F + exactTicksx;
            if (timeSinceRelease < 4.0F) {
               float holdShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * 0.2F;
               shakeIntensity = Math.max(shakeIntensity, holdShake);
            }
         }

         float maxMultiplayerShake = 0.0F;
         float maxMultiplayerBlur = 0.0F;
         Entity cameraEntity = minecraft.getCameraEntity();
         if (minecraft.level != null && cameraEntity != null) {
            for (Player p : minecraft.level.players()) {
               if (p instanceof ViltrumiteCorePlayer) {
                  ViltrumiteCorePlayer cp = (ViltrumiteCorePlayer)p;
                  float dist = cameraEntity.distanceTo(p);
                  int cTicks = cp.getThunderclapTicks();
                  if (cTicks > 0) {
                     float exactTicksx = (float)cTicks - partialTicks;
                     if (exactTicksx <= 11.0F) {
                        float normalized = Math.max(0.0F, exactTicksx / 11.0F);
                        float distFactor = Math.max(0.0F, 1.0F - dist / 50.0F);
                        float lingerDecay = (float)Math.pow((double)normalized, 0.4);
                        float currentShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * lingerDecay * 2.0F * distFactor;
                        maxMultiplayerShake = Math.max(maxMultiplayerShake, currentShake);
                        if (exactTicksx > 1.0F) {
                           float blurNormalized = (exactTicksx - 1.0F) / 10.0F;
                           float currentBlur = ViltrumitePostProcessingConfig.INSTANCE.punchEffectMultiplier * blurNormalized * blurNormalized * distFactor;
                           maxMultiplayerBlur = Math.max(maxMultiplayerBlur, currentBlur);
                        }
                     }
                  }

                  if (p != cameraEntity) {
                     int pTicks = cp.getPunchTicks();
                     if (pTicks > 0) {
                        float exactTicksx = (float)pTicks - partialTicks;
                        if (exactTicksx <= 15.0F && exactTicksx > 0.0F) {
                           float distFactor = Math.max(0.0F, 1.0F - dist / 25.0F);
                           float normalizedShake = exactTicksx / 15.0F;
                           float currentShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * normalizedShake * 0.6F * distFactor;
                           maxMultiplayerShake = Math.max(maxMultiplayerShake, currentShake);
                           if (exactTicksx > 5.0F) {
                              float blurNormalized = (exactTicksx - 5.0F) / 10.0F;
                              float currentBlur = ViltrumitePostProcessingConfig.INSTANCE.punchEffectMultiplier
                                 * blurNormalized
                                 * blurNormalized
                                 * blurNormalized
                                 * 0.5F
                                 * distFactor;
                              maxMultiplayerBlur = Math.max(maxMultiplayerBlur, currentBlur);
                           }
                        }
                     }

                     int mpBarrageTicks = cp.getBarrageTicks();
                     if (mpBarrageTicks >= 5) {
                        float exactTicksx = (float)(mpBarrageTicks - 1) + partialTicks;
                        float punchTime = exactTicksx - 5.0F;
                        float cycleProgress = punchTime % 3.0F / 3.0F;
                        float spike = cycleProgress < 0.66F ? cycleProgress / 0.66F : 1.0F - (cycleProgress - 0.66F) / 0.34F;
                        spike *= spike;
                        float distFactor = Math.max(0.0F, 1.0F - dist / 20.0F);
                        float bShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * spike * 0.4F * distFactor;
                        maxMultiplayerShake = Math.max(maxMultiplayerShake, bShake);
                     }

                     if (cp.isDashing() && p instanceof ViltrumiteFlightPlayer) {
                        ViltrumiteFlightPlayer flightP = (ViltrumiteFlightPlayer)p;
                        if (flightP.getFlightThrottle() > 0.6F) {
                           float distFactor = Math.max(0.0F, 1.0F - dist / 35.0F);
                           float dashShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * 0.4F * distFactor;
                           maxMultiplayerShake = Math.max(maxMultiplayerShake, dashShake);
                        }
                     }

                     ItemStack pMainHand = p.getMainHandItem();
                     if (pMainHand.getItem() instanceof InfinityGunItem && InfinityGunItem.isFiring(pMainHand)) {
                        int gunTimer = pMainHand.getOrCreateTag().getInt("GunTimer");
                        float exactTicksx = (float)gunTimer - partialTicks;
                        float elapsed = 14.0F - exactTicksx;
                        float gunFade = Mth.clamp(1.0F - elapsed / 5.0F, 0.0F, 1.0F);
                        if (gunFade > 0.01F) {
                           float distFactor = Math.max(0.0F, 1.0F - dist / 30.0F);
                           float pGunShake = ViltrumitePostProcessingConfig.INSTANCE.punchShakeMultiplier * 0.2F * distFactor * gunFade;
                           maxMultiplayerShake = Math.max(maxMultiplayerShake, pGunShake);
                        }
                     }
                  }
               }
            }
         }

         shakeIntensity = Math.max(shakeIntensity, maxMultiplayerShake);
         punchIntensity = Math.max(punchIntensity, maxMultiplayerBlur);
         boolean isLocked = TargetLockManager.lockedTarget != null;
         this.currentLockIntensity = Mth.lerp(
            0.05F, this.currentLockIntensity, isLocked ? ViltrumitePostProcessingConfig.INSTANCE.lockVignetteMultiplier : 0.0F
         );
         boolean isGrabbing = corePlayer.getGrabbedTarget() != null;
         if (isGrabbing && !this.wasGrabbing) {
            this.grabStartTime = System.currentTimeMillis();
         }

         this.wasGrabbing = isGrabbing;
         float currentGrabIntensity = 0.0F;
         if (this.grabStartTime > 0L) {
            long elapsed = System.currentTimeMillis() - this.grabStartTime;
            if (elapsed < 750L) {
               float normalized = 1.0F - (float)elapsed / 750.0F;
               currentGrabIntensity = normalized * normalized;
            } else {
               this.grabStartTime = 0L;
            }
         }

         float rawTurn = 0.0F;
         Entity scourgeCam = minecraft.getCameraEntity();
         if (scourgeCam != null) {
            float camYaw = scourgeCam.getViewYRot(partialTicks);
            float camPitch = scourgeCam.getViewXRot(partialTicks);
            if (!Float.isNaN(this.prevCamYaw)) {
               float dYaw = Mth.degreesDifference(this.prevCamYaw, camYaw);
               float dPitch = camPitch - this.prevCamPitch;
               rawTurn = Mth.clamp(Mth.sqrt(dYaw * dYaw + dPitch * dPitch) / 6.0F, 0.0F, 1.0F);
            }

            this.prevCamYaw = camYaw;
            this.prevCamPitch = camPitch;
         }

         this.scourgeTurn = Mth.lerp(0.25F, this.scourgeTurn, rawTurn);
         float targetScourge = 0.0F;
         MobEffectInstance scourge = minecraft.player.getEffect((MobEffect)ViltrumiteEffects.SCOURGE_VIRUS.get());
         if (scourge != null) {
            float amp = Math.min(1.0F + (float)scourge.getAmplifier() * 0.35F, 1.5F);
            float fade = scourge.getDuration() < 40 ? (float)scourge.getDuration() / 40.0F : 1.0F;
            targetScourge = amp * fade;
         }

         this.currentScourgeIntensity = Mth.lerp(0.08F, this.currentScourgeIntensity, targetScourge);
         float regulusHeartFlash = 0.0F;
         float regulusPulse = 0.0F;
         // Regulus Reborn draws his own overlays (client.regulus); the first kit's uniforms stay at rest.

         ErasureUniforms erasure = ErasurePost.uniforms(partialTicks);
         float[] timeStop = dev.baranhan.viltrumitecore.client.dio.TimeStopClient.screen(partialTicks);
         boolean anyEffect = ErasurePost.active() || dashProgress > 0.0F
            || timeStop[0] + timeStop[1] + timeStop[2] > 0.0F
            || punchIntensity > 0.0F
            || shakeIntensity > 0.0F
            || this.currentLockIntensity > 0.01F
            || currentGrabIntensity > 0.01F
            || chopIntensity > 0.01F
            || barrageIntensity > 0.01F
            || this.currentScourgeIntensity > 0.005F
            || this.currentRegulusOverheat > 0.01F
            || this.currentRegulusMadness > 0.01F
            || regulusHeartFlash > 0.01F;
         if (anyEffect) {
            PostChain chain = ViltrumiteShaders.get();
            if (chain != null) {
               for (PostPass pass : ((PostChainAccessor)chain).getPasses()) {
                  Uniform crackErase = pass.getEffect().getUniform("EraseCrack");
                  if (crackErase != null) crackErase.set(erasure.crack());
                  Uniform shatterErase = pass.getEffect().getUniform("EraseShatter");
                  if (shatterErase != null) shatterErase.set(erasure.shatter());
                  Uniform darkErase = pass.getEffect().getUniform("EraseDark");
                  if (darkErase != null) darkErase.set(erasure.dark());
                  Uniform rebuildErase = pass.getEffect().getUniform("EraseRebuild");
                  if (rebuildErase != null) rebuildErase.set(erasure.rebuild());
                  Uniform seedErase = pass.getEffect().getUniform("EraseSeed");
                  if (seedErase != null) seedErase.set(erasure.seed());
                  Uniform tsGrey = pass.getEffect().getUniform("TimeStopGrey");
                  if (tsGrey != null) tsGrey.set(timeStop[0]);
                  Uniform tsInvert = pass.getEffect().getUniform("TimeStopInvert");
                  if (tsInvert != null) tsInvert.set(timeStop[1]);
                  Uniform tsRipple = pass.getEffect().getUniform("TimeStopRipple");
                  if (tsRipple != null) tsRipple.set(timeStop[2]);
                  Uniform dashUniform = pass.getEffect().getUniform("DashIntensity");
                  if (dashUniform != null) {
                     dashUniform.set(Math.max(0.0F, Math.min(1.0F, dashProgress)));
                  }

                  Uniform punchUniform = pass.getEffect().getUniform("PunchIntensity");
                  if (punchUniform != null) {
                     punchUniform.set(Math.max(0.0F, Math.min(1.0F, punchIntensity)));
                  }

                  Uniform shakeUniform = pass.getEffect().getUniform("ShakeIntensity");
                  if (shakeUniform != null) {
                     shakeUniform.set(Math.max(0.0F, Math.min(1.0F, shakeIntensity)));
                  }

                  Uniform grabUniform = pass.getEffect().getUniform("GrabIntensity");
                  if (grabUniform != null) {
                     grabUniform.set(currentGrabIntensity);
                  }

                  Uniform lockUniform = pass.getEffect().getUniform("LockIntensity");
                  if (lockUniform != null) {
                     lockUniform.set(this.currentLockIntensity);
                  }

                  Uniform timeUniform = pass.getEffect().getUniform("Time");
                  if (timeUniform != null) {
                     timeUniform.set((float)(System.currentTimeMillis() % 1000000L) / 1000.0F);
                  }

                  Uniform chopUnif = pass.getEffect().getUniform("ChopIntensity");
                  if (chopUnif != null) {
                     chopUnif.set(Math.max(0.0F, Math.min(1.0F, chopIntensity)));
                  }

                  Uniform chopTypeUnif = pass.getEffect().getUniform("ChopType");
                  if (chopTypeUnif != null) {
                     chopTypeUnif.set((float)chopType);
                  }

                  Uniform chopLeftUnif = pass.getEffect().getUniform("ChopIsLeft");
                  if (chopLeftUnif != null) {
                     chopLeftUnif.set(chopIsLeft ? 1.0F : 0.0F);
                  }

                  Uniform barrageUnif = pass.getEffect().getUniform("BarrageIntensity");
                  if (barrageUnif != null) {
                     barrageUnif.set(Math.max(0.0F, Math.min(1.0F, barrageIntensity)));
                  }

                  Uniform barrageLeftUnif = pass.getEffect().getUniform("BarrageIsLeft");
                  if (barrageLeftUnif != null) {
                     barrageLeftUnif.set(barrageIsLeft ? 1.0F : 0.0F);
                  }

                  Uniform scourgeUnif = pass.getEffect().getUniform("ScourgeIntensity");
                  if (scourgeUnif != null) {
                     scourgeUnif.set(Math.max(0.0F, Math.min(1.5F, this.currentScourgeIntensity)));
                  }

                  Uniform scourgeTurnUnif = pass.getEffect().getUniform("ScourgeTurn");
                  if (scourgeTurnUnif != null) {
                     scourgeTurnUnif.set(this.scourgeTurn);
                  }

                  Uniform regulusOverheatUnif = pass.getEffect().getUniform("RegulusOverheat");
                  if (regulusOverheatUnif != null) {
                     regulusOverheatUnif.set(Math.max(0.0F, Math.min(1.0F, this.currentRegulusOverheat)));
                  }

                  Uniform regulusHeartFlashUnif = pass.getEffect().getUniform("RegulusHeartFlash");
                  if (regulusHeartFlashUnif != null) {
                     regulusHeartFlashUnif.set(Math.max(0.0F, Math.min(1.0F, regulusHeartFlash)));
                  }

                  Uniform regulusMadnessUnif = pass.getEffect().getUniform("RegulusMadness");
                  if (regulusMadnessUnif != null) {
                     regulusMadnessUnif.set(Math.max(0.0F, Math.min(1.0F, this.currentRegulusMadness)));
                  }

                  Uniform regulusPulseUnif = pass.getEffect().getUniform("RegulusPulse");
                  if (regulusPulseUnif != null) {
                     regulusPulseUnif.set(Math.max(0.0F, Math.min(1.0F, regulusPulse)));
                  }
               }

               ViltrumiteShaders.process(partialTicks);
            }
         }
      }
   }
}
