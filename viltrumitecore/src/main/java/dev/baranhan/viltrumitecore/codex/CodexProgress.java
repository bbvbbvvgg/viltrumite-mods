package dev.baranhan.viltrumitecore.codex;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server side of the encyclopedia (hero gallery): per-player progress saved in the persisted Forge player
 * data ({@code Player.PERSISTED_NBT_TAG} → {@code ViltrumiteCodex}, copied to the respawned body and saved
 * with the player, like {@link dev.baranhan.viltrumitecore.hero.PersistentCooldowns}). Only the owner ever
 * receives his own record ({@link CodexNetwork}), so one player's discoveries never show up for another.
 *
 * <p>Public API for other systems (contextual interactions):
 * {@link #discoverInteraction(ServerPlayer, String)} when an interaction really fired for that player.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CodexProgress {
   public static final String KEY = "ViltrumiteCodex";
   static final String HEROES = "Heroes";
   static final String INTERACTIONS = "Interactions";

   private CodexProgress() {
   }

   public static CodexRecord read(Player player) {
      CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
      return fromTag(persisted.getCompound(KEY));
   }

   static CodexRecord fromTag(CompoundTag tag) {
      CodexRecord record = new CodexRecord();
      ListTag heroes = tag.getList(HEROES, Tag.TAG_STRING);
      for (int i = 0; i < heroes.size(); i++) {
         record.addHero(heroes.getString(i));
      }
      ListTag found = tag.getList(INTERACTIONS, Tag.TAG_STRING);
      for (int i = 0; i < found.size(); i++) {
         record.addInteraction(found.getString(i));
      }
      return record;
   }

   static CompoundTag toTag(CodexRecord record) {
      CompoundTag tag = new CompoundTag();
      ListTag heroes = new ListTag();
      record.heroes().forEach(h -> heroes.add(StringTag.valueOf(h)));
      ListTag found = new ListTag();
      record.interactions().forEach(i -> found.add(StringTag.valueOf(i)));
      tag.put(HEROES, heroes);
      tag.put(INTERACTIONS, found);
      return tag;
   }

   static void write(ServerPlayer player, CodexRecord record) {
      CompoundTag data = player.getPersistentData();
      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      persisted.put(KEY, toTag(record));
      data.put(Player.PERSISTED_NBT_TAG, persisted);
   }

   /** The player has really become this hero (HeroRegistry.changeHero). The hero's wish card gets its icon. */
   public static void heroChosen(ServerPlayer player, @Nullable HeroId hero) {
      if (hero == null || hero == HeroId.VILTRUMITE || !isRealPlayer(player)) {
         return;
      }
      CodexRecord record = read(player);
      if (record.addHero(hero.key())) {
         write(player, record);
         CodexNetwork.sync(player, record);
      }
   }

   /**
    * A contextual interaction fired for this player: it is now open in his encyclopedia. Unknown or
    * malformed ids are ignored. Returns true when it was a new discovery.
    */
   public static boolean discoverInteraction(ServerPlayer player, String interactionId) {
      CodexRecord record = read(player);
      if (!record.addInteraction(interactionId)) {
         return false;
      }
      write(player, record);
      CodexNetwork.sync(player, record);
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

   /** Op reset (QA): forgets every discovery and every chosen hero except the current one. */
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
      if (player instanceof HeroPlayer hero && hero.getHeroId() != null && hero.getHeroId() != HeroId.HUMAN && hero.getHeroId() != HeroId.VILTRUMITE) {
         if (record.addHero(hero.getHeroId().key())) {
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
      }
   }

   /** Squad NPCs are ServerPlayers with a dummy connection: they have no encyclopedia. */
   static boolean isRealPlayer(ServerPlayer player) {
      return !(player instanceof dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer);
   }
}
