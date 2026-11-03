package dev.baranhan.viltrumitecore.city.life;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.city.citizen.CitizenTraits;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The voice of the living city (issue #136), server side, one per {@link CityManager}:
 * <ul>
 * <li>the {@link CityMood} of every part of the city, fed by the danger map (threats: alarm; shock waves and
 * destruction: alarm and trauma) and by citizens running in panic;</li>
 * <li>voiced conversations: a chat that starts ({@code CitizenGoals.Talk}) picks a dialogue for the mood, the time
 * of day and the weather that was not heard nearby lately, and the two speak it line by line with their own
 * voices from where they stand; at most {@link #VOICED_NEAR} voiced chats within {@link #NEAR} blocks and
 * {@link #VOICED_MAX} in the whole city, the others chat silently (gestures only);</li>
 * <li>calls by what happens to a citizen (startled: a scream or "what is that?!", fleeing: "run!", hiding:
 * "help!", getting up and recovering: coughs, "anyone alive?"), and in the aftermath coughs, sobbing and
 * survivors calling, rate limited per {@link #CALL_CELL}-block cell and per citizen.</li>
 * </ul>
 * Every sound is played at the citizen (it follows him and is heard only by players in range), with the event
 * of the voice of that citizen; the client ({@code client.city.life.CityLifeVoices}) adds occlusion, the source
 * limits and the gestures of the speaker.
 */
public final class CityLife {
   public static final String NS = "viltrumitecore";
   public static final double NEAR = 20.0;
   public static final int VOICED_NEAR = 2;
   public static final int VOICED_MAX = 16;
   /** The same dialogue is not heard again within this many blocks... */
   public static final double REPEAT_RANGE = 48.0;
   /** ...for this many ticks. */
   public static final long REPEAT_TICKS = 6000L;
   public static final int CALL_CELL = 16;
   /** Calls of one cell: at least this many ticks apart... */
   public static final int CALL_SPACING = 12;
   /** ...and at most {@link #CALL_BURST} within {@link #CALL_WINDOW} ticks. */
   public static final int CALL_BURST = 3;
   public static final int CALL_WINDOW = 40;
   public static final int CITIZEN_COOLDOWN = 120;
   public static final float TALK_VOLUME = 1.0F;
   public static final float CALL_VOLUME = 2.4F;
   public static final float SCREAM_VOLUME = 2.6F;
   public static final float QUIET_VOLUME = 1.0F;

   private final CityManager city;
   public final CityMood mood = new CityMood();
   private final Int2ObjectOpenHashMap<CityConversation> talks = new Int2ObjectOpenHashMap<>();
   private final List<Talk> voiced = new ArrayList<>();
   private final ArrayDeque<long[]> recent = new ArrayDeque<>();
   private final Long2ObjectOpenHashMap<long[]> callCells = new Long2ObjectOpenHashMap<>();
   private final Int2LongOpenHashMap lastCall = new Int2LongOpenHashMap();
   private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
   // QA counters
   int talksVoiced;
   int talksSilent;
   int lines;
   int calls;
   int screams;
   int coughs;
   int sobs;
   int limited;

   private record Talk(CitizenEntity a, CitizenEntity b, CityConversation conversation) {
   }

   public CityLife(CityManager city) {
      this.city = city;
   }

   @Nullable
   public static CityLife of(Level level) {
      CityManager m = CityManager.of(level);
      return m == null ? null : m.life();
   }

   private long now() {
      return this.city.now();
   }

   private RandomSource random() {
      return this.city.level().random;
   }

   // ---------------------------------------------------------------- mood inputs

   /** A danger zone (a threat is there): people get nervous, nothing happened to them yet. */
   public void onDanger(double x, double z, float radius) {
      this.mood.hit(x, z, radius, 0.55F, 0.02F, this.now());
   }

   /** A shock wave (hit, explosion, roar): fear and a little trauma, more the bigger it is. */
   public void onBlast(double x, double z, float radius) {
      this.mood.hit(x, z, radius, 1.0F, Math.min(0.45F, 0.08F + radius * 0.025F), this.now());
   }

   /** Many blocks changed at once: something is being destroyed there. */
   public void onBurst(double x, double z, int blocks) {
      this.mood.hit(x, z, 16.0 + Math.min(32.0, blocks * 0.25), 1.0F, blocks >= 30 ? 0.6F : 0.3F, this.now());
   }

   // ---------------------------------------------------------------- voices of citizens

   /** Pure: the voice (CityLifeCatalog.VOICES index) of a seed: two men's and two women's voices. */
   public static int voiceOf(int seed, boolean female) {
      boolean second = (CitizenTraits.mix(seed, 301) & 1) != 0;
      return CityLifeCatalog.voice(female ? (second ? "f2" : "f1") : (second ? "m2" : "m1"));
   }

   /** Pure: the playback pitch of a seed (0.94..1.06): no two people sound quite alike. */
   public static float pitchOf(int seed) {
      return 0.94F + CitizenTraits.unit(seed, 302) * 0.12F;
   }

   /**
    * Pure: the voice the answering citizen speaks with in a voiced chat: their own, unless it is the same voice as
    * the one who started (two copies of one voice talking to each other sound like one person) - then the other
    * voice of the same sex (m1 and m2, f1 and f2).
    */
   public static int partnerVoice(int starter, int partner) {
      if (starter != partner) {
         return partner;
      }

      String id = CityLifeCatalog.VOICES[partner];
      int other = CityLifeCatalog.voice(id.charAt(0) + (id.charAt(1) == '1' ? "2" : "1"));
      return other < 0 ? partner : other;
   }

   static int voice(CitizenEntity c) {
      return voiceOf(c.skin(), c.look().female());
   }

   static float pitch(CitizenEntity c) {
      return pitchOf(c.skin());
   }

   private static SoundEvent event(String path) {
      return EVENTS.computeIfAbsent(path, p -> SoundEvent.createVariableRangeEvent(new ResourceLocation(NS, p)));
   }

   /** The event of voice {@code v} saying line {@code l}. */
   public static String lineEvent(int v, int l) {
      return "city_life.voice." + CityLifeCatalog.VOICES[v] + "." + CityLifeCatalog.LINES[l];
   }

   private void play(CitizenEntity c, String path, float volume, float pitch) {
      if (c.level() instanceof ServerLevel level) {
         level.playSound(null, c, event(path), SoundSource.NEUTRAL, volume, pitch);
      }
   }

   // ---------------------------------------------------------------- conversations

   /** Pure: how much more or less people start chatting in a mood, at a time (-1 night) and in rain. */
   public static float chatFactor(CityMood.Mood mood, int time, boolean rain) {
      float f = switch (mood) {
         case CALM -> 1.0F;
         case WARY -> 0.45F;
         case AFTER -> 0.7F;
         case PANIC -> 0.0F;
      };
      if (time == -1) {
         f *= 0.45F;
      }

      return rain ? f * 0.8F : f;
   }

   public float chatFactor(CitizenEntity c) {
      Level level = c.level();
      return chatFactor(this.mood.mood(c.getX(), c.getZ(), this.now()), CityConversation.timeClass(level.getDayTime()), level.isRaining());
   }

   /**
    * A chat between {@code a} (starts) and {@code b} begins: a voiced conversation when there is room for one
    * here, else null (they chat silently).
    */
   @Nullable
   public CityConversation startTalk(CitizenEntity a, CitizenEntity b) {
      long now = this.now();
      this.cleanTalks(now);
      CityMood.Mood m = this.mood.mood(a.getX(), a.getZ(), now);
      int near = 0;
      for (Talk t : this.voiced) {
         if (t.a().distanceToSqr(a) < NEAR * NEAR) {
            near++;
         }
      }

      if (m == CityMood.Mood.PANIC || near >= VOICED_NEAR || this.voiced.size() >= VOICED_MAX) {
         this.talksSilent++;
         return null;
      }

      Level level = a.level();
      int d = CityConversation.pick(m.catalog(), CityConversation.timeClass(level.getDayTime()), level.isRaining(),
         k -> this.heardLately(k, a.getX(), a.getZ(), now), this.random().nextFloat());
      if (d < 0) {
         this.talksSilent++;
         return null;
      }

      int[] gaps = new int[CityLifeCatalog.TURNS[d].length];
      for (int i = 0; i < gaps.length; i++) {
         gaps[i] = CityConversation.GAP_MIN + this.random().nextInt(CityConversation.GAP_RANDOM);
      }

      CityConversation conv = new CityConversation(d, voice(a), partnerVoice(voice(a), voice(b)), pitch(a), pitch(b), now, gaps);
      this.talks.put(a.getId(), conv);
      this.talks.put(b.getId(), conv);
      this.voiced.add(new Talk(a, b, conv));
      if (this.recent.size() >= 64) {
         this.recent.removeFirst();
      }

      this.recent.addLast(new long[]{d, (long)a.getX(), (long)a.getZ(), now});
      this.talksVoiced++;
      return conv;
   }

   private boolean heardLately(int dialogue, double x, double z, long now) {
      for (long[] r : this.recent) {
         if (r[0] == dialogue && now - r[3] < REPEAT_TICKS && (r[1] - x) * (r[1] - x) + (r[2] - z) * (r[2] - z) < REPEAT_RANGE * REPEAT_RANGE) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public CityConversation talkOf(CitizenEntity c) {
      return this.talks.get(c.getId());
   }

   /** Called every tick by the goal of the one who started the chat: says the line that is due. */
   public void tickTalk(CitizenEntity a, CitizenEntity b, CityConversation conv) {
      int t = conv.next(this.now());
      if (t < 0) {
         return;
      }

      int role = t >>> 16;
      CitizenEntity speaker = role == 0 ? a : b;
      this.play(speaker, lineEvent(conv.voices[role], t & 0xFFFF), TALK_VOLUME, conv.pitches[role]);
      this.lines++;
   }

   /** The chat of {@code c} ended (finished, interrupted, one of them left). */
   public void endTalk(CitizenEntity c) {
      CityConversation conv = this.talks.remove(c.getId());
      if (conv == null) {
         return;
      }

      Iterator<Talk> it = this.voiced.iterator();
      while (it.hasNext()) {
         Talk t = it.next();
         if (t.conversation() == conv) {
            this.talks.remove(t.a().getId());
            this.talks.remove(t.b().getId());
            it.remove();
         }
      }
   }

   private void cleanTalks(long now) {
      Iterator<Talk> it = this.voiced.iterator();
      while (it.hasNext()) {
         Talk t = it.next();
         if (!t.a().isAlive() || !t.b().isAlive() || now > t.conversation().endsAt() + 200L) {
            this.talks.remove(t.a().getId());
            this.talks.remove(t.b().getId());
            it.remove();
         }
      }
   }

   // ---------------------------------------------------------------- calls

   /** Pure: whether a cell with these last call ticks (ring, oldest first) may start one more at {@code now}. */
   static boolean cellAllows(long[] ring, long now) {
      long last = Long.MIN_VALUE;
      long oldest = Long.MAX_VALUE;
      for (long t : ring) {
         last = Math.max(last, t);
         oldest = Math.min(oldest, t);
      }

      return (last == Long.MIN_VALUE || now - last >= CALL_SPACING) && (oldest == Long.MIN_VALUE || now - oldest >= CALL_WINDOW);
   }

   private boolean mayCall(CitizenEntity c, long now) {
      if (now - this.lastCall.getOrDefault(c.getId(), Long.MIN_VALUE / 2) < CITIZEN_COOLDOWN) {
         this.limited++;
         return false;
      }

      long key = (long)Math.floorDiv(c.getBlockX(), CALL_CELL) << 32 | Math.floorDiv(c.getBlockZ(), CALL_CELL) & 0xFFFFFFFFL;
      long[] ring = this.callCells.get(key);
      if (ring == null) {
         ring = new long[CALL_BURST];
         java.util.Arrays.fill(ring, Long.MIN_VALUE);
         this.callCells.put(key, ring);
      }

      if (!cellAllows(ring, now)) {
         this.limited++;
         return false;
      }

      int oldest = 0;
      for (int i = 1; i < ring.length; i++) {
         if (ring[i] < ring[oldest]) {
            oldest = i;
         }
      }

      ring[oldest] = now;
      this.lastCall.put(c.getId(), now);
      return true;
   }

   private void call(CitizenEntity c, int[] pool) {
      int v = voice(c);
      int l = pool[this.random().nextInt(pool.length)];
      this.play(c, lineEvent(v, l), CALL_VOLUME, pitch(c));
      this.calls++;
   }

   private void scream(CitizenEntity c) {
      this.play(c, "city_life.scream." + (c.look().female() ? "female" : "male"), SCREAM_VOLUME, pitch(c) * (0.96F + this.random().nextFloat() * 0.08F));
      this.screams++;
   }

   private void cough(CitizenEntity c) {
      this.play(c, "city_life.cough", QUIET_VOLUME, pitch(c));
      this.coughs++;
   }

   private void sob(CitizenEntity c) {
      this.play(c, "city_life.sob." + (c.look().female() ? "female" : "male"), QUIET_VOLUME, pitch(c));
      this.sobs++;
   }

   /** The state of a citizen changed (server side): what he says or shouts about it. */
   public void onState(CitizenEntity c, CitizenState from, CitizenState to) {
      if (from == CitizenState.TALKING) {
         this.endTalk(c);
      }

      long now = this.now();
      RandomSource r = this.random();
      float fear = 1.0F - c.traits().bravery();
      switch (to) {
         case STARTLED -> {
            float p = r.nextFloat();
            if (p < 0.25F + fear * 0.35F) {
               if (this.mayCall(c, now)) {
                  this.scream(c);
               }
            } else if (p < 0.8F && this.mayCall(c, now)) {
               this.call(c, CityLifeCatalog.CALL_STARTLE);
            }
         }
         case FLEEING -> {
            if (r.nextFloat() < 0.3F && this.mayCall(c, now)) {
               this.call(c, CityLifeCatalog.CALL_FLEE);
            }
         }
         case COVER -> {
            if (r.nextFloat() < 0.3F && this.mayCall(c, now)) {
               this.call(c, CityLifeCatalog.CALL_HELP);
            }
         }
         case FALLEN -> {
            if (r.nextFloat() < 0.4F && this.mayCall(c, now)) {
               this.scream(c);
            }
         }
         case GETTING_UP, RECOVERING -> {
            float trauma = this.mood.trauma(c.getX(), c.getZ(), now);
            float p = r.nextFloat();
            if (p < 0.12F + trauma * 0.25F) {
               if (this.mayCall(c, now)) {
                  this.cough(c);
               }
            } else if (to == CitizenState.RECOVERING && p < 0.2F + trauma * 0.2F && this.mayCall(c, now)) {
               this.call(c, CityLifeCatalog.CALL_SURVIVOR);
            }
         }
         default -> {
         }
      }
   }

   // ---------------------------------------------------------------- tick

   public void tick(long now, Iterable<CitizenEntity> citizens) {
      if (now % 40 != 13) {
         return;
      }

      this.cleanTalks(now);
      RandomSource r = this.random();
      // running people are a sign for everyone around
      Long2ObjectOpenHashMap<int[]> panicked = new Long2ObjectOpenHashMap<>();
      for (CitizenEntity c : citizens) {
         CitizenState s = c.citizenState();
         if (s.panicked()) {
            panicked.computeIfAbsent(CityMood.key(CityMood.cell(c.getX()), CityMood.cell(c.getZ())), k -> new int[1])[0]++;
         } else if ((s == CitizenState.IDLE || s == CitizenState.RECOVERING || s == CitizenState.COVER) && r.nextInt(6) == 0) {
            // the aftermath: coughs in the dust, someone crying, survivors calling; hidden people still call for help
            float trauma = this.mood.trauma(c.getX(), c.getZ(), now);
            float alarm = this.mood.alarm(c.getX(), c.getZ(), now);
            float p = r.nextFloat();
            if (s == CitizenState.COVER && alarm > 0.3F) {
               if (p < 0.25F && this.mayCall(c, now)) {
                  this.call(c, CityLifeCatalog.CALL_HELP);
               }
            } else if (trauma > 0.3F && alarm < 0.3F) {
               if (p < trauma * 0.25F) {
                  if (this.mayCall(c, now)) {
                     this.cough(c);
                  }
               } else if (p < trauma * 0.33F && trauma > 0.45F) {
                  if (this.mayCall(c, now)) {
                     this.sob(c);
                  }
               } else if (p < trauma * 0.38F && trauma > 0.4F && this.mayCall(c, now)) {
                  this.call(c, CityLifeCatalog.CALL_SURVIVOR);
               }
            }
         }
      }

      for (Long2ObjectOpenHashMap.Entry<int[]> e : panicked.long2ObjectEntrySet()) {
         int n = e.getValue()[0];
         if (n >= 2) {
            long k = e.getLongKey();
            double x = ((int)(k >> 32) + 0.5) * CityMood.CELL;
            double z = ((int)k + 0.5) * CityMood.CELL;
            this.mood.hit(x, z, 8.0, Math.min(0.6F, 0.12F * n), Math.min(0.2F, 0.02F * n), now);
         }
      }

      if (now % 400 == 13) {
         this.mood.prune(now, 0.02F);
         this.callCells.long2ObjectEntrySet().removeIf(e -> {
            long last = Long.MIN_VALUE;
            for (long t : e.getValue()) {
               last = Math.max(last, t);
            }
            return now - last > 200L;
         });
         this.lastCall.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > CITIZEN_COOLDOWN);
         while (!this.recent.isEmpty() && now - this.recent.peekFirst()[3] > REPEAT_TICKS) {
            this.recent.removeFirst();
         }
      }
   }

   public void clear() {
      this.mood.clear();
      this.talks.clear();
      this.voiced.clear();
      this.recent.clear();
      this.callCells.clear();
      this.lastCall.clear();
   }

   /** /city life: the mood here and what the city said so far. */
   public String describe(double x, double z) {
      long now = this.now();
      return String.format(java.util.Locale.ROOT, "mood=%s alarm=%.2f trauma=%.2f cells=%d voicedTalks=%d silentTalks=%d active=%d lines=%d calls=%d screams=%d coughs=%d sobs=%d limited=%d",
         this.mood.mood(x, z, now), this.mood.alarm(x, z, now), this.mood.trauma(x, z, now), this.mood.size(), this.talksVoiced, this.talksSilent,
         this.voiced.size(), this.lines, this.calls, this.screams, this.coughs, this.sobs, this.limited);
   }

   /** Voiced chats, silent chats, lines, calls, screams, coughs, sobs, calls held back by the limits. */
   public int[] counters() {
      return new int[]{this.talksVoiced, this.talksSilent, this.lines, this.calls, this.screams, this.coughs, this.sobs, this.limited};
   }

   public CityMood.Mood moodAt(double x, double z) {
      return this.mood.mood(x, z, this.now());
   }

   public float alarmAt(double x, double z) {
      return this.mood.alarm(x, z, this.now());
   }

   public float traumaAt(double x, double z) {
      return this.mood.trauma(x, z, this.now());
   }

   public int activeTalks() {
      return this.voiced.size();
   }
}
