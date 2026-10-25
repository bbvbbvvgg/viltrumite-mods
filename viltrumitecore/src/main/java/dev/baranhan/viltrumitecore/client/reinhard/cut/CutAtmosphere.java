package dev.baranhan.viltrumitecore.client.reinhard.cut;
import dev.baranhan.viltrumitecore.client.render.vfx.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Scoped atmosphere leases; silence never owns World Erasure's post chain. */
@EventBusSubscriber(modid="viltrumitecore",value=Dist.CLIENT)
public final class CutAtmosphere {
   private static final Map<UUID,Lease> ACTIVE=new HashMap<>();
   private static final Set<ResourceLocation> OWN=Set.of(
      new ResourceLocation("viltrumitecore","reinhard.world_cut_silence"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_blast"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_tail"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_hum"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_pulse"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_impact"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_blade"),
      new ResourceLocation("viltrumitecore","reinhard.cut_rumble"));
   private record Lease(long seen,float weight) {}
   private CutAtmosphere() {}
   static boolean owns(ResourceLocation id) { return OWN.contains(id); }
   public static void update(UUID id,Vec3 near,double t,boolean working,double tail,boolean inRange,long now) {
      update(id,near,t,working,tail,inRange,now,0F);
   }
   /** {@code pulse} is extra cinematic shake from the rupture clock; diagonal passes zero. */
   public static void update(UUID id,Vec3 near,double t,boolean working,double tail,boolean inRange,long now,float pulse) {
      if(!inRange || t<8 || (!working&&tail<=0)) { release(id);return; }
      float weight=(float)(Math.min(1,Math.max(0,(t-8)/22))*Math.min(1,tail));
      if(ACTIVE.put(id,new Lease(now,weight))==null) SkyTint.INSTANCE.push(id,110,()-> {
         Lease lease=ACTIVE.get(id);return SkyTint.mix(SkyTint.NONE,new SkyTint.Tint(.15F,.18F,.25F,.94F,0),lease==null?0:lease.weight());
      });
      if(t<80) SoundDuck.INSTANCE.duck(id,SoundSource.MASTER,.08F,6,OWN);
      else SoundDuck.INSTANCE.release(id);
      float power=(t<40?1.2F:t<80?2.3F:6.8F)*weight;
      CameraShake.cinematicAt(near,power+pulse,ReinhardRange.VALUE);
   }
   public static void release(UUID id) { ACTIVE.remove(id);SkyTint.INSTANCE.remove(id);SoundDuck.INSTANCE.release(id); }
   @SubscribeEvent public static void fog(ViewportEvent.RenderFog e) {
      Minecraft m=Minecraft.getInstance();
      if(m.level==null) { for(UUID id:List.copyOf(ACTIVE.keySet()))release(id);return; }
      long now=m.level.getGameTime();
      for(var a:List.copyOf(ACTIVE.entrySet())) if(now-a.getValue().seen()>3)release(a.getKey());
      if(ACTIVE.isEmpty())return;
      // A bounded dust/energy visibility curtain hides incremental block updates without a black screen.
      e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(),.1F));
      float weight=ACTIVE.values().stream().map(Lease::weight).max(Float::compare).orElse(0F);
      float far=e.getFarPlaneDistance();e.setFarPlaneDistance(far+(Math.min(far,32)-far)*weight);e.setCanceled(true);
   }
   private static final class ReinhardRange { static final double VALUE=320; }
}
