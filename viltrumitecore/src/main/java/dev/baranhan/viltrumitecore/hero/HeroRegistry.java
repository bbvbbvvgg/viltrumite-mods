package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumiteflight.util.FlightPermissions;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class HeroRegistry {
   private static final Map<HeroId, HeroDefinition> DEFINITIONS = new EnumMap<>(HeroId.class);

   private HeroRegistry() {
   }

   public static void registerDefaults() {
      register(new HumanHero());
      register(new ViltrumiteHero());
      register(RegulusHero.INSTANCE);
      register(new dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero());
      register(new dev.baranhan.viltrumitecore.hero.ironman.IronManHero());
      register(dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero.INSTANCE);
      register(dev.baranhan.viltrumitecore.hero.thor.ThorHero.INSTANCE);
      register(dev.baranhan.viltrumitecore.hero.dio.DioHero.INSTANCE);
      // Spec 2026-10-28-sinclair §11.
      register(dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero.INSTANCE);
      register(dev.baranhan.viltrumitecore.hero.jinwoo.JinwooHero.INSTANCE);
      installFlightPolicy();
   }

   /**
    * Core adapter for mod flight (plan flight-policy contract): the hero's own
    * capability AND vanilla mayfly AND no world-control deny — an anchored or
    * dome-captured player loses mod flight until the control releases.
    */
   public static void installFlightPolicy() {
      FlightPermissions.setPolicy(player ->
         get(player).allowsFlight(player)
            && player.getAbilities().mayfly
            && (!(player.level() instanceof ServerLevel level) || !ControlManager.get(level).preventsFlight(player))
      );
      dev.baranhan.viltrumiteflight.util.FlightProfiles.setResolver(player -> get(player).flightProfile(player));
      dev.baranhan.viltrumiteflight.util.FlightProfiles.setSpeedScaleResolver(player -> get(player).flightSpeedScale(player));
   }

   public static void register(HeroDefinition definition) {
      DEFINITIONS.put(definition.id(), definition);
      for (dev.baranhan.viltrumitecore.ability.ViltrumiteAbility ability : definition.panelAbilities()) {
         dev.baranhan.viltrumitecore.ability.ViltrumiteAbilities.registerHeroAbility(ability);
      }
   }

   /** Server, once per joining mob: may any hero scare this mob type (HeroMobFear). */
   public static boolean anyScares(net.minecraft.world.entity.EntityType<?> type) {
      for (HeroDefinition definition : DEFINITIONS.values()) {
         if (definition.scaresMobType(type)) return true;
      }
      return false;
   }

   public static HeroDefinition get(HeroId id) {
      HeroDefinition definition = DEFINITIONS.get(id);
      return definition == null ? DEFINITIONS.get(HeroId.HUMAN) : definition;
   }

   public static HeroDefinition get(Player player) {
      if (player instanceof HeroPlayer heroPlayer) {
         return get(heroPlayer.getHeroId());
      }

      return get(HeroId.HUMAN);
   }

   /**
    * Victim-side control policy for shared push/grab/freeze paths: non-hero
    * targets always allow it; hero targets defer to their definition (an
    * active Lion's Heart denies every kind, including IMPULSE).
    */
   public static boolean allowsExternalControl(net.minecraft.world.entity.LivingEntity target, dev.baranhan.viltrumitecore.hero.control.ControlKind kind) {
      return !(target instanceof Player player) || get(player).allowsExternalControl(target, kind);
   }

   /** Knockback gate for hero pushes: IMPULSE allowed and the target is not anchored by a control. */
   public static boolean allowsImpulse(net.minecraft.world.entity.LivingEntity target) {
      return allowsExternalControl(target, dev.baranhan.viltrumitecore.hero.control.ControlKind.IMPULSE) && !HeroDamage.isAnchored(target);
   }

   /**
    * Explicit hero transition: full lifecycle. No-op when the id is unchanged,
    * so selecting the current hero is never an exit/re-entry exploit. False
    * when nothing changed: same id, or the new hero refused the entry
    * (HeroDefinition.admitsEntry, checked before any cleanup). True when the
    * change ran, or when a scene deferred it (HeroChangeDeferral: the scene
    * calls this again at its end, so admitsEntry is checked again then).
    */
   public static boolean changeHero(ServerPlayer player, HeroId id) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }

      HeroId current = heroPlayer.getHeroId();
      if (!isRealTransition(current, id)) {
         return false;
      }

      if (!get(id).admitsEntry(player)) {
         return false;
      }

      // Session C: a running scene (fatality) takes the change over and applies it at its end (#216).
      // Accepted, not refused: the choice screen must not reopen (HeroChoiceC2SPacket).
      if (HeroChangeDeferral.defer(player, id)) {
         return true;
      }

      dev.baranhan.viltrumitecore.hero.control.WorldPause.ownerLeft(player.getServer(),player.getUUID());
      HeldInputs.releaseAll(player);
      get(current).cleanup(player, CleanupReason.HERO_CHANGE);
      heroPlayer.viltrumitecore$setHeroState(null);

      if (player instanceof ViltrumiteAbilityUser abilityUser) {
         String[] loadout = get(id).defaultLoadout();

         for (int slot = 0; slot < 18; slot++) {
            abilityUser.setAbilityInSlot(slot, slot < loadout.length ? loadout[slot] : "");
         }

         abilityUser.setOfferedAbilities(defaultIds(loadout));
         // The new loadout starts on the first page: the old hero's page (Iron Man's second page,
         // Reinhard's mode page) left a hero without pages on dead keys and Alt could not move it.
         abilityUser.setActivePage(AbilityPages.FIRST);
      }

      heroPlayer.viltrumitecore$setHeroSession(new HeroSession(id, UUID.randomUUID(), false));

      if (player instanceof ViltrumiteCorePlayer corePlayer) {
         corePlayer.setChosenRace(true);
      }

      FlightPermissions.resetModFlight(player);
      get(id).enter(player);
      HeroFlightGrant.sync(player);
      syncSnapshot(player);
      return true;
   }

   /**
    * NBT load / clone path: restores identity without a lifecycle, never enters
    * a new session, and clears mod flight leftovers.
    */
   public static void restoreHero(ServerPlayer player, HeroSession session) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return;
      }

      HeroId previous = heroPlayer.getHeroId();
      heroPlayer.viltrumitecore$setHeroSession(session);

      if (shouldResetState(previous, session.heroId())) {
         heroPlayer.viltrumitecore$setHeroState(null);
      }

      FlightPermissions.resetModFlight(player);
   }

   /** Selecting the hero already worn is never an exit/re-entry exploit. */
   public static boolean isRealTransition(HeroId current, HeroId next) {
      return current != next;
   }

   /** Restore keeps the loaded state only when the identity did not change. */
   public static boolean shouldResetState(HeroId previous, HeroId restored) {
      return previous != restored;
   }

   /** Compose and publish the public snapshot when it changed. */
   public static void syncSnapshot(ServerPlayer player) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return;
      }

      HeroPublicSnapshot snapshot = get(player).snapshot(player);
      heroPlayer.viltrumitecore$setSyncedSnapshot(snapshot);
   }

   /** Owner-private snapshot push of the CARRIERS section (carrier ids, focus targets). */
   public static void pushOwnerSnapshot(ServerPlayer player, HeroOwnerSnapshot snapshot) {
      pushOwnerSection(player, OwnerSection.CARRIERS, snapshot.section(OwnerSection.CARRIERS));
   }

   /**
    * Sends every non-empty owner section again, without the dedup. The client
    * drops its sections on a new local player (respawn, dimension change) and
    * on log out, so the server view must be replayed after those.
    */
   public static void resendOwnerSnapshot(ServerPlayer player) {
      HeroOwnerSnapshot current = ((HeroPlayer)player).viltrumitecore$getOwnerSnapshot();
      for (OwnerSection section : OwnerSection.values()) {
         HeroOwnerSnapshot.Section value = current.section(section);
         if (value.ids().length > 0) {
            dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(
               new dev.baranhan.viltrumitecore.network.packet.HeroOwnerSnapshotS2CPacket(section, value), player
            );
         }
      }
   }

   /** Clears the given owner sections (hero cleanup, world change); only changed ones are sent. */
   public static void clearOwnerSections(ServerPlayer player, OwnerSection... sections) {
      for (OwnerSection section : sections) {
         pushOwnerSection(player, section, HeroOwnerSnapshot.Section.EMPTY);
      }
   }

   /** Replaces one owner-only section and sends it when it changed; other sections stay. */
   public static void pushOwnerSection(ServerPlayer player, OwnerSection section, HeroOwnerSnapshot.Section value) {
      HeroOwnerSnapshot current = ((HeroPlayer)player).viltrumitecore$getOwnerSnapshot();
      HeroOwnerSnapshot.Section safe = value == null ? HeroOwnerSnapshot.Section.EMPTY : value;
      if (!current.section(section).equals(safe)) {
         ((HeroPlayer)player).viltrumitecore$setOwnerSnapshot(current.with(section, safe));
         dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(
            new dev.baranhan.viltrumitecore.network.packet.HeroOwnerSnapshotS2CPacket(section, safe), player
         );
      }
   }

   /**
    * Repair the slots after login / a repeated choice: retired ids are first
    * mapped by {@link HeroDefinition#migrateLoadout}; a slot still holding an
    * ability the current hero does not own (e.g. a migrated Viltrumite save)
    * resets the whole loadout to the hero's default, as does an untouched
    * former default. Otherwise default abilities the hero
    * never gave this player (added by a later version) are filled in once; an
    * ability the player removed stays removed.
    */
   public static void repairLoadout(ServerPlayer player) {
      if (!(player instanceof ViltrumiteAbilityUser abilityUser)) {
         return;
      }

      HeroDefinition hero = get(player);
      String[] slots = new String[18];
      for (int slot = 0; slot < 18; slot++) {
         slots[slot] = abilityUser.getAbilityInSlot(slot);
      }

      String[] repaired = repairedSlots(slots, hero);
      if (repaired == null) {
         resetLoadout(player);
         String[] reset = new String[18];
         for (int slot = 0; slot < 18; slot++) {
            reset[slot] = abilityUser.getAbilityInSlot(slot);
         }

         abilityUser.setActivePage(AbilityPages.settle(abilityUser.getActivePage(), reset));
         return;
      }

      for (int slot = 0; slot < 18; slot++) {
         if (!java.util.Objects.equals(repaired[slot], slots[slot])) {
            abilityUser.setAbilityInSlot(slot, repaired[slot]);
         }
      }

      slots = repaired;

      String[] defaults = hero.defaultLoadout();
      // A save from before the offered set: what is in the slots counts as given.
      java.util.Set<String> offered = abilityUser.getOfferedAbilities();
      if (offered == null) {
         offered = defaultIds(slots);
      }

      String[] filled = fillMissingDefaults(slots, defaults, offered);
      for (int slot = 0; slot < 18; slot++) {
         if (!java.util.Objects.equals(filled[slot], slots[slot])) {
            abilityUser.setAbilityInSlot(slot, filled[slot]);
         }
      }

      java.util.Set<String> given = new java.util.LinkedHashSet<>(offered);
      given.addAll(defaultIds(defaults));
      abilityUser.setOfferedAbilities(given);
      // A save left on an empty page (before the page reset on hero change) moves to a filled one.
      int page = abilityUser.getActivePage();
      int settled = AbilityPages.settle(page, filled);
      if (settled != page) {
         abilityUser.setActivePage(settled);
      }
   }

   /**
    * Pure: the saved slots after the hero's id migration, or null when the whole
    * loadout resets to the default (an untouched former default, or an id the
    * hero does not own even after the migration).
    */
   @javax.annotation.Nullable
   public static String[] repairedSlots(String[] slots, HeroDefinition hero) {
      if (isPreviousDefault(slots, hero.previousDefaultLoadouts())) {
         return null;
      }

      String[] normalized = new String[slots.length];
      for (int i = 0; i < slots.length; i++) {
         normalized[i] = slots[i] == null ? "" : slots[i];
      }

      String[] migrated = hero.migrateLoadout(normalized);
      return needsLoadoutReset(migrated, hero::ownsAbility) ? null : migrated;
   }

   /** Non-empty ids of a slot array. */
   public static java.util.Set<String> defaultIds(String[] slots) {
      java.util.Set<String> ids = new java.util.LinkedHashSet<>();
      for (String id : slots) {
         if (id != null && !id.isEmpty()) {
            ids.add(id);
         }
      }

      return ids;
   }

   /**
    * Slots with every default ability that is not in {@code offered} present: a
    * missing one goes to its default slot when that is empty, else to the first
    * empty slot; with no empty slot it stays out. Filled slots are never overwritten.
    */
   public static String[] fillMissingDefaults(String[] slots, String[] defaults, java.util.Set<String> offered) {
      String[] out = new String[slots.length];
      java.util.Set<String> present = new java.util.HashSet<>();
      for (int i = 0; i < slots.length; i++) {
         out[i] = slots[i] == null ? "" : slots[i];
         if (!out[i].isEmpty()) {
            present.add(out[i]);
         }
      }

      for (int i = 0; i < defaults.length; i++) {
         String id = defaults[i];
         if (id == null || id.isEmpty() || present.contains(id) || offered.contains(id)) {
            continue;
         }

         int target = i < out.length && out[i].isEmpty() ? i : -1;
         for (int j = 0; target < 0 && j < out.length; j++) {
            if (out[j].isEmpty()) {
               target = j;
            }
         }

         if (target >= 0) {
            out[target] = id;
            present.add(id);
         }
      }

      return out;
   }

   /** Put the hero's default loadout into all 18 slots (legacy save migration, repair). */
   public static void resetLoadout(ServerPlayer player) {
      if (!(player instanceof ViltrumiteAbilityUser abilityUser)) {
         return;
      }

      String[] loadout = get(player).defaultLoadout();
      for (int slot = 0; slot < 18; slot++) {
         String id = slot < loadout.length ? loadout[slot] : null;
         abilityUser.setAbilityInSlot(slot, id == null ? "" : id);
      }

      abilityUser.setOfferedAbilities(defaultIds(loadout));
   }

   /** True when the slots equal one of the hero's former default layouts (null and "" are the same). */
   public static boolean isPreviousDefault(String[] slots, String[][] previous) {
      for (String[] old : previous) {
         boolean same = true;
         for (int i = 0; i < Math.max(slots.length, old.length) && same; i++) {
            String a = i < slots.length && slots[i] != null ? slots[i] : "";
            String b = i < old.length && old[i] != null ? old[i] : "";
            same = a.equals(b);
         }

         if (same) {
            return true;
         }
      }

      return false;
   }

   /** True when any non-empty slot holds an ability the hero does not own. */
   public static boolean needsLoadoutReset(String[] slots, java.util.function.Predicate<String> owns) {
      for (String id : slots) {
         if (id != null && !id.isEmpty() && !owns.test(id)) {
            return true;
         }
      }

      return false;
   }
}
