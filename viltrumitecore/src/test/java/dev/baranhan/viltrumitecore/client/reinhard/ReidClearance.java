package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Penetration DEPTH of the weapon volume into the body cubes (px), not a boolean overlap.
 * The weapon cubes are sampled on a 0.35 px grid; a sample inside a body cube scores its
 * distance to the nearest face of that cube. Samples within {@code exemptRadius} px of a
 * gripping palm are skipped: a handle in a fist always crosses the glove and the end of
 * the sleeve (the accepted Sword Drawn grip measures ~1 px there and nothing beyond 3.5 px). */
final class ReidClearance {
   record Hit(String bone, float depth, float palmDistance, Vector3f at) {
      @Override public String toString() {
         return String.format(java.util.Locale.ROOT,"%s %.2fpx (%.1fpx from palm, at %.1f,%.1f,%.1f)",bone,depth,palmDistance,-at.x*16,at.y*16,at.z*16);
      }
   }
   private static final Map<BakedGeoModel,List<Vector3f>> SAMPLES=new IdentityHashMap<>();
   private ReidClearance() {}

   private static Matrix4f cubeFrame(GeoBone bone,GeoBone.Cube c) {
      Matrix4f m=ReinhardGeometry.frame(bone);
      if(c.hasRotation())m.translate(c.pivot()).rotateZ(c.rotation().z).rotateY(c.rotation().y).rotateX(c.rotation().x).translate(new Vector3f(c.pivot()).negate());
      return m;
   }
   private static Vector3f[] bounds(GeoBone.Cube c) {
      Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
      for(var q:c.quads())if(q!=null)for(var v:q.vertices()){var p=new Vector3f(v.x(),v.y(),v.z());min.min(p);max.max(p);}
      return new Vector3f[]{min,max};
   }
   static synchronized List<Vector3f> samples(BakedGeoModel weapon) {
      return SAMPLES.computeIfAbsent(weapon,w->{
         List<Vector3f> out=new ArrayList<>();
         for(GeoBone b:w.allBones())for(var c:b.cubes) {
            Matrix4f m=cubeFrame(b,c);Vector3f[] box=bounds(c);Vector3f min=box[0],max=box[1];
            float step=.35F/16;
            int nx=Math.max(1,(int)Math.ceil((max.x-min.x)/step)),ny=Math.max(1,(int)Math.ceil((max.y-min.y)/step)),nz=Math.max(1,(int)Math.ceil((max.z-min.z)/step));
            for(int i=0;i<=nx;i++)for(int j=0;j<=ny;j++)for(int k=0;k<=nz;k++)
               out.add(m.transformPosition(new Vector3f(min.x+(max.x-min.x)*i/nx,min.y+(max.y-min.y)*j/ny,min.z+(max.z-min.z)*k/nz)));
         }
         return out;
      });
   }
   /** Deepest sample per body bone (bones without penetration are omitted). */
   static List<Hit> hits(BakedGeoModel body,BakedGeoModel weapon,Matrix4f weaponFrame,String[] bones,Vector3f[] exempt,float exemptRadius) {
      List<Vector3f> points=new ArrayList<>();
      for(Vector3f p:samples(weapon))points.add(weaponFrame.transformPosition(new Vector3f(p)));
      List<Hit> out=new ArrayList<>();
      for(String name:bones) {
         GeoBone bone=body.getBone(name);if(bone==null)continue;
         float best=0,bestPalm=0;Vector3f bestAt=null;
         for(var c:bone.cubes) {
            Matrix4f inv=cubeFrame(bone,c).invert();Vector3f[] box=bounds(c);Vector3f min=box[0],max=box[1];
            for(Vector3f w:points) {
               Vector3f l=inv.transformPosition(new Vector3f(w));
               float d=Math.min(Math.min(Math.min(l.x-min.x,max.x-l.x),Math.min(l.y-min.y,max.y-l.y)),Math.min(l.z-min.z,max.z-l.z))*16;
               if(d<=best)continue;
               float palm=Float.POSITIVE_INFINITY;for(Vector3f e:exempt)palm=Math.min(palm,e.distance(w)*16);
               if(palm<exemptRadius)continue;
               best=d;bestPalm=palm;bestAt=w;
            }
         }
         if(best>0)out.add(new Hit(name,best,bestPalm,bestAt));
      }
      return out;
   }
   private static final Map<GeoBone.Cube,List<Vector3f>> LIMB=new IdentityHashMap<>();
   /** Deepest penetration (px) of a limb bone's own cubes into the cubes of {@code fixed} bones, in the current pose. */
   static float limbDepth(BakedGeoModel body,String limb,String... fixed) {
      GeoBone moving=body.getBone(limb);if(moving==null)return 0;
      List<Vector3f> points=new ArrayList<>();
      Matrix4f base=ReinhardGeometry.frame(moving);
      for(var c:moving.cubes) {
         List<Vector3f> local;
         synchronized(LIMB) {
            local=LIMB.computeIfAbsent(c,cube->{
               List<Vector3f> out=new ArrayList<>();Vector3f[] box=bounds(cube);Vector3f min=box[0],max=box[1];
               Matrix4f m=new Matrix4f();
               if(cube.hasRotation())m.translate(cube.pivot()).rotateZ(cube.rotation().z).rotateY(cube.rotation().y).rotateX(cube.rotation().x).translate(new Vector3f(cube.pivot()).negate());
               float step=.35F/16;
               int nx=Math.max(1,(int)Math.ceil((max.x-min.x)/step)),ny=Math.max(1,(int)Math.ceil((max.y-min.y)/step)),nz=Math.max(1,(int)Math.ceil((max.z-min.z)/step));
               for(int i=0;i<=nx;i++)for(int j=0;j<=ny;j++)for(int k=0;k<=nz;k++)
                  out.add(m.transformPosition(new Vector3f(min.x+(max.x-min.x)*i/nx,min.y+(max.y-min.y)*j/ny,min.z+(max.z-min.z)*k/nz)));
               return out;
            });
         }
         for(Vector3f p:local)points.add(base.transformPosition(new Vector3f(p)));
      }
      float best=0;
      for(String name:fixed) {
         GeoBone bone=body.getBone(name);if(bone==null)continue;
         for(var c:bone.cubes) {
            Matrix4f inv=cubeFrame(bone,c).invert();Vector3f[] box=bounds(c);Vector3f min=box[0],max=box[1];
            for(Vector3f w:points) {
               Vector3f l=inv.transformPosition(new Vector3f(w));
               float d=Math.min(Math.min(Math.min(l.x-min.x,max.x-l.x),Math.min(l.y-min.y,max.y-l.y)),Math.min(l.z-min.z,max.z-l.z))*16;
               if(d>best)best=d;
            }
         }
      }
      return best;
   }
   static float deepest(List<Hit> hits) {
      float d=0;for(Hit h:hits)d=Math.max(d,h.depth());return d;
   }
}
