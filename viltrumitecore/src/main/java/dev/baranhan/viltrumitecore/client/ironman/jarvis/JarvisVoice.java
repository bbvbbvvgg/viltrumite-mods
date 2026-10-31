package dev.baranhan.viltrumitecore.client.ironman.jarvis;

import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.client.ironman.HelmetAnim;
import dev.baranhan.viltrumitecore.client.ironman.HelmetAnim;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkState;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.client.ironman.scan.ScanCardRenderer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.IronManJarvisSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * JARVIS voice lines on the owner's client (spec §11.1), only with the helmet
 * closed. Triggers come from changes of the synced snapshot and the owner
 * sections. Recorded lines (Codex-Superheroes, CC0) play where a matching
 * recording exists; every other line is a subtitle + UI chime (no synthetic
 * voice). {@link VoiceGate} keeps lines from stacking.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class JarvisVoice {
   /** Trigger → recording (null = chime), subtitle key, line length and cooldown (ticks). */
   public enum Line {
      /** jarvis_mark85_preset.ogg ("preset loaded"), 7.0 s. */
      SUIT_UP(() -> IronManJarvisSounds.JARVIS_SUIT_PRESET.get(), "jarvis.viltrumitecore.suit_up", 141, 2400),
      /** jarvis_detect.ogg ("hero detected"), 10.4 s. */
      THREAT(() -> IronManJarvisSounds.JARVIS_DETECT.get(), "jarvis.viltrumitecore.threat", 209, 1200),
      /** jarvis_detect_excited.ogg ("critical threat"), 10.9 s. */
      THREAT_CRITICAL(() -> IronManJarvisSounds.JARVIS_DETECT_EXCITED.get(), "jarvis.viltrumitecore.threat_critical", 218, 1200),
      /** jarvis_diagnostic.ogg ("diagnostic"), 4.5 s. */
      SCAN_DONE(() -> IronManJarvisSounds.JARVIS_DIAGNOSTIC.get(), "jarvis.viltrumitecore.scan_done", 90, 200),
      LOW_ENERGY(null, "jarvis.viltrumitecore.low_energy", 40, 600),
      OVERHEAT(null, "jarvis.viltrumitecore.overheat", 40, 200),
      SECOND_OVERHEAT(null, "hud.viltrumitecore.ironman.overheat_warning", 60, 200),
      OVERDRAFT(null, "hud.viltrumitecore.ironman.overdraft", 60, 100),
      COUNTERMEASURES(null, "jarvis.viltrumitecore.countermeasures", 30, 300),
      /** Stage 4: the Veronica pod landed in front of the owner (helmet closed). */
      VERONICA_POD(null, "jarvis.viltrumitecore.veronica", 100, 900),
      MARK_READY(null, "jarvis.viltrumitecore.mark_ready", 100, 600),
      MARK_LOW(null, "jarvis.viltrumitecore.mark_low", 100, 1200),
      MARK_BROKEN(null, "jarvis.viltrumitecore.mark_broken", 120, 600),
      HULKBUSTER(null, "jarvis.viltrumitecore.hulkbuster", 100, 600),
      HULKBUSTER_BROKEN(null, "jarvis.viltrumitecore.hulkbuster_broken", 100, 600),
      /** Stage 6: the Iron Legion was called (server event, owner only). */
      LEGION_ON(null, "jarvis.viltrumitecore.legion_on", 50, 200),
      LEGION_THREE(null, "jarvis.viltrumitecore.legion_three", 60, 200),
      LEGION_THREAT(null, "jarvis.viltrumitecore.legion_threat", 50, 400),
      LEGION_CRITICAL(null, "jarvis.viltrumitecore.legion_critical", 70, 200),
      LEGION_RETURN(null, "jarvis.viltrumitecore.legion_return", 60, 100);

      @Nullable
      private final Supplier<SoundEvent> sound;
      private final String subtitle;
      private final int duration;
      private final int cooldown;

      Line(@Nullable Supplier<SoundEvent> sound, String subtitle, int duration, int cooldown) {
         this.sound = sound;
         this.subtitle = subtitle;
         this.duration = duration;
         this.cooldown = cooldown;
      }

      public String subtitle() {
         return this.subtitle;
      }

      public boolean recorded() {
         return this.sound != null;
      }
   }

   private static final VoiceGate GATE = new VoiceGate();
   private static final Set<Integer> KNOWN_THREATS = new HashSet<>();
   private static boolean seen;
   private static boolean wasReady;
   private static float lastEnergy;
   private static int lastOverheats;
   private static boolean lastOverdraft;
   private static int lastCounter;
   @Nullable
   private static Component subtitle;
   private static long subtitleUntil;
   private static long subtitleStart;
   private static final float MARK_LOW_FRACTION = 0.25F;
   @Nullable
   private static MarkId lastMark;
   private static boolean lastFull;
   @Nullable
   private static MarkId lowSaidFor;

   private JarvisVoice() {
   }

   @Nullable
   public static Component subtitle(long now) {
      return subtitle != null && now < subtitleUntil ? subtitle : null;
   }

   /** 0..1 alpha of the current subtitle (fade in / out). */
   public static float subtitleAlpha(long now, float partialTick) {
      if (subtitle(now) == null) {
         return 0.0F;
      }

      float in = Math.min(1.0F, (now - subtitleStart + partialTick) / 4.0F);
      float out = Math.min(1.0F, (subtitleUntil - now - partialTick) / 10.0F);
      return Math.max(0.0F, Math.min(in, out));
   }

   /** JARVIS is up: suit ready and the helmet closed. */
   public static boolean online(@Nullable HeroPublicSnapshot snapshot) {
      return snapshot != null && IronManView.suitReady(snapshot) && HelmetAnim.closedFlag(snapshot);
   }

   static boolean critical(Entity entity) {
      return entity instanceof WitherBoss || entity instanceof EnderDragon || entity instanceof Warden;
   }

   /** The Veronica pod landed in front of its owner (MarkVfx). */
   public static void onVeronicaLanded() {
      sayHelmet(Line.VERONICA_POD);
   }

   /** Scan card arrived (ScanCardRenderer). */
   public static void onScanCard() {
      say(Line.SCAN_DONE);
   }

   static void say(Line line) {
      say(line, online(IronManView.of(Minecraft.getInstance().player)));
   }

   /** Stage 4 lines need only the closed helmet: the suit may be deploying or absent. */
   static void sayHelmet(Line line) {
      HeroPublicSnapshot snapshot = IronManView.of(Minecraft.getInstance().player);
      say(line, snapshot != null && HelmetAnim.closedFlag(snapshot));
   }

   /**
    * Stage 6: a Legion line from a server event. The Legion answers with or
    * without the helmet (the suits report to Tony's earpiece); false when the
    * gate is busy so the caller may retry.
    */
   public static boolean legion(Line line) {
      return say(line, true);
   }

   private static boolean say(Line line, boolean allowed) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || !allowed) {
         return false;
      }

      long now = client.level.getGameTime();
      if (!GATE.tryPlay(line.name(), now, line.duration, line.cooldown)) {
         return false;
      }

      SoundEvent sound = line.sound == null ? IronManCombatSounds.JARVIS_WARNING.get() : line.sound.get();
      client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, line.sound == null ? 1.25F : 1.0F));
      subtitle = Component.translatable(line.subtitle);
      subtitleStart = now;
      subtitleUntil = now + Math.max(60, line.duration);
      return true;
   }

   /** Game time restarts per world: gate, subtitle and card must not carry over to the next one. */
   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      GATE.reset();
      KNOWN_THREATS.clear();
      seen = false;
      subtitle = null;
      subtitleUntil = 0L;
      ScanCardRenderer.clear();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      Minecraft client = Minecraft.getInstance();
      if (event.phase != TickEvent.Phase.END || client.level == null || client.isPaused()) {
         return;
      }

      LocalPlayer player = client.player;
      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null) {
         seen = false;
         KNOWN_THREATS.clear();
         return;
      }

      boolean ready = IronManView.suitReady(snapshot);
      float energy = IronManView.energy(snapshot);
      int overheats = IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.OVERHEAT_COUNT);
      boolean overdraft = IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.OVERDRAFT);
      int counter = snapshot.extraCooldown(1);
      if (seen) {
         if (ready && !wasReady) {
            say(Line.SUIT_UP);
         }

         if (energy < IronManRules.JARVIS_LOW_ENERGY && lastEnergy >= IronManRules.JARVIS_LOW_ENERGY) {
            say(Line.LOW_ENERGY);
         }

         if (overheats > lastOverheats) {
            say(overheats >= 2 ? Line.SECOND_OVERHEAT : Line.OVERHEAT);
         }

         if (overdraft && !lastOverdraft) {
            say(Line.OVERDRAFT);
         }

         if (counter > lastCounter + 20) {
            say(Line.COUNTERMEASURES);
         }

         threats(client);
      } else {
         // First look at this player: remember the current threats without announcing them.
         KNOWN_THREATS.clear();
         for (int id : ClientHeroData.section(OwnerSection.THREATS)) {
            KNOWN_THREATS.add(id);
         }
      }

      markLines(snapshot, seen);
      hulkLines(snapshot, seen);
      seen = true;
      wasReady = ready;
      lastEnergy = energy;
      lastOverheats = overheats;
      lastOverdraft = overdraft;
      lastCounter = counter;
   }

   private static int lastHulk;

   /** Hulkbuster lines (plan Task 8): online when the layer goes ACTIVE, lost when it breaks (not on a normal exit). */
   private static void hulkLines(HeroPublicSnapshot snapshot, boolean announce) {
      int phase = IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.HULKBUSTER_PHASE);
      int active = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ACTIVE.ordinal();
      int none = dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.NONE.ordinal();
      if (announce) {
         if (phase == active && lastHulk != active) {
            sayHelmet(Line.HULKBUSTER);
         }

         if (lastHulk == active && phase == none) {
            sayHelmet(Line.HULKBUSTER_BROKEN);
         }
      }

      lastHulk = phase;
   }

   /** Mark lines (spec §11.1): ready, low durability once per wear, mark broken into the nano. */
   private static void markLines(HeroPublicSnapshot snapshot, boolean announce) {
      MarkState mark = MarkState.of(snapshot);
      boolean full = mark.full();
      if (announce) {
         if (full && !lastFull) {
            lowSaidFor = null;
            sayHelmet(Line.MARK_READY);
         }

         if (mark.mark() == null) {
            lowSaidFor = null;
         } else if (mark.worn() && mark.durabilityFraction() < MARK_LOW_FRACTION && lowSaidFor != mark.mark()) {
            lowSaidFor = mark.mark();
            sayHelmet(Line.MARK_LOW);
         }

         if (lastMark != null && mark.mark() == null && mark.worn()) {
            sayHelmet(Line.MARK_BROKEN);
         }
      }

      lastMark = mark.mark();
      lastFull = full;
   }

   /** New player / boss threats get a line; mobs only get brackets (no spam). */
   private static void threats(Minecraft client) {
      int[] ids = ClientHeroData.section(OwnerSection.THREATS);
      Set<Integer> now = new HashSet<>();
      Line line = null;
      for (int id : ids) {
         now.add(id);
         if (!KNOWN_THREATS.contains(id)) {
            Entity entity = client.level.getEntity(id);
            if (entity != null && critical(entity)) {
               line = Line.THREAT_CRITICAL;
            } else if (entity instanceof Player && line == null) {
               line = Line.THREAT;
            }
         }
      }

      KNOWN_THREATS.clear();
      KNOWN_THREATS.addAll(now);
      if (line != null) {
         say(line);
      }
   }
}
