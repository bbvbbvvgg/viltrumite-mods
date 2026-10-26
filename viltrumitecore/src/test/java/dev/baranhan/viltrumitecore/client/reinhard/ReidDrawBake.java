package dev.baranhan.viltrumitecore.client.reinhard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * REINHARD OVERHAUL item 15: generator of the {@code reid_draw} (6 s) and {@code reid_sheathe} (1 s)
 * clips from one authored choreography ({@link ReidDrawChoreo} timing). The weapon path is authored as
 * rigid frames (handle, blade axis, face normal) in body space; both arms are solved per tick with the
 * runtime grip constraint ({@link ReinhardGrip}) and authored elbow poles, then stored as keys, so the
 * renderer only refines palms that are already on the hilt and on the scabbard throat.
 * Regenerate: {@code REINHARD_BAKE=1 ./gradlew :viltrumitecore:test --tests '*ReidDrawBakeTest*'}.
 */
final class ReidDrawBake {
   static final Path ANIMATIONS = Path.of("src/main/resources/assets/viltrumitecore/animations/reinhard/reinhard.animation.json");
   static final float SY = ReinhardSword.SWORD_SCALE * ReinhardSword.LENGTH_SCALE;
   /** Handle centre (native -3.5) to the right palm (native 0) along the axis, px. */
   static final float PALM_AHEAD = (ReidDrawChoreo.RIGHT_GRIP + 3.5F) * SY;
   static final String[] ARMS = {"right_arm", "right_forearm", "left_arm", "left_forearm"};

   // ---- authored acting: {draw tick, json x, json y, json z} ----
   static final double[][] BODY_ROT = {{0, 0, 0, 0}, {5, 3, -3, 0}, {12, 4, -5, 0}, {18, 1, -3, 0}, {26, -1, 0, 0}, {32, 0, 1, 0},
      {37, .5, 1, 0}, {60, 1.5, 3, 0}, {86, 2.5, 5, 0}, {88, 2.9, 4.6, .4}, {90, 2.3, 5.4, -.4}, {92, 3, 5, .3}, {94, 1, 6, 0},
      {96, -2.5, 8, 0}, {100, -1, 9, 0}, {106, 2, 8, 0}, {112, 2.6, 6.5, 0}, {120, 2.3, 6, 0}};
   static final double[][] BODY_POS = {{0, 0, 0, 0}, {12, 0, -.1, 0}, {26, 0, 0, 0}, {36, 0, -.375, 0}, {86, 0, -.45, 0}, {92, 0, -.5, 0},
      {96, 0, -.2, 0}, {104, 0, -.3, 0}, {120, 0, -.375, 0}};
   static final double[][] HEAD_ROT = {{0, 0, 0, 0}, {6, 12, 10, 0}, {14, 14, 9, 0}, {22, 4, 6, 0}, {26.5, -6.5, 8.5, 0}, {29, -4.5, 6.5, 0}, {33, -1, 4, 0}, {60, 3, 2, 0},
      {86, 2.5, 3, 0}, {88, 3, 2.5, 0}, {90, 2.2, 3.4, 0}, {92, 2.8, 2.9, 0}, {96, -2, 0, 0}, {102, -4, -5, 0}, {110, -1.5, -2, 0}, {120, -.5, 0, 0}};
   static final double[][] RIGHT_LEG = {{0, 0, 0, 0}, {20, 0, 0, 0}, {30, 5, 0, 3}, {36, 7, 0, 4}, {86, 8, 0, 4.5}, {96, 7, 0, 4}, {120, 7, 0, 4}};
   static final double[][] LEFT_LEG = {{0, 0, 0, 0}, {20, 0, 0, 0}, {30, -6, 0, -2}, {36, -9, 0, -3}, {86, -10, 0, -3.5}, {96, -9, 0, -3}, {120, -9, 0, -3}};
   static final double[][] RIGHT_SHIN = {{0, 0, 0, 0}, {20, 0, 0, 0}, {36, 9, 0, 0}, {86, 11, 0, 0}, {96, 9, 0, 0}, {120, 9, 0, 0}};
   static final double[][] LEFT_SHIN = {{0, 0, 0, 0}, {20, 0, 0, 0}, {36, 7, 0, 0}, {86, 9, 0, 0}, {96, 7, 0, 0}, {120, 7, 0, 0}};

   // ---- authored weapon path (body-local Bedrock px, +x = Reinhard's left, -z = front) ----
   /** Presentation (30): handle level across the chest, broad face to the front. */
   static final Vector3f R1 = new Vector3f(-1.0F, 20.0F, -7.0F);
   /** Release (96): right palm far right, left palm (throat) far left. */
   static final Vector3f R2 = new Vector3f(-11.6F, 20.6F, -4.8F);
   static final Vector3f AXIS = new Vector3f(.996F, -.087F, 0).normalize();
   static final Vector3f FACE = new Vector3f(0, 0, -1);
   /** Lift (16-30): handle centre keys (the frame turns by one slerp). */
   static final double[][] LIFT = {{20, 3.4, 18.6, -5.6}, {24, -1.4, 20.0, -7.0}};
   /** The sheathed sword is level in front of the chest (the right hand meets the handle there). */
   static final double PRESENTED = 27;
   /** After the release the freed blade rises in one arc through the vertical into the guard:
    * {draw tick, right palm x y z, tip direction x y z, fraction of the roll into the Sword Drawn edge}. */
   static final double[][] ARC = {{99, -11.8, 21.0, -5.6, .85, .5, -.15, 0}, {103, -10.5, 22.0, -6.0, .15, .98, -.15, .1},
      {108, -6.5, 20.5, -7.0, -.55, .82, -.15, .55}, {113, -3.4, 19.2, -6.5, -.75, .65, -.13, .9}};
   /** Docking (96-110): left palm (throat) keys; the scabbard turns by one slerp to the hip. */
   static final double[][] DOCK_PALM = {{100, 11.2, 17.6, -4.0}, {105, 8.9, 15.6, -1.4}};
   /** Elbow poles in body space (Bedrock): right arm, left arm, at draw ticks. */
   static final double[][] POLE_R = {{16, -1, -1.5, -1}, {26, -.6, -.8, -.3}, {34, -.5, -.8, -.2}, {96, -.6, -.6, .3}, {100, -.8, -.55, .1},
      {104, -.75, -.65, 0}, {108, -.5, -.87, 0}, {113, -.45, -.85, -.25}, {118, -1, -1.5, -1}};
   /** Left elbow poles: back while the hand closes on the throat, out to the side as it lifts, then raised
    * in front (an overhand hold of the level scabbard, the forearm across it), lowering as the arms open. */
   static final double[][] POLE_L = {{8, 1, 0, -.25}, {10, .86, 0, .51}, {12, .25, 0, .97}, {14, -.5, 0, .87}, {17, -.18, -.71, .68},
      {18.5, .34, -.68, .65}, {20, .77, -.46, .44}, {21.5, .99, -.12, .11}, {23, .93, .26, -.25}, {26, .56, .58, -.57}, {28, .55, .4, -.73},
      {31, .5, .22, -.84}, {34, .8, .2, -.55}, {60, .7, -.3, -.3}, {96, .6, -.6, .3}, {104, .5, -.8, .2}, {110, 1, 0, -.25}};
   /** Right palm reach before it takes the handle (16-26). */
   static final double[][] REACH = {{20, -5.5, 16.0, -5.5}, {24, -3.6, 18.8, -8.2}};

   private ReidDrawBake() {
   }

   static Vector3f baked(Vector3f bedrockPx) {
      return new Vector3f(-bedrockPx.x / 16, bedrockPx.y / 16, bedrockPx.z / 16);
   }

   static Vector3f bedrock(Vector3f baked) {
      return new Vector3f(-baked.x * 16, baked.y * 16, baked.z * 16);
   }

   static Vector3f row(double[] r) {
      return new Vector3f((float)r[1], (float)r[2], (float)r[3]);
   }

   /** Rotation whose native y is {@code axis} and native z {@code face} (Bedrock directions). */
   static Quaternionf basis(Vector3f axisBed, Vector3f faceBed) {
      Vector3f a = new Vector3f(-axisBed.x, axisBed.y, axisBed.z).normalize();
      Vector3f n = new Vector3f(-faceBed.x, faceBed.y, faceBed.z);
      n.sub(new Vector3f(a).mul(n.dot(a))).normalize();
      Vector3f w = new Vector3f(a).cross(n).normalize();
      return new Matrix3f(w, a, n).getNormalizedRotation(new Quaternionf());
   }

   static double ease(double[] times, double[] values, double d) {
      return ReidDrawChoreo.monotone(times, values, d);
   }

   static Vector3f curve(List<double[]> keys, double d) {
      double[] t = new double[keys.size()], x = new double[keys.size()], y = new double[keys.size()], z = new double[keys.size()];
      for (int i = 0; i < keys.size(); i++) {
         t[i] = keys.get(i)[0];
         x[i] = keys.get(i)[1];
         y[i] = keys.get(i)[2];
         z[i] = keys.get(i)[3];
      }
      return new Vector3f((float)ease(t, x, d), (float)ease(t, y, d), (float)ease(t, z, d));
   }

   static double[] key(double d, Vector3f v) {
      return new double[]{d, v.x, v.y, v.z};
   }

   /** Rendered blade handle frame (baked, blocks): translation = handle centre, rotation = native axes. */
   record Frame(Vector3f handle, Quaternionf rotation) {
      Matrix4f grip() {
         return new Matrix4f().translation(handle).rotate(rotation);
      }

      Vector3f axisBed() {
         Vector3f a = rotation.transform(new Vector3f(0, 1, 0));
         return new Vector3f(-a.x, a.y, a.z);
      }
   }

   /** The sheathed (hip) and the Sword Drawn (rendered, rolled) handle frames, sampled from their clips. */
   static Frame sheathed, drawn;

   static void anchors(Map<String, Animation> clips) throws Exception {
      if (sheathed != null) return;
      sheathed = boneFrame(clips.get("idle_holy"), 0);
      Frame unrolled = boneFrame(clips.get("idle_holy_drawn"), 0);
      drawn = new Frame(unrolled.handle(), new Quaternionf(unrolled.rotation()).rotateY((float)Math.PI / 2));
   }

   static Frame boneFrame(Animation clip, double seconds) throws Exception {
      BakedGeoModel b = ReinhardSecondPassTest.body();
      AnimationController.seek(clip, b, seconds);
      GeoBone g = b.getBone("reid_grip");
      Matrix4f local = new Matrix4f().translation(-g.posX / 16, g.posY / 16, g.posZ / 16).rotateZ(g.rotZ).rotateY(g.rotY).rotateX(g.rotX);
      return new Frame(local.getTranslation(new Vector3f()), local.getUnnormalizedRotation(new Quaternionf()).normalize());
   }

   static Vector3f handleOf(Vector3f palmBed, Vector3f axisBed) {
      return baked(new Vector3f(palmBed).sub(new Vector3f(axisBed).normalize().mul(PALM_AHEAD)));
   }

   static Frame presentation() {
      return new Frame(handleOf(R1, AXIS), basis(AXIS, FACE));
   }

   static Frame release() {
      return new Frame(handleOf(R2, AXIS), basis(AXIS, FACE));
   }

   /** Right palm during the extraction (30-96), on the line between presentation and release. */
   static Vector3f extractionPalm(double d) {
      return new Vector3f(R1).lerp(R2, ReidDrawChoreo.bladeOut(d));
   }

   /** The rendered blade frame at draw tick {@code d}. */
   static Frame blade(double d) {
      if (d <= ReidDrawChoreo.THROAT_CLOSED) return sheathed;
      if (d <= ReidDrawChoreo.LIFT_END) {
         Frame p = presentation();
         List<double[]> keys = new ArrayList<>();
         keys.add(key(16, bedrock(sheathed.handle())));
         for (double[] r : LIFT) keys.add(r);
         keys.add(key(PRESENTED, bedrock(p.handle())));
         keys.add(key(PRESENTED + 1, bedrock(p.handle())));
         double k = ease(new double[]{16, PRESENTED}, new double[]{0, 1}, d);
         return new Frame(baked(curve(keys, d)), new Quaternionf(sheathed.rotation()).slerp(p.rotation(), (float)k));
      }
      if (d <= ReidDrawChoreo.RELEASE) return new Frame(handleOf(extractionPalm(d), AXIS), basis(AXIS, FACE));
      // Rising arc into the guard: palm keys; orientation carried by the smallest turns between the
      // key axes (the flat keeps facing the front), plus a growing roll into the Sword Drawn edge.
      Frame r = release();
      Quaternionf inverse = new Quaternionf(r.rotation()).conjugate();
      List<double[]> palm = new ArrayList<>(), rot = new ArrayList<>();
      for (double t : new double[]{90, 93, 96}) {
         palm.add(key(t, extractionPalm(t)));
         rot.add(new double[]{t, 0, 0, 0});
      }
      Quaternionf[] carried = new Quaternionf[ARC.length + 1];
      Quaternionf q = new Quaternionf(r.rotation());
      for (int i = 0; i < ARC.length; i++) {
         q = towards(q, new Vector3f((float)ARC[i][4], (float)ARC[i][5], (float)ARC[i][6]).normalize());
         carried[i] = new Quaternionf(q);
      }
      carried[ARC.length] = towards(q, drawn.axisBed());
      // Twist about the blade axis that turns the carried end frame into the Sword Drawn one.
      Quaternionf twist = new Quaternionf(carried[ARC.length]).conjugate().mul(drawn.rotation());
      float roll = 2 * (float)Math.atan2(twist.y, twist.w);
      if (roll > Math.PI) roll -= 2 * (float)Math.PI;
      if (roll < -Math.PI) roll += 2 * (float)Math.PI;
      for (int i = 0; i < ARC.length; i++) {
         palm.add(new double[]{ARC[i][0], ARC[i][1], ARC[i][2], ARC[i][3]});
         Quaternionf k = new Quaternionf(carried[i]).rotateY((float)(roll * ARC[i][7]));
         rot.add(key(ARC[i][0], log(new Quaternionf(inverse).mul(k))));
      }
      Vector3f drawnPalm = bedrock(drawn.handle()).add(drawn.axisBed().mul(PALM_AHEAD));
      palm.add(key(120, drawnPalm));
      palm.add(key(121, drawnPalm));
      Vector3f last = log(new Quaternionf(inverse).mul(drawn.rotation()));
      rot.add(key(120, last));
      rot.add(key(121, last));
      if (d >= ReidDrawChoreo.LENGTH) return drawn;
      Vector3f p = curve(palm, d);
      Quaternionf o = new Quaternionf(r.rotation()).mul(exp(curve(rot, d)));
      Vector3f a = o.transform(new Vector3f(0, 1, 0));
      return new Frame(baked(p).sub(new Vector3f(a).mul(PALM_AHEAD / 16)), o);
   }

   /** Smallest turn of {@code from} that brings its native y onto {@code axisBed}. */
   static Quaternionf towards(Quaternionf from, Vector3f axisBed) {
      Vector3f a = from.transform(new Vector3f(0, 1, 0));
      Vector3f target = new Vector3f(-axisBed.x, axisBed.y, axisBed.z).normalize();
      return new Quaternionf().rotationTo(a, target).mul(from);
   }

   static Vector3f log(Quaternionf q) {
      Quaternionf n = new Quaternionf(q).normalize();
      if (n.w < 0) n.mul(-1);
      float s = (float)Math.sqrt(n.x * n.x + n.y * n.y + n.z * n.z);
      if (s < 1E-7F) return new Vector3f();
      float angle = 2 * (float)Math.atan2(s, n.w);
      return new Vector3f(n.x, n.y, n.z).mul(angle / s);
   }

   static Quaternionf exp(Vector3f v) {
      float angle = v.length();
      if (angle < 1E-7F) return new Quaternionf();
      return new Quaternionf().rotationAxis(angle, v.x / angle, v.y / angle, v.z / angle);
   }

   /** The scabbard as a sheathed blade frame ("sheath as blade"): rides the blade, then docks (96-110). */
   static Frame sheath(double d) {
      Frame b = blade(d);
      if (d <= ReidDrawChoreo.RELEASE) {
         Vector3f a = b.rotation().transform(new Vector3f(0, 1, 0));
         return new Frame(new Vector3f(b.handle()).add(new Vector3f(a).mul(ReidDrawChoreo.extraction(d) * SY / 16)), b.rotation());
      }
      Frame start = sheath(ReidDrawChoreo.RELEASE);
      if (d >= ReidDrawChoreo.DOCKED) return sheathed;
      double k = ease(new double[]{96, 110}, new double[]{0, 1}, d);
      Quaternionf q = new Quaternionf(start.rotation()).slerp(sheathed.rotation(), (float)k);
      float throat = (3.5F + ReidDrawChoreo.MOUTH + ReidDrawChoreo.THROAT_GRIP) * SY;
      List<double[]> keys = new ArrayList<>();
      keys.add(key(93, throatOf(sheath(93))));
      keys.add(key(96, throatOf(start)));
      for (double[] r : DOCK_PALM) keys.add(r);
      keys.add(key(110, throatOf(sheathed)));
      keys.add(key(111, throatOf(sheathed)));
      Vector3f palm = curve(keys, d);
      Vector3f a = q.transform(new Vector3f(0, 1, 0));
      return new Frame(baked(palm).sub(new Vector3f(a).mul(throat / 16)), q);
   }

   static Vector3f throatOf(Frame sheathAsBlade) {
      Vector3f a = sheathAsBlade.rotation().transform(new Vector3f(0, 1, 0));
      float throat = (3.5F + ReidDrawChoreo.MOUTH + ReidDrawChoreo.THROAT_GRIP) * SY;
      return bedrock(new Vector3f(sheathAsBlade.handle()).add(a.mul(throat / 16)));
   }

   static Vector3f pole(double[][] keys, double d) {
      List<double[]> list = new ArrayList<>(List.of(keys));
      list.add(new double[]{keys[keys.length - 1][0] + 1, keys[keys.length - 1][1], keys[keys.length - 1][2], keys[keys.length - 1][3]});
      Vector3f p = curve(list, Math.max(keys[0][0], d));
      return new Vector3f(-p.x, p.y, p.z);
   }

   // ---- acting clip (parsed with the runtime parser, so the bake samples what the renderer samples) ----
   static JsonObject channel(double[][] keys, double scaleLength) {
      JsonObject out = new JsonObject();
      for (double[] k : keys) out.add(time(k[0] / 20.0), vectorKey(k[1], k[2], k[3]));
      return out;
   }

   static String time(double seconds) {
      return number(seconds, 4).getAsString().contains(".") ? number(seconds, 4).getAsString() : number(seconds, 4).getAsString() + ".0";
   }

   static JsonObject vectorKey(double x, double y, double z) {
      JsonObject o = new JsonObject();
      JsonArray v = new JsonArray();
      v.add(number(x, 3));
      v.add(number(y, 3));
      v.add(number(z, 3));
      o.add("vector", v);
      o.addProperty("lerp_mode", "monotone");
      return o;
   }

   static JsonPrimitive number(double value, int places) {
      BigDecimal b = new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).stripTrailingZeros();
      if (b.signum() == 0) b = BigDecimal.ZERO;
      if (b.scale() < 0) b = b.setScale(0);
      return new JsonPrimitive(b);
   }

   static JsonObject acting() {
      JsonObject bones = new JsonObject();
      bones.add("body", rotPos(channel(BODY_ROT, 1), channel(BODY_POS, 1)));
      bones.add("head", rotPos(channel(HEAD_ROT, 1), null));
      bones.add("right_leg", rotPos(channel(RIGHT_LEG, 1), null));
      bones.add("left_leg", rotPos(channel(LEFT_LEG, 1), null));
      bones.add("right_shin", rotPos(channel(RIGHT_SHIN, 1), null));
      bones.add("left_shin", rotPos(channel(LEFT_SHIN, 1), null));
      return bones;
   }

   static JsonObject rotPos(JsonObject rotation, JsonObject position) {
      JsonObject o = new JsonObject();
      if (rotation != null) o.add("rotation", rotation);
      if (position != null) o.add("position", position);
      return o;
   }

   static Animation parse(String name, double length, JsonObject bones) {
      JsonObject clip = new JsonObject();
      clip.addProperty("loop", "hold_on_last_frame");
      clip.add("animation_length", number(length, 4));
      clip.add("bones", bones);
      JsonObject animations = new JsonObject();
      animations.add(name, clip);
      JsonObject root = new JsonObject();
      root.addProperty("format_version", "1.8.0");
      root.add("animations", animations);
      return AnimationParser.parse(root).get(name);
   }

   /** One baked tick: the acted body with the weapon bones and both solved arms. */
   record Tick(double d, float[] arms, Frame grip, Frame sheath) {
   }

   static void setGrip(GeoBone bone, Frame f) {
      bone.posX = -f.handle().x * 16;
      bone.posY = f.handle().y * 16;
      bone.posZ = f.handle().z * 16;
      Vector3f e = ReinhardGrip.euler(f.rotation());
      bone.rotX = e.x;
      bone.rotY = e.y;
      bone.rotZ = e.z;
   }

   /** reid_grip carries the unrolled frame; the renderer applies {@link ReidDrawChoreo#roll}. */
   static Frame unrolled(double d) {
      Frame f = blade(d);
      return new Frame(f.handle(), new Quaternionf(f.rotation()).rotateY(-ReidDrawChoreo.roll(d)));
   }

   static BakedGeoModel posed(Map<String, Animation> clips, Animation acting, double d, float[] arms) throws Exception {
      BakedGeoModel b = ReinhardSecondPassTest.body();
      AnimationController.seek(clips.get("idle_holy"), b, 0);
      ReinhardLayers.overlay(acting, b, d / 20.0, 1);
      setGrip(b.getBone("reid_grip"), unrolled(d));
      setGrip(b.getBone("reid_sheath"), sheath(d));
      if (arms != null) for (int i = 0; i < 4; i++) {
         GeoBone g = b.getBone(ARMS[i]);
         g.rotX = arms[i * 3];
         g.rotY = arms[i * 3 + 1];
         g.rotZ = arms[i * 3 + 2];
      }
      return b;
   }

   static float[] arms(BakedGeoModel b) {
      float[] out = new float[12];
      for (int i = 0; i < 4; i++) {
         GeoBone g = b.getBone(ARMS[i]);
         out[i * 3] = g.rotX;
         out[i * 3 + 1] = g.rotY;
         out[i * 3 + 2] = g.rotZ;
      }
      return out;
   }

   static float[] rawArms(Animation clip) throws Exception {
      BakedGeoModel b = ReinhardSecondPassTest.body();
      AnimationController.seek(clip, b, 0);
      return arms(b);
   }

   static Matrix4f bladeOf(Matrix4f grip) {
      return new Matrix4f(grip).scale(ReinhardSword.SWORD_SCALE, SY, ReinhardSword.SWORD_SCALE).translate(new Vector3f(ReinhardGrip.HANDLE).negate());
   }

   /** Solves both arms of tick {@code d} from the previous tick's arms (warm start). */
   static float[] solve(Map<String, Animation> clips, Animation acting, double d, float[] seed, float[] restArms, float[] drawnArms) throws Exception {
      BakedGeoModel b = posed(clips, acting, d, d <= ReidDrawChoreo.THROAT_CLOSED ? withRight(seed, restArms) : seed);
      Matrix4f body = ReinhardGeometry.frame(b.getBone("body"));
      Matrix4f bladeLocal = bladeOf(unrolled(d).grip());
      Matrix4f blade = new Matrix4f(body).mul(bladeLocal);
      Matrix4f throat = ReinhardSword.gripAt(new Matrix4f(body).mul(bladeOf(sheath(d).grip())), ReidDrawChoreo.MOUTH + ReidDrawChoreo.THROAT_GRIP);
      // Left: resting on the hilt -> closed on the throat (8-16), on the throat until docked.
      Matrix4f leftTarget = throat;
      if (d < ReidDrawChoreo.THROAT_CLOSED) {
         float w = ReidDrawChoreo.throatWeight(d);
         leftTarget = ReinhardSword.interpolate(blade, throat, w);
         leftTarget.m31(leftTarget.m31() + (1 - w) * 1.3F / 16);
      }
      ReinhardGrip.solve(b, leftTarget, 1, true, false, pole(POLE_L, d), 1);
      // Right: free reach (16-26), then the handle behind the guard.
      if (d > ReidDrawChoreo.THROAT_CLOSED) {
         Matrix4f handle = ReinhardSword.gripAt(blade, ReidDrawChoreo.RIGHT_GRIP);
         Matrix4f target = handle;
         if (d < 26) {
            // The reach is a palm PATH: blend the points, not the frames (a slerped frame
            // swings the handle offset out of line and the solve loses the palm).
            Vector3f grip = handle.transformPosition(new Vector3f(ReinhardGrip.HANDLE));
            Vector3f palm = body.transformPosition(baked(curve(reachKeys(), d))).lerp(grip, ReidDrawChoreo.smooth((d - 23) / 3));
            target = new Matrix4f(handle).setTranslation(handle.getTranslation(new Vector3f()).add(palm).sub(grip));
         }
         // The elbow leaves the rest pose's plane gradually: no snap onto the pole at 16.
         Vector3f pole = new Vector3f(restElbow(clips)).lerp(pole(POLE_R, d).normalize(), ReidDrawChoreo.smooth((d - ReidDrawChoreo.THROAT_CLOSED) / 6));
         ReinhardGrip.solve(b, target, 1, false, true, pole, 1);
      }
      float[] out = arms(b);
      if (d > ReidDrawChoreo.DOCKED) {
         float k = ReidDrawChoreo.smooth((d - ReidDrawChoreo.DOCKED) / (ReidDrawChoreo.LENGTH - ReidDrawChoreo.DOCKED));
         out = blend(out, drawnArms, k);
      }
      return out;
   }

   static Vector3f restPalm;

   /** Right palm reach before it takes the handle: rest palm (16), {@link #REACH}, the handle of 26. */
   static List<double[]> reachKeys() {
      List<double[]> keys = new ArrayList<>();
      keys.add(key(16, restPalm));
      for (double[] r : REACH) keys.add(r);
      Vector3f at26 = bedrock(ReinhardSword.gripAt(bladeOf(unrolled(26).grip()), ReidDrawChoreo.RIGHT_GRIP).transformPosition(new Vector3f(ReinhardGrip.HANDLE)));
      keys.add(key(26, at26));
      keys.add(key(27, at26));
      return keys;
   }

   private static Vector3f restElbow;

   /** Unit shoulder-to-elbow direction of the rest right arm, in the body frame (the pole of the rest pose). */
   static Vector3f restElbow(Map<String, Animation> clips) throws Exception {
      if (restElbow == null) {
         BakedGeoModel b = ReinhardSecondPassTest.body();
         AnimationController.seek(clips.get("idle_holy"), b, 0);
         GeoBone upper = b.getBone("right_arm"), lower = b.getBone("right_forearm");
         Vector3f shoulder = ReinhardGeometry.frame(upper).transformPosition(new Vector3f(upper.pivotX / 16, upper.pivotY / 16, upper.pivotZ / 16));
         Vector3f elbow = ReinhardGeometry.frame(lower).transformPosition(new Vector3f(lower.pivotX / 16, lower.pivotY / 16, lower.pivotZ / 16));
         Matrix4f body = ReinhardGeometry.frame(b.getBone("body"));
         restElbow = new Matrix4f(body).invert().transformDirection(elbow.sub(shoulder)).normalize();
      }
      return restElbow;
   }

   /** The seed with the right arm at rest (it is not solved until it leaves the rest, after 16). */
   static float[] withRight(float[] seed, float[] restArms) {
      float[] out = seed.clone();
      System.arraycopy(restArms, 0, out, 0, 6);
      return out;
   }

   static float[] blend(float[] a, float[] b, float k) {
      float[] out = new float[12];
      for (int i = 0; i < 4; i++) {
         Quaternionf qa = new Quaternionf().rotationZYX(a[i * 3 + 2], a[i * 3 + 1], a[i * 3]);
         Quaternionf qb = new Quaternionf().rotationZYX(b[i * 3 + 2], b[i * 3 + 1], b[i * 3]);
         Vector3f e = ReinhardGrip.euler(qa.slerp(qb, k));
         out[i * 3] = e.x;
         out[i * 3 + 1] = e.y;
         out[i * 3 + 2] = e.z;
      }
      return out;
   }

   static List<Tick> bake(Map<String, Animation> clips, double step) throws Exception {
      anchors(clips);
      Animation acting = parse("reid_draw_acting", 6, acting());
      float[] rest = rawArms(clips.get("idle_holy")), drawnArms = rawArms(clips.get("idle_holy_drawn"));
      BakedGeoModel restBody = ReinhardSecondPassTest.body();
      AnimationController.seek(clips.get("idle_holy"), restBody, 0);
      restPalm = bedrock(ReinhardGrip.point(restBody, false));
      List<Tick> out = new ArrayList<>();
      float[] seed = rest.clone();
      for (double d = 0; d <= ReidDrawChoreo.LENGTH + 1E-9; d += step) {
         float[] arms = solve(clips, acting, d, seed, rest, drawnArms);
         // Euler continuity: keep each channel on the branch nearest the previous key.
         for (int i = 0; i < 12; i++) arms[i] = unwrap(arms[i], seed[i]);
         seed = arms;
         out.add(new Tick(d, arms, unrolled(d), sheath(d)));
      }
      return out;
   }

   static float unwrap(float value, float near) {
      float twoPi = (float)(2 * Math.PI);
      while (value - near > Math.PI) value -= twoPi;
      while (value - near < -Math.PI) value += twoPi;
      return value;
   }

   // ---- writer ----
   /** One sampled channel: times (s) and values (json units: degrees / px). */
   record Samples(double[] t, double[][] v) {
   }

   /** JSON rotation (degrees, Bedrock signs) of renderer Euler radians. */
   static double[] jsonRotation(float x, float y, float z) {
      return new double[]{-Math.toDegrees(x), -Math.toDegrees(y), Math.toDegrees(z)};
   }

   static double[] near(double[] value, double[] previous) {
      if (previous == null) return value;
      double[] out = value.clone();
      for (int i = 0; i < 3; i++) {
         while (out[i] - previous[i] > 180) out[i] -= 360;
         while (out[i] - previous[i] < -180) out[i] += 360;
      }
      return out;
   }

   static double[] gripRotation(Frame f, double[] previous) {
      Vector3f e = ReinhardGrip.euler(f.rotation());
      return near(jsonRotation(e.x, e.y, e.z), previous);
   }

   static double[] gripPosition(Frame f) {
      return new double[]{-f.handle().x * 16, f.handle().y * 16, f.handle().z * 16};
   }

   /** Piecewise-linear key reduction within {@code tolerance}; the first and last samples are always kept. */
   static List<Integer> reduce(Samples s, double tolerance) {
      List<Integer> keys = new ArrayList<>();
      int n = s.t().length, i = 0;
      keys.add(0);
      for (int j = 2; j < n; j++) {
         boolean ok = true;
         for (int m = i + 1; m < j && ok; m++) {
            double k = (s.t()[m] - s.t()[i]) / (s.t()[j] - s.t()[i]);
            for (int c = 0; c < 3; c++) {
               double lerp = s.v()[i][c] + (s.v()[j][c] - s.v()[i][c]) * k;
               if (Math.abs(lerp - s.v()[m][c]) > tolerance) {
                  ok = false;
                  break;
               }
            }
         }
         if (!ok) {
            i = j - 1;
            keys.add(i);
         }
      }
      if (keys.get(keys.size() - 1) != n - 1) keys.add(n - 1);
      return keys;
   }

   static JsonObject keys(Samples s, double tolerance, int places) {
      JsonObject out = new JsonObject();
      for (int i : reduce(s, tolerance)) {
         JsonObject o = new JsonObject();
         JsonArray v = new JsonArray();
         for (int c = 0; c < 3; c++) v.add(number(s.v()[i][c], places));
         o.add("vector", v);
         o.addProperty("lerp_mode", "monotone");
         out.add(time(s.t()[i]), o);
      }
      return out;
   }

   static final double ROTATION_TOLERANCE = .12, POSITION_TOLERANCE = .015;

   /** The dense draw table: half-tick samples of the solved arms and both weapon frames. */
   static List<Tick> dense;

   static List<Tick> dense(Map<String, Animation> clips) throws Exception {
      if (dense == null) dense = bake(clips, .5);
      return dense;
   }

   /** Tick of the dense table at draw tick {@code d} (rotations slerped, positions lerped). */
   static Tick at(List<Tick> table, double d) {
      double x = Math.max(0, Math.min(ReidDrawChoreo.LENGTH, d)) * 2;
      int i = Math.min(table.size() - 2, (int)Math.floor(x));
      float k = (float)(x - i);
      Tick a = table.get(i), b = table.get(i + 1);
      if (k <= 0) return a;
      float[] arms = blend(a.arms(), b.arms(), k);
      for (int c = 0; c < 12; c++) arms[c] = unwrap(arms[c], a.arms()[c]);
      return new Tick(d, arms, mix(a.grip(), b.grip(), k), mix(a.sheath(), b.sheath(), k));
   }

   static Frame mix(Frame a, Frame b, float k) {
      return new Frame(new Vector3f(a.handle()).lerp(b.handle(), k), new Quaternionf(a.rotation()).slerp(b.rotation(), k));
   }

   /** Channels of a clip sampled at {@code times} (s) from draw ticks {@code draw}. */
   static JsonObject weaponAndArms(List<Tick> table, double[] times, double[] draw, double sheathFrom) {
      JsonObject bones = new JsonObject();
      int n = times.length;
      for (int b = 0; b < 4; b++) {
         double[][] v = new double[n][];
         for (int i = 0; i < n; i++) {
            float[] arms = at(table, draw[i]).arms();
            v[i] = near(jsonRotation(arms[b * 3], arms[b * 3 + 1], arms[b * 3 + 2]), i == 0 ? null : v[i - 1]);
         }
         bones.add(ARMS[b], rotPos(keys(new Samples(times, v), ROTATION_TOLERANCE, 3), null));
      }
      double[][] gp = new double[n][], gr = new double[n][];
      for (int i = 0; i < n; i++) {
         Tick t = at(table, draw[i]);
         gp[i] = gripPosition(t.grip());
         gr[i] = gripRotation(t.grip(), i == 0 ? null : gr[i - 1]);
      }
      bones.add("reid_grip", rotPos(keys(new Samples(times, gr), ROTATION_TOLERANCE, 3), keys(new Samples(times, gp), POSITION_TOLERANCE, 4)));
      List<Integer> free = new ArrayList<>();
      for (int i = 0; i < n; i++) if (draw[i] >= sheathFrom) free.add(i);
      if (!free.isEmpty()) {
         double[] st = new double[free.size()];
         double[][] sp = new double[free.size()][], sr = new double[free.size()][];
         for (int j = 0; j < free.size(); j++) {
            int i = free.get(j);
            Tick t = at(table, draw[i]);
            st[j] = times[i];
            sp[j] = gripPosition(t.sheath());
            sr[j] = gripRotation(t.sheath(), j == 0 ? null : sr[j - 1]);
         }
         bones.add("reid_sheath", rotPos(keys(new Samples(st, sr), ROTATION_TOLERANCE, 3), keys(new Samples(st, sp), POSITION_TOLERANCE, 4)));
      }
      return bones;
   }

   static JsonObject clip(double length, JsonObject bones) {
      JsonObject clip = new JsonObject();
      clip.addProperty("loop", "hold_on_last_frame");
      clip.add("animation_length", new JsonPrimitive(length));
      clip.add("bones", bones);
      return clip;
   }

   /** reid_draw: the authored acting + the solved arms and weapon frames, 6 s. */
   static JsonObject drawClip(Map<String, Animation> clips) throws Exception {
      List<Tick> table = dense(clips);
      int n = table.size();
      double[] times = new double[n], draw = new double[n];
      for (int i = 0; i < n; i++) {
         draw[i] = table.get(i).d();
         times[i] = draw[i] / 20.0;
      }
      JsonObject bones = acting();
      for (Map.Entry<String, JsonElement> e : weaponAndArms(table, times, draw, ReidDrawChoreo.RELEASE - 1).entrySet()) bones.add(e.getKey(), e.getValue());
      return clip(6.0, bones);
   }

   /** reid_sheathe: the same choreography replayed through {@link ReidDrawChoreo#drawTick} for SHEATHING, 1 s. */
   static JsonObject sheatheClip(Map<String, Animation> clips) throws Exception {
      List<Tick> table = dense(clips);
      int length = dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.REID_SHEATHE_TICKS;
      int n = length * 4 + 1;
      double[] times = new double[n], draw = new double[n];
      for (int i = 0; i < n; i++) {
         double tau = i / 4.0;
         times[i] = tau / 20.0;
         draw[i] = ReidDrawChoreo.drawTick(ReinhardPoses.ReidPhase.SHEATHING, tau);
      }
      Animation acting = parse("reid_draw_acting", 6, acting());
      JsonObject bones = new JsonObject();
      String[][] channels = {{"body", "rotation"}, {"body", "position"}, {"head", "rotation"}, {"right_leg", "rotation"}, {"left_leg", "rotation"},
         {"right_shin", "rotation"}, {"left_shin", "rotation"}};
      Map<String, double[][]> sampled = new LinkedHashMap<>();
      for (String[] c : channels) sampled.put(c[0] + "/" + c[1], new double[n][]);
      for (int i = 0; i < n; i++) {
         BakedGeoModel b = ReinhardSecondPassTest.body();
         ReinhardLayers.overlay(acting, b, draw[i] / 20.0, 1);
         for (String[] c : channels) {
            GeoBone g = b.getBone(c[0]);
            double[][] v = sampled.get(c[0] + "/" + c[1]);
            v[i] = c[1].equals("rotation") ? near(jsonRotation(g.rotX, g.rotY, g.rotZ), i == 0 ? null : v[i - 1]) : new double[]{g.posX, g.posY, g.posZ};
         }
      }
      for (String[] c : channels) {
         JsonObject bone = bones.has(c[0]) ? bones.getAsJsonObject(c[0]) : new JsonObject();
         boolean rotation = c[1].equals("rotation");
         bone.add(c[1], keys(new Samples(times, sampled.get(c[0] + "/" + c[1])), rotation ? ROTATION_TOLERANCE : POSITION_TOLERANCE, rotation ? 3 : 4));
         bones.add(c[0], bone);
      }
      for (Map.Entry<String, JsonElement> e : weaponAndArms(table, times, draw, ReidDrawChoreo.RELEASE - 1).entrySet()) bones.add(e.getKey(), e.getValue());
      return clip(1.0, bones);
   }

   /** Rewrites both clips in place in the animation file, in the file's own (Python json, indent 2) format. */
   static void write(Map<String, Animation> clips) throws Exception {
      JsonObject root = JsonParser.parseString(Files.readString(ANIMATIONS)).getAsJsonObject();
      JsonObject animations = root.getAsJsonObject("animations");
      animations.add("reid_draw", drawClip(clips));
      animations.add("reid_sheathe", sheatheClip(clips));
      StringBuilder out = new StringBuilder();
      json(out, root, 0);
      Files.writeString(ANIMATIONS, out.append('\n').toString());
   }

   static void json(StringBuilder out, JsonElement e, int depth) {
      if (e.isJsonObject()) {
         JsonObject o = e.getAsJsonObject();
         if (o.size() == 0) {
            out.append("{}");
            return;
         }
         out.append("{\n");
         int i = 0;
         for (Map.Entry<String, JsonElement> m : o.entrySet()) {
            if (i++ > 0) out.append(",\n");
            out.append("  ".repeat(depth + 1));
            string(out, m.getKey());
            out.append(": ");
            json(out, m.getValue(), depth + 1);
         }
         out.append('\n').append("  ".repeat(depth)).append('}');
      } else if (e.isJsonArray()) {
         JsonArray a = e.getAsJsonArray();
         if (a.size() == 0) {
            out.append("[]");
            return;
         }
         out.append("[\n");
         for (int i = 0; i < a.size(); i++) {
            if (i > 0) out.append(",\n");
            out.append("  ".repeat(depth + 1));
            json(out, a.get(i), depth + 1);
         }
         out.append('\n').append("  ".repeat(depth)).append(']');
      } else if (e.isJsonNull()) {
         out.append("null");
      } else {
         JsonPrimitive p = e.getAsJsonPrimitive();
         if (p.isString()) string(out, p.getAsString());
         else if (p.isBoolean()) out.append(p.getAsBoolean());
         else if (p.getAsNumber() instanceof BigDecimal b) out.append(b.toPlainString());
         else if (p.getAsNumber() instanceof Double x) out.append(x == Math.rint(x) && Math.abs(x) < 1E15 ? String.format(java.util.Locale.ROOT, "%.1f", x) : Double.toString(x));
         else out.append(p.getAsString());
      }
   }

   /** Python json.dumps string escaping (ensure_ascii). */
   static void string(StringBuilder out, String s) {
      out.append('"');
      for (char c : s.toCharArray()) {
         switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            default -> {
               if (c < 0x20 || c > 0x7E) out.append(String.format("\\u%04x", (int)c));
               else out.append(c);
            }
         }
      }
      out.append('"');
   }

   public static void main(String[] args) throws Exception {
      var clips = ReinhardSecondPassTest.clips();
      if (args.length > 0 && args[0].equals("write")) {
         write(clips);
         System.out.println("written " + ANIMATIONS.toAbsolutePath());
         return;
      }
      List<Tick> ticks = bake(clips, 1);
      System.out.println("ticks " + ticks.size());
   }
}
