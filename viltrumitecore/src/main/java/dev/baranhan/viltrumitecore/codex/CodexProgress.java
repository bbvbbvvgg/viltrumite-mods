package dev.baranhan.viltrumitecore.codex;

import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.CodexEntryDef;
import dev.baranhan.viltrumitecore.codex.data.CodexUnlockRules;
import dev.baranhan.viltrumitecore.codex.data.UnlockCondition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server side of the encyclopedia: per-player progress saved in the persisted Forge player data
 * ({@code Player.PERSISTED_NBT_TAG} -> {@code ViltrumiteCodex}, copied to the respawned body and saved with
 * the player, like {@link dev.baranhan.viltrumitecore.hero.PersistentCooldowns}). Only the owner ever
 * receives his own record ({@link CodexNetwork}), so one player's discoveries never show up for another.
 *
 * <p>Public API for other systems: {@link #discoverInteraction(ServerPlayer, String)} when an interaction
 * really fired for that player; {@link #unlock(ServerPlayer, String)} / {@link #unlockMatching} for Codex
 * 2.0 entries (a toast is shown and {@link CodexUnlockEvent} is posted for every newly opened entry).
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CodexProgress {
   public static final String KEY = "ViltrumiteCodex";
   static final String HEROES = "Heroes";
   static final String INTERACTIONS = "Interactions";
   static final String ENTRIES = "Entries";
   static final String TUTORIALS = "Tutorials";
   /** At most this many toasts per burst (an op's unlockall must not flood the screen). */
   static final int MAX_TOASTS = 4;

   private CodexProgress() {
   }

   public static CodexRecord read(Player player) {
      CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
      return fromTag(persisted.getCompound(KEY));
   }

   static CodexRecord fromTag(CompoundTag tag) {
      CodexRecord record = new CodexRecord();
      readList(tag, HEROES).forEach(record::addHero);
      readList(tag, INTERACTIONS).forEach(record::addInteraction);
      readList(tag, ENTRIES).forEach(record::addEntry);
      readList(tag, TUTORIALS).forEach(record::addTutorial);
      return record;
   }

   private static List<String> readList(CompoundTag tag, String key) {
      ListTag list = tag.getList(key, Tag.TAG_STRING);
      List<String> out = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
         out.add(list.getString(i));
      }
      return out;
   }

   private static ListTag writeList(Collection<String> values) {
      ListTag list = new ListTag();
      values.forEach(v -> list.add(StringTag.valueOf(v)));
      return list;
   }

   static CompoundTag toTag(CodexRecord record) {
      CompoundTag tag = new CompoundTag();
      tag.put(HEROES, writeList(record.heroes()));
      tag.put(INTERACTIONS, writeList(record.interactions()));
      tag.put(ENTRIES, writeList(record.entries()));
      tag.put(TUTORIALS, writeList(record.tutorials()));
      return tag;
   }

   static void write(ServerPlayer player, CodexRecord record) {
      CompoundTag data = player.getPersistentData();
      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      persisted.put(KEY, toTag(record));
      data.put(Player.PERSISTED_NBT_TAG, persisted);
   }

   /** The id a hero is saved as (the legacy Viltrumite id loads as Homelander), or null. */
   @Nullable
   public static HeroId canonical(@Nullable HeroId hero) {
      return hero == null ? null : HeroId.fromKey(hero.key());
   }

   /**
    * The player has really become this hero (HeroRegistry.changeHero). The hero's wish card gets its icon,
    * and Codex 2.0 entries waiting for this hero announce themselves.
    */
   public static void heroChosen(ServerPlayer player, @Nullable HeroId hero) {
      hero = canonical(hero);
      if (hero == null || !isRealPlayer(player)) {
         return;
      }
      CodexRecord record = read(player);
      List<CodexEntryDef> waiting = CodexUnlockRules.newlyUnlocked(CodexData.get().entries().values(), UnlockCondition.Type.HERO_CHOSEN,
         hero.key(), record);
      if (record.addHero(hero.key())) {
         write(player, record);
         CodexNetwork.sync(player, record);
         announce(player, waiting);
      }
   }

   /**
    * A contextual interaction fired for this player: it is now open in his encyclopedia. Unknown or
    * malformed ids are ignored. Returns true when it was a new discovery.
    */
   public static boolean discoverInteraction(ServerPlayer player, String interactionId) {
      CodexRecord record = read(player);
      List<CodexEntryDef> waiting = CodexUnlockRules.newlyUnlocked(CodexData.get().entries().values(), UnlockCondition.Type.INTERACTION,
         interactionId, record);
      if (!record.addInteraction(interactionId)) {
         return false;
      }
      write(player, record);
      CodexNetwork.sync(player, record);
      announce(player, waiting);
      return true;
   }

   public static boolean forgetInteraction(ServerPlayer player, String interactionId) {
      CodexRecord record = read(player);
      if (!record.removeInteraction(interactionId)) {
         return false;
      }
      write(player, record);
      CodexNetwork.sync(player, record);
      return true;
   }

   /** Opens one Codex 2.0 entry (known ids only). True when it was newly opened. */
   public static boolean unlock(ServerPlayer player, String entryId) {
      CodexEntryDef entry = CodexData.get().entry(entryId);
      return entry != null && unlockAll(player, List.of(entry)) > 0;
   }

   /** Opens every given entry that is still closed; one save, one sync. Returns how many opened. */
   public static int unlockAll(ServerPlayer player, Collection<CodexEntryDef> entries) {
      if (!isRealPlayer(player) || entries.isEmpty()) {
         return 0;
      }
      CodexRecord record = read(player);
      List<CodexEntryDef> opened = new ArrayList<>();
      for (CodexEntryDef entry : entries) {
         if (!CodexUnlockRules.isUnlocked(entry, record) && record.addEntry(entry.id())) {
            opened.add(entry);
         }
      }
      if (opened.isEmpty()) {
         return 0;
      }
      write(player, record);
      CodexNetwork.sync(player, record);
      announce(player, opened);
      return opened.size();
   }

   /** A gameplay fact happened for this player (killed / saw / entered / event): open what waited for it. */
   public static int unlockMatching(ServerPlayer player, UnlockCondition.Type type, String value) {
      if (!isRealPlayer(player)) {
         return 0;
      }
      List<CodexEntryDef> waiting = CodexUnlockRules.newlyUnlocked(CodexData.get().entries().values(), type, value, read(player));
      return waiting.isEmpty() ? 0 : unlockAll(player, waiting);
   }

   /** Op/QA: closes an explicitly opened entry again. */
   public static boolean lock(ServerPlayer player, String entryId) {
      CodexRecord record = read(player);
      if (!record.removeEntry(entryId)) {
         return false;
      }
      write(player, record);
      CodexNetwork.sync(player, record);
      return true;
   }

   /** The client finished (or skipped) the hero's first-time tutorial; it is never offered again. */
   public static void tutorialDone(ServerPlayer player, String hero) {
      CodexRecord record = read(player);
      if (record.addTutorial(hero)) {
         write(player, record);
         CodexNetwork.sync(player, record);
      }
   }

   private static void announce(ServerPlayer player, List<CodexEntryDef> opened) {
      int toasts = 0;
      for (CodexEntryDef entry : opened) {
         if (toasts++ < MAX_TOASTS) {
            CodexNetwork.unlocked(player, entry.id());
         }
         MinecraftForge.EVENT_BUS.post(new CodexUnlockEvent(player, entry.id()));
      }
   }

   /** Op reset (QA): forgets every discovery, entry, tutorial and chosen hero except the current one. */
   public static void reset(ServerPlayer player) {
      CodexRecord record = new CodexRecord();
      write(player, record);
      migrateCurrentHero(player, record);
      CodexNetwork.sync(player, read(player));
   }

   /**
    * Players from before the encyclopedia: the hero they wear now was chosen by them, so it counts. A Human
    * by default never chose anything (the human card gets its icon only after the return wish).
    */
   private static void migrateCurrentHero(ServerPlayer player, CodexRecord record) {
      HeroId current = player instanceof HeroPlayer hero ? canonical(hero.getHeroId()) : null;
      if (current != null && current != HeroId.HUMAN) {
         if (record.addHero(current.key())) {
            write(player, record);
         }
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && isRealPlayer(player)) {
         CodexRecord record = read(player);
         migrateCurrentHero(player, record);
         CodexNetwork.sync(player, read(player));
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && isRealPlayer(player)) {
         CodexNetwork.sync(player, read(player));
      }
   }

   @SubscribeEvent
   public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && isRealPlayer(player)) {
         CodexNetwork.sync(player, read(player));
         unlockMatching(player, UnlockCondition.Type.DIMENSION, event.getTo().location().toString());
      }
   }

   /** Squad NPCs are ServerPlayers with a dummy connection: they have no encyclopedia. */
   public static boolean isRealPlayer(ServerPlayer player) {
      return !(player instanceof dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer);
   }
}
