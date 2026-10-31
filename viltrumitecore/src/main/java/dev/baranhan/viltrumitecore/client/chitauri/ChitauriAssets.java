package dev.baranhan.viltrumitecore.client.chitauri;

import net.minecraft.resources.ResourceLocation;

/** Resource paths of the Chitauri models, clips and atlases (own work, see tools/assets/chitauri_sources.md). */
final class ChitauriAssets {
   static final ResourceLocation SOLDIER_GEO = rl("geo/chitauri/soldier.geo.json");
   static final ResourceLocation SOLDIER_ANIM = rl("animations/chitauri/soldier.animation.json");
   static final ResourceLocation SOLDIER_STATES = rl("animations/chitauri/soldier_states.animation.json");
   static final ResourceLocation SOLDIER_TEX = rl("textures/entity/chitauri/soldier.png");
   static final ResourceLocation SOLDIER_GLOW = rl("textures/entity/chitauri/soldier_glow.png");
   static final ResourceLocation CHARIOT_GEO = rl("geo/chitauri/chariot.geo.json");
   static final ResourceLocation CHARIOT_ANIM = rl("animations/chitauri/chariot.animation.json");
   static final ResourceLocation CHARIOT_TEX = rl("textures/entity/chitauri/chariot.png");
   static final ResourceLocation CHARIOT_GLOW = rl("textures/entity/chitauri/chariot_glow.png");
   static final ResourceLocation HEAD_GEO = rl("geo/chitauri/leviathan_head.geo.json");
   static final ResourceLocation SEGMENT_GEO = rl("geo/chitauri/leviathan_segment.geo.json");
   static final ResourceLocation TAIL_GEO = rl("geo/chitauri/leviathan_tail.geo.json");
   static final ResourceLocation LEVIATHAN_ANIM = rl("animations/chitauri/leviathan.animation.json");
   static final ResourceLocation HEAD_TEX = rl("textures/entity/chitauri/leviathan_head.png");
   static final ResourceLocation HEAD_GLOW = rl("textures/entity/chitauri/leviathan_head_glow.png");
   static final ResourceLocation SEGMENT_TEX = rl("textures/entity/chitauri/leviathan_segment.png");
   static final ResourceLocation SEGMENT_GLOW = rl("textures/entity/chitauri/leviathan_segment_glow.png");
   static final ResourceLocation TAIL_TEX = rl("textures/entity/chitauri/leviathan_tail.png");
   static final ResourceLocation TAIL_GLOW = rl("textures/entity/chitauri/leviathan_tail_glow.png");

   private ChitauriAssets() {
   }

   static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }
}
