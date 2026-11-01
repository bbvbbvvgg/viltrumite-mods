package dev.baranhan.viltrumitecore.client.aftermath;

import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Reads the ruin the way it really is on this client: from each destroyed-block seed of a zone it looks for a
 * window or breach the smoke can pour out of (an air cell under a ceiling that opens to the sky within a few
 * blocks), the rubble top below an open hole, broken ceilings that drop crumbs, hot spots (magma, fire),
 * burnable debris, metal and torn light fittings or wires. A few seeds per tick, all zones together, within
 * {@link #RANGE} of the camera; zones without seeds (declared collapses, old saves) get random probes in their
 * focus box or sphere. Every {@link #RESCAN} ticks a zone is read again (debris cleared, blocks restored).
 */
final class AftermathScanner {
   static final int SEEDS_PER_TICK = 6;
   static final int MAX_EMITTERS = 40;
   static final double RANGE = 176.0;
   static final int RESCAN = 600;
   static final int FALLBACK_PROBES = 18;
   private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

   private AftermathScanner() {
   }

   static void tick(ClientLevel level, List<ClientZone> nearestFirst, int clock) {
      int budget = SEEDS_PER_TICK;
      for (ClientZone zn : nearestFirst) {
         if (budget <= 0) {
            return;
         }

         if (zn.distance > RANGE + zn.radius) {
            continue;
         }

         if (zn.scanned && clock - zn.scannedAt > RESCAN) {
            zn.rescan();
         }

         while (budget > 0 && !zn.scanned) {
            budget--;
            step(level, zn, clock);
         }
      }
   }

   private static void step(ClientLevel level, ClientZone zn, int clock) {
      RandomSource random = RandomSource.create(zn.id * 341873128712L + zn.cursor * 132897987541L + zn.probes);
      if (zn.cursor < zn.seeds.length) {
         probeSeed(level, zn, BlockPos.of(zn.seeds[zn.cursor++]), random);
         return;
      }

      int fallback = zn.seeds.length >= 12 ? 4 : FALLBACK_PROBES;
      if (zn.probes < fallback) {
         zn.probes++;
         probeRandom(level, zn, random);
         return;
      }

      zn.scanned = true;
      zn.scannedAt = clock;
      // the biggest ruins get one to three tall plumes: the strongest open breaches
      int plumes = zn.severity >= 0.75F ? 3 : zn.severity >= 0.5F ? 2 : zn.severity >= 0.3F ? 1 : 0;
      ClientZone.Emitter[] best = new ClientZone.Emitter[plumes];
      for (ClientZone.Emitter e : zn.emitters) {
         if (e.kind != ClientZone.Emitter.BREACH && e.kind != ClientZone.Emitter.HOT) {
            continue;
         }

         for (int i = 0; i < plumes; i++) {
            if (best[i] == null || e.strength > best[i].strength) {
               System.arraycopy(best, i, best, i + 1, plumes - i - 1);
               best[i] = e;
               break;
            }
         }
      }

      for (ClientZone.Emitter e : best) {
         if (e != null) {
            e.plume = true;
         }
      }
   }

   private static boolean loaded(ClientLevel level, BlockPos pos) {
      return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && !level.isOutsideBuildHeight(pos);
   }

   private static boolean solid(BlockState s) {
      return !s.isAir() && !(s.getBlock() instanceof BaseFireBlock) && s.getFluidState().isEmpty() && !s.canBeReplaced();
   }

   private static boolean hot(BlockState s) {
      return s.is(Blocks.MAGMA_BLOCK) || s.getBlock() instanceof BaseFireBlock || s.is(Blocks.LAVA) || s.is(Blocks.CAMPFIRE) && s.getLightEmission() > 0;
   }

   private static boolean metal(BlockState s) {
      SoundType t = s.getSoundType();
      return t == SoundType.METAL || t == SoundType.ANVIL || t == SoundType.CHAIN || t == SoundType.COPPER || t == SoundType.NETHERITE_BLOCK
         || t == SoundType.LANTERN;
   }

   private static boolean wire(BlockState s) {
      return s.getLightEmission() > 0 && !hot(s) || s.is(Blocks.REDSTONE_WIRE) || s.is(Blocks.LIGHTNING_ROD) || s.is(Blocks.REDSTONE_LAMP)
         || s.is(Blocks.CHAIN);
   }

   /** Sky above this cell (nothing motion blocking higher up in its column). */
   private static boolean open(ClientLevel level, BlockPos p) {
      return p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
   }

   private static void add(ClientZone zn, ClientZone.Emitter e) {
      if (zn.emitters.size() < MAX_EMITTERS) {
         zn.emitters.add(e);
      }
   }

   private static void probeSeed(ClientLevel level, ClientZone zn, BlockPos seed, RandomSource random) {
      if (!loaded(level, seed)) {
         return;
      }

      BlockState at = level.getBlockState(seed);
      if (hot(at)) {
         emit(level, zn, ClientZone.Emitter.HOT, seed.above(), 0.0F, 0.0F, at, random);
         return;
      }

      if (solid(at)) {
         // filled again (restored, debris landed): only its top can still smoulder or dust
         BlockPos up = seed.above();
         if (loaded(level, up) && !solid(level.getBlockState(up)) && open(level, up)) {
            emit(level, zn, ClientZone.Emitter.RUBBLE, up, 0.0F, 0.0F, at, random);
         }

         return;
      }

      // an air cell where a block was: under a ceiling, or open to the sky?
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      BlockState ceiling = null;
      int ceilingAt = 0;
      for (int k = 1; k <= 4; k++) {
         m.setWithOffset(seed, 0, k, 0);
         BlockState s = level.getBlockState(m);
         if (solid(s)) {
            ceiling = s;
            ceilingAt = k;
            break;
         }
      }

      BlockState floor = null;
      int floorAt = 0;
      for (int k = 1; k <= 10; k++) {
         m.setWithOffset(seed, 0, -k, 0);
         BlockState s = level.getBlockState(m);
         if (solid(s) || hot(s)) {
            floor = s;
            floorAt = k;
            break;
         }
      }

      if (ceiling != null) {
         int start = random.nextInt(4);
         for (int i = 0; i < 4; i++) {
            Direction d = SIDES[(start + i) & 3];
            for (int k = 1; k <= 6; k++) {
               m.setWithOffset(seed, d.getStepX() * k, 0, d.getStepZ() * k);
               if (!loaded(level, m)) {
                  break;
               }

               BlockState s = level.getBlockState(m);
               if (solid(s)) {
                  break;
               }

               if (open(level, m)) {
                  // the opening: the face between the last covered cell and the open one
                  BlockPos face = m.immutable().relative(d.getOpposite());
                  BlockState wall = level.getBlockState(face.above(ceilingAt));
                  emit(level, zn, ClientZone.Emitter.WINDOW, face, d.getStepX(), d.getStepZ(), solid(wall) ? wall : ceiling, random,
                     d.getStepX() * 0.5, d.getStepZ() * 0.5);
                  i = 4;
                  break;
               }
            }
         }

         if (ceilingAt <= 2 && random.nextFloat() < 0.6F) {
            emit(level, zn, ClientZone.Emitter.OVERHANG, seed.above(ceilingAt), 0.0F, 0.0F, ceiling, random);
         }
      } else if (floor != null) {
         BlockPos top = seed.below(floorAt - 1);
         byte kind = hot(floor) ? ClientZone.Emitter.HOT : floorAt >= 3 ? ClientZone.Emitter.BREACH : ClientZone.Emitter.RUBBLE;
         emit(level, zn, kind, top, 0.0F, 0.0F, floor, random);
      }

      // the broken edge of a floor or wall next to the hole drops crumbs
      for (Direction d : SIDES) {
         BlockPos edge = seed.relative(d);
         BlockState s = level.getBlockState(edge);
         if (solid(s) && !solid(level.getBlockState(edge.below())) && random.nextFloat() < 0.35F) {
            emit(level, zn, ClientZone.Emitter.OVERHANG, edge, 0.0F, 0.0F, s, random);
            break;
         }
      }
   }

   private static void probeRandom(ClientLevel level, ClientZone zn, RandomSource random) {
      int x;
      int z;
      if (zn.focus != null) {
         x = zn.focus[0] + random.nextInt(Math.max(1, zn.focus[3] - zn.focus[0] + 1));
         z = zn.focus[2] + random.nextInt(Math.max(1, zn.focus[5] - zn.focus[2] + 1));
      } else {
         double a = random.nextDouble() * Mth.TWO_PI;
         double r = Math.sqrt(random.nextDouble()) * zn.radius * 0.75;
         x = Mth.floor(zn.x + Math.cos(a) * r);
         z = Mth.floor(zn.z + Math.sin(a) * r);
      }

      BlockPos column = new BlockPos(x, Mth.floor(zn.y), z);
      if (!loaded(level, column)) {
         return;
      }

      int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
      if (Math.abs(top - zn.y) > zn.radius + 6.0) {
         return;
      }

      BlockPos air = new BlockPos(x, top, z);
      BlockState below = level.getBlockState(air.below());
      if (!solid(below) && !hot(below)) {
         return;
      }

      emit(level, zn, hot(below) ? ClientZone.Emitter.HOT : ClientZone.Emitter.RUBBLE, air, 0.0F, 0.0F, below, random);
   }

   private static void emit(ClientLevel level, ClientZone zn, byte kind, BlockPos pos, float dx, float dz, BlockState material, RandomSource random) {
      emit(level, zn, kind, pos, dx, dz, material, random, 0.0, 0.0);
   }

   private static void emit(ClientLevel level, ClientZone zn, byte kind, BlockPos pos, float dx, float dz, BlockState material, RandomSource random,
      double ox, double oz) {
      for (ClientZone.Emitter e : zn.emitters) {
         if (e.kind == kind && e.distanceSq(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < (kind == ClientZone.Emitter.WINDOW ? 4.0 : 6.25)) {
            return; // one source per spot
         }
      }

      boolean wire = false;
      boolean metal = metal(material);
      for (Direction d : Direction.values()) {
         BlockState s = level.getBlockState(pos.relative(d));
         wire |= wire(s);
         metal |= metal(s);
      }

      float strength = 0.45F + random.nextFloat() * 0.55F;
      float life = 0.35F + random.nextFloat() * 0.65F;
      double y = kind == ClientZone.Emitter.OVERHANG ? pos.getY() - 0.02 : pos.getY() + (kind == ClientZone.Emitter.WINDOW ? 0.3 + random.nextDouble() * 0.5 : 0.05);
      add(zn, new ClientZone.Emitter(kind, pos.getX() + 0.5 + ox, y, pos.getZ() + 0.5 + oz, dx, dz, strength, life, random.nextFloat() * Mth.TWO_PI,
         material, material.ignitedByLava() || kind == ClientZone.Emitter.HOT, metal, wire));
   }
}
