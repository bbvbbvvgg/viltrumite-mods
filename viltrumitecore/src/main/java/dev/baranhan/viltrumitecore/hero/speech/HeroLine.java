package dev.baranhan.viltrumitecore.hero.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * One spoken hero line shown as a cinematic subtitle (spec docs/plans/2026-10-28-hero-subtitles.md).
 * The text lives in lang files: every fragment is a translation key and is shown
 * as its own page (a long monologue is a list of fragments; the client also
 * splits a fragment that does not fit three lines). Immutable; build with
 * {@link #builder(String)} and register through a hero's {@link HeroLines}.
 */
public final class HeroLine {
   public static final int MAX_FRAGMENTS = 16;
   public static final int MAX_KEY_LENGTH = 128;
   /** Longest explicit duration of a whole line, ticks (two minutes). */
   public static final int MAX_DURATION_TICKS = 20 * 120;
   public static final int DEFAULT_COOLDOWN_TICKS = 40;
   public static final double DEFAULT_RANGE = 32.0;
   public static final double MAX_RANGE = 256.0;
   private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");

   /** Who wins when lines meet: a higher rank interrupts a lower one, equal ranks wait in turn. */
   public enum Priority {
      AMBIENT,
      ABILITY,
      ULTIMATE;

      public static Priority byRank(int rank) {
         Priority[] values = values();
         return values[Math.max(0, Math.min(values.length - 1, rank))];
      }
   }

   private final String id;
   private final List<String> fragments;
   @Nullable
   private final String speakerKey;
   private final Priority priority;
   private final int durationTicks;
   private final int cooldownTicks;
   private final double range;
   @Nullable
   private final Predicate<ServerPlayer> activeWhile;

   private HeroLine(Builder builder) {
      this.id = builder.id;
      this.fragments = List.copyOf(builder.fragments);
      this.speakerKey = builder.speakerKey;
      this.priority = builder.priority;
      this.durationTicks = builder.durationTicks;
      this.cooldownTicks = builder.cooldownTicks;
      this.range = builder.range;
      this.activeWhile = builder.activeWhile;
   }

   public static Builder builder(String id) {
      return new Builder(id);
   }

   /** Namespaced id, e.g. {@code reinhard:world_cut}. */
   public String id() {
      return this.id;
   }

   /** Translation keys, one page (or more, when long) each, in order. */
   public List<String> fragments() {
      return this.fragments;
   }

   /** Translation key of the shown name, or null for {@code subtitle.viltrumitecore.speaker.<hero>}. */
   @Nullable
   public String speakerKey() {
      return this.speakerKey;
   }

   public Priority priority() {
      return this.priority;
   }

   /** Whole-line duration in ticks, or 0 to derive it from the text length on the client. */
   public int durationTicks() {
      return this.durationTicks;
   }

   /** Quiet time after the line ends before it may start again. */
   public int cooldownTicks() {
      return this.cooldownTicks;
   }

   /** Blocks around the speaker within which other players see the line too. */
   public double range() {
      return this.range;
   }

   /** Server: the line stops early once this turns false (e.g. a cancelled channel), or null. */
   @Nullable
   public Predicate<ServerPlayer> activeWhile() {
      return this.activeWhile;
   }

   @Override
   public String toString() {
      return "HeroLine[" + this.id + ", " + this.fragments.size() + " fragment(s), " + this.priority + "]";
   }

   public static final class Builder {
      private final String id;
      private final List<String> fragments = new ArrayList<>();
      @Nullable
      private String speakerKey;
      private Priority priority = Priority.ABILITY;
      private int durationTicks;
      private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
      private double range = DEFAULT_RANGE;
      @Nullable
      private Predicate<ServerPlayer> activeWhile;

      private Builder(String id) {
         this.id = Objects.requireNonNull(id, "id");
      }

      /** Appends fragments (translation keys) in reading order. */
      public Builder text(String... keys) {
         for (String key : keys) {
            this.fragments.add(Objects.requireNonNull(key, "fragment key"));
         }

         return this;
      }

      /** Appends {@code prefix + 1 … prefix + count}: a monologue written as numbered lang keys. */
      public Builder textSeries(String prefix, int count) {
         for (int i = 1; i <= count; i++) {
            this.fragments.add(prefix + i);
         }

         return this;
      }

      public Builder speaker(String key) {
         this.speakerKey = Objects.requireNonNull(key, "speaker key");
         return this;
      }

      public Builder priority(Priority priority) {
         this.priority = Objects.requireNonNull(priority, "priority");
         return this;
      }

      /** Fixed whole-line duration; the client spreads it over the pages by text length. */
      public Builder duration(int ticks) {
         this.durationTicks = ticks;
         return this;
      }

      public Builder cooldown(int ticks) {
         this.cooldownTicks = ticks;
         return this;
      }

      public Builder range(double blocks) {
         this.range = blocks;
         return this;
      }

      public Builder activeWhile(Predicate<ServerPlayer> condition) {
         this.activeWhile = Objects.requireNonNull(condition, "condition");
         return this;
      }

      public HeroLine build() {
         if (!ID.matcher(this.id).matches()) {
            throw new IllegalArgumentException("Hero line id must be namespaced lower-case (hero:name): " + this.id);
         }

         if (this.fragments.isEmpty() || this.fragments.size() > MAX_FRAGMENTS) {
            throw new IllegalArgumentException(this.id + ": 1.." + MAX_FRAGMENTS + " fragments, got " + this.fragments.size());
         }

         for (String key : this.fragments) {
            checkKey(key);
         }

         if (this.speakerKey != null) {
            checkKey(this.speakerKey);
         }

         if (this.durationTicks < 0 || this.durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException(this.id + ": duration 0.." + MAX_DURATION_TICKS + " ticks");
         }

         if (this.cooldownTicks < 0) {
            throw new IllegalArgumentException(this.id + ": negative cooldown");
         }

         if (!(this.range >= 0.0 && this.range <= MAX_RANGE)) {
            throw new IllegalArgumentException(this.id + ": range 0.." + MAX_RANGE);
         }

         return new HeroLine(this);
      }

      private void checkKey(String key) {
         if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(this.id + ": bad translation key '" + key + "'");
         }
      }
   }
}
