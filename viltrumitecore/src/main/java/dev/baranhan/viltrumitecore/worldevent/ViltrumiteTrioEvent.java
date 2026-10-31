package dev.baranhan.viltrumitecore.worldevent;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class ViltrumiteTrioEvent extends WorldEvent {
   public static final int DEFAULT_WARMUP_TICKS = 1800;
   private int lastAnnouncedAlive = -1;

   @Override
   protected void onBegin(ServerLevel level) {
      this.spawnViltrumite(
         level,
         new Vec3(-4.0, 10.0, 0.0),
         "Thula",
         "skin_thula",
         "cape_viltrumite",
         "slim",
         0.91F,
         1,
         ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES,
         BalanceTable.THULA.baseDamage(),
         BalanceTable.THULA.ignoreThreshold(),
         BalanceTable.THULA.reductionPercent(),
         BalanceTable.THULA.healFactor(),
         12.0F,
         0.025F
      );
      this.spawnViltrumite(
         level,
         new Vec3(0.0, 10.0, 4.0),
         "Lucan",
         "skin_lucan",
         "cape_viltrumite",
         "default",
         1.073F,
         1,
         ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES,
         BalanceTable.LUCAN.baseDamage(),
         BalanceTable.LUCAN.ignoreThreshold(),
         BalanceTable.LUCAN.reductionPercent(),
         BalanceTable.LUCAN.healFactor(),
         9.0F,
         0.018F
      );
      this.spawnViltrumite(
         level,
         new Vec3(4.0, 10.0, -4.0),
         "Vidor",
         "skin_vidor",
         "cape_viltrumite",
         "default",
         1.01F,
         2,
         ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES,
         BalanceTable.VIDOR.baseDamage(),
         BalanceTable.VIDOR.ignoreThreshold(),
         BalanceTable.VIDOR.reductionPercent(),
         BalanceTable.VIDOR.healFactor(),
         10.5F,
         0.02F
      );
      this.lastAnnouncedAlive = 3;
   }

   @Override
   protected void onTick(ServerLevel level) {
      int alive = this.aliveCount(level);
      if (alive < this.lastAnnouncedAlive && alive > 0) {
         this.lastAnnouncedAlive = alive;
         this.broadcast(level, Component.translatable("event.viltrumitecore.trio.remaining", new Object[]{alive}));
      }
   }

   @Override
   protected void saveExtra(CompoundTag tag) {
      tag.putInt("LastAnnouncedAlive", this.lastAnnouncedAlive);
   }

   @Override
   protected void loadExtra(CompoundTag tag) {
      this.lastAnnouncedAlive = tag.contains("LastAnnouncedAlive") ? tag.getInt("LastAnnouncedAlive") : -1;
   }
}
