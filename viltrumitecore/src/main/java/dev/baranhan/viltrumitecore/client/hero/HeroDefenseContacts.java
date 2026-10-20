package dev.baranhan.viltrumitecore.client.hero;
import dev.baranhan.viltrumitecore.hero.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** Final rendered contact, analogous to the existing hand trackers. Never authoritative. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="viltrumitecore",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HeroDefenseContacts {
   private record Contact(Vec3 point,long tick,float projectionX,float projectionY) {}
   private static float worldX=1,worldY=1;
   /** Tick/audio callers may run with the GUI orthographic matrix bound. Capture the actual world projection. */
   @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
   public static void projection(net.minecraftforge.client.event.RenderLevelStageEvent event) {
      worldProjection(event.getProjectionMatrix());
   }
   private static final Map<Player,Contact> WORLD=new WeakHashMap<>(),VIEW=new WeakHashMap<>();
   private HeroDefenseContacts() {}
   public static void worldProjection(org.joml.Matrix4f m) {
      if(Math.abs(m.m33())<1E-6 && m.m00()>0 && m.m11()>0) {worldX=m.m00();worldY=m.m11();}
   }
   public static Vec3 reprojectWorld(Vec3 view,float handX,float handY) {return reproject(view,handX,handY,worldX,worldY);}
   public static void record(Player p,Vec3 point,boolean first) {
      float x=1,y=1;
      if(first) {
         var camera=Minecraft.getInstance().gameRenderer.getMainCamera();
         var v=new org.joml.Vector3f((float)(point.x-camera.getPosition().x),(float)(point.y-camera.getPosition().y),(float)(point.z-camera.getPosition().z));
         point=worldToView(new Vec3(v.x,v.y,v.z),camera.rotation());
         var projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();x=projection.m00();y=projection.m11();
      }
      (first?VIEW:WORLD).put(p,new Contact(point,p.level().getGameTime(),x,y));
   }
   /** Preserve the rendered pixel even when world FOV differs from the hand projection. */
   public static Vec3 reproject(Vec3 view,float handX,float handY,float worldX,float worldY) {
      return Math.abs(worldX)<1E-6 || Math.abs(worldY)<1E-6 ? view : new Vec3(view.x*handX/worldX,view.y*handY/worldY,view.z);
   }
   /** Camera.rotation uses +Z forward; OpenGL hand/view coordinates use -Z. */
   public static Vec3 viewToWorld(Vec3 point,org.joml.Quaternionfc camera) {
      var v=new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z);
      new org.joml.Quaternionf(camera).rotateY((float)Math.PI).transform(v);return new Vec3(v.x,v.y,v.z);
   }
   public static Vec3 worldToView(Vec3 point,org.joml.Quaternionfc camera) {
      var v=new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z);
      new org.joml.Quaternionf(camera).rotateY((float)Math.PI).conjugate().transform(v);return new Vec3(v.x,v.y,v.z);
   }
   public static void clear(Player p) { WORLD.remove(p);VIEW.remove(p); }
   public static Vec3 contact(Player p) {
      if(HeroRegistry.get(p).defenseContact(p)==null)return null;
      var mc=Minecraft.getInstance();boolean fp=p==mc.player && mc.options.getCameraType().isFirstPerson();
      Contact c=(fp?VIEW:WORLD).get(p);
      if(c==null || Math.abs(p.level().getGameTime()-c.tick)>2)return HeroRegistry.get(p).defenseContact(p);
      if(!fp)return c.point;
      Vec3 view=reprojectWorld(c.point,c.projectionX,c.projectionY);
      var camera=mc.gameRenderer.getMainCamera();return camera.getPosition().add(viewToWorld(view,camera.rotation()));
   }
   public static Vec3 endpoint(Entity target,Vec3 fallback) {
      Vec3 p=target instanceof Player player?contact(player):null;return p==null?fallback:p;
   }
   public static boolean defended(Vec3 synced) {
      var level=Minecraft.getInstance().level;if(level==null)return false;
      for(Player p:level.players()) { Vec3 anchor=HeroRegistry.get(p).defenseContact(p);if(anchor!=null && (anchor.distanceToSqr(synced)<.04 || contact(p).distanceToSqr(synced)<.04))return true;}
      return false;
   }
   public static Vec3 endpoint(Vec3 synced) {
      var level=Minecraft.getInstance().level;if(level==null)return synced;
      for(Player p:level.players()) {
         Vec3 anchor=HeroRegistry.get(p).defenseContact(p);
         if(anchor!=null && anchor.distanceToSqr(synced)<.04)return endpoint(p,synced);
      }
      return synced;
   }
   public static Vec3 arrowOffset(AbstractArrow arrow,float partial) {
      for(Player p:arrow.level().players()) if(p instanceof HeroPlayer hp && hp.getHeroSnapshot()!=null
         && hp.getHeroSnapshot().resource2()==arrow.getId()+1 && HeroRegistry.get(p).defenseContact(p)!=null) {
         Vec3 target=contact(p);return target==null?Vec3.ZERO:target.subtract(arrow.getPosition(partial));
      }
      return Vec3.ZERO;
   }
}
