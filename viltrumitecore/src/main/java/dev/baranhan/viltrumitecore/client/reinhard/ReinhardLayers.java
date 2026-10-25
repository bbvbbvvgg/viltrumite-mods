package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.HashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reinhard's layer ownership policy, using the shared sampler. An absent channel does not own the base pose. */
final class ReinhardLayers {
   private ReinhardLayers() {}
   static void overlay(Animation clip, BakedGeoModel model, double seconds) { overlay(clip,model,seconds,1); }
   static void overlay(Animation clip, BakedGeoModel model, double seconds, float weight) {
      overlay(clip, model, seconds, weight, null);
   }
   /** The authored equipment anchor must not fade with the surrounding body. */
   static void overlay(Animation clip, BakedGeoModel model, double seconds, float weight, String fullWeightBone) {
      float bodyWeight=Math.max(0,Math.min(1,weight));
      if(bodyWeight==0 && fullWeightBone==null) return;
      Map<String,float[]> before=new HashMap<>();
      for(GeoBone b:model.allBones()) before.put(b.name,new float[]{b.rotX,b.rotY,b.rotZ,b.posX,b.posY,b.posZ,b.scaleX,b.scaleY,b.scaleZ});
      AnimationController.overlay(clip,model,seconds);
      for(Animation.BoneAnimation a:clip.boneAnimations().values()) {
         GeoBone b=model.getBone(a.boneName()); if(b==null) continue;
         float w=a.boneName().equals(fullWeightBone)?1:bodyWeight;
         float[] p=before.get(b.name);
         boolean rotation=!a.rotX().isEmpty() || !a.rotY().isEmpty() || !a.rotZ().isEmpty();
         if(rotation && w<1) {
            Vector3f e=ReinhardGrip.euler(new Quaternionf().rotationZYX(p[2],p[1],p[0]).slerp(
               new Quaternionf().rotationZYX(a.rotZ().isEmpty()?p[2]:b.rotZ,a.rotY().isEmpty()?p[1]:b.rotY,a.rotX().isEmpty()?p[0]:b.rotX),w));
            b.rotX=e.x;b.rotY=e.y;b.rotZ=e.z;
         } else {
            if(a.rotX().isEmpty())b.rotX=p[0];
            if(a.rotY().isEmpty())b.rotY=p[1];
            if(a.rotZ().isEmpty())b.rotZ=p[2];
         }
         b.posX=a.posX().isEmpty()?p[3]:p[3]+(b.posX-p[3])*w;
         b.posY=a.posY().isEmpty()?p[4]:p[4]+(b.posY-p[4])*w;
         b.posZ=a.posZ().isEmpty()?p[5]:p[5]+(b.posZ-p[5])*w;
         b.scaleX=a.scaleX().isEmpty()?p[6]:p[6]+(b.scaleX-p[6])*w;
         b.scaleY=a.scaleY().isEmpty()?p[7]:p[7]+(b.scaleY-p[7])*w;
         b.scaleZ=a.scaleZ().isEmpty()?p[8]:p[8]+(b.scaleZ-p[8])*w;
      }
   }
}
