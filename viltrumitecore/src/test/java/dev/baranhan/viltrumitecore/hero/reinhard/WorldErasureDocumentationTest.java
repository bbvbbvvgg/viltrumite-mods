package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class WorldErasureDocumentationTest {
   static String read(String p) throws Exception{return Files.readString(Path.of("..",p));}
   @Test void core1300OrNewerAndStage6SlotEnabled() throws Exception{var version=java.util.regex.Pattern.compile("(?m)^version = '([0-9]+)\\.([0-9]+)\\.([0-9]+)'").matcher(read("viltrumitecore/build.gradle"));assertTrue(version.find());int major=Integer.parseInt(version.group(1)),minor=Integer.parseInt(version.group(2));assertTrue(major>1 || major==1 && minor>=30);assertEquals(6,ReinhardAbilities.SHIPPED_STAGE);assertEquals("reinhard:world_erasure",ReinhardAbilities.defaultLoadout()[17]);assertTrue(ReinhardAbilities.owns("reinhard:world_erasure"));}
   @Test void toolkitExplainsIsolationAndEndQueue() throws Exception{var d=read(".agents/skills/add_hero/references/shared-toolkit.md");for(String term:new String[]{"WorldPause","ControlManager.refresh","ERASED","freshBody","ServerEndTasks","BodyIsolation","pendingStrip","online"})assertTrue(d.contains(term),term);}
   @Test void heroSeamExplainsSameEntityAndFreshReturn() throws Exception{var d=read(".agents/skills/add_hero/references/hero-seam.md");assertTrue(d.contains("same entity"));assertTrue(d.contains("ERASED"));assertTrue(d.contains("freshBody"));}
   @Test void animationSkillRecordsEveryUniformAndPriority() throws Exception{var d=read(".agents/skills/animation-system/SKILL.md");for(String uniform:new String[]{"EraseCrack","EraseShatter","EraseDark","EraseRebuild","EraseSeed","sonic"})assertTrue(d.contains(uniform),uniform);}
   @Test void memoryRecordsQuietDragonLimitation() throws Exception{var d=read(".memory-bank/project.md");assertTrue(d.contains("## Reinhard stage 6"));assertTrue(d.contains("the_void"));assertTrue(d.contains("portal/gateway/egg"));}
   @Test void sessionSeparatesPureProofFromUnrunGame() throws Exception{var d=read("SESSION.md");assertTrue(d.contains("## 2026-10-13 — reinhard-stage6-world-erasure"));assertTrue(d.contains("core 1.29.0 → 1.30.0"));assertTrue(d.contains("30 онлайн"));assertTrue(d.contains("не запускались"));}
}
