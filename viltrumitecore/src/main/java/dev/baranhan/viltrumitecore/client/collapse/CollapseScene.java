package dev.baranhan.viltrumitecore.client.collapse;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket;
import dev.baranhan.viltrumitecore.world.collapse.CollapseAnalyzer;
import dev.baranhan.viltrumitecore.world.collapse.CollapseRules;
import dev.baranhan.viltrumitecore.world.collapse.CollapseSeverity;
import dev.baranhan.viltrumitecore.world.collapse.CollapseVariant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * One collapse as this client sees it. START snapshots the section's blocks (they are still in the world),
 * the server's phase packets drive the rest: phase 1 hit (flash, shake, chips), phase 2 failure (crack
 * overlay, creaks, facade pieces breaking off as the server sheds them, dust trickling), phase 3 the
 * section falls as baked fragments ({@link CollapseMotion}), the slam (dust wall along the streets, plume,
 * debris), phase 4 the dust settles. Distance decides the detail (LOD); a far client hears and sees the
 * cloud only. Owns its GPU buffers and crack overlays and releases them in {@link #close()}.
 */
final class CollapseScene {
   /** Fragments per scene (and baked buffers). */
   static final int MAX_PIECES = 140;
   /** Single blocks shed in phase 2 that fall on their own. */
   static final int MAX_SHED_PIECES = 40;
   static final int MAX_CRACKS = 20;
   private static int nextCrackBase = 0x5C0000;

   final CityCollapseS2CPacket start;
   final ClientLevel level;
   final long id;
   final CollapseVariant variant;
   final CollapseSeverity severity;
   final float strength;
   final Vec3 center, failure, landing;
   final int surfaceY;
   final RandomSource random;
   final int crackBase;
   // snapshot
   final int count;
   final int[] bx, by, bz;
   final BlockState[] states;
   final int[] light;
   final int[] exposed;
   final int[] cracks;
   int shedCursor;
   // timeline (scene ticks)
   int age;
   int collapseAge = -1, impactAge = -1;
   boolean ended;
   int endAge = -1;
   // fragments
   final List<CollapseMotion.Piece> pieces = new ArrayList<>();
   final List<FragmentMesh> meshes = new ArrayList<>();
   final List<Integer> pieceStart = new ArrayList<>();
   final boolean[] shedDone;
   int shedPieces;
   int pieceSounds;
   int lastPieceSound = -100;
   int lastShedSound = -100;

   CollapseScene(ClientLevel level, CityCollapseS2CPacket p) {
      this.start = p;
      this.level = level;
      this.id = p.id;
      this.variant = CollapseVariant.values()[Mth.clamp(p.variant, 0, CollapseVariant.values().length - 1)];
      this.severity = CollapseSeverity.byOrdinal(p.severity);
      this.strength = this.severity.strength();
      this.center = p.center;
      this.surfaceY = p.groundY + 1;
      this.random = RandomSource.create(p.seed);
      this.crackBase = -(nextCrackBase += 64);
      if (nextCrackBase > 0x7F0000) {
         nextCrackBase = 0x5C0000;
      }
      // snapshot the section from the bitset
      int total = Math.min(4096, countBits(p.bits));
      int[] xs = new int[total], ys = new int[total], zs = new int[total];
      BlockState[] st = new BlockState[total];
      int[] li = new int[total];
      int n = 0;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      BlockPos.MutableBlockPos nb = new BlockPos.MutableBlockPos();
      List<Integer> exp = new ArrayList<>();
      for (int dy = 0; dy < p.sizeY && n < total; dy++) {
         for (int dz = 0; dz < p.sizeZ && n < total; dz++) {
            for (int dx = 0; dx < p.sizeX && n < total; dx++) {
               if (!p.has(dx, dy, dz)) {
                  continue;
               }
               pos.set(p.minX + dx, p.minY + dy, p.minZ + dz);
               BlockState s = level.isLoaded(pos) ? level.getBlockState(pos) : null;
               if (s == null || s.isAir()) {
                  continue;
               }
               int best = 0;
               boolean open = false;
               for (Direction d : Direction.values()) {
                  nb.setWithOffset(pos, d);
                  int ndx = nb.getX() - p.minX, ndy = nb.getY() - p.minY, ndz = nb.getZ() - p.minZ;
                  boolean inside = ndx >= 0 && ndy >= 0 && ndz >= 0 && ndx < p.sizeX && ndy < p.sizeY && ndz < p.sizeZ && p.has(ndx, ndy, ndz);
                  if (!inside) {
                     best = Math.max(best, LevelRenderer.getLightColor(level, nb));
                     if (d != Direction.UP && level.getBlockState(nb).isAir()) {
                        open = true;
                     }
                  }
               }
               xs[n] = pos.getX();
               ys[n] = pos.getY();
               zs[n] = pos.getZ();
               st[n] = s;
               li[n] = best == 0 ? LevelRenderer.getLightColor(level, pos.above(64)) : best;
               if (open) {
                  exp.add(n);
               }
               n++;
            }
         }
      }
      this.count = n;
      this.bx = xs;
      this.by = ys;
      this.bz = zs;
      this.states = st;
      this.light = li;
      this.shedDone = new boolean[n];
      this.exposed = exp.stream().mapToInt(Integer::intValue).toArray();
      // crack sites: exposed blocks in the lower part of the section, seeded
      List<Integer> low = new ArrayList<>();
      int lowTop = p.minY + Math.max(2, p.sizeY / 3);
      for (int i : this.exposed) {
         if (ys[i] <= lowTop) {
            low.add(i);
         }
      }
      for (int i = low.size() - 1; i > 0; i--) {
         int j = (int)(CollapseAnalyzer.hash(p.seed, i, 41, 0) % (i + 1));
         Integer t = low.get(i);
         low.set(i, low.get(j));
         low.set(j, t);
      }
      this.cracks = low.subList(0, Math.min(MAX_CRACKS, low.size())).stream().mapToInt(Integer::intValue).toArray();
      double fx = (p.minX + p.sizeX * 0.5), fz = (p.minZ + p.sizeZ * 0.5);
      this.failure = new Vec3(fx, p.minY + 1.0, fz);
      double spread = Math.min(8.0, 1.5 + p.sizeY * 0.35) * (this.variant == CollapseVariant.PANCAKE ? 0.4 : 1.0);
      this.landing = new Vec3(fx + p.fallX * spread, this.surfaceY, fz + p.fallZ * spread);
      this.onStart();
   }

   private static int countBits(long[] bits) {
      int c = 0;
      for (long b : bits) {
         c += Long.bitCount(b);
      }
      return c;
   }

   double cameraDistance() {
      return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(this.center);
   }

   /** 0 near, 1 mid, 2 far (cloud and sound only). */
   int lod() {
      double d = this.cameraDistance();
      return this.start.far || d > 170.0 ? 2 : d > 90.0 ? 1 : 0;
   }

   private Vec3 at(int i) {
      return new Vec3(this.bx[i] + 0.5, this.by[i] + 0.5, this.bz[i] + 0.5);
   }

   private BlockState anyState() {
      return this.count > 0 ? this.states[this.count / 2] : null;
   }

   // ------------------------------------------------------------------ phases

   private void onStart() {
      int lod = this.lod();
      CollapseAudio.impact(this.failure, this.strength, this.random.nextDouble(), CollapseClient.running());
      CameraShake.addAt(this.failure, 0.3F + 0.4F * this.strength, 70.0 + 40.0 * this.strength);
      double d = this.cameraDistance();
      if (d < 56.0) {
         FlashOverlay.flash(0.12F * (float)(1.0 - d / 56.0), 4);
      }
      if (lod < 2) {
         BlockState s = this.anyState();
         CollapseDust.chips(s, this.failure, 3.0, 0.5, lod == 0 ? 36 : 14, this.random);
         for (int i = 0; i < (lod == 0 ? 10 : 4); i++) {
            CollapseDust.small(this.failure.x + (this.random.nextDouble() - 0.5) * this.start.sizeX, this.failure.y + this.random.nextDouble() * 2.0,
               this.failure.z + (this.random.nextDouble() - 0.5) * this.start.sizeZ, (this.random.nextDouble() - 0.5) * 0.15, 0.02, (this.random.nextDouble() - 0.5) * 0.15,
               0.7F);
         }
      }
      // phase 2 creaks: a groan for big or toppling sections, a few cracks spread over the creak time
      int creakStart = CollapseRules.IMPACT_TICKS;
      this.scheduleGroan = this.severity != CollapseSeverity.MINOR || this.variant == CollapseVariant.TOPPLE ? creakStart + 2 : -1;
      int cracksN = 2 + this.severity.ordinal();
      this.crackCues = new int[cracksN];
      for (int k = 0; k < cracksN; k++) {
         this.crackCues[k] = creakStart + (int)((k + 0.6 + 0.4 * this.random.nextDouble()) * this.start.creakTicks / (cracksN + 0.5));
      }
   }

   private int scheduleGroan;
   private int[] crackCues = new int[0];

   void onPhase(CityCollapseS2CPacket p) {
      switch (p.kind) {
         case CityCollapseS2CPacket.COLLAPSE -> this.onCollapse();
         case CityCollapseS2CPacket.IMPACT -> this.onImpact();
         case CityCollapseS2CPacket.END -> {
            this.ended = true;
            this.endAge = this.age;
            this.clearCracks();
         }
         default -> {
         }
      }
   }

   private void onCollapse() {
      if (this.collapseAge >= 0) {
         return;
      }
      this.collapseAge = this.age;
      this.clearCracks();
      CollapseAudio.collapse(this.center, this.strength, this.random.nextDouble(), CollapseClient.running());
      CameraShake.addAt(this.failure, 0.25F + 0.35F * this.strength, 80.0);
      // fragments: every snapshot block not shed yet
      int[] idx = new int[this.count];
      int m = 0;
      for (int i = 0; i < this.count; i++) {
         if (!this.shedDone[i]) {
            idx[m++] = i;
         }
      }
      int[] xs = new int[m], ys = new int[m], zs = new int[m];
      for (int k = 0; k < m; k++) {
         xs[k] = this.bx[idx[k]];
         ys[k] = this.by[idx[k]];
         zs[k] = this.bz[idx[k]];
      }
      int lod = this.lod();
      int cap = lod == 2 ? 48 : lod == 1 ? 90 : MAX_PIECES;
      List<CollapseMotion.Piece> made = CollapseMotion.build(xs, ys, zs, this.variant, this.start.seed, this.start.fallX, this.start.fallZ, this.surfaceY,
         this.start.fallTicks, cap, this::surface);
      for (CollapseMotion.Piece piece : made) {
         int[] members = new int[piece.members.length];
         for (int k = 0; k < members.length; k++) {
            members[k] = idx[piece.members[k]];
         }
         this.addPiece(piece, members, this.age);
      }
      if (lod < 2) {
         // dust bursting out of the break line
         for (int i = 0; i < (lod == 0 ? 14 : 6); i++) {
            double a = this.random.nextDouble() * Mth.TWO_PI;
            CollapseDust.small(this.failure.x + Math.cos(a) * this.start.sizeX * 0.5, this.failure.y + this.random.nextDouble(),
               this.failure.z + Math.sin(a) * this.start.sizeZ * 0.5, Math.cos(a) * 0.2, 0.03, Math.sin(a) * 0.2, 0.9F);
         }
      }
   }

   private int surface(int x, int z) {
      return this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
   }

   /** Bakes one fragment; {@code members} index the snapshot. */
   private void addPiece(CollapseMotion.Piece piece, int[] members, int startAge) {
      int n = members.length;
      BlockState[] s = new BlockState[n];
      float[] rx = new float[n], ry = new float[n], rz = new float[n];
      int[] li = new int[n];
      double r2 = 0.0;
      for (int k = 0; k < n; k++) {
         int i = members[k];
         s[k] = this.states[i];
         rx[k] = (float)(this.bx[i] - piece.ox);
         ry[k] = (float)(this.by[i] - piece.oy);
         rz[k] = (float)(this.bz[i] - piece.oz);
         li[k] = this.light[i];
         double cx = rx[k] + 0.5, cy = ry[k] + 0.5, cz = rz[k] + 0.5;
         r2 = Math.max(r2, cx * cx + cy * cy + cz * cz);
      }
      FragmentMesh mesh = FragmentMesh.bake(s, rx, ry, rz, li, n);
      if (mesh == null) {
         return;
      }
      this.pieces.add(piece);
      this.meshes.add(mesh);
      this.pieceStart.add(startAge);
      this.pieceFirst.add(members[0]);
      this.pieceRadius.add((float)Math.sqrt(r2) + 0.87F);
   }

   /** Bounding radius of each fragment around its pivot (camera clipping). */
   private final List<Float> pieceRadius = new ArrayList<>();

   private final List<Integer> pieceFirst = new ArrayList<>();

   private void onImpact() {
      if (this.impactAge >= 0) {
         return;
      }
      this.impactAge = this.age;
      int lod = this.lod();
      int running = CollapseClient.running();
      CollapseAudio.ground(this.landing, this.strength, this.random.nextDouble(), running);
      CameraShake.addAt(this.landing, 0.6F + 0.9F * this.strength, 90.0 + 70.0 * this.strength);
      float crowd = running >= 3 ? 0.55F : running == 2 ? 0.75F : 1.0F;
      int wall = (int)((lod == 0 ? 96 : lod == 1 ? 56 : 28) * (0.6F + 0.4F * this.strength) * crowd);
      CollapseDust.streetWall(this.level, this.landing, this.surfaceY, this.strength, wall, this.random);
      CollapseDust.plume(this.landing, this.surfaceY, 2.0F + this.start.sizeX * 0.3F, (int)((lod == 2 ? 8 : 18) * crowd), this.random);
      if (lod < 2) {
         CollapseDust.chips(this.anyState(), this.landing.add(0, 0.8, 0), 4.0, 0.7, (int)((lod == 0 ? 70 : 24) * crowd), this.random);
      }
   }

   // ------------------------------------------------------------------ tick

   /** One client tick; true when the scene can be dropped. */
   boolean tick() {
      this.age++;
      int lod = this.lod();
      int running = CollapseClient.running();
      if (this.collapseAge < 0) {
         this.creak(lod, running);
      }
      if (this.collapseAge >= 0) {
         this.fall(lod, running);
      }
      if (this.impactAge >= 0) {
         this.settle(lod);
      }
      // safety: a lost END never leaves a scene behind
      int limit = this.start.collapseAt + this.start.fallTicks + CollapseRules.SETTLE_PHASE_TICKS + 200;
      boolean piecesDone = true;
      for (int i = 0; i < this.pieces.size(); i++) {
         if (!this.pieces.get(i).done(this.age - this.pieceStart.get(i))) {
            piecesDone = false;
            break;
         }
      }
      return this.ended && (piecesDone || this.age - this.endAge > 80) || this.age > limit;
   }

   private void creak(int lod, int running) {
      if (this.age == this.scheduleGroan) {
         CollapseAudio.groan(this.center, this.strength, this.random.nextDouble(), running);
      }
      for (int c : this.crackCues) {
         if (c == this.age) {
            CollapseAudio.crack(this.failure, this.strength, this.random.nextDouble(), running);
            CameraShake.addAt(this.failure, 0.12F + 0.12F * this.strength, 48.0);
         }
      }
      if (this.age < CollapseRules.IMPACT_TICKS) {
         return;
      }
      // crack overlay grows over the failure zone
      if (lod < 2 && this.age % 3 == 0) {
         float k = (this.age - CollapseRules.IMPACT_TICKS) / (float)Math.max(1, this.start.creakTicks);
         int stage = Mth.clamp((int)(k * 10.0F), 0, 9);
         for (int j = 0; j < this.cracks.length; j++) {
            int i = this.cracks[j];
            if (j <= k * this.cracks.length * 1.4F) {
               this.level.destroyBlockProgress(this.crackBase - j, new BlockPos(this.bx[i], this.by[i], this.bz[i]), stage);
            }
         }
      }
      // dust trickling out of the cracks
      if (lod < 2 && this.cracks.length > 0 && this.age % 2 == 0) {
         int i = this.cracks[this.random.nextInt(this.cracks.length)];
         CollapseDust.trickle(this.states[i], this.bx[i] + this.random.nextDouble(), this.by[i] - 0.05, this.bz[i] + this.random.nextDouble());
      }
      // facade pieces the server sheds now: polled round robin, each falls as its own fragment
      if (lod < 2) {
         this.pollShed(lod, running);
      }
   }

   private void pollShed(int lod, int running) {
      if (this.exposed.length == 0) {
         return;
      }
      int checks = Math.min(this.exposed.length, 96);
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      for (int c = 0; c < checks; c++) {
         int i = this.exposed[this.shedCursor++ % this.exposed.length];
         if (this.shedDone[i]) {
            continue;
         }
         p.set(this.bx[i], this.by[i], this.bz[i]);
         if (!this.level.getBlockState(p).isAir()) {
            continue;
         }
         this.shedDone[i] = true;
         Vec3 at = this.at(i);
         CollapseDust.chips(this.states[i], at, 1.0, 0.25, lod == 0 ? 6 : 2, this.random);
         if (this.shedPieces < MAX_SHED_PIECES) {
            this.shedPieces++;
            List<CollapseMotion.Piece> one = CollapseMotion.build(new int[]{this.bx[i]}, new int[]{this.by[i]}, new int[]{this.bz[i]}, CollapseVariant.CRUMBLE,
               this.start.seed ^ i, this.start.fallX, this.start.fallZ, this.surfaceY, 8, 1, this::surface);
            if (!one.isEmpty()) {
               one.get(0).delay = 0;
               this.addPiece(one.get(0), new int[]{i}, this.age);
            }
         }
         if (this.age - this.lastShedSound >= 5) {
            this.lastShedSound = this.age;
            CollapseAudio.shed(at, this.strength, this.random.nextDouble(), running);
         }
      }
   }

   private void fall(int lod, int running) {
      for (int k = 0; k < this.pieces.size(); k++) {
         CollapseMotion.Piece piece = this.pieces.get(k);
         int t = this.age - this.pieceStart.get(k);
         if (t == piece.land) {
            Vec3 at = new Vec3(piece.x[piece.at(t)], piece.y[piece.at(t)], piece.z[piece.at(t)]);
            if (lod < 2) {
               CollapseDust.chips(this.states[this.pieceFirst.get(k)], at, 1.0 + piece.half, 0.35, lod == 0 ? Math.min(10, 2 + piece.members.length) : 2, this.random);
               if (lod == 0 || piece.members.length >= 6) {
                  CollapseDust.small(at.x, at.y, at.z, (this.random.nextDouble() - 0.5) * 0.2, 0.03, (this.random.nextDouble() - 0.5) * 0.2, 0.6F + piece.half * 0.3F);
               }
            }
            if (this.pieceSounds < 14 && this.age - this.lastPieceSound >= 3 && piece.members.length >= 2) {
               this.pieceSounds++;
               this.lastPieceSound = this.age;
               CollapseAudio.piece(at, piece.half, this.random.nextDouble(), running);
            }
         } else if (lod == 0 && t > piece.delay && (piece.land < 0 || t < piece.land) && (k + this.age) % 5 == 0 && piece.members.length >= 3) {
            int j = piece.at(t);
            CollapseDust.small(piece.x[j], piece.y[j] + piece.half, piece.z[j], 0.0, 0.02, 0.0, 0.5F);
         }
      }
   }

   private void settle(int lod) {
      int since = this.age - this.impactAge;
      if (since < 120 && since % 6 == 0 && lod < 2 && this.count > 0) {
         // dust pouring off the broken edge above the cut
         int i = this.exposed.length > 0 ? this.exposed[this.random.nextInt(this.exposed.length)] : this.random.nextInt(this.count);
         BlockPos below = new BlockPos(this.bx[i], this.start.minY - 1, this.bz[i]);
         BlockState s = this.level.getBlockState(below);
         if (!s.isAir()) {
            CollapseDust.trickle(s, below.getX() + this.random.nextDouble(), below.getY() - 0.05, below.getZ() + this.random.nextDouble());
         }
      }
      if (since > 0 && since < 70 && since % 14 == 0) {
         CollapseDust.plume(this.landing, this.surfaceY, 1.5F + this.start.sizeX * 0.25F, lod == 2 ? 1 : 3, this.random);
      }
   }

   // ------------------------------------------------------------------ render

   /** Draws the falling fragments; the render type is set up by the caller. */
   int render(PoseStack pose, Vec3 cam, float partial) {
      int drawn = 0;
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      Quaternionf q = new Quaternionf();
      Quaternionf q2 = new Quaternionf();
      for (int k = 0; k < this.pieces.size(); k++) {
         CollapseMotion.Piece piece = this.pieces.get(k);
         int t = this.age - this.pieceStart.get(k);
         if (piece.done(t)) {
            continue;
         }
         if (t <= 3) {
            // the real blocks are still there until the server's removal reaches the client
            int f = this.pieceFirst.get(k);
            p.set(this.bx[f], this.by[f], this.bz[f]);
            if (!this.level.getBlockState(p).isAir()) {
               continue;
            }
         }
         int a = piece.at(t), b = piece.at(t + 1);
         double x = Mth.lerp(partial, piece.x[a], piece.x[b]);
         double y = Mth.lerp(partial, piece.y[a], piece.y[b]) - Mth.lerp(partial, piece.sink(t), piece.sink(t + 1));
         double z = Mth.lerp(partial, piece.z[a], piece.z[b]);
         // found in game: a tower toppling onto the viewer swept its fragments through the camera; a fragment
         // that would enclose the eye is skipped for those frames instead of filling the screen with its inside
         double ex = x - cam.x, ey = y - cam.y, ez = z - cam.z, clear = this.pieceRadius.get(k) + 0.6;
         if (ex * ex + ey * ey + ez * ez < clear * clear) {
            continue;
         }
         q.set(piece.qx[a], piece.qy[a], piece.qz[a], piece.qw[a]);
         q2.set(piece.qx[b], piece.qy[b], piece.qz[b], piece.qw[b]);
         q.slerp(q2, partial);
         pose.pushPose();
         pose.translate(x - cam.x, y - cam.y, z - cam.z);
         pose.mulPose(q);
         this.meshes.get(k).draw(pose.last().pose());
         pose.popPose();
         drawn++;
      }
      return drawn;
   }

   boolean hasPieces() {
      return !this.pieces.isEmpty();
   }

   private void clearCracks() {
      for (int j = 0; j < this.cracks.length; j++) {
         int i = this.cracks[j];
         this.level.destroyBlockProgress(this.crackBase - j, new BlockPos(this.bx[i], this.by[i], this.bz[i]), -1);
      }
   }

   void close() {
      this.clearCracks();
      for (FragmentMesh m : this.meshes) {
         m.close();
      }
      this.meshes.clear();
      this.pieces.clear();
   }
}
