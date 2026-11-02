package dev.baranhan.viltrumitecore.hero.book;

import dev.baranhan.viltrumitecore.balance.HeroTier;
import java.util.List;

/**
 * How a body answers Heaven's Door (or any future book power): one immutable record per body and moment. Shared code
 * never names a hero: heroes return it from {@code HeroDefinition.bookProfile}, entities from {@link BookSubject}, and
 * {@link BookProfiles} classifies everything else.
 *
 * @param access          open, read-only or closed
 * @param bookClass       body class (instruction durations)
 * @param windowTicks     how long the book stays open
 * @param lines           how many instructions fit before it closes
 * @param maxInstruction  longest instruction in ticks (0 = the class default)
 * @param resistChance    chance [0..1] that a written line slides off
 * @param durationScale   multiplier of instruction durations
 * @param costScale       multiplier of the ink cost
 * @param partialReading  only the first pages are legible
 * @param reflectTicks    writing into a read-only book writes "cannot attack <owner>" into the reader for this long
 * @param sealTicks       opening the book seals the owner's {@code sealedActions} for this long (0 = none)
 * @param burnPerSecond   damage per second the reader takes while the book is open (burning pages)
 * @param sightImmune     sight-based openings (Air Sketch, Manuscript) never work
 * @param guardsFront     a raised guard facing the reader blocks the touch
 * @param notes           extra page lines (lang keys under {@code book.viltrumitecore.note.})
 */
public record BookProfile(BookAccess access, BookClass bookClass, int windowTicks, int lines, int maxInstruction, float resistChance,
   float durationScale, float costScale, boolean partialReading, int reflectTicks, int sealTicks, float burnPerSecond, boolean sightImmune,
   boolean guardsFront, List<String> notes) {
   public static final BookProfile ANIMAL = new BookProfile(BookAccess.OPEN, BookClass.ANIMAL, 200, 3, 0, 0.0F, 1.25F, 0.75F, false, 0, 0,
      0.0F, false, false, List.of());
   public static final BookProfile SIMPLE_MOB = new BookProfile(BookAccess.OPEN, BookClass.SIMPLE_MOB, 160, 3, 0, 0.0F, 1.0F, 1.0F, false, 0,
      0, 0.0F, false, false, List.of());
   public static final BookProfile SMART_MOB = new BookProfile(BookAccess.OPEN, BookClass.SMART_MOB, 130, 3, 0, 0.05F, 0.8F, 1.0F, false, 0,
      0, 0.0F, false, false, List.of());
   public static final BookProfile CITIZEN = new BookProfile(BookAccess.OPEN, BookClass.CITIZEN, 200, 3, 0, 0.0F, 1.0F, 0.75F, false, 0, 0,
      0.0F, false, false, List.of("citizen"));
   public static final BookProfile SUMMON = new BookProfile(BookAccess.OPEN, BookClass.SUMMON, 100, 2, 0, 0.1F, 0.8F, 1.0F, false, 0, 0,
      0.0F, false, false, List.of("summon"));
   public static final BookProfile BOSS = new BookProfile(BookAccess.OPEN, BookClass.BOSS, 40, 1, 80, 0.35F, 0.5F, 1.5F, true, 40, 0, 0.0F,
      false, false, List.of("boss"));
   /** A player without a hero, or a B / B+ hero. */
   public static final BookProfile PLAYER = new BookProfile(BookAccess.OPEN, BookClass.PLAYER, 50, 2, 100, 0.0F, 1.0F, 1.0F, false, 0, 0,
      0.0F, false, false, List.of());

   /** The default book of a hero of this tier (spec §5 table): the stronger, the shorter and harder. */
   public static BookProfile forTier(HeroTier tier) {
      return switch (tier) {
         case S_PLUS -> new BookProfile(BookAccess.READ_ONLY, BookClass.PLAYER, 20, 0, 0, 1.0F, 0.0F, 2.0F, true, 60, 0, 0.0F, false, false,
            List.of("tier_s_plus"));
         case S -> new BookProfile(BookAccess.OPEN, BookClass.PLAYER, 20, 1, 40, 0.5F, 0.5F, 2.0F, true, 0, 0, 0.0F, false, false,
            List.of("tier_s"));
         case A_PLUS -> new BookProfile(BookAccess.OPEN, BookClass.PLAYER, 30, 1, 60, 0.25F, 0.6F, 1.5F, false, 0, 0, 0.0F, false, false,
            List.of("tier_a_plus"));
         case A, A_ON_ULTIMATE -> new BookProfile(BookAccess.OPEN, BookClass.PLAYER, 40, 1, 80, 0.15F, 0.8F, 1.25F, false, 0, 0, 0.0F, false,
            false, List.of("tier_a"));
         default -> PLAYER;
      };
   }

   public boolean writable() {
      return this.access == BookAccess.OPEN && this.lines > 0;
   }

   public BookProfile withAccess(BookAccess access) {
      return new BookProfile(access, this.bookClass, this.windowTicks, this.lines, this.maxInstruction, this.resistChance, this.durationScale,
         this.costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, this.sightImmune, this.guardsFront, this.notes);
   }

   public BookProfile withWindow(int windowTicks) {
      return new BookProfile(this.access, this.bookClass, Math.max(1, windowTicks), this.lines, this.maxInstruction, this.resistChance,
         this.durationScale, this.costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, this.sightImmune,
         this.guardsFront, this.notes);
   }

   public BookProfile withScales(float durationScale, float costScale) {
      return new BookProfile(this.access, this.bookClass, this.windowTicks, this.lines, this.maxInstruction, this.resistChance, durationScale,
         costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, this.sightImmune, this.guardsFront, this.notes);
   }

   public BookProfile withResist(float resistChance) {
      return new BookProfile(this.access, this.bookClass, this.windowTicks, this.lines, this.maxInstruction, resistChance, this.durationScale,
         this.costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, this.sightImmune, this.guardsFront, this.notes);
   }

   public BookProfile withReaction(int reflectTicks, int sealTicks, float burnPerSecond) {
      return new BookProfile(this.access, this.bookClass, this.windowTicks, this.lines, this.maxInstruction, this.resistChance, this.durationScale,
         this.costScale, this.partialReading, reflectTicks, sealTicks, burnPerSecond, this.sightImmune, this.guardsFront, this.notes);
   }

   public BookProfile withSenses(boolean sightImmune, boolean guardsFront) {
      return new BookProfile(this.access, this.bookClass, this.windowTicks, this.lines, this.maxInstruction, this.resistChance, this.durationScale,
         this.costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, sightImmune, guardsFront, this.notes);
   }

   public BookProfile withClass(BookClass bookClass) {
      return new BookProfile(this.access, bookClass, this.windowTicks, this.lines, this.maxInstruction, this.resistChance, this.durationScale,
         this.costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, this.sightImmune, this.guardsFront, this.notes);
   }

   /** Adds page notes (lang keys {@code book.viltrumitecore.note.<key>}). */
   public BookProfile withNotes(String... keys) {
      java.util.List<String> all = new java.util.ArrayList<>(this.notes);
      all.addAll(java.util.Arrays.asList(keys));
      return new BookProfile(this.access, this.bookClass, this.windowTicks, this.lines, this.maxInstruction, this.resistChance, this.durationScale,
         this.costScale, this.partialReading, this.reflectTicks, this.sealTicks, this.burnPerSecond, this.sightImmune, this.guardsFront,
         List.copyOf(all));
   }
}
