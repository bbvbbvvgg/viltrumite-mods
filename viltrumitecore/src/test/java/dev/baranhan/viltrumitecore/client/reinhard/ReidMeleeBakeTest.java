package dev.baranhan.viltrumitecore.client.reinhard;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import java.nio.file.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ReidMeleeBakeTest {
   @Test void finalRenderedBladeMatchesServerCollisionBake() throws Exception {
      var clips=ReinhardSecondPassTest.clips(); JsonArray tracks=new JsonArray();
      for(boolean crouch:new boolean[]{false,true})for(String name:new String[]{"chop_right","chop_left"}) {
         JsonArray rows=new JsonArray();
         var body=ReinhardSecondPassTest.body();
         for(int i=0;i<=160;i++) {
            double ticks=i/8.0;
            AnimationController.seek(clips.get("idle_holy_drawn"),body,0);
            ReinhardLayers.overlay(clips.get(name),body,ticks/20,ReinhardPlayback.weight(ticks/20,1));
            if(crouch)AnimationController.additive(clips.get("crouch"),body,0,1);
            var frame=ReinhardSword.equipment(body,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1)[1];
            var base=frame.transformPosition(new Vector3f(0,.7F/16,0));
            var tip=frame.transformPosition(new Vector3f(0,40.7798F/16,0));
            JsonArray row=new JsonArray();for(float n:new float[]{base.x,base.y,base.z,tip.x,tip.y,tip.z})row.add(Math.round(n*1E6)/1E6);
            rows.add(row);
         }
         tracks.add(rows);
      }
      Path file=Path.of("src/main/resources/data/viltrumitecore/reinhard/reid_melee.json");
      if("1".equals(System.getenv("REINHARD_BAKE")))Files.writeString(file,tracks.toString()+"\n");
      assertEquals(tracks,JsonParser.parseString(Files.readString(file)),"Regenerate with REINHARD_BAKE=1 after intentional blade path edits");
   }
}
