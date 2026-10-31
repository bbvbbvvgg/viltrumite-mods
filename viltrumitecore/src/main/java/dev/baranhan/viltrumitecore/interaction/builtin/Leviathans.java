package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerAction;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.entity.chitauri.LeviathanPart;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared checks of the interactions around the Chitauri Leviathan and lightning. */
final class Leviathans {
   private Leviathans() {
   }

   /** The living Leviathan behind a hit entity (the body or one of its segments), else null. */
   @Nullable
   static ChitauriLeviathan of(@Nullable Entity e) {
      Entity body = e instanceof LeviathanPart part ? part.getParent() : e;
      return body instanceof ChitauriLeviathan lev && lev.isAlive() && lev.state() == ChitauriLeviathan.ALIVE ? lev : null;
   }

   /** Thunder in the blow: a lightning damage type, or a lightning weapon in its striking window. */
   static boolean lightning(InteractionContext ctx) {
      return lightning(ctx.source() != null && ctx.source().is(DamageTypeTags.IS_LIGHTNING), ctx.attackerPresence()) || npcThunder(ctx.attacker());
   }

   /** Pure rule (tests). */
   static boolean lightning(boolean lightningType, CombatPresence striker) {
      return lightningType || striker.has(CombatTags.ELEMENT_LIGHTNING) && striker.stance() == CombatStance.ACTIVE;
   }

   /** The summoned Thor while his hammer throw or his storm is running. */
   static boolean npcThunder(@Nullable Entity attacker) {
      return attacker instanceof AvengerEntity a && npcThunder(a.kind(), a.action());
   }

   static boolean npcThunder(AvengerKind kind, AvengerAction action) {
      return kind == AvengerKind.THOR && (action == AvengerAction.SPECIAL_1 || action == AvengerAction.SPECIAL_2);
   }

   /** Chitauri foot soldiers and chariots near the body (never the Leviathan itself), nearest the body first. */
   static List<LivingEntity> riders(ChitauriLeviathan lev, double reach, int max) {
      List<Vec3> spine = spine(lev);
      List<LivingEntity> out = new ArrayList<>();
      AABB box = lev.getBoundingBoxForCulling().inflate(reach);
      for (LivingEntity e : lev.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != lev && e.isAlive() && e instanceof ChitauriMob)) {
         if (out.size() >= max) {
            break;
         }
         for (Vec3 p : spine) {
            if (e.getBoundingBox().distanceToSqr(p) <= reach * reach) {
               out.add(e);
               break;
            }
         }
      }
      return out;
   }

   /** Centres of the body segments, head first. */
   static List<Vec3> spine(ChitauriLeviathan lev) {
      List<Vec3> out = new ArrayList<>();
      for (LeviathanPart part : lev.parts()) {
         out.add(part.getBoundingBox().getCenter());
      }
      if (out.isEmpty()) {
         out.add(lev.centre());
      }
      return out;
   }

   /** The segment nearest a point (where a fist lands). */
   static Vec3 nearest(ChitauriLeviathan lev, Vec3 to) {
      Vec3 best = lev.centre();
      double d = Double.MAX_VALUE;
      for (Vec3 p : spine(lev)) {
         double q = p.distanceToSqr(to);
         if (q < d) {
            d = q;
            best = p;
         }
      }
      return best;
   }

   /** The attacker's own melee damage source for a side hit (players: player attack, mobs: mob attack). */
   @Nullable
   static DamageSource sourceOf(@Nullable Entity attacker) {
      if (attacker instanceof Player p) {
         return p.damageSources().playerAttack(p);
      }
      return attacker instanceof LivingEntity l ? l.damageSources().mobAttack(l) : null;
   }
}
