package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomSounds;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.VenomDriveC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.VenomPossessS2CPacket;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.RegistryObject;

/**
 * The client side of the host takeover (VenomPossession on the server). Three roles:
 * <ul>
 * <li><b>The possessor</b> sees through the host (camera entity = the host, his own look drives the angles at once),
 * his own body lies still as a husk, and every tick his keys go to the server as a {@link VenomDriveC2SPacket}:
 * movement, jump / sneak / sprint, the attack and use edges and, on a Spider host, the held ability (keys 1-4).</li>
 * <li><b>The host</b> moves by the relayed DRIVE packet (his own keys and mouse do nothing); attack / use / jump
 * presses are resistance presses instead; the Falbiks key mappings and the drop / inventory keys are held off. He
 * sees through the symbiote (VenomHostFx: the narrow breathing slit, glimpses, flashes, crawling veins, blur and a
 * violet grade on the shared post shader, never plain black), hears the world muffled and lower, and hears its
 * heartbeat, breath and whispers.</li>
 * <li><b>Everybody</b> gets the coat progress of a ridden host for VenomCoatLayer and the hunched gait.</li>
 * </ul>
 * Anything unexpected (the level unloads, the host vanishes, the connection drops) restores the camera and the
 * inputs at once. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class VenomPossessionClient {
   static final int ENGULF = 0, CONTROL = 1, ENDING = 2;
   /** Coat recedes over this many ticks after the end. */
   static final int END_TICKS = 12;
   /** The host's client keeps a DRIVE input this long, then stops (packet loss never leaves him running). */
   static final int DRIVE_STALE = 6;
   static final int RESIST_GAP = 3;

   static final class Session {
      final int hostId;
      int possessorId;
      int phase = ENGULF;
      long start;
      long controlStart;
      long endStart;
      int remaining;
      int max = 1;
      int resist;
      int bits;
      int reason;
      /** Client tick of the last server word about this session (the watchdog, VenomHostFx#stale). */
      long heard = ticks;
      final int seed;

      Session(int hostId, long now) {
         this.hostId = hostId;
         this.start = now;
         this.seed = (int)(hostId * 31L + now) & 0x7fff;
      }

      boolean spider() {
         return (this.bits & VenomPossessS2CPacket.SPIDER) != 0;
      }
   }

   private static final Map<Integer, Session> BY_HOST = new HashMap<>();
   /** Latest DRIVE for the local host. */
   private static float driveForward, driveStrafe, driveYaw, drivePitch;
   private static int driveBits;
   private static long driveAt = -100;
   private static boolean cameraTaken;
   private static int attackEdges, useEdges;
   private static long lastResist = -100;
   private static long ticks;
   private static boolean wasJump;
   static float maxRemaining = 1;

   private VenomPossessionClient() {
   }

   // --- packets ------------------------------------------------------------------------------------------

   public static void receive(VenomPossessS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      long now = mc.level.getGameTime();
      switch (p.op()) {
         case VenomPossessS2CPacket.ENGULF -> {
            Session s = new Session(p.hostId(), now);
            s.possessorId = p.possessorId();
            s.bits = p.bits();
            BY_HOST.put(p.hostId(), s);
            s.heard = ticks;
         }
         case VenomPossessS2CPacket.CONTROL, VenomPossessS2CPacket.STATUS -> {
            Session s = BY_HOST.computeIfAbsent(p.hostId(), id -> {
               Session fresh = new Session(id, now - VenomMove.ENGULF.contact);
               fresh.controlStart = now;
               return fresh;
            });
            if (s.phase != CONTROL) {
               s.phase = CONTROL;
               s.controlStart = now;
            }
            s.heard = ticks;
            s.possessorId = p.possessorId();
            s.remaining = p.remaining();
            s.max = Math.max(1, p.max());
            s.resist = p.resist();
            s.bits = p.bits();
            if (mc.player != null && p.possessorId() == mc.player.getId()) {
               maxRemaining = s.max;
            }
         }
         case VenomPossessS2CPacket.END -> {
            Session s = BY_HOST.get(p.hostId());
            if (s != null && s.phase != ENDING) {
               s.phase = ENDING;
               s.endStart = now;
               s.reason = p.reason();
            }
            if (mc.player != null && (p.possessorId() == mc.player.getId() || p.hostId() == mc.player.getId())) {
               restore(mc);
            }
         }
         case VenomPossessS2CPacket.DRIVE -> {
            driveForward = p.forward();
            driveStrafe = p.strafe();
            driveYaw = p.yaw();
            drivePitch = p.pitch();
            driveBits = p.bits();
            driveAt = now;
            Session s = BY_HOST.get(p.hostId());
            if (s != null) {
               s.heard = ticks;
               s.remaining = p.remaining();
               s.resist = p.resist();
               s.bits = p.bits() & (VenomPossessS2CPacket.SPIDER | VenomPossessS2CPacket.BOSS) | s.bits & ~(VenomPossessS2CPacket.SPIDER | VenomPossessS2CPacket.BOSS);
            }
         }
         default -> {
         }
      }
   }

   // --- queries ------------------------------------------------------------------------------------------

   @Nullable
   static Session ofHost(int id) {
      return BY_HOST.get(id);
   }

   @Nullable
   static Session ofPossessor(int id) {
      for (Session s : BY_HOST.values()) {
         if (s.possessorId == id && s.phase != ENDING) {
            return s;
         }
      }
      return null;
   }

   /** The symbiote's coat over a ridden host, 0-1 (engulf spreads it, the end strips it). */
   public static float coatOf(Entity e, float partial) {
      Session s = BY_HOST.get(e.getId());
      if (s == null || e.level() == null) {
         return 0;
      }
      double now = e.level().getGameTime() + partial;
      return switch (s.phase) {
         case ENGULF -> (float)Mth.clamp((now - s.start) / VenomMove.ENGULF.contact, 0, 1);
         case CONTROL -> 1.0F;
         default -> (float)Mth.clamp(1.0 - (now - s.endStart) / END_TICKS, 0, 1);
      };
   }

   /** A ridden host walks hunched with the symbiote's gait (0-1 blend). */
   public static float gaitOf(Entity e, float partial) {
      Session s = BY_HOST.get(e.getId());
      return s == null ? 0 : s.phase == CONTROL ? 1 : s.phase == ENGULF ? coatOf(e, partial) * 0.6F : coatOf(e, partial);
   }

   static boolean localHost() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      }
      Session s = BY_HOST.get(mc.player.getId());
      return s != null && s.phase == CONTROL;
   }

   /** The local player is being engulfed or ridden. */
   static boolean localTaken() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      }
      Session s = BY_HOST.get(mc.player.getId());
      return s != null && s.phase != ENDING;
   }

   @Nullable
   static Session localSession() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? null : BY_HOST.get(mc.player.getId());
   }

   @Nullable
   static Session localPossession() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return null;
      }
      Session s = ofPossessor(mc.player.getId());
      return s != null && s.phase == CONTROL ? s : null;
   }

   /** The host's mouse does not turn him (TimeFreezeMouseMixin asks). */
   public static boolean locksMouse() {
      return localHost();
   }

   // --- ticks --------------------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (!BY_HOST.isEmpty() || cameraTaken) {
            BY_HOST.clear();
            cameraTaken = false;
         }
         return;
      }
      ticks++;
      long now = mc.level.getGameTime();
      for (Session s : BY_HOST.values()) {
         if (s.phase != ENDING && VenomHostFx.stale(s.phase == ENGULF, ticks - s.heard)) {
            s.phase = ENDING;
            s.endStart = now;
         }
      }
      BY_HOST.values().removeIf(s -> s.phase == ENDING && (now - s.endStart > END_TICKS || now < s.endStart)
         || s.phase != ENDING && mc.level.getEntity(s.hostId) == null && Math.abs(now - s.start) > 40);
      LocalPlayer me = mc.player;
      Session mine = localPossession();
      if (mine != null) {
         possessorTick(mc, me, mine);
      } else if (cameraTaken) {
         restore(mc);
      }
      Session self = BY_HOST.get(me.getId());
      if (self != null && self.phase == CONTROL) {
         hostTick(mc, me, self, now);
      } else {
         wasJump = mc.options.keyJump.isDown();
      }
      attackEdges = 0;
      useEdges = 0;
   }

   private static void possessorTick(Minecraft mc, LocalPlayer me, Session s) {
      Entity host = mc.level.getEntity(s.hostId);
      if (host instanceof LivingEntity && host.isAlive()) {
         if (mc.getCameraEntity() != host) {
            if (!cameraTaken) {
               me.setYRot(host.getYRot());
               me.setXRot(host.getXRot());
            }
            mc.setCameraEntity(host);
            cameraTaken = true;
         }
      } else if (cameraTaken) {
         restore(mc);
      }
      float forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
      float strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
      int bits = 0;
      if (mc.options.keyJump.isDown()) bits |= VenomDriveC2SPacket.JUMP;
      if (mc.options.keyShift.isDown()) bits |= VenomDriveC2SPacket.SNEAK;
      if (mc.options.keySprint.isDown()) bits |= VenomDriveC2SPacket.SPRINT;
      if (attackEdges > 0) bits |= VenomDriveC2SPacket.ATTACK;
      if (useEdges > 0) bits |= VenomDriveC2SPacket.USE;
      if (mc.options.keyAttack.isDown()) bits |= VenomDriveC2SPacket.ATTACK_HELD;
      int ability = 0;
      if (s.spider()) {
         KeyMapping[] keys = {AbilityInputManager.abilityKey1, AbilityInputManager.abilityKey2, AbilityInputManager.abilityKey3,
            AbilityInputManager.abilityKey4};
         for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null && keys[i].isDown()) {
               ability = i + 1;
               break;
            }
         }
      }
      CoreMessages.sendToServer(new VenomDriveC2SPacket(false, forward, strafe, Mth.wrapDegrees(me.getYRot()), me.getXRot(), bits, ability));
   }

   private static void hostTick(Minecraft mc, LocalPlayer me, Session s, long now) {
      if (now - driveAt <= DRIVE_STALE) {
         me.setYRot(driveYaw);
         me.setXRot(drivePitch);
         me.setYHeadRot(driveYaw);
      }
      // resistance: attack / use / jump presses (the jump key no longer jumps: the symbiote has the legs)
      boolean jump = mc.options.keyJump.isDown();
      int presses = attackEdges + useEdges + (jump && !wasJump ? 1 : 0);
      wasJump = jump;
      if (presses > 0 && now - lastResist >= RESIST_GAP) {
         lastResist = now;
         CoreMessages.sendToServer(VenomDriveC2SPacket.resistPress());
         VenomHostHud.struggle();
      }
      // the keys that are not his any more
      while (mc.options.keyDrop.consumeClick()) {
         // the item stays in the hand (the server refuses the toss too)
      }
      while (mc.options.keyInventory.consumeClick()) {
         // no inventory while ridden
      }
      for (KeyMapping k : mc.options.keyMappings) {
         if ("key.categories.falbiksheroes".equals(k.getCategory())) {
            while (k.consumeClick()) {
               // the Spider's own keys go nowhere; the possessor drives them through the adapter
            }
            k.setDown(false);
         }
      }
      // breathing, the racing heartbeat, whispers
      float resist = s.resist / (float)dev.baranhan.viltrumitecore.hero.venom.VenomRules.RESIST_TO_BREAK;
      long t = now - s.controlStart;
      if (t % VenomHostFx.heartbeatGap(resist) == 0) {
         local(mc, VenomSounds.HEARTBEAT, 0.9F, 0.95F + 0.15F * resist);
      }
      if (t % 46 == 12) {
         local(mc, VenomSounds.BREATH, 0.7F, 0.9F + 0.2F * me.getRandom().nextFloat());
      }
      if (t % 70 == 35 + s.seed % 20) {
         local(mc, VenomSounds.WHISPER, 0.8F, 0.85F + 0.2F * me.getRandom().nextFloat());
      }
   }

   static void local(Minecraft mc, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      if (sound.isPresent()) {
         mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), pitch, volume));
      }
   }

   private static void restore(Minecraft mc) {
      if (cameraTaken && mc.player != null) {
         mc.setCameraEntity(mc.player);
      }
      cameraTaken = false;
   }

   // --- input --------------------------------------------------------------------------------------------

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
      if (localPossession() == null && !localTaken()) {
         return;
      }
      if (event.isAttack()) {
         attackEdges++;
      } else if (event.isUseItem()) {
         useEdges++;
      }
      event.setCanceled(true);
      event.setSwingHand(false);
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onMovement(MovementInputUpdateEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || event.getEntity() != mc.player) {
         return;
      }
      Input in = event.getInput();
      if (localPossession() != null) {
         // the husk lies where it fell
         zero(in);
         return;
      }
      Session s = localSession();
      if (s == null || s.phase == ENDING) {
         return;
      }
      if (s.phase == ENGULF || mc.level.getGameTime() - driveAt > DRIVE_STALE) {
         zero(in);
         return;
      }
      in.forwardImpulse = Mth.clamp(driveForward, -1, 1);
      in.leftImpulse = Mth.clamp(driveStrafe, -1, 1);
      in.up = driveForward > 0.1F;
      in.down = driveForward < -0.1F;
      in.left = driveStrafe > 0.1F;
      in.right = driveStrafe < -0.1F;
      in.jumping = (driveBits & VenomPossessS2CPacket.JUMP) != 0;
      in.shiftKeyDown = (driveBits & VenomPossessS2CPacket.SNEAK) != 0;
      if (in.shiftKeyDown) {
         in.forwardImpulse *= 0.3F;
         in.leftImpulse *= 0.3F;
      }
      mc.player.setSprinting((driveBits & VenomPossessS2CPacket.SPRINT) != 0 && driveForward > 0.1F && !in.shiftKeyDown);
   }

   private static void zero(Input in) {
      in.forwardImpulse = 0;
      in.leftImpulse = 0;
      in.up = in.down = in.left = in.right = false;
      in.jumping = false;
      in.shiftKeyDown = false;
   }

   /** The possessor looks with his own mouse at once (the host's synced rotation follows a moment later). */
   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      if (cameraTaken && mc.player != null && mc.getCameraEntity() != mc.player) {
         float partial = (float)event.getPartialTick();
         event.setYaw(mc.player.getViewYRot(partial) + (mc.options.getCameraType().isMirrored() ? 180.0F : 0.0F));
         event.setPitch(mc.player.getViewXRot(partial) * (mc.options.getCameraType().isMirrored() ? -1.0F : 1.0F));
      }
   }

   /** Neither the possessor nor the ridden host has hands of his own in first person. */
   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent event) {
      if (cameraTaken || localHost()) {
         event.setCanceled(true);
      }
   }

   /** The host hears the world through the symbiote: quieter and lower; its own voice stays clear. */
   @SubscribeEvent
   public static void onPlaySound(PlaySoundEvent event) {
      SoundInstance sound = event.getSound();
      if (sound == null || !localTaken() || sound instanceof TickableSoundInstance || sound.getSource() == SoundSource.MASTER
         || sound.getSource() == SoundSource.MUSIC || sound.getLocation().getPath().startsWith("venom.")) {
         return;
      }
      event.setSound(new DistortedSound(sound, 0.42F, 0.8F));
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      BY_HOST.clear();
      cameraTaken = false;
      driveAt = -100;
   }

   // --- the shader and the HUD ---------------------------------------------------------------------------

   /** Post-shader inputs for the local view: {intensity, veins, flash, slit} (all 0 when nothing applies). */
   public static float[] post(float partial) {
      Minecraft mc = Minecraft.getInstance();
      float[] out = new float[4];
      if (mc.player == null || mc.level == null) {
         return out;
      }
      Session s = BY_HOST.get(mc.player.getId());
      if (s != null) {
         double now = mc.level.getGameTime() + partial;
         double seconds = (now - s.controlStart) / 20.0;
         float resist = s.resist / (float)dev.baranhan.viltrumitecore.hero.venom.VenomRules.RESIST_TO_BREAK;
         switch (s.phase) {
            case ENGULF -> {
               float e = coatOf(mc.player, partial);
               out[0] = e * 0.8F;
               out[1] = VenomHostFx.veins(e, false, 0, seconds);
               out[2] = e > 0.85F ? 1.0F : 0.0F;
               out[3] = 1.0F - e * (1.0F - VenomHostFx.MIN_OPEN);
            }
            case CONTROL -> {
               out[0] = 1.0F;
               out[1] = VenomHostFx.veins(1, true, 0, seconds);
               out[2] = VenomHostFx.flash(seconds, s.seed);
               out[3] = VenomHostFx.slit(seconds, resist, s.seed);
            }
            default -> {
               float r = (float)Mth.clamp((now - s.endStart) / END_TICKS, 0, 1);
               out[0] = 1.0F - r;
               out[1] = VenomHostFx.veins(1, true, r, seconds);
               out[3] = VenomHostFx.MIN_OPEN + (1.0F - VenomHostFx.MIN_OPEN) * r;
            }
         }
         return out;
      }
      // Venom himself: veins crawl over the edges of his view while the symbiote takes him (first person)
      var snap = VenomView.of(mc.player);
      if (snap != null && VenomView.move(snap) == VenomMove.TRANSFORM && mc.options.getCameraType().isFirstPerson()) {
         double t = VenomPose.actionTicks(mc.player, snap, partial);
         out[1] = (float)Mth.clamp(t / 22.0, 0, 1) * (float)Mth.clamp((46 - t) / 8.0, 0, 1) * 0.7F;
         out[3] = 1.0F;
      }
      return out;
   }

   /** QA log line (SubtitlePlaytest {@code venomstate}): every Venom, takeover and boss this client draws. */
   public static String qaSummary() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return "no level";
      }
      StringBuilder b = new StringBuilder();
      for (var p : mc.level.players()) {
         var snap = VenomView.of(p);
         if (snap != null) {
            VenomMove m = VenomView.move(snap);
            b.append(String.format(java.util.Locale.ROOT, "[venom %s form=%b move=%s hunger=%.2f h=%.2f rage=%b shell=%b wall=%b crawl=%b recoil=%b possessing=%b spider=%b] ",
               p.getGameProfile().getName(), VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.FORM), m == null ? "-" : m.name(),
               VenomView.hunger(snap), p.getBbHeight(), VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.RAGE),
               VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.SHELL), VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.WALL),
               VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.CRAWL), VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.RECOIL),
               VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.POSSESSING), VenomView.flag(snap, dev.baranhan.viltrumitecore.hero.venom.VenomFlags.SPIDER_NEAR)));
         }
      }
      for (Session s : BY_HOST.values()) {
         b.append(String.format(java.util.Locale.ROOT, "[session host=%d possessor=%d phase=%d remaining=%d/%d resist=%d bits=%d reason=%d] ",
            s.hostId, s.possessorId, s.phase, s.remaining, s.max, s.resist, s.bits, s.reason));
      }
      float[] post = post(0);
      b.append(String.format(java.util.Locale.ROOT, "[local cameraTaken=%b host=%b post=%.2f,%.2f,%.2f,%.2f clinging=%b] ", cameraTaken, localHost(),
         post[0], post[1], post[2], post[3], VenomCrawlClient.clinging()));
      for (var e : mc.level.entitiesForRendering()) {
         if (e instanceof dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity boss) {
            b.append(String.format(java.util.Locale.ROOT, "[boss id=%d hp=%.1f phase=%d move=%s h=%.2f dist=%.1f] ", boss.getId(), boss.getHealth(),
               boss.phaseId(), boss.move() == null ? "-" : boss.move().name(), boss.getBbHeight(), boss.distanceTo(mc.player)));
         }
      }
      return b.toString().trim();
   }

   static float remainingFraction(Session s) {
      return Mth.clamp(s.remaining / (float)Math.max(1, s.max), 0, 1);
   }

   static long ticks() {
      return ticks;
   }
}
