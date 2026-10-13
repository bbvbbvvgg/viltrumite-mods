package dev.baranhan.viltrumitecore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Same safe column test vanilla uses for shared-spawn placement; no terrain edits. */
@Mixin(PlayerRespawnLogic.class)
public interface PlayerRespawnLogicInvoker {
   @Invoker("getOverworldRespawnPos")
   static BlockPos viltrumitecore$safeSpawn(ServerLevel level, int x, int z) {throw new AssertionError("mixin not applied");}
}
