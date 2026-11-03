package dev.baranhan.viltrumitecore.t1000;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Review of PR #143: clean damage, control policy, cover, reform, disabled AI. */
class T1000ReviewTest {
   private static String src(String name) throws Exception {
      return Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/t1000/" + name));
   }

   private static String body(String s, String head) {
      int i = s.indexOf(head);
      assertTrue(i >= 0, head);
      int open = s.indexOf('{', i), depth = 0;
      for (int k = open; k < s.length(); k++) {
         char c = s.charAt(k);
         if (c == '{') depth++;
         if (c == '}' && --depth == 0) return s.substring(i, k + 1);
      }
      throw new AssertionError(head);
   }

   @Test
   void cleanDamageGoesThroughTheLiquidMetalModel() throws Exception {
      String e = src("T1000Entity.java");
      assertTrue(e.contains("implements CleanDamageSink"));
      String clean = body(e, "public void cleanDamage(");
      assertTrue(clean.contains("T1000Damage.split("), "integrity/core split");
      assertTrue(clean.contains("this.reactToHit("), "wounds and brain().onHurt via reactToHit");
      assertTrue(clean.contains("this.melt()"), "integrity gone still melts");
      assertTrue(clean.contains("isInvulnerableTo(source)"), "immune kinds stay immune");
   }

   @Test
   void hookAndGrabRespectTheVictimPolicy() throws Exception {
      String b = src("T1000Brain.java");
      assertTrue(body(b, "static boolean canPull(").contains("ControlKind.PULL"));
      assertTrue(body(b, "static boolean canHold(").contains("ControlKind.VILTRUMITE_GRAB"));
      assertTrue(b.contains("l > 1.0E-3 && canPull(t)"), "hook pull gated");
      assertTrue(body(b, "private void holdGrab(").contains("!canHold(g)"), "held target rechecked every tick");
      assertTrue(body(b, "private void holdGrab(").contains("HeroRegistry.allowsImpulse(g)"), "throw gated");
   }

   @Test
   void meleeContactsNeedAClearPath() throws Exception {
      String impact = body(src("T1000Brain.java"), "private void impact(");
      for (String c : new String[] {"case STAB", "case SLASH", "case GRAB", "case COUNTER"}) {
         int i = impact.indexOf(c);
         int next = impact.indexOf("case ", i + 5);
         assertTrue(impact.substring(i, next).contains("clearPath("), c);
      }
   }

   @Test
   void reformingIntoAChaseRestoresTheRightBlade() throws Exception {
      String recover = body(src("T1000Brain.java"), "private void recover(");
      int blade = recover.indexOf("setArm(true, T1000Arm.BLADE");
      int chase = recover.indexOf("this.set(T1000State.CHASE)");
      assertTrue(blade >= 0 && blade < chase);
   }

   @Test
   void disabledAiStopsTheBrain() throws Exception {
      String tick = body(src("T1000Entity.java"), "public void tick()");
      assertTrue(tick.contains("if (this.isNoAi())") && tick.contains("this.brain().onDisabled()"));
      String disabled = body(src("T1000Brain.java"), "void onDisabled()");
      assertTrue(disabled.contains("releaseGrab()") && disabled.contains("T1000Action.NONE"));
   }
}
