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
      return HALF_SPAN*wiggle(along)*taper(along);
   }
   private static double taper(double along) {
      return Math.max(0,Math.min(1,Math.min((along+4)/20,(LENGTH-along+4)/28)));
   }
   private double wiggle(double along) { return .88+.12*Math.sin(along*.065+(seed&1023)); }
   /** Upper bound of {@link #width} on [lo, hi]: the taper rises then falls (max at an end or the flat
    *  top); the wiggle peaks at an end or at an interior crest. Never smaller than any sampled width. */
   public double maxWidth(double lo,double hi) {
      if(hi<lo) return 0;
      double taper=Math.max(0,Math.min(1,Math.min((hi+4)/20,(LENGTH-lo+4)/28)));
      double phase=seed&1023, crest=Math.ceil((lo*.065+phase-Math.PI/2)/(2*Math.PI));
      double crestAt=(Math.PI/2+2*Math.PI*crest-phase)/.065;
      double wiggle=crestAt<=hi ? 1 : Math.max(wiggle(lo),wiggle(hi));
      return HALF_SPAN*wiggle*taper;
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
      double lo=Math.max(0,a-ra), hi=Math.min(Math.min(through,LENGTH),a+ra);
      if(a+ra<0 || a-ra>=through || a-ra>=LENGTH || hi<lo) return false;
      // The rendered and carved wound tapers at both ends: test the local width, not HALF_SPAN.
      return Math.abs(c.dot(span()))<=maxWidth(lo,hi)+radius(h,span())
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
      // Y stays in the projected grid: every emitted candidate obeys build height.
      int axis=Math.abs(n.x)>=Math.abs(n.z)?0:2;
      final int depthAxis=axis, uAxis=(axis+1)%3, vAxis=(axis+2)%3;
      int[] lo={(int)Math.floor(b.minX),Math.max(minY,(int)Math.floor(b.minY)),(int)Math.floor(b.minZ)};
      int[] hi={(int)Math.ceil(b.maxX),Math.min(maxY,(int)Math.ceil(b.maxY)),(int)Math.ceil(b.maxZ)};
      double depth=HALF_THICKNESS/Math.abs(nv[axis]);
      int samples=(int)Math.ceil(2*depth)+1;
      // Project the finite OBB, not its much larger AABB. At most ~400 rows x 12
      // fixed edges: bounded setup, no voxel search outside CarveJobs' budget.
      double[][] corners=new double[8][];
      for(int k=0;k<8;k++) {
         Vec3 c=point((k&1)==0?0:LENGTH,(k&2)==0?-HALF_SPAN:HALF_SPAN,(k&4)==0?-HALF_THICKNESS:HALF_THICKNESS);
         double[] xyz={c.x,c.y,c.z};corners[k]=new double[]{xyz[uAxis],xyz[vAxis]};
      }
      record Row(int u,int v,int columns,int end) {}
      java.util.List<Row> rows=new java.util.ArrayList<>();int cells=0;
      if(minY<maxY)for(int u=lo[uAxis];u<hi[uAxis];u++) {
         double at=u+.5,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
         for(int k=0;k<8;k++)for(int bit=1;bit<=4;bit*=2)if((k&bit)==0) {
            double[] c=corners[k],d=corners[k|bit];double delta=d[0]-c[0];
            if(Math.abs(delta)<1E-10) {
               if(Math.abs(at-c[0])<1E-8) {min=Math.min(min,Math.min(c[1],d[1]));max=Math.max(max,Math.max(c[1],d[1]));}
            } else {
               double t=(at-c[0])/delta;
               if(t>=-1E-10 && t<=1+1E-10) {double v=c[1]+t*(d[1]-c[1]);min=Math.min(min,v);max=Math.max(max,v);}
            }
         }
         if(!Double.isFinite(min))continue;
         int first=Math.max(lo[vAxis],(int)Math.ceil(min-.5-1E-8));
         int last=Math.min(hi[vAxis],(int)Math.floor(max-.5+1E-8)+1);
         if(last<=first)continue;
         cells=Math.addExact(cells,Math.multiplyExact(last-first,samples));rows.add(new Row(u,first,last-first,cells));
      }
      final int total=cells;
      return new WorldCutGeometry.Wave() {
         int index,front,rowIndex;
         public boolean hasNext() { return index<total; }
         public int remainingWork() { return total-index; }
         public int frontAlong() { return front; }
         public boolean carve(WorldCutGeometry.Cell c) {
            double x=c.x()+.5-origin.x, y=c.y()+.5-origin.y, z=c.z()+.5-origin.z;
            double a=x*forward.x+y*forward.y+z*forward.z;
            return c.y()>=minY && c.y()<maxY && a>=0 && a<LENGTH && Math.abs(x*span.x+y*span.y+z*span.z)<=width(a)
               && Math.abs(x*n.x+y*n.y+z*n.z)<=HALF_THICKNESS;
         }
         public WorldCutGeometry.Cell next() {
            if(!hasNext())throw new NoSuchElementException();
            int q=index++;
            if(q>=rows.get(rowIndex).end())rowIndex++;
            Row row=rows.get(rowIndex);int base=rowIndex==0?0:rows.get(rowIndex-1).end();
            int u=row.u(),v=row.v()+(q-base)/samples;
            double center=ov[depthAxis]-(nv[uAxis]*(u+.5-ov[uAxis])+nv[vAxis]*(v+.5-ov[vAxis]))/nv[depthAxis];
            int d=(int)Math.ceil(center-.5-depth-1E-8)+(q-base)%samples;
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
