package dev.baranhan.viltrumitecore.hero.reinhard.qa;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.hero.*;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.*;
import dev.baranhan.viltrumitecore.hero.ironman.*;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.homelander.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Opt-in operator QA harness. No command is available in a normal game.
 * It invokes production handlers, never substitutes a client animation for server damage. */
@EventBusSubscriber(modid="viltrumitecore")
public final class ReinhardQaCommands {
   private record Run(ServerPlayer target,ServerPlayer actor,String kind,long start,int arrowItems) {}
   private static final List<Run> RUNS=new ArrayList<>();
   private ReinhardQaCommands() {}
   @SubscribeEvent public static void register(RegisterCommandsEvent event) {
      if(!dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)return;
      event.getDispatcher().register(Commands.literal("reinhardqa").requires(s->s.hasPermission(2))
         .then(Commands.argument("target",EntityArgument.player()).then(Commands.argument("scenario",com.mojang.brigadier.arguments.StringArgumentType.word())
            .executes(c->run(EntityArgument.getPlayer(c,"target"),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"scenario"),null))
            .then(Commands.argument("actor",EntityArgument.player()).executes(c->run(EntityArgument.getPlayer(c,"target"),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"scenario"),EntityArgument.getPlayer(c,"actor")))))));
   }
   private static void ready(ServerPlayer p,boolean drawn) {
      HeroRegistry.changeHero(p,HeroId.REINHARD);
      var s=ReinhardState.ensure(p);ReinhardActions.cleanup(p,s,null);Arrays.fill(s.cooldowns,0);s.modeCooldown=0;
      s.mode=drawn?ReinhardMode.HOLY_SWORD:ReinhardMode.RESTRAINED;
      var tag=new net.minecraft.nbt.CompoundTag();tag.putString("Phase",drawn?"DRAWN":"SHEATHED");s.reid.load(tag);
   }
   private static int arrows(ServerPlayer p) {return p.getInventory().countItem(net.minecraft.world.item.Items.ARROW);}
   private static int run(ServerPlayer p,String scenario,ServerPlayer source) {
      var state=ReinhardState.ensure(p);
      if(scenario.equals("state")) {status(p,"manual",0);return 1;}
      if(scenario.startsWith("mode_")) {
         ready(p,scenario.equals("mode_holy"));state=ReinhardState.ensure(p);
         if(scenario.equals("mode_knight"))state.mode=ReinhardMode.KNIGHT;
         return 1;
      }
      if(Set.of("ground","diagonal","both").contains(scenario)) {
         ready(p,true);state=ReinhardState.ensure(p);
         boolean ok=scenario.equals("diagonal")?new DiagonalWorldCut().tryStart(p,state):new WorldCut().tryStart(p,state);
         if(scenario.equals("both"))ok=new DiagonalWorldCut().tryStart(p,state)&&ok;
         LogUtils.getLogger().info("CUT_QA,CAST,{},{},{},{}",scenario,p.level().getGameTime(),ok,p.position());return ok?1:0;
      }
      if(Set.of("draw","sheathe","abort").contains(scenario)) {
         // REINHARD OVERHAUL item 15: the production Reid toggle (draw from Holy Sword / sheathe) and a control change mid-draw (ABORT).
         if(scenario.equals("draw")) {ready(p,false);state=ReinhardState.ensure(p);state.mode=ReinhardMode.HOLY_SWORD;}
         var toggle=new dev.baranhan.viltrumitecore.hero.reinhard.reid.ReidToggle();
         boolean ok=true;
         if(scenario.equals("abort"))toggle.cancel(p,state,null);else ok=toggle.tryStart(p,state);
         LogUtils.getLogger().info("REID_QA,{},{},{},{}",scenario,p.level().getGameTime(),ok,state.reid.phase());return ok?1:0;
      }
      // Keep a currently drawn sword/action for defense-composition tests.
      HeroRegistry.changeHero(p,HeroId.REINHARD);p.setGameMode(GameType.SURVIVAL);
      Vec3 facing=p.getLookAngle();
      ServerPlayer actor=source==null?FakePlayerFactory.get(p.serverLevel(),new GameProfile(UUID.fromString("fef61c38-0ce7-4aee-a060-de96b2c8a941"),"ReinhardQASource")):source;
      actor.setPos(p.position().add(facing.scale(6)));actor.setYRot(p.getYRot()+180);actor.setYHeadRot(actor.getYRot());actor.yBodyRot=actor.getYRot();actor.setXRot(-p.getXRot());
      RUNS.removeIf(r->r.actor==actor);
      HeroRegistry.changeHero(actor,HeroId.HUMAN);
      int inventory=arrows(p);
      switch(scenario) {
         case "arrow" -> {
            Arrow arrow=new Arrow(EntityType.ARROW,p.level());if(source!=null)arrow.setOwner(actor);arrow.setPos(p.getEyePosition().add(facing.scale(3)).add(0,-.05,0));
            arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;arrow.setNoGravity(false);arrow.setDeltaMovement(facing.scale(-.8));p.serverLevel().addFreshEntity(arrow);
         }
         case "airblade" -> {
            // Regulus Reborn: the Air Severance sheet (released on its move's tick 15).
            HeroRegistry.changeHero(actor,HeroId.REGULUS);dev.baranhan.viltrumitecore.hero.regulus.RegulusHero.INSTANCE.handleInput(actor,dev.baranhan.viltrumitecore.hero.HeroAction.MANIA,true);
         }
         case "wave" -> dev.baranhan.viltrumitecore.hero.HeroAttackContext.hurt(p,p.damageSources().playerAttack(actor),10,dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.WAVE);
         case "repulsor" -> dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity.shoot(actor,actor.getEyePosition().add(0,-.2,0),facing.scale(-1),6,1,.2);
         case "missile" -> dev.baranhan.viltrumitecore.entity.MicroMissileEntity.launch(actor,actor.getEyePosition().add(0,-.2,0),facing.scale(-1),p);
         case "splash" -> {
            var at=p.position().add(facing.scale(1.5));var block=net.minecraft.core.BlockPos.containing(at);
            p.serverLevel().setBlockAndUpdate(block,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            // Strike the defender-facing block face: a wall must not shield this splash fixture.
            var start=block.getCenter().subtract(facing.scale(.8));
            dev.baranhan.viltrumitecore.entity.MicroMissileEntity.launch(actor,start,facing,null);
         }
         case "melee", "weapon", "heavy" -> {
            actor.setPos(p.position().add(facing.scale(1.8)));
            actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,scenario.equals("weapon")?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD):net.minecraft.world.item.ItemStack.EMPTY);
            p.hurt(p.damageSources().playerAttack(actor),scenario.equals("heavy")?25:6);
         }
         case "homelander" -> {HeroRegistry.changeHero(actor,HeroId.HOMELANDER);var h=HomelanderState.ensure(actor);h.laserHeld=true;h.laserAge=4;}
         case "unibeam","pulse","microlaser","gun","rocketfist" -> {
            HeroRegistry.changeHero(actor,HeroId.IRON_MAN);var iron=IronManState.ensure(actor);
            iron.suit.equipNow(switch(scenario) {case "microlaser" -> MarkId.MARK_7;case "gun" -> MarkId.WAR_MACHINE_MK2;case "rocketfist" -> MarkId.MARK_42;default -> MarkId.MARK_17;});
            if(scenario.equals("unibeam")) {iron.beamDir=actor.getLookAngle();iron.unibeam.startBeam(false);}
            else if(scenario.equals("pulse"))new dev.baranhan.viltrumitecore.hero.ironman.mark.sig.PulseUnibeam().press(actor,iron);
            else if(scenario.equals("gun"))new dev.baranhan.viltrumitecore.hero.ironman.mark.sig.ShoulderGun().press(actor,iron);
            else if(scenario.equals("rocketfist"))new dev.baranhan.viltrumitecore.hero.ironman.mark.sig.RocketFist().press(actor,iron);
            else new dev.baranhan.viltrumitecore.hero.ironman.mark.sig.MicroLaser().press(actor,iron);
         }
         default -> {return 0;}
      }
      RUNS.add(new Run(p,actor,scenario,p.level().getGameTime(),inventory));return 1;
   }
   private static void status(ServerPlayer p,String kind,long age) {
      var s=ReinhardState.of(p);if(s==null)return;
      LogUtils.getLogger().info("DEFENSE_QA,{},{},{},{},{},{},{},{},{}",kind,age,s.defense.kind(),s.defense.age(),s.defense.leftHand(),s.arrowCapture.holding(),arrows(p),p.getHealth(),s.action.elapsed());
   }
   @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
      if(event.phase!=TickEvent.Phase.END)return;
      RUNS.removeIf(r->{
         long age=r.target.level().getGameTime()-r.start;
         if(r.actor instanceof net.minecraftforge.common.util.FakePlayer && Set.of("homelander","unibeam","pulse","microlaser","gun","rocketfist").contains(r.kind))HeroRegistry.get(r.actor).tick(r.actor);
         if(age<=20 || age%10==0)status(r.target,r.kind,age);
         if(age<=20 && Set.of("repulsor","missile").contains(r.kind)) {
            for(var projectile:r.target.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,r.target.getBoundingBox().inflate(20)))
               LogUtils.getLogger().info("PROJECTILE_QA,{},{},{},{},{},{}",r.kind,age,projectile.getId(),projectile.getOwner()==r.target,
                  projectile instanceof dev.baranhan.viltrumitecore.entity.Homing h && h.homingTarget()==r.actor,projectile.position());
         }
         if(r.kind.equals("arrow") && age<=20) {
            var s=ReinhardState.of(r.target);
            if(s!=null && s.arrowCapture.holding() && arrows(r.target)!=r.arrowItems)throw new IllegalStateException("Held arrow was picked up");
         }
         if(age<100)return false;
         HeroRegistry.changeHero(r.actor,HeroId.HUMAN);return true;
      });
   }
   @SubscribeEvent public static void stopped(ServerStoppedEvent e) {RUNS.clear();}
}
