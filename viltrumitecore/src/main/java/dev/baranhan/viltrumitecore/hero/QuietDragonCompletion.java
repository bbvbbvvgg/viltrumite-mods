package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.mixin.EndDragonFightAccess;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EndDragonFight;

/** Administrative boss disappearance: defeat bookkeeping without loot, XP, portal, gateway or egg. */
public final class QuietDragonCompletion {
   private QuietDragonCompletion() {}
   public interface State {void hideBossbar();void markDefeated();}
   public static void finish(State state) {state.hideBossbar();state.markDefeated();}
   public static void markKilled(EndDragonFight fight,EnderDragon dragon) {
      if(!dragon.getUUID().equals(fight.getDragonUUID())) return;
      EndDragonFightAccess access=(EndDragonFightAccess)(Object)fight;
      finish(new State() {
         public void hideBossbar() {access.viltrumitecore$bossbar().setProgress(0);access.viltrumitecore$bossbar().setVisible(false);}
         public void markDefeated() {
            access.viltrumitecore$setNeedsScan(false);
            access.viltrumitecore$setPreviouslyKilled(true);
            access.viltrumitecore$setDragonKilled(true);
         }
      });
   }
}
