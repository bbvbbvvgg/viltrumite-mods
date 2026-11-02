package dev.baranhan.viltrumitecore.entity.directive;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Pure rules of the behaviour-control layer (no world access; unit-tested in DirectiveRulesTest). */
public final class DirectiveRules {
   /** No directive lasts longer than five minutes, whatever wrote it. */
   public static final int MAX_TICKS = 20 * 60 * 5;
   /** A body carries at most this many directives; the oldest one fades when a new one is written. */
   public static final int MAX_ACTIVE = 6;

   private DirectiveRules() {
   }

   /** The list without expired directives (a new list when anything was dropped, the same list otherwise). */
   public static List<Directive> prune(List<Directive> list, long gameTime) {
      boolean any = false;
      for (Directive d : list) {
         if (d.expired(gameTime)) {
            any = true;
            break;
         }
      }
      if (!any) {
         return list;
      }
      List<Directive> out = new ArrayList<>(list.size());
      for (Directive d : list) {
         if (!d.expired(gameTime)) {
            out.add(d);
         }
      }
      return out;
   }

   /**
    * Adds a directive: the expiry is capped at {@link #MAX_TICKS} from now, the same instruction of the same author
    * is replaced (rewriting refreshes, it never stacks), and beyond {@link #MAX_ACTIVE} the soonest-ending one fades.
    */
   public static List<Directive> merge(List<Directive> list, Directive added, long gameTime) {
      Directive capped = added.expiresAt() > gameTime + MAX_TICKS ? added.withExpiry(gameTime + MAX_TICKS) : added;
      List<Directive> out = new ArrayList<>(prune(list, gameTime));
      out.removeIf(d -> d.id().equals(capped.id()) && d.author().equals(capped.author()));
      out.add(capped);
      while (out.size() > MAX_ACTIVE) {
         Directive soonest = out.get(0);
         for (Directive d : out) {
            if (d.expiresAt() < soonest.expiresAt()) {
               soonest = d;
            }
         }
         out.remove(soonest);
      }
      return out;
   }

   public static int effects(List<Directive> list) {
      int m = 0;
      for (Directive d : list) {
         m |= d.effects();
      }
      return m;
   }

   /**
    * May a body with these directives pick this target?
    *
    * @param target   the target's UUID
    * @param peaceful the target is a villager, citizen or other bystander
    */
   public static boolean forbidsTarget(List<Directive> list, UUID target, boolean peaceful) {
      for (Directive d : list) {
         if (d.has(DirectiveEffect.CALM) || d.has(DirectiveEffect.OPEN)) {
            return true;
         }
         if ((d.has(DirectiveEffect.SAFETY_LOCK) || d.has(DirectiveEffect.RIPPED) || d.has(DirectiveEffect.FORGET_FACES)) && d.author().equals(target)) {
            return true;
         }
         if (d.has(DirectiveEffect.FORGET_TARGET) && target.equals(d.subject())) {
            return true;
         }
         if (d.has(DirectiveEffect.GUARD) && target.equals(d.subject())) {
            return true;
         }
         if (d.has(DirectiveEffect.SPARE_PEACEFUL) && peaceful) {
            return true;
         }
      }
      return false;
   }

   /** The kind of a hit, for the "cannot attack with ..." instructions. */
   public enum HitKind {
      MELEE,
      RANGED,
      ABILITY
   }

   /** May a body with these directives hurt this victim with this kind of hit? */
   public static boolean forbidsDamage(List<Directive> list, UUID victim, boolean peaceful, HitKind kind) {
      if (forbidsTarget(list, victim, peaceful)) {
         // Safety Lock and friends forbid the harm itself, not only the targeting
         return true;
      }
      for (Directive d : list) {
         if (kind == HitKind.MELEE && d.has(DirectiveEffect.NO_MELEE)
            || kind == HitKind.RANGED && d.has(DirectiveEffect.NO_RANGED)
            || kind == HitKind.ABILITY && d.has(DirectiveEffect.NO_ABILITIES)) {
            return true;
         }
      }
      return false;
   }

   /** Is this hero action sealed by any directive? */
   public static boolean seals(List<Directive> list, int actionOrdinal) {
      for (Directive d : list) {
         if (d.has(DirectiveEffect.OPEN) || d.seals(actionOrdinal)) {
            return true;
         }
      }
      return false;
   }

   /** What survives a hit of {@code amount} that leaves the body at {@code healthFractionAfter}. */
   public static List<Directive> afterHurt(List<Directive> list, @Nullable UUID attacker, float amount, float healthFractionAfter, float heavyHit,
      float lowHealth) {
      List<Directive> out = new ArrayList<>(list.size());
      for (Directive d : list) {
         boolean breaks = amount > 0.0F && d.flag(Directive.BREAK_ON_HURT)
            || attacker != null && attacker.equals(d.author()) && d.flag(Directive.BREAK_ON_AUTHOR_HIT)
            || d.flag(Directive.RESTORE_ON_HEAVY) && (amount >= heavyHit || healthFractionAfter < lowHealth);
         if (!breaks) {
            out.add(d);
         }
      }
      return out.size() == list.size() ? list : out;
   }

   public static ListTag save(List<Directive> list) {
      ListTag tag = new ListTag();
      for (Directive d : list) {
         tag.add(d.save());
      }
      return tag;
   }

   public static List<Directive> load(ListTag tag, long gameTime) {
      List<Directive> out = new ArrayList<>();
      for (int i = 0; i < tag.size(); i++) {
         Directive d = Directive.load(tag.getCompound(i));
         if (d != null && !d.expired(gameTime)) {
            // a corrupted or edited save can never hold a body longer than the cap
            out.add(d.expiresAt() > gameTime + MAX_TICKS ? d.withExpiry(gameTime + MAX_TICKS) : d);
         }
      }
      return out;
   }

   public static boolean isList(CompoundTag data, String key) {
      return data.contains(key, Tag.TAG_LIST);
   }
}
