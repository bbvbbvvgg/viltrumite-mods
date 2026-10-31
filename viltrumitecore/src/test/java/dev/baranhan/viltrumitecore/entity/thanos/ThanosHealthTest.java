package dev.baranhan.viltrumitecore.entity.thanos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.mixin.ThanosMaxHealthMixin;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Found in a real game: "/thanos status" showed 1024/1024 instead of 3000 because vanilla clamps
 * the max-health attribute at 1024 (and getMaxHealth() is final). Thanos keeps his max in synced
 * data and ThanosMaxHealthMixin answers getMaxHealth() from it; these checks keep that wired.
 */
class ThanosHealthTest {
   @BeforeAll
   static void boot() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void vanillaClampsTheAttributeBelowHisHealth() throws NoSuchMethodException {
      assertEquals(1024.0, Attributes.MAX_HEALTH.sanitizeValue(ThanosRules.maxHealth(1)), 1.0E-9);
      assertEquals(3000.0F, ThanosRules.maxHealth(1), 1.0E-3F);
      assertTrue(ThanosRules.maxHealth(4) > 1024.0F);
      assertTrue(Modifier.isFinal(LivingEntity.class.getMethod("getMaxHealth").getModifiers()), "no override possible: the mixin is needed");
   }

   @Test
   void theMixinAnswersHisOwnMax() throws Exception {
      Method own = ThanosEntity.class.getDeclaredMethod("thanosMaxHealth");
      assertEquals(float.class, own.getReturnType());
      Method hook = Arrays.stream(ThanosMaxHealthMixin.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Inject.class)).findFirst().orElseThrow();
      assertEquals("getMaxHealth", hook.getAnnotation(Inject.class).method()[0]);
      assertTrue(hook.getAnnotation(Inject.class).cancellable());
      try (var in = ThanosHealthTest.class.getClassLoader().getResourceAsStream("viltrumitecore.mixins.json")) {
         JsonArray mixins = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("mixins");
         assertTrue(mixins.toString().contains("\"ThanosMaxHealthMixin\""), "registered as a common mixin");
      }
   }
}
