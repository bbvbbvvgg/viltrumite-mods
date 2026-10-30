package dev.baranhan.viltrumitecore.hero.speech;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.HeroSession;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Server side of the cinematic hero subtitles: decides whether a line starts,
 * sends it to the speaker and the players around, and stops it when the
 * speaker dies, leaves, changes world or hero, turns spectator or the line's
 * {@link HeroLine#activeWhile} condition ends. Rendering is client-only
 * ({@code client.hero.speech.HeroSubtitles}). Gameplay is never touched.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroSpeech {
   /** Line id of operator previews with literal text. */
   public static final String LITERAL_LINE = "viltrumitecore:literal";
   private static final Map<UUID, Active> ACTIVE = new HashMap<>();
   private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
   private static final Map<UUID, Map<HeroLineTrigger, Integer>> ROTATION = new HashMap<>();
   private static int nextSerial = 1;

   private record Active(int serial, String lineId, HeroLine.Priority priority, WeakReference<ServerPlayer> entity, ResourceKey<Level> level,
      @Nullable UUID session, List<UUID> audience, long started, long until, @Nullable Predicate<ServerPlayer> activeWhile) {
   }

   private HeroSpeech() {
   }

   /** The speaker started this ability: play its line, if the speaker's hero has one. */
   public static boolean ability(ServerPlayer speaker, String abilityId) {
      return trigger(speaker, HeroLineTrigger.ability(abilityId));
   }

   /** Plays the next ready variant bound to the trigger in the speaker's current hero lines. */
   public static boolean trigger(ServerPlayer speaker, HeroLineTrigger trigger) {
      List<HeroLine> variants = HeroRegistry.get(speaker).lines().on(trigger);
      if (variants.isEmpty() || !canSpeak(speaker)) {
         return false;
      }

      long now = speaker.server.getTickCount();
      Map<String, Long> cooldowns = COOLDOWNS.getOrDefault(speaker.getUUID(), Map.of());
      Map<HeroLineTrigger, Integer> rotation = ROTATION.computeIfAbsent(speaker.getUUID(), id -> new HashMap<>());
      int index = HeroSpeechRules.nextVariant(rotation.getOrDefault(trigger, -1), variants.size(),
         i -> now >= cooldowns.getOrDefault(variants.get(i).id(), Long.MIN_VALUE));
      if (index < 0 || !say(speaker, variants.get(index))) {
         return false;
      }

      rotation.put(trigger, index);
      return true;
   }

   /** Plays a line in the speaker's own voice (current hero's name and portrait). */
   public static boolean say(ServerPlayer speaker, HeroLine line) {
      return play(speaker, line, voiceOf(speaker), false);
   }

   /**
    * Plays a line as {@code voice} (whose name and portrait the client shows).
    * {@code force} skips the cooldown and restarts a running copy (operator preview).
    */
   public static boolean play(ServerPlayer speaker, HeroLine line, HeroId voice, boolean force) {
      String speakerKey = line.speakerKey() != null ? line.speakerKey() : HeroSpeechRules.defaultSpeakerKey(voice.key());
      return start(speaker, line.id(), line.priority(), line.fragments(), false, speakerKey, voice, line.durationTicks(), line.cooldownTicks(),
         line.range(), line.activeWhile(), force);
   }

   /** Operator preview of arbitrary text in the speaker's voice; not a gameplay path. */
   public static boolean sayLiteral(ServerPlayer speaker, String text, HeroLine.Priority priority) {
      HeroId voice = voiceOf(speaker);
      String body = text.length() > HeroLineS2CPacket.MAX_TEXT ? text.substring(0, HeroLineS2CPacket.MAX_TEXT) : text;
      return start(speaker, LITERAL_LINE, priority, List.of(body), true, HeroSpeechRules.defaultSpeakerKey(voice.key()), voice, 0, 0,
         HeroLine.DEFAULT_RANGE, null, true);
   }

   /** Stops the speaker's line (only {@code lineId}, or any when null); e.g. a cancelled channel. */
   public static void interrupt(ServerPlayer speaker, @Nullable String lineId) {
      Active active = ACTIVE.get(speaker.getUUID());
      if (active != null && (lineId == null || active.lineId.equals(lineId))) {
         ACTIVE.remove(speaker.getUUID());
         sendStop(speaker.server, active);
      }
   }

   /** Whether the speaker is saying this line right now (server estimate). */
   public static boolean speaking(ServerPlayer speaker, String lineId) {
      Active active = ACTIVE.get(speaker.getUUID());
      return active != null && active.lineId.equals(lineId) && speaker.server.getTickCount() < active.until;
   }

   /** Finds a registered line of any hero, with the hero that owns it. */
   public static Optional<Owned> find(String lineId) {
      for (HeroId id : HeroId.values()) {
         Optional<HeroLine> line = HeroRegistry.get(id).lines().byId(lineId);
         if (line.isPresent() && HeroRegistry.get(id).id() == id) {
            return Optional.of(new Owned(id, line.get()));
         }
      }

      return Optional.empty();
   }

   /** Every registered line id (operator command suggestions). */
   public static List<String> allLineIds() {
      List<String> ids = new ArrayList<>();
      for (HeroId id : HeroId.values()) {
         if (HeroRegistry.get(id).id() == id) {
            HeroRegistry.get(id).lines().all().forEach(line -> ids.add(line.id()));
         }
      }

      return ids;
   }

   public record Owned(HeroId hero, HeroLine line) {
   }

   /** Playtest log only (SubtitleQaCommands): speaker -> line id, age and audience of every active line. */
   public static String qaDump(MinecraftServer server) {
      long now = server.getTickCount();
      StringBuilder out = new StringBuilder("[");
      ACTIVE.forEach((id, active) -> {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         out.append(player == null ? id.toString() : player.getGameProfile().getName()).append('=').append(active.lineId).append(" age=")
            .append(now - active.started).append(" left=").append(active.until - now).append(" audience=").append(active.audience.size()).append("; ");
      });
      return out.append(']').toString();
   }

   private static boolean start(ServerPlayer speaker, String lineId, HeroLine.Priority priority, List<String> fragments, boolean literal, String speakerKey,
      HeroId voice, int durationTicks, int cooldownTicks, double range, @Nullable Predicate<ServerPlayer> activeWhile, boolean force) {
      if (!canSpeak(speaker)) {
         return false;
      }

      UUID id = speaker.getUUID();
      long now = speaker.server.getTickCount();
      Active current = ACTIVE.get(id);
      if (current != null && now >= current.until) {
         ACTIVE.remove(id);
         current = null;
      } else if (current != null && !stillSpeaking(speaker, current)) {
         // The previous hero's (or a cancelled channel's) line has not been swept by the tick yet.
         ACTIVE.remove(id);
         sendStop(speaker.server, current);
         current = null;
      }

      Map<String, Long> cooldowns = COOLDOWNS.computeIfAbsent(id, key -> new HashMap<>());
      cooldowns.values().removeIf(until -> until <= now);
      HeroSpeechRules.Decision decision = HeroSpeechRules.decide(current == null ? null : current.lineId, current == null ? null : current.priority,
         current == null ? 0L : now - current.started, lineId, priority, now, cooldowns.getOrDefault(lineId, Long.MIN_VALUE), force);
      if (dev.baranhan.viltrumitecore.hero.speech.qa.SubtitleQaCommands.ENABLED) {
         com.mojang.logging.LogUtils.getLogger().info("SPEECHQA t={} {} {} -> {} (active {})", now, speaker.getGameProfile().getName(), lineId, decision,
            current == null ? "none" : current.lineId);
      }

      if (!decision.starts()) {
         return false;
      }

      if (current != null) {
         ACTIVE.remove(id);
         sendStop(speaker.server, current);
      }

      List<ServerPlayer> audience = audience(speaker, range);
      int serial = nextSerial++;
      if (nextSerial == Integer.MAX_VALUE) {
         nextSerial = 1;
      }

      HeroLineS2CPacket packet = HeroLineS2CPacket.play(serial, id, speaker.getId(), speaker.getGameProfile().getName(), voice.key(), lineId, speakerKey,
         fragments, literal, priority.ordinal(), durationTicks);
      List<UUID> listeners = new ArrayList<>(audience.size());
      for (ServerPlayer listener : audience) {
         CoreMessages.sendToPlayer(packet, listener);
         listeners.add(listener.getUUID());
      }

      long until = now + HeroSpeechRules.lifetimeTicks(durationTicks, fragmentChars(fragments, literal));
      ACTIVE.put(id, new Active(serial, lineId, priority, new WeakReference<>(speaker), speaker.level().dimension(), sessionOf(speaker), List.copyOf(listeners),
         now, until, activeWhile));
      cooldowns.put(lineId, until + cooldownTicks);
      return true;
   }

   /** Text length of each fragment as the server reads it (English on a dedicated server), -1 when the key is unknown. */
   private static int[] fragmentChars(List<String> fragments, boolean literal) {
      Language language = Language.getInstance();
      int[] chars = new int[fragments.size()];
      for (int i = 0; i < chars.length; i++) {
         String fragment = fragments.get(i);
         if (literal) {
            chars[i] = fragment.length();
         } else {
            String text = language.getOrDefault(fragment);
            chars[i] = text.equals(fragment) ? -1 : text.length();
         }
      }

      return chars;
   }

   private static boolean canSpeak(ServerPlayer speaker) {
      return isRealPlayer(speaker) && speaker.isAlive() && !speaker.isSpectator();
   }

   /**
    * A connected human: not a Forge fake player and not a Viltrumite squad NPC (a ServerPlayer
    * with a dummy connection that is listed in level.players(); lines sent to it would only pile up).
    */
   static boolean isRealPlayer(ServerPlayer player) {
      return !(player instanceof FakePlayer) && !(player instanceof dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer) && player.connection != null;
   }

   /** The speaker and every real player in the same level within range. */
   private static List<ServerPlayer> audience(ServerPlayer speaker, double range) {
      List<ServerPlayer> audience = new ArrayList<>();
      audience.add(speaker);
      double rangeSq = range * range;
      for (ServerPlayer other : speaker.serverLevel().players()) {
         if (other != speaker && isRealPlayer(other) && other.distanceToSqr(speaker) <= rangeSq) {
            audience.add(other);
         }
      }

      return audience;
   }

   /** The hero whose name and portrait a player speaks with: the current hero. */
   public static HeroId voiceOf(ServerPlayer player) {
      return player instanceof HeroPlayer hero ? hero.getHeroId() : HeroId.HUMAN;
   }

   @Nullable
   private static UUID sessionOf(ServerPlayer player) {
      HeroSession session = player instanceof HeroPlayer hero ? hero.getHeroSession() : null;
      return session == null ? null : session.sessionId();
   }

   private static boolean stillSpeaking(@Nullable ServerPlayer player, Active active) {
      return player != null && player == active.entity.get() && player.isAlive() && !player.isSpectator()
         && player.level().dimension().equals(active.level) && java.util.Objects.equals(sessionOf(player), active.session)
         && (active.activeWhile == null || active.activeWhile.test(player));
   }

   private static void sendStop(MinecraftServer server, Active active) {
      HeroLineS2CPacket stop = HeroLineS2CPacket.stop(active.serial);
      for (UUID listener : active.audience) {
         ServerPlayer player = server.getPlayerList().getPlayer(listener);
         if (player != null) {
            CoreMessages.sendToPlayer(stop, player);
         }
      }
   }

   /** Once per server tick, only while lines play: stop the lines whose speaker can no longer be speaking. */
   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
         return;
      }

      MinecraftServer server = event.getServer();
      long now = server.getTickCount();
      Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Active> entry = it.next();
         Active active = entry.getValue();
         if (now >= active.until) {
            it.remove();
         } else if (!stillSpeaking(server.getPlayerList().getPlayer(entry.getKey()), active)) {
            it.remove();
            sendStop(server, active);
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      UUID id = event.getEntity().getUUID();
      COOLDOWNS.remove(id);
      ROTATION.remove(id);
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      ACTIVE.clear();
      COOLDOWNS.clear();
      ROTATION.clear();
      nextSerial = 1;
   }
}
