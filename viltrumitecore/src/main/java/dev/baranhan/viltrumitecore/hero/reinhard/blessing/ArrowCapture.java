package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** The original tracked arrow remains visible in the hand, then falls as a real arrow.
 * No inventory conversion, no copy, no extra pickup item. Tridents are not captured.
 */
public final class ArrowCapture {
   private AbstractArrow arrow;
   private boolean gravity;
   private AbstractArrow.Pickup pickup;
   private int ticks;
   public int entityId() { return holding() ? arrow.getId()+1 : 0; }
   public boolean holding() { return arrow != null && !arrow.isRemoved(); }
   public boolean start(ServerPlayer self, AbstractArrow next) {
      if (holding()) return false;
      arrow = next; gravity = next.isNoGravity(); pickup = next.pickup; ticks = 10;
      // AbstractArrow.playerTouch permits pickup while noPhysics is true. A held
      // arrow must remain an entity, not disappear into the nearby owner inventory.
      next.setOwner(self); // Vanilla setOwner(Player) rewrites pickup; lock only afterwards.
      next.pickup = AbstractArrow.Pickup.DISALLOWED; next.setNoPhysics(true); next.setNoGravity(true);
      next.setDeltaMovement(Vec3.ZERO); next.hasImpulse = true;
      move(self);
      return true;
   }
   private void move(ServerPlayer self) {
      // Right palm, in front of the face; identical facing policy to the reaction layer.
      Vec3 forward = self.getLookAngle();
      Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
      var state=dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState.of(self);
      Vec3 palm=state==null?null:state.defense.palm();
      Vec3 at = palm==null ? self.getEyePosition().add(forward.scale(.62)).add(right.scale(.22)).add(0,-.2,0) : DefenseContact.world(self,palm);
      arrow.setPos(at); arrow.setDeltaMovement(Vec3.ZERO); arrow.hasImpulse = true;
   }
   public void tick(ServerPlayer self) {
      if (arrow == null) return;
      if (arrow.isRemoved()) { arrow = null; return; }
      if (!self.isAlive() || arrow.level() != self.level() || --ticks <= 0) { release(); return; }
      move(self);
   }
   public void release() {
      if (arrow != null && !arrow.isRemoved()) {
         arrow.setNoPhysics(false); arrow.setNoGravity(gravity); arrow.pickup = pickup;
         arrow.setBaseDamage(0); arrow.setDeltaMovement(0,-.12,0); arrow.hasImpulse = true;
      }
      arrow = null; ticks = 0;
   }
}
