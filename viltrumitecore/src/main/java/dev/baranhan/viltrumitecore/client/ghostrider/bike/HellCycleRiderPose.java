package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The rider leans, pitches and lifts with the Hell Cycle: his whole render (body, gear layers, the anchors his flames
 * read) is turned round the bike's tyre contact line by the bike's tilt, so he banks into a drift and rises with a
 * wheelie instead of sitting bolt upright on a leaning frame. RenderPlayerEvent Pre/Post at LOWEST priority (a hero
 * renderer that cancels the vanilla render runs first). Client only.
 */
public final class HellCycleRiderPose {
   private static final Matrix4f TILT = new Matrix4f();
   private static final Matrix3f NORMAL = new Matrix3f();
   /** Entity ids whose pose stack this class pushed in Pre (popped in Post). */
   private static final IntOpenHashSet PUSHED = new IntOpenHashSet();

   private HellCycleRiderPose() {
   }

   public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
      Player player = event.getEntity();
      if (!(player.getVehicle() instanceof HellCycleEntity bike) || PUSHED.contains(player.getId())) {
         return;
      }
      float partial = event.getPartialTick();
      Matrix4f m = riderTilt(TILT, bike, bike.getPosition(partial).subtract(player.getPosition(partial)), partial);
      PoseStack stack = event.getPoseStack();
      stack.pushPose();
      stack.last().pose().mul(m);
      stack.last().normal().mul(NORMAL.set(m));
      PUSHED.add(player.getId());
   }

   public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
      if (PUSHED.remove(event.getEntity().getId())) {
         event.getPoseStack().popPose();
      }
   }

   /**
    * The rider's extra transform: T(rel) * yaw * tilt * yaw^-1 * T(-rel), rel = bike origin - rider origin. Identity when
    * the bike stands level.
    */
   static Matrix4f riderTilt(Matrix4f out, HellCycleEntity bike, Vec3 rel, float partial) {
      float yaw = Mth.rotLerp(partial, bike.yRotO, bike.getYRot());
      float park = Mth.lerp(partial, bike.parkO, bike.park);
      out.identity().translate((float)rel.x, (float)rel.y, (float)rel.z);
      HellCycleRenderer.tilt(out, bike, partial, park, true);
      out.rotateY(-(180.0F - yaw) * Mth.DEG_TO_RAD);
      return out.translate((float)-rel.x, (float)-rel.y, (float)-rel.z);
   }
}
