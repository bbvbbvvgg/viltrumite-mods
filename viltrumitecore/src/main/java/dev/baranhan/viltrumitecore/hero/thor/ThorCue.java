package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundSource;

/**
 * R5 (spec §8): the Thor cue list. One cue is one moment of a move (windup, contact, tail) or a loop, made of
 * layers of {@link ThorSounds} events played together: thunder = crack + impact + rumble, a hammer blow on the
 * ground = metal + earth + bass. Pure data: no registry access, so tests and docs read it directly.
 *
 * <p>Ids are stable ({@code family.move[.moment]}) and are the reference for the attack table (R2a owns
 * {@link ThorStrike} and its timings; a strike's sound is {@link #windup}, {@link #hit}, {@link #ground} and
 * {@link #tail}). Every phase cue is started by a phase event of the move, never by a tick count: windup on
 * {@code onStart} / {@code CHAIN} (INTRO), contact on {@code ENTER_MAIN}, tail on {@code ENTER_FINISH}.
 * Loops ({@link #loop()}) are started and stopped by the client ({@code ThorSoundLoops}).
 *
 * <p>Layer volume above 1 only widens the hearing range (16 blocks per unit, loudness is capped at 1);
 * relative loudness lives in the files (tools/sfx/thor.py, M100 targets).
 */
public enum ThorCue {
   // --- melee: Mjölnir in the hand (HAMMER_COMBO, HAMMER_STRIKE) ---
   HAMMER_WINDUP("hammer.windup", L("hammer_swing", 0.9F, 0.9F)),
   // Audit v3: the windups are the swing whoosh only (the long creak of hammer_windup peaked after the new contact tick).
   HAMMER_WINDUP_HEAVY("hammer.windup_heavy", L("hammer_swing", 1.0F, 0.78F), L("hammer_swing", 0.45F, 0.62F)),
   HAMMER_HIT("hammer.hit", L("hammer_metal", 0.8F, 1.0F), L("hammer_body", 1.0F, 1.0F), L("hammer_bass", 0.5F, 1.1F)),
   HAMMER_HIT_HEAVY("hammer.hit_heavy", L("hammer_metal", 1.0F, 0.9F), L("hammer_body", 1.3F, 0.9F), L("hammer_bass", 1.0F, 1.0F)),
   HAMMER_SLAM("hammer.slam", L("hammer_metal", 1.0F, 0.9F), L("hammer_earth", 1.6F, 1.0F), L("hammer_bass", 1.6F, 1.0F)),
   HAMMER_TAIL("hammer.tail", L("hammer_tail", 0.6F, 1.0F)),
   // --- melee: bare fists (FIST_COMBO) ---
   FIST_WINDUP("fist.windup", L("fist_swing", 0.6F, 1.1F)),
   FIST_WINDUP_HEAVY("fist.windup_heavy", L("fist_swing", 0.8F, 0.9F)),
   FIST_HIT("fist.hit", L("fist_hit", 0.9F, 1.0F)),
   FIST_HIT_HEAVY("fist.hit_heavy", L("fist_hit", 1.1F, 0.9F), L("hammer_bass", 0.5F, 1.3F)),
   FIST_SLAM("fist.slam", L("fist_hit", 0.8F, 0.8F), L("hammer_earth", 1.3F, 1.1F), L("hammer_bass", 1.2F, 1.2F)),
   FIST_TAIL("fist.tail", L("hammer_tail", 0.3F, 1.3F)),
   // --- guard (audit v3, ThorGuard: F) ---
   GUARD_UP("guard.up", L("fist_windup", 0.5F, 1.25F)),
   GUARD_BLOCK("guard.block", L("hammer_metal", 1.0F, 1.15F), L("hammer_body", 0.6F, 1.2F)),
   GUARD_BLOCK_FIST("guard.block_fist", L("fist_hit", 0.8F, 1.25F)),
   PARRY("guard.parry", L("hammer_metal", 1.3F, 1.3F), L("thunder_crack", 0.9F, 1.4F), L("electric_zap", 1.0F, 1.0F)),
   PARRY_FIST("guard.parry_fist", L("fist_hit", 1.1F, 1.1F), L("hammer_bass", 0.6F, 1.4F)),
   // --- electric mode ---
   ELECTRIC_ON("electric.on", L("electric_on", 1.0F, 1.0F)),
   ELECTRIC_OFF("electric.off", L("electric_off", 0.8F, 1.0F)),
   DISCHARGE_WINDUP("electric.discharge.windup", L("electric_zap", 0.4F, 1.5F)),
   DISCHARGE("electric.discharge", J("thunder_crack", 0.9F, 1.4F, 0.1F), L("electric_zap", 0.9F, 1.0F)),
   ELECTRIC_TAIL("electric.tail", L("electric_tail", 0.5F, 1.0F)),
   WAVE_WINDUP("electric.wave.windup", L("electric_zap", 0.5F, 0.9F)),
   WAVE("electric.wave", L("electric_wave", 1.2F, 1.0F), L("thunder_impact", 0.6F, 1.3F)),
   STORM_START("electric.storm.start", L("thunder_crack", 0.7F, 1.3F)),
   /** The storm's riser: cut by the burst or a cancel ({@link ThorSounds#stop}). */
   STORM_CHARGE("electric.storm.charge", L("storm_charge", 1.5F, 1.0F)),
   STORM_STAGE("electric.storm.stage", L("thunder_crack", 1.0F, 1.2F), L("thunder_impact", 0.7F, 1.2F)),
   STORM_CRACKLE("electric.storm.crackle", J("electric_zap", 0.6F, 0.8F, 0.15F)),
   STORM_BURST("electric.storm.burst", L("thunder_crack", 2.0F, 1.0F), L("thunder_impact", 2.0F, 1.0F),
      L("thunder_rumble", 3.0F, 1.0F), L("electric_wave", 1.2F, 0.8F)),
   COMBO("electric.combo", L("thunder_crack", 1.2F, 1.1F), L("electric_zap", 1.0F, 0.9F)),
   COMBO_SLAM("electric.combo.slam", L("thunder_impact", 1.5F, 0.9F), L("hammer_earth", 1.3F, 1.0F), L("hammer_bass", 1.0F, 1.0F)),
   /** The server half of a drawn bolt; its crack ({@link #BOLT_CRACK}) rides the bolt packet so it lands with the flash. */
   BOLT("electric.bolt", SoundSource.WEATHER, J("thunder_impact", 1.5F, 1.0F, 0.08F)),
   /** The long roll after bolts, at most once per {@link #RUMBLE_GAP} ticks per level. */
   BOLT_RUMBLE("electric.bolt.rumble", SoundSource.WEATHER, J("thunder_rumble", 3.0F, 1.0F, 0.08F)),
   WRATH_WINDUP("wrath.windup", L("wrath_windup", 2.0F, 1.0F), L("electric_on", 0.8F, 0.7F)),
   WRATH_CONTACT("wrath.contact", L("thunder_crack", 4.0F, 0.9F), L("thunder_impact", 4.0F, 0.8F), L("thunder_rumble", 6.0F, 0.85F),
      L("hammer_bass", 3.0F, 0.8F), L("hammer_earth", 3.0F, 0.8F)),
   WRATH_TAIL("wrath.tail", L("electric_tail", 1.0F, 0.8F), L("hammer_tail", 0.8F, 0.8F)),
   // --- the world hammer ---
   THROW_WINDUP("hammer.throw.windup", L("hammer_windup", 0.6F, 1.1F)),
   THROW("hammer.throw", L("hammer_throw", 1.2F, 1.0F)),
   THROW_CHARGED("hammer.throw.charged", L("hammer_throw", 1.5F, 0.85F), L("thunder_crack", 0.6F, 1.5F)),
   RECALL("hammer.recall", L("hammer_recall", 1.2F, 1.0F)),
   CATCH("hammer.catch", L("hammer_catch", 1.6F, 1.0F), L("hammer_metal", 0.5F, 1.2F)),
   PICKUP("hammer.pickup", L("hammer_draw", 1.0F, 1.0F), L("hammer_earth", 0.5F, 1.3F)),
   PULL("hammer.pull", L("pull", 1.0F, 1.0F), L("electric_zap", 0.4F, 1.2F)),
   STICK("hammer.stick", L("hammer_metal", 1.4F, 0.9F), L("hammer_earth", 1.2F, 1.0F), L("hammer_bass", 0.8F, 1.0F)),
   RIP("hammer.rip", L("hammer_earth", 1.0F, 1.2F), L("hammer_swing", 0.7F, 1.2F)),
   LAND("hammer.land", SoundSource.BLOCKS, L("hammer_metal", 0.9F, 0.8F), L("hammer_earth", 1.2F, 0.9F), L("hammer_bass", 1.0F, 0.9F)),
   THROWN_HIT("hammer.thrown_hit", L("hammer_metal", 1.0F, 1.0F), L("hammer_body", 1.2F, 0.9F), L("electric_zap", 0.6F, 1.1F)),
   BLOCK_SMASH("hammer.block_smash", SoundSource.BLOCKS, J("hammer_earth", 0.9F, 1.1F, 0.1F), L("hammer_bass", 0.5F, 1.2F)),
   /** Crater / death impact of the world hammer (MjolnirImpact): played with the impact's range scale. */
   IMPACT("hammer.impact", SoundSource.BLOCKS, L("hammer_metal", 1.0F, 0.8F), L("hammer_earth", 1.0F, 0.8F), L("hammer_bass", 1.0F, 0.8F),
      L("thunder_impact", 1.0F, 0.8F), L("thunder_rumble", 1.0F, 0.85F)),
   /** First appearance: heard by the whole dimension (range scale 10000), seconds before the impact. */
   FIRST_FALL_THUNDER("hammer.first_fall.thunder", SoundSource.WEATHER, L("thunder_crack", 1.0F, 0.7F), L("thunder_rumble", 1.0F, 0.75F)),
   FIRST_FALL_TRAIL("hammer.first_fall.trail", SoundSource.WEATHER, L("thunder_crack", 8.0F, 0.8F), L("electric_zap", 2.0F, 0.8F)),
   LIFT_STRAIN("hammer.lift.strain", L("lift_strain", 0.9F, 1.0F)),
   LIFT_FAIL("hammer.lift.fail", L("refuse", 0.8F, 0.8F), L("hammer_metal", 0.3F, 1.4F)),
   DRAW("hammer.draw", L("hammer_draw", 0.8F, 1.0F)),
   HOLSTER("hammer.holster", L("hammer_holster", 0.7F, 1.0F)),
   /** Private answer to a refused press (only the pressing player hears it). */
   REFUSE("ui.refuse", L("refuse", 0.4F, 1.0F)),
   // --- scenes (session C) ---
   DASH("scene.dash", J("dash", 0.7F, 1.0F, 0.08F)),
   GRAB("scene.grab", L("grab", 1.0F, 1.0F)),
   GRAB_ACTION("scene.grab.action", L("fist_swing", 0.8F, 0.8F)),
   GRAB_BREAK("scene.grab.break", L("grab", 0.8F, 1.3F), L("fist_hit", 0.8F, 1.2F)),
   TELEPORT_CHARGE("scene.teleport.charge", L("storm_charge", 0.8F, 1.4F), L("electric_on", 0.5F, 1.4F)),
   TELEPORT_DEPART("scene.teleport.depart", L("electric_zap", 0.8F, 1.0F), L("thunder_crack", 0.5F, 1.4F)),
   TELEPORT_ARRIVE("scene.teleport.arrive", L("thunder_crack", 1.0F, 1.2F), L("thunder_impact", 1.0F, 1.1F)),
   SLAM("scene.slam", L("hammer_earth", 1.2F, 1.0F), L("hammer_bass", 1.0F, 1.0F), L("fist_hit", 0.9F, 0.8F)),
   BARRAGE_HIT("scene.barrage.hit", J("fist_hit", 0.9F, 1.0F, 0.04F)),
   BARRAGE_FINISH("scene.barrage.finish", L("fist_hit", 1.2F, 0.8F), L("hammer_bass", 0.7F, 1.2F)),
   BODY_IMPACT("scene.body.impact", L("hammer_earth", 1.0F, 1.2F), L("bone_crunch", 0.6F, 1.0F)),
   BODY_HIT("scene.body.hit", L("hammer_body", 0.9F, 1.0F)),
   HEAD_CRUSH("scene.fatality.head_crush", L("hammer_metal", 1.0F, 0.8F), L("bone_crunch", 1.2F, 0.9F), L("gore_squelch", 0.9F, 0.9F)),
   CHEST_PUNCH("scene.fatality.chest_punch", L("fist_hit", 1.2F, 0.8F), L("bone_crunch", 1.0F, 1.0F), L("gore_squelch", 0.8F, 1.0F)),
   HEART_RIP("scene.fatality.heart_rip", L("heartbeat", 1.2F, 1.0F), L("gore_squelch", 1.0F, 0.9F)),
   HEART_CRUSH("scene.fatality.heart_crush", L("gore_squelch", 1.2F, 0.7F)),
   UPPERCUT("scene.fatality.uppercut", L("fist_hit", 1.2F, 0.8F), L("bone_crunch", 0.8F, 1.1F)),
   AIR_BLOW("scene.fatality.air_blow", L("thunder_crack", 1.2F, 1.0F), L("thunder_impact", 1.0F, 0.9F), L("hammer_bass", 1.2F, 0.8F)),
   GROUND_SLAM("scene.fatality.ground_slam", L("hammer_earth", 1.5F, 0.9F), L("hammer_bass", 1.4F, 0.9F)),
   THOR_LAND("scene.fatality.thor_land", L("hammer_earth", 0.9F, 1.1F), L("hammer_bass", 0.6F, 1.1F)),
   // --- client loops (ThorSoundLoops): one layer, base volume / pitch ---
   LOOP_ELECTRIC("loop.electric", true, L("electric_loop", 0.35F, 1.0F)),
   LOOP_HAMMER_SPIN("loop.hammer_spin", true, L("hammer_spin_loop", 0.6F, 1.0F)),
   LOOP_FLIGHT("loop.flight", true, L("flight_loop", 0.5F, 1.0F)),
   LOOP_HAMMER_FLY("loop.hammer_fly", true, L("hammer_fly_loop", 1.0F, 1.0F));

   /** Client half of every drawn Thor bolt (HeroFx.electricBolt sound). */
   public static final String BOLT_CRACK = "thunder_crack";
   /** Ticks between two server bolt impacts in one level (a five-bolt Wrath is one impact, not five). */
   public static final int IMPACT_GAP = 2;
   /** Ticks between two bolt rumbles in one level (a storm is one long roll, not a pile-up). */
   public static final int RUMBLE_GAP = 30;

   /**
    * Every Thor sound event (sounds.json {@code thor.<name>}, files {@code viltrumitecore:thor/<name>[_n]}).
    * Registered in this order by {@link ThorSounds}; append only.
    */
   public static final List<String> EVENTS = List.of(
      "hammer_windup", "hammer_swing", "hammer_metal", "hammer_earth", "hammer_bass", "hammer_body", "hammer_tail",
      "fist_windup", "fist_swing", "fist_hit",
      "thunder_crack", "thunder_impact", "thunder_rumble",
      "electric_on", "electric_off", "electric_loop", "electric_zap", "electric_tail", "electric_wave", "storm_charge", "wrath_windup",
      "hammer_spin_loop", "flight_loop", "hammer_fly_loop",
      "hammer_throw", "hammer_catch", "hammer_recall", "hammer_draw", "hammer_holster", "lift_strain", "refuse",
      "dash", "pull", "grab", "bone_crunch", "gore_squelch", "heartbeat");

   /** One event of a cue: base volume and pitch, random pitch spread ± {@code jitter}. */
   public record Layer(String event, float volume, float pitch, float jitter) {
   }

   private final String id;
   private final SoundSource source;
   private final boolean loop;
   private final List<Layer> layers;

   ThorCue(String id, Layer... layers) {
      this(id, SoundSource.PLAYERS, false, layers);
   }

   ThorCue(String id, SoundSource source, Layer... layers) {
      this(id, source, false, layers);
   }

   ThorCue(String id, boolean loop, Layer... layers) {
      this(id, SoundSource.PLAYERS, loop, layers);
   }

   ThorCue(String id, SoundSource source, boolean loop, Layer... layers) {
      this.id = id;
      this.source = source;
      this.loop = loop;
      this.layers = List.of(layers);
   }

   private static Layer L(String event, float volume, float pitch) {
      return new Layer(event, volume, pitch, 0.05F);
   }

   private static Layer J(String event, float volume, float pitch, float jitter) {
      return new Layer(event, volume, pitch, jitter);
   }

   public String id() {
      return this.id;
   }

   public SoundSource source() {
      return this.source;
   }

   public boolean loop() {
      return this.loop;
   }

   public List<Layer> layers() {
      return this.layers;
   }

   /** Largest layer volume (the hearing range of the cue, 16 blocks per unit). */
   public float range() {
      float max = 1.0F;
      for (Layer layer : this.layers) {
         max = Math.max(max, layer.volume());
      }

      return 16.0F * max;
   }

   // --- the attack table: strike -> cues (pure) ---

   private static boolean fist(ThorStrike strike) {
      return strike.move() == ThorMove.FIST_COMBO;
   }

   /** INTRO (onStart / CHAIN): the swing leaves. */
   public static ThorCue windup(ThorStrike strike) {
      return fist(strike) ? strike.heavy() ? FIST_WINDUP_HEAVY : FIST_WINDUP : strike.heavy() ? HAMMER_WINDUP_HEAVY : HAMMER_WINDUP;
   }

   /** Seconds from the start of the swing sound (hammer_swing 0.17 s, fist_swing 0.10 s at pitch 1) to its whoosh peak. */
   public static float whooshPeakSeconds(ThorCue windup) {
      return switch (windup) {
         case HAMMER_WINDUP -> 0.17F / 0.9F;
         case HAMMER_WINDUP_HEAVY -> 0.17F / 0.78F;
         case FIST_WINDUP -> 0.10F / 1.1F;
         case FIST_WINDUP_HEAVY -> 0.10F / 0.9F;
         default -> 0.0F;
      };
   }

   /**
    * Pure (audit v3, sound to contact sync): ticks to hold the swing sound so its whoosh peaks one
    * tick before the contact tick {@code windup}; 0 = play at once.
    */
   public static int windupDelay(ThorCue windup, int windupTicks) {
      return Math.max(0, Math.round(windupTicks - whooshPeakSeconds(windup) * 20.0F - 1.0F));
   }

   /** ENTER_MAIN, at least one body struck. */
   public static ThorCue hit(ThorStrike strike) {
      return fist(strike) ? strike.heavy() ? FIST_HIT_HEAVY : FIST_HIT : strike.heavy() ? HAMMER_HIT_HEAVY : HAMMER_HIT;
   }

   /** ENTER_MAIN of a strike with a ground shock ({@code shockRadius > 0}), at the shock centre; null otherwise. */
   @Nullable
   public static ThorCue ground(ThorStrike strike) {
      return strike.shockRadius() <= 0 ? null : fist(strike) ? FIST_SLAM : HAMMER_SLAM;
   }

   /** ENTER_FINISH (recovery): the heavy strikes ring out; light ones end in their own swing. */
   @Nullable
   public static ThorCue tail(ThorStrike strike) {
      return !strike.heavy() ? null : fist(strike) ? FIST_TAIL : HAMMER_TAIL;
   }

   /** Pure rate gate: may a gated layer play at {@code now} after one at {@code last}? */
   public static boolean gate(long last, long now, int gap) {
      return now - last >= gap || now < last;
   }
}
