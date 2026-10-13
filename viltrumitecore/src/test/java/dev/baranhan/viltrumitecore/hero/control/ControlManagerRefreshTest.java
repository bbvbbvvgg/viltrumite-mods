package dev.baranhan.viltrumitecore.hero.control;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ControlManagerRefreshTest {
   final UUID target = UUID.randomUUID(), owner = UUID.randomUUID(), effect = UUID.randomUUID();
   ControlManager.ControlRecord record() {
      return new ControlManager.ControlRecord(target, owner, effect, ControlKind.FREEZE, new Vec3(1,2,3), false, false);
   }
   @Test void refreshKeepsSavedFlags() {
      var r = record(); Vec3 anchor = r.anchor;
      for (int i=0;i<10;i++) assertTrue(ControlManager.refreshRecord(r, owner, effect, ControlKind.FREEZE, i+5));
      assertFalse(r.savedNoGravity); assertFalse(r.savedNoAi); assertSame(anchor, r.anchor);
      assertEquals(14,r.expiresAt); assertEquals(0,r.queuedDamage);
   }
   @Test void refreshRefusesForeignCaster() { var r=record(); assertFalse(ControlManager.refreshRecord(r,UUID.randomUUID(),effect,ControlKind.FREEZE,20)); assertEquals(-1,r.expiresAt); }
   @Test void refreshRefusesForeignEffect() { assertFalse(ControlManager.mayRefresh(record(),owner,UUID.randomUUID(),ControlKind.FREEZE)); }
   @Test void refreshRefusesOtherKind() { assertFalse(ControlManager.mayRefresh(record(),owner,effect,ControlKind.PULL)); }
   @Test void refreshMissingRecordFalse() { assertFalse(ControlManager.refreshRecord(null,owner,effect,ControlKind.FREEZE,20)); }
   @Test void managerRefreshDoesNotDirtyOrReplaceRecord() throws Exception {
      var ctor=ControlManager.class.getDeclaredConstructor(); ctor.setAccessible(true); var cm=ctor.newInstance();
      var field=ControlManager.class.getDeclaredField("controls"); field.setAccessible(true);
      @SuppressWarnings("unchecked") var map=(java.util.Map<UUID,ControlManager.ControlRecord>)field.get(cm);
      var rec=record(); map.put(target,rec);
      assertTrue(cm.refresh(target,owner,effect,ControlKind.FREEZE,99)); assertSame(rec,map.get(target));
      var dirty=ControlManager.class.getDeclaredField("dirty"); dirty.setAccessible(true); assertFalse(dirty.getBoolean(cm));
      assertEquals(99,rec.expiresAt);
   }
}
