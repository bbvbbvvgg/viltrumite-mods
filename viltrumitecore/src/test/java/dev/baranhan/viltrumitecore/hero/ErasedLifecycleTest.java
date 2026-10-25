package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.control.*;
import dev.baranhan.viltrumitecore.hero.homelander.*;
import dev.baranhan.viltrumitecore.hero.ironman.*;
import dev.baranhan.viltrumitecore.hero.regulus.*;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Same body identity, real hero state reset helpers, and deterministic lifecycle glue. No game world. */
class ErasedLifecycleTest {
   @ParameterizedTest @EnumSource(CleanupReason.class) void losesBody(CleanupReason reason) {
      assertEquals(reason==CleanupReason.DEATH||reason==CleanupReason.ERASED,reason.losesBody());
   }
   @Test void cleanupReleaseReasonErasedIsDeath() {
      assertEquals(ReleaseReason.CASTER_DEATH,ControlManager.cleanupReleaseReason(CleanupReason.ERASED));
   }
   @ParameterizedTest @EnumSource(value=HeroId.class,names={"VILTRUMITE","REGULUS","HOMELANDER","IRON_MAN","REINHARD"})
   void sameEntityFreshAfterReturn(HeroId id) throws Exception {
      Fake body=new Fake(id); Object identity=body.identity;
      BodyLoss.erase(body); BodyLoss.fresh(body);
      assertSame(identity,body.identity); assertEquals(List.of("hero","held","control","grab","motion","sections","sync","baseline","pending","flight","owner","respawn","legacy"),body.order);
      assertFalse(body.grabTag); assertFalse(body.control); assertTrue(body.held.isEmpty()); assertEquals(HeroOwnerSnapshot.EMPTY,body.owner);
      assertFalse(body.session.totemConsumed()); assertTrue(body.phoenix==600); assertTrue(body.persistentCooldown==120);
      switch(id) {
         case VILTRUMITE -> { for(String key:body.flags.keySet()) if(key.startsWith("set")) assertTrue(Boolean.FALSE.equals(body.flags.get(key))||Integer.valueOf(0).equals(body.flags.get(key)),key); }
         case HOMELANDER -> assertFresh(body.state,new HomelanderState(),Set.of());
         case REGULUS -> { assertFresh(body.state,new RegulusState(),Set.of("cooldowns")); assertEquals(99,((RegulusState)body.state).cooldownOf("test")); }
         case IRON_MAN -> {assertFresh(body.state,new IronManState(),Set.of("roster","veronicaCooldown","podUuid","signature","countermeasures","suit","hulkbuster")); var s=(IronManState)body.state; assertEquals(SuitState.NONE,s.suit.state()); assertFalse(s.wantsFlight()); assertEquals(99,s.veronicaCooldown); assertEquals(IronManRules.ENERGY_MAX,s.energy.value()); assertEquals(0,s.overheat.count()); assertEquals(99,s.signature.cooldown); assertEquals(75,s.countermeasures.cooldown());}
         case REINHARD -> {assertFresh(body.state,new ReinhardState(),Set.of("mode","modeCooldown","cooldowns")); var s=(ReinhardState)body.state; assertEquals(ReinhardMode.HOLY_SWORD,s.mode); assertEquals(88,s.cooldowns[0]); assertFalse(s.busy());}
         default -> fail();
      }
   }
   @Test void noControlOwnedOrHeld() { var b=new Fake(HeroId.VILTRUMITE); BodyLoss.erase(b); assertFalse(b.control); assertFalse(b.grabTag); }
   @Test void heldInputsEmpty() { var b=new Fake(HeroId.VILTRUMITE); BodyLoss.erase(b); assertTrue(b.held.isEmpty()); }
   @Test void grabTagGone() { var b=new Fake(HeroId.VILTRUMITE); BodyLoss.erase(b); assertFalse(b.grabTag); assertTrue(b.order.indexOf("grab")>b.order.indexOf("hero")); }
   @Test void serverGlueUsesAllGenericResetsAndOneFreshPath() throws Exception {
      String s=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/HeroEvents.java"));
      assertTrue(s.contains("public static void prepareErasure(ServerPlayer player)"));
      for(String required:List.of("BodyLoss.erase(","BodyLoss.fresh(","cleanupTarget(player.getUUID())","releaseProjectilesFor(player.getUUID())","clearOwnerSections(player, OwnerSection.values())","player.removeTag(","freshBody(player)")) assertTrue(s.contains(required),required);
      assertTrue(s.contains("otherCore.releaseTarget()"));
   }
   private static void assertFresh(Object actual,Object expected,Set<String> kept) throws Exception {
      if(actual==null||expected==null){assertEquals(expected,actual);return;}
      Class<?> type=expected.getClass(); assertEquals(type,actual.getClass());
      if(type.isEnum()||actual instanceof Number||actual instanceof Boolean||actual instanceof String||actual instanceof UUID||actual instanceof net.minecraft.world.phys.Vec3){assertEquals(expected,actual);return;}
      if(actual instanceof Map||actual instanceof Collection){assertEquals(expected,actual);return;}
      if(type.isArray()){assertEquals(java.util.Arrays.deepToString(new Object[]{expected}),java.util.Arrays.deepToString(new Object[]{actual}));return;}
      for(Field f:type.getDeclaredFields()) {
         if(Modifier.isStatic(f.getModifiers())||kept.contains(f.getName())) continue;
         f.setAccessible(true); assertFresh(f.get(actual),f.get(expected),Set.of());
      }
   }
   static final HeldInputs.Sink SINK=new HeldInputs.Sink() {
      public HeroAction claim(MouseButton b){return HeroAction.LIGHT_PUNCH;} public boolean canAct(HeroAction a){return true;} public void handle(HeroAction a,boolean pressed){}
   };
   static final class Fake implements BodyLoss.ErasedBody,BodyLoss.FreshBody {
      final Object identity=new Object(); final HeroId hero; final List<String> order=new ArrayList<>();
      final HeldInputs held=new HeldInputs(); final Map<String,Object> flags=new HashMap<>(); Object state;
      boolean control=true,grabTag=true; int phoenix=600,persistentCooldown=120;
      HeroOwnerSnapshot owner=new HeroOwnerSnapshot(new int[]{7}); HeroSession session;
      Fake(HeroId hero){this.hero=hero;session=new HeroSession(hero,UUID.randomUUID(),true);held.press(MouseButton.PRIMARY,SINK);
         switch(hero){
            case HOMELANDER -> {var s=new HomelanderState(); s.laserHeld=true;s.laserAge=70;s.focusOn=true;s.focusTargets.add(7);s.roarAnim=10;s.roarCooldown=60;s.regen.onDamaged();s.heat.tick(true,true);state=s;}
            case REGULUS -> {var s=new RegulusState();s.beginAction("test",40,10,30);s.carriers.add(UUID.randomUUID());s.carrierLevels.put(UUID.randomUUID(),new net.minecraft.resources.ResourceLocation("minecraft","overworld"));s.lionActive=true;s.channelTargetId=UUID.randomUUID();s.channelEffectId=UUID.randomUUID();s.madnessTicksLeft=40;s.cooldowns.put("test",99);state=s;}
            case IRON_MAN -> {var s=new IronManState();s.suit.toggle();s.energy.drain(80);s.glide=true;s.heldTool=dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool.NANO_HAMMER;s.repulsor.press();s.hulkKit.grabbedId=7;s.hulkKit.grabbedUuid=UUID.randomUUID();s.hulkKit.grabEffect=UUID.randomUUID();s.ramHits.put(UUID.randomUUID(),20L);s.threats.add(7);s.emptySuitId=8;s.veronicaCooldown=99;s.signature.cooldown=99;s.countermeasures.setCooldown(75);state=s;}
            case REINHARD -> {var s=new ReinhardState();s.mode=ReinhardMode.HOLY_SWORD;s.cooldowns[0]=88;s.modeCooldown=12;s.action.start(ReinhardAbility.LIGHT_PUNCH,10,-1);s.comboStep=2;s.comboQueued=true;s.catchWindow=10;s.reflectWindow=15;s.recentAttackers.put(UUID.randomUUID(),10L);state=s;}
            default -> {flags.put("setBarraging",true);flags.put("setBlocking",true);flags.put("setPunchTicks",10);}
         }
      }
      public void cleanupHero(CleanupReason reason){assertEquals(CleanupReason.ERASED,reason);order.add("hero");switch(hero){
         case HOMELANDER -> state=((HomelanderState)state).erasedCopy();
         case REGULUS -> {var s=(RegulusState)state;s.resetTransient(reason);assertNull(s.actionId);assertTrue(s.carrierLevels.isEmpty());state=s.erasedCopy();}
         case IRON_MAN -> {var s=(IronManState)state;s.onCleanup(reason);assertEquals(SuitState.NONE,s.suit.state());state=s.erasedCopy();}
         case REINHARD -> state=((ReinhardState)state).erasedCopy();
         default -> {var core=new FakeCore(flags);LegacyKit.resetActions(core);}
      }}
      public void clearHeldInputs(){order.add("held");held.releaseAll(SINK);}
      public void releaseControls(){order.add("control");control=false;}
      public void releaseLegacyGrabs(){order.add("grab");grabTag=false;}
      public void resetMotionAndSession(){order.add("motion");session=session.refreshTotem();}
      public void clearOwnerSections(){order.add("sections");owner=HeroOwnerSnapshot.EMPTY;}
      public void sync(){order.add("sync");}
      public void controlBaseline(){order.add("baseline");}
      public void restorePendingFlags(){order.add("pending");}
      public void flightSync(){order.add("flight");}
      public void resendOwner(){order.add("owner");}
      public void heroRespawn(){order.add("respawn");}
      public void legacyRespawn(){order.add("legacy");}
   }
   static final class FakeCore implements ViltrumiteCorePlayer {
      final Map<String,Object> flags; FakeCore(Map<String,Object> flags){this.flags=flags;}
      public boolean isViltrumite(){return false;}
      public void setViltrumite(boolean var1){flags.put("setViltrumite",var1);}
      public boolean hasChosenRace(){return false;}
      public void setChosenRace(boolean var1){flags.put("setChosenRace",var1);}
      public boolean isDashing(){return false;}
      public void setDashing(boolean var1){flags.put("setDashing",var1);}
      public void startDash(){}
      public float getDashProgress(float var1){return 0f;}
      public int getPunchTicks(){return 0;}
      public void setPunchTicks(int var1){flags.put("setPunchTicks",var1);}
      public boolean isLeftArmPunch(){return false;}
      public void setLeftArmPunch(boolean var1){flags.put("setLeftArmPunch",var1);}
      public float getPunchStrength(){return 0f;}
      public void setPunchStrength(float var1){flags.put("setPunchStrength",var1);}
      public int getPunchCooldown(){return 0;}
      public void setPunchCooldown(int var1){flags.put("setPunchCooldown",var1);}
      public boolean isTryingToGrab(){return false;}
      public void setTryingToGrab(boolean var1){flags.put("setTryingToGrab",var1);}
      public LivingEntity getGrabbedTarget(){return null;}
      public void setGrabbedTarget(LivingEntity var1){flags.put("setGrabbedTarget",var1);}
      public void releaseTarget(){}
      public Vec3 getCalculatedHandPos(){return null;}
      public void setCalculatedHandPos(Vec3 var1){flags.put("setCalculatedHandPos",var1);}
      public Vec3 getServerHandPos(){return null;}
      public void setServerHandPos(Vec3 var1){flags.put("setServerHandPos",var1);}
      public Vec3 getChopHandPos(){return null;}
      public void setChopHandPos(Vec3 var1){flags.put("setChopHandPos",var1);}
      public boolean isBlocking(){return false;}
      public void setBlocking(boolean var1){flags.put("setBlocking",var1);}
      public int getBlockCooldown(){return 0;}
      public void setBlockCooldown(int var1){flags.put("setBlockCooldown",var1);}
      public boolean isSuperSpeed(){return false;}
      public void setSuperSpeed(boolean var1){flags.put("setSuperSpeed",var1);}
      public int getChopTicks(){return 0;}
      public void setChopTicks(int var1){flags.put("setChopTicks",var1);}
      public boolean isLeftChop(){return false;}
      public void setLeftChop(boolean var1){flags.put("setLeftChop",var1);}
      public int getChopType(){return 0;}
      public void setChopType(int var1){flags.put("setChopType",var1);}
      public void setFirstPersonLocalHandPos(Vec3 var1){flags.put("setFirstPersonLocalHandPos",var1);}
      public Vec3 getFirstPersonLocalHandPos(){return null;}
      public int getThunderclapTicks(){return 0;}
      public void setThunderclapTicks(int var1){flags.put("setThunderclapTicks",var1);}
      public Vec3 getCalculatedHandOffset(){return null;}
      public void setCalculatedHandOffset(Vec3 var1){flags.put("setCalculatedHandOffset",var1);}
      public float getCloneScale(){return 0f;}
      public void setCloneScale(float var1){flags.put("setCloneScale",var1);}
      public boolean isBarraging(){return false;}
      public void setBarraging(boolean var1){flags.put("setBarraging",var1);}
      public int getBarrageCooldown(){return 0;}
      public void setBarrageCooldown(int var1){flags.put("setBarrageCooldown",var1);}
      public int getBarrageTicks(){return 0;}
      public void setBarrageTicks(int var1){flags.put("setBarrageTicks",var1);}
      public boolean isLeftBarrageArm(){return false;}
      public void setLeftBarrageArm(boolean var1){flags.put("setLeftBarrageArm",var1);}
      public float getCalculatedHandPitch(){return 0f;}
      public void setCalculatedHandPitch(float var1){flags.put("setCalculatedHandPitch",var1);}
      public float getCalculatedHandYaw(){return 0f;}
      public void setCalculatedHandYaw(float var1){flags.put("setCalculatedHandYaw",var1);}
      public void setCalculatedBodyPitch(float var1){flags.put("setCalculatedBodyPitch",var1);}
      public float getCalculatedBodyPitch(){return 0f;}
      public void setCalculatedBodyYaw(float var1){flags.put("setCalculatedBodyYaw",var1);}
      public float getCalculatedBodyYaw(){return 0f;}
   }

}
