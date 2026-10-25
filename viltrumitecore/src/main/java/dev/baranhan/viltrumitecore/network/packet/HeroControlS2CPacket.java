package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * World-control snapshot for one dimension: live domes plus every anchored
 * or pulled target, with stable ids and expiry ticks. Sent as a baseline on
 * login/respawn/dimension change and re-broadcast whenever control state
 * mutates, so observers see stasis/freeze state even when the caster's own
 * entity is untracked or dead (plan Task 4 contract).
 */
public class HeroControlS2CPacket {
   private final ResourceKey<Level> dimension;
   private final List<DomeInfo> domes;
   private final List<ControlInfo> controls;

   public HeroControlS2CPacket(ResourceKey<Level> dimension, List<DomeInfo> domes, List<ControlInfo> controls) {
      this.dimension = dimension;
      this.domes = domes;
      this.controls = controls;
   }

   public HeroControlS2CPacket(FriendlyByteBuf buffer) {
      this.dimension = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
      int domeCount = buffer.readVarInt();
      this.domes = new ArrayList<>(domeCount);
      for (int i = 0; i < domeCount; i++) {
         UUID id = buffer.readUUID();
         UUID caster = buffer.readUUID();
         double x = buffer.readDouble();
         double y = buffer.readDouble();
         double z = buffer.readDouble();
         double radius = buffer.readDouble();
         long createdAt = buffer.readLong();
         long expiresAt = buffer.readLong();
         int capturedCount = buffer.readVarInt();
         List<UUID> captured = new ArrayList<>(capturedCount);
         for (int j = 0; j < capturedCount; j++) {
            captured.add(buffer.readUUID());
         }

         this.domes.add(new DomeInfo(id, caster, x, y, z, radius, createdAt, expiresAt, captured));
      }

      int controlCount = buffer.readVarInt();
      this.controls = new ArrayList<>(controlCount);
      for (int i = 0; i < controlCount; i++) {
         this.controls.add(new ControlInfo(buffer.readUUID(), buffer.readInt(), buffer.readByte(), buffer.readLong(), buffer.readUUID(), buffer.readUUID()));
      }
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeResourceLocation(this.dimension.location());
      buffer.writeVarInt(this.domes.size());
      for (DomeInfo dome : this.domes) {
         buffer.writeUUID(dome.id());
         buffer.writeUUID(dome.caster());
         buffer.writeDouble(dome.x());
         buffer.writeDouble(dome.y());
         buffer.writeDouble(dome.z());
         buffer.writeDouble(dome.radius());
         buffer.writeLong(dome.createdAt());
         buffer.writeLong(dome.expiresAt());
         buffer.writeVarInt(dome.captured().size());
         for (UUID captured : dome.captured()) {
            buffer.writeUUID(captured);
         }
      }

      buffer.writeVarInt(this.controls.size());
      for (ControlInfo control : this.controls) {
         buffer.writeUUID(control.target());
         buffer.writeInt(control.entityId());
         buffer.writeByte(control.kindOrdinal());
         buffer.writeLong(control.expiresAt());
         buffer.writeUUID(control.caster());
         buffer.writeUUID(control.effectId());
      }
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHeroData.setControlSnapshot(this.domes, this.controls)));
      context.setPacketHandled(true);
   }

   /** A dome as the client sees it: identity, geometry, lifetime, occupants. */
   public record DomeInfo(UUID id, UUID caster, double x, double y, double z, double radius, long createdAt, long expiresAt, List<UUID> captured) {
   }

   /** A live control record: target identity + entity id, kind ordinal, expiry, owner. */
   public record ControlInfo(UUID target, int entityId, int kindOrdinal, long expiresAt, UUID caster, UUID effectId) {
   }
}
