package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.baranhan.viltrumitecore.hero.HeroAirJump;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Adaptation;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.ArrowWard;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.CloudStep;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Detox;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.ManaAbsorption;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Parry;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Phoenix;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.WindReading;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

/** Spec §3.1 last line: every blessing works the same in every mode. */
class BlessingModeMatrixTest {
   private static final ResourceLocation DIVINE = new ResourceLocation("viltrumitecore", "divine");

   /** Every pure blessing decision, for a state in {@code mode}. */
   private static List<Object> decisions(ReinhardMode mode) {
      ReinhardState state = new ReinhardState();
      state.mode = mode;
      List<Object> out = new ArrayList<>();
      Vec2 look = Parry.lookOf(0.0F);
      out.add(Parry.isMelee(true, false, false, false));
      out.add(Parry.inFront(look, new Vec2(0.0F, 3.0F)));
      out.add(Parry.inFront(look, new Vec2(0.0F, -3.0F)));
      out.add(ArrowWard.response(false, false));
      out.add(ReinhardHero.INSTANCE.projectileResponse(null, null));
      out.add(Detox.blocks(MobEffectCategory.HARMFUL, false));
      out.add(Detox.blocks(MobEffectCategory.BENEFICIAL, false));
      out.add(ManaAbsorption.factor(false, 100.0, false));
      Adaptation adaptation = state.adaptation;
      for (int i = 0; i < 4; i++) {
         ReinhardHero.DivineStep step = ReinhardHero.divineStep(adaptation, DIVINE);
         out.add(step.result());
         adaptation = step.next();
      }

      out.add(Phoenix.saves(true, 5.0F, 10.0F, 0));
      out.add(Phoenix.saves(true, 5.0F, 10.0F, 1));
      out.add(CloudStep.steps());
      out.add(ReinhardHero.INSTANCE.airJumps(null));
      out.add(ReinhardHero.INSTANCE.superJumpVelocity(null));
      out.add(WindReading.isMarked(false, true, true));
      out.add(WindReading.isMarked(true, false, false));
      out.add(ReinhardHero.absorb(false, 100.0F).passOn());
      out.add(ReinhardHero.clamp(false, 5.0F, 100.0F));
      return out;
   }

   @Test
   void everyBlessingIgnoresMode() {
      List<Object> restrained = decisions(ReinhardMode.RESTRAINED);
      for (ReinhardMode mode : ReinhardMode.values()) {
         assertEquals(restrained, decisions(mode), mode.name());
      }
   }

   @Test
   void noBlessingRuleTakesTheMode() {
      Class<?>[] blessings = {Adaptation.class, ArrowWard.class, CloudStep.class, Detox.class, ManaAbsorption.class, Parry.class, Phoenix.class,
         WindReading.class, HeroAirJump.class};
      for (Class<?> blessing : blessings) {
         for (Method method : blessing.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
               assertNotEquals(ReinhardMode.class, parameter, blessing.getSimpleName() + "." + method.getName());
            }
         }
      }
   }
}
