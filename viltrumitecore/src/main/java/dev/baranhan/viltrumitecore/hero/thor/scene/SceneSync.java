package dev.baranhan.viltrumitecore.hero.thor.scene;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * One scene state on the wire (S2C, to Thor, the victim and everyone
 * tracking Thor). Field meaning per kind:
 * <ul>
 *   <li>GRAB: stage = GrabFlow.Stage, variant = GrabAction id or -1,
 *       tick = ticks in the stage, resistKey / meter for the victim HUD;</li>
 *   <li>FATALITY: the FatalityPlan (a = Thor start, b = victim start,
 *       c = victim velocity, d = origin, yaws, intro), variant = scene id,
 *       entry = SceneEntry id, tick = scene tick;</li>
 *   <li>TELEPORT: a = eye at the start, b = camera point, tick = selection
 *       tick, intro = rise ticks, variant = limit ticks.</li>
 * </ul>
 * {@code end} closes the scene on the client (camera handed back).
 */
public record SceneSync(
   Kind kind,
   boolean end,
   int thorId,
   int victimId,
   int stage,
   int variant,
   int entry,
   int tick,
   int resistKey,
   float meter,
   boolean rehearsal,
   Vec3 a,
   Vec3 b,
   Vec3 c,
   Vec3 d,
   float yawA,
   float yawB,
   float yawC,
   int intro
) {
   public enum Kind {
      GRAB,
      FATALITY,
      TELEPORT;

      static Kind byId(int id) {
         Kind[] all = values();
         return all[Math.max(0, Math.min(all.length - 1, id))];
      }
   }

   public static SceneSync grab(int thorId, int victimId, GrabFlow flow) {
      GrabAction action = flow.action();
      ResistKey key = flow.resistible() ? flow.key() : null;
      return new SceneSync(Kind.GRAB, false, thorId, victimId, flow.stage().ordinal(), action == null ? -1 : action.ordinal(), 0, flow.ticks(),
         key == null ? -1 : key.ordinal(), flow.meter(), false, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0, 0, 0, 0);
   }

   public static SceneSync fatality(int thorId, int victimId, FatalityPlan plan, int tick, boolean rehearsal) {
      return new SceneSync(Kind.FATALITY, false, thorId, victimId, 0, plan.scene().ordinal(), plan.entry().ordinal(), tick, -1, 0, rehearsal,
         plan.thorStart(), plan.victimStart(), plan.victimVelocity(), plan.origin(), plan.thorStartYaw(), plan.victimStartYaw(), plan.frameYaw(),
         plan.intro());
   }

   public static SceneSync teleport(int thorId, Vec3 eye, Vec3 camera, int tick, int riseTicks, int limit) {
      return new SceneSync(Kind.TELEPORT, false, thorId, -1, 0, limit, 0, tick, -1, 0, false, eye, camera, Vec3.ZERO, Vec3.ZERO, 0, 0, 0, riseTicks);
   }

   public static SceneSync end(Kind kind, int thorId, int victimId) {
      return new SceneSync(kind, true, thorId, victimId, 0, -1, 0, 0, -1, 0, false, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0, 0, 0, 0);
   }

   /** The fatality plan of a FATALITY sync, or null. */
   @Nullable
   public FatalityPlan plan() {
      FatalityScene scene = FatalityScene.byId(this.variant);
      if (this.kind != Kind.FATALITY || scene == null) {
         return null;
      }

      return new FatalityPlan(scene, SceneEntry.byId(this.entry), this.a, this.yawA, this.b, this.yawB, this.c, this.d, this.yawC, Math.max(1, this.intro));
   }

   public GrabFlow.Stage grabStage() {
      GrabFlow.Stage[] all = GrabFlow.Stage.values();
      return all[Math.max(0, Math.min(all.length - 1, this.stage))];
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeByte(this.kind.ordinal());
      buf.writeBoolean(this.end);
      buf.writeVarInt(this.thorId);
      buf.writeVarInt(this.victimId + 1);
      buf.writeByte(this.stage);
      buf.writeShort(this.variant);
      buf.writeByte(this.entry);
      buf.writeVarInt(Math.max(0, this.tick));
      buf.writeByte(this.resistKey);
      buf.writeFloat(this.meter);
      buf.writeBoolean(this.rehearsal);
      vec(buf, this.a);
      vec(buf, this.b);
      vec(buf, this.c);
      vec(buf, this.d);
      buf.writeFloat(this.yawA);
      buf.writeFloat(this.yawB);
      buf.writeFloat(this.yawC);
      buf.writeVarInt(Math.max(0, this.intro));
   }

   public static SceneSync read(FriendlyByteBuf buf) {
      Kind kind = Kind.byId(buf.readByte());
      boolean end = buf.readBoolean();
      int thor = buf.readVarInt();
      int victim = buf.readVarInt() - 1;
      int stage = buf.readByte();
      int variant = buf.readShort();
      int entry = buf.readByte();
      int tick = buf.readVarInt();
      int key = buf.readByte();
      float meter = buf.readFloat();
      boolean rehearsal = buf.readBoolean();
      Vec3 a = vec(buf);
      Vec3 b = vec(buf);
      Vec3 c = vec(buf);
      Vec3 d = vec(buf);
      return new SceneSync(kind, end, thor, victim, stage, variant, entry, tick, key, meter, rehearsal, a, b, c, d, buf.readFloat(), buf.readFloat(),
         buf.readFloat(), buf.readVarInt());
   }

   private static void vec(FriendlyByteBuf buf, Vec3 v) {
      buf.writeDouble(v.x);
      buf.writeDouble(v.y);
      buf.writeDouble(v.z);
   }

   private static Vec3 vec(FriendlyByteBuf buf) {
      return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
   }
}
