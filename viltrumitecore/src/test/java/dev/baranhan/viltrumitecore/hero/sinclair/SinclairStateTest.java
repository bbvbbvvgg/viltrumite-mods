package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** §8 lifecycle of the state and the snapshot wire layout. */
class SinclairStateTest {
   private static SinclairState busy() {
      SinclairState s = new SinclairState();
      s.magazine = 3;
      s.boltCooldown = 50;
      s.assistCooldown = 60;
      s.hexCooldown = 70;
      s.misCooldown = 80;
      s.ultCooldown = 90;
      s.ovations = 4;
      s.veil = 20;
      s.assistant = UUID.randomUUID();
      s.decoy = UUID.randomUUID();
      s.ult = SinclairState.Ult.WINDOW;
      s.captured = SinclairRepertoire.standard(ActShape.CUT);
      s.capturedFrom = SinclairRepertoire.Source.REPERTOIRE;
      s.window = 100;
      s.startMove(SinclairMove.SNAP);
      return s;
   }

   @Test
   void respawnKeepsCooldownsAndOvationOnly() {
      SinclairState copy = busy().respawnCopy();
      assertEquals(50, copy.boltCooldown);
      assertEquals(60, copy.assistCooldown);
      assertEquals(70, copy.hexCooldown);
      assertEquals(80, copy.misCooldown);
      assertEquals(90, copy.ultCooldown);
      assertEquals(4, copy.ovations);
      assertEquals(SinclairRules.MAGAZINE, copy.magazine);
      assertEquals(0, copy.veil);
      assertNull(copy.assistant);
      assertNull(copy.decoy);
      assertNull(copy.captured);
      assertSame(SinclairState.Ult.NONE, copy.ult);
      assertFalse(copy.busy());
   }

   @Test
   void resetTransientKeepsCooldowns() {
      SinclairState s = busy();
      s.resetTransient();
      assertEquals(90, s.ultCooldown);
      assertEquals(3, s.magazine);
      assertNull(s.assistant);
      assertNull(s.scene);
      assertSame(SinclairState.Ult.NONE, s.ult);
      assertEquals(0, s.veil);
      assertFalse(s.busy());
   }

   @Test
   void saveLoadRoundTrip() {
      CompoundTag tag = new CompoundTag();
      busy().save(tag);
      SinclairState loaded = new SinclairState();
      loaded.load(tag);
      assertEquals(3, loaded.magazine);
      assertEquals(50, loaded.boltCooldown);
      assertEquals(90, loaded.ultCooldown);
      assertEquals(4, loaded.ovations);
      assertNull(loaded.assistant);
      assertSame(SinclairState.Ult.NONE, loaded.ult);
   }

   @Test
   void loadClampsGarbage() {
      CompoundTag tag = new CompoundTag();
      CompoundTag inner = new CompoundTag();
      inner.putInt("magazine", 99);
      inner.putInt("ult", 999999);
      inner.putInt("bolt", -5);
      tag.put("sinclair", inner);
      SinclairState loaded = new SinclairState();
      loaded.load(tag);
      assertEquals(SinclairRules.MAGAZINE, loaded.magazine);
      assertEquals(SinclairRules.ULT_COOLDOWN, loaded.ultCooldown);
      assertEquals(0, loaded.boltCooldown);
      SinclairState empty = new SinclairState();
      empty.load(new CompoundTag());
      assertEquals(SinclairRules.MAGAZINE, empty.magazine);
   }

   @Test
   void emptyMagazineLoadsReloading() {
      CompoundTag tag = new CompoundTag();
      SinclairState s = new SinclairState();
      s.magazine = 0;
      s.save(tag);
      SinclairState loaded = new SinclairState();
      loaded.load(tag);
      assertEquals(SinclairRules.RELOAD_TICKS, loaded.reload);
   }

   @Test
   void snapshotLayout() {
      SinclairState s = busy();
      s.reload = 7;
      HeroPublicSnapshot snap = SinclairHero.compose(s, 42, 120, false);
      assertSame(HeroId.SINCLAIR, snap.heroId());
      assertEquals(SinclairMove.SNAP.ordinal(), snap.actionId());
      assertEquals(SinclairMove.SNAP.length(), snap.actionLength());
      assertEquals(50, snap.cooldowns()[SinclairFlags.COOLDOWN_BOLT]);
      assertEquals(90, snap.cooldowns()[SinclairFlags.COOLDOWN_ULT]);
      assertEquals(7, snap.cooldowns()[SinclairFlags.COOLDOWN_RELOAD]);
      assertEquals(3, snap.resource());
      assertTrue(SinclairFlags.has(snap.heroFlags(), SinclairFlags.VEIL));
      assertTrue(SinclairFlags.has(snap.heroFlags(), SinclairFlags.ASSIST_ON_STAGE));
      assertTrue(SinclairFlags.has(snap.heroFlags(), SinclairFlags.CARD_HELD));
      assertTrue(SinclairFlags.has(snap.heroFlags(), SinclairFlags.RELOADING));
      assertFalse(SinclairFlags.has(snap.heroFlags(), SinclairFlags.HEXED));
      assertEquals(ActShape.CUT.ordinal() + 1, snap.extraCooldown(SinclairFlags.EXTRA_SHAPE));
      assertEquals(100, snap.extraCooldown(SinclairFlags.EXTRA_WINDOW));
      assertEquals(120, snap.extraCooldown(SinclairFlags.EXTRA_ASSIST_LEFT));
      assertEquals(2, snap.extraCooldown(SinclairFlags.EXTRA_SWAPS_LEFT));
      HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(snap.encode());
      assertEquals(snap, decoded);
   }

   @Test
   void heroBasics() {
      HeroRegistry.registerDefaults();
      SinclairHero hero = SinclairHero.INSTANCE;
      assertSame(hero, HeroRegistry.get(HeroId.SINCLAIR));
      assertFalse(hero.allowsFlight(null));
      assertFalse(hero.allowsLegacyAbilities(null));
      assertNull(hero.signatureAct(null));
      assertEquals(18, hero.defaultLoadout().length);
      String[] loadout = hero.defaultLoadout();
      assertEquals(SinclairAbilities.VEXING_BOLT, loadout[0]);
      assertEquals(SinclairAbilities.SPECTRAL_ASSISTANT, loadout[1]);
      assertEquals(SinclairAbilities.RABBIT_HEX, loadout[2]);
      assertEquals(SinclairAbilities.MISDIRECTION, loadout[3]);
      assertEquals(SinclairAbilities.AUDIENCE_PARTICIPATION, loadout[4]);
      for (String id : SinclairAbilities.OWN) {
         assertTrue(hero.ownsAbility(id));
         assertTrue(hero.heroActionFor(id) != null);
         assertTrue(hero.abilityIcon(id).getPath().startsWith("textures/gui/ability/sinclair/"));
      }
      assertFalse(hero.ownsAbility("viltrumite:punch"));
      assertTrue(hero.interceptsProjectiles(null));
   }
}
