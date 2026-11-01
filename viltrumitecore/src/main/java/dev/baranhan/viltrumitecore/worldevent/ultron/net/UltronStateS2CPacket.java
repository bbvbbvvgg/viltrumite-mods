package dev.baranhan.viltrumitecore.worldevent.ultron.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * What a player in range knows about the uprising (HUD, node markers, tethers, loops): the phase and its
 * progress, the doctrine, the running protocols, the nodes (positions and links only once discovered),
 * Ultron Prime and the upload, the machines Ultron controls, and the player's own hack progress. Sent
 * every half second to the viewers, at once on changes, and {@link #inactive()} when the event ends or the
 * player leaves its range.
 */
public record UltronStateS2CPacket(boolean active, int phase, float progress, int variant, int doctrine, int outcome, double ax, double az, float radius,
   int[] protocolSeconds, int[] protocolLevel, List<Node> nodes, int bossId, int bossStage, float bossHealth, float upload, boolean uploading, int[] captured,
   int units, float hack, int hackNode) {
   public static final int MAX_NODES = 16;
   public static final int MAX_CAPTURED = 16;
   public static final int FLAG_POWERED = 1;
   public static final int FLAG_DISCOVERED = 2;
   public static final int FLAG_FORTIFIED = 4;
   public static final int FLAG_REBOOTING = 8;
   public static final int OUTCOME_NONE = 0;
   public static final int OUTCOME_VICTORY = 1;
   public static final int OUTCOME_DEFEAT = 2;

   /** One node as the client may know it (coordinates are zero while undiscovered). */
   public record Node(int id, int kind, int state, int flags, float hull, float shield, float build, int x, int y, int z, int[] links) {
      public boolean discovered() {
         return (this.flags & FLAG_DISCOVERED) != 0;
      }

      public boolean powered() {
         return (this.flags & FLAG_POWERED) != 0;
      }

      public boolean fortified() {
         return (this.flags & FLAG_FORTIFIED) != 0;
      }
   }

   public static UltronStateS2CPacket inactive() {
      return new UltronStateS2CPacket(false, 0, 0.0F, 0, 0, OUTCOME_NONE, 0.0, 0.0, 0.0F, new int[0], new int[0], List.of(), -1, 0, 0.0F, 0.0F, false,
         new int[0], 0, 0.0F, -1);
   }

   /** The same state with this player's own hack progress. */
   public UltronStateS2CPacket withHack(float hack, int node) {
      return new UltronStateS2CPacket(this.active, this.phase, this.progress, this.variant, this.doctrine, this.outcome, this.ax, this.az, this.radius,
         this.protocolSeconds, this.protocolLevel, this.nodes, this.bossId, this.bossStage, this.bossHealth, this.upload, this.uploading, this.captured,
         this.units, hack, node);
   }

   public UltronStateS2CPacket(FriendlyByteBuf b) {
      this(b.readBoolean(), b.readVarInt(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readFloat(),
         readInts(b, 8), readInts(b, 8), readNodes(b), b.readInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readBoolean(), readInts(b, MAX_CAPTURED),
         b.readVarInt(), b.readFloat(), b.readVarInt() - 1);
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeBoolean(this.active);
      b.writeVarInt(this.phase);
      b.writeFloat(this.progress);
      b.writeVarInt(this.variant);
      b.writeVarInt(this.doctrine);
      b.writeVarInt(this.outcome);
      b.writeDouble(this.ax);
      b.writeDouble(this.az);
      b.writeFloat(this.radius);
      writeInts(b, this.protocolSeconds, 8);
      writeInts(b, this.protocolLevel, 8);
      int n = Math.min(MAX_NODES, this.nodes.size());
      b.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         Node node = this.nodes.get(i);
         b.writeVarInt(node.id);
         b.writeVarInt(node.kind);
         b.writeVarInt(node.state);
         b.writeVarInt(node.flags);
         b.writeFloat(node.hull);
         b.writeFloat(node.shield);
         b.writeFloat(node.build);
         b.writeInt(node.x);
         b.writeInt(node.y);
         b.writeInt(node.z);
         writeInts(b, node.links, 8);
      }
      b.writeInt(this.bossId);
      b.writeVarInt(this.bossStage);
      b.writeFloat(this.bossHealth);
      b.writeFloat(this.upload);
      b.writeBoolean(this.uploading);
      writeInts(b, this.captured, MAX_CAPTURED);
      b.writeVarInt(this.units);
      b.writeFloat(this.hack);
      b.writeVarInt(this.hackNode + 1);
   }

   private static List<Node> readNodes(FriendlyByteBuf b) {
      int n = Math.min(MAX_NODES, b.readVarInt());
      List<Node> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         out.add(new Node(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readInt(), b.readInt(),
            b.readInt(), readInts(b, 8)));
      }
      return out;
   }

   private static int[] readInts(FriendlyByteBuf b, int max) {
      int n = b.readVarInt();
      if (n < 0 || n > max) {
         throw new IllegalArgumentException("bad ultron list size " + n);
      }
      int[] out = new int[n];
      for (int i = 0; i < n; i++) {
         out[i] = b.readVarInt();
      }
      return out;
   }

   private static void writeInts(FriendlyByteBuf b, int[] values, int max) {
      int n = Math.min(max, values.length);
      b.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         b.writeVarInt(values[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.ultron.UltronClientState.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
