package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerPlayer;

/**
 * DIO's spoken lines (cinematic subtitles). Text keys
 * {@code subtitle.viltrumitecore.dio.*}, speaker {@code subtitle.viltrumitecore.speaker.dio}.
 * Each line starts once when the server accepts the action; a cancelled cast interrupts it.
 */
public final class DioLines {
   public static final String RESUME_EVENT = "dio:time_resume";
   public static final String SUMMON_EVENT = "dio:summon";

   /** Time stop accepted: "ZA WARUDO! Toki yo tomare!" */
   public static final HeroLine TIME_STOP = HeroLine.builder(DioAbilities.TIME_STOP)
      .text("subtitle.viltrumitecore.dio.time_stop")
      .priority(HeroLine.Priority.ABILITY)
      .range(48.0)
      .build();
   /** Twenty ticks before the world moves again. */
   public static final HeroLine TIME_RESUME = HeroLine.builder(RESUME_EVENT)
      .text("subtitle.viltrumitecore.dio.time_resume")
      .priority(HeroLine.Priority.ABILITY)
      .range(48.0)
      .build();
   /**
    * The ultimate, one page per beat of the authoritative Road Roller timeline (DioRules):
    * the threat over the cast and the leap, "Road Roller!" when it materialises (ROLLER_APPEAR,
    * the "ROAD ROLLER DA!" sound), MUDA over the punches from the slam and WRYYY with its sound
    * (ROLLER_BARRAGE_END). Before: three auto-spread pages over 160 ticks, so "Road Roller!" was
    * gone before the roller appeared and MUDA came before the slam. A cancelled ult stops each page.
    */
   public static final HeroLine ROAD_ROLLER = HeroLine.builder(DioAbilities.ROAD_ROLLER)
      .text("subtitle.viltrumitecore.dio.road_roller.2")
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(DioRules.ROLLER_APPEAR)
      .range(64.0)
      .activeWhile(DioLines::inRoller)
      .build();
   public static final String ROLLER_APPEAR_EVENT = "dio:roller_appear";
   public static final String ROLLER_SLAM_EVENT = "dio:roller_slam";
   public static final String ROLLER_WRYYY_EVENT = "dio:roller_wryyy";
   public static final HeroLine ROLLER_APPEAR = rollerPage(ROLLER_APPEAR_EVENT, "subtitle.viltrumitecore.dio.road_roller.1",
      DioRules.ROLLER_SLAM - DioRules.ROLLER_APPEAR);
   public static final HeroLine ROLLER_SLAM = rollerPage(ROLLER_SLAM_EVENT, "subtitle.viltrumitecore.dio.road_roller.muda",
      DioRules.ROLLER_BARRAGE_END - DioRules.ROLLER_SLAM);
   public static final HeroLine ROLLER_WRYYY = rollerPage(ROLLER_WRYYY_EVENT, "subtitle.viltrumitecore.dio.road_roller.wryyy",
      DioRules.ROLLER_RESUME - DioRules.ROLLER_BARRAGE_END + 20);
   /** First summon of a session-like cooldown: "The World!" */
   public static final HeroLine SUMMON = HeroLine.builder(SUMMON_EVENT)
      .text("subtitle.viltrumitecore.dio.summon")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 30)
      .range(32.0)
      .build();

   public static final String BARRAGE_EVENT = "dio:muda_barrage";
   public static final String FINISHER_EVENT = "dio:barrage_finisher";
   public static final String KNIVES_EVENT = "dio:knives";
   public static final String DRAIN_EVENT = "dio:vampire_drain";
   /** The MUDA rush while it is held (stops when the finisher starts or the rush breaks). */
   public static final HeroLine BARRAGE = HeroLine.builder(BARRAGE_EVENT)
      .text("subtitle.viltrumitecore.dio.barrage")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 20)
      .range(32.0)
      .activeWhile(player -> {
         DioState state = DioState.of(player);
         return state != null && state.action == dev.baranhan.viltrumitecore.hero.HeroAction.MUDA_BARRAGE && !state.finisher;
      })
      .build();
   /** The last blow of the rush: "Useless!" */
   public static final HeroLine FINISHER = ability(FINISHER_EVENT, "subtitle.viltrumitecore.dio.barrage_finisher", 20 * 25);
   public static final HeroLine KNIVES = ability(KNIVES_EVENT, "subtitle.viltrumitecore.dio.knives", 20 * 30);
   public static final HeroLine DRAIN = ability(DRAIN_EVENT, "subtitle.viltrumitecore.dio.drain", 20 * 30);
   public static final HeroLine KILL_A = ambient("dio:kill_a", "subtitle.viltrumitecore.dio.kill_a", 20 * 60);
   public static final HeroLine KILL_B = ambient("dio:kill_b", "subtitle.viltrumitecore.dio.kill_b", 20 * 60);
   public static final HeroLine LOW_HEALTH = ambient("dio:low_health", "subtitle.viltrumitecore.dio.low_health", 20 * 90);
   public static final HeroLine ENTER = ability("dio:enter", "subtitle.viltrumitecore.dio.enter", 20 * 60);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(DioAbilities.TIME_STOP, TIME_STOP)
      .onAbility(DioAbilities.ROAD_ROLLER, ROAD_ROLLER)
      .onEvent(ROLLER_APPEAR_EVENT, ROLLER_APPEAR)
      .onEvent(ROLLER_SLAM_EVENT, ROLLER_SLAM)
      .onEvent(ROLLER_WRYYY_EVENT, ROLLER_WRYYY)
      .onEvent(RESUME_EVENT, TIME_RESUME)
      .onEvent(SUMMON_EVENT, SUMMON)
      .onEvent(BARRAGE_EVENT, BARRAGE)
      .onEvent(FINISHER_EVENT, FINISHER)
      .onEvent(KNIVES_EVENT, KNIVES)
      .onEvent(DRAIN_EVENT, DRAIN)
      .onEvent(HeroLineEvents.KILL, KILL_A)
      .onEvent(HeroLineEvents.KILL, KILL_B)
      .onEvent(HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   /** Speaks a DIO event line (no-op for any other hero). */
   public static void say(ServerPlayer player, String event) {
      HeroSpeech.trigger(player, HeroLineTrigger.event(event));
   }

   private static boolean inRoller(ServerPlayer player) {
      DioState state = DioState.of(player);
      return state != null && state.action == dev.baranhan.viltrumitecore.hero.HeroAction.ROAD_ROLLER;
   }

   private static HeroLine rollerPage(String id, String key, int ticks) {
      return HeroLine.builder(id).text(key).priority(HeroLine.Priority.ULTIMATE).duration(ticks).cooldown(0).range(64.0).activeWhile(DioLines::inRoller).build();
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(key).priority(HeroLine.Priority.ABILITY).cooldown(cooldown).range(32.0).build();
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(key).priority(HeroLine.Priority.AMBIENT).cooldown(cooldown).range(24.0).build();
   }

   private DioLines() {
   }
}
