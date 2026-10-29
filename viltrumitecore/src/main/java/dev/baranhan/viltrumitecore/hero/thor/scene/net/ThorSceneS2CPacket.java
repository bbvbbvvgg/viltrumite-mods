package dev.baranhan.viltrumitecore.hero.thor.scene.net;

import dev.baranhan.viltrumitecore.hero.thor.scene.SceneSync;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneView;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Scene state for Thor, the victim and Thor's trackers (session C). */
public class ThorSceneS2CPacket {
   private final SceneSync sync;

   public ThorSceneS2CPacket(SceneSync sync) {
      this.sync = sync;
   }

   public ThorSceneS2CPacket(FriendlyByteBuf buf) {
      this.sync = SceneSync.read(buf);
   }

   public SceneSync sync() {
      return this.sync;
   }

   public void toBytes(FriendlyByteBuf buf) {
      this.sync.write(buf);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> {
         ThorSceneView.accept(this.sync);
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.thor.scene.ThorSceneClient.onSync(this.sync));
      });
      context.get().setPacketHandled(true);
   }
}
