package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorFx;
import dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** R4: Thor's FX presets are named, unique, all have a client recipe, and are sized for the 3.2-block Thor. */
class ThorFxTest {
   @Test
   void presetIdsAreUniqueAndNamespaced() {
      Set<String> ids = new HashSet<>();
      Set<Integer> nets = new HashSet<>();
      for (FxPreset preset : ThorFx.PRESETS) {
         assertTrue(preset.id().startsWith("thor:"), preset.id());
         assertTrue(ids.add(preset.id()), "duplicate id " + preset.id());
         assertTrue(nets.add(preset.netId()), "duplicate net id " + preset.netId());
         assertSame(preset, FxPreset.byId(preset.id()));
         assertSame(preset, FxPreset.byNetId(preset.netId()));
      }

      for (String state : ThorFx.STATE_FX) {
         assertTrue(state.startsWith("thor:") && !ids.contains(state), state);
      }

      assertThrows(IllegalStateException.class, () -> FxPreset.register("thor:bolt", 999), "an id is taken once");
      assertThrows(IllegalStateException.class, () -> FxPreset.register("thor:other", ThorFx.BOLT.netId()), "a net id is taken once");
   }

   @Test
   void everyPresetHasExactlyOneRecipe() {
      List<FxRecipe> recipes = ThorFxRecipes.all();
      assertEquals(ThorFx.PRESETS.stream().map(FxPreset::id).collect(Collectors.toSet()),
         recipes.stream().map(r -> r.preset().id()).collect(Collectors.toSet()));
      assertEquals(ThorFx.PRESETS.size(), recipes.size());
      ThorFxRecipes.init();
      for (FxPreset preset : ThorFx.PRESETS) {
         assertNotNull(FxRecipe.byNetId(preset.netId()), preset.id());
      }
   }

   @Test
   void lightningIsLayeredAndThick() {
      for (FxRecipe recipe : List.of(ThorFxRecipes.bolt(), ThorFxRecipes.boltHeavy(), ThorFxRecipes.wrathStrike())) {
         String id = recipe.preset().id();
         FxRecipe.Layer core = recipe.of(FxRecipe.Kind.BOLT).get(0);
         assertTrue(core.width() * FxRecipe.BOLT_CORE_HALF_WIDTH * 2.0F >= 0.25F, id + ": the core is at least a quarter block wide for Thor");
         assertTrue(core.life() >= 40, id + ": the bolt lingers (re-strikes, afterglow), was " + core.life());
         for (FxRecipe.Kind kind : new FxRecipe.Kind[]{FxRecipe.Kind.SKY_FLASH, FxRecipe.Kind.LIGHT_POOL, FxRecipe.Kind.GROUND_CRAWL,
            FxRecipe.Kind.SCORCH, FxRecipe.Kind.CRACKLE, FxRecipe.Kind.SHAKE}) {
            assertFalse(recipe.of(kind).isEmpty(), id + " has " + kind);
         }

         assertTrue(core.count() >= 4, id + " branches");
      }
   }

   @Test
   void wrathStrikeHasALongTail() {
      FxRecipe wrath = ThorFxRecipes.wrathStrike();
      assertTrue(wrath.length() >= 60, "the ultimate's strike tail lasts at least 3 s, was " + wrath.length());
      assertTrue(wrath.of(FxRecipe.Kind.BOLT).size() >= 5, "main bolt, satellites and aftershocks");
      assertTrue(wrath.of(FxRecipe.Kind.SHAKE).stream().anyMatch(l -> l.power() >= 2.0F), "a heavy shake on contact");
      assertTrue(wrath.of(FxRecipe.Kind.SCREEN_FLASH).size() == 1);
      assertTrue(ThorFxRecipes.boltHeavy().length() > ThorFxRecipes.bolt().length());
   }

   @Test
   void impactsHaveDustDebrisShockwaveAndShake() {
      for (FxRecipe recipe : List.of(ThorFxRecipes.impactHammer(), ThorFxRecipes.impactHammerHeavy(), ThorFxRecipes.impactGround())) {
         String id = recipe.preset().id();
         assertFalse(recipe.of(FxRecipe.Kind.DUST).isEmpty(), id);
         assertFalse(recipe.of(FxRecipe.Kind.SHOCKWAVE).isEmpty(), id);
         assertFalse(recipe.of(FxRecipe.Kind.SHAKE).isEmpty(), id);
      }

      assertFalse(ThorFxRecipes.impactHammerHeavy().of(FxRecipe.Kind.DEBRIS).isEmpty());
      assertFalse(ThorFxRecipes.impactFist().of(FxRecipe.Kind.SHAKE).isEmpty());
   }

   @Test
   void presetPacketRoundTrips() {
      Vec3 at = new Vec3(10.5, 64.0, -3.25);
      Vec3 target = new Vec3(11.0, 66.0, -2.0);
      HeroFxS2CPacket sent = HeroFx.presetPacket(ThorFx.WRATH_STRIKE, at, target, 1.5F, 0, 1234, 5, 77, 70000);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      sent.encode(buf);
      HeroFxS2CPacket read = new HeroFxS2CPacket(buf);
      assertEquals(HeroFxS2CPacket.PRESET, read.kind);
      assertEquals(at, read.origin);
      assertEquals(1.5F, read.power);
      assertEquals(ThorFx.WRATH_STRIKE.netId(), Math.round(read.points[0]));
      assertEquals(1234, Math.round(read.points[1]));
      assertEquals(5, Math.round(read.points[2]));
      assertEquals(new Vec3(target.x, target.y, target.z), new Vec3(read.points[3], read.points[4], read.points[5]));
      int[] ids = HeroFx.decodeIds(java.util.Arrays.copyOfRange(read.points, 6, read.points.length));
      assertEquals(List.of(77, 70000), java.util.Arrays.stream(ids).boxed().toList());
   }

   @Test
   void hostilePresetValuesAreClamped() {
      HeroFxS2CPacket packet = HeroFx.presetPacket(ThorFx.BOLT, Vec3.ZERO, null, 1.0E9F, 0, 1, 1000);
      assertEquals(HeroFx.PRESET_MAX_SCALE, packet.power);
      assertEquals(HeroFx.PRESET_MAX_HIT_STOP, Math.round(packet.points[2]));
      assertEquals(1.0F, HeroFx.clampPresetScale(Float.NaN));
      int[] many = new int[200];
      assertTrue(HeroFx.presetPacket(ThorFx.BOLT, Vec3.ZERO, null, 1.0F, 0, 1, 2, many).points.length <= 64 * 3);
   }

   @Test
   void wrathStrikeIsOnePreset() throws Exception {
      String wrath = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/combat/WrathMove.java"));
      assertTrue(wrath.contains("ThorFx.WRATH_STRIKE"));
      assertFalse(wrath.contains("ElectricFx.bolt("), "the strike's bolts live in the thor:wrath_strike recipe");
   }
}
