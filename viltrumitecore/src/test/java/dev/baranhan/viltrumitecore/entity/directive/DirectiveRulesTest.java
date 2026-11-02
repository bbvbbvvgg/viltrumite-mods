package dev.baranhan.viltrumitecore.entity.directive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The shared behaviour-control layer: expiry, refresh-not-stack, caps, enforcement, cleanup on hurt, save/load. */
class DirectiveRulesTest {
   private static final UUID ROHAN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
   private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

   private static Directive d(String id, DirectiveEffect e, long until, int flags) {
      return new Directive(id, e.bit(), ROHAN, null, null, null, until, flags, null);
   }

   @Test
   void pruneDropsExpiredAndKeepsTheSameListOtherwise() {
      List<Directive> list = List.of(d("a", DirectiveEffect.CALM, 100, 0), d("b", DirectiveEffect.SAFETY_LOCK, 300, 0));
      assertSame(list, DirectiveRules.prune(list, 50));
      List<Directive> pruned = DirectiveRules.prune(list, 100);
      assertEquals(1, pruned.size());
      assertEquals("b", pruned.get(0).id());
      assertTrue(DirectiveRules.prune(list, 1000).isEmpty(), "every directive ends: nothing is permanent");
   }

   @Test
   void mergeRefreshesCapsAndBoundsTheCount() {
      List<Directive> list = DirectiveRules.merge(List.of(), d("calm", DirectiveEffect.CALM, 200, 0), 0);
      list = DirectiveRules.merge(list, d("calm", DirectiveEffect.CALM, 400, 0), 0);
      assertEquals(1, list.size(), "rewriting the same line refreshes it, it never stacks");
      assertEquals(400, list.get(0).expiresAt());
      list = DirectiveRules.merge(list, d("forever", DirectiveEffect.SAFETY_LOCK, Long.MAX_VALUE, 0), 10);
      assertEquals(10 + DirectiveRules.MAX_TICKS, list.get(1).expiresAt(), "no permanent lock");
      List<Directive> many = new ArrayList<>();
      for (int i = 0; i < DirectiveRules.MAX_ACTIVE + 3; i++) {
         many = DirectiveRules.merge(many, d("x" + i, DirectiveEffect.CALM, 100 + i, 0), 0);
      }
      assertEquals(DirectiveRules.MAX_ACTIVE, many.size());
      assertFalse(many.stream().anyMatch(x -> x.id().equals("x0")), "the soonest-ending one fades first");
   }

   @Test
   void enforcement() {
      List<Directive> lock = List.of(d("safety_lock", DirectiveEffect.SAFETY_LOCK, 100, 0));
      assertTrue(DirectiveRules.forbidsTarget(lock, ROHAN, false));
      assertFalse(DirectiveRules.forbidsTarget(lock, OTHER, false));
      List<Directive> spare = List.of(d("spare", DirectiveEffect.SPARE_PEACEFUL, 100, 0));
      assertTrue(DirectiveRules.forbidsTarget(spare, OTHER, true));
      assertFalse(DirectiveRules.forbidsTarget(spare, OTHER, false));
      List<Directive> noMelee = List.of(d("no_attack", DirectiveEffect.NO_MELEE, 100, 0));
      assertTrue(DirectiveRules.forbidsDamage(noMelee, OTHER, false, DirectiveRules.HitKind.MELEE));
      assertFalse(DirectiveRules.forbidsDamage(noMelee, OTHER, false, DirectiveRules.HitKind.RANGED));
      List<Directive> open = List.of(d("open", DirectiveEffect.OPEN, 100, 0));
      assertTrue(DirectiveRules.seals(open, 3), "an open book acts on nothing");
      Directive sealed = new Directive("seal", 0, ROHAN, null, null, null, 100, 0, new int[]{7});
      assertTrue(DirectiveRules.seals(List.of(sealed), 7));
      assertFalse(DirectiveRules.seals(List.of(sealed), 8));
   }

   @Test
   void afterHurtCleansUp() {
      List<Directive> list = List.of(d("calm", DirectiveEffect.CALM, 100, Directive.BREAK_ON_HURT),
         d("follow", DirectiveEffect.FOLLOW, 100, Directive.BREAK_ON_AUTHOR_HIT), d("ripped", DirectiveEffect.RIPPED, 100, Directive.RESTORE_ON_HEAVY),
         d("lock", DirectiveEffect.SAFETY_LOCK, 100, 0));
      assertSame(list, DirectiveRules.afterHurt(list, null, 0.0F, 1.0F, 10.0F, 0.25F));
      List<Directive> byOther = DirectiveRules.afterHurt(list, OTHER, 2.0F, 0.9F, 10.0F, 0.25F);
      assertEquals(List.of("follow", "ripped", "lock"), byOther.stream().map(Directive::id).toList());
      List<Directive> byAuthor = DirectiveRules.afterHurt(list, ROHAN, 2.0F, 0.9F, 10.0F, 0.25F);
      assertEquals(List.of("ripped", "lock"), byAuthor.stream().map(Directive::id).toList());
      List<Directive> heavy = DirectiveRules.afterHurt(list, OTHER, 12.0F, 0.5F, 10.0F, 0.25F);
      assertEquals(List.of("follow", "lock"), heavy.stream().map(Directive::id).toList());
   }

   @Test
   void saveLoadRoundTripDropsExpired() {
      Directive full = new Directive("go_to", DirectiveEffect.GO_TO.bit(), ROHAN, OTHER, new BlockPos(1, 2, 3), new Vec3(0.5, 0, -0.5), 500, 2,
         new int[]{4, 5});
      List<Directive> list = List.of(full, d("old", DirectiveEffect.CALM, 50, 0));
      CompoundTag root = new CompoundTag();
      root.put("D", DirectiveRules.save(list));
      List<Directive> back = DirectiveRules.load(root.getList("D", 10), 100);
      assertEquals(1, back.size());
      Directive r = back.get(0);
      assertEquals(full.id(), r.id());
      assertEquals(full.effects(), r.effects());
      assertEquals(full.author(), r.author());
      assertEquals(full.subject(), r.subject());
      assertEquals(full.point(), r.point());
      assertEquals(full.expiresAt(), r.expiresAt());
      assertEquals(full.flags(), r.flags());
      assertTrue(r.seals(5));
   }
}
