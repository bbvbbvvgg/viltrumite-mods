package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class HeroPublicSnapshotTest {

   private static HeroPublicSnapshot withResource(Vec3 target) {
      return new HeroPublicSnapshot(HeroId.HOMELANDER, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[]{120, 0, 0, 0, 0, 0}, false, -1, target, 735, true, 3);
   }

   @Test
   void encodeDecodeKeepsResourceFields() {
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(withResource(null).encode());
      assertEquals(HeroId.HOMELANDER, decoded.heroId());
      assertEquals(735, decoded.resource());
      assertTrue(decoded.resourceLocked());
      assertEquals(3, decoded.heroFlags());
      assertTrue(decoded.heroFlag(0));
      assertTrue(decoded.heroFlag(1));
      assertFalse(decoded.heroFlag(2));
      assertEquals(120, decoded.cooldowns()[0]);
      assertNull(decoded.actionTarget());
      assertEquals(withResource(null), decoded);
   }

   @Test
   void encodeDecodeKeepsTargetAndResource() {
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(withResource(new Vec3(1.5, 2, -3)).encode());
      assertEquals(new Vec3(1.5, 2, -3), decoded.actionTarget());
      assertEquals(735, decoded.resource());
   }

   @Test
   void oldEncodingDecodesWithDefaults() {
      // v1.13 encoding without target and without resource fields.
      String old = "regulus;-1;0;0;4;0;0;0;0;0;0;-1;-1;0;0;0;0;0;0;0;-1";
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(old);
      assertEquals(HeroId.REGULUS, decoded.heroId());
      assertEquals(4, decoded.hearts());
      assertEquals(0, decoded.resource());
      assertFalse(decoded.resourceLocked());
      assertEquals(0, decoded.heroFlags());
   }

   @Test
   void distinctResourceIsNotEqual() {
      HeroPublicSnapshot a = withResource(null);
      HeroPublicSnapshot b = new HeroPublicSnapshot(HeroId.HOMELANDER, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[]{120, 0, 0, 0, 0, 0}, false, -1, null, 736, true, 3);
      assertFalse(a.equals(b));
   }

   @Test
   void extraCooldownsRoundTrip() {
      HeroPublicSnapshot snapshot = new HeroPublicSnapshot(HeroId.IRON_MAN, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[HeroPublicSnapshot.COOLDOWN_COUNT], false, -1, null, 1000, false, 5, new int[]{600, 0, 40});
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(snapshot.encode());
      assertEquals(600, decoded.extraCooldown(0));
      assertEquals(40, decoded.extraCooldown(2));
      assertEquals(0, decoded.extraCooldown(9));
      assertEquals(snapshot, decoded);
   }

   @Test
   void oldEncodingDecodesNoExtraCooldowns() {
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(withResource(null).encode());
      assertEquals(0, decoded.extraCooldowns().length);
      assertEquals(0, decoded.extraCooldown(0));
   }

   @Test
   void extraCooldownsCapped() {
      HeroPublicSnapshot snapshot = new HeroPublicSnapshot(HeroId.IRON_MAN, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[HeroPublicSnapshot.COOLDOWN_COUNT], false, -1, null, 0, false, 0, new int[40]);
      assertEquals(HeroPublicSnapshot.EXTRA_COOLDOWN_MAX, snapshot.extraCooldowns().length);
   }

   private static HeroPublicSnapshot withMask(long mask) {
      return new HeroPublicSnapshot(HeroId.REINHARD, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
         new int[HeroPublicSnapshot.COOLDOWN_COUNT], false, mask, null);
   }

   @Test
   void highOrdinalDoesNotAliasBitZero() {
      HeroPublicSnapshot snapshot = withMask(HeroPublicSnapshot.actionMask(HeroAction.WORLD_ERASURE));
      assertTrue(snapshot.actionAvailable(HeroAction.WORLD_ERASURE));
      assertFalse(snapshot.actionAvailable(HeroAction.LIONS_HEART));
      assertFalse(snapshot.actionAvailable(HeroAction.MODE_CYCLE));
   }

   @Test
   void decodesLegacyIntMask() {
      String legacy = "regulus;-1;0;0;4;0;0;0;0;0;0;-1;-1;0;0;0;0;0;0;0;5";
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(legacy);
      assertEquals(5L, decoded.availableActions());
      assertTrue(decoded.actionAvailable(HeroAction.LIONS_HEART));
      assertFalse(decoded.actionAvailable(HeroAction.DEBRIS_KICK));
      assertTrue(decoded.actionAvailable(HeroAction.MANIA));
      assertEquals(-1L, HeroPublicSnapshot.decode("regulus;-1;0;0;4;0;0;0;0;0;0;-1;-1;0;0;0;0;0;0;0;-1").availableActions());
   }

   @Test
   void encodeRoundTripLong() {
      long mask = HeroPublicSnapshot.actionMask(HeroAction.WORLD_ERASURE, HeroAction.REID, HeroAction.LIONS_HEART);
      HeroPublicSnapshot snapshot = withMask(mask);
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(snapshot.encode());
      assertEquals(mask, decoded.availableActions());
      assertEquals(snapshot, decoded);
      assertEquals(snapshot.hashCode(), decoded.hashCode());
   }

   @Test
   void actionCountFitsMask() {
      assertTrue(HeroAction.values().length <= 64, "HeroPublicSnapshot.availableActions is a long bit mask");
   }
}
