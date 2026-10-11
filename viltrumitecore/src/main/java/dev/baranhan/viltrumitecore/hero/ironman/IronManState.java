package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.ironman.combat.*;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Server-side per-player Iron Man state. Suit and energy survive relog (spec §16). */
public final class IronManState {
   private static final String KEY = "IronMan";
   private static final String FLARE_COOLDOWN_KEY = "FlareCooldown";
   private static final String VERONICA_COOLDOWN_KEY = "VeronicaCooldown";
   private static final String VERONICA_POD_KEY = "VeronicaPod";
   private static final String SIGNATURE_COOLDOWN_KEY = "SignatureCooldown";
   public final Suit suit = new Suit();
   public final Energy energy = new Energy();
   /** Energy hit 0 in flight: glide profile (Task 9). */
   public boolean glide;
   /** Transient: touchdown classification and air strike arming (Task 9). */
   public final LandingTracker landing = new LandingTracker();
   public final AirStrike airStrike = new AirStrike();
   /** Transient: kneel-pose ticks left (HEAVY_LANDING flag). */
   public int heavyPoseTicks;
   /** Transient: game time of the last flight touchdown effect (no double effect with onLanded). */
   public long landedAt = Long.MIN_VALUE;
   /** Transient: sonic-ram rehit timestamps per target (bounded). */
   public final java.util.Map<java.util.UUID, Long> ramHits = new dev.baranhan.viltrumitecore.hero.control.BoundedMap<>(64);
   /** Transient: ticks until the next fly-by punch may land. */
   public int flyByCooldown;

   // ---- Stage 2: nano combat (plan Tasks 1-10) ----
   /** Persisted (NBT CoreOverheats): only a core explosion or death resets it. */
   public final CoreOverheat overheat = new CoreOverheat();
   /** Transient: current RMB tool (server authority; synced in RMB_TOOL bits). */
   public RightTool rightTool = RightTool.REPULSOR;
   /** Transient: the tool the held RMB started with (release goes there). */
   @Nullable
   public RightTool heldTool;
   public final Repulsor repulsor = new Repulsor();
   public int recoilTicks;
   public boolean recoilRight;
   public final UnibeamTimeline unibeam = new UnibeamTimeline();
   public final Overdraft overdraft = new Overdraft();
   /** Beam direction (slow turn) and the synced beam end point. */
   @Nullable
   public net.minecraft.world.phys.Vec3 beamDir;
   @Nullable
   public net.minecraft.world.phys.Vec3 beamEnd;
   /** Own core explosion window: damage from it is floored (spec §9.4). */
   public int coreExplosionWindow;
   public final MissileLock missiles = new MissileLock();
   public final NanoArsenal arsenal = new NanoArsenal();
   /** LMB weapon strike pose timeline. */
   public int strikeTicks;
   public int strikeLength;
   /** Hammer RMB charge ticks; -1 = not charging. */
   public int hammerCharge = -1;
   public final HammerLaunch hammerLaunch = new HammerLaunch();
   public final Shield shield = new Shield();
   /** Projectiles to redirect next tick after a perfect block (entity id → speed). */
   public final java.util.Map<Integer, Double> reflect = new java.util.HashMap<>();
   /** Nano damage zones (bit 0 mask, 1 shoulder, 2 chest) and repair ticks left. */
   public int damagedZones;
   public int repairTicks;

   // ---- Stage 3: helmet, JARVIS, scan, countermeasures ----
   /** Persisted (NBT HelmetOpen). */
   public final Helmet helmet = new Helmet();
   /** Transient: JARVIS threat ids (nearest first), mirrored in the THREATS owner section. */
   public final java.util.List<Integer> threats = new java.util.ArrayList<>();
   public final dev.baranhan.viltrumitecore.hero.ironman.scan.ScanProgress scan = new dev.baranhan.viltrumitecore.hero.ironman.scan.ScanProgress();
   /** Cooldown carries over death (extraCooldowns[1]). */
   public final Countermeasures countermeasures = new Countermeasures();

   // ---- Stage 4: Veronica and marks ----
   /** Persisted (NBT MarkRoster): durability, cooldown and location of every mark. */
   public final dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster roster = new dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster();
   /** The worn mark's signature; only the cooldown is persisted (SignatureCooldown). */
   public final dev.baranhan.viltrumitecore.hero.ironman.mark.SignatureState signature = new dev.baranhan.viltrumitecore.hero.ironman.mark.SignatureState();
   /** Persisted (VeronicaCooldown): starts when a pod is called (the pod itself stays). */
   public int veronicaCooldown;
   /** Persisted (VeronicaPod): UUID of this player's pod, null = none. The pod stays in the world until a newer call or a hero change. */
   @Nullable
   public java.util.UUID podUuid;
   /** Transient: entity id of this player's empty suit, -1 = none (one per owner, spec §12.6). */
   public int emptySuitId = -1;
   /** Transient: entity id of the empty suit this player is walking into, -1 = none (rooted, no actions). */
   public int enteringSuitId = -1;
   /** Transient: facing of the suit Tony is walking out of (exit), null otherwise. */
   @Nullable
   public net.minecraft.world.phys.Vec3 exitDir;
   /** Transient: where the flying parts start (pod, old empty suit, sky); synced as actionTarget while equipping. */
   @Nullable
   public net.minecraft.world.phys.Vec3 equipSource;

   // ---- Stage 5: Hulkbuster Mark 48 ----
   /** Persisted (NBT Hulkbuster): phase, durability, cooldown, partial parts. */
   public final dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer hulkbuster = new dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer();
   /** Transient kit state (punch cadence, jackhammer, charge, grab, slam, hop). */
   public final dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterKit hulkKit = new dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterKit();

   /** Missile marks need the JARVIS targeting: helmet closed (spec §10). */
   public boolean canMarkTargets() {
      return this.helmet.closed();
   }

   /** Flight grant: only while fully worn; the Hulkbuster has no real flight (spec §14.3). */
   public boolean wantsFlight() {
      return this.suit.worn() && !this.hulkbuster.present();
   }

   /** Pure part of HeroDefinition.cleanup (spec §16). */
   public void onCleanup(CleanupReason reason) {
      this.landing.reset();
      this.airStrike.consume();
      this.heavyPoseTicks = 0;
      this.ramHits.clear();
      this.stopCombat();
      this.scan.cancel();
      this.threats.clear();
      this.countermeasures.clearForget();
      this.signature.clear();
      this.emptySuitId = -1;
      this.enteringSuitId = -1;
      this.equipSource = null;
      this.hulkKit.stopChannels();
      switch (reason) {
         case DEATH -> {
            this.hulkbuster.breakNow();
            this.suit.clear();
            this.glide = false;
            // Spec §9.1/§16: death cancels a pending core explosion and resets the counter.
            this.overdraft.cancel();
            this.overheat.resetByDeath();
         }
         case DISCONNECT -> {
            this.suit.resolve();
            this.overdraft.cancel();
         }
         case HERO_CHANGE -> {
            this.suit.clear();
            this.energy.reset();
            this.glide = false;
            this.overdraft.cancel();
            this.overheat.resetByDeath();
            this.suit.setNanoLock(0);
            this.countermeasures.setCooldown(0);
            this.helmet.reset();
            this.roster.reset();
            this.veronicaCooldown = 0;
            // The pod sees the hero change and flies away by itself.
            this.podUuid = null;
            this.signature.cooldown = 0;
            this.hulkbuster.reset();
            this.hulkKit.reset();
         }
      }

      // Spec §16: the empty suit flies away, a delivery goes back; durability is kept. Veronica stays (user decision 2026-10-10).
      this.roster.recallWorld();
      this.reconcileMark();
   }

   /**
    * Every combat channel off at once, without shots (cleanup, suit lost).
    * The overdraft is not touched: it ends only by explosion, death or hero change.
    */
   public void stopCombat() {
      this.repulsor.cancel();
      this.heldTool = null;
      this.recoilTicks = 0;
      if (!this.overdraft.active()) {
         this.unibeam.clear();
      }

      this.beamDir = null;
      this.beamEnd = null;
      this.missiles.cancel();
      this.arsenal.clear();
      this.rightTool = RightTool.REPULSOR;
      this.strikeTicks = 0;
      this.hammerCharge = -1;
      this.hammerLaunch.clear();
      this.shield.lower();
      this.reflect.clear();
      this.damagedZones = 0;
      this.repairTicks = 0;
   }

   /** Death: suit off, energy full; cooldowns and mark durability carry over, the overheat counter resets. */
   public static IronManState cloneForRespawn(IronManState original) {
      IronManState state = new IronManState();
      state.suit.setNanoLock(original.suit.nanoLockTicks());
      state.countermeasures.setCooldown(original.countermeasures.cooldown());
      state.roster.copyFrom(original.roster);
      state.roster.recallWorld();
      state.reconcileMark();
      state.veronicaCooldown = original.veronicaCooldown;
      // The pod is permanent (user decision 2026-10-10): death must not retire it.
      state.podUuid = original.podUuid;
      state.signature.cooldown = original.signature.cooldown;
      state.hulkbuster.copyCooldownFrom(original.hulkbuster);
      return state;
   }

   /** End exit portal (clone without death): the suit stays on (spec §16), only live channels drop. */
   public static IronManState cloneForPortal(IronManState original) {
      CompoundTag nbt = new CompoundTag();
      original.save(nbt);
      IronManState state = new IronManState();
      state.load(nbt);
      return state;
   }

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      this.suit.save(tag);
      this.energy.save(tag);
      this.overheat.save(tag);
      this.helmet.save(tag);
      tag.putInt(FLARE_COOLDOWN_KEY, this.countermeasures.cooldown());
      this.roster.save(tag);
      tag.putInt(VERONICA_COOLDOWN_KEY, this.veronicaCooldown);
      if (this.podUuid != null) {
         tag.putUUID(VERONICA_POD_KEY, this.podUuid);
      }
      tag.putInt(SIGNATURE_COOLDOWN_KEY, this.signature.cooldown);
      this.hulkbuster.save(tag);
      nbt.put(KEY, tag);
   }

   public void load(CompoundTag nbt) {
      CompoundTag tag = nbt.getCompound(KEY);
      this.suit.load(tag);
      this.energy.load(tag);
      this.overheat.load(tag);
      this.helmet.load(tag);
      this.countermeasures.setCooldown(tag.getInt(FLARE_COOLDOWN_KEY));
      this.roster.load(tag);
      this.veronicaCooldown = Math.max(0, tag.getInt(VERONICA_COOLDOWN_KEY));
      this.podUuid = tag.hasUUID(VERONICA_POD_KEY) ? tag.getUUID(VERONICA_POD_KEY) : null;
      this.signature.clear();
      this.signature.cooldown = Math.max(0, tag.getInt(SIGNATURE_COOLDOWN_KEY));
      this.reconcileMark();
      this.hulkbuster.load(tag);
      this.glide = false;
   }

   /** The roster agrees with the suit: only the mark on the body is WORN. */
   public void reconcileMark() {
      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId onBody = this.suit.markOn() ? this.suit.mark() : null;
      for (dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId id : dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId.values()) {
         dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation location = this.roster.location(id);
         if (id == onBody && location != dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation.WORN) {
            this.roster.move(id, location, dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation.WORN);
         } else if (id != onBody && location == dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation.WORN) {
            this.roster.move(id, location, dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation.STORED);
         }
      }
   }

   /** Suit numbers for every system: nano defaults or the worn mark (spec §13). */
   public dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec spec() {
      dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = this.suit.markOn() ? this.suit.mark() : null;
      if (mark == null) {
         return dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec.NANO;
      }

      int lost = mark == dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId.MARK_42
         ? dev.baranhan.viltrumitecore.hero.ironman.mark.Mark42Parts.lostMask(this.roster.durability(mark), this.roster.maxDurability(mark)) : 0;
      return dev.baranhan.viltrumitecore.hero.ironman.mark.SuitSpec.mark(mark, lost);
   }

   @Nullable
   public static IronManState of(@Nullable Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.IRON_MAN) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof IronManState state ? state : null;
      }

      return null;
   }

   public static IronManState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof IronManState existing) {
         return existing;
      }

      IronManState state = new IronManState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
