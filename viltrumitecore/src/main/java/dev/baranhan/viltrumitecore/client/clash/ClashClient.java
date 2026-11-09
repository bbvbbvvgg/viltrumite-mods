package dev.baranhan.viltrumitecore.client.clash;

import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.hero.combat.clash.ClashSounds;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ClashInputC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.ClashStateS2CPacket;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the beam struggle: the views from {@link ClashStateS2CPacket}, the spray at the clash point, the
 * hum loop, beams of sources that do not draw themselves, and the mash input (attack key: the normal swing is
 * cancelled while the local player struggles). Gameplay is all on the server; this only shows and asks.
 */
@OnlyIn(Dist.CLIENT)
public final class ClashClient {
   /** A view not refreshed for this many client ticks is dropped (lost final packet, chunk out of range). */
   static final int STALE_TICKS = 10;
   /** Pulses sent per client tick at most (the server caps the window anyway). */
   static final int MAX_PULSES_PER_TICK = 2;
   private static final Map<Integer, View> VIEWS = new LinkedHashMap<>();
   private static long clientTicks;
   private static long lastPulseTick = -1L;
   private static int pulsesThisTick;
   @Nullable
   private static Hum hum;

   private ClashClient() {
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.register(ClashClient.class);
      MinecraftForge.EVENT_BUS.register(ClashHud.class);
      ClashFxRecipes.init();
   }

   /** One struggle as last received. */
   static final class View {
      ClashStateS2CPacket last;
      long receivedAt;
      double shown;

      View(ClashStateS2CPacket packet) {
         this.last = packet;
         this.receivedAt = clientTicks;
         this.shown = packet.point;
      }

      Vec3 point() {
         return this.last.originA.add(this.last.originB.subtract(this.last.originA).scale(this.shown));
      }

      int age() {
         return this.last.age + (int)Math.max(0L, clientTicks - this.receivedAt);
      }

      boolean involves(int entityId) {
         return entityId >= 0 && (this.last.entityA == entityId || this.last.entityB == entityId);
      }
   }

   public static void handle(ClashStateS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      int localId = mc.player == null ? -1 : mc.player.getId();
      if (!packet.running()) {
         VIEWS.remove(packet.id);
         if (localId >= 0 && (packet.entityA == localId || packet.entityB == localId)) {
            ClashHud.result(packet, packet.entityA == localId);
         }

         return;
      }

      View view = VIEWS.get(packet.id);
      if (view == null) {
         VIEWS.put(packet.id, new View(packet));
      } else {
         view.last = packet;
         view.receivedAt = clientTicks;
      }
   }

   /** Where the owner's beam ends because it is locked in a struggle (renderers clip to it), else null. */
   @Nullable
   public static Vec3 clashPoint(@Nullable Entity owner) {
      if (owner == null) {
         return null;
      }

      for (View view : VIEWS.values()) {
         if (view.involves(owner.getId())) {
            return view.point();
         }
      }

      return null;
   }

   /** The struggle of the local player, else null. */
   @Nullable
   static View local() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return null;
      }

      int id = mc.player.getId();
      for (View view : VIEWS.values()) {
         if (view.involves(id)) {
            return view;
         }
      }

      return null;
   }

   static Collection<View> views() {
      return VIEWS.values();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null) {
         VIEWS.clear();
         hum = null;
         return;
      }

      if (mc.isPaused()) {
         return;
      }

      clientTicks++;
      VIEWS.values().removeIf(view -> clientTicks - view.receivedAt > STALE_TICKS);
      for (View view : VIEWS.values()) {
         view.shown += (view.last.point - view.shown) * 0.5;
         spray(level, view);
      }

      if (!VIEWS.isEmpty() && (hum == null || hum.isStopped())) {
         hum = new Hum();
         mc.getSoundManager().play(hum);
      }
   }

   private static void spray(ClientLevel level, View view) {
      ClashStateS2CPacket p = view.last;
      Vec3 point = view.point();
      RandomSource random = level.getRandom();
      BeamStyle styleA = BeamStyle.of(p.beamA);
      BeamStyle styleB = BeamStyle.of(p.beamB);
      if (!styleA.selfDrawn()) {
         beam(level, p.originA, point, styleA, random);
      }

      if (!styleB.selfDrawn()) {
         beam(level, p.originB, point, styleB, random);
      }

      boolean surgeA = p.flag(ClashStateS2CPacket.FLAG_SURGE_A);
      boolean surgeB = p.flag(ClashStateS2CPacket.FLAG_SURGE_B);
      int sparks = 3 + (surgeA ? 3 : 0) + (surgeB ? 3 : 0);
      for (int i = 0; i < sparks; i++) {
         level.addParticle(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8,
            (random.nextDouble() - 0.5) * 0.8);
      }

      side(level, point, p.originA, styleA, random);
      side(level, point, p.originB, styleB, random);
      if (clientTicks % 3L == 0L) {
         level.addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z, (random.nextDouble() - 0.5) * 0.2, random.nextDouble() * 0.15,
            (random.nextDouble() - 0.5) * 0.2);
      }

      if (view.age() % 8 == 0) {
         level.addParticle(ParticleTypes.FLASH, point.x, point.y, point.z, 0.0, 0.0, 0.0);
      }

      if (clientTicks % 2L == 0L) {
         CameraShake.addAt(point, surgeA || surgeB ? 0.12F : 0.05F, 20.0);
      }
   }

   private static void side(ClientLevel level, Vec3 point, Vec3 origin, BeamStyle style, RandomSource random) {
      Vec3 back = origin.subtract(point);
      if (back.lengthSqr() < 1.0E-6) {
         return;
      }

      Vec3 dir = back.normalize();
      DustParticleOptions dust = new DustParticleOptions(style.color(), 1.4F);
      for (int i = 0; i < 2; i++) {
         double t = 0.2 + random.nextDouble() * 0.5;
         level.addParticle(dust, point.x + dir.x * t + (random.nextDouble() - 0.5) * 0.4, point.y + dir.y * t + (random.nextDouble() - 0.5) * 0.4,
            point.z + dir.z * t + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
      }
   }

   private static void beam(ClientLevel level, Vec3 from, Vec3 to, BeamStyle style, RandomSource random) {
      Vec3 delta = to.subtract(from);
      double length = delta.length();
      if (length < 0.5) {
         return;
      }

      int steps = Math.min(40, (int)Math.ceil(length / 1.1));
      double phase = random.nextDouble();
      DustParticleOptions dust = new DustParticleOptions(style.color(), 1.2F);
      for (int i = 0; i < steps; i++) {
         double t = (i + phase) / steps;
         level.addParticle(dust, from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t, 0.0, 0.0, 0.0);
      }
   }

   @SubscribeEvent
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (event.isAttack() && local() != null) {
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void onMouse(InputEvent.MouseButton.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getAction() == GLFW.GLFW_PRESS && mc.screen == null && mc.options.keyAttack.matchesMouse(event.getButton())) {
         pulse();
      }
   }

   @SubscribeEvent
   public static void onKey(InputEvent.Key event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getAction() == GLFW.GLFW_PRESS && mc.screen == null && mc.options.keyAttack.matches(event.getKey(), event.getScanCode())) {
         pulse();
      }
   }

   private static void pulse() {
      View view = local();
      if (view == null) {
         return;
      }

      if (lastPulseTick != clientTicks) {
         lastPulseTick = clientTicks;
         pulsesThisTick = 0;
      }

      if (pulsesThisTick >= MAX_PULSES_PER_TICK) {
         return;
      }

      pulsesThisTick++;
      CoreMessages.sendToServer(new ClashInputC2SPacket(view.last.id));
      ClashHud.pressed(view.age());
   }

   /** Colour of a beam id at the point and in the HUD; {@code selfDrawn} beams render themselves (clipped). */
   public record BeamStyle(Vector3f color, boolean selfDrawn) {
      public static BeamStyle of(@Nullable String id) {
         return switch (id == null ? "" : id) {
            case "homelander:eye_lasers" -> new BeamStyle(new Vector3f(1.0F, 0.12F, 0.08F), true);
            case "ironman:unibeam" -> new BeamStyle(new Vector3f(0.65F, 0.92F, 1.0F), true);
            case "ironman:repulsor" -> new BeamStyle(new Vector3f(0.75F, 0.95F, 1.0F), false);
            case "thor:lightning" -> new BeamStyle(new Vector3f(0.72F, 0.82F, 1.0F), false);
            case "clash:dummy" -> new BeamStyle(new Vector3f(1.0F, 0.78F, 0.15F), false);
            default -> new BeamStyle(new Vector3f(1.0F, 0.95F, 0.8F), false);
         };
      }

      public int argb() {
         int r = Math.round(Math.max(0.0F, Math.min(1.0F, this.color.x())) * 255.0F);
         int g = Math.round(Math.max(0.0F, Math.min(1.0F, this.color.y())) * 255.0F);
         int b = Math.round(Math.max(0.0F, Math.min(1.0F, this.color.z())) * 255.0F);
         return 0xFF000000 | r << 16 | g << 8 | b;
      }
   }

   /** The crackling roar at the nearest clash point; fades out when no struggle is in view. */
   static final class Hum extends AbstractTickableSoundInstance {
      Hum() {
         super(ClashSounds.BEAM_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.looping = true;
         this.delay = 0;
         this.volume = 0.05F;
         this.attenuation = SoundInstance.Attenuation.LINEAR;
         View nearest = nearest();
         if (nearest != null) {
            Vec3 at = nearest.point();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
         }
      }

      @Override
      public void tick() {
         View nearest = nearest();
         if (nearest == null) {
            this.volume -= 0.2F;
            if (this.volume <= 0.0F) {
               this.stop();
            }

            return;
         }

         Vec3 at = nearest.point();
         this.x = at.x;
         this.y = at.y;
         this.z = at.z;
         boolean surge = nearest.last.flag(ClashStateS2CPacket.FLAG_SURGE_A) || nearest.last.flag(ClashStateS2CPacket.FLAG_SURGE_B);
         this.volume = Math.min(surge ? 2.2F : 1.6F, this.volume + 0.25F);
         float lean = (float)Math.min(1.0, Math.abs(nearest.shown - 0.5) * 2.0);
         this.pitch = 0.85F + 0.35F * lean + (surge ? 0.1F : 0.0F);
      }

      @Nullable
      private static View nearest() {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return null;
         }

         View best = null;
         double bestDistance = Double.MAX_VALUE;
         for (View view : VIEWS.values()) {
            double distance = view.point().distanceToSqr(mc.player.position());
            if (distance < bestDistance) {
               bestDistance = distance;
               best = view;
            }
         }

         return best;
      }
   }
}
