package dev.baranhan.viltrumitecore.network.packet;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Cinematic hero subtitle: PLAY one line (who speaks, which hero portrait and
 * name, the text fragments as lang keys or literal text), STOP one line by its
 * serial (death, logout, hero or world change, interrupt) or CLEAR all.
 */
public record HeroLineS2CPacket(int op, int serial, UUID speaker, int entityId, String speakerName, String heroKey, String lineId,
   String speakerKey, List<String> fragments, boolean literal, int priority, int durationTicks) {
   public static final int PLAY = 0;
   public static final int STOP = 1;
   public static final int CLEAR = 2;
   public static final int MAX_FRAGMENTS = 16;
   public static final int MAX_ID = 128;
   public static final int MAX_NAME = 64;
   public static final int MAX_TEXT = 1024;
   public static final int MAX_DURATION = 20 * 600;
   private static final UUID NOBODY = new UUID(0L, 0L);

   public HeroLineS2CPacket {
      if (op < PLAY || op > CLEAR) {
         throw new IllegalArgumentException("bad hero line op " + op);
      }

      Objects.requireNonNull(speaker, "speaker");
      Objects.requireNonNull(speakerName, "speakerName");
      Objects.requireNonNull(heroKey, "heroKey");
      Objects.requireNonNull(lineId, "lineId");
      Objects.requireNonNull(speakerKey, "speakerKey");
      fragments = List.copyOf(fragments);
      if (fragments.size() > MAX_FRAGMENTS) {
         throw new IllegalArgumentException("too many fragments " + fragments.size());
      }

      if (op == PLAY && fragments.isEmpty()) {
         throw new IllegalArgumentException("a played line needs text");
      }

      if (priority < 0 || priority > 2 || durationTicks < 0 || durationTicks > MAX_DURATION) {
         throw new IllegalArgumentException("bad hero line timing");
      }
   }

   public static HeroLineS2CPacket play(int serial, UUID speaker, int entityId, String speakerName, String heroKey, String lineId, String speakerKey,
      List<String> fragments, boolean literal, int priority, int durationTicks) {
      return new HeroLineS2CPacket(PLAY, serial, speaker, entityId, speakerName, heroKey, lineId, speakerKey, fragments, literal, priority, durationTicks);
   }

   public static HeroLineS2CPacket stop(int serial) {
      return new HeroLineS2CPacket(STOP, serial, NOBODY, -1, "", "", "", "", List.of(), false, 0, 0);
   }

   public static HeroLineS2CPacket clear() {
      return new HeroLineS2CPacket(CLEAR, 0, NOBODY, -1, "", "", "", "", List.of(), false, 0, 0);
   }

   public HeroLineS2CPacket(FriendlyByteBuf b) {
      this(read(b));
   }

   private HeroLineS2CPacket(HeroLineS2CPacket copy) {
      this(copy.op, copy.serial, copy.speaker, copy.entityId, copy.speakerName, copy.heroKey, copy.lineId, copy.speakerKey, copy.fragments, copy.literal,
         copy.priority, copy.durationTicks);
   }

   private static HeroLineS2CPacket read(FriendlyByteBuf b) {
      int op = b.readVarInt();
      if (op == CLEAR) {
         return clear();
      }

      int serial = b.readVarInt();
      if (op == STOP) {
         return stop(serial);
      }

      if (op != PLAY) {
         throw new IllegalArgumentException("bad hero line op " + op);
      }

      UUID speaker = b.readUUID();
      int entityId = b.readVarInt();
      String name = b.readUtf(MAX_NAME);
      String hero = b.readUtf(MAX_ID);
      String line = b.readUtf(MAX_ID);
      String speakerKey = b.readUtf(MAX_ID);
      int count = b.readVarInt();
      if (count < 1 || count > MAX_FRAGMENTS) {
         throw new IllegalArgumentException("bad fragment count " + count);
      }

      String[] fragments = new String[count];
      for (int i = 0; i < count; i++) {
         fragments[i] = b.readUtf(MAX_TEXT);
      }

      boolean literal = b.readBoolean();
      int priority = b.readVarInt();
      int duration = b.readVarInt();
      return play(serial, speaker, entityId, name, hero, line, speakerKey, List.of(fragments), literal, priority, duration);
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeVarInt(this.op);
      if (this.op == CLEAR) {
         return;
      }

      b.writeVarInt(this.serial);
      if (this.op == STOP) {
         return;
      }

      b.writeUUID(this.speaker);
      b.writeVarInt(this.entityId);
      b.writeUtf(this.speakerName, MAX_NAME);
      b.writeUtf(this.heroKey, MAX_ID);
      b.writeUtf(this.lineId, MAX_ID);
      b.writeUtf(this.speakerKey, MAX_ID);
      b.writeVarInt(this.fragments.size());
      for (String fragment : this.fragments) {
         b.writeUtf(fragment, MAX_TEXT);
      }

      b.writeBoolean(this.literal);
      b.writeVarInt(this.priority);
      b.writeVarInt(this.durationTicks);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.hero.speech.HeroSubtitles.receive(this)));
      ctx.get().setPacketHandled(true);
   }
}
