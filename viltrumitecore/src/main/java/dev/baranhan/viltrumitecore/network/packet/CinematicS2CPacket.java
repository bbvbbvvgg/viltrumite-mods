package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.cinematic.CinematicCamera;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One-shot cinematic camera cue (wave1-22): the caster started a scene around {@code focusId}
 * (or himself). Values are clamped on both ends so a bad peer cannot ask for a huge scene id or a
 * negative entity id. Wire: varint casterId, utf sceneId, varint focusId. It says nothing about the
 * scene itself; the client plays it through {@link CinematicCamera}, which obeys the player's
 * comfort settings and never fights another hero's own camera.
 */
public final class CinematicS2CPacket {
   /** Largest entity id we accept from the wire (vanilla ids stay well below this). */
   public static final int MAX_ENTITY_ID = 0xFFFFF;
   public static final int MAX_SCENE_ID_LENGTH = 128;
   /** The only scene known before T4's JSON loader: the built-in orbit. */
   public static final ResourceLocation DEFAULT_SCENE_ID = new ResourceLocation("viltrumitecore", "orbit");

   public final int casterId;
   public final ResourceLocation sceneId;
   public final int focusId;

   public CinematicS2CPacket(int casterId, ResourceLocation sceneId, int focusId) {
      this.casterId = clampId(casterId);
      this.sceneId = sceneId == null ? DEFAULT_SCENE_ID : sceneId;
      this.focusId = clampId(focusId);
   }

   public CinematicS2CPacket(FriendlyByteBuf buf) {
      this(buf.readVarInt(), readScene(buf), buf.readVarInt());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.casterId);
      buf.writeUtf(this.sceneId.toString(), MAX_SCENE_ID_LENGTH);
      buf.writeVarInt(this.focusId);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CinematicCamera.play(this)));
      context.get().setPacketHandled(true);
   }

   private static int clampId(int id) {
      return Mth.clamp(id, 0, MAX_ENTITY_ID);
   }

   private static ResourceLocation readScene(FriendlyByteBuf buf) {
      ResourceLocation id = ResourceLocation.tryParse(buf.readUtf(MAX_SCENE_ID_LENGTH));
      return id == null ? DEFAULT_SCENE_ID : id;
   }
}
