package dev.baranhan.viltrumitecore.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** setDragonKilled generates terrain/rewards; quiet disappearance updates only persistent fight flags. */
@Mixin(EndDragonFight.class)
public interface EndDragonFightAccess {
   @Accessor("dragonEvent") ServerBossEvent viltrumitecore$bossbar();
   @Accessor("dragonKilled") void viltrumitecore$setDragonKilled(boolean value);
   @Accessor("previouslyKilled") void viltrumitecore$setPreviouslyKilled(boolean value);
   @Accessor("needsStateScanning") void viltrumitecore$setNeedsScan(boolean value);
}
