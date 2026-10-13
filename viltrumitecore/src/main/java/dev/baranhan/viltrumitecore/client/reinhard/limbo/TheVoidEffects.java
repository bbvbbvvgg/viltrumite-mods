package dev.baranhan.viltrumitecore.client.reinhard.limbo;

import dev.baranhan.viltrumitecore.hero.BodyIsolation;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** No sky/ground/clouds; exact black fog, even with a bright biome/fog input. */
public final class TheVoidEffects extends DimensionSpecialEffects {
   public TheVoidEffects(){super(Float.NaN,false,SkyType.NONE,false,false);}
   @Override public Vec3 getBrightnessDependentFogColor(Vec3 color,float brightness){return Vec3.ZERO;}
   @Override public boolean isFoggyAt(int x,int z){return true;}
   @EventBusSubscriber(modid="viltrumitecore",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
   public static final class Registrar {
      @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event){event.register(new ResourceLocation(BodyIsolation.DIMENSION_ID),new TheVoidEffects());}
   }
}
