package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.book.BookPages;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Heaven's Door book session for one client (docs/rohan/heavens-door-design.md §4): the reader gets OPEN (pages,
 * the server-made instruction list with refusals, parameter candidates and point presets), UPDATE (time and lines left,
 * ink), RESULT of a line and CLOSE; an opened player gets VICTIM / VICTIM_END (struggle prompt). The client only ever
 * echoes ids and indices of these lists back (RohanBookC2SPacket); the server re-validates everything.
 */
public final class RohanBookS2CPacket {
   public static final int OPEN = 0;
   public static final int UPDATE = 1;
   public static final int RESULT = 2;
   public static final int CLOSE = 3;
   public static final int VICTIM = 4;
   public static final int VICTIM_END = 5;
   static final int MAX_LIST = 32;

   /** One writable line as offered by the server ({@code refusal} empty = allowed). */
   public record Entry(String id, int param, float cost, String refusal) {
   }

   /** A body the ENTITY parameter may name. */
   public record Candidate(int entityId, String name) {
   }

   /** A place the POINT parameter may name (lang key label). */
   public record Point(BlockPos pos, String label) {
   }

   public final int mode;
   public final int session;
   public int targetId = -1;
   public int[] targets = new int[0];
   public String targetName = "";
   @Nullable
   public BookPages pages;
   public int ticks;
   public int lines;
   /** Bits: 1 read-only, 2 partial, 4 mass session. */
   public int bookFlags;
   public float ink;
   public List<Entry> entries = new ArrayList<>();
   public List<Candidate> candidates = new ArrayList<>();
   public List<Point> points = new ArrayList<>();
   public boolean ok;
   public String key = "";
   public List<String> args = new ArrayList<>();

   public RohanBookS2CPacket(int mode, int session) {
      this.mode = mode;
      this.session = session;
   }

   public static RohanBookS2CPacket update(int session, int ticks, int lines, float ink) {
      RohanBookS2CPacket p = new RohanBookS2CPacket(UPDATE, session);
      p.ticks = ticks;
      p.lines = lines;
      p.ink = ink;
      return p;
   }

   public static RohanBookS2CPacket result(int session, boolean ok, String key, String... args) {
      RohanBookS2CPacket p = new RohanBookS2CPacket(RESULT, session);
      p.ok = ok;
      p.key = key;
      p.args = new ArrayList<>(List.of(args));
      return p;
   }

   public static RohanBookS2CPacket close(int session) {
      return new RohanBookS2CPacket(CLOSE, session);
   }

   public static RohanBookS2CPacket victim(int session, int readerId, int ticks) {
      RohanBookS2CPacket p = new RohanBookS2CPacket(VICTIM, session);
      p.targetId = readerId;
      p.ticks = ticks;
      return p;
   }

   public static RohanBookS2CPacket victimEnd(int session) {
      return new RohanBookS2CPacket(VICTIM_END, session);
   }

   public RohanBookS2CPacket(FriendlyByteBuf buf) {
      this.mode = buf.readByte();
      this.session = buf.readVarInt();
      switch (this.mode) {
         case OPEN -> {
            this.targetId = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), MAX_LIST);
            this.targets = new int[n];
            for (int i = 0; i < n; i++) {
               this.targets[i] = buf.readVarInt();
            }
            this.targetName = buf.readUtf(96);
            this.pages = BookPages.read(buf);
            this.ticks = buf.readVarInt();
            this.lines = buf.readVarInt();
            this.bookFlags = buf.readByte();
            this.ink = buf.readFloat();
            int e = Math.min(buf.readVarInt(), MAX_LIST);
            for (int i = 0; i < e; i++) {
               this.entries.add(new Entry(buf.readUtf(48), buf.readByte(), buf.readFloat(), buf.readUtf(48)));
            }
            int c = Math.min(buf.readVarInt(), MAX_LIST);
            for (int i = 0; i < c; i++) {
               this.candidates.add(new Candidate(buf.readVarInt(), buf.readUtf(96)));
            }
            int p = Math.min(buf.readVarInt(), MAX_LIST);
            for (int i = 0; i < p; i++) {
               this.points.add(new Point(buf.readBlockPos(), buf.readUtf(96)));
            }
         }
         case UPDATE -> {
            this.ticks = buf.readVarInt();
            this.lines = buf.readVarInt();
            this.ink = buf.readFloat();
         }
         case RESULT -> {
            this.ok = buf.readBoolean();
            this.key = buf.readUtf(96);
            int n = Math.min(buf.readVarInt(), 4);
            for (int i = 0; i < n; i++) {
               this.args.add(buf.readUtf(96));
            }
         }
         case VICTIM -> {
            this.targetId = buf.readVarInt();
            this.ticks = buf.readVarInt();
         }
         default -> {
         }
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.mode);
      buf.writeVarInt(this.session);
      switch (this.mode) {
         case OPEN -> {
            buf.writeVarInt(this.targetId);
            int n = Math.min(this.targets.length, MAX_LIST);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
               buf.writeVarInt(this.targets[i]);
            }
            buf.writeUtf(clip(this.targetName), 96);
            (this.pages == null ? new BookPages() : this.pages).write(buf);
            buf.writeVarInt(this.ticks);
            buf.writeVarInt(this.lines);
            buf.writeByte(this.bookFlags);
            buf.writeFloat(this.ink);
            List<Entry> e = this.entries.subList(0, Math.min(this.entries.size(), MAX_LIST));
            buf.writeVarInt(e.size());
            for (Entry x : e) {
               buf.writeUtf(x.id(), 48);
               buf.writeByte(x.param());
               buf.writeFloat(x.cost());
               buf.writeUtf(x.refusal(), 48);
            }
            List<Candidate> c = this.candidates.subList(0, Math.min(this.candidates.size(), MAX_LIST));
            buf.writeVarInt(c.size());
            for (Candidate x : c) {
               buf.writeVarInt(x.entityId());
               buf.writeUtf(clip(x.name()), 96);
            }
            List<Point> p = this.points.subList(0, Math.min(this.points.size(), MAX_LIST));
            buf.writeVarInt(p.size());
            for (Point x : p) {
               buf.writeBlockPos(x.pos());
               buf.writeUtf(clip(x.label()), 96);
            }
         }
         case UPDATE -> {
            buf.writeVarInt(this.ticks);
            buf.writeVarInt(this.lines);
            buf.writeFloat(this.ink);
         }
         case RESULT -> {
            buf.writeBoolean(this.ok);
            buf.writeUtf(clip(this.key), 96);
            int n = Math.min(this.args.size(), 4);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
               buf.writeUtf(clip(this.args.get(i)), 96);
            }
         }
         case VICTIM -> {
            buf.writeVarInt(this.targetId);
            buf.writeVarInt(this.ticks);
         }
         default -> {
         }
      }
   }

   private static String clip(String s) {
      return s == null ? "" : s.length() > 96 ? s.substring(0, 96) : s;
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.rohan.RohanClientNet.receiveBook(this)));
      context.setPacketHandled(true);
   }
}
