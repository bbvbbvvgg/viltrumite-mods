package dev.baranhan.viltrumitecore.client.aftermath;

import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathStage;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathZone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/** A ruin zone as this client knows it: the server snapshot, its running clock, the current mix and the emitters found in the world. */
final class ClientZone {
   final int id;
   double x;
   double y;
   double z;
   float radius;
   float severity;
   float factor;
   long age;
   int causes;
   int flags;
   int[] focus;
   long[] seeds = new long[0];
   float[] mix = new float[AftermathRules.CHANNELS];
   AftermathStage stage = AftermathStage.IMPACT;
   final List<Emitter> emitters = new ArrayList<>();
   int cursor;
   int probes;
   boolean scanned;
   int scannedAt;
   /** The fresh dust cloud was drawn (once per hit). */
   boolean surged;
   /** Ticks the ruin's one-shots wait after a fresh hit: the blast owns that moment, then the debris settles. */
   int hold;
   private boolean known;
   double distance;

   ClientZone(int id) {
      this.id = id;
   }

   void apply(AftermathZone.Snapshot s) {
      boolean moved = s.x() != this.x || s.y() != this.y || s.z() != this.z || s.radius() != this.radius;
      boolean newSeeds = !Arrays.equals(s.seeds(), this.seeds) || !Arrays.equals(s.focus(), this.focus);
      boolean hit = s.age() < this.age - 40;
      boolean fresh = hit || !this.known && s.age() < 40L;
      this.x = s.x();
      this.y = s.y();
      this.z = s.z();
      this.radius = s.radius();
      this.severity = s.severity();
      this.factor = s.factor();
      this.age = s.age();
      this.causes = s.causes();
      this.flags = s.flags();
      this.focus = s.focus();
      this.seeds = s.seeds();
      if (moved || newSeeds) {
         this.rescan();
      }

      if (hit) {
         this.surged = false;
      }

      if (fresh) {
         this.hold = holdTicks(this.id);
      }

      this.known = true;
      this.update();
   }

   /** Pure: the settle pause after a hit, 0.8-1.4 s, different per ruin so neighbouring ruins do not answer in unison. */
   static int holdTicks(int id) {
      return 16 + Math.floorMod(id * 7, 13);
   }

   void rescan() {
      this.emitters.clear();
      this.cursor = 0;
      this.probes = 0;
      this.scanned = false;
   }

   void update() {
      this.mix = AftermathRules.mix(this.age, this.factor, this.severity, this.causes, this.flags);
      this.stage = AftermathRules.stage(this.age, this.factor);
   }

   /** 0 at the hit, 1 at the end of the late stage. */
   float progress() {
      return (float)Math.min(1.0, this.age / (AftermathRules.LATE_TICKS * (double)Math.max(0.05F, this.factor)));
   }

   float channel(int c) {
      return this.mix[c];
   }

   double distanceSq(double px, double py, double pz) {
      double dx = px - this.x;
      double dy = py - this.y;
      double dz = pz - this.z;
      return dx * dx + dy * dy + dz * dz;
   }

   /** A place in the ruin that does something: smoke from an opening, dust on rubble, crumbs from a broken ceiling... */
   static final class Emitter {
      static final byte WINDOW = 0;
      static final byte BREACH = 1;
      static final byte RUBBLE = 2;
      static final byte OVERHANG = 3;
      static final byte HOT = 4;

      final byte kind;
      final double x;
      final double y;
      final double z;
      /** Outward direction of an opening (unit, horizontal), 0 for the others. */
      final float dx;
      final float dz;
      final float strength;
      /** Share of the smoke time this source keeps smoking (they die out one by one). */
      final float life;
      final float phase;
      final BlockState material;
      final boolean burnable;
      final boolean metal;
      final boolean wire;
      boolean plume;

      Emitter(byte kind, double x, double y, double z, float dx, float dz, float strength, float life, float phase, BlockState material, boolean burnable,
         boolean metal, boolean wire) {
         this.kind = kind;
         this.x = x;
         this.y = y;
         this.z = z;
         this.dx = dx;
         this.dz = dz;
         this.strength = strength;
         this.life = life;
         this.phase = phase;
         this.material = material;
         this.burnable = burnable;
         this.metal = metal;
         this.wire = wire;
      }

      boolean smokes() {
         return this.kind == WINDOW || this.kind == BREACH || this.kind == HOT;
      }

      double distanceSq(double px, double py, double pz) {
         double ax = px - this.x;
         double ay = py - this.y;
         double az = pz - this.z;
         return ax * ax + ay * ay + az * az;
      }
   }
}
