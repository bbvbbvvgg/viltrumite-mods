package dev.baranhan.viltrumitecore.hero.thor.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session B tuning: every number here is a PLACEHOLDER (not approved by the
 * author, spec "[ОТКРЫТО]"), editable in {@code config/viltrumitecore-thor-combat.json}.
 * Server-side numbers only: move timings stay in {@link ThorCombatTimings}
 * because client clips and the server timeline must agree.
 */
public final class ThorCombatConfig {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/thor-combat");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String FILE = "viltrumitecore-thor-combat.json";
   private static volatile ThorCombatConfig instance = new ThorCombatConfig();

   // --- melee (PLACEHOLDER) ---
   /** Multiplier over the per-strike damage of ThorStrike. */
   public float meleeDamageScale = 1.0F;
   /** Ground slams crack a shallow crater of ordinary blocks (mobGriefing and protected blocks respected). */
   public boolean meleeBreaksBlocks = true;
   /** Extra lightning damage of an MMB strike in the electric mode. */
   public float electricStrikeBonus = 4.0F;

   // --- electric LMB discharge (PLACEHOLDER) ---
   public double dischargeRange = 16.0;
   public float dischargeDamage = 8.0F;
   public int chainJumps = 3;
   public double chainRadius = 6.0;
   public float chainFalloff = 0.7F;

   // --- electric RMB wave (PLACEHOLDER) ---
   public double waveRadius = 6.0;
   public float waveDamage = 7.0F;
   public double waveKnockback = 1.2;

   // --- held RMB storm (PLACEHOLDER) ---
   public double stormStartRadius = 3.0;
   public double stormMaxRadius = 16.0;
   public int stormPulseTicks = 5;
   public float stormPulseDamage = 3.0F;
   public int stormBoltTicks = 10;
   public float stormBurstDamage = 12.0F;
   public int stormCooldownTicks = 160;

   // --- six electric combos (PLACEHOLDER sequences, effects and numbers) ---
   /** Max ticks between two actions of one electric combo. */
   public int electricComboGapTicks = 30;
   public float electricComboDamageScale = 1.0F;

   // --- automatic sky lightning (PLACEHOLDER; target choice OPEN #75/#130/#149) ---
   public SkyTargetPolicy skyTargetPolicy = SkyTargetPolicy.LAST_PRIMARY_HIT;
   public int skyStrikeDelayTicks = 8;
   public float skyStrikeDamage = 14.0F;
   public double skyStrikeSplashRadius = 2.5;
   /** Ordinary blocks above the target the bolt passes through (carved, protected blocks spared). */
   public int skyColumnHeight = 24;
   public double skyMaxDistance = 64.0;

   // --- ultimate (PLACEHOLDER; key, scale, damage, cooldown, hammer rule OPEN) ---
   public double wrathRange = 64.0;
   public double wrathCoreRadius = 2.5;
   public double wrathRadius = 7.0;
   public float wrathDamage = 40.0F;
   public int wrathCraterRadius = 4;
   public int wrathCooldownTicks = 600;
   /** OPEN: whether the ultimate needs Mjölnir in the hand (not approved either way). */
   public boolean wrathRequiresHammer = false;

   public static ThorCombatConfig get() {
      return instance;
   }

   /** Tests: install a custom tuning (null = defaults). */
   public static void set(ThorCombatConfig config) {
      instance = config == null ? new ThorCombatConfig() : config;
   }

   /** Reads the file (writes the defaults when missing); a broken file keeps the defaults. */
   public static void load() {
      File file = new File(FMLPaths.CONFIGDIR.get().toFile(), FILE);
      if (file.exists()) {
         try (Reader reader = new FileReader(file)) {
            ThorCombatConfig loaded = GSON.fromJson(reader, ThorCombatConfig.class);
            if (loaded != null) {
               if (loaded.skyTargetPolicy == null) {
                  loaded.skyTargetPolicy = SkyTargetPolicy.LAST_PRIMARY_HIT;
               }

               instance = loaded;
            }
         } catch (Exception e) {
            LOGGER.warn("Bad {}: defaults kept", FILE, e);
         }
      }

      try (Writer writer = new FileWriter(file)) {
         GSON.toJson(instance, writer);
      } catch (Exception e) {
         LOGGER.warn("Cannot write {}", FILE, e);
      }
   }
}
