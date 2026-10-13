package dev.baranhan.viltrumitecore.hero.control;

import dev.baranhan.viltrumitecore.hero.HeldInputs;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** A single, bounded, server-wide cinematic pause. Controls remain level-owned. */
public final class WorldPause {
   private static final Coordinator<MinecraftServer> SESSIONS = new Coordinator<>();
   private WorldPause() {}
   public enum EndReason { DONE, OWNER_LEFT, SERVER_STOP, RELOAD, WATCHDOG }
   public interface Listener {
      void tick(int elapsed);
      void ended(EndReason reason);
      default void onJoin(ServerPlayer player, int elapsed) {}
   }
   public static boolean begin(MinecraftServer server, UUID owner, int maxTicks) {
      return begin(server, owner, maxTicks, true, 32);
   }
   public static boolean begin(MinecraftServer server, UUID owner, int maxTicks, boolean protect, int mobRadius) {
      ServerPlayer caster = server.getPlayerList().getPlayer(owner);
      if (caster == null || !caster.isAlive()) return false;
      Session session = SESSIONS.begin(server, owner, maxTicks, protect, mobRadius);
      if (session == null) return false;
      session.ownerLevel = caster.level().dimension();
      session.sync(new LiveBackend(server));
      for (ServerPlayer player : server.getPlayerList().getPlayers()) sendState(player, session);
      return true;
   }
   public static boolean active(MinecraftServer server) { return SESSIONS.get(server) != null; }
   @Nullable public static UUID id(MinecraftServer server) { Session s=SESSIONS.get(server); return s==null?null:s.id; }
   public static int elapsed(MinecraftServer server) { Session s=SESSIONS.get(server); return s==null?0:s.age; }
   public static void listen(MinecraftServer server, Listener listener) {
      Session s=SESSIONS.get(server); if(s!=null) s.listener=listener;
   }
   public static void tick(MinecraftServer server) {
      Session s=SESSIONS.get(server); if(s==null) return;
      ServerPlayer owner=server.getPlayerList().getPlayer(s.owner);
      if(owner==null || !owner.isAlive() || !owner.level().dimension().equals(s.ownerLevel)) {
         end(server,EndReason.OWNER_LEFT); return;
      }
      if(s.tick(new LiveBackend(server))) { finish(server,s,EndReason.WATCHDOG); return; }
      if(s.listener!=null) s.listener.tick(s.age);
   }
   public static void ownerLeft(MinecraftServer server,UUID owner) {
      Session s=SESSIONS.get(server); if(s!=null && s.owner.equals(owner)) end(server,EndReason.OWNER_LEFT);
   }
   public static void end(MinecraftServer server,EndReason reason) {
      Session s=SESSIONS.end(server,new LiveBackend(server),reason); if(s!=null) finish(server,s,reason);
   }
   private static void finish(MinecraftServer server,Session s,EndReason reason) {
      SESSIONS.remove(server);
      try { if(s.listener!=null) s.listener.ended(reason); }
      finally {
         for(ServerPlayer player:server.getPlayerList().getPlayers())
            CoreMessages.sendToPlayer(new WorldPauseS2CPacket(false,s.id,s.owner,0),player);
      }
   }
   public static void onJoin(ServerPlayer player) {
      MinecraftServer server=player.getServer(); if(server==null) return;
      Session s=SESSIONS.get(server); if(s==null) return;
      s.sync(new LiveBackend(server)); sendState(player,s);
      if(s.listener!=null) s.listener.onJoin(player,s.age);
   }
   private static void sendState(ServerPlayer player,Session s) {
      CoreMessages.sendToPlayer(new WorldPauseS2CPacket(true,s.id,s.owner,Math.max(0,s.maxTicks+40-s.age)),player);
   }
   public static boolean isPaused(Entity entity) {
      if(!(entity.level() instanceof ServerLevel level)) return false;
      Session s=SESSIONS.get(level.getServer()); return s!=null && s.isPaused(entity.getUUID());
   }
   public static boolean isProtected(Entity entity) {
      if(!(entity.level() instanceof ServerLevel level)) return false;
      Session s=SESSIONS.get(level.getServer()); return s!=null && s.protect && s.isPaused(entity.getUUID());
   }
   /** Hide only this pause's anchor decoration, not another owner's stasis/freeze. */
   public static boolean hidesAnchor(boolean active,@Nullable UUID pause,@Nullable UUID effect) {
      return active && pause!=null && pause.equals(effect);
   }
   record Target(UUID id,String level) {}
   interface Backend {
      List<Target> players();
      List<Target> hostiles(List<Target> players,int radius);
      long now(Target target);
      void freeze(Target target,UUID owner,UUID id,long until);
      void release(Target target,UUID id,ReleaseReason reason);
   }
   static final class Coordinator<K> {
      private final Map<K,Session> sessions=new HashMap<>();
      Session begin(K key,UUID owner,int ticks,boolean protect,int radius) {
         if(sessions.containsKey(key) || ticks<=0) return null;
         Session s=new Session(owner,UUID.randomUUID(),ticks,protect,radius); sessions.put(key,s); return s;
      }
      Session get(K key) {return sessions.get(key);}
      Session remove(K key) {return sessions.remove(key);}
      Session end(K key,Backend env,EndReason reason) {Session s=remove(key); if(s!=null) s.end(env,reason); return s;}
   }
   static final class Session {
      final UUID owner,id; final int maxTicks,mobRadius; final boolean protect;
      final Set<Target> tracked=new HashSet<>(); Set<UUID> paused=Set.of();
      ResourceKey<Level> ownerLevel; Listener listener; int age; boolean ended;
      Session(UUID owner,UUID id,int ticks,boolean protect,int radius) {
         this.owner=owner; this.id=id; this.maxTicks=ticks; this.protect=protect; this.mobRadius=radius;
      }
      boolean isPaused(UUID who) {return !ended && !owner.equals(who) && paused.contains(who);}
      void sync(Backend env) {
         if(ended) return;
         List<Target> players=env.players().stream().filter(t->!owner.equals(t.id())).toList();
         Set<Target> current=new HashSet<>(players);
         if(protect) current.addAll(env.hostiles(players,mobRadius));
         current.removeIf(t->owner.equals(t.id()));
         // Release transferred/left targets before the new level captures its flags.
         for(Target old:tracked) if(!current.contains(old)) env.release(old,id,ReleaseReason.INTERRUPTED);
         Set<UUID> ids=new HashSet<>();
         for(Target target:current) {ids.add(target.id()); env.freeze(target,owner,id,env.now(target)+5);}
         tracked.addAll(current); paused=Set.copyOf(ids);
      }
      boolean tick(Backend env) {
         if(ended) return true;
         sync(env); age++;
         if(age>=maxTicks+40) {end(env,EndReason.WATCHDOG); return true;} return false;
      }
      void end(Backend env,EndReason reason) {
         if(ended) return; ended=true; paused=Set.of();
         ReleaseReason release=reason==EndReason.DONE?ReleaseReason.EXPIRED:ReleaseReason.INTERRUPTED;
         for(Target target:tracked) env.release(target,id,release);
         tracked.clear();
      }
   }
   private record LiveBackend(MinecraftServer server) implements Backend {
      private ServerLevel level(String key) {
         return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(key)));
      }
      public List<Target> players() {
         return server.getPlayerList().getPlayers().stream().map(p->new Target(p.getUUID(),p.level().dimension().location().toString())).toList();
      }
      public List<Target> hostiles(List<Target> players,int radius) {
         Set<Target> result=new HashSet<>();
         for(Target key:players) {
            ServerLevel level=level(key.level()); Entity player=level==null?null:level.getEntity(key.id());
            if(player==null) continue;
            for(Monster mob:level.getEntitiesOfClass(Monster.class,player.getBoundingBox().inflate(radius),m->m.isAlive() && m.distanceToSqr(player)<=radius*radius))
               result.add(new Target(mob.getUUID(),key.level()));
         }
         return List.copyOf(result);
      }
      public long now(Target target) {ServerLevel level=level(target.level()); return level==null?0:level.getGameTime();}
      public void freeze(Target key,UUID owner,UUID id,long until) {
         ServerLevel level=level(key.level()); Entity e=level==null?null:level.getEntity(key.id());
         if(!(e instanceof LivingEntity target)) return;
         ControlManager.restorePendingTarget(target);
         if(target instanceof ServerPlayer player) {HeldInputs.releaseAll(player); player.stopUsingItem();}
         ControlManager cm=ControlManager.get(level);
         if(!cm.refresh(key.id(),owner,id,ControlKind.FREEZE,until)) cm.tryAcquire(target,owner,id,ControlKind.FREEZE,until);
      }
      public void release(Target key,UUID id,ReleaseReason reason) {
         ServerLevel level=level(key.level()); if(level!=null) ControlManager.get(level).release(key.id(),id,reason);
      }
   }
}
