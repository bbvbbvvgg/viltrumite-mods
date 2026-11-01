package dev.baranhan.viltrumitecore.worldevent;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class ConquestEvent extends WorldEvent {
   public static final int DEFAULT_WARMUP_TICKS = 2400;

   @Override
   protected void onBegin(ServerLevel level) {
      this.spawnViltrumite(
         level,
         new Vec3(0.0, 12.0, 0.0),
         "Conquest",
         "skin_conquest",
         "cape_conquest",
         "default",
         1.15F,
         2,
         ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES,
         BalanceTable.CONQUEST.baseDamage(),
         BalanceTable.CONQUEST.ignoreThreshold(),
         BalanceTable.CONQUEST.reductionPercent(),
         BalanceTable.CONQUEST.healFactor(),
         8.5F,
         0.016F
      );
   }
}
