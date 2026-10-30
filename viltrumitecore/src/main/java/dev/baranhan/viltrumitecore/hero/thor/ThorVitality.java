package dev.baranhan.viltrumitecore.hero.thor;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Audit v3 (item 4): the god's body. Thor had the vanilla 20 HP and no armour while every other melee hero
 * carries a chassis (Jinwoo / DIO +20 HP and 6 armour, Homelander +20 and 10). Thor is the heavy one: more
 * health than the duelists, their armour plus toughness (heavy hits hurt him less), and a body that is hard
 * to push around; his defence beyond that is the guard and parry (ThorGuard), not a bigger number.
 * Permanent attribute modifiers, re-applied on entry, respawn and once a second (idempotent), removed when
 * he stops being Thor.
 */
public final class ThorVitality {
   public static final double BONUS_MAX_HEALTH = 24.0;
   public static final double BONUS_ARMOR = 8.0;
   public static final double BONUS_TOUGHNESS = 4.0;
   public static final double BONUS_KNOCKBACK_RESISTANCE = 0.5;
   private static final UUID HEALTH_ID = UUID.fromString("7c1f3e2a-6b4d-4f8e-9a31-5d7e00000011");
   private static final UUID ARMOR_ID = UUID.fromString("7c1f3e2a-6b4d-4f8e-9a31-5d7e00000012");
   private static final UUID TOUGHNESS_ID = UUID.fromString("7c1f3e2a-6b4d-4f8e-9a31-5d7e00000013");
   private static final UUID KNOCKBACK_ID = UUID.fromString("7c1f3e2a-6b4d-4f8e-9a31-5d7e00000014");

   private ThorVitality() {
   }

   public static void apply(ServerPlayer player) {
      add(player, Attributes.MAX_HEALTH, HEALTH_ID, "Thor vitality", BONUS_MAX_HEALTH);
      add(player, Attributes.ARMOR, ARMOR_ID, "Thor armour", BONUS_ARMOR);
      add(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Thor toughness", BONUS_TOUGHNESS);
      add(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Thor weight", BONUS_KNOCKBACK_RESISTANCE);
   }

   public static void remove(ServerPlayer player) {
      remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
      remove(player, Attributes.ARMOR, ARMOR_ID);
      remove(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
      remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   private static void add(ServerPlayer player, Attribute attribute, UUID id, String name, double amount) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
      }
   }

   private static void remove(ServerPlayer player, Attribute attribute, UUID id) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null) {
         instance.removeModifier(id);
      }
   }
}
