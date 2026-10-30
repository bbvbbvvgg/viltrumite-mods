package dev.baranhan.viltrumitecore.event;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.world.data.MeteorImpactData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE
)
public class MeteorSpawnEvent {
   @SubscribeEvent
   public static void onLevelTick(LevelTickEvent event) {
      if (event.phase == Phase.END
         && event.level instanceof ServerLevel serverLevel
         && serverLevel.dimension() == ServerLevel.OVERWORLD
         && serverLevel.random.nextFloat() < ViltrumiteCoreConfig.INSTANCE.meteorSpawnChancePercent * 0.01F) {
         spawnMeteor(serverLevel);
      }
   }

   private static void spawnMeteor(ServerLevel level) {
      if (!level.players().isEmpty()) {
         ServerPlayer player = (ServerPlayer)level.players().get(level.random.nextInt(level.players().size()));
         int offsetX = level.random.nextInt(200) - 100;
         int offsetZ = level.random.nextInt(200) - 100;
         BlockPos targetPos = player.blockPosition().offset(offsetX, 0, offsetZ);
         int groundY = level.getHeight(Types.WORLD_SURFACE, targetPos.getX(), targetPos.getZ());
         BlockPos impactTarget = new BlockPos(targetPos.getX(), groundY, targetPos.getZ());
         MeteorImpactData data = MeteorImpactData.get(level);
         if (data.isAreaClear(impactTarget, ViltrumiteCoreConfig.INSTANCE.meteorMinDistance)) {
            data.addImpact(impactTarget);
            launch(level, impactTarget);
         }
      }
   }

   /** Spawn point of a meteor that lands on {@code impactTarget}: the fixed fall line from y 250. */
   public static Vec3 spawnPoint(BlockPos impactTarget) {
      double distanceY = 250.0 - (double)impactTarget.getY();
      double ticksToFall = distanceY / 1.5;
      return new Vec3((double)impactTarget.getX() - ticksToFall * 0.5, 250.0, (double)impactTarget.getZ() - ticksToFall * 0.5);
   }

   /** Launch one regular meteor at a surface point (shared by the random spawn and the meteor rain). */
   public static MeteorEntity launch(ServerLevel level, BlockPos impactTarget) {
      return launch(level, impactTarget, true);
   }

   /** {@code dropsLoot} false: the meteor rain, which must not pile up gun parts. */
   public static MeteorEntity launch(ServerLevel level, BlockPos impactTarget, boolean dropsLoot) {
      MeteorEntity meteor = (MeteorEntity)((EntityType)ViltrumiteEntities.METEOR.get()).create(level);
      if (meteor != null) {
         meteor.setDropsLoot(dropsLoot);
         Vec3 spawn = spawnPoint(impactTarget);
         meteor.moveTo(spawn.x, spawn.y, spawn.z);
         meteor.setDeltaMovement(new Vec3(0.5, -1.5, 0.5));
         level.addFreshEntity(meteor);
      }

      return meteor;
   }
}
