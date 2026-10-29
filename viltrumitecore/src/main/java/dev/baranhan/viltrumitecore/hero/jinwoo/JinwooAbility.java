package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import javax.annotation.Nullable;

/**
 * Jinwoo's panel abilities (spec §5). Page 1 = combat, page 2 = army. The ordinal
 * is the {@code extraCooldowns} index of the public snapshot.
 */
public enum JinwooAbility {
   SHADOW_STEP("shadow_step", HeroAction.STEP_DASH, JinwooRules.STEP_COOLDOWN, JinwooRules.STEP_MANA),
   MUTILATION("mutilation", HeroAction.DAGGER_RUSH, JinwooRules.RUSH_COOLDOWN, JinwooRules.RUSH_MANA),
   AUTHORITY("rulers_authority", HeroAction.GRAB_THROW, JinwooRules.AUTHORITY_COOLDOWN, JinwooRules.AUTHORITY_MANA),
   EXTRACTION("extraction", HeroAction.SHADOW_EXTRACTION, JinwooRules.EXTRACT_COOLDOWN, JinwooRules.EXTRACT_MANA),
   EXCHANGE("shadow_exchange", HeroAction.DASH_BEHIND, JinwooRules.EXCHANGE_COOLDOWN, JinwooRules.EXCHANGE_MANA),
   MONARCH("arise", HeroAction.MONARCH_DOMAIN, JinwooRules.ULT_COOLDOWN, JinwooRules.ULT_MANA),
   SUMMON("army_summon", HeroAction.ARMY_SUMMON, JinwooRules.SUMMON_COOLDOWN, 0.0F),
   ATTACK("army_attack", HeroAction.ARMY_ATTACK, JinwooRules.COMMAND_COOLDOWN, 0.0F),
   GUARD("army_guard", HeroAction.ARMY_GUARD, JinwooRules.COMMAND_COOLDOWN, 0.0F),
   SENTINEL("army_sentinel", HeroAction.ARMY_SENTINEL, JinwooRules.COMMAND_COOLDOWN, 0.0F),
   CHARGE("monarch_charge", HeroAction.MONARCH_CHARGE, JinwooRules.CHARGE_COOLDOWN, JinwooRules.CHARGE_MANA);

   /** Extra cooldown index of the block (not a slot). */
   public static final int BLOCK_CD = 11;
   public static final int COOLDOWNS = 12;

   private final String key;
   private final HeroAction action;
   public final int cooldown;
   public final float mana;

   JinwooAbility(String key, HeroAction action, int cooldown, float mana) {
      this.key = key;
      this.action = action;
      this.cooldown = cooldown;
      this.mana = mana;
   }

   public String key() {
      return this.key;
   }

   public String id() {
      return "jinwoo:" + this.key;
   }

   public HeroAction action() {
      return this.action;
   }

   @Nullable
   public static JinwooAbility byId(@Nullable String id) {
      if (id == null) {
         return null;
      }
      for (JinwooAbility ability : values()) {
         if (ability.id().equals(id)) {
            return ability;
         }
      }
      return null;
   }

   @Nullable
   public static JinwooAbility byAction(HeroAction action) {
      for (JinwooAbility ability : values()) {
         if (ability.action == action) {
            return ability;
         }
      }
      return null;
   }
}
