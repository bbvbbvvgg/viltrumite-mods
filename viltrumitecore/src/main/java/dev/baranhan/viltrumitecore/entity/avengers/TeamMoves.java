package dev.baranhan.viltrumitecore.entity.avengers;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.captain.CaptainCombat;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainLines;
import dev.baranhan.viltrumitecore.hero.captain.CaptainSounds;
import dev.baranhan.viltrumitecore.hero.captain.CaptainState;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The three joint moves of the summoned team (rework §15), one at a time, ticked by the assemble scene during the
 * fight. Each move takes its Avengers out of their own brains ({@link AvengerBrain#joint}), drives both bodies
 * on one shared clock and gives them back when it ends or is cancelled. Real interplay, no simultaneous attacks:
 * <ul>
 *    <li>vault — Clint runs at Natasha, springs off her cupped hands, hangs at the top and looses four arrows down,
 *    lands safely beside her;</li>
 *    <li>charged unibeam — Iron Man hovers above Thor, Thor calls a bolt into Mjolnir and arcs it into Tony's chest,
 *    the unibeam that follows is wider, longer and stronger;</li>
 *    <li>shield strike — Thor asks Steve to raise the shield, waits for a real (server-side) block toward him and
 *    hammers the shield: a shockwave with capped damage and a short stun, Steve braces under the blow.</li>
 * </ul>
 * Any missing participant, a lost line of sight, Steve walking off or the window running out cancels the move
 * safely (gravity, flight and the brains restored). Damage goes through {@link TeamDamage}; allies are never
 * hit ({@link AvengerEntity#mayHit}). Everything is server-side; clients get the synced actions, the FX packets,
 * the sounds and the subtitle. Server only.
 */
public final class TeamMoves {
   private static final Logger LOGGER = LogUtils.getLogger();
   /** Playtest log (-Dviltrumite.qa.team=true): start / end / cancel of every joint move. */
   static final boolean QA = Boolean.getBoolean("viltrumite.qa.team");
   static final String[] NAMES = {"vault", "beam", "shield"};

   private final ServerPlayer owner;
   private final int[] cooldown = TeamRules.FIRST.clone();
   private int gap;
   private int rotation;
   private int forced = -1;
   private int clock;
   @Nullable private Move running;

   public TeamMoves(ServerPlayer owner) {
      this.owner = owner;
   }

   /** Index of a move by its command name (vault, beam, shield), or -1. */
   public static int parse(String name) {
      for (int i = 0; i < NAMES.length; i++) {
         if (NAMES[i].equalsIgnoreCase(name)) {
            return i;
         }
      }
      return -1;
   }

   public static List<String> names() {
      return List.of(NAMES);
   }

   /** Operator playtest: try this move next, ignoring its cooldown and the gap (/hero team). */
   public void force(int move) {
      this.forced = Math.max(-1, Math.min(TeamRules.MOVES - 1, move));
      this.gap = 0;
   }

   @Nullable
   public String running() {
      return this.running == null ? null : NAMES[this.running.index];
   }

   /** Server, every fight tick: runs the current move or looks for a chance to start one. */
   public void tick(Map<AvengerKind, AvengerEntity> team) {
      this.clock++;
      for (int i = 0; i < TeamRules.MOVES; i++) {
         if (this.cooldown[i] > 0) {
            this.cooldown[i]--;
         }
      }
      if (this.gap > 0) {
         this.gap--;
      }
      if (this.running != null) {
         Move m = this.running;
         if (!m.tick()) {
            this.end(m, m.done ? "done" : m.reason);
         }
         return;
      }
      if (this.clock % TeamRules.CHECK_INTERVAL != 0 && this.forced < 0) {
         return;
      }
      Move[] plans = new Move[TeamRules.MOVES];
      boolean[] ready = new boolean[TeamRules.MOVES];
      for (int i = 0; i < TeamRules.MOVES; i++) {
         if (this.cooldown[i] <= 0 || this.forced == i) {
            plans[i] = this.plan(i, team);
            ready[i] = plans[i] != null;
         }
      }
      int pick = this.forced >= 0 ? (ready[this.forced] ? this.forced : -1) : TeamRules.pick(this.cooldown, this.gap, false, this.rotation, ready);
      if (pick < 0) {
         return;
      }
      this.forced = -1;
      this.rotation = pick + 1;
      this.running = plans[pick];
      this.running.begin();
      if (QA) {
         LOGGER.info("TEAMMOVE start {} t={}", NAMES[pick], this.clock);
      }
   }

   /** The fight ends (departure, dismissal, scene gone): stop the move and give the bodies back. */
   public void cancel() {
      if (this.running != null) {
         this.running.reason = "scene";
         this.end(this.running, "scene");
      }
   }

   private void end(Move m, String why) {
      m.cleanup();
      for (AvengerEntity e : m.cast) {
         m.release(e);
      }
      this.running = null;
      this.cooldown[m.index] = m.done ? TeamRules.COOLDOWN[m.index] : TeamRules.CANCEL_COOLDOWN[m.index];
      this.gap = TeamRules.GAP;
      if (QA) {
         LOGGER.info("TEAMMOVE end {} {} after {} t", NAMES[m.index], why, m.t);
      }
   }

   // --- planning ---

   @Nullable
   private Move plan(int index, Map<AvengerKind, AvengerEntity> team) {
      return switch (index) {
         case TeamRules.VAULT -> this.planVault(free(team.get(AvengerKind.BLACK_WIDOW)), free(team.get(AvengerKind.HAWKEYE)));
         case TeamRules.BEAM -> this.planBeam(free(team.get(AvengerKind.THOR)), free(team.get(AvengerKind.IRON_MAN)));
         case TeamRules.SHIELD -> this.planShield(free(team.get(AvengerKind.THOR)));
         default -> null;
      };
   }

   /** A team member that can join a move now (alive, fighting, not busy), or null. */
   @Nullable
   private static AvengerEntity free(@Nullable AvengerEntity e) {
      return fit(e) && e.brain.freeForTeam() ? e : null;
   }

   /** Still able to take part (alive, in the fight, with a brain). */
   static boolean fit(@Nullable AvengerEntity e) {
      return e != null && e.isAlive() && !e.isRemoved() && e.phase() == AvengerEntity.PHASE_FIGHT && e.brain != null && !e.scripted;
   }

   private int enemiesNear(AvengerBrain brain, Vec3 at, double radius, float[] toughest) {
      int n = 0;
      for (LivingEntity e : brain.enemiesAround(at, radius)) {
         if (brain.hostile(e) && brain.reachable(this.owner, e)) {
            n++;
            toughest[0] = Math.max(toughest[0], e.getMaxHealth());
         }
      }
      return n;
   }

   @Nullable
   private Move planVault(@Nullable AvengerEntity widow, @Nullable AvengerEntity clint) {
      if (widow == null || clint == null || !widow.onGround() || !clint.onGround() || widow.isInWater() || clint.isInWater()
         || widow.distanceTo(clint) > TeamRules.VAULT_PAIR_RANGE || widow.distanceToSqr(this.owner) > 20 * 20) {
         return null;
      }
      float[] tough = {0};
      if (!TeamRules.worthIt(this.enemiesNear(clint.brain, widow.position(), TeamRules.VAULT_ENEMY_RANGE, tough), tough[0])) {
         return null;
      }
      Vec3 toClint = clint.position().subtract(widow.position());
      Vec3 h = toClint.horizontalDistanceSqr() < 1.0E-4 ? widow.getLookAngle() : new Vec3(toClint.x, 0, toClint.z).normalize();
      Vec3 spot = widow.position().add(h.scale(0.9));
      double rise = TeamRules.apex(TeamRules.VAULT_LAUNCH) + 0.6;
      if (!widow.level().noCollision(clint, clint.getBoundingBox().move(spot.subtract(clint.position())).expandTowards(0, rise, 0))) {
         return null; // a roof over Natasha: no vault
      }
      return new Vault(widow, clint, spot);
   }

   @Nullable
   private Move planBeam(@Nullable AvengerEntity thor, @Nullable AvengerEntity tony) {
      if (thor == null || tony == null || !(tony.brain instanceof IronManBrain im) || !im.unibeamNearlyReady()) {
         return null;
      }
      double pair = thor.distanceTo(tony);
      LivingEntity t = tony.brain.target();
      if (pair < TeamRules.BEAM_PAIR_MIN || pair > TeamRules.BEAM_PAIR_MAX || t == null || !t.isAlive() || !tony.brain.sees(t)) {
         return null;
      }
      double gap = thor.distanceTo(t);
      if (gap < TeamRules.BEAM_TARGET_MIN || gap > TeamRules.BEAM_TARGET_MAX) {
         return null;
      }
      float[] tough = {t.getMaxHealth()};
      if (!TeamRules.worthIt(this.enemiesNear(tony.brain, t.position(), 5.0, tough), tough[0])
         || !CaptainTargets.visible(thor.level(), thor.getEyePosition(), tony.getBoundingBox().getCenter())) {
         return null;
      }
      return new Beam(thor, tony, im, t);
   }

   @Nullable
   private Move planShield(@Nullable AvengerEntity thor) {
      CaptainState s = CaptainState.of(this.owner);
      if (thor == null || s == null || !this.owner.isAlive() || !this.owner.onGround() || !s.shieldInHand() || s.move != null && s.move.ceremony()
         || thor.distanceTo(this.owner) > TeamRules.SHIELD_CALL_RANGE
         || !CaptainTargets.visible(thor.level(), thor.getEyePosition(), this.owner.getEyePosition())) {
         return null;
      }
      float[] tough = {0};
      if (!TeamRules.worthIt(this.enemiesNear(thor.brain, this.owner.position(), TeamRules.SHIELD_ENEMY_RANGE, tough), tough[0])) {
         return null;
      }
      return new ShieldStrike(thor);
   }

   // --- shared helpers ---

   static void face(AvengerEntity e, Vec3 at) {
      Vec3 d = at.subtract(e.position());
      if (d.horizontalDistanceSqr() < 1.0E-4) {
         return;
      }
      float yaw = (float)(Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
      e.setYRot(yaw);
      e.setYBodyRot(yaw);
      e.setYHeadRot(yaw);
      e.getLookControl().setLookAt(at.x, at.y, at.z, 60.0F, 60.0F);
   }

   private ServerLevel level() {
      return this.owner.serverLevel();
   }

   /** One running joint move. */
   abstract class Move {
      final int index;
      final List<AvengerEntity> cast = new ArrayList<>();
      int t;
      int stage;
      int stageT;
      boolean done;
      String reason = "cancel";

      Move(int index) {
         this.index = index;
      }

      void claim(AvengerEntity e) {
         e.brain.joinTeam(this);
         this.cast.add(e);
      }

      void release(AvengerEntity e) {
         if (e.brain != null && e.brain.joint == this) {
            e.brain.joint = null;
            e.play(AvengerAction.NONE);
         }
      }

      void next(int stage) {
         this.stage = stage;
         this.stageT = 0;
      }

      /** Cancels with a reason (playtest log); false so tick() can return it. */
      boolean cancel(String why) {
         this.done = false;
         this.reason = why;
         return false;
      }

      abstract void begin();

      /** One tick; false when the move is over ({@link #done} says how). */
      boolean tick() {
         this.t++;
         this.stageT++;
         return this.step();
      }

      abstract boolean step();

      /** Restores what the move changed on the bodies (gravity, physics, flight). */
      void cleanup() {
      }
   }

   // =========================================================================== 15.1 the vault

   final class Vault extends Move {
      private static final int RUN = 0, RISE = 1, HANG = 2, FALL = 3;
      final AvengerEntity widow;
      final AvengerEntity clint;
      final Vec3 spot;
      private int shot;
      private boolean widowFree;
      private final Set<Integer> shotAt = new HashSet<>();

      Vault(AvengerEntity widow, AvengerEntity clint, Vec3 spot) {
         super(TeamRules.VAULT);
         this.widow = widow;
         this.clint = clint;
         this.spot = spot;
      }

      @Override
      void begin() {
         this.claim(this.widow);
         this.claim(this.clint);
         this.widow.play(AvengerAction.TEAM_1); // crouch, cupped hands
         face(this.widow, this.clint.position());
         CaptainSounds.play(this.widow, CaptainSounds.ACROBAT, 0.7F, 1.2F);
         this.clint.getNavigation().moveTo(this.spot.x, this.spot.y, this.spot.z, 1.7);
      }

      @Override
      boolean step() {
         if (!fit(this.clint) || this.stage == RUN && !fit(this.widow)) {
            return this.cancel("partner gone");
         }
         if (!this.widowFree && this.stage != RUN && this.stageT >= 0 && fit(this.widow) && this.t >= this.launchT + TeamRules.VAULT_HEAVE_TICKS) {
            this.widowFree = true;
            this.release(this.widow);
         }
         switch (this.stage) {
            case RUN -> {
               this.widow.getNavigation().stop();
               face(this.widow, this.clint.position());
               double d = Math.sqrt(this.clint.position().subtract(this.spot).horizontalDistanceSqr());
               if (d <= TeamRules.VAULT_CONTACT) {
                  this.launch();
               } else if (this.stageT > TeamRules.VAULT_RUN_TICKS) {
                  return this.cancel("run-up blocked");
               } else if (this.stageT % 4 == 0 || this.clint.getNavigation().isDone()) {
                  this.clint.getNavigation().moveTo(this.spot.x, this.spot.y, this.spot.z, 1.7);
               }
            }
            case RISE -> {
               Vec3 v = this.clint.getDeltaMovement();
               this.clint.setDeltaMovement(0, v.y, 0);
               this.lookDown();
               if (v.y <= 0.08 || this.clint.verticalCollision || this.stageT >= TeamRules.VAULT_RISE_MAX) {
                  this.next(HANG);
                  this.clint.setNoGravity(true);
               }
            }
            case HANG -> {
               this.clint.setNoGravity(true);
               this.clint.setDeltaMovement(0, -TeamRules.VAULT_HANG_SINK, 0);
               this.clint.hasImpulse = true;
               LivingEntity aim = this.lookDown();
               if (this.shot < TeamRules.VAULT_SHOTS.length) {
                  int release = TeamRules.VAULT_SHOTS[this.shot];
                  if (this.stageT == release - TeamRules.VAULT_AIR_RELEASE) {
                     this.clint.play(AvengerAction.TEAM_2); // draw in the air, loose at TeamRules.VAULT_AIR_RELEASE
                     CaptainSounds.play(this.clint, CaptainSounds.BOW_DRAW, 0.7F, 1.1F);
                  }
                  if (this.stageT == release) {
                     this.fire(aim);
                     this.shot++;
                  }
               }
               if (this.stageT >= TeamRules.VAULT_HANG) {
                  this.next(FALL);
                  this.clint.setNoGravity(false);
                  this.clint.play(AvengerAction.TEAM_3);
               }
            }
            case FALL -> {
               Vec3 v = this.clint.getDeltaMovement();
               this.clint.setDeltaMovement(0, v.y, 0); // straight down beside Natasha, no drift into danger
               if (this.clint.onGround() || this.clint.isInWater()) {
                  this.clint.play(AvengerAction.LAND);
                  CaptainFx.around(this.clint, CaptainFx.GROUND, 2, this.clint.position(), new Vec3(1.6, 0, 0));
                  CaptainSounds.play(this.clint, CaptainSounds.HEAVY_LAND, 0.6F, 1.25F);
                  this.done = true;
                  return false;
               }
               if (this.stageT > TeamRules.VAULT_FALL_MAX) {
                  this.done = true;
                  return false;
               }
            }
            default -> {
            }
         }
         return true;
      }

      private int launchT = Integer.MAX_VALUE;

      private void launch() {
         this.launchT = this.t;
         this.next(RISE);
         this.clint.getNavigation().stop();
         Vec3 at = this.spot;
         this.clint.teleportTo(at.x, Math.max(at.y, this.clint.getY()), at.z);
         this.clint.setDeltaMovement(0, TeamRules.VAULT_LAUNCH, 0);
         this.clint.hasImpulse = true;
         this.clint.play(AvengerAction.TEAM_1); // springs off the hands, flips up
         this.widow.play(AvengerAction.TEAM_2); // the heave
         face(this.widow, at);
         Vec3 hands = this.widow.position().add(this.widow.getLookAngle().multiply(1, 0, 1).scale(0.5)).add(0, 0.7, 0);
         CaptainFx.around(this.widow, CaptainFx.TEAM, CaptainFx.TEAM_VAULT, hands, new Vec3(0, 1, 0));
         CaptainSounds.play(this.widow, CaptainSounds.ACROBAT, 1.0F, 0.9F);
         CaptainSounds.play(this.clint, CaptainSounds.SLAM_LEAP, 0.6F, 1.3F);
      }

      /** Faces the best target below and returns it (or null). */
      @Nullable
      private LivingEntity lookDown() {
         LivingEntity best = null;
         double bestD = Double.MAX_VALUE;
         for (LivingEntity e : this.clint.brain.enemiesAround(this.widow.position(), TeamRules.VAULT_SHOT_RANGE)) {
            if (!this.clint.brain.hostile(e) || !CaptainTargets.visible(this.clint.level(), this.clint.getEyePosition(), e.getBoundingBox().getCenter())) {
               continue;
            }
            // spread the volley: enemies not shot at yet first, then the nearest
            double d = e.distanceToSqr(this.clint) + (this.shotAt.contains(e.getId()) ? 400 : 0);
            if (d < bestD) {
               bestD = d;
               best = e;
            }
         }
         if (best != null) {
            face(this.clint, best.position());
            this.clint.getLookControl().setLookAt(best, 90.0F, 90.0F);
         }
         return best;
      }

      private void fire(@Nullable LivingEntity aim) {
         if (aim == null || !(this.clint.brain instanceof HawkeyeBrain bow)) {
            return;
         }
         this.shotAt.add(aim.getId());
         boolean crowd = this.shot == 0 && this.clint.brain.enemiesAround(aim.position(), 3.5).size() >= 3;
         bow.looseAt(aim, crowd ? AvengerProjectileEntity.ARROW_EXPLOSIVE : AvengerProjectileEntity.ARROW,
            crowd ? TeamRules.VAULT_EXPLOSIVE_DAMAGE : TeamRules.VAULT_ARROW_DAMAGE);
      }

      @Override
      void cleanup() {
         this.clint.setNoGravity(false);
         this.clint.noPhysics = false;
      }
   }

   // =========================================================================== 15.2 the charged unibeam

   final class Beam extends Move {
      private static final int POSITION = 0, CHARGE = 1, FIRE = 2;
      final AvengerEntity thor;
      final AvengerEntity tony;
      final IronManBrain im;
      LivingEntity target;
      Vec3 beamDir = Vec3.ZERO;
      private final Set<Integer> burned = new HashSet<>();

      Beam(AvengerEntity thor, AvengerEntity tony, IronManBrain im, LivingEntity target) {
         super(TeamRules.BEAM);
         this.thor = thor;
         this.tony = tony;
         this.im = im;
         this.target = target;
      }

      @Override
      void begin() {
         this.claim(this.thor);
         this.claim(this.tony);
         this.tony.setFlying(true);
         CaptainSounds.play(this.thor, CaptainSounds.HAMMER_SPIN, 1.0F, 1.1F);
      }

      /** Above Thor, out toward the target: Thor's arc goes up into Tony, Tony's beam goes on at the target. */
      private Vec3 spot() {
         Vec3 d = this.target.position().subtract(this.thor.position());
         Vec3 h = d.horizontalDistanceSqr() < 1.0E-4 ? this.thor.getLookAngle().multiply(1, 0, 1).normalize() : new Vec3(d.x, 0, d.z).normalize();
         return this.thor.position().add(h.scale(TeamRules.BEAM_SPOT_OUT)).add(0, TeamRules.BEAM_SPOT_UP, 0);
      }

      private Vec3 hammerUp() {
         return this.thor.position().add(0, this.thor.getBbHeight() + 0.75, 0);
      }

      private Vec3 hammerAt() {
         Vec3 d = this.im.chest().subtract(this.thor.getEyePosition());
         return this.thor.getEyePosition().add(d.normalize().scale(1.0)).add(0, 0.25, 0);
      }

      @Override
      boolean step() {
         if (!fit(this.tony) || this.stage != FIRE && !fit(this.thor)) {
            return this.cancel("partner gone");
         }
         if (!this.target.isAlive() || !this.tony.mayHit(this.target)) {
            LivingEntity next = this.tony.brain.nearestHostile(this.tony.position(), TeamRules.BEAM_TARGET_MAX);
            if (next == null || !this.tony.brain.sees(next)) {
               return this.cancel("no target");
            }
            this.target = next;
         }
         switch (this.stage) {
            case POSITION -> {
               this.thor.getNavigation().stop();
               face(this.thor, this.tony.position());
               Vec3 spot = this.spot();
               this.im.fly(spot, 0.8);
               face(this.tony, this.target.position());
               if (this.tony.position().distanceTo(spot) < 1.6 || this.stageT >= TeamRules.BEAM_POSITION_TICKS) {
                  this.next(CHARGE);
                  this.thor.play(AvengerAction.TEAM_1);
                  this.tony.play(AvengerAction.TEAM_1);
               }
            }
            case CHARGE -> {
               this.thor.getNavigation().stop();
               this.tony.setDeltaMovement(this.tony.getDeltaMovement().scale(0.5));
               face(this.thor, this.tony.position());
               face(this.tony, this.target.position());
               ServerLevel level = level();
               if (this.stageT == TeamRules.BEAM_SKY_BOLT) {
                  HeroFx.electricBolt(level, this.hammerUp(), 16.0F, 0.6F, CaptainSounds.THUNDER.get());
                  HeroFx.electricCrackle(level, this.hammerUp(), 8);
               }
               if (this.stageT >= TeamRules.BEAM_ARC_START && this.stageT % 2 == 0) {
                  Vec3 from = this.hammerAt();
                  Vec3 chest = this.im.chest();
                  if (!CaptainTargets.visible(level, from, chest)) {
                     return this.cancel("arc blocked");
                  }
                  CaptainFx.near(level, chest, 64, CaptainFx.TEAM, this.thor.getId(), CaptainFx.TEAM_CHANNEL, from, chest, this.tony.getId());
                  if (this.stageT % 6 == 0) {
                     CaptainSounds.playAt(this.tony, chest, CaptainSounds.SHOCK, 0.9F, CaptainSounds.jitter(this.tony, 1.2F));
                  }
               }
               if (this.stageT == TeamRules.BEAM_ARC_START + 6) {
                  HeroSpeech.play(this.owner(), CaptainLines.TEAM_BEAM, HeroId.IRON_MAN, false);
               }
               if (this.stageT >= TeamRules.BEAM_CHARGE_TICKS) {
                  this.next(FIRE);
                  this.release(this.thor);
                  this.tony.play(AvengerAction.TEAM_2);
                  this.beamDir = this.target.getBoundingBox().getCenter().subtract(this.im.chest()).normalize();
                  CaptainSounds.play(this.tony, CaptainSounds.UNIBEAM, 1.6F, 0.85F);
                  CaptainSounds.play(this.tony, CaptainSounds.THUNDER, 0.9F, 1.4F);
               }
            }
            case FIRE -> {
               this.tony.setDeltaMovement(Vec3.ZERO);
               Vec3 want = this.target.getBoundingBox().getCenter().subtract(this.im.chest()).normalize();
               this.beamDir = this.beamDir.lerp(want, 0.12).normalize();
               face(this.tony, this.target.position());
               if (this.stageT % 2 == 0) {
                  Vec3 from = this.im.chest();
                  Vec3 end = this.im.rayEnd(from, from.add(this.beamDir.scale(TeamRules.BEAM_RANGE)));
                  boolean last = this.stageT >= TeamRules.BEAM_FIRE_TICKS - 2;
                  for (LivingEntity e : this.im.alongRay(from, end, TeamRules.BEAM_WIDTH)) {
                     if (this.im.hit(e, TeamRules.BEAM_PULSE_DAMAGE)) {
                        CaptainTargets.push(e, this.beamDir.scale(0.35));
                        this.burned.add(e.getId());
                     }
                  }
                  CaptainFx.near(level(), from, 96, CaptainFx.TEAM, this.tony.getId(), CaptainFx.TEAM_BEAM, from, end);
                  if (last) {
                     for (int id : this.burned) {
                        if (this.tony.level().getEntity(id) instanceof LivingEntity e && e.isAlive()) {
                           CaptainTargets.stun(this.tony, e, TeamRules.BEAM_STUN);
                        }
                     }
                  }
               }
               if (this.stageT >= TeamRules.BEAM_FIRE_TICKS) {
                  this.done = true;
                  return false;
               }
            }
            default -> {
            }
         }
         return true;
      }

      private ServerPlayer owner() {
         return TeamMoves.this.owner;
      }

      @Override
      void cleanup() {
         this.im.unibeamUsed();
         this.tony.setFlying(true);
      }
   }

   // =========================================================================== 15.3 the shield strike

   final class ShieldStrike extends Move {
      private static final int APPROACH = 0, READY = 1, STRIKE = 2;
      final AvengerEntity thor;
      private Vec3 spot = Vec3.ZERO;
      private boolean flying;

      ShieldStrike(AvengerEntity thor) {
         super(TeamRules.SHIELD);
         this.thor = thor;
      }

      @Override
      void begin() {
         this.claim(this.thor);
         this.spot = this.place();
         HeroSpeech.play(TeamMoves.this.owner, CaptainLines.TEAM_SHIELD, HeroId.THOR, false);
         this.hint();
         CaptainSounds.play(this.thor, CaptainSounds.THUNDER, 0.6F, 1.5F);
      }

      private void hint() {
         TeamMoves.this.owner.displayClientMessage(Component.translatable("hint.viltrumitecore.captain.team_shield", Component.keybind("key.use")), true);
      }

      /** Thor's place: in front of Steve on Thor's side, so Steve only has to turn toward him. */
      private Vec3 place() {
         ServerPlayer steve = TeamMoves.this.owner;
         Vec3 d = this.thor.position().subtract(steve.position());
         Vec3 h = d.horizontalDistanceSqr() < 1.0E-4 ? steve.getLookAngle().multiply(1, 0, 1).normalize() : new Vec3(d.x, 0, d.z).normalize();
         return steve.position().add(h.scale(TeamRules.SHIELD_SPOT));
      }

      private Vec3 hammer() {
         return this.thor.position().add(0, this.thor.getBbHeight() + 0.7, 0);
      }

      @Override
      boolean step() {
         ServerPlayer steve = TeamMoves.this.owner;
         CaptainState s = CaptainState.of(steve);
         if (s == null || !steve.isAlive() || steve.level() != this.thor.level()) {
            return this.cancel("steve gone");
         }
         if (!fit(this.thor)) {
            return this.cancel("thor gone");
         }
         double toSteve = this.thor.distanceTo(steve);
         switch (this.stage) {
            case APPROACH -> {
               if (this.stageT % 10 == 1) {
                  this.spot = this.place();
               }
               double d = Math.sqrt(this.thor.position().subtract(this.spot).horizontalDistanceSqr());
               boolean close = toSteve <= TeamRules.SHIELD_MAX_DISTANCE - 0.6
                  && CaptainTargets.visible(this.thor.level(), this.thor.getEyePosition(), steve.getEyePosition());
               if (d <= 1.2 || close) {
                  this.ready();
               } else if (this.stageT > TeamRules.SHIELD_APPROACH_TICKS) {
                  return this.cancel("approach blocked");
               } else if (d > 9.0 || this.stageT > 20 && this.thor.getNavigation().isDone()) {
                  // too far or no path: Mjolnir pulls him over
                  this.flying = true;
                  this.thor.setFlying(true);
                  Vec3 to = this.spot.add(0, 0.8, 0).subtract(this.thor.position());
                  double len = to.length();
                  Vec3 want = len < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(1.0, len * 0.3) / len);
                  this.thor.setDeltaMovement(this.thor.getDeltaMovement().scale(0.4).add(want.scale(0.6)));
                  this.thor.hasImpulse = true;
                  face(this.thor, this.spot);
               } else if (this.stageT % 4 == 1 || this.thor.getNavigation().isDone()) {
                  this.thor.getNavigation().moveTo(this.spot.x, this.spot.y, this.spot.z, 1.4);
               }
            }
            case READY -> {
               this.thor.getNavigation().stop();
               face(this.thor, steve.position());
               if (this.stageT % 5 == 0) {
                  CaptainFx.near(level(), this.hammer(), 64, CaptainFx.TEAM, this.thor.getId(), CaptainFx.TEAM_CRACKLE, this.hammer(), Vec3.ZERO);
               }
               if (this.stageT % 12 == 0) {
                  CaptainSounds.play(this.thor, CaptainSounds.SHOCK, 0.5F, CaptainSounds.jitter(this.thor, 0.8F));
               }
               if (this.stageT % 30 == 0) {
                  this.hint();
               }
               if (toSteve > TeamRules.SHIELD_LEAVE_DISTANCE) {
                  return this.cancel("steve left");
               }
               if (TeamRules.blockCounts(s.guarding, s.shieldInHand(), CaptainCombat.facing(steve, this.thor.position()), s.guardTicks, this.t, toSteve)) {
                  this.next(STRIKE);
                  this.thor.play(AvengerAction.TEAM_3);
                  CaptainSounds.play(this.thor, CaptainSounds.HAMMER_SWING, 1.3F, 0.7F);
               } else if (this.stageT > TeamRules.SHIELD_WINDOW) {
                  return this.cancel("no block");
               }
            }
            case STRIKE -> {
               this.thor.getNavigation().stop();
               face(this.thor, steve.position());
               if (this.stageT == 3) {
                  Vec3 d = steve.position().subtract(this.thor.position());
                  Vec3 h = d.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(d.x, 0, d.z).normalize();
                  double step = Math.max(0.0, Math.min(1.2, toSteve - 2.2));
                  this.thor.setDeltaMovement(h.scale(step * 0.45).add(0, 0.25, 0)); // the hop into the blow
                  this.thor.hasImpulse = true;
               }
               if (this.stageT == TeamRules.SHIELD_IMPACT) {
                  this.impact(steve, s);
               }
               if (this.stageT >= TeamRules.SHIELD_STRIKE_TICKS) {
                  this.done = true;
                  return false;
               }
            }
            default -> {
            }
         }
         return true;
      }

      private void ready() {
         this.next(READY);
         if (this.flying) {
            this.thor.setFlying(false);
            this.flying = false;
         }
         this.thor.getNavigation().stop();
         this.thor.play(AvengerAction.TEAM_2); // Mjolnir raised, gathering lightning
         HeroFx.electricBolt(level(), this.hammer(), 14.0F, 0.5F, CaptainSounds.THUNDER.get());
      }

      private void impact(ServerPlayer steve, CaptainState s) {
         ServerLevel level = level();
         Vec3 look = steve.getLookAngle().multiply(1, 0, 1);
         look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
         Vec3 shield = steve.getEyePosition().add(look.scale(0.7)).add(0, -0.35, 0);
         CaptainCombat.teamBrace(steve, s, TeamRules.SHOCK_KINETIC);
         List<LivingEntity> hit = new ArrayList<>(this.thor.brain.enemiesAround(steve.position(), TeamRules.SHOCK_RADIUS));
         hit.sort(Comparator.comparingDouble(e -> e.distanceToSqr(steve)));
         int n = 0;
         for (LivingEntity e : hit) {
            if (n >= TeamRules.SHOCK_MAX_TARGETS) {
               break;
            }
            double d = Math.sqrt(e.distanceToSqr(steve));
            float dmg = TeamRules.shockDamage(d);
            if (dmg <= 0 || !CaptainTargets.visible(level, shield, e.getBoundingBox().getCenter())) {
               continue;
            }
            n++;
            if (this.thor.brain.hit(e, dmg)) {
               Vec3 away = e.position().subtract(steve.position());
               Vec3 h = away.horizontalDistanceSqr() < 1.0E-4 ? look : new Vec3(away.x, 0, away.z).normalize();
               CaptainTargets.push(e, h.scale(1.3 * (1.0 - 0.5 * d / TeamRules.SHOCK_RADIUS)).add(0, 0.45, 0));
               CaptainTargets.stun(this.thor, e, TeamRules.SHOCK_STUN);
               CaptainFx.around(this.thor, CaptainFx.STUN, TeamRules.SHOCK_STUN, e.getBoundingBox().getCenter(), Vec3.ZERO, e.getId());
            }
         }
         CaptainFx.near(level, shield, 96, CaptainFx.TEAM, steve.getId(), CaptainFx.TEAM_SHOCKWAVE, shield, new Vec3(TeamRules.SHOCK_RADIUS, steve.getY(), 0));
         HeroFx.electricRing(level, steve.position().add(0, 0.1, 0), TeamRules.SHOCK_RADIUS * 0.8);
         HeroFx.flash(level, shield);
         CaptainSounds.playAt(steve, shield, CaptainSounds.THUNDER, 1.8F, 0.9F);
         CaptainSounds.playAt(steve, shield, CaptainSounds.PARRY, 1.4F, 0.7F);
         CaptainSounds.playAt(steve, shield, CaptainSounds.SLAM_EMPOWERED, 1.5F, 1.0F);
         CaptainSounds.playAt(steve, shield, CaptainSounds.HAMMER_HIT, 1.5F, 0.8F);
         if (QA) {
            LOGGER.info("TEAMMOVE shield impact: {} enemies hit", n);
         }
      }

      @Override
      void cleanup() {
         if (this.flying) {
            this.thor.setFlying(false);
         }
      }
   }
}
