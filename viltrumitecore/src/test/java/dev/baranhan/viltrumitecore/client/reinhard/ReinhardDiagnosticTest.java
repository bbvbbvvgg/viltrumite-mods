package dev.baranhan.viltrumitecore.client.reinhard;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.*;
import java.nio.file.*;
import org.joml.*;
import org.junit.jupiter.api.Test;

/** Optional CPU geometry export: native baked vertices and the production attachment matrices.
 * REINHARD_DIAGNOSTICS=1 ./gradlew :viltrumitecore:test --tests '*ReinhardDiagnosticTest'
 * Does not stand in for Minecraft renderer / gameplay acceptance.
 */
class ReinhardDiagnosticTest {
   @Test void exportNativePoseGeometry() throws Exception {
      if(System.getenv("REINHARD_DIAGNOSTICS")==null)return;
      var clips=ReinhardSecondPassTest.clips();
      for(String sample:new String[]{"holy","walk","run","crouch","kick_prep","kick_contact","draw_48","draw_108","sheath_10","draw_32","draw_40","draw_64","draw_80","draw_88","drawn","world_cut_12","world_cut_20","world_erasure_15","world_erasure_35","cut_rain_4","cut_rain_8","chop_left_6","chop_left_7","chop_right_6","chop_right_7","drawn_walk","drawn_run","drawn_crouch"}) {
         var b=ReinhardSecondPassTest.body(); AnimationController.seek(clips.get("idle_holy"),b,0);
         if(sample.equals("walk")||sample.equals("run")) ReinhardLayers.overlay(clips.get(sample),b,.18,1);
         if(sample.equals("crouch")) AnimationController.additive(clips.get("crouch"),b,0,1);
         if(sample.startsWith("kick")) AnimationController.seek(clips.get("kick_toss"),b,(sample.equals("kick_prep")?4.8:6)/20);

         boolean swordAction=sample.startsWith("world_")||sample.startsWith("cut_rain_")||sample.startsWith("chop_");
         var phase=sample.startsWith("draw_")?ReinhardPoses.ReidPhase.DRAWING:sample.startsWith("sheath")?ReinhardPoses.ReidPhase.SHEATHING:sample.startsWith("drawn")||swordAction?ReinhardPoses.ReidPhase.DRAWN:ReinhardPoses.ReidPhase.SHEATHED;
         double tick=sample.contains("_")&&(sample.startsWith("draw_")||sample.startsWith("sheath_"))?Double.parseDouble(sample.split("_")[1]):0;
         if(phase!=ReinhardPoses.ReidPhase.SHEATHED) {
            var clip=ReinhardPoses.reidClip(phase,tick);AnimationController.seek(clips.get(clip.animation()),b,clip.seconds());
         }
         if(swordAction) {
            int split=sample.lastIndexOf('_');
            AnimationController.seek(clips.get(sample.substring(0,split)),b,Double.parseDouble(sample.substring(split+1))/20);
         }
         if(sample.equals("drawn_walk")||sample.equals("drawn_run"))ReinhardLayers.overlay(clips.get(sample.substring(6)),b,.18,1);
         if(sample.equals("drawn_crouch"))AnimationController.additive(clips.get("crouch"),b,0,1);
         var frames=ReinhardSword.equipment(b,phase,tick,false,0,1);
         JsonArray meshes=new JsonArray();meshes.add(mesh(b,new Matrix4f(),"reinhard.png"));
         int i=0; for(String name:new String[]{"sheath","blade"}) {
            var model=BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/reinhard/"+name+".geo.json"))).getAsJsonObject());
            meshes.add(mesh(model,frames[i++],"dragon_sword.png"));
         }
         var out=Path.of("build/diagnostics/reinhard/"+sample+".json");Files.createDirectories(out.getParent());Files.writeString(out,meshes.toString());
      }
   }
   /** Same pose/cameras, mesh-only roll. This is an audit, not a corrected production pose. */
   @Test void exportFixedPoseAxialAudit() throws Exception {
      if(System.getenv("REINHARD_DIAGNOSTICS")==null)return;
      var b=ReinhardSecondPassTest.body();var clips=ReinhardSecondPassTest.clips();
      AnimationController.seek(clips.get("idle_holy"),b,0);
      AnimationController.seek(clips.get(ReinhardPoses.reidClip(ReinhardPoses.ReidPhase.DRAWN,0).animation()),b,0);
      AnimationController.seek(clips.get("world_erasure"),b,15/20.0);
      var frames=ReinhardSword.equipment(b,ReinhardPoses.ReidPhase.DRAWN,0,false,0,1);
      var x=frames[1].transformDirection(new Vector3f(1,0,0)).normalize();
      var z=frames[1].transformDirection(new Vector3f(0,0,1)).normalize();
      float faceRoll=(float)java.lang.Math.atan2(x.z,z.z);
      var dir=Path.of("build/diagnostics/reinhard/axial-audit");Files.createDirectories(dir);
      var report=new JsonObject();report.addProperty("faceAlignmentRollDegrees",java.lang.Math.toDegrees(faceRoll));
      for(int trial=0;trial<3;trial++) {
         float roll=trial==0?0:trial==1?(float)java.lang.Math.PI/2:faceRoll;
         String name=new String[]{"before","roll_90_trial","face_aligned_trial"}[trial];
         var blade=new Matrix4f(frames[1]).rotateY(roll);
         var meshes=new JsonArray();meshes.add(mesh(b,new Matrix4f(),"reinhard.png"));
         int i=0;for(String asset:new String[]{"sheath","blade"}) {
            var model=BakedGeoModel.parse(JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/geo/reinhard/"+asset+".geo.json"))).getAsJsonObject());
            meshes.add(mesh(model,i++==0?frames[0]:blade,"dragon_sword.png"));
         }
         Files.writeString(dir.resolve(name+".json"),meshes.toString());
         var matrix=new JsonArray();for(float value:blade.get(new float[16]))matrix.add(value);
         report.add(name+"MatrixColumnMajor",matrix);
      }
      Files.writeString(dir.resolve("matrices.txt"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
   }
   private static JsonObject mesh(BakedGeoModel model,Matrix4f base,String texture) {
      JsonObject m=new JsonObject();m.addProperty("texture",texture);JsonArray quads=new JsonArray();
      for(GeoBone bone:model.allBones()) {
         PoseStack stack=new PoseStack();ReinhardRenderer.boneFrame(stack,bone);stack.translate(-bone.pivotX/16,-bone.pivotY/16,-bone.pivotZ/16);
         for(GeoBone.Cube cube:bone.cubes) {
            Matrix4f transform=new Matrix4f(base).mul(stack.last().pose());
            if(cube.hasRotation())transform.translate(cube.pivot()).rotateZ(cube.rotation().z).rotateY(cube.rotation().y).rotateX(cube.rotation().x).translate(new Vector3f(cube.pivot()).negate());
            for(GeoBone.Quad quad:cube.quads()) {
               if(quad==null)continue;JsonArray vertices=new JsonArray();
               for(GeoBone.Vertex v:quad.vertices()) {
                  Vector3f p=transform.transformPosition(new Vector3f(v.x(),v.y(),v.z()));JsonArray vertex=new JsonArray();
                  for(float value:new float[]{-p.x*16,p.y*16,p.z*16,v.u(),v.v()})vertex.add(value);vertices.add(vertex);
               }quads.add(vertices);
            }
         }
      }m.add("quads",quads);return m;
   }
}
