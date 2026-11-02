package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.world.collapse.CollapseInfo;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Citizens in peril (#138, server): people on a floor that a collapse is taking away lose their balance and
 * scream, fall when it goes (a real drop: vanilla gravity, the scream travels with the body), may grab a remaining
 * edge and hang on calling for help until a player comes close (pulled up), they climb back or let go. A flying
 * player catches a falling person. Witnesses gasp, others on the floors of a collapsing building call for help and
 * the street crowd is heard panicking. Every fall has a physical cause: only a floor that was removed, a blast or
 * debris put a person in the air; this class reacts, it never pushes anybody off.
 *
 * <p>Bounded: voices go through a per-level {@link PerilRules.VoiceBudget} and a per-person cooldown, at most
 * {@link PerilRules#MAX_HANGING} people hang at once, the airborne check runs once per fall. Citizens are never
 * saved (chunk unload removes them), so no peril state outlives its chunk.
 */
public final class CitizenPeril {
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final Map<ResourceKey<Level>, Stats> STATS = new HashMap<>();

   /** Per-level counters and the voice budget. */
   static final class Stats {
      final PerilRules.VoiceBudget budget = new PerilRules.VoiceBudget();
      long crowdUntil = Long.MIN_VALUE;
      int teeters, falls, hangs, climbs, pulledUp, caught, deaths, voices, crowds, skippedVoices;
   }

   private CitizenPeril() {
   }

   static Stats stats(Level level) {
      return STATS.computeIfAbsent(level.dimension(), k -> new Stats());
   }

   /** One server tick of a citizen; true when peril owns the body this tick (the brain is skipped). */
   static boolean tick(CitizenEntity c, CityManager city) {
      long now = city.now();
      if (c.voiceCooldown > 0) {
         c.voiceCooldown--;
      }
      if (c.voiceLater != null && now >= c.voiceAt) {
         CitizenVoices.Kind k = c.voiceLater;
         c.voiceLater = null;
         voice(c, k, 1.5F);
      }
      if (c.onGround() || c.isInWater() || c.onClimbable()) {
         c.fallChecked = false;
      }
      if (c.citizenState().peril()) {
         c.stateTicks++; // the brain (which counts otherwise) sleeps while the peril owns the body
      }
      switch (c.citizenState()) {
         case TEETER -> {
            teeter(c, city, now);
            return true;
         }
         case HANGING -> {
            hang(c, city, now);
            return true;
         }
         case FALLING -> {
            falling(c, city, now);
            return true;
         }
         default -> {
         }
      }
      if (!c.fallChecked && !c.onGround() && !c.isInWater() && !c.onClimbable() && !c.isNoGravity() && c.getDeltaMovement().y < -0.15 && c.fallDistance > 0.5F) {
         c.fallChecked = true;
         return airborne(c, city, now);
      }
      return false;
   }

   /** The ground went from under the feet: a stumble, a grab at the edge or a fall. */
   private static boolean airborne(CitizenEntity c, CityManager city, long now) {
      double drop = dropBelow(c);
      if (!PerilRules.isFall(drop)) {
         return false;
      }
      boolean caused = now < c.perilUntil;
      if (city.crowd().count(CitizenState.HANGING) < PerilRules.MAX_HANGING
         && c.getRandom().nextFloat() < PerilRules.grabChance(c.traits().bravery(), caused) && startHang(c, city, now)) {
         return true;
      }
      startFalling(c, city, now, drop);
      return true;
   }

   private static void startFalling(CitizenEntity c, CityManager city, long now, double drop) {
      c.getNavigation().stop();
      c.setNoGravity(false);
      c.fallFromY = c.getY();
      c.setCitizenState(CitizenState.FALLING);
      Stats st = stats(c.level());
      st.falls++;
      if (drop >= PerilRules.SCREAM_DROP) {
         voice(c, CitizenVoices.Kind.FALL, 0.0F);
      }
      witnesses(c, city, now);
   }

   private static void falling(CitizenEntity c, CityManager city, long now) {
      c.getNavigation().stop();
      if (c.onGround() || c.isInWater() || c.onClimbable()) {
         // landed and alive (a deadly landing never gets here): down, then up and away
         c.fallChecked = false;
         if (!c.hasThreat()) {
            c.setThreat(c.getX(), c.fallFromY, c.getZ(), 10.0F);
         }
         c.fallTicks = 40 + c.getRandom().nextInt(30);
         c.fallCooldown = 120;
         c.setCitizenState(CitizenState.FALLEN);
         return;
      }
      if (c.fallFromY - c.getY() > 1.5) {
         Player p = c.level().getNearestPlayer(c.getX(), c.getY() + 0.9, c.getZ(), PerilRules.CATCH_RANGE, q -> !q.isSpectator() && !q.onGround() && q.isAlive());
         if (p != null) {
            caught(c, p, city, now);
         }
      }
   }

   /** A flying hero caught the falling person: the fall stops, it floats down to safety. */
   private static void caught(CitizenEntity c, Player by, CityManager city, long now) {
      c.setDeltaMovement(by.getDeltaMovement().scale(0.6).add(0.0, 0.08, 0.0));
      c.hurtMarked = true;
      c.fallDistance = 0.0F;
      c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
      c.perilUntil = Long.MIN_VALUE;
      stats(c.level()).caught++;
      stopFallScream(c);
      c.setThreat(c.getX(), c.fallFromY, c.getZ(), 10.0F);
      c.setCitizenState(CitizenState.STARTLED);
   }

   private static void stopFallScream(CitizenEntity c) {
      if (!(c.level() instanceof ServerLevel level)) {
         return;
      }
      ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(CitizenVoices.of(CitizenVoices.Kind.FALL, c.look().female()).getLocation(), SoundSource.NEUTRAL);
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(c) < 48.0 * 48.0) {
            p.connection.send(stop);
         }
      }
   }

   /** Grabs the nearest edge still standing next to the body; false when there is none in reach. */
   private static boolean startHang(CitizenEntity c, CityManager city, long now) {
      Level level = c.level();
      double feet = c.getY();
      int bx = Mth.floor(c.getX()), bz = Mth.floor(c.getZ());
      BlockPos best = null;
      Direction bestSide = null;
      double bestTop = 0.0, bestGap = Double.MAX_VALUE;
      for (Direction d : SIDES) {
         // distance from the body centre to the side of its own column it would reach over
         double gap = switch (d) {
            case EAST -> bx + 1 - c.getX();
            case WEST -> c.getX() - bx;
            case SOUTH -> bz + 1 - c.getZ();
            default -> c.getZ() - bz;
         };
         if (gap > PerilRules.GRAB_REACH) {
            continue;
         }
         for (int y = Mth.floor(feet + PerilRules.GRAB_LOW) - 1; y <= Mth.floor(feet + PerilRules.GRAB_HIGH); y++) {
            BlockPos l = new BlockPos(bx + d.getStepX(), y, bz + d.getStepZ());
            double top = solidTop(level, l);
            if (Double.isNaN(top) || !PerilRules.reachable(feet, top) || !clear(level, l.above()) || !clear(level, l.above(2))) {
               continue;
            }
            if (gap < bestGap) {
               best = l;
               bestSide = d;
               bestTop = top;
               bestGap = gap;
            }
         }
      }
      if (best == null) {
         return false;
      }
      double reach = 1.95 * c.look().height();
      double hx = best.getX() + 0.5 - bestSide.getStepX() * 0.84, hz = best.getZ() + 0.5 - bestSide.getStepZ() * 0.84;
      double hy = bestTop - reach;
      if (!level.noCollision(c, c.getType().getAABB(hx, hy, hz).deflate(0.05))) {
         return false;
      }
      c.getNavigation().stop();
      c.setNoGravity(true);
      c.hangLedge = best;
      c.hangX = hx;
      c.hangY = hy;
      c.hangZ = hz;
      c.hangTop = bestTop;
      c.hangTicks = PerilRules.hangTicks(c.skin() ^ (int)now);
      c.fallFromY = hy;
      float yaw = (float)Math.toDegrees(Math.atan2(bestSide.getStepZ(), bestSide.getStepX())) - 90.0F;
      c.moveTo(hx, hy, hz, yaw, 0.0F);
      c.setYHeadRot(yaw);
      c.yBodyRot = yaw;
      c.setDeltaMovement(Vec3.ZERO);
      c.fallDistance = 0.0F;
      c.setCitizenState(CitizenState.HANGING);
      stats(level).hangs++;
      voice(c, CitizenVoices.Kind.SCREAM, 0.5F);
      c.voiceLater = CitizenVoices.Kind.HELP;
      c.voiceAt = now + 20 + c.getRandom().nextInt(20);
      return true;
   }

   private static void hang(CitizenEntity c, CityManager city, long now) {
      Level level = c.level();
      c.getNavigation().stop();
      c.setDeltaMovement(Vec3.ZERO);
      c.setPos(c.hangX, c.hangY, c.hangZ);
      c.fallDistance = 0.0F;
      BlockPos l = c.hangLedge;
      if (l == null || Double.isNaN(solidTop(level, l)) || !clear(level, l.above())) {
         release(c, city, now); // the edge went too, or something fell onto it
         return;
      }
      Player p = level.getNearestPlayer(c.hangX, c.hangTop, c.hangZ, PerilRules.RESCUE_RANGE, q -> !q.isSpectator() && q.isAlive());
      if (p != null) {
         climb(c, city, now, true);
         return;
      }
      if (c.stateTicks > 30 && c.stateTicks % (56 + (c.skin() & 31)) == 0) {
         voice(c, CitizenVoices.Kind.HELP, 1.0F);
      }
      if (--c.hangTicks <= 0) {
         if (c.getRandom().nextFloat() < PerilRules.climbChance(c.traits().bravery())) {
            climb(c, city, now, false);
         } else {
            release(c, city, now);
         }
      }
   }

   /** Pulled up (or climbed): standing on the ledge, then away from the edge. */
   private static void climb(CitizenEntity c, CityManager city, long now, boolean pulled) {
      BlockPos l = c.hangLedge;
      c.setNoGravity(false);
      c.hangLedge = null;
      if (l == null) {
         c.setCitizenState(CitizenState.FLEEING);
         return;
      }
      double x = l.getX() + 0.5, z = l.getZ() + 0.5;
      if (!c.level().noCollision(c, c.getType().getAABB(x, c.hangTop + 0.01, z).deflate(0.05))) {
         release(c, city, now);
         return;
      }
      c.moveTo(x, c.hangTop + 0.01, z, c.getYRot(), 0.0F);
      c.setDeltaMovement(Vec3.ZERO);
      c.fallDistance = 0.0F;
      Stats st = stats(c.level());
      if (pulled) {
         st.pulledUp++;
      } else {
         st.climbs++;
      }
      // run from the edge it hung on (inward)
      c.setThreat(c.hangX, c.hangY, c.hangZ, 8.0F);
      c.legs = 0;
      c.setCitizenState(CitizenState.FLEEING);
   }

   private static void release(CitizenEntity c, CityManager city, long now) {
      c.setNoGravity(false);
      c.hangLedge = null;
      c.fallChecked = true;
      startFalling(c, city, now, dropBelow(c));
   }

   /** The floor under the feet is part of a collapsing section: balance lost until it goes (or holds). */
   private static void teeter(CitizenEntity c, CityManager city, long now) {
      c.getNavigation().stop();
      if (!c.onGround() && !c.isInWater() && c.getDeltaMovement().y < -0.15 && c.fallDistance > 0.3F) {
         c.fallChecked = true;
         if (!airborne(c, city, now)) {
            c.setCitizenState(CitizenState.FLEEING);
         }
         return;
      }
      if (c.stateTicks % 36 == 18 && c.getRandom().nextFloat() < 0.35F) {
         voice(c, c.getRandom().nextBoolean() ? CitizenVoices.Kind.HELP : CitizenVoices.Kind.SCREAM, 1.5F);
      }
      if (c.stateTicks > c.teeterTicks) {
         c.legs = 0;
         c.setCitizenState(CitizenState.FLEEING); // the floor held: run
      }
   }

   /** A hurt hanging person lets go; a hurt person in peril stays in peril (no switch to plain fleeing). */
   static boolean onHurt(CitizenEntity c) {
      CitizenState s = c.citizenState();
      if (s == CitizenState.HANGING) {
         CityManager city = CityManager.of(c.level());
         if (city != null) {
            release(c, city, city.now());
         }
         return true;
      }
      return s.peril();
   }

   /** A death from a fall: the closest people around react aloud. */
   static void onDeath(CitizenEntity c, CityManager city) {
      if (c.citizenState() == CitizenState.FALLING || c.fallDistance > PerilRules.FALL_MIN_DROP) {
         stats(c.level()).deaths++;
         witnesses(c, city, city.now());
      }
   }

   /** The nearest people who see a person fall gasp (a short delay each) and get away. */
   private static void witnesses(CitizenEntity c, CityManager city, long now) {
      Level level = c.level();
      List<CitizenEntity> near = new ArrayList<>();
      double r2 = PerilRules.WITNESS_RANGE * PerilRules.WITNESS_RANGE;
      for (CitizenEntity o : city.citizens()) {
         if (o != c && o.isAlive() && !o.citizenState().peril() && o.distanceToSqr(c) < r2) {
            near.add(o);
         }
      }
      near.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
      int told = 0;
      Vec3 at = c.position().add(0.0, 0.9, 0.0);
      for (int i = 0; i < near.size() && told < PerilRules.WITNESSES && i < 8; i++) {
         CitizenEntity o = near.get(i);
         if (level.clip(new ClipContext(o.getEyePosition(), at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, o)).getType() != HitResult.Type.MISS) {
            continue; // did not see it
         }
         told++;
         o.setThreat(c.getX(), c.getY(), c.getZ(), 8.0F);
         if (o.citizenState().calm() || o.citizenState() == CitizenState.ALERT) {
            o.setCitizenState(CitizenState.STARTLED);
         }
         o.voiceLater = CitizenVoices.Kind.WITNESS;
         o.voiceAt = now + 3 + o.getRandom().nextInt(14);
      }
      // everybody else around notices something happened there
      city.dangerMap().add(c.getX(), c.getY(), c.getZ(), 0.0F, 24.0F, now, 120);
   }

   /**
    * A collapse starts (creak): people standing on the section lose their balance, people on the other floors of
    * the building call for help, the street crowd panics.
    */
   public static void onCollapseStarted(ServerLevel level, CollapseInfo info) {
      CityManager city = CityManager.of(level);
      if (city == null || info.affectedBlocks().isEmpty()) {
         return;
      }
      long now = city.now();
      LongOpenHashSet planned = new LongOpenHashSet(info.affectedBlocks().size());
      int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
      for (BlockPos p : info.affectedBlocks()) {
         planned.add(p.asLong());
         x0 = Math.min(x0, p.getX());
         y0 = Math.min(y0, p.getY());
         z0 = Math.min(z0, p.getZ());
         x1 = Math.max(x1, p.getX());
         y1 = Math.max(y1, p.getY());
         z1 = Math.max(z1, p.getZ());
      }
      double ground = info.landing().minY + 1.0;
      AABB section = new AABB(x0 - 1, y0, z0 - 1, x1 + 2, y1 + 3, z1 + 2);
      AABB building = section.inflate(12.0, 6.0, 12.0).setMinY(ground + 5.0);
      Vec3 c = Vec3.atCenterOf(info.center());
      Stats st = stats(level);
      int callers = 0;
      List<CitizenEntity> street = new ArrayList<>();
      double crowdR = info.radius() + 32.0;
      for (CitizenEntity e : city.citizens()) {
         if (!e.isAlive() || CitizenCaptivity.isHeld(e)) {
            continue;
         }
         CitizenState s = e.citizenState();
         if (s.peril()) {
            continue;
         }
         boolean onSection = e.onGround() && (planned.contains(e.getOnPos().asLong()) || planned.contains(e.blockPosition().asLong()));
         if (onSection && section.contains(e.position())) {
            e.perilUntil = now + PerilRules.PERIL_MEMORY;
            e.teeterTicks = PerilRules.TEETER_MAX;
            e.setThreat(c.x, c.y, c.z, info.radius());
            e.getNavigation().stop();
            e.setCitizenState(CitizenState.TEETER);
            st.teeters++;
            voice(e, CitizenVoices.Kind.SCREAM, 1.0F);
         } else if (building.contains(e.position())) {
            // another floor of the same building: it may go next
            e.perilUntil = now + PerilRules.PERIL_MEMORY * 2L;
            if (callers < PerilRules.MAX_CALLERS) {
               callers++;
               e.voiceLater = CitizenVoices.Kind.HELP;
               e.voiceAt = now + 8 + e.getRandom().nextInt(50);
            }
            if (s.calm()) {
               e.setThreat(c.x, c.y, c.z, info.radius());
               e.setCitizenState(CitizenState.ALERT);
            }
         } else if (e.getY() < ground + 4.0 && e.distanceToSqr(c.x, e.getY(), c.z) < crowdR * crowdR) {
            street.add(e);
         }
      }
      if (street.size() >= PerilRules.CROWD_MIN && now >= st.crowdUntil) {
         double sx = 0, sy = 0, sz = 0;
         for (CitizenEntity e : street) {
            sx += e.getX();
            sy += e.getY();
            sz += e.getZ();
         }
         int n = street.size();
         st.crowdUntil = now + PerilRules.CROWD_COOLDOWN;
         st.crowds++;
         level.playSound(null, sx / n, sy / n + 1.2, sz / n, CitizenVoices.CROWD_PANIC.get(), SoundSource.NEUTRAL, Math.min(3.0F, 1.6F + n * 0.08F),
            0.95F + level.random.nextFloat() * 0.1F);
      }
   }

   /** One voice of a citizen, carried by it (moves with the body); false when the budget or its cooldown says no. */
   static boolean voice(CitizenEntity c, CitizenVoices.Kind kind, float reserve) {
      if (c.voiceCooldown > 0 || c.isSilent()) {
         return false;
      }
      Stats st = stats(c.level());
      if (!st.budget.take(CityManager.of(c.level()) == null ? c.level().getGameTime() : CityManager.of(c.level()).now(), reserve)) {
         st.skippedVoices++;
         return false;
      }
      st.voices++;
      float volume = switch (kind) {
         case FALL -> 3.2F;
         case SCREAM -> 2.4F;
         case HELP -> 1.7F;
         case WITNESS -> 1.3F;
      };
      float pitch = 0.93F + CitizenTraits.unit(c.skin(), 201) * 0.14F;
      c.level().playSound(null, c, CitizenVoices.of(kind, c.look().female()), SoundSource.NEUTRAL, volume, pitch);
      c.voiceCooldown = kind == CitizenVoices.Kind.FALL ? 70 : 34 + c.getRandom().nextInt(26);
      return true;
   }

   /** Distance from the feet down to the first thing to land on (a block or water), at most 96. */
   static double dropBelow(CitizenEntity c) {
      Level level = c.level();
      int x = Mth.floor(c.getX()), z = Mth.floor(c.getZ());
      double feet = c.getY();
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, Mth.floor(feet), z);
      for (int i = 0; i < 96 && p.getY() >= level.getMinBuildHeight(); i++) {
         if (!level.isLoaded(p)) {
            return 96.0;
         }
         BlockState s = level.getBlockState(p);
         if (!s.getFluidState().isEmpty()) {
            return Math.max(0.0, feet - p.getY() - 0.9);
         }
         VoxelShape shape = s.getCollisionShape(level, p);
         if (!shape.isEmpty()) {
            double top = p.getY() + shape.max(Direction.Axis.Y);
            if (top <= feet + 0.01) {
               return feet - top;
            }
         }
         p.move(Direction.DOWN);
      }
      return 96.0;
   }

   /** Top of a block one can hold on to (a full-width solid top), NaN otherwise. */
   private static double solidTop(Level level, BlockPos p) {
      VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
      if (s.isEmpty() || s.max(Direction.Axis.Y) < 0.45 || s.min(Direction.Axis.X) > 0.1 || s.max(Direction.Axis.X) < 0.9 || s.min(Direction.Axis.Z) > 0.1
         || s.max(Direction.Axis.Z) < 0.9) {
         return Double.NaN;
      }
      return p.getY() + s.max(Direction.Axis.Y);
   }

   private static boolean clear(Level level, BlockPos p) {
      return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
   }

   /** {@code /city status} line. */
   public static String status(Level level) {
      Stats st = stats(level);
      return String.format("peril: teetered %d, fell %d, hung on %d (climbed %d, pulled up %d), caught %d, died %d; voices %d (%d skipped), crowd %d",
         st.teeters, st.falls, st.hangs, st.climbs, st.pulledUp, st.caught, st.deaths, st.voices, st.skippedVoices, st.crowds);
   }

   /** Arguments of the localized {@code /city status} line: upstairs, teetered, fell, hung on, climbed, pulled up, caught, died, crowds. */
   public static Object[] statusArgs(Level level, int upstairs) {
      Stats st = stats(level);
      return new Object[]{upstairs, st.teeters, st.falls, st.hangs, st.climbs, st.pulledUp, st.caught, st.deaths, st.crowds};
   }

   @Nullable
   static Stats peek(Level level) {
      return STATS.get(level.dimension());
   }
}
