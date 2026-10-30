package dev.baranhan.viltrumitecore.client.regulus;

import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of {@link RegulusFxS2CPacket}: every Regulus world event becomes a piece of the same
 * visual language (spec §7): stopped time is drawn as motes that halt with a gold glint, hairline
 * crack stars, glass panes and sheets of stopped air, frozen fragments, the travelling heart. No
 * lightning, no fire, no smoke. The server decides what happened; this only draws it. Sounds are
 * played positionally by the server, never here.
 */
public final class RegulusFx {
   private RegulusFx() {
   }

   public static void accept(RegulusFxS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      Vec3 at = new Vec3(p.x(), p.y(), p.z());
      Vec3 dir = new Vec3(p.dx(), p.dy(), p.dz());
      Entity source = level.getEntity(p.a());
      Entity target = p.b() < 0 ? null : level.getEntity(p.b());
      boolean mine = mc.player != null && p.a() == mc.player.getId();
      boolean victim = mc.player != null && p.b() == mc.player.getId();
      switch (p.kind()) {
         case RegulusFxS2CPacket.LION_ON -> {
            RegulusVfx.ring(at, 2.4, 28, 6.0F, 40, RegulusPalette.IVORY);
            RegulusVfx.ring(at.add(0.0, -0.9, 0.0), 1.6, 20, 5.0F, 30, RegulusPalette.GOLD);
            RegulusVfx.still(at, 0.6, 10, 26, RegulusPalette.PALE_GOLD, 0.03F);
            shake(mine, at, 0.25F, 16.0);
         }
         case RegulusFxS2CPacket.LION_OFF -> {
            // the stopped air around him is released: the motes finally fall
            RegulusVfx.burst(at, 16, 0.06, 4.0F, 30, RegulusPalette.GREY, 0.03F, 6.0, Vec3.ZERO);
         }
         case RegulusFxS2CPacket.RECOIL -> {
            Vec3 normal = target == null ? dir : target.getEyePosition().subtract(at);
            RegulusVfx.pane(at, safe(normal), 0.32, 14);
            RegulusVfx.crack(at, 0.35, 16);
            RegulusVfx.burst(at, 6, 0.05, 3.0F, 16, RegulusPalette.IVORY, 0.025F, -1.0, Vec3.ZERO);
            shake(victim, at, 0.4F, 16.0);
         }
         case RegulusFxS2CPacket.PROJECTILE_STOP -> {
            Vec3 normal = target == null ? Vec3.ZERO : target.getDeltaMovement();
            RegulusVfx.pane(at, safe(normal), 0.22, 30);
            RegulusVfx.still(at, 0.12, 6, 30, RegulusPalette.GLASS, 0.025F);
         }
         case RegulusFxS2CPacket.SEIZURE -> {
            RegulusVfx.burst(at, 20, 0.09, 5.0F, 34, RegulusPalette.INK, 0.035F, 10.0, Vec3.ZERO);
            RegulusVfx.crack(at, 0.5, 30);
            shake(mine, at, 0.6F, 12.0);
         }
         case RegulusFxS2CPacket.TOUCH_HIT -> touch(source, at, dir, p.power(), p.extra(), true, mine || victim);
         case RegulusFxS2CPacket.TOUCH_MISS -> touch(source, at, dir, p.power(), p.extra(), false, mine);
         case RegulusFxS2CPacket.RUPTURE -> {
            RegulusVfx.burst(at, 18, 0.12, 5.0F, 30, RegulusPalette.HEART, 0.04F, 10.0, Vec3.ZERO);
            RegulusVfx.crack(at, 0.8, 40);
            shake(mine || victim, at, 0.9F, 20.0);
         }
         case RegulusFxS2CPacket.SAND_SHOT -> {
            RegulusVfx.sand(p.a(), at, safe(dir), Math.round(p.power()), p.extra() & 0x7FFF, p.extra() >>> 15);
            shake(mine, at, 0.15F, 8.0);
         }
         case RegulusFxS2CPacket.GRAIN_STOP -> RegulusVfx.grainStop(p.a(), p.extra(), at);
         case RegulusFxS2CPacket.GRAIN_HIT -> {
            RegulusVfx.grainStop(p.a(), p.extra(), at);
            RegulusVfx.crack(at, 0.22, 16);
            RegulusVfx.burst(at, 3, 0.04, 2.0F, 14, RegulusPalette.PALE_GOLD, 0.02F, 4.0, safe(dir).scale(0.5));
         }
         case RegulusFxS2CPacket.SEVER_SHOT -> {
            RegulusVfx.blade(at, safe(dir), p.extra() == 1);
            shake(mine, at, 0.3F, 10.0);
         }
         case RegulusFxS2CPacket.SEVER_CUT -> {
            Vec3 f = safe(dir);
            Vec3 across = p.extra() == 1 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(-f.z, 0.0, f.x).normalize();
            double half = 0.55 + Math.min(0.6, p.power() * 0.03);
            RegulusVfx.streak(at.subtract(across.scale(half)), at.add(across.scale(half)), 0.035F, 22);
            RegulusVfx.still(at, 0.25, 6, 24, RegulusPalette.GLASS, 0.022F);
            RegulusVfx.crack(at, 0.3, 20);
            shake(victim || mine, at, 0.45F, 16.0);
         }
         case RegulusFxS2CPacket.PROCESSION_RAM -> {
            Vec3 f = safe(dir);
            RegulusVfx.crack(at, 0.6, 26);
            RegulusVfx.pane(at.subtract(f.scale(0.4)), f, 0.7, 18);
            RegulusVfx.burst(at, 14, 0.18, 4.0F, 26, RegulusPalette.IVORY, 0.035F, -1.0, f);
            shake(mine || victim, at, 0.9F, 24.0);
         }
         case RegulusFxS2CPacket.BLOCK_STOP -> {
            RegulusVfx.fragments(at, p.extra());
            CameraShake.addAt(at, 0.2F, 12.0);
         }
         case RegulusFxS2CPacket.REPLY_BLOCK -> {
            Vec3 normal = target == null ? dir : target.getEyePosition().subtract(at);
            RegulusVfx.pane(at, safe(normal), 0.45, 16);
            RegulusVfx.crack(at, 0.3, 18);
            RegulusVfx.ring(at, 0.7, 12, 3.0F, 16, RegulusPalette.GOLD);
         }
         case RegulusFxS2CPacket.REPLY_STRIKE -> {
            Vec3 from = source == null ? at.subtract(safe(dir).scale(2.0)) : RegulusAnchors.rightHand(source, 0.0F);
            RegulusVfx.streak(from, at, 0.05F, 14);
            if (p.b() >= 0) {
               RegulusVfx.crack(at, 0.5, 24);
               RegulusVfx.burst(at, 10, 0.12, 3.0F, 20, RegulusPalette.IVORY, 0.03F, -1.0, safe(dir));
            } else {
               RegulusVfx.still(at, 0.2, 5, 16, RegulusPalette.IVORY, 0.025F);
            }

            shake(mine || victim, at, p.b() >= 0 ? 0.7F : 0.25F, 16.0);
         }
         case RegulusFxS2CPacket.KING_BIND -> {
            Vec3 from = source == null ? at : RegulusAnchors.heart(source, 0.0F);
            RegulusVfx.heart(from, p.b(), 18, false);
         }
         case RegulusFxS2CPacket.KING_HOP -> {
            Entity old = p.extra() < 0 ? null : level.getEntity(p.extra());
            Vec3 from = old == null ? at : old.position().add(0.0, old.getBbHeight() * 0.68, 0.0);
            RegulusVfx.heart(from, p.b(), 22, false);
         }
         case RegulusFxS2CPacket.KING_RELEASE -> RegulusVfx.heart(at, p.b(), 16, true);
         case RegulusFxS2CPacket.ULT_START -> {
            RegulusVfx.ring(at.add(0.0, 0.1, 0.0), 2.0, 24, 8.0F, 60, RegulusPalette.PALE_GOLD);
            RegulusUltScene.start(p.a(), p.b(), at);
         }
         case RegulusFxS2CPacket.ULT_SCOOP -> {
            RegulusVfx.dome(at, p.power(), p.extra());
            RegulusVfx.still(at.add(0.0, 1.0, 0.0), 2.6, 36, 70, RegulusPalette.PALE_GOLD, 0.03F);
            CameraShake.cinematicAt(at, 0.5F, 40.0);
         }
         case RegulusFxS2CPacket.ULT_WAVE -> {
            int wave = p.extra();
            RegulusVfx.ring(at, 1.4 + 0.3 * wave, 24, 4.0F, 20, RegulusPalette.GOLD);
            RegulusVfx.crack(at, 0.5 + 0.08 * wave, 20);
            CameraShake.cinematicAt(at, 0.6F + 0.15F * wave, 40.0);
            RegulusUltScene.wave(p.a(), wave);
         }
         case RegulusFxS2CPacket.ULT_FLICK -> {
            Vec3 f = safe(dir);
            RegulusVfx.endDome();
            RegulusVfx.streak(at, at.add(f.scale(8.0)), 0.06F, 20);
            RegulusVfx.crack(at, 1.6, 60);
            RegulusVfx.burst(at, 30, 0.25, 6.0F, 40, RegulusPalette.IVORY, 0.04F, 12.0, f);
            RegulusVfx.ring(at.subtract(0.0, 0.8, 0.0), 3.0, 32, 5.0F, 40, RegulusPalette.IVORY);
            CameraShake.cinematicAt(at, 2.4F, 48.0);
            RegulusUltScene.flick(p.a());
         }
         case RegulusFxS2CPacket.ULT_END -> {
            RegulusVfx.endDome();
            RegulusUltScene.end(p.a());
         }
         case RegulusFxS2CPacket.REFUSE -> {
            Vec3 look = source == null ? Vec3.ZERO : source.getLookAngle().scale(0.5);
            RegulusVfx.still(at.add(look), 0.08, 4, 12, RegulusPalette.GREY, 0.02F);
         }
         case RegulusFxS2CPacket.LAND -> {
            float fall = p.power();
            if (fall >= 3.0F) {
               double radius = Math.min(3.0, 0.6 + fall * 0.08);
               RegulusVfx.ring(at.add(0.0, 0.05, 0.0), radius, 20, 5.0F, 24, RegulusPalette.IVORY);
               RegulusVfx.crack(at.add(0.0, 0.05, 0.0), radius * 0.6, 40);
               shake(mine, at, Math.min(1.2F, fall * 0.05F), 16.0);
            }
         }
         default -> {
         }
      }
   }

   /** Greed's Touch: the crack star where the stopped hand met the body, shaped by the move. */
   private static void touch(@Nullable Entity source, Vec3 at, Vec3 dir, float damage, int moveId, boolean hit, boolean close) {
      RegulusMove move = RegulusMove.byId(moveId);
      Vec3 f = safe(dir);
      if (!hit) {
         RegulusVfx.still(at, 0.2, 5, 16, RegulusPalette.IVORY, 0.025F);
         if (move == RegulusMove.TOUCH_FLICK) {
            RegulusVfx.streak(at, at.add(f.scale(1.6)), 0.02F, 8);
         }

         return;
      }

      RegulusVfx.crack(at, 0.4 + damage * 0.03, 24);
      if (move == RegulusMove.TOUCH_PALM) {
         RegulusVfx.pane(at.subtract(f.scale(0.2)), f, 0.5, 14);
      } else if (move == RegulusMove.TOUCH_FLICK) {
         RegulusVfx.streak(at, at.add(f.scale(3.0)), 0.035F, 12);
      } else {
         Vec3 side = new Vec3(-f.z, 0.0, f.x);
         RegulusVfx.burst(at, 8, 0.1, 3.0F, 18, RegulusPalette.IVORY, 0.03F, -1.0, side.add(f).normalize());
      }

      if (close) {
         CameraShake.add(0.3F + damage * 0.04F);
      } else {
         CameraShake.addAt(at, 0.3F + damage * 0.04F, 20.0);
      }
   }

   private static void shake(boolean close, Vec3 at, float power, double range) {
      if (close) {
         CameraShake.add(power);
      } else {
         CameraShake.addAt(at, power, range);
      }
   }

   private static Vec3 safe(Vec3 v) {
      return v.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : v.normalize();
   }
}
