package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.HeroSession;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hero seam on the Player entity: canonical synced identity + the packed public
 * snapshot + server-only session/state fields. Runs after the legacy mixins so
 * a stored HeroData always wins over the legacy IsViltrumite projection.
 */
@Mixin(
   value = {Player.class},
   priority = 1400
)
public abstract class PlayerHeroMixin implements HeroPlayer {
   @Unique
   private boolean legacyLoadoutPending;
   @Unique
   private static final String GRANTED_MAYFLY_KEY = "HeroGrantedMayfly";
   @Unique
   private boolean heroGrantedMayfly;
   @Unique
   private static final EntityDataAccessor<Integer> HERO_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
   @Unique
   private static final EntityDataAccessor<String> HERO_SNAPSHOT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.STRING);

   @Unique
   private UUID heroSessionId;
   @Unique
   private boolean heroTotemConsumed;
   @Unique
   private Object heroState;
   @Unique
   private HeroOwnerSnapshot ownerSnapshot = HeroOwnerSnapshot.EMPTY;
   @Unique
   private String cachedSnapshotString = "";
   @Unique
   private HeroPublicSnapshot cachedSnapshot = HeroPublicSnapshot.EMPTY;

   @Inject(method = {"defineSynchedData"}, at = {@At("TAIL")})
   private void viltrumitecore$defineHeroData(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      // Every new player starts as a Human; a hero is chosen only with Od Lagna's Wish (the item).
      int defaultId = HeroId.HUMAN.ordinal();
      player.getEntityData().define(HERO_ID, defaultId);
      player.getEntityData().define(HERO_SNAPSHOT, HeroPublicSnapshot.EMPTY.encode());
   }

   @Override
   public HeroId getHeroId() {
      int ordinal = (Integer)((Player)(Object)this).getEntityData().get(HERO_ID);
      HeroId[] values = HeroId.values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : HeroId.HUMAN;
   }

   @Override
   public HeroSession getHeroSession() {
      return new HeroSession(this.getHeroId(), this.heroSessionId == null ? new UUID(0L, 0L) : this.heroSessionId, this.heroTotemConsumed);
   }

   @Override
   public HeroPublicSnapshot getHeroSnapshot() {
      String encoded = (String)((Player)(Object)this).getEntityData().get(HERO_SNAPSHOT);
      if (!encoded.equals(this.cachedSnapshotString)) {
         this.cachedSnapshot = HeroPublicSnapshot.decode(encoded);
         this.cachedSnapshotString = encoded;
      }

      return this.cachedSnapshot;
   }

   @Override
   public void viltrumitecore$setHeroId(HeroId id) {
      ((Player)(Object)this).getEntityData().set(HERO_ID, id.ordinal());
   }

   @Override
   public void viltrumitecore$setHeroSession(HeroSession session) {
      this.viltrumitecore$setHeroId(session.heroId());
      this.heroSessionId = session.sessionId();
      this.heroTotemConsumed = session.totemConsumed();
   }

   @Override
   public void viltrumitecore$setTotemConsumed(boolean consumed) {
      this.heroTotemConsumed = consumed;
   }

   @Override
   public Object viltrumitecore$getHeroState() {
      return this.heroState;
   }

   @Override
   public void viltrumitecore$setHeroState(Object state) {
      this.heroState = state;
   }

   @Override
   public HeroOwnerSnapshot viltrumitecore$getOwnerSnapshot() {
      return this.ownerSnapshot;
   }

   @Override
   public void viltrumitecore$setOwnerSnapshot(HeroOwnerSnapshot snapshot) {
      this.ownerSnapshot = snapshot;
   }

   @Override
   public void viltrumitecore$setSyncedSnapshot(HeroPublicSnapshot snapshot) {
      String encoded = snapshot.encode();
      Player player = (Player)(Object)this;
      if (!encoded.equals(player.getEntityData().get(HERO_SNAPSHOT))) {
         player.getEntityData().set(HERO_SNAPSHOT, encoded);
      }
   }

   @Override
   public boolean viltrumitecore$isBodyKey(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
      return HERO_ID.equals(key) || HERO_SNAPSHOT.equals(key);
   }

   @Inject(method = {"tick"}, at = {@At("TAIL")})
   private void viltrumitecore$tickHero(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
         return;
      }

      // Migration: every player gets a session identity lazily on first tick.
      if (this.heroSessionId == null) {
         this.heroSessionId = UUID.randomUUID();
      }

      dev.baranhan.viltrumitecore.hero.HeldInputs.tick(serverPlayer);
      HeroRegistry.get(serverPlayer).tick(serverPlayer);
      dev.baranhan.viltrumitecore.hero.HeroFlightGrant.sync(serverPlayer);
      HeroRegistry.syncSnapshot(serverPlayer);
   }

   @Override
   public boolean viltrumitecore$isMayflyGranted() {
      return this.heroGrantedMayfly;
   }

   @Override
   public void viltrumitecore$setMayflyGranted(boolean granted) {
      this.heroGrantedMayfly = granted;
   }

   @Override
   public boolean viltrumitecore$consumeLegacyLoadout() {
      boolean pending = this.legacyLoadoutPending;
      this.legacyLoadoutPending = false;
      return pending;
   }

   /** A save written before Homelander: hero id "viltrumite", or no hero data and the old flag. */
   @Unique
   private static boolean isLegacyViltrumiteSave(CompoundTag nbt) {
      if (nbt.contains(HeroSession.NBT_KEY)) {
         return HeroId.VILTRUMITE.key().equals(nbt.getCompound(HeroSession.NBT_KEY).getString("Id"));
      }

      return nbt.getBoolean("IsViltrumite");
   }

   @Inject(method = {"addAdditionalSaveData"}, at = {@At("TAIL")})
   private void viltrumitecore$writeHeroData(CompoundTag nbt, CallbackInfo ci) {
      Player player = (Player)(Object)this;
      new HeroSession(this.getHeroId(), this.heroSessionId == null ? UUID.randomUUID() : this.heroSessionId, this.heroTotemConsumed).save(nbt);
      nbt.putBoolean(GRANTED_MAYFLY_KEY, this.heroGrantedMayfly);
      HeroRegistry.get(player).saveHeroState(player, nbt);
   }

   @Inject(method = {"readAdditionalSaveData"}, at = {@At("TAIL")})
   private void viltrumitecore$readHeroData(CompoundTag nbt, CallbackInfo ci) {
      Player player = (Player)(Object)this;
      if (!(player instanceof ServerPlayer serverPlayer)) {
         return;
      }

      HeroId legacy = HeroId.fromLegacyBoolean(nbt.getBoolean("IsViltrumite"));
      this.legacyLoadoutPending = isLegacyViltrumiteSave(nbt);
      this.heroGrantedMayfly = nbt.getBoolean(GRANTED_MAYFLY_KEY);
      HeroSession session = HeroSession.load(nbt, legacy);
      // Restore path: identity + session fields, never a lifecycle entry.
      this.viltrumitecore$setHeroSession(session);
      // The hero definition decides what state survives a relog (Regulus keeps
      // its cooldowns); the default rebuilds the state empty as before.
      HeroRegistry.get(serverPlayer).loadHeroState(serverPlayer, nbt);
   }
}
