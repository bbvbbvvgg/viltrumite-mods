package dev.baranhan.viltrumitecore.hero;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

/** Narrow metadata for non-entity attacks whose legacy source is playerAttack.
 * The same hurt call, source, damage, cadence and return value are preserved. */
public final class HeroAttackContext {
   public enum Kind { SHOT, WAVE }
   private static final ThreadLocal<Kind> CURRENT=new ThreadLocal<>();
   private HeroAttackContext() {}
   public static Kind current() {return CURRENT.get();}
   public static boolean hurt(LivingEntity target,DamageSource source,float amount,Kind kind) {
      Kind previous=CURRENT.get();CURRENT.set(kind);
      try {return target.hurt(source,amount);}finally {if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
   }
}
