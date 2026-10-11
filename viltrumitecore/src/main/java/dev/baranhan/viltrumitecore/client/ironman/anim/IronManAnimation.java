package dev.baranhan.viltrumitecore.client.ironman.anim;

import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import dev.baranhan.viltrumitecore.hero.ironman.IronManVariant;
import dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.util.EnumMap;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Iron Man animation state, the RegulusAnimationManager pattern (PR 19
 * iteration 2): everything a pose needs is derived once per client tick from
 * the synced snapshot, so the third-person model and both first-person arms
 * read the same story. Repulsor shots are detected on the RECOIL edge (hand
 * from SHOT_HAND, a volley when the charge was full) and each palm then holds
 * its firing stance for {@link #STANCE_HOLD} ticks instead of dropping between
 * shots, so alternating fire never flails. Weapon strikes run on an
 * {@link ActionClock} with a client recovery tail and alternate forehand /
 * backhand; the nano forming wave, missile release and the signatures are
 * clocks too. Layer weights ease by real time per view.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class IronManAnimation {
   /** Ticks a palm stays raised after its last shot. */
   public static final int STANCE_HOLD = 30;
   /** Recovery tail of a weapon strike (ticks after the server strike). */
   public static final int STRIKE_TAIL = 6;
   /** Recovery tail of the nano forming / dissolving wave. */
   public static final int FORM_TAIL = 6;
   /** Ticks the arms stay aimed after the missile volley. */
   public static final int MISSILE_HOLD = 12;

   public enum Layer {
      STANCE_RIGHT(16.0F),
      STANCE_LEFT(16.0F),
      MISSILES(14.0F),
      UNIBEAM(12.0F),
      OVERHEAT(8.0F),
      WINDUP(12.0F),
      LUNGE(22.0F),
      GUARD(16.0F),
      STRIKE(26.0F),
      FORM(20.0F),
      SIGNATURE(14.0F),
      EQUIP(10.0F),
      GESTURE(14.0F);

      final float rate;

      Layer(float rate) {
         this.rate = rate;
      }
   }

   public enum View {
      MODEL,
      FIRST_PERSON_RIGHT,
      FIRST_PERSON_LEFT
   }

   public enum Signature {
      NONE,
      LASER,
      FIST,
      GUN,
      SLAM_JUMP,
      SLAM_DIVE
   }

   /** Per-player state, written on the client tick, read by every view. */
   public static final class State {
      boolean worn;
      boolean lastRecoil;
      int prevCharge = -1;
      int shotRight = 1000;
      int shotLeft = 1000;
      int stanceRight;
      int stanceLeft;
      boolean chargeRight;
      boolean chargeLeft;
      float charge;
      final ActionClock strike = new ActionClock();
      int strikeCount;
      int lastStrikeElapsed = -1;
      int strikeLength = 8;
      boolean backhand;
      RightTool strikeTool = RightTool.NANO_BLADE;
      final ActionClock form = new ActionClock();
      boolean dissolve;
      boolean wasForming;
      boolean missilesHeld;
      int missileFired = 1000;
      int unibeamPhase;
      boolean windup;
      float windupCharge;
      boolean lunge;
      boolean guard;
      Signature signature = Signature.NONE;
      Signature lastSignature = Signature.NONE;
      final EnumMap<View, EnumMap<Layer, float[]>> weights = new EnumMap<>(View.class);

      public boolean worn() {
         return this.worn;
      }

      /** Palm raised (charging, firing or holding the stance after a shot). */
      public boolean stance(boolean right) {
         return this.worn && (right ? this.stanceRight > 0 || this.chargeRight : this.stanceLeft > 0 || this.chargeLeft);
      }

      /** Ticks since that palm's last shot (+partialTick), large when it did not fire. */
      public float sinceShot(boolean right, float partialTick) {
         return (right ? this.shotRight : this.shotLeft) + partialTick;
      }

      /** Repulsor charge 0..1 of the palm that fires next (0 when it does not charge). */
      public float charge(boolean right) {
         return (right ? this.chargeRight : this.chargeLeft) ? this.charge : 0.0F;
      }

      public boolean missiles() {
         return this.worn && (this.missilesHeld || this.missileFired < MISSILE_HOLD);
      }

      public float sinceMissiles(float partialTick) {
         return this.missilesHeld ? 1000.0F : this.missileFired + partialTick;
      }

      public int unibeamPhase() {
         return this.worn ? this.unibeamPhase : 0;
      }

      public boolean windup() {
         return this.worn && this.windup;
      }

      public float windupCharge() {
         return this.windupCharge;
      }

      public boolean lunge() {
         return this.worn && this.lunge;
      }

      public boolean guard() {
         return this.worn && this.guard;
      }

      public boolean striking() {
         return this.worn && this.strike.isPlaying(HeroAction.PRIMARY_ATTACK.ordinal());
      }

      public float strikeTime(float partialTick) {
         return this.strike.time(partialTick);
      }

      public boolean backhand() {
         return this.backhand;
      }

      public boolean hammer() {
         return this.strikeTool == RightTool.NANO_HAMMER;
      }

      public boolean forming() {
         return this.worn && this.form.isPlaying(HeroAction.NANO_ARSENAL.ordinal());
      }

      public float formTime(float partialTick) {
         return this.form.time(partialTick);
      }

      public boolean dissolve() {
         return this.dissolve;
      }

      /** The running signature, or the last one while its weight fades. */
      public Signature signature() {
         return this.worn ? this.signature : Signature.NONE;
      }

      public Signature lastSignature() {
         return this.lastSignature;
      }

      /** Weight of a layer in one view, eased by real time (each view advances once per frame). */
      public float weight(View view, Layer layer, boolean active) {
         float[] st = this.weights.computeIfAbsent(view, v -> new EnumMap<>(Layer.class)).computeIfAbsent(layer, l -> new float[]{0.0F, Float.NaN});
         float now = dev.baranhan.viltrumitecore.client.anim.RealTime.seconds();
         float dt = Float.isNaN(st[1]) ? 0.0F : Math.min(0.1F, Math.max(0.0F, now - st[1]));
         st[1] = now;
         st[0] = Mth.lerp(1.0F - (float)Math.exp(-layer.rate * dt), st[0], active ? 1.0F : 0.0F);
         if (!active && st[0] < 0.001F) {
            st[0] = 0.0F;
         }

         return st[0];
      }
   }

   private static final WeakHashMap<LivingEntity, State> STATES = new WeakHashMap<>();
   private static ClientLevel lastLevel;

   private IronManAnimation() {
   }

   @Nullable
   public static State of(LivingEntity entity) {
      return STATES.get(entity);
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      ClientLevel level = Minecraft.getInstance().level;
      if (level != lastLevel) {
         lastLevel = level;
         STATES.clear();
      }

      if (level == null || Minecraft.getInstance().isPaused()) {
         return;
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null) {
            STATES.remove(player);
            continue;
         }

         tick(STATES.computeIfAbsent(player, p -> new State()), snapshot);
      }
   }

   /** One client tick of the state (pure on the snapshot sequence). */
   static void tick(State s, HeroPublicSnapshot snapshot) {
      int flags = snapshot.heroFlags();
      // The Hulkbuster reuses the channels for its own moves; Iron Man poses stay off inside it.
      s.worn = IronManView.worn(snapshot) && IronManFlags.get(flags, IronManFlags.Field.HULKBUSTER_PHASE) == 0;
      RightTool tool = IronManView.tool(snapshot);
      boolean rmb = s.worn && IronManView.channel(snapshot, HeroAction.SECONDARY_USE);
      int rmbElapsed = rmb ? snapshot.actionElapsed() : -1;

      // Repulsors: the RECOIL edge is a shot; a full charge before it was a volley.
      boolean recoil = s.worn && IronManFlags.is(flags, IronManFlags.Field.RECOIL);
      boolean shotRight = IronManFlags.is(flags, IronManFlags.Field.SHOT_HAND);
      s.shotRight = Math.min(1000, s.shotRight + 1);
      s.shotLeft = Math.min(1000, s.shotLeft + 1);
      s.stanceRight = Math.max(0, s.stanceRight - 1);
      s.stanceLeft = Math.max(0, s.stanceLeft - 1);
      if (recoil && !s.lastRecoil) {
         boolean volley = s.prevCharge >= IronManRules.REPULSOR_CHARGE_MAX;
         if (volley || shotRight) {
            s.shotRight = 0;
            s.stanceRight = STANCE_HOLD;
         }

         if (volley || !shotRight) {
            s.shotLeft = 0;
            s.stanceLeft = STANCE_HOLD;
         }
      }

      s.lastRecoil = recoil;
      boolean repulsorCharge = rmb && tool == RightTool.REPULSOR;
      boolean full = repulsorCharge && rmbElapsed >= IronManRules.REPULSOR_CHARGE_MAX;
      // The palm that fires next is the other one than the last shot (Repulsor.nextRight).
      s.chargeRight = repulsorCharge && (!shotRight || full);
      s.chargeLeft = repulsorCharge && (shotRight || full);
      s.charge = repulsorCharge ? Mth.clamp((rmbElapsed - IronManRules.REPULSOR_TAP_TICKS) / (float)(IronManRules.REPULSOR_CHARGE_MAX - IronManRules.REPULSOR_TAP_TICKS), 0.0F, 1.0F) : 0.0F;
      s.prevCharge = repulsorCharge ? rmbElapsed : -1;

      // Weapon strikes: a new server strike restarts the clock and flips forehand / backhand.
      boolean strike = s.worn && IronManView.channel(snapshot, HeroAction.PRIMARY_ATTACK);
      if (strike) {
         int elapsed = snapshot.actionElapsed();
         if (s.lastStrikeElapsed < 0 || elapsed < s.lastStrikeElapsed) {
            s.strikeCount++;
            s.strikeTool = tool == RightTool.NANO_HAMMER ? RightTool.NANO_HAMMER : RightTool.NANO_BLADE;
            s.backhand = s.strikeTool == RightTool.NANO_BLADE && s.strikeCount % 2 == 0;
            s.strike.reset();
         }

         s.lastStrikeElapsed = elapsed;
         s.strikeLength = snapshot.actionLength();
         s.strike.tick(HeroAction.PRIMARY_ATTACK.ordinal(), elapsed, snapshot.actionLength(), snapshot.actionLength() + STRIKE_TAIL);
      } else {
         s.lastStrikeElapsed = -1;
         s.strike.tick(-1, 0, s.strikeLength, s.strikeLength + STRIKE_TAIL);
      }

      // Nano forming / dissolving wave.
      boolean wave = s.worn && IronManView.channel(snapshot, HeroAction.NANO_ARSENAL);
      if (wave) {
         if (!s.wasForming) {
            s.dissolve = !tool.nanoWeapon();
            s.form.reset();
         }

         s.form.tick(HeroAction.NANO_ARSENAL.ordinal(), snapshot.actionElapsed(), snapshot.actionLength(), snapshot.actionLength() + FORM_TAIL);
      } else {
         s.form.tick(-1, 0, IronManRules.NANO_FORM_TICKS, IronManRules.NANO_FORM_TICKS + FORM_TAIL);
      }

      s.wasForming = wave;

      // Missiles: held, then a short aimed hold after the release.
      boolean held = s.worn && IronManView.channel(snapshot, HeroAction.MISSILES);
      if (s.missilesHeld && !held) {
         s.missileFired = 0;
      } else {
         s.missileFired = Math.min(1000, s.missileFired + 1);
      }

      s.missilesHeld = held;
      s.unibeamPhase = s.worn ? IronManView.unibeamPhase(snapshot) : 0;
      s.windup = rmb && tool == RightTool.NANO_HAMMER;
      s.windupCharge = s.windup ? IronManView.progress(snapshot, HeroAction.SECONDARY_USE, 0.0F) : 0.0F;
      s.lunge = rmb && tool == RightTool.NANO_BLADE;
      s.guard = s.worn && IronManFlags.is(flags, IronManFlags.Field.SHIELD_UP);
      s.signature = s.worn ? signature(snapshot) : Signature.NONE;
      if (s.signature != Signature.NONE) {
         s.lastSignature = s.signature;
      }
   }

   static Signature signature(HeroPublicSnapshot snapshot) {
      int flags = snapshot.heroFlags();
      if (!IronManFlags.is(flags, IronManFlags.Field.SIGNATURE_ACTIVE)) {
         return Signature.NONE;
      }

      MarkId mark = IronManVariant.mark(snapshot.variant());
      if (mark == null) {
         return Signature.NONE;
      }

      return switch (mark) {
         case MARK_7 -> Signature.LASER;
         case MARK_42 -> Signature.FIST;
         case WAR_MACHINE_MK2 -> Signature.GUN;
         case IRON_HEART_MK3 -> IronManFlags.is(flags, IronManFlags.Field.SIGNATURE_AUX) ? Signature.SLAM_DIVE : Signature.SLAM_JUMP;
         default -> Signature.NONE;
      };
   }

   /** Recoil kick 0..1 after a shot: snaps up in a tick, settles over five. */
   public static float kick(float since) {
      if (since < 0.0F || since > 6.0F) {
         return 0.0F;
      }

      if (since < 1.0F) {
         return since;
      }

      float u = 1.0F - (since - 1.0F) / 5.0F;
      return u * u;
   }
}
