package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.*;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import dev.baranhan.viltrumitecore.hero.reinhard.limbo.Limbo;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket;
import java.util.*;
import java.util.function.*;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.entity.PartEntity;

/** Server-owned cinematic, one immutable verdict, one removal attempt. No damage or terrain operations. */
public final class WorldErasure implements AbilityHandler {
   public enum TargetKind { MOB, DRAGON, PLAYER }
   public interface Environment {
      boolean targetPresent();
      TargetKind targetKind();
      void markDragonKilled();
      void discard();
      void erasePlayer();
      void end(WorldPause.EndReason reason);
   }
   /** {@code chopping}: the shared Lethal Chop is still running (its damage, pose and VFX must not overlap the cast). */
   public record Gate(boolean target,boolean pauseActive,boolean creative,boolean spectator,boolean limbo,boolean drawn,boolean chopping) {
      public Gate(boolean target,boolean pauseActive,boolean creative,boolean spectator,boolean limbo,boolean drawn) {
         this(target,pauseActive,creative,spectator,limbo,drawn,false);
      }
   }
   private final DivineTargets.Verdict verdict;
   private final Environment environment;
   private boolean attempted,ended;
   private UUID pauseId;
   public WorldErasure() {this(null,null);}
   private WorldErasure(DivineTargets.Verdict verdict,Environment environment) {this.verdict=verdict;this.environment=environment;}
   public static WorldErasure timed(DivineTargets.Verdict verdict,Environment environment) {
      return new WorldErasure(Objects.requireNonNull(verdict),Objects.requireNonNull(environment));
   }
   public boolean erase() {return verdict==DivineTargets.Verdict.MORTAL;}
   /** The scene starts with the press: the world stops on tick 0 and the line is spoken inside the scene. */
   public static int cinematicStart() {return 0;}
   /** Pure: scene age for a pause tick. One clock: the WorldPause elapsed tick. */
   public static int cinematicAge(int pauseElapsed) {return pauseElapsed-cinematicStart();}
   public boolean ended() {return ended;}
   public static boolean tryStart(ReinhardState state,Gate gate,BooleanSupplier beginPause) {
      if(!gate.target() || gate.pauseActive() || gate.creative() || gate.spectator() || gate.limbo() || !gate.drawn() || gate.chopping()
         || state.cooldowns[ReinhardAbility.WORLD_ERASURE.ordinal()]>0 || state.busy()) return false;
      if(!beginPause.getAsBoolean()) return false;
      state.cooldowns[ReinhardAbility.WORLD_ERASURE.ordinal()]=ReinhardRules.ERASE_COOLDOWN;
      state.action.start(ReinhardAbility.WORLD_ERASURE,ReinhardRules.ERASE_LENGTH,-1);
      return true;
   }
   public boolean advance(int age) {
      if(ended) return true;
      if(age>=ReinhardRules.ERASE_REMOVE_TICK && !attempted) {
         attempted=true;
         if(erase() && environment.targetPresent()) {
            switch(environment.targetKind()) {
               case PLAYER -> environment.erasePlayer();
               case DRAGON -> {environment.markDragonKilled();environment.discard();}
               case MOB -> environment.discard();
            }
         }
      }
      if(age>=ReinhardRules.ERASE_LENGTH) {ended=true;environment.end(WorldPause.EndReason.DONE);}
      return ended;
   }
   public void interrupt(WorldPause.EndReason reason) {
      if(ended) return; ended=true; environment.end(reason);
   }
   /** Entity parts are target surfaces, never independent victims. */
   public static <T> T root(T candidate,Function<T,T> parent) {
      T result=candidate,next;
      while((next=parent.apply(result))!=null && next!=result) result=next;
      return result;
   }
   @Nullable private static LivingEntity aimedTarget(ServerPlayer player) {
      Vec3 from=player.getEyePosition(),delta=player.getLookAngle().scale(ReinhardRules.ERASE_RANGE),to=from.add(delta);
      BlockHitResult block=player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
      double distance=block.getType()==HitResult.Type.MISS?delta.lengthSqr():from.distanceToSqr(block.getLocation());
      EntityHitResult hit=ProjectileUtil.getEntityHitResult(player,from,to,player.getBoundingBox().expandTowards(delta).inflate(1),
         e->e!=player && e.isPickable() && root(e,part->part instanceof PartEntity<?> p?p.getParent():null) instanceof LivingEntity living && living.isAlive(),distance);
      if(hit==null) return null;
      Entity resolved=root(hit.getEntity(),part->part instanceof PartEntity<?> p?p.getParent():null);
      return resolved instanceof LivingEntity living?living:null;
   }
   @Override public boolean tryStart(ServerPlayer player,ReinhardState state) {
      MinecraftServer server=player.getServer(); if(server==null) return false;
      LivingEntity target=aimedTarget(player);
      if(target instanceof ServerPlayer && !Limbo.available(server)) return false;
      Gate gate=new Gate(target!=null,WorldPause.active(server),target instanceof ServerPlayer p && p.isCreative(),
         target instanceof ServerPlayer p && p.isSpectator(),target instanceof ServerPlayer p && BodyIsolation.active(p),state.reid.drawn(),
         player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer core && core.getChopTicks()>0);
      if(target==null || gate.pauseActive() || gate.creative() || gate.spectator() || gate.limbo() || !gate.drawn() || gate.chopping()) return false;
      DivineTargets.Verdict frozen=DivineTargets.verdict(target);
      if(!tryStart(state,gate,()->WorldPause.begin(server,player.getUUID(),ReinhardRules.ERASE_LENGTH,
         ReinhardRules.ERASE_PROTECT_FROZEN,ReinhardRules.ERASE_MOB_FREEZE_RADIUS))) return false;
      // The world stops on the press (absolute stop, camera lock, HUD) for everyone, Reinhard
      // included: no clip plays, his body stays in the exact pose of the press. Every client starts
      // the scene now.
      state.action.cancel();
      Live live=new Live(server,player,target,WorldPause.id(server));
      WorldErasure cast=timed(frozen,live); cast.pauseId=live.pauseId; state.worldErasure=cast;
      live.packet=new ErasureS2CPacket(live.pauseId,server.overworld().getGameTime(),target.level().dimension(),target.getId(),target.position(),
         target.getBbWidth(),target.getBbHeight(),target instanceof net.minecraft.world.entity.player.Player || target instanceof net.minecraft.world.entity.monster.Zombie || target instanceof net.minecraft.world.entity.monster.AbstractSkeleton || target instanceof net.minecraft.world.entity.monster.AbstractIllager || target instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin || target instanceof net.minecraft.world.entity.monster.Witch || target instanceof AbstractVillager,
         cast.erase(),0);
      WorldPause.listen(server,new WorldPause.Listener() {
         public void tick(int elapsed) {
            live.holdCaster();
            int age=cinematicAge(elapsed);
            if(age<0) return;
            cast.advance(age);
         }
         public void ended(WorldPause.EndReason reason) {
            cast.interrupt(reason); state.worldErasure=null; state.action.cancel();
         }
         public void onJoin(ServerPlayer viewer,int elapsed) {
            int age=cinematicAge(elapsed);
            if(age>=0) CoreMessages.sendToPlayer(live.packet.withAge(age),viewer);
         }
      });
      // Scene age 0 for everyone now (late joiners and dimension changes catch up with withAge).
      for(ServerPlayer viewer:server.getPlayerList().getPlayers()) CoreMessages.sendToPlayer(live.packet,viewer);
      return true;
   }
   @Override public void cancel(ServerPlayer player,ReinhardState state,CleanupReason reason) {
      WorldErasure cast=state.worldErasure;
      if(cast!=null && !cast.ended && cast.pauseId!=null && cast.pauseId.equals(WorldPause.id(player.getServer())))
         cast.interrupt(WorldPause.EndReason.OWNER_LEFT);
   }
   private static final class Live implements Environment {
      final MinecraftServer server;final UUID casterId,targetId,pauseId;final ServerLevel targetLevel;
      final Vec3 casterAnchor; ErasureS2CPacket packet;LivingEntity target;
      Live(MinecraftServer server,ServerPlayer caster,LivingEntity target,UUID id) {
         this.server=server;this.casterId=caster.getUUID();this.casterAnchor=caster.position();this.targetId=target.getUUID();
         this.targetLevel=(ServerLevel)target.level();this.pauseId=id;
      }
      void holdCaster() {
         ServerPlayer caster=server.getPlayerList().getPlayer(casterId);if(caster==null)return;
         caster.setDeltaMovement(Vec3.ZERO);
         if(caster.distanceToSqr(casterAnchor)>.01) caster.connection.teleport(casterAnchor.x,casterAnchor.y,casterAnchor.z,caster.getYRot(),caster.getXRot());
      }
      public boolean targetPresent() {
         Entity found=targetLevel.getEntity(targetId); target=found instanceof LivingEntity l && l.isAlive()?l:null;
         return target!=null && (!(target instanceof ServerPlayer p) || server.getPlayerList().getPlayer(targetId)==p);
      }
      public TargetKind targetKind() {return target instanceof ServerPlayer?TargetKind.PLAYER:target instanceof EnderDragon?TargetKind.DRAGON:TargetKind.MOB;}
      public void markDragonKilled() {
         if(target instanceof EnderDragon dragon && targetLevel.getDragonFight()!=null)
            QuietDragonCompletion.markKilled(targetLevel.getDragonFight(),dragon);
      }
      public void discard() {target.discard();}
      public void erasePlayer() {ServerPlayer p=(ServerPlayer)target;HeroEvents.prepareErasure(p);Limbo.send(p);}
      public void end(WorldPause.EndReason reason) {WorldPause.end(server,reason);}
   }
}
