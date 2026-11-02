package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.entity.Bystander;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The record the Penance Stare reads: only deeds that really happened. A player's kills come from his vanilla
 * statistics (players killed; villagers, traders, allays and city folk killed); everything else - a mob's kills, the
 * pets a player killed, the damage anyone dealt to players and innocents - is journaled here from the death and damage
 * events since the mod first saw them (saved with the world). Killing monsters is no sin. No record, no sin.
 */
public final class PenanceLedger extends SavedData {
   private static final String DATA = "viltrumitecore_penance";
   /** Entries of mobs are dropped when they die; players' entries are kept. Hard cap against unbounded growth. */
   private static final int MAX_ENTRIES = 4096;

   /** One soul's record. */
   public record Deeds(int playerKills, int innocentKills, float harm) {
      public static final Deeds NONE = new Deeds(0, 0, 0.0F);

      public int sins() {
         return GhostRiderRules.sins(this.playerKills, this.innocentKills, this.harm);
      }

      public boolean innocent() {
         return this.sins() <= 0;
      }
   }

   private static final class Entry {
      int playerKills;
      int innocentKills;
      float harm;
   }

   private final Map<UUID, Entry> entries = new HashMap<>();

   public PenanceLedger() {
   }

   public static PenanceLedger load(CompoundTag tag) {
      PenanceLedger ledger = new PenanceLedger();
      ListTag list = tag.getList("Souls", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag c = list.getCompound(i);
         if (!c.hasUUID("Id")) {
            continue;
         }
         Entry e = new Entry();
         e.playerKills = c.getInt("Players");
         e.innocentKills = c.getInt("Innocents");
         e.harm = c.getFloat("Harm");
         ledger.entries.put(c.getUUID("Id"), e);
      }
      return ledger;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag list = new ListTag();
      for (Map.Entry<UUID, Entry> en : this.entries.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putUUID("Id", en.getKey());
         c.putInt("Players", en.getValue().playerKills);
         c.putInt("Innocents", en.getValue().innocentKills);
         c.putFloat("Harm", en.getValue().harm);
         list.add(c);
      }
      tag.put("Souls", list);
      return tag;
   }

   @Nullable
   public static PenanceLedger get(@Nullable MinecraftServer server) {
      if (server == null) {
         return null;
      }
      ServerLevel overworld = server.overworld();
      return overworld == null ? null : overworld.getDataStorage().computeIfAbsent(PenanceLedger::load, PenanceLedger::new, DATA);
   }

   /** Innocents: villagers and traders, city folk, tamed pets, allays. */
   public static boolean innocent(LivingEntity e) {
      return e instanceof AbstractVillager || Bystander.is(e) || e instanceof TamableAnimal pet && pet.isTame() || e instanceof Allay;
   }

   private Entry entry(UUID id) {
      if (this.entries.size() >= MAX_ENTRIES && !this.entries.containsKey(id)) {
         this.entries.keySet().stream().findFirst().ifPresent(this.entries::remove);
      }
      return this.entries.computeIfAbsent(id, k -> new Entry());
   }

   /** Damage dealt (after armor) by {@code attacker} to a player or an innocent. */
   public void recordHarm(LivingEntity attacker, LivingEntity victim, float amount) {
      if (attacker == victim || amount <= 0.0F || !(victim instanceof Player || innocent(victim))) {
         return;
      }
      Entry e = this.entry(attacker.getUUID());
      e.harm += Math.min(amount, Math.max(0.0F, victim.getHealth()));
      this.setDirty();
   }

   /** A death: count the killer's kill (players' own kills of players and villagers are already in their stats). */
   public void recordDeath(@Nullable LivingEntity killer, LivingEntity victim) {
      if (killer != null && killer != victim) {
         boolean victimPlayer = victim instanceof Player;
         boolean victimInnocent = innocent(victim);
         if (victimPlayer || victimInnocent) {
            boolean inStats = killer instanceof Player && (victimPlayer || statCounted(victim));
            if (!inStats) {
               Entry e = this.entry(killer.getUUID());
               if (victimPlayer) {
                  e.playerKills++;
               } else {
                  e.innocentKills++;
               }
               this.setDirty();
            }
         }
      }
      if (!(victim instanceof Player) && this.entries.remove(victim.getUUID()) != null) {
         this.setDirty();
      }
   }

   /** Innocents a player's vanilla kill statistics already count. */
   private static boolean statCounted(LivingEntity victim) {
      EntityType<?> type = victim.getType();
      return type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER || type == EntityType.ALLAY
         || type == dev.baranhan.viltrumitecore.entity.ViltrumiteEntities.CITIZEN.get();
   }

   /** Everything known about {@code soul}: the journal plus, for a player, his statistics. */
   public Deeds deeds(LivingEntity soul) {
      Entry e = this.entries.get(soul.getUUID());
      int players = e == null ? 0 : e.playerKills;
      int innocents = e == null ? 0 : e.innocentKills;
      float harm = e == null ? 0.0F : e.harm;
      if (soul instanceof ServerPlayer player) {
         var stats = player.getStats();
         players += stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
         innocents += stats.getValue(Stats.ENTITY_KILLED.get(EntityType.VILLAGER));
         innocents += stats.getValue(Stats.ENTITY_KILLED.get(EntityType.WANDERING_TRADER));
         innocents += stats.getValue(Stats.ENTITY_KILLED.get(EntityType.ALLAY));
         innocents += stats.getValue(Stats.ENTITY_KILLED.get(dev.baranhan.viltrumitecore.entity.ViltrumiteEntities.CITIZEN.get()));
      }
      return new Deeds(players, innocents, harm);
   }
}
