package dev.baranhan.viltrumitecore.client.thor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * World positions of Mjölnir's head and grip as last drawn (ThorRenderer.drawMjolnir
 * on the real posed geo, both views), for charge arcs and head sparks
 * (FX id {@code thor:charge_aura}). Falls back to a body-scaled estimate when
 * the hammer was not drawn recently (off screen, other render path).
 */
public final class ThorAnchors {
   /** Hammer head centre and grip in mjolnir_hand.geo.json, geo model space (x mirrored), blocks. */
   static final Vector3f HEAD = new Vector3f(6.0F / 16.0F, 22.2F / 16.0F, 0.0F);
   static final Vector3f GRIP = new Vector3f(6.0F / 16.0F, 13.0F / 16.0F, 0.0F);
   private static final String BODY_BONE = "MjolnirBody";
   private static final int FRESH_TICKS = 2;
   private static final Map<Player, Anchor> ANCHORS = new WeakHashMap<>();

   private ThorAnchors() {
   }

   public record Anchor(Vec3 head, Vec3 grip, long gameTime, boolean measured) {
   }

   /** Frame sink of the in-hand hammer: walks the posed bone chain down to MjolnirBody. */
   static void capture(Player player, PoseStack frame, GeoBone top) {
      List<GeoBone> chain = new ArrayList<>(4);
      if (!find(top, BODY_BONE, chain)) {
         return;
      }

      PoseStack stack = new PoseStack();
      stack.mulPoseMatrix(frame.last().pose());
      for (GeoBone bone : chain) {
         apply(stack, bone);
      }

      Minecraft minecraft = Minecraft.getInstance();
      // The local first-person arm is drawn with the hand projection: bring the point to the world projection.
      boolean firstPerson = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
      Anchor anchor = new Anchor(world(stack, HEAD, firstPerson), world(stack, GRIP, firstPerson), player.level().getGameTime(), true);
      ANCHORS.put(player, anchor);
      MjolnirTrails.record(player, anchor.head(), anchor.grip());
   }

   private static Vec3 world(PoseStack stack, Vector3f local, boolean firstPerson) {
      Vector3f v = stack.last().pose().transformPosition(new Vector3f(local));
      Vec3 view = new Vec3(v.x, v.y, v.z);
      if (firstPerson) {
         var projection = RenderSystem.getProjectionMatrix();
         view = HeroDefenseContacts.reprojectWorld(view, projection.m00(), projection.m11());
      }

      var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      return camera.getPosition().add(HeroDefenseContacts.viewToWorld(view, camera.rotation()));
   }

   /** Latest anchor of {@code player}: the drawn one if fresh, else the estimate. */
   public static Anchor of(Player player, float partialTick) {
      Anchor anchor = ANCHORS.get(player);
      long now = player.level().getGameTime();
      if (anchor != null && Math.abs(now - anchor.gameTime()) <= FRESH_TICKS) {
         return anchor;
      }

      return estimate(player, partialTick);
   }

   /** Right hand and hammer head from the body yaw, scaled with the hitbox (Thor is 3.2 blocks). */
   public static Anchor estimate(Player player, float partialTick) {
      double scale = player.getBbHeight() / 1.8;
      double yaw = Math.toRadians(player.yBodyRot);
      Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
      Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
      Vec3 hand = player.getPosition(partialTick).add(right.scale(0.36 * scale)).add(0.0, 0.72 * scale, 0.0).add(forward.scale(0.12 * scale));
      return new Anchor(hand.add(forward.scale(0.4 * scale)).add(0.0, 0.08 * scale, 0.0), hand, player.level().getGameTime(), false);
   }

   @Nullable
   static Anchor raw(Player player) {
      return ANCHORS.get(player);
   }

   private static boolean find(GeoBone bone, String name, List<GeoBone> chain) {
      chain.add(bone);
      if (bone.name.equals(name)) {
         return true;
      }

      for (GeoBone child : bone.children) {
         if (find(child, name, chain)) {
            return true;
         }
      }

      chain.remove(chain.size() - 1);
      return false;
   }

   /** The same bone transform as AnimRenderer.renderBone. */
   private static void apply(PoseStack stack, GeoBone bone) {
      if (bone.posX != 0.0F || bone.posY != 0.0F || bone.posZ != 0.0F) {
         stack.translate(-bone.posX / 16.0F, bone.posY / 16.0F, bone.posZ / 16.0F);
      }

      stack.translate(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
      if (bone.rotZ != 0.0F) {
         stack.mulPose(Axis.ZP.rotation(bone.rotZ));
      }

      if (bone.rotY != 0.0F) {
         stack.mulPose(Axis.YP.rotation(bone.rotY));
      }

      if (bone.rotX != 0.0F) {
         stack.mulPose(Axis.XP.rotation(bone.rotX));
      }

      if (bone.scaleX != 1.0F || bone.scaleY != 1.0F || bone.scaleZ != 1.0F) {
         stack.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
      }

      stack.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
   }
}
