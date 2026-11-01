package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriChariot;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriEntities;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import dev.baranhan.viltrumitecore.entity.thanos.BlackOrderEntity;
import dev.baranhan.viltrumitecore.entity.thanos.CullObsidianEntity;
import dev.baranhan.viltrumitecore.entity.thanos.EbonyMawEntity;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriInvasion;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarOrder;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosEvent;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Thanos's forces (THANOS 2.0). The Black Order beams down from the Sanctuary II when he calls them
 * (phase II): Ebony Maw and Cull Obsidian, health scaled for the heroes, each with a boss bar, kept
 * near him. From the Invasion on the Chitauri come through landing portals in waves (the existing
 * soldiers and chariots, outside any Chitauri invasion): capped by the heroes counted
 * ({@link ThanosWarRules#armyCap}) and in total ({@link ThanosWarRules#ARMY_TOTAL}), every unit
 * tracked so the end removes all of them. Victory: the Order is beamed out, the hive link breaks.
 */
public final class ThanosArmy {
   private static final class Portal {
      final int id;
      final Vec3 at;
      final boolean sky;
      int life;
      int queue;
      int gap;

      Portal(int id, Vec3 at, boolean sky, int queue) {
         this.id = id;
         this.at = at;
         this.sky = sky;
         this.life = ThanosWarRules.PORTAL_LIFE;
         this.queue = queue;
         this.gap = 30;
      }
   }

   /** Height of an infantry portal over the street (a short drop, cushioned). */
   static final double DROP_HEIGHT = 5.5;
   private final ThanosWar war;
   @Nullable
   private EbonyMawEntity maw;
   @Nullable
   private CullObsidianEntity cull;
   private final ServerBossEvent mawBar = new ServerBossEvent(Component.translatable("entity.viltrumitecore.ebony_maw"), BossEvent.BossBarColor.BLUE,
      BossEvent.BossBarOverlay.NOTCHED_10);
   private final ServerBossEvent cullBar = new ServerBossEvent(Component.translatable("entity.viltrumitecore.cull_obsidian"), BossEvent.BossBarColor.RED,
      BossEvent.BossBarOverlay.NOTCHED_10);
   private boolean called;
   private int beamIn = -1;
   private Vec3 mawAt = Vec3.ZERO;
   private Vec3 cullAt = Vec3.ZERO;
   private boolean mawDown;
   private boolean cullDown;
   private boolean front;
   private boolean retreating;
   private final Set<UUID> troops = new HashSet<>();
   private final Set<UUID> chariots = new HashSet<>();
   private final List<Portal> portals = new ArrayList<>();
   private int spawned;
   private int waves;
   private int waveTimer;
   private int portalIds;
   private int guard;
   private int alive;

   ThanosArmy(ThanosWar war) {
      this.war = war;
      this.mawBar.setDarkenScreen(false);
      this.cullBar.setDarkenScreen(false);
   }

   public int spawned() {
      return this.spawned;
   }

   public int waves() {
      return this.waves;
   }

   public boolean front() {
      return this.front && !this.retreating;
   }

   /** Chitauri alive (counted on the last prune). */
   public int alive() {
      return this.alive;
   }

   public int portals() {
      return this.portals.size();
   }

   @Nullable
   public EbonyMawEntity maw() {
      return this.maw != null && this.maw.isAlive() ? this.maw : null;
   }

   @Nullable
   public CullObsidianEntity cull() {
      return this.cull != null && this.cull.isAlive() ? this.cull : null;
   }

   public boolean orderPresent() {
      return this.maw() != null || this.cull() != null || this.beamIn > 0;
   }

   public boolean called() {
      return this.called;
   }

   // ---- the Black Order -----------------------------------------------------------------------------
   void onPhase(ThanosPhase phase) {
   }

   /** The Order is called: two beams come down beside Thanos, they stand there a second later. */
   void callOrder() {
      if (this.called || this.retreating) {
         return;
      }

      this.called = true;
      ServerLevel level = this.war.level;
      Vec3 c = this.war.thanos.position();
      Vec3 f = this.war.combat.facing();
      Vec3 side = new Vec3(-f.z, 0.0, f.x);
      this.mawAt = WarGround.below(level, c.add(side.scale(7.0)).add(f.scale(-2.0)).add(0, 6, 0), 16);
      this.cullAt = WarGround.below(level, c.add(side.scale(-7.0)).add(f.scale(-2.0)).add(0, 6, 0), 16);
      this.war.ship.beam(this.mawAt, ThanosWarRules.BEAM_TICKS);
      this.war.ship.beam(this.cullAt, ThanosWarRules.BEAM_TICKS);
      this.beamIn = ThanosWarRules.BEAM_TICKS;
   }

   private void arrive() {
      ServerLevel level = this.war.level;
      int heroes = this.war.heroCount();
      if (this.maw == null && !this.mawDown) {
         EbonyMawEntity m = ViltrumiteEntities.EBONY_MAW.get().create(level);
         if (m != null) {
            m.bind(this.war);
            m.moveTo(this.mawAt.x, this.mawAt.y + EbonyMawEntity_HOVER, this.mawAt.z, this.war.thanos.getYRot(), 0.0F);
            m.scaleMaxHealth(ThanosWarRules.orderHealth(ThanosWarRules.MAW_HEALTH, heroes));
            m.setHealth(m.getMaxHealth());
            if (level.addFreshEntity(m)) {
               this.maw = m;
            }
         }
      }

      if (this.cull == null && !this.cullDown) {
         CullObsidianEntity c = ViltrumiteEntities.CULL_OBSIDIAN.get().create(level);
         if (c != null) {
            c.bind(this.war);
            c.moveTo(this.cullAt.x, this.cullAt.y, this.cullAt.z, this.war.thanos.getYRot(), 0.0F);
            c.scaleMaxHealth(ThanosWarRules.orderHealth(ThanosWarRules.CULL_HEALTH, heroes));
            c.setHealth(c.getMaxHealth());
            if (level.addFreshEntity(c)) {
               this.cull = c;
            }
         }
      }

      if (this.war.phase() == ThanosPhase.GAUNTLET) {
         this.enrage();
      }
   }

   static final double EbonyMawEntity_HOVER = 3.0;

   /** Heroes joined: the Order's health follows (as Thanos's does). */
   void rescale() {
      int heroes = this.war.heroCount();
      if (this.maw() != null) {
         this.maw.scaleMaxHealth(ThanosWarRules.orderHealth(ThanosWarRules.MAW_HEALTH, heroes));
      }

      if (this.cull() != null) {
         this.cull.scaleMaxHealth(ThanosWarRules.orderHealth(ThanosWarRules.CULL_HEALTH, heroes));
      }
   }

   void enrage() {
      if (this.maw() != null) {
         this.maw.enrage();
      }

      if (this.cull() != null) {
         this.cull.enrage();
      }
   }

   /** Thanos under heavy fire: Maw raises his barrier, Cull goes for whoever hurts Thanos most. */
   void protect() {
      this.guard = ThanosWarRules.DEFENSIVE_TICKS;
      if (this.maw() != null) {
         this.maw.guard(true);
      }

      if (this.cull() != null) {
         this.cull.guard(true);
      }
   }

   /** How many allies fight around {@code target} (the Order counts double). */
   public int engaged(LivingEntity target) {
      int n = 0;
      if (this.maw() != null && this.maw.distanceToSqr(target) < 30.0 * 30.0) {
         n += 2;
      }

      if (this.cull() != null && this.cull.distanceToSqr(target) < 20.0 * 20.0) {
         n += 2;
      }

      if (this.alive > 0) {
         int near = this.war.level.getEntitiesOfClass(ChitauriSoldier.class, target.getBoundingBox().inflate(16.0), e -> this.troops.contains(e.getUUID())).size();
         n += Math.min(3, near / 2);
      }

      return n;
   }

   // ---- the Chitauri --------------------------------------------------------------------------------
   void openFront() {
      if (this.front || this.retreating) {
         return;
      }

      this.front = true;
      this.waveTimer = 40;
   }

   private void wave() {
      int heroes = this.war.heroCount();
      int room = ThanosWarRules.room(this.alive, this.spawned, heroes);
      int size = Math.min(ThanosWarRules.waveSize(heroes, this.waves), room);
      if (size <= 0) {
         return;
      }

      this.waves++;
      ServerLevel level = this.war.level;
      int ground = size >= 6 ? 2 : 1;
      int each = (size + ground - 1) / ground;
      int left = size;
      for (int i = 0; i < ground && left > 0; i++) {
         Vec3 at = this.portalPoint(false);
         if (at == null) {
            continue;
         }

         int q = Math.min(each, left);
         left -= q;
         Portal p = new Portal(this.portalIds++, at, false, q);
         this.portals.add(p);
         this.portalFx(p);
      }

      // every other wave a chariot through a portal high over the streets
      if (this.waves % 2 == 0 && this.chariots.size() < 3 && ThanosWarRules.room(this.alive, this.spawned, heroes) - size >= 3) {
         Vec3 at = this.portalPoint(true);
         if (at != null) {
            Portal p = new Portal(this.portalIds++, at, true, 1);
            this.portals.add(p);
            this.portalFx(p);
         }
      }

      if (this.waves == 1) {
         this.war.say(ThanosWar.VOICE_THANOS, this.war.thanos, "wave", HeroLine.Priority.ABILITY, 0, false);
      }
   }

   private void portalFx(Portal p) {
      Vec3 normal = p.sky ? new Vec3(0.0, -1.0, 0.0) : new Vec3(0.0, -1.0, 0.0);
      ThanosTargets.fx(this.war.level, ThanosFxS2CPacket.ARMY_PORTAL, p.id, p.sky ? 1 : 0, p.at, normal, p.sky ? 6.0F : 3.2F, ThanosWarRules.PORTAL_LIFE);
      ThanosWarSounds.play(this.war.level, p.at, ThanosWarSounds.PORTAL_OPEN, p.sky ? 5.0F : 3.0F, p.sky ? 0.8F : 1.0F, 160.0);
   }

   /** A spot for a portal near the fight: open sky above, not on top of a hero, within the portal range. */
   @Nullable
   private Vec3 portalPoint(boolean sky) {
      ServerLevel level = this.war.level;
      Vec3 c = this.war.thanos.position();
      for (int tries = 0; tries < 16; tries++) {
         double a = this.war.random.nextDouble() * Math.PI * 2.0;
         double d = 10.0 + this.war.random.nextDouble() * (ThanosWarRules.PORTAL_RANGE - 10.0);
         double x = c.x + Math.cos(a) * d;
         double z = c.z + Math.sin(a) * d;
         Vec3 top = WarGround.top(level, x, z);
         if (Math.abs(top.y - c.y) > 24.0 || !level.isLoaded(net.minecraft.core.BlockPos.containing(top))) {
            continue;
         }

         boolean onHero = false;
         for (ServerPlayer p : this.war.heroes()) {
            if (p.distanceToSqr(top.x, p.getY(), top.z) < 36.0) {
               onHero = true;
               break;
            }
         }

         if (onHero) {
            continue;
         }

         return top.add(0.0, sky ? ThanosWarRules.PORTAL_HEIGHT : DROP_HEIGHT, 0.0);
      }

      return null;
   }

   private void tickPortals() {
      ServerLevel level = this.war.level;
      for (Iterator<Portal> it = this.portals.iterator(); it.hasNext(); ) {
         Portal p = it.next();
         if (--p.life <= 0) {
            it.remove();
            continue;
         }

         if (p.queue <= 0 || this.retreating || this.war.over() || --p.gap > 0) {
            continue;
         }

         p.gap = p.sky ? 20 : 9 + this.war.random.nextInt(6);
         int heroes = this.war.heroCount();
         if (ThanosWarRules.room(this.alive, this.spawned, heroes) <= 0) {
            p.queue = 0;
            continue;
         }

         p.queue--;
         if (p.sky) {
            Vec3 drop = this.dropPoint();
            byte kind = this.war.random.nextInt(3) == 0 ? ChitauriChariot.KIND_CARRIER : ChitauriChariot.KIND_GUNSHIP;
            Vec3 dir = drop.subtract(p.at);
            dir = new Vec3(dir.x, 0.0, dir.z).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(dir.x, 0.0, dir.z).normalize();
            List<UUID> ids = ChitauriInvasion.spawnChariotAt(level, p.at, dir.scale(0.9).add(0, -0.15, 0), kind, drop.add(0, 2, 0));
            if (!ids.isEmpty()) {
               this.chariots.add(ids.get(0));
               for (int i = 1; i < ids.size(); i++) {
                  this.troops.add(ids.get(i));
               }

               this.spawned += ids.size() - 1;
               this.alive += ids.size() - 1;
            }
         } else {
            Vec3 at = p.at.add(this.war.random.nextGaussian() * 0.6, -0.6, this.war.random.nextGaussian() * 0.6);
            boolean heavy = this.waves >= 3 && this.war.random.nextInt(6) == 0;
            ChitauriSoldier s = heavy ? ChitauriInvasion.spawnTrooper(level, at, ChitauriEntities.HEAVY.get(), ChitauriSoldier.ROLE_TROOPER, -1)
               : ChitauriInvasion.spawnSoldier(level, at, ChitauriSoldier.ROLE_TROOPER, -1);
            if (s != null) {
               s.setDeltaMovement(this.war.random.nextGaussian() * 0.08, -0.2, this.war.random.nextGaussian() * 0.08);
               this.order(s);
               this.troops.add(s.getUUID());
               this.spawned++;
               this.alive++;
            }
         }
      }
   }

   /** Where a chariot drops its riders: near a hero, else near Thanos. */
   private Vec3 dropPoint() {
      List<ServerPlayer> heroes = this.war.heroes();
      Vec3 c = heroes.isEmpty() ? this.war.thanos.position() : heroes.get(this.war.random.nextInt(heroes.size())).position();
      double a = this.war.random.nextDouble() * Math.PI * 2.0;
      return WarGround.top(this.war.level, c.x + Math.cos(a) * 9.0, c.z + Math.sin(a) * 9.0);
   }

   /** The war's order for one soldier: keep to the streets around Thanos (and the heroes there). */
   private void order(ChitauriSoldier s) {
      Vec3 c = this.war.thanos.position();
      s.order = new WarOrder(WarOrder.Type.PATROL, c.x, c.y, c.z, 0, -1, this.war.ticks());
   }

   // ---- ticking -----------------------------------------------------------------------------------
   void tick(ThanosEvent.Stage stage) {
      ServerLevel level = this.war.level;
      if (this.beamIn > 0 && --this.beamIn == 0) {
         this.arrive();
      }

      if (this.guard > 0 && --this.guard == 0) {
         if (this.maw() != null) {
            this.maw.guard(false);
         }

         if (this.cull() != null) {
            this.cull.guard(false);
         }
      }

      this.tickOrder();
      if (this.front && !this.retreating && !this.war.over() && stage == ThanosEvent.Stage.FIGHT && --this.waveTimer <= 0) {
         this.waveTimer = ThanosWarRules.waveEvery(this.war.phase()) + this.war.random.nextInt(80);
         this.wave();
      }

      this.tickPortals();
      if (this.war.ticks() % 20 == 0) {
         this.prune(level);
      }
   }

   private void tickOrder() {
      EbonyMawEntity m = this.maw;
      if (m != null && !m.isAlive()) {
         this.maw = null;
         this.mawDown = !m.isRemoved() || m.getHealth() <= 0.0F;
         this.mawBar.removeAllPlayers();
         if (m.getHealth() <= 0.0F) {
            this.war.say(ThanosWar.VOICE_THANOS, this.war.thanos, "lost_maw", HeroLine.Priority.ABILITY, 0, false);
         }
      }

      CullObsidianEntity c = this.cull;
      if (c != null && !c.isAlive()) {
         this.cull = null;
         this.cullDown = !c.isRemoved() || c.getHealth() <= 0.0F;
         this.cullBar.removeAllPlayers();
         if (c.getHealth() <= 0.0F) {
            this.war.say(ThanosWar.VOICE_THANOS, this.war.thanos, "lost_cull", HeroLine.Priority.ABILITY, 0, false);
         }
      }

      if (this.war.ticks() % 5 == 0) {
         this.bar(this.mawBar, this.maw());
         this.bar(this.cullBar, this.cull());
      }
   }

   private void bar(ServerBossEvent bar, @Nullable BlackOrderEntity e) {
      if (e == null) {
         if (!bar.getPlayers().isEmpty()) {
            bar.removeAllPlayers();
         }

         return;
      }

      bar.setProgress(Math.max(0.0F, Math.min(1.0F, e.getHealth() / e.getMaxHealth())));
      Set<ServerPlayer> want = new HashSet<>();
      for (ServerPlayer p : this.war.level.players()) {
         if (p.distanceToSqr(e) < 96.0 * 96.0) {
            want.add(p);
         }
      }

      for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
         if (!want.contains(p)) {
            bar.removePlayer(p);
         }
      }

      for (ServerPlayer p : want) {
         if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
         }
      }
   }

   private void prune(ServerLevel level) {
      int n = 0;
      for (Iterator<UUID> it = this.troops.iterator(); it.hasNext(); ) {
         Entity e = level.getEntity(it.next());
         if (!(e instanceof LivingEntity l) || !l.isAlive()) {
            it.remove();
            continue;
         }

         n++;
         if (e instanceof ChitauriSoldier s && !this.retreating && this.war.ticks() % 100 == 0) {
            this.order(s);
         }
      }

      this.chariots.removeIf(id -> {
         Entity e = level.getEntity(id);
         return !(e instanceof LivingEntity l) || !l.isAlive();
      });
      this.alive = n;
   }

   // ---- the end ------------------------------------------------------------------------------------
   /** Victory: the Order is beamed out, the hive link breaks and the soldiers collapse. */
   void retreat() {
      this.retreating = true;
      this.front = false;
      this.beamIn = -1;
      this.portals.clear();
      ServerLevel level = this.war.level;
      for (BlackOrderEntity e : new BlackOrderEntity[]{this.maw(), this.cull()}) {
         if (e != null) {
            this.war.ship.beam(e.position(), 30);
            e.beamOut();
         }
      }

      for (UUID id : this.troops) {
         if (level.getEntity(id) instanceof ChitauriSoldier s) {
            s.sever(1 + this.war.random.nextInt(40));
         }
      }

      for (UUID id : this.chariots) {
         if (level.getEntity(id) instanceof LivingEntity l) {
            l.kill();
         }
      }

      this.mawBar.removeAllPlayers();
      this.cullBar.removeAllPlayers();
   }

   /** The snap: the war is won for him; his forces stop where they stand. */
   void standDown() {
      this.front = false;
      this.portals.clear();
      if (this.maw() != null) {
         this.maw.standDown();
      }

      if (this.cull() != null) {
         this.cull.standDown();
      }
   }

   /** Everything the war brought leaves the world ({@code fade}: the soldiers collapse, else they vanish). */
   void clear(boolean fade) {
      ServerLevel level = this.war.level;
      this.retreating = true;
      this.front = false;
      this.beamIn = -1;
      this.portals.clear();
      for (BlackOrderEntity e : new BlackOrderEntity[]{this.maw, this.cull}) {
         if (e != null && !e.isRemoved()) {
            e.discard();
         }
      }

      this.maw = null;
      this.cull = null;
      for (UUID id : this.troops) {
         Entity e = level.getEntity(id);
         if (e != null) {
            if (e.getVehicle() != null) {
               e.stopRiding();
            }

            if (fade && e instanceof ChitauriSoldier s && s.isAlive()) {
               s.sever(1 + this.war.random.nextInt(20));
            } else {
               e.discard();
            }
         }
      }

      for (UUID id : this.chariots) {
         Entity e = level.getEntity(id);
         if (e != null) {
            e.ejectPassengers();
            e.discard();
         }
      }

      this.troops.clear();
      this.chariots.clear();
      this.alive = 0;
      this.mawBar.removeAllPlayers();
      this.cullBar.removeAllPlayers();
   }

   /** Debug: the tracked units (commands). */
   public int tracked() {
      return this.troops.size() + this.chariots.size();
   }

   /** Debug: a wave now (commands). */
   public void forceWave() {
      this.front = true;
      this.retreating = false;
      this.wave();
   }

   /** Debug: the Order now (commands). */
   public void forceOrder() {
      this.retreating = false;
      if (!this.called) {
         this.callOrder();
      } else if (this.maw() == null && this.cull() == null && this.beamIn <= 0) {
         this.mawDown = false;
         this.cullDown = false;
         this.called = false;
         this.callOrder();
      }
   }
}
