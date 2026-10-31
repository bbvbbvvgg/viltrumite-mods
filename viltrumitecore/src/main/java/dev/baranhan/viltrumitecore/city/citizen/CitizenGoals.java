package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

/**
 * The citizen behaviours that carry out the {@link CitizenBrain} state: panic (startle,
 * flee, cover, fall and get up), alert (stop and watch), get back onto the sidewalk, chat,
 * stroll (alone or with a companion, cautiously after a danger) and look at players.
 */
final class CitizenGoals {
   private CitizenGoals() {
   }

   private static boolean loaded(CitizenEntity c, int x, int z) {
      return c.level().hasChunk(x >> 4, z >> 4);
   }

   private static void lookAtThreat(CitizenEntity c, float speed) {
      if (c.hasThreat()) {
         c.getLookControl().setLookAt(c.threatX, c.threatY + 1.2, c.threatZ, speed, 60.0F);
      }
   }

   /** Turns the whole body toward the threat (the head alone only turns 75 degrees). */
   private static void faceThreat(CitizenEntity c, float maxStep) {
      if (!c.hasThreat()) {
         return;
      }

      float want = (float)Math.toDegrees(Math.atan2(c.threatZ - c.getZ(), c.threatX - c.getX())) - 90.0F;
      float delta = net.minecraft.util.Mth.wrapDegrees(want - c.getYRot());
      float step = net.minecraft.util.Mth.clamp(delta, -maxStep, maxStep);
      c.setYRot(c.getYRot() + step);
      c.yBodyRot = c.getYRot();
   }

   /** Everything a danger makes a person do: startle, run (several legs), hide or cower, fall and get up. */
   static final class Panic extends Goal {
      private final CitizenEntity c;
      private int repathIn;
      private int glance;

      Panic(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
      }

      @Override
      public boolean canUse() {
         return this.c.citizenState().panicked();
      }

      @Override
      public boolean canContinueToUse() {
         return this.c.citizenState().panicked();
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void start() {
         this.repathIn = 0;
         this.glance = 0;
      }

      @Override
      public void tick() {
         CityManager city = CityManager.of(this.c.level());
         switch (this.c.citizenState()) {
            case STARTLED -> {
               // the flinch: stop dead, turn to the danger
               this.c.getNavigation().stop();
               faceThreat(this.c, 30.0F);
               lookAtThreat(this.c, 40.0F);
            }
            case FALLEN -> {
               this.c.getNavigation().stop();
               if (--this.c.fallTicks <= 0) {
                  this.c.setCitizenState(CitizenState.GETTING_UP);
               }
            }
            case GETTING_UP -> {
               this.c.getNavigation().stop();
               if (this.c.stateTicks >= 18) {
                  this.c.legs = 0;
                  this.c.setCitizenState(CitizenState.FLEEING);
               }
            }
            case COVER -> {
               this.c.getNavigation().stop();
               // peek at the danger now and then
               if (++this.glance % 50 < 18) {
                  lookAtThreat(this.c, 10.0F);
               }
            }
            case FLEEING -> this.flee(city);
            default -> {
            }
         }
      }

      private void flee(CityManager city) {
         if (city == null) {
            return;
         }

         if (!this.c.hasThreat()) {
            this.c.setThreat(this.c.getX(), this.c.getY(), this.c.getZ(), 10.0F);
         }

         if (!this.c.getNavigation().isDone()) {
            // a look back over the shoulder now and then, otherwise eyes on the way
            if (++this.glance % 60 < 8) {
               lookAtThreat(this.c, 20.0F);
            }

            return;
         }

         if (this.c.fleeTarget != null) {
            // arrived somewhere: safe enough to hide here, or run on
            this.c.fleeTarget = null;
            this.c.legs++;
            double dx = this.c.getX() - this.c.threatX;
            double dz = this.c.getZ() - this.c.threatZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            BlockPos p = this.c.blockPosition();
            boolean far = d > this.c.threatRadius + this.c.traits().margin() + 6.0;
            if (far && CitizenBrain.covered(this.c, p.getX(), p.getY(), p.getZ()) || this.c.legs >= CitizenBrain.MAX_LEGS) {
               this.c.setCitizenState(CitizenState.COVER);
               return;
            }

            this.repathIn = this.c.getRandom().nextInt(6);
         }

         if (--this.repathIn > 0) {
            lookAtThreat(this.c, 30.0F);
            return;
         }

         long now = city.now();
         if (!PathBudget.tryAcquire(now, true)) {
            this.repathIn = 1;
            return;
         }

         Path path = CitizenBrain.fleePath(this.c, city, now);
         if (path == null) {
            // nowhere to go (cornered, off the grid): cower after a few failed tries
            this.repathIn = 8;
            if (++this.c.legs >= CitizenBrain.MAX_LEGS) {
               this.c.setCitizenState(CitizenState.COVER);
            }

            return;
         }

         this.c.fleeTarget = path.getTarget();
         this.c.getNavigation().moveTo(path, this.c.runSpeed());
      }

      @Override
      public void stop() {
         this.c.getNavigation().stop();
         this.c.fleeTarget = null;
         this.c.idleTicks = 20 + this.c.getRandom().nextInt(40);
      }
   }

   /** Noticed something: stand still, turn to it, watch (and keep one step away from walls of people). */
   static final class Alert extends Goal {
      private final CitizenEntity c;

      Alert(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         return this.c.citizenState() == CitizenState.ALERT;
      }

      @Override
      public boolean canContinueToUse() {
         return this.canUse();
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void start() {
         this.c.getNavigation().stop();
      }

      @Override
      public void tick() {
         faceThreat(this.c, 12.0F);
         lookAtThreat(this.c, 25.0F);
      }
   }

   /** Knocked off the sidewalk (into a yard, onto a car, into water): walk straight back. */
   static final class ReturnToGrid extends Goal {
      private final CitizenEntity c;
      private double tx;
      private double ty;
      private double tz;
      private int time;

      ReturnToGrid(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         if ((this.c.tickCount + this.c.getId()) % 20 != 0 || !this.c.getNavigation().isDone() || !this.c.citizenState().calm()) {
            return false;
         }

         if (!this.c.onGround() && !this.c.isInWater() || this.c.onGrid()) {
            this.c.offGridTicks = 0;
            return false;
         }

         this.c.offGridTicks += 20;
         CityManager city = CityManager.of(this.c.level());
         if (city == null) {
            return false;
         }

         if (this.c.offGridTicks > 400) {
            this.c.discard();
            return false;
         }

         BlockPos p = this.c.blockPosition();
         long cell = city.grid().nearestCell(p.getX(), p.getZ(), 10, y -> Math.abs(y - p.getY()) <= 2);
         if (cell == Long.MIN_VALUE) {
            return false;
         }

         this.tx = WalkGrid.unpackX(cell) + 0.5;
         this.tz = WalkGrid.unpackZ(cell) + 0.5;
         this.ty = city.grid().feetY(WalkGrid.unpackX(cell), WalkGrid.unpackZ(cell));
         return true;
      }

      @Override
      public void start() {
         this.time = 0;
         if (this.c.citizenState() == CitizenState.IDLE) {
            this.c.setCitizenState(CitizenState.WALKING);
         }
      }

      @Override
      public boolean canContinueToUse() {
         return this.time < 100 && this.c.citizenState().calm() && !this.c.onGrid() && this.c.distanceToSqr(this.tx, this.ty, this.tz) > 0.3;
      }

      @Override
      public void tick() {
         this.time++;
         this.c.getMoveControl().setWantedPosition(this.tx, this.ty, this.tz, this.c.walkSpeed());
      }

      @Override
      public void stop() {
         if (this.c.citizenState() == CitizenState.WALKING) {
            this.c.setCitizenState(CitizenState.IDLE);
         }
      }
   }

   /** Two people standing close stop for a chat: face each other, talk with their hands. */
   static final class Talk extends Goal {
      private final CitizenEntity c;
      private int time;

      Talk(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         if (this.c.citizenState() == CitizenState.TALKING && this.c.talkPartner != null) {
            return true; // the other one started the chat
         }

         if (this.c.citizenState() != CitizenState.IDLE || (this.c.tickCount + this.c.getId()) % 40 != 0
            || this.c.getRandom().nextFloat() > 0.15F + this.c.traits().sociability() * 0.35F) {
            return false;
         }

         CityManager city = CityManager.of(this.c.level());
         if (city == null) {
            return false;
         }

         CitizenEntity o = city.crowd().idleNear(this.c, 4.0);
         if (o == null || o.citizenState() != CitizenState.IDLE) {
            return false;
         }

         this.c.setCitizenState(CitizenState.TALKING);
         o.setCitizenState(CitizenState.TALKING);
         this.c.talkPartner = o;
         o.talkPartner = this.c;
         return true;
      }

      @Override
      public void start() {
         this.time = 80 + this.c.getRandom().nextInt(160);
         this.c.getNavigation().stop();
      }

      @Override
      public boolean canContinueToUse() {
         CitizenEntity o = this.c.talkPartner;
         return this.time > 0 && this.c.citizenState() == CitizenState.TALKING && o != null && o.isAlive()
            && o.talkPartner == this.c && o.distanceToSqr(this.c) < 36.0;
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void tick() {
         this.time--;
         CitizenEntity o = this.c.talkPartner;
         if (o != null) {
            this.c.getLookControl().setLookAt(o, 20.0F, 30.0F);
            float want = (float)Math.toDegrees(Math.atan2(o.getZ() - this.c.getZ(), o.getX() - this.c.getX())) - 90.0F;
            float delta = net.minecraft.util.Mth.wrapDegrees(want - this.c.getYRot());
            this.c.setYRot(this.c.getYRot() + net.minecraft.util.Mth.clamp(delta, -8.0F, 8.0F));
            this.c.yBodyRot = this.c.getYRot();
         }
      }

      @Override
      public void stop() {
         CitizenEntity o = this.c.talkPartner;
         if (this.c.citizenState() == CitizenState.TALKING) {
            this.c.setCitizenState(CitizenState.IDLE);
         }

         if (o != null && o.talkPartner == this.c && o.citizenState() == CitizenState.TALKING) {
            o.setCitizenState(CitizenState.IDLE);
            o.idleTicks = 20 + o.getRandom().nextInt(60);
         }

         this.c.talkPartner = null;
         this.c.idleTicks = 20 + this.c.getRandom().nextInt(60);
      }
   }

   /**
    * Idle a few seconds, then walk to a random sidewalk/plaza cell 8-24 blocks away (or
    * after the companion of a small group). After a danger the walk is slower and keeps
    * away from where the danger was.
    */
   static final class Stroll extends Goal {
      private final CitizenEntity c;
      private int time;
      private boolean following;

      Stroll(CitizenEntity c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE));
      }

      private boolean strollState() {
         CitizenState s = this.c.citizenState();
         return s == CitizenState.IDLE || s == CitizenState.WALKING || s == CitizenState.RECOVERING;
      }

      @Override
      public boolean canUse() {
         if (!this.strollState()) {
            return false;
         }

         CitizenEntity lead = this.c.leader;
         if (lead != null && (!lead.isAlive() || lead.distanceToSqr(this.c) > 24.0 * 24.0)) {
            this.c.leader = lead = null;
         }

         if (lead != null && lead.citizenState() == CitizenState.WALKING && !lead.getNavigation().isDone() && this.c.distanceToSqr(lead) > 4.0) {
            this.following = true;
            return (this.c.tickCount + this.c.getId()) % 10 == 0;
         }

         if (this.c.idleTicks > 0) {
            this.c.idleTicks--;
            return false;
         }

         CityManager city = CityManager.of(this.c.level());
         if (city == null || !this.c.onGrid()) {
            return false;
         }

         long now = city.now();
         if (!PathBudget.tryAcquire(now, false)) {
            return false;
         }

         boolean preferred = this.c.getRandom().nextFloat() < 0.85F;
         boolean wary = now < this.c.memUntil;
         long cell = city.grid().randomCell(this.c.getRandom()::nextDouble, this.c.getX(), this.c.getZ(), 8.0, 24.0, preferred, 10,
            (x, y, z) -> loaded(this.c, x, z) && Math.abs(y - this.c.getY()) < 12 && city.dangerMap().noticed(x + 0.5, y, z + 0.5, now) == null
               && (!wary || (x - this.c.memX) * (x - this.c.memX) + (z - this.c.memZ) * (z - this.c.memZ) > 28.0 * 28.0));
         if (cell == Long.MIN_VALUE) {
            this.c.idleTicks = 20;
            return false;
         }

         int x = WalkGrid.unpackX(cell);
         int z = WalkGrid.unpackZ(cell);
         Path p = this.c.getNavigation().createPath(new BlockPos(x, city.grid().feetY(x, z), z), 0);
         if (p == null || p.getNodeCount() < 2) {
            this.c.idleTicks = 20;
            return false;
         }

         this.following = false;
         this.c.getNavigation().moveTo(p, this.c.walkSpeed());
         return true;
      }

      @Override
      public void start() {
         this.time = 0;
         if (this.following) {
            CitizenEntity lead = this.c.leader;
            if (lead != null) {
               // walk beside the companion, not into it
               double side = (this.c.getId() & 1) == 0 ? 1.2 : -1.2;
               double yaw = Math.toRadians(lead.getYRot());
               this.c.getNavigation().moveTo(lead.getX() + Math.cos(yaw) * side, lead.getY(), lead.getZ() + Math.sin(yaw) * side, this.c.walkSpeed() * 1.05);
            }
         }

         if (this.c.citizenState() == CitizenState.IDLE) {
            this.c.setCitizenState(CitizenState.WALKING);
         }
      }

      @Override
      public boolean canContinueToUse() {
         return this.strollState() && ++this.time < (this.following ? 20 : 900) && !this.c.getNavigation().isDone();
      }

      @Override
      public void stop() {
         this.c.getNavigation().stop();
         if (this.c.citizenState() == CitizenState.WALKING) {
            this.c.setCitizenState(CitizenState.IDLE);
         }

         if (!this.following) {
            this.c.idleTicks = 40 + this.c.getRandom().nextInt(160);
         }
      }
   }

   /** Looks at a player nearby, only while calm (a danger owns the eyes). */
   static final class Gaze extends LookAtPlayerGoal {
      private final CitizenEntity c;

      Gaze(CitizenEntity c, float range) {
         super(c, Player.class, range);
         this.c = c;
      }

      @Override
      public boolean canUse() {
         return this.c.citizenState().calm() && super.canUse();
      }

      @Override
      public boolean canContinueToUse() {
         return this.c.citizenState().calm() && super.canContinueToUse();
      }
   }
}
