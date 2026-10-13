package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CinematicProtectionTest {
   @ParameterizedTest @EnumSource(HeroDamage.DamageKind.class) void protectionBlocksBeforeQueueOrLayers(HeroDamage.DamageKind kind){assertEquals(HeroDamage.DamageResult.BLOCKED,HeroDamage.decide(kind,true,true,true,10,true));assertEquals(HeroDamage.DamageResult.PASS,HeroDamage.decide(kind,true,false,false,10,false));}
   @Test void pausedProtectionIsNotAnAccumulatedPayout(){assertEquals(HeroDamage.DamageResult.BLOCKED,HeroDamage.decide(HeroDamage.DamageKind.EXTERNAL,true,true,false,20,true));}
   @Test void persistentIsolationMarker(){var data=new CompoundTag();assertFalse(BodyIsolation.active(data));data.put(BodyIsolation.TAG,new CompoundTag());assertTrue(BodyIsolation.active(data));data.remove(BodyIsolation.TAG);assertFalse(BodyIsolation.active(data));}
   @Test void everyLiveDamagePathChecksProtectionBeforeDoingWork() throws Exception {String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/HeroDamage.java"));for(String signature:new String[]{"public static DamageResult route(","public static void applyInternal(","public static void applyCleanDamage(LivingEntity target, DamageSource source, float amount, boolean flinch)"}) {int start=s.indexOf(signature);int end=s.indexOf("\n   }",start);assertTrue(s.substring(start,end).contains("isProtected("),signature);}assertTrue(s.contains("BodyIsolation.active(target)"));}
}
