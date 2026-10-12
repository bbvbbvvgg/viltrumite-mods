package dev.baranhan.viltrumitecore.client.render.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

class SoundDuckTest {
   private static final ResourceLocation OWN = new ResourceLocation("viltrumitecore", "reinhard.reid_rumble");
   private static final ResourceLocation OTHER = new ResourceLocation("minecraft", "entity.player.hurt");

   @Test
   void duckMinOfOwners() {
      assertEquals(1.0F, SoundDuck.factor(List.of()), 1.0E-6F);
      assertEquals(0.25F, SoundDuck.factor(List.of(0.5F, 0.25F, 0.8F)), 1.0E-6F);
      SoundDuck duck = new SoundDuck();
      duck.duck("a", SoundSource.MASTER, 0.5F, 0, Set.of());
      duck.duck("b", SoundSource.HOSTILE, 0.2F, 0, Set.of());
      assertEquals(0.2F, duck.volumeFactor(SoundSource.HOSTILE, OTHER), 1.0E-6F);
      assertEquals(0.5F, duck.volumeFactor(SoundSource.BLOCKS, OTHER), 1.0E-6F);
   }

   @Test
   void releaseRestoresOne() {
      SoundDuck duck = new SoundDuck();
      duck.duck("a", SoundSource.MASTER, 0.25F, 10, Set.of());
      for (int i = 0; i < 10; i++) {
         duck.tick();
      }
      assertEquals(0.25F, duck.volumeFactor(SoundSource.AMBIENT, OTHER), 1.0E-4F);
      duck.release("a");
      duck.tick();
      float mid = duck.volumeFactor(SoundSource.AMBIENT, OTHER);
      assertTrue(mid > 0.25F && mid < 1.0F, "fades back: " + mid);
      for (int i = 0; i < 10; i++) {
         duck.tick();
      }
      assertEquals(1.0F, duck.volumeFactor(SoundSource.AMBIENT, OTHER), 1.0E-6F);
      assertFalse(duck.active());
   }

   @Test
   void exemptIdNotDucked() {
      assertTrue(SoundDuck.exempt(OWN, Set.of(OWN)));
      assertFalse(SoundDuck.exempt(OTHER, Set.of(OWN)));
      SoundDuck duck = new SoundDuck();
      duck.duck("scene", SoundSource.MASTER, 0.25F, 0, Set.of(OWN));
      assertEquals(1.0F, duck.volumeFactor(SoundSource.PLAYERS, OWN), 1.0E-6F);
   }

   @Test
   void otherPlayersSoundDucked() {
      SoundDuck duck = new SoundDuck();
      duck.duck("scene", SoundSource.MASTER, 0.25F, 0, Set.of(OWN));
      assertEquals(0.25F, duck.volumeFactor(SoundSource.PLAYERS, OTHER), 1.0E-6F);
   }
}
