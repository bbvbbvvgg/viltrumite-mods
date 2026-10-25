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
   /** Every AABB candidate, including mask rejects, consumes one queue visit. O(1) memory. */
   public WorldCutGeometry.Wave wave(int minY,int maxY) {
      AABB b=bounds();
      int x0=(int)Math.floor(b.minX),x1=(int)Math.ceil(b.maxX);
      int y0=Math.max(minY,(int)Math.floor(b.minY)),y1=Math.min(maxY,(int)Math.ceil(b.maxY));
      int z0=(int)Math.floor(b.minZ),z1=(int)Math.ceil(b.maxZ);
      boolean majorX=Math.abs(forward.x)>=Math.abs(forward.z);
      int u0=majorX?x0:z0, u1=majorX?x1:z1, v0=majorX?z0:x0,v1=majorX?z1:x1;
      boolean reverse=(majorX?forward.x:forward.z)<0;
      int total=Math.max(0,u1-u0)*Math.max(0,v1-v0)*Math.max(0,y1-y0);
      return new WorldCutGeometry.Wave() {
         int index,front;
         public boolean hasNext() { return index<total; }
         public int remainingWork() { return total-index; }
         public int frontAlong() { return front; }
         public boolean carve(WorldCutGeometry.Cell c) { return contains(new Vec3(c.x()+.5,c.y()+.5,c.z()+.5)); }
         public WorldCutGeometry.Cell next() {
            if(!hasNext()) throw new NoSuchElementException();
            int h=y1-y0,w=v1-v0, q=index++;
            int u=q/(h*w),v=q/h%w,y=y1-1-q%h;
            u=reverse?u1-1-u:u0+u;
            int x=majorX?u:v0+v,z=majorX?v0+v:u;
            int along=(int)Math.max(0,Math.min(LENGTH-1,new Vec3(x+.5,y+.5,z+.5).subtract(origin).dot(forward)));
            front=Math.max(front,along+1);
            return new WorldCutGeometry.Cell(x,y,z,along,99);
         }
      };
   }
}
