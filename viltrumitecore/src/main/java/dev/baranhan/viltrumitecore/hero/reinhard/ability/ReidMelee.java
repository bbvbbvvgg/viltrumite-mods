package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Ordinary drawn-Reid strokes, no carve, forced combo, input lock or cinematic kill.
 * Collision tracks are baked from the production final blade transform, including
 * body animation, axial roll, scale and crouch; a regression compares the bake.
 * Runtime loads no client classes. Eight blade segments per authored tick cap work.
 */
public final class ReidMelee implements AbilityHandler {
   private static final String RESOURCE = "/data/viltrumitecore/reinhard/reid_melee.json";
   private static final class Samples {
      static final double[][][] TRACKS = load();
      static double[][][] load() {
         try (var stream = ReidMelee.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing Reid blade bake");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            double[][][] tracks = new double[root.size()][][];
            for (int i=0;i<root.size();i++) {
               var rows=root.get(i).getAsJsonArray(); tracks[i]=new double[rows.size()][6];
               for(int j=0;j<rows.size();j++) for(int k=0;k<6;k++) tracks[i][j][k]=rows.get(j).getAsJsonArray().get(k).getAsDouble();
            }
            return tracks;
         } catch (java.io.IOException e) { throw new IllegalStateException(e); }
      }
   }
   public static double[] sample(int side, boolean crouch, int index) {
      return Samples.TRACKS[(side==1?1:0)+(crouch?2:0)][Math.max(0,Math.min(160,index))].clone();
   }
   @Override public boolean tryStart(ServerPlayer p, ReinhardState s) {
      if (!s.reid.drawn() || s.action.running() || s.busy()) return false;
      int phase=p.isShiftKeyDown()?2:(s.meleeSide++ & 1);
      s.meleeHit.clear(); s.action.start(ReinhardAbility.REID_STRIKE,phase==2?20:14,-1,phase);
      return true;
   }
   private static Vec3 world(ServerPlayer p,double[] row,int off) {
      // Same entity frame and player scale as ReinhardRenderer.setupRotations.
      double yaw=Math.toRadians(180-p.yBodyRot),c=Math.cos(yaw),s=Math.sin(yaw);
      double x=row[off]*.9375,y=row[off+1]*.9375,z=row[off+2]*.9375;
      return p.position().add(c*x+s*z,y,-s*x+c*z);
   }
   @Override public void tick(ServerPlayer p, ReinhardState s) {
      if(!s.action.is(ReinhardAbility.REID_STRIKE))return;
      double rate=20.0/s.action.length();
      int first=(int)Math.floor((s.action.elapsed()-1)*rate*8)+1, last=(int)Math.floor(s.action.elapsed()*rate*8);
      if(last<32 || first>72)return;
      int phase=s.action.phase();
      for(int i=Math.max(32,first);i<=Math.min(72,last);i++) {
         double[] row=sample(phase,p.isCrouching(),i);
         Vec3 from=world(p,row,0),tip=world(p,row,3);
         var block=p.level().clip(new ClipContext(from,tip,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
         Vec3 to=block.getType()==HitResult.Type.MISS?tip:block.getLocation();
         if(i==40) ReinhardSounds.playAt(p,from,ReinhardSounds.CUT_RAIN.get(),.55F,phase==2?.8F:1.3F);
         for(Entity e:p.level().getEntities(p,new AABB(from,to).inflate(.12),e->ReinhardTargets.canHit(p,e))) {
            Entity root=ReinhardTargets.resolve(e);
            if(!(root instanceof LivingEntity target) || s.meleeHit.contains(root.getUUID())
               || !CutRain.segmentHits(e.getBoundingBox().inflate(.08),from,to))continue;
            s.meleeHit.add(root.getUUID());
            if(Strikes.hit(p,target,phase==2?14:8)) {
               Strikes.knockback(target,p.getLookAngle(),phase==2?.6:.25);
               Vec3 at=target.getBoundingBox().getCenter(); HeroFx.flash(p,at,.35F);
               HeroFx.blade(p,from,to,null);
               ReinhardSounds.playAt(p,at,ReinhardSounds.PARRY.get(),.6F,phase==2?.8F:1.2F);
            }
         }
      }
   }
   @Override public void cancel(ServerPlayer p, ReinhardState s, CleanupReason reason) { s.meleeHit.clear(); }
}
