package dev.baranhan.viltrumitecore.hero.reinhard.blessing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Local model-space palm target shared by authoritative contact and the existing arm solver. */
public final class DefenseContact {
   public static final double SCALE=.9375;
   private DefenseContact() {}
   public static Vec3 local(boolean left,float yaw,float pitch) {
      double aim=Math.toRadians(Math.max(-55,Math.min(55,yaw)));
      return new Vec3((left?-.30:.30)-Math.sin(aim)*.14,
         1.33-Math.max(-45,Math.min(45,pitch))*.002,-.46);
   }
   public static Vec3 world(Vec3 position,float bodyYaw,Vec3 local) {
      double r=Math.toRadians(180-bodyYaw),c=Math.cos(r),s=Math.sin(r);
      return position.add((c*local.x+s*local.z)*SCALE,local.y*SCALE,(-s*local.x+c*local.z)*SCALE);
   }
   public static Vec3 world(Player player,Vec3 local) { return world(player.position(),player.yBodyRot,local); }
}
