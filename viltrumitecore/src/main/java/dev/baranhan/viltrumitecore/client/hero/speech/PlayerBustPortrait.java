package dev.baranhan.viltrumitecore.client.hero.speech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Default portrait: a 3D bust (head, hat layer, shoulders with jacket and
 * sleeves) of the speaker's actual in-game skin, the hero skins and cosmetics
 * included (AbstractClientPlayer#getSkinTextureLocation already resolves them).
 * A speaker out of view falls back to the tab-list skin, then the default skin.
 */
public final class PlayerBustPortrait implements HeroPortraits.Portrait {
   public static final PlayerBustPortrait INSTANCE = new PlayerBustPortrait();
   /** Model px shown from the hat top to the cut below the shoulders. */
   private static final float SPAN_PX = 13.5F;
   /** Neck height in the box (fraction of the height from the top). */
   private static final float NECK = 0.66F;
   @Nullable
   private PlayerModel<AbstractClientPlayer> wide;
   @Nullable
   private PlayerModel<AbstractClientPlayer> slim;

   private PlayerBustPortrait() {
   }

   @Override
   public void render(GuiGraphics graphics, HeroPortraits.Frame frame) {
      AbstractClientPlayer player = frame.player();
      ResourceLocation skin;
      boolean slimArms;
      boolean hat = true;
      boolean jacket = true;
      boolean sleeves = true;
      if (player != null) {
         skin = player.getSkinTextureLocation();
         slimArms = "slim".equals(player.getModelName());
         hat = player.isModelPartShown(PlayerModelPart.HAT);
         jacket = player.isModelPartShown(PlayerModelPart.JACKET);
         sleeves = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE) || player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
      } else {
         ClientPacketListener connection = Minecraft.getInstance().getConnection();
         PlayerInfo info = connection == null ? null : connection.getPlayerInfo(frame.speaker());
         if (info != null) {
            skin = info.getSkinLocation();
            slimArms = "slim".equals(info.getModelName());
         } else {
            skin = DefaultPlayerSkin.getDefaultSkin(frame.speaker());
            slimArms = "slim".equals(DefaultPlayerSkin.getSkinModelName(frame.speaker()));
         }
      }

      PlayerModel<AbstractClientPlayer> model = this.model(slimArms);
      model.head.resetPose();
      model.head.yRot = HeroPortraits.swayYaw(frame.seconds());
      model.head.xRot = HeroPortraits.swayPitch(frame.seconds());
      model.hat.copyFrom(model.head);
      model.hat.visible = hat;
      model.jacket.visible = jacket;
      model.leftSleeve.visible = sleeves;
      model.rightSleeve.visible = sleeves;
      float pixel = frame.height() / SPAN_PX;
      PoseStack pose = graphics.pose();
      HeroPortraits.renderClipped(graphics, frame, (buffers, alpha) -> {
         pose.pushPose();
         HeroPortraits.camera(pose, frame.x() + frame.width() / 2.0F + 1.0F, frame.y() + frame.height() * NECK, pixel);
         // The player model is y-down and faces -z: half a turn about x faces it to the viewer, upright.
         pose.mulPose(Axis.XP.rotationDegrees(180.0F));
         model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            1.0F, 1.0F, 1.0F, alpha);
         pose.popPose();
      });
   }

   private PlayerModel<AbstractClientPlayer> model(boolean slimArms) {
      if (slimArms) {
         if (this.slim == null) {
            this.slim = bust(true);
         }

         return this.slim;
      }

      if (this.wide == null) {
         this.wide = bust(false);
      }

      return this.wide;
   }

   private static PlayerModel<AbstractClientPlayer> bust(boolean slimArms) {
      PlayerModel<AbstractClientPlayer> model = new PlayerModel<>(
         Minecraft.getInstance().getEntityModels().bakeLayer(slimArms ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slimArms);
      model.young = false;
      model.crouching = false;
      model.riding = false;
      model.leftLeg.visible = false;
      model.rightLeg.visible = false;
      model.leftPants.visible = false;
      model.rightPants.visible = false;
      return model;
   }
}
