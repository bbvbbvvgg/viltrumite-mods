package dev.baranhan.viltrumitecore.client.ironman.legion.qa;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.ironman.legion.LegionScreen;
import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.legion.IronManLegion;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionOrder;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSquad;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.sig.SignatureTrace;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.LegionCallC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionOrderC2SPacket;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Opt-in scripted playtest of the Iron Legion: {@code -Dviltrumitecore.legion.playtest=true} only, inert
 * otherwise. It loads the save {@code saves/legion}, becomes Iron Man and drives the Legion through the real
 * paths (ability keys → menu screen → C2S packets → server), logs {@code LEGION_PT CHECK} lines and takes
 * screenshots into {@code run/screenshots/legion_*.png}. Server state is read on the integrated server thread.
 * <p>
 * The save is not in git: create a superflat world named {@code legion} with cheats on once. The script resets
 * the roster itself (hero change), so a run that was killed half-way does not leave a broken mark behind.
 * {@link #cmd} is queued one server task later: do not use it to clear mobs right before spawning a target.
 * <p>
 * Multiplayer pass: {@code -Dviltrumitecore.legion.playtest.mp=localhost:25565} joins a dedicated server
 * (started with {@code -Dviltrumitecore.subtitles.qa=true} for {@code /subtitleqa hero}, players op) and checks
 * the client side only (menu S2C, tracked suits, HUD status, fight, RETURN, cooldown). A second client with
 * {@code .watcher=true} and {@code --username Watcher} checks another player's Legion: tracked and rendered,
 * no HUD of its own, and with server {@code pvp=false} its hits do not lower the suit.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class LegionPlaytest {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.legion.playtest");
   private static final Logger LOG = LogUtils.getLogger();
   private static final String WORLD = "legion";
   /** {@code host:port}: join a dedicated server instead of the save (multiplayer pass, client-side checks only). */
   private static final String MP = System.getProperty("viltrumitecore.legion.playtest.mp", "");
   /** With {@link #MP}: a second player who only watches another player's Legion. */
   private static final boolean WATCHER = Boolean.getBoolean("viltrumitecore.legion.playtest.watcher");
   private static boolean connectRequested;
   private static double clientMaxStep;
   private static boolean clientWatch;
   private static boolean engagedSeen;
   private static int othersSeen;
   private static boolean ownHudSeen;
   private static int victimId = -1;
   private static float victimBefore;
   private static int hits;
   private static final List<Step> STEPS = new ArrayList<>();
   private static final List<Release> RELEASES = new ArrayList<>();
   private static final Map<Integer, Vec3> LAST = new HashMap<>();
   private static final Map<String, Integer> SOUNDS = new HashMap<>();
   private static int stepIndex;
   private static int wait;
   private static int ticks;
   private static boolean worldRequested;
   private static boolean scripted;
   private static String pendingShot;
   private static int shotFrames;
   private static int pass;
   private static int fail;
   /** Largest single-tick move of any suit while {@link #watch} is on (teleport detector). */
   private static double maxStep;
   private static boolean watch;
   // values carried between steps (server thread)
   private static final Map<MarkId, Float> KEPT = new HashMap<>();
   private static int zombieId = -1;
   private static Vec3 holdAt;

   private record Step(int delay, String label, Runnable action, @Nullable BooleanSupplier ready) {
   }

   private record Release(KeyMapping key, int at) {
   }

   private LegionPlaytest() {
   }

   // ------------------------------------------------------------ helpers

   private static void at(int delay, String label, Runnable action) {
      STEPS.add(new Step(delay, label, action, null));
   }

   private static void until(String label, BooleanSupplier ready, Runnable action) {
      STEPS.add(new Step(1, label, action, ready));
   }

   private static void shot(int delay, String name) {
      at(delay, "shot " + name, () -> {
         pendingShot = "legion_" + name;
         shotFrames = 3;
      });
   }

   private static void check(String name, boolean ok, Object detail) {
      if (ok) {
         pass++;
      } else {
         fail++;
      }

      LOG.info("LEGION_PT CHECK {} {} :: {}", ok ? "PASS" : "FAIL", name, detail);
   }

   private static void server(Consumer<ServerPlayer> action) {
      Minecraft mc = Minecraft.getInstance();
      MinecraftServer server = mc.getSingleplayerServer();
      if (server == null || mc.player == null) {
         return;
      }

      java.util.UUID id = mc.player.getUUID();
      server.execute(() -> {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player != null) {
            try {
               action.accept(player);
            } catch (RuntimeException e) {
               LOG.error("LEGION_PT server action failed", e);
               fail++;
            }
         }
      });
   }

   private static void cmd(String command) {
      server(p -> p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command));
   }

   private static void press(KeyMapping key) {
      KeyMapping.set(key.getKey(), true);
      KeyMapping.click(key.getKey());
      RELEASES.add(new Release(key, ticks + 3));
   }

   private static void menuKey(int glfw) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof LegionScreen screen) {
         screen.keyPressed(glfw, 0, 0);
      } else {
         LOG.warn("LEGION_PT menu key {} without the Legion screen ({})", glfw, mc.screen);
      }
   }

   private static void order(LegionOrder order) {
      CoreMessages.sendToServer(new LegionOrderC2SPacket(order.ordinal(), -1));
   }

   @Nullable
   private static LegionSquad squad(ServerPlayer p) {
      return IronManLegion.squad(p);
   }

   private static List<LegionSuitEntity> suits(ServerPlayer p) {
      LegionSquad squad = squad(p);
      return squad == null ? List.of() : new ArrayList<>(squad.suits());
   }

   private static String describe(ServerPlayer p) {
      StringBuilder out = new StringBuilder();
      IronManState state = IronManState.of(p);
      for (LegionSuitEntity s : suits(p)) {
         out.append(String.format("[%s %s frac=%.2f pos=%.1f,%.1f,%.1f d=%.1f tgt=%s] ", s.mark(), s.phase(), s.fraction(), s.getX(), s.getY(), s.getZ(),
            s.distanceTo(p), s.brain == null || s.brain.target() == null ? "-" : s.brain.target().getType().toShortString()));
      }

      if (state != null) {
         for (MarkId id : MarkId.values()) {
            out.append(String.format("%s:%s/%.0f/cd%d ", id, state.roster.location(id), state.roster.durability(id), state.roster.cooldown(id)));
         }
      }

      LegionSquad squad = squad(p);
      out.append(squad == null ? "squad=none" : "order=" + squad.order() + " cooldown=" + squad.cooldown());
      return out.toString();
   }

   private static void log(String label) {
      server(p -> LOG.info("LEGION_PT STATE {} :: {}", label, describe(p)));
   }

   private static Zombie zombie(ServerPlayer p, Vec3 at, boolean ai) {
      ServerLevel level = p.serverLevel();
      Zombie z = EntityType.ZOMBIE.create(level);
      z.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
      z.setNoAi(!ai);
      z.setPersistenceRequired();
      level.addFreshEntity(z);
      return z;
   }

   private static void clearZombies(ServerPlayer p) {
      for (Zombie z : p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(128.0))) {
         z.discard();
      }
   }

   private static boolean leaving(ServerPlayer p) {
      return suits(p).stream().allMatch(s -> s.phase() == LegionPhase.LEAVE || s.phase() == LegionPhase.CRASH || !s.isAlive());
   }

   @Nullable
   private static Object reflect(Class<?> type, @Nullable Object self, String name, Object arg) {
      try {
         for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
               m.setAccessible(true);
               return m.invoke(self, arg);
            }
         }
      } catch (ReflectiveOperationException e) {
         return e;
      }
      return null;
   }

   @Nullable
   private static Object field(@Nullable Object self, String name) {
      if (self == null) {
         return null;
      }
      try {
         java.lang.reflect.Field f = self.getClass().getDeclaredField(name);
         f.setAccessible(true);
         return f.get(self);
      } catch (ReflectiveOperationException e) {
         return e;
      }
   }

   private static void face(ServerPlayer p, Vec3 target) {
      Vec3 d = target.subtract(p.getEyePosition());
      float yaw = (float)(Math.toDegrees(Math.atan2(-d.x, d.z)));
      float pitch = (float)(-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
      p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
   }

   private static boolean squadActive() {
      final boolean[] out = {false};
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server == null || Minecraft.getInstance().player == null) {
         return false;
      }

      ServerPlayer p = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
      out[0] = p != null && IronManLegion.active(p);
      return out[0];
   }

   // ------------------------------------------------------- multiplayer

   private static void mcmd(String command) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.connection.sendCommand(command);
      }
   }

   private static List<LegionSuitEntity> clientSuits(boolean own) {
      List<LegionSuitEntity> out = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LegionSuitEntity suit && dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.isLocalOwner(suit.ownerId()) == own) {
               out.add(suit);
            }
         }
      }
      return out;
   }

   private static String clientDescribe() {
      StringBuilder b = new StringBuilder();
      for (LegionSuitEntity s : clientSuits(true)) {
         b.append(String.format("[%s %s frac=%.2f d=%.1f] ", s.mark(), s.phase(), s.fraction(), Minecraft.getInstance().player.distanceTo(s)));
      }
      dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus st = dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.status();
      return b + "status active=" + st.active() + " order=" + st.order() + " suits=" + st.suits().size() + " cooldown=" + st.cooldown()
         + " others=" + clientSuits(false).size();
   }

   /** Client-side view: interpolated steps of my suits, whether any engaged, how many foreign suits I could see. */
   private static void trackClient(Minecraft mc) {
      for (LegionSuitEntity s : clientSuits(true)) {
         Vec3 last = LAST.put(s.getId(), s.position());
         if (clientWatch && last != null && s.phase() != LegionPhase.APPROACH && s.phase() != LegionPhase.LEAVE && s.phase() != LegionPhase.CRASH) {
            clientMaxStep = Math.max(clientMaxStep, last.distanceTo(s.position()));
         }
      }

      dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus st = dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.status();
      engagedSeen |= st.suits().stream().anyMatch(dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus.Suit::engaged);
      ownHudSeen |= st.active();
      othersSeen = Math.max(othersSeen, clientSuits(false).size());
      if (mc.screen instanceof dev.baranhan.viltrumitecore.client.gui.RaceSelectionScreen) {
         mc.setScreen(null);
      }
   }

   private static long clientMobs() {
      Minecraft mc = Minecraft.getInstance();
      long n = 0;
      for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
         if (e instanceof net.minecraft.world.entity.monster.Monster) {
            n++;
         }
      }
      return n;
   }

   /** Owner on a dedicated server: the real network path for menu, call, status, tracking, orders and cooldown. */
   private static void mpScript() {
      at(1, "mp setup", () -> {
         mcmd("gamerule doMobSpawning false");
         mcmd("gamerule doDaylightCycle false");
         mcmd("time set 6000");
         mcmd("weather clear");
         mcmd("kill @e[type=!player]");
         mcmd("gamemode survival");
         mcmd("difficulty normal");
         mcmd("tp @s 0 -60 0 0 0");
         mcmd("effect give @s resistance 99999 4 true");
         mcmd("effect give @s saturation 99999 1 true");
         mcmd("subtitleqa hero @s human");
      });
      at(20, "mp iron man", () -> mcmd("subtitleqa hero @s ironman"));
      at(40, "camera", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
      shot(5, "mp_00_idle");
      at(5, "page 2", () -> press(AbilityInputManager.abilitySwapKey));
      at(20, "legion key", () -> press(AbilityInputManager.abilityKey6));
      until("mp menu open", () -> Minecraft.getInstance().screen instanceof LegionScreen, () -> check("MP: the ability opens the call menu (S2C)", Minecraft.getInstance().screen instanceof LegionScreen, Minecraft.getInstance().screen));
      shot(10, "mp_01_menu");
      at(2, "mp enter", () -> menuKey(GLFW.GLFW_KEY_ENTER));
      at(3, "mp called", () -> {
         Minecraft.getInstance().setScreen(null);
         Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
      });
      shot(20, "mp_02_arrive");
      at(1, "mp arrival watch", () -> clientWatch = true);
      shot(30, "mp_03_brake");
      until("mp suits active", () -> clientSuits(true).size() == 3 && clientSuits(true).stream().allMatch(s -> s.phase() == LegionPhase.ACTIVE), () -> {});
      at(1, "mp arrived", () -> {
         check("MP: three suits tracked and ACTIVE on the client", clientSuits(true).size() == 3, clientDescribe());
         check("MP: the HUD status lists three suits", dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.status().suits().size() == 3, clientDescribe());
      });
      shot(5, "mp_04_ready");
      at(2, "mp mobs", () -> {
         mcmd("summon zombie 10 -60 6");
         mcmd("summon zombie 12 -60 2");
         mcmd("summon zombie 14 -60 -2");
         mcmd("summon skeleton 16 -60 0");
      });
      shot(40, "mp_05_fight_a");
      shot(60, "mp_06_fight_b");
      at(140, "mp fight checked", () -> {
         check("MP: the HUD showed an engaged suit", engagedSeen, clientDescribe());
         check("MP: the Legion fought the mobs (" + clientMobs() + " of 4 left)", clientMobs() < 4, clientDescribe());
         check("MP: no teleport-like step on the client (max " + String.format("%.2f", clientMaxStep) + ")", clientMaxStep < 6.0, clientMaxStep);
         clientWatch = false;
      });
      at(2, "mp order menu", () -> press(AbilityInputManager.abilityKey6));
      until("mp order menu open", () -> Minecraft.getInstance().screen instanceof LegionScreen, () -> {});
      shot(5, "mp_07_orders");
      at(2, "mp RETURN", () -> menuKey(GLFW.GLFW_KEY_5));
      at(2, "mp close", () -> Minecraft.getInstance().setScreen(null));
      until("mp returned", () -> clientSuits(true).isEmpty(), () -> check("MP: RETURN removes the suits on the client", clientSuits(true).isEmpty() && ownHudSeen, clientDescribe()));
      at(10, "mp cooldown", () -> check("MP: the HUD gets the call cooldown", dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.status().cooldown() > 0, clientDescribe()));
      shot(5, "mp_08_cooldown");
      // Stay a while so a watcher can still see a second call.
      // After the short return cooldown a second call, so a watcher sees a Legion for longer.
      at(220, "mp second key", () -> press(AbilityInputManager.abilityKey6));
      until("mp second menu", () -> Minecraft.getInstance().screen instanceof LegionScreen, () -> {});
      at(2, "mp second enter", () -> menuKey(GLFW.GLFW_KEY_ENTER));
      at(2, "mp second close", () -> Minecraft.getInstance().setScreen(null));
      at(600, "mp done", () -> LOG.info("LEGION_PT DONE pass={} fail={} sounds={}", pass, fail, SOUNDS));
   }

   /**
    * A second player: sees the other Legion through entity tracking, gets no HUD of his own, and (server
    * {@code pvp=false}) cannot wear another player's suit down (Codex review #4).
    */
   private static void watcherScript() {
      at(1, "watch setup", () -> {
         mcmd("gamemode creative");
         mcmd("tp @s 0 -55 -14 0 20");
         Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
      });
      until("watch sees an ACTIVE foreign suit", () -> clientSuits(false).stream().anyMatch(s -> s.phase() == LegionPhase.ACTIVE), () -> {
         check("MP watcher: another player's suits are tracked and rendered", !clientSuits(false).isEmpty(), clientSuits(false).size());
         check("MP watcher: no Legion HUD for a non-owner", !ownHudSeen, clientDescribe());
      });
      shot(5, "mpw_0_sees");
      at(1, "watch pick", () -> clientSuits(false).stream().filter(s -> s.phase() == LegionPhase.ACTIVE).findFirst().ifPresent(s -> {
         victimId = s.getId();
         victimBefore = s.fraction();
      }));
      for (int i = 0; i < 6; i++) {
         at(2, "watch close in " + i, () -> {
            net.minecraft.world.entity.Entity v = Minecraft.getInstance().level.getEntity(victimId);
            if (v != null) {
               mcmd(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", v.getX(), v.getY() - 0.5, v.getZ() + 1.6));
            }
         });
         at(4, "watch hit " + i, () -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.world.entity.Entity v = mc.level.getEntity(victimId);
            if (v != null && mc.player.distanceTo(v) < 4.5) {
               mc.gameMode.attack(mc.player, v);
               mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
               hits++;
            }
         });
      }
      shot(2, "mpw_1_hits");
      at(20, "watch hit checked", () -> {
         net.minecraft.world.entity.Entity v = Minecraft.getInstance().level.getEntity(victimId);
         float now = v instanceof LegionSuitEntity suit ? suit.fraction() : -1.0F;
         check("MP watcher: pvp=false, " + hits + " hits on another player's suit leave it at " + now + " (was " + victimBefore + ")",
            hits > 0 && v != null && Math.abs(now - victimBefore) < 0.001F, clientDescribe());
         mcmd("tp @s 0 -55 -14 0 20");
      });
      for (int i = 2; i < 7; i++) {
         shot(200, "mpw_" + i);
         final int n = i;
         at(1, "watch " + i, () -> LOG.info("LEGION_PT watch {} others={} max={} {}", n, clientSuits(false).size(), othersSeen, clientDescribe()));
      }
      at(1, "watch checked", () -> check("MP watcher: still no own HUD at the end (max foreign suits seen " + othersSeen + ")", !ownHudSeen && othersSeen > 0, clientDescribe()));
      at(20, "watch done", () -> LOG.info("LEGION_PT DONE pass={} fail={} sounds={}", pass, fail, SOUNDS));
   }

   // ------------------------------------------------------------- script

   private static void script() {
      at(1, "setup", () -> {
         cmd("gamerule doMobSpawning false");
         cmd("gamerule doDaylightCycle false");
         cmd("gamerule doWeatherCycle false");
         cmd("time set 6000");
         cmd("weather clear");
         cmd("kill @e[type=!player]");
         cmd("gamemode survival");
         cmd("difficulty normal");
         cmd("tp @s 0 -60 0 0 0");
         cmd("effect give @s resistance 99999 4 true");
         cmd("effect give @s saturation 99999 1 true");
         // A clean roster every run: an earlier run may have saved a broken mark (HERO_CHANGE resets the roster).
         server(p -> {
            HeroRegistry.changeHero(p, HeroId.HUMAN);
            HeroRegistry.changeHero(p, HeroId.IRON_MAN);
            check("hero is Iron Man with a clean roster", IronManState.of(p) != null && IronManState.of(p).roster.legion().isEmpty(), describe(p));
         });
      });
      at(40, "camera", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
      at(1, "mspt idle", () -> server(p -> LOG.info("LEGION_PT mspt idle {}", p.server.getAverageTickTime())));
      shot(5, "00_hud_idle");
      // Ability page 2, slot 6 (the real key path).
      at(5, "page 2", () -> press(AbilityInputManager.abilitySwapKey));
      at(20, "legion key", () -> press(AbilityInputManager.abilityKey6));
      until("menu open", () -> Minecraft.getInstance().screen instanceof LegionScreen, () -> check("ability opens the Legion menu", Minecraft.getInstance().screen instanceof LegionScreen, Minecraft.getInstance().screen));
      shot(10, "01_menu_select");
      at(2, "close menu", () -> Minecraft.getInstance().setScreen(null));
      // Packet spam: duplicates, unknown ids, three packets at once → one Mark 7.
      at(5, "spam call", () -> {
         for (int i = 0; i < 3; i++) {
            CoreMessages.sendToServer(new LegionCallC2SPacket(new int[] {0, 0, 0, 99, -4}));
         }
      });
      at(4, "spam checked", () -> server(p -> {
         List<LegionSuitEntity> s = suits(p);
         check("spam/duplicate call gives one suit", s.size() == 1 && s.get(0).mark() == MarkId.MARK_7, describe(p));
         watch = true;
         maxStep = 0.0;
      }));
      at(1, "third person", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
      shot(4, "02_arrive_a");
      shot(6, "03_arrive_b");
      shot(8, "04_arrive_c");
      shot(10, "05_arrive_brake");
      shot(12, "06_ready");
      at(60, "arrived", () -> server(p -> {
         List<LegionSuitEntity> s = suits(p);
         check("single suit reaches ACTIVE", s.size() == 1 && s.get(0).phase() == LegionPhase.ACTIVE, describe(p));
         check("no teleport-like step during arrival (max step " + String.format("%.2f", maxStep) + ")", maxStep < 6.0, maxStep);
      }));
      // Orders through the command menu.
      at(2, "order menu", () -> press(AbilityInputManager.abilityKey6));
      until("order menu open", () -> Minecraft.getInstance().screen instanceof LegionScreen, () -> {});
      shot(6, "07_menu_orders");
      at(2, "FOLLOW", () -> menuKey(GLFW.GLFW_KEY_1));
      at(10, "FOLLOW checked", () -> server(p -> check("menu key 1 = FOLLOW", squad(p) != null && squad(p).order() == LegionOrder.FOLLOW, describe(p))));
      at(2, "walk", () -> {
         Minecraft.getInstance().setScreen(null);
         cmd("tp @s 12 -60 0 -90 0");
      });
      shot(40, "08_follow");
      at(1, "GUARD", () -> order(LegionOrder.GUARD));
      at(30, "GUARD checked", () -> server(p -> check("GUARD order", squad(p).order() == LegionOrder.GUARD, describe(p))));
      shot(1, "09_guard");
      // Return a single suit: roster, cooldown, HUD countdown.
      at(2, "RETURN 1", () -> {
         server(p -> KEPT.put(MarkId.MARK_7, IronManState.of(p).roster.durability(MarkId.MARK_7)));
         order(LegionOrder.RETURN);
      });
      shot(10, "10_return_leave");
      until("returned", () -> !squadActive(), () -> server(p -> {
         IronManState st = IronManState.of(p);
         check("returned suit back STORED", st.roster.location(MarkId.MARK_7) == MarkLocation.STORED, describe(p));
         check("return keeps durability", Math.abs(st.roster.durability(MarkId.MARK_7) - KEPT.get(MarkId.MARK_7)) < 0.01F, describe(p));
         int cd = IronManLegion.cooldown(p.getUUID());
         check("short cooldown after return (~200 t)", cd > 120 && cd <= 200, cd);
      }));
      at(1, "first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
      shot(5, "11_hud_cooldown_early");
      shot(80, "12_hud_cooldown_late");
      at(1, "recall refused", () -> CoreMessages.sendToServer(new LegionCallC2SPacket(new int[] {1})));
      at(5, "recall refused checked", () -> server(p -> check("call refused during cooldown", !IronManLegion.active(p), describe(p))));
      until("cooldown over", () -> {
         MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
         return s != null && IronManLegion.cooldown(Minecraft.getInstance().player.getUUID()) <= 0;
      }, () -> log("cooldown over"));
      shot(10, "13_hud_cooldown_done");
      // Three suits from the menu (recommended picks, Enter) and a fight.
      at(2, "mobs", () -> server(p -> {
         for (int i = 0; i < 4; i++) {
            zombie(p, p.position().add(10 + i * 2, 0, 6 - i * 4), true);
         }
         EntityType.SKELETON.spawn(p.serverLevel(), net.minecraft.core.BlockPos.containing(p.position().add(16, 0, 0)), net.minecraft.world.entity.MobSpawnType.COMMAND);
         EntityType.PHANTOM.spawn(p.serverLevel(), net.minecraft.core.BlockPos.containing(p.position().add(8, 10, 0)), net.minecraft.world.entity.MobSpawnType.COMMAND);
      }));
      at(2, "menu 3", () -> press(AbilityInputManager.abilityKey6));
      until("menu 3 open", () -> Minecraft.getInstance().screen instanceof LegionScreen, () -> {});
      shot(4, "14_menu_three");
      at(2, "enter", () -> menuKey(GLFW.GLFW_KEY_ENTER));
      at(5, "three called", () -> {
         Minecraft.getInstance().setScreen(null);
         Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
         server(p -> {
            check("menu Enter calls three suits", suits(p).size() == 3, describe(p));
            maxStep = 0.0;
         });
      });
      shot(10, "15_three_arrive");
      shot(20, "16_three_brake");
      shot(30, "17_three_ready");
      shot(30, "18_fight_a");
      at(1, "fight mid", () -> server(p -> LOG.info("LEGION_PT fight mid {}", describe(p))));
      shot(29, "19_fight_b");
      shot(30, "20_fight_c");
      at(60, "fight checked", () -> server(p -> {
         long left = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, p.getBoundingBox().inflate(40)).size()
            + p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Phantom.class, p.getBoundingBox().inflate(40)).size();
         check("Legion fought the mobs (" + left + " of 6 left)", left < 6, describe(p));
         check("no teleport-like step during three-suit arrival/fight", maxStep < 6.0, maxStep);
         LOG.info("LEGION_PT mspt fight {}", p.server.getAverageTickTime());
      }));
      at(1, "log sounds", () -> LOG.info("LEGION_PT SOUNDS {}", SOUNDS));
      // Durability goes into the roster.
      at(2, "hurt suit", () -> server(p -> {
         LegionSuitEntity s = suits(p).get(0);
         IronManState st = IronManState.of(p);
         float before = st.roster.durability(s.mark());
         Zombie z = zombie(p, s.position().add(2, 0, 0), false);
         s.hurt(p.serverLevel().damageSources().mobAttack(z), 20.0F);
         float after = st.roster.durability(s.mark());
         check("damage lowers roster durability", after < before, s.mark() + " " + before + " -> " + after);
         z.discard();
      }));
      // Codex #2: Tony's signature ray passes his own suit to hit the zombie behind it.
      at(5, "ray through suit", () -> server(p -> {
         cmd("kill @e[type=!player,type=!viltrumitecore:legion_suit]");
         Vec3 look = new Vec3(0, 0, 1);
         p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0.0F, 0.0F);
         Vec3 eye = p.getEyePosition();
         Zombie z = zombie(p, new Vec3(eye.x, p.getY(), eye.z + 12), false);
         List<LegionSuitEntity> s = suits(p);
         for (int i = 0; i < s.size(); i++) {
            s.get(i).moveTo(eye.x, p.getY() + 0.2, eye.z + 3 + i * 2.5, 0, 0);
         }
         SignatureTrace.Hit hit = SignatureTrace.shoot(p, look, 30.0);
         check("signature ray passes " + s.size() + " own suits to the enemy", hit.target() == z, hit.target());
         zombieId = z.getId();
      }));
      // Codex #1: ATTACK on a target 40 blocks away (outside the 28-block scan box).
      at(10, "far target", () -> server(p -> {
         // Not cmd("kill ..."): that runs one task later and would kill the new zombie too.
         clearZombies(p);
         Zombie z = zombie(p, p.position().add(40, 0, 0), false);
         z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
         zombieId = z.getId();
         face(p, z.getBoundingBox().getCenter());
      }));
      at(5, "ATTACK pick", () -> server(p -> check("crosshair picks the 40-block target", reflect(IronManLegion.class, null, "pickEntity", p) == p.serverLevel().getEntity(zombieId),
         p.serverLevel().getEntity(zombieId))));
      at(1, "ATTACK far", () -> order(LegionOrder.ATTACK));
      at(5, "ATTACK ordered", () -> server(p -> check("ATTACK stores the ordered target", Integer.valueOf(zombieId).equals(field(squad(p), "orderedTargetId")), describe(p))));
      at(55, "ATTACK checked", () -> server(p -> {
         boolean on = suits(p).stream().anyMatch(s -> s.brain != null && s.brain.target() != null && s.brain.target().getId() == zombieId);
         check("ATTACK engages the 40-block ordered target", on, describe(p));
      }));
      shot(1, "21_attack_far");
      // Codex #5: a spectator cannot command.
      at(5, "spectator", () -> cmd("gamemode spectator"));
      at(5, "order as spectator", () -> order(LegionOrder.FOLLOW));
      at(5, "spectator checked", () -> server(p -> check("spectator order refused", squad(p).order() == LegionOrder.ATTACK, describe(p))));
      at(2, "survival", () -> cmd("gamemode survival"));
      // Codex #3: HOLD, then Tony walks 120 blocks away; the suits stay on the point.
      at(5, "hold aim", () -> server(p -> {
         clearZombies(p);
         p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0.0F, 60.0F);
      }));
      at(3, "HOLD", () -> order(LegionOrder.HOLD));
      at(40, "HOLD set", () -> server(p -> {
         holdAt = squad(p).holdPoint();
         check("HOLD sets a point", squad(p).order() == LegionOrder.HOLD && holdAt != null, holdAt);
         if (holdAt != null) {
            cmd("forceload add " + (int)holdAt.x + " " + (int)holdAt.z);
         }
      }));
      shot(1, "22_hold");
      at(2, "walk away", () -> {
         cmd("tp @s 120 -60 0");
         maxStep = 0.0;
      });
      at(260, "HOLD checked", () -> server(p -> {
         double far = 0.0;
         for (LegionSuitEntity s : suits(p)) {
            far = Math.max(far, holdAt == null ? 999 : Math.sqrt(s.distanceToSqr(holdAt)));
         }
         check("HOLD suits stay near the held point while Tony is 120 blocks away (" + String.format("%.1f", far) + ")", far < 20.0 && !suits(p).isEmpty(), describe(p));
         check("no fallback teleport under HOLD", maxStep < 6.0, maxStep);
      }));
      at(2, "come back", () -> cmd("tp @s 0 -60 -6 0 0"));
      at(2, "GUARD again", () -> order(LegionOrder.GUARD));
      // Destroy one suit: crash, long cooldown, the other two remain.
      at(60, "destroy", () -> server(p -> {
         KEPT.clear();
         for (LegionSuitEntity s : suits(p)) {
            KEPT.put(s.mark(), IronManState.of(p).roster.durability(s.mark()));
         }
         LegionSuitEntity s = suits(p).get(0);
         Zombie z = zombie(p, s.position().add(2, 0, 0), false);
         s.hurt(p.serverLevel().damageSources().mobAttack(z), 9999.0F);
         check("destroyed suit crashes", s.phase() == LegionPhase.CRASH, describe(p));
         KEPT.remove(s.mark());
         KEPT.put(null, (float)s.mark().ordinal());
         z.discard();
      }));
      shot(6, "23_crash_a");
      shot(14, "24_crash_b");
      at(50, "crash done", () -> server(p -> {
         MarkId broken = MarkId.values()[KEPT.get(null).intValue()];
         IronManState st = IronManState.of(p);
         check("broken mark gets the long cooldown (~6000 t)", st.roster.cooldown(broken) > 5000, describe(p));
         check("broken mark is not in the Legion", st.roster.location(broken) != MarkLocation.LEGION, st.roster.location(broken));
         check("two suits remain", suits(p).size() == 2, describe(p));
      }));
      at(1, "fp", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
      shot(4, "25_hud_after_crash");
      // GUI scales with the Legion HUD up.
      for (int scale : new int[] {1, 2, 3, 4, 0}) {
         at(2, "gui " + scale, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
         });
         shot(6, "26_hud_scale_" + scale);
      }
      at(2, "gui 2", () -> {
         Minecraft.getInstance().options.guiScale().set(2);
         Minecraft.getInstance().resizeDisplay();
      });
      // RETURN all: durability kept, re-call does not repair.
      at(2, "RETURN all", () -> order(LegionOrder.RETURN));
      at(2, "RETURN spam", () -> order(LegionOrder.RETURN));
      until("all returned", () -> !squadActive(), () -> server(p -> {
         IronManState st = IronManState.of(p);
         boolean kept = true;
         for (Map.Entry<MarkId, Float> e : KEPT.entrySet()) {
            if (e.getKey() != null) {
               kept &= st.roster.location(e.getKey()) == MarkLocation.STORED && Math.abs(st.roster.durability(e.getKey()) - e.getValue()) < 0.01F;
            }
         }
         check("RETURN keeps durability and stores the marks", kept, describe(p) + " kept=" + KEPT);
         IronManLegion.clearCooldown(p, st);
      }));
      at(5, "recall same", () -> {
         int[] ids = KEPT.keySet().stream().filter(k -> k != null).mapToInt(Enum::ordinal).toArray();
         CoreMessages.sendToServer(new LegionCallC2SPacket(ids));
      });
      at(5, "recall checked", () -> server(p -> {
         boolean same = true;
         for (LegionSuitEntity s : suits(p)) {
            Float kept = KEPT.get(s.mark());
            same &= kept != null && Math.abs(IronManState.of(p).roster.durability(s.mark()) - kept) < 0.01F;
         }
         check("re-call does not repair", same && !suits(p).isEmpty(), describe(p));
      }));
      // Owner death and respawn: no suit, no LEGION entry left.
      at(80, "kill owner", () -> cmd("kill @s"));
      at(10, "death checked", () -> server(p -> {
         IronManState st = IronManState.of(p);
         check("owner death releases every suit (LEAVE/CRASH) and empties the LEGION roster", leaving(p) && (st == null || st.roster.legion().isEmpty()), describe(p));
      }));
      at(140, "death gone", () -> server(p -> check("suits gone after owner death", !IronManLegion.active(p), describe(p))));
      at(10, "respawn", () -> {
         if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.respawn();
         }
      });
      // Dimension change and hero change both recall.
      at(40, "call for dim", () -> server(p -> {
         IronManLegion.clearCooldown(p, IronManState.of(p));
         IronManLegion.call(p, new int[] {1, 3});
      }));
      at(60, "nether", () -> cmd("execute in minecraft:the_nether run tp @s 0 80 0"));
      at(20, "dim checked", () -> server(p -> check("dimension change recalls the Legion", !IronManLegion.active(p) && IronManState.of(p).roster.legion().isEmpty(), describe(p))));
      at(5, "overworld", () -> cmd("execute in minecraft:overworld run tp @s 0 -60 0"));
      at(40, "call for hero", () -> server(p -> {
         IronManLegion.clearCooldown(p, IronManState.of(p));
         IronManLegion.call(p, new int[] {4});
      }));
      at(40, "hero change", () -> server(p -> {
         List<LegionSuitEntity> before = suits(p);
         HeroRegistry.changeHero(p, HeroId.HUMAN);
         check("hero change releases the Legion (" + before.size() + " before)", leaving(p), describe(p));
      }));
      at(140, "hero gone", () -> server(p -> check("suits gone after hero change", !IronManLegion.active(p), describe(p))));
      at(5, "back to Iron Man", () -> server(p -> {
         HeroRegistry.changeHero(p, HeroId.IRON_MAN);
         IronManState st = IronManState.of(p);
         check("no LEGION entry survives a hero change", st != null && st.roster.legion().isEmpty(), describe(p));
      }));
      at(20, "done", () -> LOG.info("LEGION_PT DONE pass={} fail={} sounds={}", pass, fail, SOUNDS));
   }

   // ------------------------------------------------------------- events

   @SubscribeEvent
   public static void onPlaySound(PlaySoundEvent event) {
      if (ENABLED && event.getSound() != null) {
         String id = event.getSound().getLocation().toString();
         if (id.contains("ironman") || id.contains("jarvis")) {
            SOUNDS.merge(id, 1, Integer::sum);
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (!ENABLED || !watch || event.phase != TickEvent.Phase.END) {
         return;
      }

      for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
         for (LegionSuitEntity s : suits(p)) {
            Vec3 last = LAST.put(s.getId(), s.position());
            if (last != null && s.phase() != LegionPhase.APPROACH && s.phase() != LegionPhase.LEAVE && s.phase() != LegionPhase.CRASH) {
               maxStep = Math.max(maxStep, last.distanceTo(s.position()));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (!ENABLED || event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      mc.setWindowActive(true);
      if (mc.level == null || mc.player == null) {
         if (!MP.isEmpty()) {
            if (!connectRequested && mc.screen instanceof TitleScreen) {
               connectRequested = true;
               net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(MP),
                  new net.minecraft.client.multiplayer.ServerData("legion-qa", MP, false), false);
            }

            return;
         }

         if (!worldRequested && mc.screen instanceof TitleScreen && new File(mc.gameDirectory, "saves/" + WORLD).isDirectory()) {
            worldRequested = true;
            mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
         }

         return;
      }

      if (!scripted) {
         scripted = true;
         if (MP.isEmpty()) {
            script();
         } else if (WATCHER) {
            watcherScript();
         } else {
            mpScript();
         }
         LOG.info("LEGION_PT enabled, {} steps", STEPS.size());
      }

      ticks++;
      if (!MP.isEmpty()) {
         trackClient(mc);
      }

      RELEASES.removeIf(r -> {
         if (ticks >= r.at()) {
            KeyMapping.set(r.key().getKey(), false);
            return true;
         }

         return false;
      });
      if (ticks < 60 || pendingShot != null || stepIndex >= STEPS.size()) {
         return;
      }

      Step next = STEPS.get(stepIndex);
      if (wait < next.delay() || next.ready() != null && !next.ready().getAsBoolean() && wait < next.delay() + 900) {
         if (next.ready() != null && wait == next.delay() + 899) {
            check("step '" + next.label() + "' became ready", false, "timed out");
         }

         wait++;
         return;
      }

      Step step = STEPS.get(stepIndex++);
      wait = 0;
      LOG.info("LEGION_PT step {} '{}' at tick {}", stepIndex, step.label(), ticks);
      try {
         step.action().run();
      } catch (RuntimeException e) {
         fail++;
         LOG.error("LEGION_PT step '{}' failed", step.label(), e);
      }
   }

   @SubscribeEvent
   public static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (!ENABLED || event.phase != TickEvent.Phase.END || pendingShot == null || shotFrames-- > 0) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      String name = pendingShot + ".png";
      pendingShot = null;
      Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> LOG.info("LEGION_PT shot {}", name));
      LOG.info("LEGION_PT fps {} at shot {}", mc.getFps(), name);
   }
}
