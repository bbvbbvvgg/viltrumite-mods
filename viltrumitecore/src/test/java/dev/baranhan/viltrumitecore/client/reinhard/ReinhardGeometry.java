package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.geo.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** CPU geometry diagnostics/clearance, using exactly AnimRenderer's cube transforms.
 * A grip-point equality alone says nothing about a long blade intersecting the head. */
final class ReinhardGeometry {
   private ReinhardGeometry(){}
   static Matrix4f frame(GeoBone bone) {
      PoseStack s=new PoseStack();ReinhardRenderer.boneFrame(s,bone);
      return new Matrix4f(s.last().pose()).translate(-bone.pivotX/16,-bone.pivotY/16,-bone.pivotZ/16);
   }
   static Vector3f[] vertices(GeoBone.Cube cube,Matrix4f base) {
      Matrix4f m=new Matrix4f(base);
      if(cube.hasRotation())m.translate(cube.pivot()).rotateZ(cube.rotation().z).rotateY(cube.rotation().y).rotateX(cube.rotation().x).translate(new Vector3f(cube.pivot()).negate());
      List<Vector3f> points=new ArrayList<>();
      for(var q:cube.quads())if(q!=null)for(var v:q.vertices()) {
         Vector3f p=m.transformPosition(new Vector3f(v.x(),v.y(),v.z()));
         if(points.stream().noneMatch(old->old.distanceSquared(p)<1E-10))points.add(p);
      }return points.toArray(Vector3f[]::new);
   }
   /** Conservative envelope of the actual head mesh in its own animated bone frame.
    * Includes hair; a clearance result cannot hide a hair intersection. */
   static boolean intersectsHead(BakedGeoModel body,BakedGeoModel weapon,Matrix4f weaponFrame) {
      GeoBone head=body.getBone("head");return intersectsBone(head,weapon,weaponFrame);
   }
   static boolean intersectsBone(GeoBone bone,BakedGeoModel weapon,Matrix4f weaponFrame) {
      if(bone==null)return false;
      Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
      for(var c:bone.cubes)for(var p:vertices(c,new Matrix4f())) {min.min(p);max.max(p);}
      if(!Float.isFinite(min.x))return false;
      // 0.12px safety margin, not a guessed weapon displacement.
      min.sub(.12F/16,.12F/16,.12F/16);max.add(.12F/16,.12F/16,.12F/16);
      Matrix4f relative=frame(bone).invert().mul(weaponFrame);
      for(GeoBone w:weapon.allBones())for(var cube:w.cubes) {
         Matrix4f base=new Matrix4f(relative).mul(frame(w));
         if(overlap(vertices(cube,base),min,max))return true;
      }return false;
   }
   static boolean intersectsBoneExactly(GeoBone fixed,BakedGeoModel weapon,Matrix4f weaponFrame) {
      for(var c:fixed.cubes) {
         Matrix4f cubeFrame=frame(fixed);
         if(c.hasRotation())cubeFrame.translate(c.pivot()).rotateZ(c.rotation().z).rotateY(c.rotation().y).rotateX(c.rotation().x).translate(new Vector3f(c.pivot()).negate());
         Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
         for(var q:c.quads())if(q!=null)for(var v:q.vertices()){var p=new Vector3f(v.x(),v.y(),v.z());min.min(p);max.max(p);}
         Matrix4f relative=cubeFrame.invert().mul(weaponFrame);
         for(var w:weapon.allBones())for(var wc:w.cubes)
            if(overlap(vertices(wc,new Matrix4f(relative).mul(frame(w))),min,max))return true;
      }
      return false;
   }
   static boolean intersectsBones(GeoBone fixed,GeoBone moving) {
      for(var c:fixed.cubes) {
         Matrix4f cubeFrame=frame(fixed);
         if(c.hasRotation())cubeFrame.translate(c.pivot()).rotateZ(c.rotation().z).rotateY(c.rotation().y).rotateX(c.rotation().x).translate(new Vector3f(c.pivot()).negate());
         Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
         for(var q:c.quads())if(q!=null)for(var v:q.vertices()){var p=new Vector3f(v.x(),v.y(),v.z());min.min(p);max.max(p);}
         Matrix4f relative=cubeFrame.invert().mul(frame(moving));
         for(var mc:moving.cubes)if(overlap(vertices(mc,relative),min,max))return true;
      }
      return false;
   }

   /** SAT: convex cube against the axis-aligned bone envelope. Test face normals
    * and edge cross-products, not only projected screen rectangles or vertices. */
   static boolean overlap(Vector3f[] p,Vector3f min,Vector3f max) {
      if(p.length<4)return false;
      Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
      for(var v:p){lo.min(v);hi.max(v);}
      if(hi.x<=min.x||lo.x>=max.x||hi.y<=min.y||lo.y>=max.y||hi.z<=min.z||lo.z>=max.z)return false;
      Vector3f[] xyz={new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)};
      List<Vector3f> edges=new ArrayList<>();
      // Cube edge directions are the three shortest independent vectors from a corner.
      List<Vector3f> differences=new ArrayList<>();for(int i=1;i<p.length;i++)differences.add(new Vector3f(p[i]).sub(p[0]));
      differences.sort(Comparator.comparingDouble(Vector3f::lengthSquared));
      for(var d:differences) {
         if(d.lengthSquared()<1E-12)continue;Vector3f n=new Vector3f(d).normalize();
         if(edges.isEmpty() || (edges.size()==1 && Math.abs(edges.get(0).dot(n))<.01)
            || (edges.size()==2 && Math.abs(edges.get(0).dot(n))<.01 && Math.abs(edges.get(1).dot(n))<.01))edges.add(n);
         if(edges.size()==3)break;
      }
      List<Vector3f> axes=new ArrayList<>(List.of(xyz));axes.addAll(edges);
      for(var e:edges)for(var a:xyz)axes.add(new Vector3f(e).cross(a));
      for(var a:axes) {
         if(a.lengthSquared()<1E-10)continue;
         float pmin=Float.POSITIVE_INFINITY,pmax=Float.NEGATIVE_INFINITY;
         for(var v:p){float d=v.dot(a);pmin=Math.min(pmin,d);pmax=Math.max(pmax,d);}
         float centre=new Vector3f(min).add(max).mul(.5F).dot(a);
         Vector3f half=new Vector3f(max).sub(min).mul(.5F);
         float r=Math.abs(a.x)*half.x+Math.abs(a.y)*half.y+Math.abs(a.z)*half.z;
         if(pmax<=centre-r||pmin>=centre+r)return false;
      }return true;
   }
}
