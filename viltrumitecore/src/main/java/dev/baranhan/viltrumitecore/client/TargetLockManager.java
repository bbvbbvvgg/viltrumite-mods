package dev.baranhan.viltrumitecore.client;

import dev.baranhan.viltrumitecore.config.ViltrumiteCameraConfig;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class TargetLockManager {
   public static LivingEntity lockedTarget = null;
   private static long lastRenderTime = 0L;
   private static int lostVisibilityTicks = 0;

   public static void toggleLock(Minecraft client) {
      if (client.player != null && client.level != null) {
         if (lockedTarget != null) {
            lockedTarget = null;
         } else {
            double maxDistance = 64.0;
            Vec3 startPos;
            Vec3 aimVec;
            if (ViltrumiteCameraConfig.INSTANCE.enableThirdPersonCrosshair && !client.options.getCameraType().isFirstPerson()) {
               Camera camera = client.gameRenderer.getMainCamera();
               startPos = camera.getPosition();
               Vec3 playerEye = client.player.getEyePosition(1.0F);
               Vec3 playerLook = client.player.getViewVector(1.0F);
               Vec3 crosshairPos = playerEye.add(playerLook.scale(5.0));
               aimVec = crosshairPos.subtract(startPos).normalize();
            } else {
               startPos = client.player.getEyePosition(1.0F);
               aimVec = client.player.getViewVector(1.0F);
            }

            Vec3 endVec = startPos.add(aimVec.scale(maxDistance));
            AABB box = client.player.getBoundingBox().expandTowards(aimVec.scale(maxDistance)).inflate(2.0, 2.0, 2.0);
            EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
               client.player,
               startPos,
               endVec,
               box,
               entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity != client.player && !dev.baranhan.viltrumitecore.entity.Bystander.is(entity),
               maxDistance * maxDistance
            );
            if (hitResult != null && hitResult.getEntity() instanceof LivingEntity living) {
               if (client.player instanceof ViltrumiteCorePlayer corePlayer && corePlayer.getGrabbedTarget() == living) {
                  return;
               }

               if (hasLineOfSight(client.player, living)) {
                  lockedTarget = living;
                  lostVisibilityTicks = 0;
                  lastRenderTime = System.currentTimeMillis();
               }
            }
         }
      }
   }

   public static void tickClient(Minecraft client) {
      if (lockedTarget != null && client.player != null && client.level != null) {
         if (lockedTarget.isAlive() && !lockedTarget.isRemoved() && !(client.player.distanceTo(lockedTarget) > 100.0F)) {
            if (!hasLineOfSight(client.player, lockedTarget)) {
               lostVisibilityTicks++;
               if (lostVisibilityTicks >= 20) {
                  lockedTarget = null;
               }
            } else {
               lostVisibilityTicks = 0;
            }
         } else {
            lockedTarget = null;
         }
      }
   }

   public static void updateCameraEveryFrame(Minecraft client) {
      if (lockedTarget != null && client.player != null) {
         long now = System.currentTimeMillis();
         if (lastRenderTime == 0L) {
            lastRenderTime = now;
         }

         float deltaSeconds = (float)(now - lastRenderTime) / 1000.0F;
         lastRenderTime = now;
         if (deltaSeconds > 0.1F) {
            deltaSeconds = 0.1F;
         }

         Vec3 targetPos = lockedTarget.position().add(0.0, (double)lockedTarget.getBbHeight() * 0.8, 0.0);
         Vec3 playerEye = client.player.getEyePosition(client.getFrameTime());
         double dX = targetPos.x - playerEye.x;
         double dY = targetPos.y - playerEye.y;
         double dZ = targetPos.z - playerEye.z;
         double diffXZ = Math.sqrt(dX * dX + dZ * dZ);
         float targetYaw = (float)Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F;
         float targetPitch = (float)(-Math.toDegrees(Math.atan2(dY, diffXZ)));
         float currentYaw = client.player.getYRot();
         float currentPitch = client.player.getXRot();
         float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
         float pitchDiff = targetPitch - currentPitch;
         float smoothFactor = 1.0F - (float)Math.exp((double)(-12.0F * deltaSeconds));
         client.player.setYRot(currentYaw + yawDiff * smoothFactor);
         client.player.setXRot(currentPitch + pitchDiff * smoothFactor);
         client.player.setYHeadRot(client.player.getYRot());
         client.player.yBodyRot = client.player.getYRot();
      }
   }

   private static boolean hasLineOfSight(Player player, LivingEntity target) {
      Vec3 start = player.getEyePosition();
      Vec3 end = target.position().add(0.0, (double)target.getBbHeight() * 0.8, 0.0);
      ClipContext context = new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player);
      HitResult result = player.level().clip(context);
      return result.getType() == Type.MISS;
   }
}
