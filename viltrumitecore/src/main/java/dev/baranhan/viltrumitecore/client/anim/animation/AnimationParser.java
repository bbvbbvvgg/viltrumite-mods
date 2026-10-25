package dev.baranhan.viltrumitecore.client.anim.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

public final class AnimationParser {
   public static final List<String> lastWarnings = new ArrayList<>();

   public static Map<String, Animation> parse(JsonObject root) {
      lastWarnings.clear();
      Map<String, Animation> out = new LinkedHashMap<>();
      JsonObject animations = root.getAsJsonObject("animations");
      if (animations == null) {
         return out;
      } else {
         for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            out.put(entry.getKey(), parseSingle(entry.getKey(), entry.getValue().getAsJsonObject()));
         }

         return out;
      }
   }

   private static Animation parseSingle(String name, JsonObject json) {
      Animation.LoopType loop = parseLoop(json.get("loop"));
      Map<String, Animation.BoneAnimation> bones = new LinkedHashMap<>();
      JsonObject bonesJson = json.getAsJsonObject("bones");
      double maxKeyframeTime = 0.0;
      if (bonesJson != null) {
         for (Map.Entry<String, JsonElement> e : bonesJson.entrySet()) {
            if (e.getValue().isJsonObject()) {
               JsonObject bone = e.getValue().getAsJsonObject();
               KeyframeStack[] rot = buildChannel(bone.get("rotation"), AnimationParser.Channel.ROTATION);
               KeyframeStack[] pos = buildChannel(bone.get("position"), AnimationParser.Channel.POSITION);
               KeyframeStack[] scl = buildChannel(bone.get("scale"), AnimationParser.Channel.SCALE);
               bones.put(e.getKey(), new Animation.BoneAnimation(e.getKey(), rot[0], rot[1], rot[2], pos[0], pos[1], pos[2], scl[0], scl[1], scl[2]));
               maxKeyframeTime = Math.max(maxKeyframeTime, lastTimeOf(bone));
            }
         }
      }

      double length = json.has("animation_length") ? json.get("animation_length").getAsDouble() : maxKeyframeTime;
      if (length <= 0.0) {
         length = 1.0E-4;
      }

      List<Animation.Event> events = new ArrayList<>();
      collectEvents(json.getAsJsonObject("sound_effects"), Animation.Event.Type.SOUND, events);
      collectEvents(json.getAsJsonObject("particle_effects"), Animation.Event.Type.PARTICLE, events);
      collectEvents(json.getAsJsonObject("timeline"), Animation.Event.Type.INSTRUCTION, events);
      events.sort(Comparator.comparingDouble(Animation.Event::time));
      return new Animation(name, length, loop, bones, events);
   }

   private static Animation.LoopType parseLoop(JsonElement el) {
      if (el == null || el.isJsonNull()) {
         return Animation.LoopType.PLAY_ONCE;
      } else if (el.isJsonPrimitive()) {
         JsonPrimitive p = el.getAsJsonPrimitive();
         if (p.isBoolean()) {
            return p.getAsBoolean() ? Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE;
         } else {
            String s = p.getAsString().toLowerCase();

            return switch (s) {
               case "true", "loop" -> Animation.LoopType.LOOP;
               case "hold_on_last_frame" -> Animation.LoopType.HOLD_ON_LAST_FRAME;
               default -> Animation.LoopType.PLAY_ONCE;
            };
         }
      } else {
         return Animation.LoopType.PLAY_ONCE;
      }
   }

   private static KeyframeStack[] buildChannel(JsonElement el, AnimationParser.Channel channel) {
      KeyframeStack fallbackX = KeyframeStack.empty(channel.defaultValue);
      KeyframeStack fallbackY = KeyframeStack.empty(channel.defaultValue);
      KeyframeStack fallbackZ = KeyframeStack.empty(channel.defaultValue);
      if (el != null && !el.isJsonNull()) {
         if (el.isJsonArray()) {
            float[] v = readVector(el.getAsJsonArray(), channel);
            return new KeyframeStack[]{constant(v[0]), constant(v[1]), constant(v[2])};
         } else if (!el.isJsonObject()) {
            return new KeyframeStack[]{fallbackX, fallbackY, fallbackZ};
         } else {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("vector") && !looksLikeTimeKeys(obj)) {
               float[] v = readVector(obj.getAsJsonArray("vector"), channel);
               return new KeyframeStack[]{constant(v[0]), constant(v[1]), constant(v[2])};
            } else {
               List<AnimationParser.Entry> entries = new ArrayList<>();

               for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                  double time;
                  try {
                     time = Double.parseDouble(e.getKey());
                  } catch (NumberFormatException var16) {
                     continue;
                  }

                  entries.add(readEntry(time, e.getValue(), channel));
               }

               if (entries.isEmpty()) {
                  return new KeyframeStack[]{fallbackX, fallbackY, fallbackZ};
               } else {
                  entries.sort(Comparator.comparingDouble(a -> a.time));
                  int n = entries.size();
                  double[] times = new double[n];
                  DoubleUnaryOperator[] easings = new DoubleUnaryOperator[n];
                  boolean[] smooth = new boolean[n];
                  boolean[] monotone = new boolean[n];
                  float[][] pre = new float[3][n];
                  float[][] post = new float[3][n];

                  for (int i = 0; i < n; i++) {
                     AnimationParser.Entry en = entries.get(i);
                     times[i] = en.time;
                     easings[i] = en.easing;
                     smooth[i] = en.smooth;
                     monotone[i] = en.monotone;

                     for (int axis = 0; axis < 3; axis++) {
                        pre[axis][i] = en.pre[axis];
                        post[axis][i] = en.post[axis];
                     }
                  }

                  KeyframeStack[] out = new KeyframeStack[3];

                  for (int axis = 0; axis < 3; axis++) {
                     out[axis] = new KeyframeStack(times, pre[axis], post[axis], easings, smooth, monotone, channel.defaultValue);
                  }

                  return out;
               }
            }
         }
      } else {
         return new KeyframeStack[]{fallbackX, fallbackY, fallbackZ};
      }
   }

   private static boolean looksLikeTimeKeys(JsonObject obj) {
      for (String key : obj.keySet()) {
         try {
            Double.parseDouble(key);
            return true;
         } catch (NumberFormatException var4) {
         }
      }

      return false;
   }

   private static KeyframeStack constant(float value) {
      return new KeyframeStack(new double[]{0.0}, new float[]{value}, new float[]{value}, new DoubleUnaryOperator[]{Easing.LINEAR}, new boolean[]{false}, value);
   }

   private static AnimationParser.Entry readEntry(double time, JsonElement value, AnimationParser.Channel channel) {
      DoubleUnaryOperator easing = Easing.LINEAR;
      boolean smooth = false;
      boolean monotone = false;
      float[] pre;
      float[] post;
      if (value.isJsonArray()) {
         pre = readVector(value.getAsJsonArray(), channel);
         post = pre;
      } else if (value.isJsonObject()) {
         JsonObject o = value.getAsJsonObject();
         if (o.has("lerp_mode") && "catmullrom".equalsIgnoreCase(o.get("lerp_mode").getAsString())) {
            smooth = true;
         }

         monotone = o.has("lerp_mode") && "monotone".equalsIgnoreCase(o.get("lerp_mode").getAsString());

         if (o.has("easing")) {
            easing = Easing.byName(o.get("easing").getAsString());
         }

         float[] vector = o.has("vector") ? readVector(o.getAsJsonArray("vector"), channel) : null;
         pre = o.has("pre") ? readAny(o.get("pre"), channel) : vector;
         post = o.has("post") ? readAny(o.get("post"), channel) : vector;
         if (pre == null && post == null) {
            pre = post = new float[]{channel.defaultValue, channel.defaultValue, channel.defaultValue};
         } else if (pre == null) {
            pre = post;
         } else if (post == null) {
            post = pre;
         }
      } else {
         float v = readNumber(value, channel.defaultValue);
         pre = post = new float[]{v, v, v};
      }

      return new AnimationParser.Entry(time, pre, post, easing, smooth, monotone);
   }

   private static float[] readAny(JsonElement el, AnimationParser.Channel channel) {
      if (el.isJsonArray()) {
         return readVector(el.getAsJsonArray(), channel);
      } else if (el.isJsonObject() && el.getAsJsonObject().has("vector")) {
         return readVector(el.getAsJsonObject().getAsJsonArray("vector"), channel);
      } else {
         float v = readNumber(el, channel.defaultValue);
         return new float[]{v, v, v};
      }
   }

   private static float[] readVector(JsonArray arr, AnimationParser.Channel channel) {
      float x = arr.size() > 0 ? readNumber(arr.get(0), channel.defaultValue) : channel.defaultValue;
      float y = arr.size() > 1 ? readNumber(arr.get(1), channel.defaultValue) : channel.defaultValue;
      float z = arr.size() > 2 ? readNumber(arr.get(2), channel.defaultValue) : channel.defaultValue;
      return channel.isRotation
         ? new float[]{(float)Math.toRadians((double)(-x)), (float)Math.toRadians((double)(-y)), (float)Math.toRadians((double)z)}
         : new float[]{x, y, z};
   }

   private static float readNumber(JsonElement el, float def) {
      if (el != null && !el.isJsonNull()) {
         if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isNumber()) {
               return p.getAsFloat();
            }

            if (p.isString()) {
               try {
                  return Float.parseFloat(p.getAsString().trim());
               } catch (NumberFormatException var4) {
                  lastWarnings.add(p.getAsString());
                  return def;
               }
            }
         }

         return def;
      } else {
         return def;
      }
   }

   private static double lastTimeOf(JsonObject boneJson) {
      double max = 0.0;

      for (String channel : new String[]{"rotation", "position", "scale"}) {
         JsonElement el = boneJson.get(channel);
         if (el != null && el.isJsonObject()) {
            for (String key : el.getAsJsonObject().keySet()) {
               try {
                  max = Math.max(max, Double.parseDouble(key));
               } catch (NumberFormatException var11) {
               }
            }
         }
      }

      return max;
   }

   private static void collectEvents(JsonObject json, Animation.Event.Type type, List<Animation.Event> out) {
      if (json != null) {
         for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            double time;
            try {
               time = Double.parseDouble(e.getKey());
            } catch (NumberFormatException var10) {
               continue;
            }

            JsonElement v = e.getValue();
            String data;
            if (v.isJsonObject()) {
               JsonObject o = v.getAsJsonObject();
               data = o.has("effect") ? o.get("effect").getAsString() : (o.has("sound") ? o.get("sound").getAsString() : o.toString());
            } else if (v.isJsonArray()) {
               data = v.getAsJsonArray().isEmpty() ? "" : v.getAsJsonArray().get(0).getAsString();
            } else {
               data = v.getAsString();
            }

            out.add(new Animation.Event(time, type, data));
         }
      }
   }

   private AnimationParser() {
   }

   private static enum Channel {
      ROTATION(0.0F, true),
      POSITION(0.0F, false),
      SCALE(1.0F, false);

      final float defaultValue;
      final boolean isRotation;

      private Channel(float defaultValue, boolean isRotation) {
         this.defaultValue = defaultValue;
         this.isRotation = isRotation;
      }
   }

   private static record Entry(double time, float[] pre, float[] post, DoubleUnaryOperator easing, boolean smooth, boolean monotone) {
   }
}
