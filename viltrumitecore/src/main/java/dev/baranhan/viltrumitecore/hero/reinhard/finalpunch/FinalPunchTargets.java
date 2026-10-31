package dev.baranhan.viltrumitecore.hero.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The explicit registry of what the Final Punch may hit (never "any LivingEntity"): people
 * (every player, whatever hero they chose; civilian humans; human-like hero NPCs) and the iron
 * golem. Animals, Chitauri, leviathans, monsters, the dragon and everything else are refused.
 * {@link Jaw} picks how the client builds the knocked-off jaw. Pure except {@link #of(Entity)}, which also
 * sizes a city citizen's entry to its own height and build.
 */
public final class FinalPunchTargets {
   /** What kind of body takes the punch (server: sounds/FX; client: bone jaw or a metal one). */
   public enum Kind {
      HUMAN,
      GOLEM;
   }

   /** Which texture layout the client cuts the jaw from. */
   public enum Jaw {
      /** 64×64 player skin layout (players, city citizens): head 8×8×8 at (0,0), hat at (32,0). */
      PLAYER_SKIN,
      /** 64×64 villager layout: head 8×10×8 at (0,0), nose 2×4×2 at (24,0). */
      VILLAGER,
      /** 128×128 iron golem layout: head 8×10×8 at (0,0), nose 2×4×2 at (24,0). */
      GOLEM,
      /** A geo-rig NPC without a vanilla head layout: a flesh-toned jaw block. */
      GENERIC;
   }

   public record Entry(String typeId, Kind kind, Jaw jaw, float headHeight, float faceDepth) {
   }

   private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

   static {
      // headHeight: bottom of the head above the feet (blocks); faceDepth: chin in front of the body centre.
      add("minecraft:player", Kind.HUMAN, Jaw.PLAYER_SKIN, 1.406F, 0.234F);
      add("minecraft:villager", Kind.HUMAN, Jaw.VILLAGER, 1.406F, 0.234F);
      add("minecraft:wandering_trader", Kind.HUMAN, Jaw.VILLAGER, 1.406F, 0.234F);
      add("viltrumitecore:citizen", Kind.HUMAN, Jaw.PLAYER_SKIN, 1.406F, 0.234F);
      // Captain America's summoned Avengers: human-like hero NPCs on their own geo rigs.
      add("viltrumitecore:avenger", Kind.HUMAN, Jaw.GENERIC, 1.5F, 0.25F);
      add("minecraft:iron_golem", Kind.GOLEM, Jaw.GOLEM, 2.0625F, 0.47F);
   }

   private FinalPunchTargets() {
   }

   private static void add(String typeId, Kind kind, Jaw jaw, float headHeight, float faceDepth) {
      REGISTRY.put(typeId, new Entry(typeId, kind, jaw, headHeight, faceDepth));
   }

   /** The registry entry of an entity type id ("minecraft:iron_golem"), null = not a valid target. */
   @Nullable
   public static Entry of(@Nullable String typeId) {
      return typeId == null ? null : REGISTRY.get(typeId);
   }

   @Nullable
   public static Entry of(@Nullable Entity entity) {
      if (entity == null) return null;
      ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      Entry entry = key == null ? null : of(key.toString());
      if (entry != null && entity instanceof CitizenEntity citizen) {
         // Living City 2.0 citizens are drawn at their own height and build (CitizenRenderer.scale), from the
         // synced look seed, so server and every client agree: the jaw and the fist line follow the real head.
         CitizenLook look = citizen.look();
         return sized(entry, look.height(), look.width());
      }
      return entry;
   }

   /** Pure: an entry for a body drawn {@code height} times taller and {@code width} times wider than the standard one. */
   public static Entry sized(Entry entry, float height, float width) {
      if (!(height > 0.0F) || !(width > 0.0F) || height == 1.0F && width == 1.0F) return entry;
      return new Entry(entry.typeId(), entry.kind(), entry.jaw(), entry.headHeight() * height, entry.faceDepth() * width);
   }

   public static List<Entry> all() {
      return Collections.unmodifiableList(List.copyOf(REGISTRY.values()));
   }

   /** One candidate of the look pick (pure). */
   public record Candidate(int id, Vec3 offset, boolean valid, boolean lineOfSight) {
   }

   /**
    * Pure look pick: among valid candidates in line of sight within {@code radius} and the look
    * cone, the one closest to the look ray (angle), ties by distance. −1 = none.
    */
   public static int pick(Vec3 look, List<Candidate> candidates, double radius, double coneDegrees) {
      Vec3 dir = look.lengthSqr() < 1.0E-8 ? Vec3.ZERO : look.normalize();
      double cos = Math.cos(Math.toRadians(coneDegrees));
      int best = -1;
      double bestScore = Double.MAX_VALUE;
      for (Candidate c : candidates) {
         double dist = c.offset().length();
         if (!c.valid() || !c.lineOfSight() || dist > radius || dist < 1.0E-4) continue;
         double dot = c.offset().scale(1.0 / dist).dot(dir);
         if (dot < cos) continue;
         // angle dominates; distance only breaks near-ties (1 block ≈ 0.4°)
         double score = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot)))) + dist * 0.4;
         if (score < bestScore) {
            bestScore = score;
            best = c.id();
         }
      }
      return best;
   }

   /** Pure: does a held target's anchor stay within the cone/radius grace of a client hint (server re-check). */
   public static boolean withinGrace(Vec3 look, Vec3 offset, double radius, double coneDegrees) {
      double dist = offset.length();
      if (dist > radius + 2.0 || dist < 1.0E-4) return false;
      Vec3 dir = look.lengthSqr() < 1.0E-8 ? Vec3.ZERO : look.normalize();
      return offset.scale(1.0 / dist).dot(dir) >= Math.cos(Math.toRadians(coneDegrees + 12.0));
   }
}
