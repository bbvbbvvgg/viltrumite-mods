package dev.baranhan.viltrumitecore.hero.reinhard.ability;
import dev.baranhan.viltrumitecore.hero.*;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.*;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/** Crouch + the world-cut slot owns a spatial frame; detached clock/budget are reused. */
public final class DiagonalWorldCut implements AbilityHandler {
   @Override public boolean tryStart(ServerPlayer player,ReinhardState state) {
      ServerLevel level=player.serverLevel();
      if(!state.reid.drawn()||WorldCutManager.get(level).size()>=2) return false;
      DiagonalCutGeometry shape=new DiagonalCutGeometry(player.position().add(0,1,0),
         Vec3.directionFromRotation(Math.max(-35,Math.min(35,player.getXRot())),player.getYRot()),level.getGameTime());
      Live live=new Live(level,UUID.randomUUID(),player.getUUID(),shape,level.getGameTime());
      state.cooldowns[ReinhardAbility.DIAGONAL_WORLD_CUT.ordinal()]=ReinhardRules.WORLD_CUT_COOLDOWN;
      state.action.start(ReinhardAbility.DIAGONAL_WORLD_CUT,ReinhardRules.WORLD_CUT_BLAST+20,-1);
      state.worldCut=WorldCut.timed(live.start,live); WorldCutManager.get(level).submit(state.worldCut);
      state.cutAnchor=player.position();
      live.send(0);return true;
   }
   private static final class Live implements WorldCut.Environment {
      final ServerLevel level; final UUID id,caster; final DiagonalCutGeometry shape; final long start;
      final long telemetryStart=System.nanoTime();
      WorldCutCarve carve; final Map<UUID,Entity> targets=new HashMap<>();
      Live(ServerLevel l,UUID id,UUID caster,DiagonalCutGeometry s,long start) {
         this.level=l;this.id=id;this.caster=caster;this.shape=s;this.start=start;
      }
      void send(int phase) {
         DiagonalCutS2CPacket packet=new DiagonalCutS2CPacket(id,caster,shape,start,phase);
         for(ServerPlayer p:level.players()) if(shape.bounds().inflate(ReinhardRules.WORLD_CUT_VIEW_RANGE).contains(p.position()))
            CoreMessages.sendToPlayer(packet,p);
      }
      public void blast() {
         carve=new WorldCutCarve(shape.wave(level.getMinBuildHeight(),level.getMaxBuildHeight()),null,shape.origin(),shape.forward());
         CarveJobs.submit(level,carve);send(1);
         ReinhardLines.blast(level,caster);
      }
      public boolean pendingWork() { return carve!=null&&CarveJobs.pending(level,carve); }
      public int carveFront() { return carve==null?0:carve.frontAlong(); }
      public void progress(long elapsed,boolean done) { if(done)CutTelemetry.done("diagonal",id,level,elapsed,telemetryStart,carve); send(done?3:2); }
      public Collection<UUID> targets(int through) {
         targets.clear();
         for(Entity e:level.getEntities((Entity)null,shape.bounds(),e->e instanceof LivingEntity||e instanceof PartEntity<?>)) {
            Entity root=e instanceof PartEntity<?> p?p.getParent():e;
            if(!root.getUUID().equals(caster)&&shape.intersects(e.getBoundingBox(),through)) targets.put(root.getUUID(),root);
         }
         return targets.keySet();
      }
      public void strike(UUID id) {
         ServerPlayer p=level.getServer().getPlayerList().getPlayer(caster);
         if(p!=null&&(!p.isAlive()||p.serverLevel()!=level))p=null;
         Annihilation.strike(p,targets.get(id));
      }
   }
}
