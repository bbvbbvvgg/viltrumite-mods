package dev.baranhan.viltrumitecore.worldevent.ultron;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Ultron's lines. He has no recorded or synthesised voice: every line goes through the shared subtitle
 * system ({@link HeroSpeech#worldLine}) with his name and portrait, in the reader's language (Russian is
 * the reference text, all twelve locales are translated), and only a short transmission blip plays. A
 * line has its own cooldown; ambient remarks also keep a gap between each other so he never chatters.
 */
public final class UltronVoice {
   public static final String VOICE = "ultron";
   public static final String SPEAKER_KEY = "subtitle.viltrumitecore.speaker.ultron";
   public static final String PREFIX = "subtitle.viltrumitecore.ultron.";
   /** Ambient lines keep at least this many ticks between each other. */
   public static final int AMBIENT_GAP = 12 * UltronRules.TPS;
   /** Battle taunts, said in turn (the longest unsaid first); each waits this long before it may come again. */
   public static final List<String> TAUNTS = List.of("taunt_1", "taunt_2", "taunt_3", "taunt_4", "taunt_5", "taunt_6");
   public static final int TAUNT_COOLDOWN_S = 300;
   /** Queued lines (protocol news) wait at most this long for him to finish the line he reads; at most this many wait. */
   public static final int QUEUE_STALE = 20 * UltronRules.TPS;
   public static final int QUEUE_MAX = 3;
   /** A queued line cut by a forced one within this many ticks is put back at the head of the queue. */
   static final int REQUEUE_WINDOW = 2 * UltronRules.TPS;

   /** A line waiting for its turn. */
   record Pending(String id, int at) {
   }

   /** One line: pages (fragments), how hard it cuts in, its cooldown. */
   public record Line(String id, int pages, HeroLine.Priority priority, int cooldownSeconds) {
      public List<String> keys() {
         if (this.pages <= 1) {
            return List.of(PREFIX + this.id);
         }
         List<String> out = new ArrayList<>(this.pages);
         for (int i = 1; i <= this.pages; i++) {
            out.add(PREFIX + this.id + "." + i);
         }
         return out;
      }

      public String lineId() {
         return "viltrumitecore:ultron/" + this.id;
      }
   }

   private static final Map<String, Line> LINES = new LinkedHashMap<>();

   static {
      // phases
      line("awaken", 2, HeroLine.Priority.ULTIMATE, 0);
      line("core_online", 1, HeroLine.Priority.ABILITY, 0);
      line("spread", 1, HeroLine.Priority.ULTIMATE, 0);
      line("war", 1, HeroLine.Priority.ULTIMATE, 0);
      line("adaptation", 1, HeroLine.Priority.ULTIMATE, 0);
      line("finale", 2, HeroLine.Priority.ULTIMATE, 0);
      line("early_finale", 1, HeroLine.Priority.ULTIMATE, 0);
      // network
      line("node_online", 1, HeroLine.Priority.AMBIENT, 50);
      line("node_lost", 1, HeroLine.Priority.ABILITY, 35);
      line("foundry_lost", 1, HeroLine.Priority.ABILITY, 30);
      line("node_disabled", 1, HeroLine.Priority.ABILITY, 40);
      line("node_rebooted", 1, HeroLine.Priority.AMBIENT, 60);
      line("relocated", 1, HeroLine.Priority.ABILITY, 60);
      line("machines_lost", 1, HeroLine.Priority.AMBIENT, 70);
      // doctrines
      line("doctrine_expansion", 1, HeroLine.Priority.ABILITY, 60);
      line("doctrine_siege", 1, HeroLine.Priority.ABILITY, 60);
      line("doctrine_hunt", 1, HeroLine.Priority.ABILITY, 60);
      line("doctrine_assimilation", 1, HeroLine.Priority.ABILITY, 60);
      // adaptation protocols
      line("protocol_anti_air", 1, HeroLine.Priority.ABILITY, 20);
      line("protocol_deflector", 1, HeroLine.Priority.ABILITY, 20);
      line("protocol_relocation", 1, HeroLine.Priority.ABILITY, 20);
      line("protocol_redistribution", 1, HeroLine.Priority.ABILITY, 20);
      line("protocol_fortify", 1, HeroLine.Priority.ABILITY, 20);
      line("countered_anti_air", 1, HeroLine.Priority.AMBIENT, 30);
      line("countered_deflector", 1, HeroLine.Priority.AMBIENT, 30);
      line("frozen", 1, HeroLine.Priority.ABILITY, 90);
      // machines
      line("capture_golem", 1, HeroLine.Priority.ABILITY, 45);
      line("capture_suit", 1, HeroLine.Priority.ABILITY, 45);
      line("liberated", 1, HeroLine.Priority.AMBIENT, 45);
      for (String taunt : TAUNTS) {
         line(taunt, 1, HeroLine.Priority.AMBIENT, TAUNT_COOLDOWN_S);
      }
      // Ultron Prime
      line("prime_assembled", 1, HeroLine.Priority.ULTIMATE, 0);
      line("prime_tether", 1, HeroLine.Priority.ABILITY, 90);
      line("prime_aerial", 1, HeroLine.Priority.ULTIMATE, 0);
      line("prime_overdrive", 1, HeroLine.Priority.ULTIMATE, 0);
      line("prime_hurt", 1, HeroLine.Priority.AMBIENT, 60);
      line("prime_summon", 1, HeroLine.Priority.AMBIENT, 60);
      line("prime_absorb", 1, HeroLine.Priority.AMBIENT, 60);
      line("upload_start", 1, HeroLine.Priority.ABILITY, 0);
      line("upload_half", 1, HeroLine.Priority.ABILITY, 0);
      line("upload_near", 1, HeroLine.Priority.ABILITY, 0);
      // endings
      line("victory", 2, HeroLine.Priority.ULTIMATE, 0);
      line("defeat", 2, HeroLine.Priority.ULTIMATE, 0);
   }

   private UltronVoice() {
   }

   private static void line(String id, int pages, HeroLine.Priority priority, int cooldownSeconds) {
      LINES.put(id, new Line(id, pages, priority, cooldownSeconds));
   }

   public static List<Line> lines() {
      return List.copyOf(LINES.values());
   }

   @Nullable
   public static Line line(String id) {
      return LINES.get(id);
   }

   /** Every lang key the lines use (tests: all 12 locales have them). */
   public static List<String> keys() {
      List<String> out = new ArrayList<>();
      out.add(SPEAKER_KEY);
      for (Line l : LINES.values()) {
         out.addAll(l.keys());
      }
      return out;
   }

   /**
    * Says a line to the players in the event's range. {@code force}: cut whatever he says now (phase and
    * boss beats only); unforced ability lines wait their turn ({@link #sayNext}). False when the line is cooling
    * down, an ambient gap holds, he is busy (ambient remarks are skipped) or nobody listens.
    */
   static boolean say(ServerLevel level, UltronSession s, String id, boolean force) {
      Line line = LINES.get(id);
      if (line == null) {
         return false;
      }
      if (!force && line.priority == HeroLine.Priority.ABILITY) {
         // news (a capture, a loss, a protocol, the upload): never cuts the line he reads, never lost to it either
         return sayNext(level, s, id);
      }
      String cut = force ? s.voiceQueued : null;
      int cutAt = s.voiceQueuedAt;
      Integer cutSaid = cut == null ? null : s.said.get(cut);
      boolean said = speak(level, s, id, force);
      if (said) {
         s.voiceQueued = null;
         if (cut != null && !cut.equals(id) && s.clock - cutAt <= REQUEUE_WINDOW && cutSaid != null && cutSaid == cutAt) {
            // a phase or boss beat cut the news he had just begun: it comes again right after
            s.said.remove(cut);
            requeue(s, cut);
         }
      }
      return said;
   }

   /**
    * Says a line as soon as he is not reading another one (news that several systems may raise in the same second):
    * now when he is quiet, otherwise queued ({@link #QUEUE_MAX} at most, dropped after {@link #QUEUE_STALE}). Every
    * unforced {@link HeroLine.Priority#ABILITY} line comes this way. False when the line is cooling down or unheard.
    */
   static boolean sayNext(ServerLevel level, UltronSession s, String id) {
      Line line = LINES.get(id);
      if (line == null || cooling(s, line)) {
         return false;
      }
      if (s.voiceQueue.isEmpty() && !HeroSpeech.worldSpeaking(level.getServer(), VOICE)) {
         if (speak(level, s, id, true)) {
            s.voiceQueued = id;
            s.voiceQueuedAt = s.clock;
            return true;
         }
         return false;
      }
      enqueue(s, id);
      return true;
   }

   private static boolean cooling(UltronSession s, Line line) {
      Integer last = s.said.get(line.id);
      return last != null && line.cooldownSeconds > 0 && s.clock - last < line.cooldownSeconds * UltronRules.TPS;
   }

   /** Every second (with listeners): the next queued line once he is quiet. */
   static void second(ServerLevel level, UltronSession s) {
      s.voiceQueue.removeIf(p -> s.clock - p.at() > QUEUE_STALE);
      if (s.voiceQueue.isEmpty() || HeroSpeech.worldSpeaking(level.getServer(), VOICE)) {
         return;
      }
      Pending next = s.voiceQueue.pollFirst();
      if (speak(level, s, next.id(), true)) {
         s.voiceQueued = next.id();
         s.voiceQueuedAt = s.clock;
      }
   }

   private static void enqueue(UltronSession s, String id) {
      enqueue(s.voiceQueue, id, s.clock);
   }

   private static void requeue(UltronSession s, String id) {
      requeue(s.voiceQueue, id, s.clock);
   }

   /** Pure: adds a line at the tail (once; the oldest goes when full). */
   static void enqueue(Deque<Pending> queue, String id, int clock) {
      queue.removeIf(p -> p.id().equals(id));
      while (queue.size() >= QUEUE_MAX) {
         queue.pollFirst();
      }
      queue.addLast(new Pending(id, clock));
   }

   /** Pure: puts a cut line back at the head (once; the newest goes when full). */
   static void requeue(Deque<Pending> queue, String id, int clock) {
      queue.removeIf(p -> p.id().equals(id));
      queue.addFirst(new Pending(id, clock));
      while (queue.size() > QUEUE_MAX) {
         queue.pollLast();
      }
   }

   @Nullable
   static String nextTaunt(UltronSession s) {
      return nextTaunt(s.said, s.clock);
   }

   /** Pure: the taunt to try now — of those off cooldown, the one never said or said longest ago (null: all cooling down). */
   @Nullable
   static String nextTaunt(Map<String, Integer> said, int clock) {
      String best = null;
      int bestAt = Integer.MAX_VALUE;
      for (String id : TAUNTS) {
         Integer last = said.get(id);
         if (last != null && clock - last < TAUNT_COOLDOWN_S * UltronRules.TPS) {
            continue;
         }
         int at = last == null ? Integer.MIN_VALUE : last;
         if (at < bestAt) {
            bestAt = at;
            best = id;
         }
      }
      return best;
   }

   private static boolean speak(ServerLevel level, UltronSession s, String id, boolean force) {
      Line line = LINES.get(id);
      if (line == null) {
         return false;
      }
      int now = s.clock;
      if (cooling(s, line)) {
         return false;
      }
      if (line.priority == HeroLine.Priority.AMBIENT) {
         Integer ambient = s.said.get("#ambient");
         if (ambient != null && now - ambient < AMBIENT_GAP) {
            return false;
         }
      }
      int speaker = -1;
      if (s.boss != null && level.getEntity(s.boss) != null) {
         speaker = level.getEntity(s.boss).getId();
      }
      boolean said = HeroSpeech.worldLine(level, VOICE, speaker, SPEAKER_KEY, "Ultron", line.lineId(), line.keys(), line.priority, 0, s.anchor,
         UltronRules.VIEW_RANGE, force || line.priority == HeroLine.Priority.ULTIMATE);
      if (said) {
         s.said.put(id, now);
         if (line.priority == HeroLine.Priority.AMBIENT) {
            s.said.put("#ambient", now);
         }
         for (ServerPlayer p : UltronDirector.viewers(level, s)) {
            p.playNotifySound(UltronSounds.TRANSMISSION.get(), SoundSource.HOSTILE, 0.7F, 1.0F);
         }
      }
      return said;
   }
}
