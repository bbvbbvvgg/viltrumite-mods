package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.compat.spider.SpiderAbility;
import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import dev.baranhan.viltrumitecore.compat.spider.SpiderFeature;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.hero.ActionLocks;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.VenomDriveC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.VenomPossessS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The host takeover (spec §8): an enraged Venom flows over a weakened player and rides him for a few seconds while his
 * own body lies as a soft husk. Server-authoritative and memory only: a session lives in this map and nowhere else -
 * never in NBT, never in a save - and every way out (time, resistance, a broken husk, death, logout, a dimension
 * change, a hero change, the leash, the config, a server stop) runs the one {@link #end} that hands everything back.
 *
 * <p>The host keeps his inventory and his hero; nothing is dropped (tossing is refused and handed back), the ground,
 * lava and fire cannot kill him while he is driven. A Falbiks Spider-Man is driven through {@link SpiderCompat}
 * (swing, net, yank, flip) only when its adapter bound those features; otherwise every host is driven in the basic
 * mode (walk, jump, strike). A host is immune to another takeover for a minute after one ends.
 */
public final class VenomPossession {
   public enum Phase { ENGULF, CONTROL }

   /** Why a session ended (client wire: ordinal, append only). */
   public enum End {
      VOLUNTARY, TIMEOUT, RESISTED, HUSK_BROKEN, DEATH, LOGOUT, DIMENSION, LEASH, HERO_CHANGE, DISABLED, SERVER_STOP, HAZARD, INTERRUPTED;

      public static End of(CleanupReason reason) {
         return switch (reason) {
            case DEATH -> DEATH;
            case DISCONNECT -> LOGOUT;
            case HERO_CHANGE -> HERO_CHANGE;
            default -> INTERRUPTED;
         };
      }

      /** The symbiote was thrown off (it recoils) instead of leaving on its own. */
      public boolean violent() {
         return this == RESISTED || this == HUSK_BROKEN || this == HAZARD;
      }
   }

   public enum Refusal {
      DISABLED("disabled"), NO_PVP("no_pvp"), NO_RAGE("no_rage"), COOLDOWN("cooldown"), HUNGER("hunger"), NO_TARGET("no_target"), IMMUNE("immune"),
      NOT_WEAKENED("not_weakened");

      public final String key;

      Refusal(String key) {
         this.key = key;
      }
   }

   static final class Session {
      final LivingEntity possessor;
      final ServerPlayer host;
      final UUID effect = UUID.randomUUID();
      final boolean boss;
      final boolean spider;
      Phase phase = Phase.ENGULF;
      int ticks;
      int control;
      final int max;
      int resist;
      int lastResistTick = -100;
      int lastAttack = -100;
      int decay;
      VenomDriveC2SPacket input = new VenomDriveC2SPacket(false, 0, 0, 0, 0, 0, 0);
      int inputAge;
      int heldAbility;
      boolean primaryDown;
      Vec3 huskAt;

      Session(LivingEntity possessor, ServerPlayer host, boolean boss) {
         this.possessor = possessor;
         this.host = host;
         this.boss = boss;
         this.spider = SpiderCompat.isSpider(host) && SpiderCompat.has(SpiderFeature.DRIVE_ABILITIES);
         this.max = boss ? VenomRules.BOSS_CONTROL_TICKS : VenomRules.controlTicks();
         this.huskAt = possessor.position();
         this.input = new VenomDriveC2SPacket(false, 0, 0, host.getYRot(), host.getXRot(), 0, 0);
      }

      int remaining() {
         return this.phase == Phase.ENGULF ? this.max : Math.max(0, this.max - this.control);
      }

      int bits() {
         return (this.spider ? VenomPossessS2CPacket.SPIDER : 0) | (this.boss ? VenomPossessS2CPacket.BOSS : 0);
      }
   }

   private static final Map<UUID, Session> BY_HOST = new HashMap<>();
   private static final Map<UUID, Session> BY_POSSESSOR = new HashMap<>();
   /** Host UUID -> game time until which no takeover may touch him (anti-chain). */
   private static final Map<UUID, Long> IMMUNE = new HashMap<>();
   /** Host UUID -> game time until which the environment cannot hurt him (after a release). */
   private static final Map<UUID, Long> SAFE = new HashMap<>();

   static {
      ActionLocks.register(VenomPossession::isHost);
   }

   private VenomPossession() {
   }

   public static void init() {
   }

   // --- queries ------------------------------------------------------------------------------------------------

   public static boolean isHost(@Nullable LivingEntity e) {
      return e != null && !e.level().isClientSide && BY_HOST.containsKey(e.getUUID());
   }

   public static boolean isPossessor(@Nullable LivingEntity e) {
      return e != null && !e.level().isClientSide && BY_POSSESSOR.containsKey(e.getUUID());
   }

   public static boolean involved(@Nullable LivingEntity e) {
      return isHost(e) || isPossessor(e);
   }

   /** The host's environment cannot hurt him: driven now, or just released. */
   public static boolean shielded(LivingEntity host) {
      if (isHost(host)) {
         return true;
      }
      Long until = SAFE.get(host.getUUID());
      return until != null && host.level().getGameTime() <= until;
   }

   /** The takeover's remaining ticks for the possessor's snapshot (0 = none). */
   public static int remainingFor(Player player) {
      Session s = player.level().isClientSide ? null : BY_POSSESSOR.get(player.getUUID());
      return s == null ? 0 : s.remaining();
   }

   public static boolean immune(LivingEntity host) {
      Long until = IMMUNE.get(host.getUUID());
      return until != null && host.level().getGameTime() <= until;
   }

   public static boolean enabled() {
      return ViltrumiteCoreConfig.INSTANCE == null || ViltrumiteCoreConfig.INSTANCE.venomPossessionEnabled;
   }

   public static boolean bossMayPossess() {
      return enabled() && (ViltrumiteCoreConfig.INSTANCE == null || ViltrumiteCoreConfig.INSTANCE.venomBossPossession);
   }

   // --- starting -----------------------------------------------------------------------------------------------

   /** Why a player Venom may not start a takeover now (null = he may look for a host). */
   @Nullable
   static Refusal check(ServerPlayer player, VenomState state) {
      if (!enabled()) return Refusal.DISABLED;
      if (!ServerRules.pvp(player)) return Refusal.NO_PVP;
      if (!state.rage) return Refusal.NO_RAGE;
      if (state.cooldowns[VenomRules.CD_TAKEOVER] > 0) return Refusal.COOLDOWN;
      if (state.hunger < VenomRules.TAKEOVER_COST) return Refusal.HUNGER;
      return null;
   }

   static void tellRefusal(ServerPlayer player, Refusal refusal) {
      player.displayClientMessage(Component.translatable("message.viltrumitecore.venom.takeover." + refusal.key), true);
   }

   /** May {@code possessor} take {@code host} over: a real, legal, unprotected player who is not part of another one. */
   public static boolean eligible(LivingEntity possessor, LivingEntity host) {
      if (!(host instanceof ServerPlayer p) || !p.isAlive() || p.isCreative() || p.isSpectator() || p == possessor || p.isSleeping()
         || p.level() != possessor.level() || involved(p) || immune(p) || p.isPassenger()) {
         return false;
      }
      if (possessor instanceof ServerPlayer sp && !VenomTargets.mayHit(sp, p)) {
         return false;
      }
      return HeroRegistry.allowsExternalControl(p, ControlKind.POSSESS);
   }

   /** The host a player Venom takes: the one he holds, else the one he looks at within reach. */
   @Nullable
   static LivingEntity pick(ServerPlayer player, VenomState state) {
      LivingEntity held = VenomCombat.held(player, state);
      if (held != null && eligible(player, held)) {
         return held;
      }
      Vec3 eye = player.getEyePosition();
      List<LivingEntity> near = VenomTargets.arc(player, eye, player.getLookAngle(), VenomRules.TAKEOVER_RANGE, 90.0,
         e -> e instanceof ServerPlayer && eligible(player, e) && VenomTargets.visible(player.level(), eye, VenomTargets.center(e)));
      LivingEntity aimed = VenomTargets.aimed(near, eye, player.getLookAngle(), 35.0);
      LivingEntity pick = aimed != null ? aimed : near.isEmpty() ? null : near.get(0);
      if (pick != null && !weakened(player, pick)) {
         tellRefusal(player, Refusal.NOT_WEAKENED);
         return null;
      }
      if (pick == null) {
         for (LivingEntity e : VenomTargets.arc(player, eye, player.getLookAngle(), VenomRules.TAKEOVER_RANGE, 90.0, e -> e instanceof ServerPlayer)) {
            if (immune(e)) {
               tellRefusal(player, Refusal.IMMUNE);
               return null;
            }
         }
      }
      return pick;
   }

   static boolean weakened(LivingEntity possessor, LivingEntity host) {
      boolean held = possessor instanceof ServerPlayer sp && VenomState.of(sp) != null && VenomState.of(sp).heldId == host.getId();
      return VenomRules.weakened(host.getHealth(), host.getMaxHealth(), held || SpiderCompat.cocooned(host)
         || ControlManager.get((ServerLevel)host.level()).isAnchored(host));
   }

   /** A player Venom leaps onto the host: the ENGULF move, the host held still meanwhile. */
   static boolean engulf(ServerPlayer player, VenomState state, LivingEntity hostEntity) {
      if (!(hostEntity instanceof ServerPlayer host) || !eligible(player, host)) {
         return false;
      }
      if (state.holding()) {
         VenomCombat.release(player, state, ReleaseReason.NORMAL_END, null);
      }
      Session s = open(player, host, false);
      if (s == null) {
         return false;
      }
      state.start(VenomMove.ENGULF);
      state.hunger = VenomRules.feed(state.hunger, -VenomRules.TAKEOVER_COST);
      HeroSpeech.say(player, VenomLines.TAKEOVER);
      return true;
   }

   /** The boss leaps onto a host (its AI calls this; it checks the config itself). */
   public static boolean engulfByBoss(LivingEntity boss, ServerPlayer host) {
      if (!bossMayPossess() || !eligible(boss, host)) {
         return false;
      }
      return open(boss, host, true) != null;
   }

   @Nullable
   private static Session open(LivingEntity possessor, ServerPlayer host, boolean boss) {
      ServerLevel level = host.serverLevel();
      Session s = new Session(possessor, host, boss);
      long expiry = level.getGameTime() + VenomRules.ENGULF_TICKS + s.max + 40;
      if (!ControlManager.get(level).tryAcquire(host, possessor.getUUID(), s.effect, ControlKind.PULL, expiry)) {
         return null;
      }
      BY_HOST.put(host.getUUID(), s);
      BY_POSSESSOR.put(possessor.getUUID(), s);
      broadcast(s, VenomPossessS2CPacket.ENGULF, 0);
      VenomSounds.play(host, VenomSounds.ENGULF, 1.2F, VenomSounds.jitter(host, 1.0F));
      return s;
   }

   // the hooks the player move timeline calls
   static void engulfTick(ServerPlayer player, VenomState state) {
      Session s = BY_POSSESSOR.get(player.getUUID());
      if (s == null) {
         state.endMove();
         return;
      }
      // the lunge: the symbiote's body closes on the host
      Vec3 to = s.host.position().subtract(VenomTargets.flat(s.host.position().subtract(player.position())).scale(0.9));
      if (player.position().distanceTo(to) > 0.3 && state.elapsed < VenomMove.ENGULF.contact) {
         Vec3 d = to.subtract(player.position());
         player.setDeltaMovement(d.scale(0.35));
         player.hurtMarked = true;
      }
   }

   static void engulfContact(ServerPlayer player, VenomState state) {
      // the manager's tick switches the phase on the same tick count (one clock for the player and the boss)
   }

   static void engulfFailed(ServerPlayer player, VenomState state) {
      Session s = BY_POSSESSOR.get(player.getUUID());
      if (s != null && s.phase == Phase.ENGULF) {
         end(s, End.INTERRUPTED);
      }
   }

   private static void toControl(Session s) {
      s.phase = Phase.CONTROL;
      s.control = 0;
      s.huskAt = s.possessor.position();
      if (s.possessor instanceof ServerPlayer player) {
         VenomState state = VenomState.ensure(player);
         state.hostId = s.host.getId();
         state.start(VenomMove.POSSESS);
      } else if (s.possessor instanceof VenomPossessor body) {
         body.beganPossessing(s.host);
      }
      if (s.spider) {
         SpiderCompat.releaseInputs(s.host);
      }
      PossessionInputGate.install(s.host);
      VenomFx.send(s.possessor, VenomFx.ENGULF, VenomTargets.center(s.host), VenomTargets.center(s.possessor), 1.0F, 3, s.host.getId());
      VenomSounds.play(s.host, VenomSounds.ROAR, 1.0F, 0.75F);
      broadcast(s, VenomPossessS2CPacket.CONTROL, 0);
      VenomLines.whisper(s.host, s.host.getRandom());
   }

   // --- inputs -------------------------------------------------------------------------------------------------

   /** C2S: the possessor's drive, or the host's resistance press. Anything from anybody else is ignored. */
   public static void input(ServerPlayer sender, VenomDriveC2SPacket packet) {
      if (packet.resist()) {
         Session s = BY_HOST.get(sender.getUUID());
         if (s != null && s.phase == Phase.CONTROL && s.control - s.lastResistTick >= VenomRules.RESIST_PRESS_GAP) {
            s.lastResistTick = s.control;
            s.resist = VenomRules.resist(s.resist, true, 0);
         }
         return;
      }
      Session s = BY_POSSESSOR.get(sender.getUUID());
      if (s != null && s.possessor == sender) {
         s.input = packet;
         s.inputAge = 0;
      }
   }

   /** The boss's AI drives the same way a player would. */
   public static void bossInput(LivingEntity boss, VenomDriveC2SPacket packet) {
      Session s = BY_POSSESSOR.get(boss.getUUID());
      if (s != null && s.boss) {
         s.input = packet.clamped();
         s.inputAge = 0;
      }
   }

   @Nullable
   public static ServerPlayer hostOf(LivingEntity possessor) {
      Session s = BY_POSSESSOR.get(possessor.getUUID());
      return s == null ? null : s.host;
   }

   public static int controlTicksOf(LivingEntity possessor) {
      Session s = BY_POSSESSOR.get(possessor.getUUID());
      return s == null || s.phase != Phase.CONTROL ? -1 : s.control;
   }

   // --- the tick -----------------------------------------------------------------------------------------------

   public static void tick(MinecraftServer server) {
      if (BY_HOST.isEmpty()) {
         if (!IMMUNE.isEmpty() && server.getTickCount() % 200 == 0) {
            long now = server.overworld().getGameTime();
            IMMUNE.values().removeIf(t -> t < now);
            SAFE.values().removeIf(t -> t < now);
         }
         return;
      }
      for (Session s : new ArrayList<>(BY_HOST.values())) {
         End reason = verdict(s);
         if (reason != null) {
            end(s, reason);
            continue;
         }
         s.ticks++;
         if (s.phase == Phase.ENGULF) {
            hold(s);
            if (s.ticks >= VenomMove.ENGULF.contact) {
               toControl(s);
            }
            continue;
         }
         s.control++;
         drive(s);
         if (s.possessor instanceof ServerPlayer && s.possessor.isAlive()) {
            // the husk stays where it fell
            Vec3 p = s.possessor.position();
            if (p.distanceToSqr(s.huskAt) > 0.04 && s.possessor.onGround()) {
               s.huskAt = p;
            }
         }
         s.decay++;
         if (s.decay >= 4) {
            s.decay = 0;
            s.resist = Math.max(0, s.resist - VenomRules.RESIST_DECAY);
         }
         if (s.control % 5 == 0) {
            broadcast(s, VenomPossessS2CPacket.STATUS, 0);
         }
         if (s.control % 60 == 30) {
            VenomLines.whisper(s.host, s.host.getRandom());
         }
         ControlManager.get(s.host.serverLevel()).refresh(s.host.getUUID(), s.possessor.getUUID(), s.effect, ControlKind.PULL,
            s.host.level().getGameTime() + 60);
      }
   }

   /** Every end condition, in one place (null: the session goes on). */
   @Nullable
   private static End verdict(Session s) {
      ServerPlayer host = s.host;
      LivingEntity possessor = s.possessor;
      if (!host.isAlive() || !possessor.isAlive()) return End.DEATH;
      if (host.hasDisconnected() || possessor instanceof ServerPlayer sp && sp.hasDisconnected()) return End.LOGOUT;
      if (host.level() != possessor.level()) return End.DIMENSION;
      if (!enabled() || s.boss && !bossMayPossess()) return End.DISABLED;
      if (possessor instanceof ServerPlayer sp && !ServerRules.pvp(sp)) return End.DISABLED;
      if (host.isCreative() || host.isSpectator()) return End.INTERRUPTED;
      if (host.distanceToSqr(possessor) > VenomRules.TAKEOVER_LEASH * VenomRules.TAKEOVER_LEASH) return End.LEASH;
      if (host.isInLava() || host.getY() < host.level().getMinBuildHeight() + 2) return End.HAZARD;
      if (possessor instanceof ServerPlayer sp && VenomState.of(sp) == null) return End.HERO_CHANGE;
      if (s.phase == Phase.ENGULF && s.ticks > VenomRules.ENGULF_TICKS + 5) return End.INTERRUPTED;
      if (s.phase == Phase.CONTROL) {
         if (s.resist >= VenomRules.RESIST_TO_BREAK) return End.RESISTED;
         if (s.control >= s.max) return End.TIMEOUT;
      }
      return null;
   }

   private static void hold(Session s) {
      ServerPlayer host = s.host;
      host.setDeltaMovement(host.getDeltaMovement().multiply(0.2, 1.0, 0.2));
      host.hurtMarked = true;
   }

   /** One tick of driving: the host's client moves itself with the relayed input; attacks are the server's. */
   private static void drive(Session s) {
      s.inputAge++;
      VenomDriveC2SPacket in = s.inputAge > 40 ? new VenomDriveC2SPacket(false, 0, 0, s.input.yaw(), s.input.pitch(), 0, 0) : s.input;
      ServerPlayer host = s.host;
      int bits = s.bits() | ((in.bits() & VenomDriveC2SPacket.JUMP) != 0 ? VenomPossessS2CPacket.JUMP : 0)
         | ((in.bits() & VenomDriveC2SPacket.SNEAK) != 0 ? VenomPossessS2CPacket.SNEAK : 0)
         | ((in.bits() & VenomDriveC2SPacket.SPRINT) != 0 ? VenomPossessS2CPacket.SPRINT : 0);
      CoreMessages.sendToPlayer(new VenomPossessS2CPacket(VenomPossessS2CPacket.DRIVE, host.getId(), s.possessor.getId(), s.remaining(), s.max, s.resist,
         bits, in.forward(), in.strafe(), in.yaw(), in.pitch(), 0), host);
      // the server keeps the look too, so the host's strikes and the Spider's aim follow the possessor at once
      host.setYRot(in.yaw());
      host.setXRot(in.pitch());
      host.setYHeadRot(in.yaw());
      boolean attack = (in.bits() & VenomDriveC2SPacket.ATTACK) != 0;
      if (s.spider) {
         boolean down = (in.bits() & VenomDriveC2SPacket.ATTACK_HELD) != 0;
         if (down != s.primaryDown) {
            s.primaryDown = down;
            SpiderCompat.drivePrimary(host, down);
         }
         int a = in.ability();
         if (a != s.heldAbility) {
            if (s.heldAbility > 0) {
               SpiderCompat.drive(host, SpiderAbility.values()[s.heldAbility - 1], false);
            }
            if (a > 0) {
               SpiderCompat.drive(host, SpiderAbility.values()[a - 1], true);
            }
            s.heldAbility = Math.max(0, a);
         }
         s.input = new VenomDriveC2SPacket(false, in.forward(), in.strafe(), in.yaw(), in.pitch(), in.bits() & ~VenomDriveC2SPacket.ATTACK, in.ability());
      } else if (attack && s.control - s.lastAttack >= VenomRules.DRIVEN_ATTACK_GAP) {
         s.lastAttack = s.control;
         drivenStrike(s);
         s.input = new VenomDriveC2SPacket(false, in.forward(), in.strafe(), in.yaw(), in.pitch(), in.bits() & ~VenomDriveC2SPacket.ATTACK, in.ability());
      }
   }

   /** The host's own fist (basic mode), aimed by the possessor; no item is used up, nothing is dropped. */
   private static void drivenStrike(Session s) {
      ServerPlayer host = s.host;
      host.swing(InteractionHand.MAIN_HAND, true);
      Vec3 eye = host.getEyePosition();
      Vec3 end = eye.add(host.getLookAngle().scale(VenomRules.DRIVEN_ATTACK_REACH));
      var block = host.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, host));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }
      List<LivingEntity> hits = VenomTargets.along(host, eye, end, 0.4, e -> e != host && e != s.possessor && VenomTargets.mayHit(host, e));
      if (hits.isEmpty()) {
         return;
      }
      LivingEntity t = hits.get(0);
      float damage = (float)Math.max(4.0, host.getAttributeValue(Attributes.ATTACK_DAMAGE));
      VenomTargets.strike(host, t, damage);
      VenomTargets.push(t, VenomTargets.outward(host.position(), t).scale(0.45).add(0, 0.15, 0));
      VenomFx.send(s.possessor, VenomFx.CLAW, VenomTargets.center(t), VenomTargets.hand(host), 0.8F, 1, t.getId());
   }

   /** A loud sound near a ridden host: the symbiote flinches and the host gets a third of the way free. */
   static void loudSound(ServerLevel level, Vec3 at, float volume, boolean listed) {
      for (Session s : BY_HOST.values()) {
         if (s.phase == Phase.CONTROL && s.host.level() == level && VenomWeakness.loudDistance(s.host.position().distanceTo(at), volume, listed)) {
            s.resist = Math.min(VenomRules.RESIST_TO_BREAK, s.resist + VenomRules.RESIST_TO_BREAK / 3);
         }
      }
   }

   /** The husk took a hit: a hard one (or a fire) breaks the bond. */
   static void huskHurt(LivingEntity husk, float amount) {
      Session s = BY_POSSESSOR.get(husk.getUUID());
      if (s != null && s.phase == Phase.CONTROL && amount >= VenomRules.HUSK_BREAK_DAMAGE) {
         end(s, End.HUSK_BROKEN);
      }
   }

   // --- the one way out ----------------------------------------------------------------------------------------

   public static void endAllFor(LivingEntity e, End reason) {
      Session s = BY_POSSESSOR.get(e.getUUID());
      if (s != null) {
         end(s, reason);
      }
      s = BY_HOST.get(e.getUUID());
      if (s != null) {
         end(s, reason);
      }
   }

   public static void endAll(End reason) {
      for (Session s : new ArrayList<>(BY_HOST.values())) {
         end(s, reason);
      }
      IMMUNE.clear();
      SAFE.clear();
   }

   static void end(Session s, End reason) {
      if (BY_HOST.get(s.host.getUUID()) != s) {
         return; // already ended
      }
      BY_HOST.remove(s.host.getUUID());
      BY_POSSESSOR.remove(s.possessor.getUUID());
      ServerPlayer host = s.host;
      PossessionInputGate.remove(host);
      if (s.spider) {
         if (s.heldAbility > 0) {
            SpiderCompat.drive(host, SpiderAbility.values()[s.heldAbility - 1], false);
         }
         if (s.primaryDown) {
            SpiderCompat.drivePrimary(host, false);
         }
         SpiderCompat.releaseInputs(host);
      }
      if (host.level() instanceof ServerLevel level) {
         ControlManager.get(level).release(host.getUUID(), s.effect, ReleaseReason.NORMAL_END);
         long now = level.getGameTime();
         if (s.phase == Phase.CONTROL) {
            IMMUNE.put(host.getUUID(), now + VenomRules.VICTIM_IMMUNITY);
         }
         SAFE.put(host.getUUID(), now + VenomRules.HOST_SAFE_TICKS);
      }
      if (!host.hasDisconnected() && host.isAlive()) {
         host.fallDistance = 0;
         host.connection.teleport(host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
      }
      if (s.possessor instanceof ServerPlayer player) {
         VenomState state = VenomState.of(player);
         if (state != null) {
            state.hostId = -1;
            if (state.is(VenomMove.POSSESS) || state.is(VenomMove.ENGULF)) {
               state.endMove();
            }
            state.cooldowns[VenomRules.CD_TAKEOVER] = VenomRules.COOLDOWN_TICKS[VenomRules.CD_TAKEOVER];
            VenomSkills.endRage(player, state);
            if (player.isAlive() && !player.hasDisconnected() && reason != End.DEATH) {
               state.start(reason.violent() ? VenomMove.RECOIL : VenomMove.REFORM);
               state.recoilImmune = VenomRules.RECOIL_IMMUNITY;
            }
         }
      } else if (s.possessor instanceof VenomPossessor boss) {
         boss.onPossessionEnd(reason);
      }
      if (s.phase == Phase.CONTROL && host.isAlive()) {
         VenomFx.send(s.possessor, VenomFx.ENGULF, VenomTargets.center(host), VenomTargets.center(s.possessor), 0.5F, 0, host.getId());
         VenomSounds.play(host, VenomSounds.RELEASE, 1.0F, VenomSounds.jitter(host, 1.0F));
      }
      broadcast(s, VenomPossessS2CPacket.END, reason.ordinal());
   }

   private static void broadcast(Session s, int op, int reason) {
      VenomPossessS2CPacket packet = VenomPossessS2CPacket.state(op, s.host.getId(), s.possessor.getId(), s.remaining(), s.max, s.resist, s.bits(), reason);
      if (s.host.hasDisconnected()) {
         if (s.possessor instanceof ServerPlayer p && !p.hasDisconnected()) {
            CoreMessages.sendToPlayer(packet, p);
         }
         return;
      }
      // the possessor tracks his host almost always; a second copy is harmless (the client applies states idempotently)
      CoreMessages.sendToTrackingAndSelf(packet, s.host);
      if (s.possessor instanceof ServerPlayer p && !p.hasDisconnected()) {
         CoreMessages.sendToPlayer(packet, p);
      }
   }

   /** A newly tracking player learns about running sessions (StartTracking). */
   public static void startTracking(ServerPlayer viewer, Entity target) {
      Session s = target instanceof LivingEntity l ? BY_HOST.get(l.getUUID()) : null;
      if (s != null) {
         CoreMessages.sendToPlayer(VenomPossessS2CPacket.state(s.phase == Phase.CONTROL ? VenomPossessS2CPacket.CONTROL : VenomPossessS2CPacket.ENGULF,
            s.host.getId(), s.possessor.getId(), s.remaining(), s.max, s.resist, s.bits(), 0), viewer);
      }
   }

   /** Test hook: running sessions. */
   static int sessions() {
      return BY_HOST.size();
   }
}
