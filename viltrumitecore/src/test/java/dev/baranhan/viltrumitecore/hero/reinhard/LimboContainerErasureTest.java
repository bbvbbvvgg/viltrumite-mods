package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.reinhard.limbo.Limbo;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Vanilla-faithful personal menu return/overflow; external storage never belongs to the player. */
class LimboContainerErasureTest {
   @ParameterizedTest @ValueSource(booleans={false,true}) void fullInventoryCursorAndCraftInputsProduceNoDrops(boolean pendingResume) {
      var p=new MenuBody();if(pendingResume){Limbo.prepareTag(p.data);Limbo.resume(p);}else Limbo.send(p);
      assertEquals(0,p.drops,"ordinary close must not overflow cursor/crafting inputs into the world");assertTrue(p.main.isEmpty());assertTrue(p.armour.isEmpty());assertTrue(p.offhand.isEmpty());assertNull(p.cursor);assertTrue(p.crafting.isEmpty());assertEquals(List.of("chest-a","chest-b"),p.external);assertEquals(List.of("ender-diamond"),p.ender);assertEquals(0,p.xp);assertFalse(p.data.getCompound(Limbo.TAG).getBoolean("pendingStrip"));
   }
   @Test void completedStripIsNotReplayedOnResume() {var p=new MenuBody();Limbo.send(p);int clears=p.clears;p.main.add("admin-granted-after-strip");Limbo.resume(p);assertEquals(clears,p.clears);assertEquals(1,p.xpClears);assertEquals(List.of("admin-granted-after-strip"),p.main);}
   static final class MenuBody implements Limbo.Body {
      final CompoundTag data=new CompoundTag();final List<String> main=new ArrayList<>(),armour=new ArrayList<>(List.of("helmet")),offhand=new ArrayList<>(List.of("shield")),crafting=new ArrayList<>(List.of("input-1","input-2","input-3","input-4")),external=new ArrayList<>(List.of("chest-a","chest-b")),ender=new ArrayList<>(List.of("ender-diamond"));String cursor="nonmergeable-cursor";int drops,clears,xp=4000,xpClears;
      MenuBody(){for(int i=0;i<36;i++)main.add("full-"+i);}
      void returnOrDrop(String stack){if(main.size()>=36)drops++;else main.add(stack);}
      public CompoundTag data(){return data;}public boolean online(){return true;}public boolean inVoid(){return true;}
      public void closeContainer(){if(cursor!=null){returnOrDrop(cursor);cursor=null;}for(String input:crafting)returnOrDrop(input);crafting.clear();}
      public void discardCarried(){cursor=null;}
      public void clearInventory(){clears++;main.clear();armour.clear();offhand.clear();}public void clearExperience(){xp=0;xpClears++;}
      public void teleportToVoid(){}public void holdVoid(){}public void startPacket(int n){}public void requestReturn(){}public void teleportToSharedSpawn(){}public void restoreVitals(){}public void endPacket(){}public void freshBody(){}
   }
}
