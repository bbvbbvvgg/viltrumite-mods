package dev.baranhan.viltrumitecore.hero.secret;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Secret abilities (easter eggs) for a future gallery / encyclopedia: each one documents its
 * exact trigger, valid targets and effect (lang keys), and a player's discovery is saved in the
 * persisted player data (survives death). Nothing here is shown anywhere by itself: a gallery
 * must list a secret only after {@link #discovered}. Contract: docs/design/2026-10-31-reinhard-final-punch.md.
 */
public final class HeroSecrets {
   /** NBT under {@code Player.PERSISTED_NBT_TAG}: secret id → game time of the first discovery. */
   public static final String NBT_KEY = "viltrumitecore:secrets";

   /**
    * One secret. {@code hero} is the HeroId key, {@code trigger} a stable machine id of the input
    * ("shift+ability_6"), the lang keys give its name / trigger / targets / effect for a gallery.
    */
   public record Secret(String id, String hero, String trigger, String nameKey, String triggerKey, String targetsKey, String effectKey) {
   }

   private static final Map<String, Secret> SECRETS = new LinkedHashMap<>();

   private HeroSecrets() {
   }

   public static Secret register(Secret secret) {
      if (SECRETS.putIfAbsent(secret.id(), secret) != null) {
         throw new IllegalStateException("Duplicate secret " + secret.id());
      }
      return secret;
   }

   @Nullable
   public static Secret get(String id) {
      return SECRETS.get(id);
   }

   public static List<Secret> all() {
      return Collections.unmodifiableList(List.copyOf(SECRETS.values()));
   }

   /** Server: marks the secret found (first time only). True = newly discovered. */
   public static boolean discover(Player player, String id, long gameTime) {
      CompoundTag secrets = tag(player, true);
      if (secrets.contains(id)) return false;
      secrets.putLong(id, gameTime);
      return true;
   }

   public static boolean discovered(Player player, String id) {
      CompoundTag secrets = tag(player, false);
      return secrets != null && secrets.contains(id);
   }

   @Nullable
   private static CompoundTag tag(Player player, boolean create) {
      CompoundTag data = player.getPersistentData();
      if (!data.contains(Player.PERSISTED_NBT_TAG, 10)) {
         if (!create) return null;
         data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
      }
      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      if (!persisted.contains(NBT_KEY, 10)) {
         if (!create) return null;
         persisted.put(NBT_KEY, new CompoundTag());
      }
      return persisted.getCompound(NBT_KEY);
   }
}
