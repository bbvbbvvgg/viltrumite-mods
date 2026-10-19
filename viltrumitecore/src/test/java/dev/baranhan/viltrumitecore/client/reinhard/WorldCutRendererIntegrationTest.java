package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class WorldCutRendererIntegrationTest {
   @Test void drawnIdleDoesNotOverwriteActualWorldCutComposition() throws Exception {
      Path root=Path.of("src/main/resources/assets/viltrumitecore");
      var clips=AnimationParser.parse(JsonParser.parseString(Files.readString(root.resolve(ReinhardAssets.ANIMATIONS.getPath()))).getAsJsonObject());
      var field=AnimCache.class.getDeclaredField("animationFiles"); field.setAccessible(true); Object old=field.get(null);
      try {
         field.set(null,java.util.Map.of(ReinhardAssets.ANIMATIONS,clips));
         var json=JsonParser.parseString(Files.readString(root.resolve(ReinhardAssets.BODY.getPath()))).getAsJsonObject();
         BakedGeoModel expected=BakedGeoModel.parse(json), actual=BakedGeoModel.parse(json);
         AnimationController.overlay(clips.get("world_cut"),expected,.5);
         var pose=new ReinhardPoses.Pose("idle_holy",0,0,0,false,new ReinhardPoses.Clip("world_cut",.5),ReinhardPoses.BodyState.STAND,0);
         ReinhardRenderer.applyClips(actual,pose,new StanceBlend.Mix(ReinhardMode.HOLY_SWORD,ReinhardMode.HOLY_SWORD,1),0,0,0,
            new ParryPulse.Sample(0,0),new ReinhardPoses.Clip("idle_holy_drawn",0),null);
         assertEquals(expected.getBone("right_arm").rotX,actual.getBone("right_arm").rotX,1E-6);
         assertTrue(Math.abs(actual.getBone("right_arm").rotX)>2, "sword arm actually overhead");
      } finally { field.set(null,old); }
   }
}
