package dev.baranhan.viltrumitecore.hero.sinclair;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Server timers of Sinclair's short debuffs (§5.1 Vexed, §5.5 Dazzled): transient speed modifiers that
 * remove themselves, and the mob "lost target" window. Transient = gone after a relog by design (§8).
 */
public final class Daze {
   public static final UUID VEX_ID = UUID.fromString("5c1a7f30-2b7e-4c55-9a31-7f0de1b0a101");
   public static final UUID DAZE_ID = UUID.fromString("5c1a7f30-2b7e-4c55-9a31-7f0de1b0a102");
   private static final Map<LivingEntity, Timers> ACTIVE = new WeakHashMap<>();

   private static final class Timers {
      final Map<UUID, Integer> slows = new HashMap<>();
      int blind;
   }

   private Daze() {
   }

   public static void slow(LivingEntity target, UUID id, String name, double amount, int ticks) {
      if (target.level().isClientSide) {
         return;
      }

      AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      if (speed.getModifier(id) == null) {
         speed.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
      }

      Timers timers = ACTIVE.computeIfAbsent(target, t -> new Timers());
      timers.slows.merge(id, ticks, Math::max);
   }

   public static void blind(LivingEntity target, int ticks) {
      if (target.level().isClientSide || !(target instanceof Mob mob)) {
         return;
      }

      ACTIVE.computeIfAbsent(target, t -> new Timers()).blind = Math.max(ticks, ACTIVE.get(target).blind);
      mob.setTarget(null);
   }

   /** True while a dazzled mob may not pick a target. */
   public static boolean blinded(LivingEntity target) {
      Timers timers = ACTIVE.get(target);
      return timers != null && timers.blind > 0;
   }

   /** Server, once per living tick (SinclairEvents). */
   public static void tick(LivingEntity target) {
      Timers timers = ACTIVE.get(target);
      if (timers == null) {
         return;
      }

      AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
      for (Iterator<Map.Entry<UUID, Integer>> it = timers.slows.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<UUID, Integer> entry = it.next();
         int left = entry.getValue() - 1;
         if (left <= 0 || !target.isAlive()) {
            if (speed != null) {
               speed.removeModifier(entry.getKey());
            }

            it.remove();
         } else {
            entry.setValue(left);
         }
      }

      if (timers.blind > 0) {
         timers.blind--;
         if (target instanceof Mob mob && mob.getTarget() != null) {
            mob.setTarget(null);
         }
      }

      if (timers.slows.isEmpty() && timers.blind <= 0) {
         ACTIVE.remove(target);
      }
   }

   /** Ends every timer (a level unloads, tests). */
   public static void clear(LivingEntity target) {
      Timers timers = ACTIVE.remove(target);
      AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
      if (timers != null && speed != null) {
         for (UUID id : timers.slows.keySet()) {
            speed.removeModifier(id);
         }
      }
   }
}
