package dev.baranhan.viltrumitecore.worldevent.chitauri;

import dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriBeacon;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriCaptor;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.CaptiveLedger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Captured citizens (docs/plans/2026-11-04-chitauri-evolution.md, "Захват жителей"). The only touch on the city is
 * {@link CitizenCaptivity}: while its tag is set the citizen's own brain, stuck check and the population manager leave
 * the person alone. Everything else is here: the ledger (saved per dimension, with a snapshot of non-saved citizens),
 * the containment fields, rescue by a hero (use the person, destroy the pylon, hit the captor hard or kill it) and the
 * restore of a person whose chunk unloaded while held. Nobody is ever removed: at the end every captive goes free.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ChitauriCaptives {
   /** Why someone went free. */
   public enum Cause {
      /** The captor let go (hard hit, death, nowhere to go). */
      DROPPED,
      /** A hero freed them from the field. */
      RESCUED,
      /** The containment pylon fell. */
      BEACON_DOWN,
      /** The invasion ended (fields collapse). */
      ENDED
   }

   private static final String DATA = "viltrumitecore_chitauri_captives";
   /** Who is going for whom (transient; a person is chased by one captor at a time). */
   private static final Map<UUID, UUID> CLAIMS = new HashMap<>();

   private ChitauriCaptives() {
   }

   // ------------------------------------------------------------------ saved ledger

   static final class Data extends SavedData {
      final CaptiveLedger ledger = new CaptiveLedger();

      static Data load(CompoundTag tag) {
         Data d = new Data();
         ListTag list = tag.getList("captives", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("id")) {
               continue;
            }
            // after a restart nobody is carried or held any more (captors and pylons are never saved): restore + free
            CaptiveLedger.Entry e = new CaptiveLedger.Entry(c.getUUID("id"), c.getString("type"), CaptiveLedger.State.LOST, null, c.getDouble("x"),
               c.getDouble("y"), c.getDouble("z"), 0);
            e.snapshot = c.getString("snbt");
            d.ledger.entriesForLoad(e);
         }
         return d;
      }

      @Override
      public CompoundTag save(CompoundTag tag) {
         ListTag list = new ListTag();
         for (CaptiveLedger.Entry e : this.ledger.entries()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id);
            c.putString("type", e.type);
            c.putString("state", e.state.name());
            c.putDouble("x", e.x);
            c.putDouble("y", e.y);
            c.putDouble("z", e.z);
            c.putString("snbt", e.snapshot);
            list.add(c);
         }
         tag.put("captives", list);
         return tag;
      }
   }

   static Data data(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA);
   }

   public static CaptiveLedger ledger(ServerLevel level) {
      return data(level).ledger;
   }

   // ------------------------------------------------------------------ claims (captor AI)

   public static boolean claimed(Entity person, Entity by) {
      UUID c = CLAIMS.get(person.getUUID());
      return c != null && !c.equals(by.getUUID());
   }

   public static void claim(Entity person, Entity by) {
      CLAIMS.put(person.getUUID(), by.getUUID());
   }

   public static void unclaim(Entity person, Entity by) {
      CLAIMS.remove(person.getUUID(), by.getUUID());
   }

   /** The hive may take another person (an invasion runs here with a war layer and the ledger has room). */
   public static boolean canTakeMore(ServerLevel level) {
      ChitauriWar war = ChitauriWar.of(level);
      return war != null && war.fighting() && ledger(level).canTake();
   }

   // ------------------------------------------------------------------ capture, delivery, release

   /** A captor finished its grab: the person is held (tag + record). False = the hive may not take more. */
   public static boolean carry(ServerLevel level, ChitauriCaptor captor, LivingEntity person) {
      Data d = data(level);
      String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(person.getType()));
      if (!d.ledger.carry(person.getUUID(), type, captor.getUUID(), person.getX(), person.getY(), person.getZ(), level.getServer().getTickCount())) {
         return false;
      }
      snapshot(d.ledger.get(person.getUUID()), person);
      CitizenCaptivity.hold(person, "");
      if (person instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }
      CLAIMS.remove(person.getUUID());
      d.setDirty();
      ChitauriWar war = ChitauriWar.of(level);
      if (war != null) {
         war.onCaptured(level, captor, person);
      }
      return true;
   }

   /** The captor reached the field: the person goes in (or free when every field is full). */
   public static void deliver(ServerLevel level, ChitauriCaptor captor, LivingEntity person) {
      Data d = data(level);
      ChitauriWar war = ChitauriWar.of(level);
      ChitauriBeacon beacon = war != null ? war.containmentNear(level, captor.position(), false) : null;
      if (beacon == null || !d.ledger.deliver(person.getUUID(), beacon.getUUID(), level.getServer().getTickCount())) {
         release(level, person, Cause.DROPPED, null);
         return;
      }
      CitizenCaptivity.hold(person, beacon.getUUID().toString());
      person.setNoGravity(true);
      beacon.setHeld(d.ledger.heldBy(beacon.getUUID()));
      d.setDirty();
      war.onDelivered(level, beacon, person);
   }

   /** Frees one person (any cause). {@code by}: the hero who did it, if any. */
   public static void release(ServerLevel level, Entity person, Cause cause, @Nullable ServerPlayer by) {
      Data d = data(level);
      CaptiveLedger.Entry e = d.ledger.release(person.getUUID(), cause == Cause.RESCUED || cause == Cause.BEACON_DOWN);
      CitizenCaptivity.release(person);
      CLAIMS.remove(person.getUUID());
      person.setNoGravity(false);
      person.noPhysics = false;
      person.fallDistance = 0.0F;
      if (e != null) {
         d.setDirty();
         if (e.holder != null && level.getEntity(e.holder) instanceof ChitauriBeacon b) {
            b.setHeld(d.ledger.heldBy(b.getUUID()));
         }
      }
      if (cause == Cause.RESCUED || cause == Cause.BEACON_DOWN || cause == Cause.ENDED) {
         Vec3 at = person.position().add(0, person.getBbHeight() * 0.5, 0);
         ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.RELEASE, at, new Vec3(0, 1, 0), 1.0F, person.getId()), at, 96);
         ChitauriSounds.play(level, at, ChitauriSounds.CAPTIVE_FREED, 1.2F, 1.0F);
      }
      ChitauriWar war = ChitauriWar.of(level);
      if (war != null && e != null) {
         war.onFreed(level, person, cause, by);
      }
   }

   /** Every person held by this pylon goes free (the pylon fell or vanished). Returns how many. */
   public static int releaseHeldBy(ServerLevel level, ChitauriBeacon beacon, Cause cause, @Nullable ServerPlayer by) {
      Data d = data(level);
      int n = 0;
      for (CaptiveLedger.Entry e : d.ledger.listHeldBy(beacon.getUUID())) {
         Entity p = level.getEntity(e.id);
         if (p != null) {
            release(level, p, cause, by);
         } else {
            d.ledger.lose(e.id);
            d.setDirty();
         }
         n++;
      }
      return n;
   }

   /** Invasion over (end, stop or a restart of it): every captive goes free. */
   public static void releaseAll(ServerLevel level) {
      Data d = data(level);
      for (CaptiveLedger.Entry e : List.copyOf(d.ledger.entries())) {
         if (!e.active()) {
            continue;
         }
         Entity p = level.getEntity(e.id);
         if (p != null) {
            release(level, p, Cause.ENDED, null);
         } else {
            d.ledger.lose(e.id);
         }
      }
      CLAIMS.clear();
      d.setDirty();
   }

   /** Where a captor takes its captive: the nearest field with room (the war plants one if needed). */
   @Nullable
   public static Vec3 containmentPoint(ServerLevel level, ChitauriCaptor captor) {
      ChitauriWar war = ChitauriWar.of(level);
      ChitauriBeacon b = war != null ? war.containmentNear(level, captor.position(), true) : null;
      return b != null ? b.position() : null;
   }

   private static void snapshot(@Nullable CaptiveLedger.Entry e, Entity person) {
      if (e == null) {
         return;
      }
      e.x = person.getX();
      e.y = person.getY();
      e.z = person.getZ();
      if (!person.getType().canSerialize()) {
         // a non-saved citizen would vanish for good with its chunk: keep a copy (without the captive tag)
         CompoundTag tag = person.saveWithoutId(new CompoundTag());
         tag.putString("id", e.type);
         if (tag.contains("ForgeData", Tag.TAG_COMPOUND)) {
            tag.getCompound("ForgeData").remove(CitizenCaptivity.TAG);
         }
         tag.remove("Passengers");
         e.snapshot = tag.toString();
      }
   }

   // ------------------------------------------------------------------ upkeep

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      int tick = event.getServer().getTickCount();
      for (ServerLevel level : event.getServer().getAllLevels()) {
         Data d = level.getDataStorage().get(Data::load, DATA);
         if (d == null || d.ledger.isEmpty()) {
            continue;
         }
         hold(level, d);
         if (tick % 40 == 0) {
            upkeep(level, d, tick);
         }
      }
   }

   /** Every tick: people in a field float in their slot around the pylon. */
   private static void hold(ServerLevel level, Data d) {
      for (CaptiveLedger.Entry e : d.ledger.entries()) {
         if (e.state != CaptiveLedger.State.HELD || e.holder == null) {
            continue;
         }
         Entity p = level.getEntity(e.id);
         if (p == null || !(level.getEntity(e.holder) instanceof ChitauriBeacon b) || !b.isAlive()) {
            continue;
         }
         List<CaptiveLedger.Entry> mates = d.ledger.listHeldBy(e.holder);
         int i = mates.indexOf(e);
         Vec3 at = freeSlot(level, p, b, i, Math.max(mates.size(), 3));
         double bob = Math.sin((level.getGameTime() + i * 13) * 0.08) * 0.12;
         p.setPos(at.x, at.y + bob, at.z);
         p.setDeltaMovement(Vec3.ZERO);
         p.fallDistance = 0.0F;
         if (p instanceof Mob mob) {
            mob.getNavigation().stop();
         }
      }
   }

   /**
    * The slot a held person floats in, moved out of walls: a pylon planted next to a building must not push its
    * captives into the bricks. Tries the ring slot, then the same angle closer in and higher, then the pylon top.
    */
   static Vec3 freeSlot(ServerLevel level, Entity p, ChitauriBeacon b, int i, int n) {
      Vec3 base = b.slot(i, n);
      Vec3 c = b.position();
      for (double lift = 0.0; lift <= 2.01; lift += 1.0) {
         for (double k = 1.0; k >= 0.49; k -= 0.25) {
            Vec3 at = new Vec3(c.x + (base.x - c.x) * k, base.y + lift, c.z + (base.z - c.z) * k);
            if (level.noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(at))) {
               return at;
            }
         }
      }
      return c.add(0.0, b.getBbHeight() + 0.2, 0.0);
   }

   /**
    * A held person is not hurt by the holding itself (walls, crowding, falling or flying into things) nor by the hive's
    * own fire: the field shields its prisoners from Chitauri bolts and blasts. Heroes' attacks still land.
    */
   @SubscribeEvent
   public static void onAttack(LivingAttackEvent event) {
      LivingEntity e = event.getEntity();
      if (e.level().isClientSide() || !CitizenCaptivity.isHeld(e)) {
         return;
      }
      DamageSource s = event.getSource();
      if (s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.CRAMMING) || s.is(DamageTypes.FALL) || s.is(DamageTypes.FLY_INTO_WALL)
         || s.is(DamageTypes.DROWN) || dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob.is(s.getEntity())
         || dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob.is(s.getDirectEntity())) {
         event.setCanceled(true);
      }
   }

   /** Every 2 s: check holders, keep snapshots fresh, restore the lost when their place ticks. */
   private static void upkeep(ServerLevel level, Data d, int tick) {
      ChitauriWar war = ChitauriWar.of(level);
      for (CaptiveLedger.Entry e : List.copyOf(d.ledger.entries())) {
         Entity p = level.getEntity(e.id);
         if (p != null) {
            if (e.state == CaptiveLedger.State.LOST) {
               release(level, p, Cause.ENDED, null); // back in the world: free
               d.ledger.forget(e.id);
               continue;
            }
            Entity holder = e.holder != null ? level.getEntity(e.holder) : null;
            boolean ok = war != null && (e.state == CaptiveLedger.State.HELD ? holder instanceof ChitauriBeacon b && b.isAlive()
               : holder instanceof ChitauriCaptor c && c.isAlive() && c.carriedId() == p.getId());
            if (!ok) {
               release(level, p, e.state == CaptiveLedger.State.HELD ? Cause.BEACON_DOWN : Cause.DROPPED, null);
               continue;
            }
            if (tick % 200 == 0) {
               snapshot(e, p);
               d.setDirty();
            }
            continue;
         }
         if (e.active()) {
            d.ledger.lose(e.id); // vanished with its chunk
            d.setDirty();
            continue;
         }
         if (ChitauriTerrain.ticking(level, e.x, e.y, e.z)) {
            restore(level, d, e);
         }
      }
   }

   /** The place of a lost person ticks again: a non-saved citizen comes back from its snapshot, free. */
   private static void restore(ServerLevel level, Data d, CaptiveLedger.Entry e) {
      d.ledger.forget(e.id);
      d.setDirty();
      if (e.snapshot.isEmpty()) {
         return; // saved entities (villagers) come back with their chunk; the join check clears a stale tag
      }
      try {
         CompoundTag tag = TagParser.parseTag(e.snapshot);
         int y = ChitauriTerrain.surface(level, e.x, e.z);
         double fy = y != ChitauriTerrain.UNKNOWN ? y : e.y;
         Optional<Entity> made = EntityType.create(tag, level);
         if (made.isPresent()) {
            Entity p = made.get();
            p.moveTo(e.x, fy, e.z, p.getYRot(), 0.0F);
            CitizenCaptivity.release(p);
            p.setNoGravity(false);
            if (level.addFreshEntity(p)) {
               ChitauriInvasion.LOG.info("Chitauri captive {} restored at {} {} {}", e.id, Math.round(e.x), Math.round(fy), Math.round(e.z));
            }
         }
      } catch (Exception ex) {
         ChitauriInvasion.LOG.warn("Chitauri captive {} could not be restored: {}", e.id, ex.toString());
      }
   }

   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level) || !CitizenCaptivity.isHeld(event.getEntity())) {
         return;
      }
      Data d = level.getDataStorage().get(Data::load, DATA);
      CaptiveLedger.Entry e = d != null ? d.ledger.get(event.getEntity().getUUID()) : null;
      if (e == null || !e.active()) {
         // a stale tag (saved villager from an earlier invasion): the person is free
         CitizenCaptivity.release(event.getEntity());
         event.getEntity().setNoGravity(false);
         if (e != null) {
            d.ledger.forget(e.id);
            d.setDirty();
         }
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity dead = event.getEntity();
      if (dead.level() instanceof ServerLevel level && CitizenCaptivity.isHeld(dead)) {
         Data d = data(level);
         d.ledger.forget(dead.getUUID());
         d.setDirty();
         CitizenCaptivity.release(dead);
      }
   }

   /** A hero uses a person in a field: they go free. */
   @SubscribeEvent
   public static void onInteract(PlayerInteractEvent.EntityInteract event) {
      Entity target = event.getTarget();
      if (!(event.getEntity() instanceof ServerPlayer player) || !(target.level() instanceof ServerLevel level) || !CitizenCaptivity.isHeld(target)) {
         return;
      }
      CaptiveLedger.Entry e = data(level).ledger.get(target.getUUID());
      if (e == null || e.state != CaptiveLedger.State.HELD) {
         return;
      }
      release(level, target, Cause.RESCUED, player);
      event.setCancellationResult(InteractionResult.SUCCESS);
      event.setCanceled(true);
   }
}
