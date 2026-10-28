package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

/** Server events of Jinwoo's systems: soul remnants on kills (spec §5.7) and their clock. */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE)
public final class JinwooEvents {
   private JinwooEvents() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity dead = event.getEntity();
      if (event.isCanceled() || !(dead.level() instanceof ServerLevel level) || dead instanceof ShadowSoldierEntity) {
         return;
      }
      ServerPlayer killer = jinwooOf(event.getSource().getEntity());
      if (killer == null || killer.level() != level) {
         return;
      }
      ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
      String typeId = key == null ? "" : key.getPath();
      boolean player = dead instanceof Player;
      ShadowKind kind = ShadowKind.classify(typeId, player, dead.getMobType() == MobType.ARTHROPOD, dead instanceof Enemy,
         dead instanceof Animal || dead instanceof net.minecraft.world.entity.npc.AbstractVillager);
      if (kind == null) {
         return;
      }
      boolean boss = dead instanceof net.minecraft.world.entity.boss.wither.WitherBoss || dead instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
      Remnants.add(level, dead.position(), kind, JinwooRules.tierOf(dead.getMaxHealth(), boss || player), killer.getUUID());
   }

   /** Jinwoo himself or the owner of the killing shadow. */
   static ServerPlayer jinwooOf(Entity source) {
      if (source instanceof ShadowSoldierEntity shadow) {
         source = shadow.owner();
      }
      if (source instanceof ServerPlayer p && p instanceof HeroPlayer hp && hp.getHeroId() == HeroId.JINWOO) {
         return p;
      }
      return null;
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
         Remnants.tick(level);
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      Remnants.clearAll();
      ShadowSoldierEntity.clearCounts();
   }
}
