package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.ArrowWard;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Divine Protection of Arrow Protection (spec §4.3). */
class ArrowWardTest {
   @Test
   void priority() {
      assertEquals(ProjectileResponse.DEFLECT, ArrowWard.response(false, false));
      assertEquals(ProjectileResponse.CATCH, ArrowWard.response(true, false));
      assertEquals(ProjectileResponse.REFLECT, ArrowWard.response(false, true));
      assertEquals(ProjectileResponse.REFLECT, ArrowWard.response(true, true));
   }

   @Test
   void allProjectileKinds() throws Exception {
      // The rule only takes the two windows: no projectile class can change it.
      Method rule = ArrowWard.class.getMethod("response", boolean.class, boolean.class);
      assertEquals(2, rule.getParameterCount());
      // The hero hook ignores the projectile entirely (null stands for any kind: arrow, trident, fireball, potion, spit).
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.INSTANCE.projectileResponse(null, null));
   }
}
