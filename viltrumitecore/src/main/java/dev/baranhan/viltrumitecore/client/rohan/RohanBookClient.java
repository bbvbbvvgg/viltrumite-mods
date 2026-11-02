package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.hero.book.BookPages;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.RohanBookS2CPacket;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client state of Heaven's Door books: the session this player reads (opens {@link HeavensDoorScreen}) and, when
 * this player's own body was opened, the victim state ({@link RohanVictimHud}). The server owns everything; the
 * client only shows it and sends choices by index.
 */
public final class RohanBookClient {
   /** Prefix of an argument that is a lang key (BookReader.TRANSLATE). */
   public static final String TRANSLATE = "@";
   @Nullable
   static Session session;
   @Nullable
   static Victim victim;

   private RohanBookClient() {
   }

   public static final class Session {
      public final int id;
      public int targetId;
      public int[] targets = new int[0];
      public String targetName = "";
      public BookPages pages = new BookPages();
      public int ticks;
      public long ticksAt;
      public int lines;
      public int flags;
      public float ink;
      public List<RohanBookS2CPacket.Entry> entries = new ArrayList<>();
      public List<RohanBookS2CPacket.Candidate> candidates = new ArrayList<>();
      public List<RohanBookS2CPacket.Point> points = new ArrayList<>();
      public final List<Component> written = new ArrayList<>();
      @Nullable
      public Component result;
      public boolean resultOk;
      public long resultAt;

      Session(int id) {
         this.id = id;
      }

      public boolean readOnly() {
         return (this.flags & 1) != 0;
      }

      public boolean partial() {
         return (this.flags & 2) != 0;
      }

      public boolean mass() {
         return (this.flags & 4) != 0;
      }

      /** Seconds left (client-extrapolated between server updates). */
      public float secondsLeft() {
         long now = gameTime();
         return Math.max(0.0F, (this.ticks - (now - this.ticksAt)) / 20.0F);
      }
   }

   public static final class Victim {
      public final int session;
      public final int readerId;
      public final int ticks;
      public final long at;
      long lastStruggle;

      Victim(int session, int readerId, int ticks, long at) {
         this.session = session;
         this.readerId = readerId;
         this.ticks = ticks;
         this.at = at;
      }

      public float secondsLeft() {
         return Math.max(0.0F, (this.ticks - (gameTime() - this.at)) / 20.0F);
      }
   }

   static long gameTime() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
   }

   @Nullable
   public static Session session() {
      return session;
   }

   @Nullable
   public static Victim victim() {
      return victim;
   }

   static void receive(RohanBookS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      switch (p.mode) {
         case RohanBookS2CPacket.OPEN -> {
            Session s = new Session(p.session);
            s.targetId = p.targetId;
            s.targets = p.targets;
            s.targetName = p.targetName;
            s.pages = p.pages == null ? new BookPages() : p.pages;
            s.ticks = p.ticks;
            s.ticksAt = gameTime();
            s.lines = p.lines;
            s.flags = p.bookFlags;
            s.ink = p.ink;
            s.entries = new ArrayList<>(p.entries);
            s.candidates = new ArrayList<>(p.candidates);
            s.points = new ArrayList<>(p.points);
            session = s;
            if (mc.screen instanceof HeavensDoorScreen screen) {
               screen.replace(s);
            } else {
               mc.setScreen(new HeavensDoorScreen(s));
            }
         }
         case RohanBookS2CPacket.UPDATE -> {
            Session s = session;
            if (s != null && s.id == p.session) {
               s.ticks = p.ticks;
               s.ticksAt = gameTime();
               s.lines = p.lines;
               s.ink = p.ink;
            }
         }
         case RohanBookS2CPacket.RESULT -> {
            Session s = session;
            if (s != null && s.id == p.session) {
               s.resultOk = p.ok;
               s.resultAt = gameTime();
               if (p.ok) {
                  s.result = Component.translatable("book.viltrumitecore.result." + p.key, args(p.args));
                  if ("written".equals(p.key) && !p.args.isEmpty()) {
                     s.written.add(arg(p.args.get(0)));
                  }
               } else {
                  s.result = Component.translatable("gui.viltrumitecore.rohan.refusal", Component.translatable("book.viltrumitecore.refuse." + p.key));
               }
            }
         }
         case RohanBookS2CPacket.CLOSE -> {
            Session s = session;
            if (s != null && s.id == p.session) {
               session = null;
               if (mc.screen instanceof HeavensDoorScreen screen) {
                  screen.serverClosed();
               }
            }
         }
         case RohanBookS2CPacket.VICTIM -> victim = new Victim(p.session, p.targetId, p.ticks, gameTime());
         case RohanBookS2CPacket.VICTIM_END -> victim = null;
         default -> {
         }
      }
   }

   /** Called by the screen when the reader closes the book (Esc / button). */
   static void closeByReader(Session s) {
      if (session == s) {
         session = null;
      }
      CoreMessages.sendToServer(new RohanBookC2SPacket(RohanBookC2SPacket.CLOSE, s.id, "", 0));
   }

   static void send(int op, Session s, String instruction, int param) {
      CoreMessages.sendToServer(new RohanBookC2SPacket(op, s.id, instruction, param));
   }

   static void struggle() {
      Victim v = victim;
      if (v == null) {
         return;
      }
      long now = gameTime();
      if (now - v.lastStruggle < 3) {
         return;
      }
      v.lastStruggle = now;
      CoreMessages.sendToServer(new RohanBookC2SPacket(RohanBookC2SPacket.STRUGGLE, v.session, "", 0));
   }

   /** Clean-up on logout / level change / expiry. */
   static void tick() {
      Victim v = victim;
      if (v != null && gameTime() - v.at > v.ticks + 60) {
         victim = null;
      }
      Session s = session;
      if (s != null && gameTime() - s.ticksAt > s.ticks + 100) {
         session = null;
      }
   }

   static void reset() {
      session = null;
      victim = null;
   }

   /** A book argument: "@key" is translated on this client, anything else is shown as is. */
   public static Component arg(String a) {
      if (a.startsWith(TRANSLATE) && a.length() > 1) {
         return Component.translatable(a.substring(1));
      }
      return Component.literal(a);
   }

   public static Object[] args(List<String> raw) {
      Object[] out = new Object[raw.size()];
      for (int i = 0; i < out.length; i++) {
         out[i] = arg(raw.get(i));
      }
      return out;
   }

   public static MutableComponent line(BookPages.Line line) {
      return Component.translatable(line.key(), args(line.args()));
   }
}
