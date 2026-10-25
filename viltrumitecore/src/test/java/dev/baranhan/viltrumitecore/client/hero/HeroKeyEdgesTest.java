package dev.baranhan.viltrumitecore.client.hero;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.*;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero;
import dev.baranhan.viltrumitecore.hero.ironman.IronManHero;
import dev.baranhan.viltrumitecore.network.packet.HeroInputC2SPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class HeroKeyEdgesTest {
   private record Edge(HeroAction action, boolean down) {}
   private final HeroKeyEdges keys = new HeroKeyEdges();
   private final List<Edge> sent = new ArrayList<>();
   private final Set<String> held = new HashSet<>();
   private Object session = new Object();
   private void tick(HeroDefinition hero, String layout, boolean focused, String[] slots) {
      keys.tick(session, hero.id().key(), layout, focused, slots, hero::heroActionFor, held::contains,
         (action, down) -> {
            // Use the same packet constructor/codec as the production tick sink.
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
               new HeroInputC2SPacket(action, down).toBytes(bytes);
               sent.add(new Edge(HeroAction.byId(bytes.readByte()), bytes.readBoolean()));
               assertEquals(0, bytes.readableBytes());
            } finally { bytes.release(); }
         });
   }
   private void tick(HeroDefinition hero, String layout) { tick(hero, layout, true, hero.heroInputSlots()); }

   @Test void everyReinhardModeKeyReachesPacketIncludingFiveLateSwordSlots() {
      HeroDefinition hero = ReinhardHero.INSTANCE;
      assertEquals(6, hero.heroInputSlots().length); // context variants are not separate keys
      Set<HeroAction> observed = new HashSet<>();
      String[] loadout = hero.defaultLoadout();
      for (int page = 0; page < 3; page++) {
         String context = "page" + page;
         tick(hero, context);
         for (int slot = page * 6; slot < page * 6 + 6; slot++) {
            String id = loadout[slot];
            HeroAction action = hero.heroActionFor(id);
            if (action == null || !Arrays.asList(hero.heroInputSlots()).contains(id)) continue;
            sent.clear(); held.add(id); tick(hero, context); tick(hero, context);
            held.clear(); tick(hero, context); tick(hero, context);
            assertEquals(List.of(new Edge(action, true), new Edge(action, false)), sent, id);
            observed.add(action);
         }
      }
      assertEquals(6, observed.size());
      assertTrue(observed.containsAll(List.of(HeroAction.REID, HeroAction.STEP_DASH,
         HeroAction.CUT_RAIN, HeroAction.WORLD_CUT, HeroAction.WORLD_ERASURE)));
      assertFalse(Arrays.asList(hero.heroInputSlots()).contains(ReinhardAbility.DASH_BEHIND.id()));
      assertFalse(Arrays.asList(hero.heroInputSlots()).contains(ReinhardAbilities.CHOP)); // shared kit path
   }

   @Test void allOtherHeroSlotsRetainExactlyOnePressAndRelease() {
      for (HeroDefinition hero : List.of(new HomelanderHero(), new RegulusHero(), new IronManHero())) {
         keys.clear(); held.clear(); tick(hero, "page");
         for (String id : hero.heroInputSlots()) {
            HeroAction action = hero.heroActionFor(id);
            if (action == null) continue;
            sent.clear(); held.add(id); tick(hero, "page"); tick(hero, "page");
            held.clear(); tick(hero, "page"); tick(hero, "page");
            assertEquals(List.of(new Edge(action, true), new Edge(action, false)), sent, hero.id() + ":" + id);
         }
      }
   }

   @Test void pageSwitchReleasesOldChannelButHeldRDoesNotFireReid() {
      HeroDefinition hero = ReinhardHero.INSTANCE;
      tick(hero, "knight"); held.add("reinhard:sky_kick"); tick(hero, "knight");
      sent.clear(); held.clear(); held.add("reinhard:reid"); tick(hero, "sword"); tick(hero, "sword");
      assertEquals(List.of(new Edge(HeroAction.SKY_KICK, false)), sent);
      sent.clear(); held.clear(); tick(hero, "sword"); assertTrue(sent.isEmpty());
      held.add("reinhard:reid"); tick(hero, "sword");
      assertEquals(List.of(new Edge(HeroAction.REID, true)), sent);
   }

   @Test void heroSwitchDoesNotTransferOldEdgesOrFireHeldKey() {
      HeroDefinition old = ReinhardHero.INSTANCE, next = new IronManHero();
      tick(old, "same-page"); held.add("reinhard:reid"); tick(old, "same-page"); sent.clear();
      held.clear(); held.add(next.heroInputSlots()[0]); tick(next, "same-page"); tick(next, "same-page");
      held.clear(); tick(next, "same-page"); assertTrue(sent.isEmpty());
      held.add(next.heroInputSlots()[0]); tick(next, "same-page"); assertEquals(1, sent.size());
      assertTrue(sent.get(0).down());
   }

   @Test void respawnOrReconnectSeedsNewPlayerAndClearDropsAllHistory() {
      HeroDefinition hero = ReinhardHero.INSTANCE;
      tick(hero, "page"); held.add("reinhard:reid"); tick(hero, "page"); sent.clear();
      session = new Object(); tick(hero, "page"); held.clear(); tick(hero, "page");
      assertTrue(sent.isEmpty());
      held.add("reinhard:reid"); tick(hero, "page"); assertEquals(1, sent.size());
      sent.clear(); keys.clear(); tick(hero, "page"); assertTrue(sent.isEmpty());
   }

   @Test void guiOrFocusLossReleasesOnceAndRequiresFreshPressAfterReturn() {
      HeroDefinition hero = ReinhardHero.INSTANCE;
      tick(hero, "page"); held.add("reinhard:step_dash"); tick(hero, "page"); sent.clear();
      tick(hero, "page", false, hero.heroInputSlots()); tick(hero, "page", false, hero.heroInputSlots());
      tick(hero, "page"); tick(hero, "page"); held.clear(); tick(hero, "page");
      assertEquals(List.of(new Edge(HeroAction.STEP_DASH, false)), sent);
      sent.clear(); held.add("reinhard:step_dash"); tick(hero, "page");
      assertEquals(List.of(new Edge(HeroAction.STEP_DASH, true)), sent);
   }

   @Test void bindingChangeWithinSamePageDoesNotSynthesizePress() {
      HeroDefinition hero = ReinhardHero.INSTANCE; tick(hero, "2|old-binding");
      held.add("reinhard:world_cut"); tick(hero, "2|new-binding"); tick(hero, "2|new-binding");
      held.clear(); tick(hero, "2|new-binding"); assertTrue(sent.isEmpty());
      held.add("reinhard:world_cut"); tick(hero, "2|new-binding");
      assertEquals(List.of(new Edge(HeroAction.WORLD_CUT, true)), sent);
   }

   @Test void reorderDoesNotTransferIndexStateAndRemovedSlotsRelease() {
      HeroDefinition hero = ReinhardHero.INSTANCE; tick(hero, "page");
      held.add("reinhard:world_erasure"); tick(hero, "page"); sent.clear();
      List<String> reordered = new ArrayList<>(Arrays.asList(hero.heroInputSlots())); Collections.reverse(reordered);
      reordered.add(reordered.get(0)); reordered.add("not-an-ability");
      tick(hero, "page", true, reordered.toArray(String[]::new)); assertTrue(sent.isEmpty());
      reordered.remove("reinhard:world_erasure"); reordered.remove("reinhard:world_erasure");
      tick(hero, "page", true, reordered.toArray(String[]::new));
      assertEquals(List.of(new Edge(HeroAction.WORLD_ERASURE, false)), sent);
   }

   @Test void productionTickUsesTestedTrackerWithoutFixedTruncation() throws Exception {
      String source = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/ViltrumiteCoreClient.java"));
      assertTrue(source.contains("HERO_KEYS.tick(client.player, hero.id().key()"));
      assertTrue(source.contains("hero.heroInputSlots(), hero::heroActionFor"));
      assertTrue(source.contains("AbilityInputManager.isAbilityKeyDown(client.player, slot)"));
      assertTrue(source.contains("new dev.baranhan.viltrumitecore.network.packet.HeroInputC2SPacket(action, down)"));
      assertFalse(source.contains("heroKeyDown"));
   }
}
