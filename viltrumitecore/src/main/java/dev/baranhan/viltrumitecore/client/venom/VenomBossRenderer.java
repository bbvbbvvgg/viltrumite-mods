package dev.baranhan.viltrumitecore.client.venom;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The raging Venom boss: the same body, poses, passes and tendrils as the player's form (VenomRenderer.drawBody),
 * at the boss's height. Its move is clocked from the synced serial on the client clock; the tendril line and the
 * held body come from its synced target. While it rides a host its body collapses into a mass on the ground (the
 * "possess" clip) and a tendril runs from it to the host. Client only.
 */
public class VenomBossRenderer extends EntityRenderer<VenomBossEntity> {
   static final float HEIGHT = 2.6F;

   public VenomBossRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.8F;
   }

   @Override
   public ResourceLocation getTextureLocation(VenomBossEntity entity) {
      return VenomView.SKIN;
   }

   @Override
   public void render(VenomBossEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel body = AnimCache.model(VenomView.BODY);
      if (body == null) {
         super.render(e, yaw, partial, stack, buffers, light);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      float now = e.tickCount + partial;
      if (e.serial() != e.clientSerialSeen) {
         e.clientSerialSeen = e.serial();
         e.clientStart = now;
      }
      VenomMove move = e.move();
      double ticks = move == null ? 0 : ticks(move, now - e.clientStart);
      int flags = e.flags();
      int target = e.targetId();
      Vec3 end = null;
      Entity t = target >= 0 ? e.level().getEntity(target) : null;
      if (t != null) {
         end = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
      }
      if (VenomFlags.has(flags, VenomFlags.POSSESSING)) {
         flags = VenomFlags.set(flags, VenomFlags.TENDRIL, end != null);
      } else if (move == VenomMove.TENDRIL_SHOT || move == VenomMove.PULL) {
         if (end == null && move == VenomMove.TENDRIL_SHOT) {
            end = e.getEyePosition(partial).add(e.getViewVector(partial).scale(9.0));
         }
         flags = VenomFlags.set(flags, VenomFlags.TENDRIL, end != null);
      } else if ((move == VenomMove.HOLD || move == VenomMove.GRAB || move == VenomMove.SLAM || move == VenomMove.THROW && ticks < move.contact)
         && t != null) {
         flags = VenomFlags.set(flags, VenomFlags.HOLDING, true);
      }
      ReinhardPoses.Passes passes = ReinhardPoses.passes(e.isInvisible(), mc.player != null && !e.isInvisibleTo(mc.player), mc.shouldEntityAppearGlowing(e));
      if (passes.body() || passes.outline()) {
         VenomView.Look look = VenomView.look(true, move, ticks);
         boolean wall = VenomFlags.has(flags, VenomFlags.WALL);
         VenomPose.pose(body, VenomPose.third(e), e, flags, move, ticks, partial, wall, false);
         if (move != VenomMove.POSSESS) {
            VenomRenderer.applyHead(body, e, partial);
         }
         VenomRenderer.Frame f = new VenomRenderer.Frame(e, flags, move, ticks, look, end, target, 1.0F);
         stack.pushPose();
         VenomRenderer.setupRotations(e, stack, partial);
         VenomRenderer.drawBody(body, f, stack, buffers, light, LivingEntityRenderer.getOverlayCoords(e, 0.0F), partial, passes, new Matrix4f[1], HEIGHT);
         stack.popPose();
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   /** The move's ticks on the client clock: loops run on, one-shots hold their last frame. */
   static double ticks(@Nullable VenomMove move, double elapsed) {
      if (move == null) {
         return 0;
      }
      double t = Math.max(0, elapsed);
      return move.loops() ? t : Math.min(t, move.length);
   }
}
