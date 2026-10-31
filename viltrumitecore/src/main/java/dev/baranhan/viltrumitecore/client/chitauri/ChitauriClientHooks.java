package dev.baranhan.viltrumitecore.client.chitauri;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import net.minecraft.world.phys.Vec3;

/** Client entry points called from the common entity code through DistExecutor. */
public final class ChitauriClientHooks {
   private ChitauriClientHooks() {
   }

   /** A soldier fired: muzzle flash at the rendered muzzle (the model's, when it was drawn this or last tick). */
   public static void soldierShot(ChitauriSoldier soldier) {
      Vec3 muzzle = soldier.clientMuzzle != null && soldier.tickCount - soldier.clientMuzzleTick <= 2 ? soldier.clientMuzzle : soldier.muzzle();
      ChitauriVfx.muzzleFlash(muzzle, soldier.getViewVector(1.0F), 1.0F);
   }
}
