package dev.baranhan.viltrumitecore.hero.reinhard.cut;

import java.util.NoSuchElementException;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A finite tilted 3-D plane, NOT a terrain-following ground trench. */
public record DiagonalCutGeometry(Vec3 origin, Vec3 forward, long seed) {
   public static final int LENGTH=200;
   public static final double HALF_SPAN=28, HALF_THICKNESS=2.25;
   public DiagonalCutGeometry {
      if (!finite(origin) || !finite(forward)) throw new IllegalArgumentException("non-finite diagonal cut");
      forward=forward.lengthSqr()<1E-8 ? new Vec3(0,0,1) : forward.normalize();
   }
   private static boolean finite(Vec3 p) { return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z); }
   public Vec3 right() {
      Vec3 r=forward.cross(new Vec3(0,1,0));
      return r.lengthSqr()<1E-8 ? new Vec3(1,0,0) : r.normalize();
   }
   public Vec3 span() { return right().add(right().cross(forward)).normalize(); }
   public Vec3 normal() { return forward.cross(span()).normalize(); }
   public Vec3 point(double along,double across,double depth) {
      return origin.add(forward.scale(along)).add(span().scale(across)).add(normal().scale(depth));
   }
   public double width(double along) {
      double t=Math.max(0,Math.min(1,Math.min((along+4)/20,(LENGTH-along+4)/28)));
      return HALF_SPAN*(.88+.12*Math.sin(along*.065+(seed&1023)))*t;
   }
   public boolean contains(Vec3 p) {
      Vec3 d=p.subtract(origin); double a=d.dot(forward);
      return a>=0 && a<LENGTH && Math.abs(d.dot(span()))<=width(a)
         && Math.abs(d.dot(normal()))<=HALF_THICKNESS;
   }
   /** Conservative OBB/AABB support test for entity extents; root de-dup is server-owned. */
   public boolean intersects(AABB box,int through) {
      Vec3 c=box.getCenter().subtract(origin);
      Vec3 h=new Vec3(box.getXsize()*.5,box.getYsize()*.5,box.getZsize()*.5);
      double a=c.dot(forward), ra=radius(h,forward);
      return a+ra>=0 && a-ra<through && Math.abs(c.dot(span()))<=HALF_SPAN+radius(h,span())
         && Math.abs(c.dot(normal()))<=HALF_THICKNESS+radius(h,normal());
   }
   private static double radius(Vec3 h,Vec3 n) { return h.x*Math.abs(n.x)+h.y*Math.abs(n.y)+h.z*Math.abs(n.z); }
   public AABB bounds() {
      Vec3 a=point(0,-HALF_SPAN,-HALF_THICKNESS); AABB b=new AABB(a,a);
      for(int end=0;end<=1;end++) for(int s=-1;s<=1;s+=2) for(int n=-1;n<=1;n+=2) {
         Vec3 p=point(end*LENGTH,s*HALF_SPAN,n*HALF_THICKNESS); b=b.minmax(new AABB(p,p));
      }
      return b.inflate(.5);
   }
   /** Raster columns parallel to the dominant normal. No unbudgeted search or full-volume scan.
    * Each column has a fixed conservative number of depth samples. The exact mask remains
    * authoritative. This covers every original voxel once, including fractional origins.
    */
   public WorldCutGeometry.Wave wave(int minY,int maxY) {
      AABB b=bounds();
      Vec3 n=normal(), span=span();
      double[] nv={n.x,n.y,n.z}, ov={origin.x,origin.y,origin.z};
      // Keep Y in the projected grid so every emitted candidate is build-height clipped.
      int axis=Math.abs(n.x)>=Math.abs(n.z)?0:2;
      final int depthAxis=axis, uAxis=(axis+1)%3, vAxis=(axis+2)%3;
      int[] lo={(int)Math.floor(b.minX),Math.max(minY,(int)Math.floor(b.minY)),(int)Math.floor(b.minZ)};
      int[] hi={(int)Math.ceil(b.maxX),Math.min(maxY,(int)Math.ceil(b.maxY)),(int)Math.ceil(b.maxZ)};
      int radius=(int)Math.ceil(HALF_THICKNESS/Math.abs(nv[axis]))+1;
      int samples=2*radius+1;
      int us=Math.max(0,hi[uAxis]-lo[uAxis]), vs=Math.max(0,hi[vAxis]-lo[vAxis]);
      int total=minY>=maxY?0:Math.multiplyExact(Math.multiplyExact(us,vs),samples);
      return new WorldCutGeometry.Wave() {
         int index,front;
         public boolean hasNext() { return index<total; }
         public int remainingWork() { return total-index; }
         public int frontAlong() { return front; }
         public boolean carve(WorldCutGeometry.Cell c) {
            double x=c.x()+.5-origin.x, y=c.y()+.5-origin.y, z=c.z()+.5-origin.z;
            double a=x*forward.x+y*forward.y+z*forward.z;
            return a>=0 && a<LENGTH && Math.abs(x*span.x+y*span.y+z*span.z)<=width(a)
               && Math.abs(x*n.x+y*n.y+z*n.z)<=HALF_THICKNESS;
         }
         public WorldCutGeometry.Cell next() {
            if(!hasNext())throw new NoSuchElementException();
            int q=index++,column=q/samples;
            int u=lo[uAxis]+column/vs,v=lo[vAxis]+column%vs;
            double center=ov[depthAxis]-(nv[uAxis]*(u+.5-ov[uAxis])+nv[vAxis]*(v+.5-ov[vAxis]))/nv[depthAxis];
            int d=(int)Math.floor(center-.5)+q%samples-radius;
            int x=depthAxis==0?d:uAxis==0?u:v;
            int y=depthAxis==1?d:uAxis==1?u:v;
            int z=depthAxis==2?d:uAxis==2?u:v;
            int along=(int)Math.max(0,Math.min(LENGTH-1,(x+.5-origin.x)*forward.x+(y+.5-origin.y)*forward.y+(z+.5-origin.z)*forward.z));
            // Conservative progress: projections are not sorted in forward order. Do not
            // claim an unvisited prefix for entity damage. Completion releases all targets.
            if(index==total)front=LENGTH;
            return new WorldCutGeometry.Cell(x,y,z,along,99);
         }
      };
   }
}
