package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.network.packet.RohanFxS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Client side of {@link RohanFxS2CPacket}: feeds the world renderer, particles and the local manga accents. */
final class RohanFx {
   static final DustParticleOptions RED_INK = new DustParticleOptions(new Vector3f(0.75F, 0.08F, 0.1F), 1.0F);
   static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.35F), 0.8F);

   private RohanFx() {
   }

   static void receive(RohanFxS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      LocalPlayer me = mc.player;
      if (level == null || me == null) {
         return;
      }
      Entity source = p.sourceId() >= 0 ? level.getEntity(p.sourceId()) : null;
      Entity target = p.targetId() >= 0 ? level.getEntity(p.targetId()) : null;
      Vec3 at = p.at();
      boolean mine = source == me, hit = target == me;
      switch (p.kind()) {
         case RohanFxS2CPacket.BOOK_OPEN -> {
            BookPagesRenderer.open(p.targetId(), p.sourceId(), p.ticks());
            BookPagesRenderer.sheets(at, 4, 0.7F);
            burst(level, ParticleTypes.ENCHANT, at, 12, 0.5);
            if (hit) {
               MangaFx.trigger(MangaFx.Kind.VICTIM, 1.0F);
            } else if (mine) {
               MangaFx.trigger(MangaFx.Kind.OPEN, 1.0F);
            } else if (witness(me, at, 10.0)) {
               MangaFx.trigger(MangaFx.Kind.OPEN, 0.45F);
            }
         }
         case RohanFxS2CPacket.BOOK_WRITE -> {
            BookPagesRenderer.write(p.targetId());
            burst(level, ParticleTypes.SQUID_INK, at, 3, 0.15);
            burst(level, GOLD, at, 6, 0.35);
         }
         case RohanFxS2CPacket.BOOK_CLOSE -> BookPagesRenderer.close(p.targetId());
         case RohanFxS2CPacket.PAGE_RIP -> {
            BookPagesRenderer.sheets(at, 1, 1.4F);
            burst(level, ParticleTypes.SQUID_INK, at, 4, 0.2);
         }
         case RohanFxS2CPacket.NIB -> {
            Vec3 from = hand(source, at);
            BookPagesRenderer.streak(from, at, Math.max(3, p.ticks()), 0.06F, 0.06F, 0.1F, 0.05F);
            burst(level, ParticleTypes.SQUID_INK, at, 2, 0.1);
         }
         case RohanFxS2CPacket.SKETCH -> {
            BookPagesRenderer.sheets(at, 2, 0.4F);
            burst(level, ParticleTypes.ENCHANT, at, 16, 0.6);
         }
         case RohanFxS2CPacket.MANUSCRIPT -> {
            BookPagesRenderer.sheets(at, 6, 0.9F);
            if (mine || witness(me, at, 16.0)) {
               MangaFx.trigger(MangaFx.Kind.MENACE, mine ? 0.8F : 1.0F);
            }
         }
         case RohanFxS2CPacket.COUNTER -> {
            Vec3 from = hand(source, at);
            BookPagesRenderer.streak(from, at, 5, 1.0F, 0.95F, 0.8F, 0.12F);
            BookPagesRenderer.sheets(at, 2, 0.8F);
            if (mine || hit) {
               MangaFx.trigger(MangaFx.Kind.COUNTER, 1.0F);
            }
         }
         case RohanFxS2CPacket.RESIST -> {
            BookPagesRenderer.resist(p.targetId());
            burst(level, RED_INK, at, 10, 0.4);
         }
         case RohanFxS2CPacket.REFLECT -> {
            burst(level, RED_INK, at, 14, 0.5);
            BookPagesRenderer.sheets(at, 2, 0.6F);
         }
         case RohanFxS2CPacket.SELF_EDIT -> {
            BookPagesRenderer.sheets(at, 3, 0.5F);
            if (source != null) {
               Vec3 back = source.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(-3.0);
               for (int i = -1; i <= 1; i++) {
                  Vec3 o = new Vec3(0.0, 0.4 * i, 0.0);
                  BookPagesRenderer.streak(at.add(o), at.add(o).add(back), 8, 0.95F, 0.95F, 1.0F, 0.04F);
               }
            }
         }
         case RohanFxS2CPacket.GUARDED -> burst(level, ParticleTypes.CRIT, at, 10, 0.4);
         default -> {
         }
      }
   }

   /** The local player is near and roughly facing the point. */
   static boolean witness(LocalPlayer me, Vec3 at, double range) {
      Vec3 eye = me.getEyePosition();
      Vec3 d = at.subtract(eye);
      double len = d.length();
      return len <= range && (len < 1.0 || d.scale(1.0 / len).dot(me.getLookAngle()) > 0.6);
   }

   static Vec3 hand(Entity source, Vec3 fallback) {
      if (source == null) {
         return fallback;
      }
      Vec3 tip = HeavensDoorRenderer.fingertip(source.getId());
      if (tip != null) {
         return tip;
      }
      float yaw = source instanceof LivingEntity l ? l.yBodyRot : source.getYRot();
      double rx = -Math.cos(Math.toRadians(yaw)), rz = -Math.sin(Math.toRadians(yaw));
      return source.getEyePosition().add(rx * 0.35, -0.35, rz * 0.35).add(source.getLookAngle().scale(0.4));
   }

   static void burst(ClientLevel level, ParticleOptions type, Vec3 at, int count, double spread) {
      var r = level.random;
      for (int i = 0; i < count; i++) {
         level.addParticle(type, at.x + (r.nextDouble() - 0.5) * spread, at.y + (r.nextDouble() - 0.5) * spread, at.z + (r.nextDouble() - 0.5) * spread,
            (r.nextDouble() - 0.5) * 0.08, r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.08);
      }
   }
}
