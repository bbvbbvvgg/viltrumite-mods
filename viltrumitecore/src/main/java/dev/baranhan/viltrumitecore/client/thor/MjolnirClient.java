package dev.baranhan.viltrumitecore.client.thor;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;
import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.under;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirModule;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPhase;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirTuning;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Client side of the world hammer: the A clips, the flight sound, who lifts / calls it (poses). */
public final class MjolnirClient {
   private MjolnirClient() {
   }

   public static void init() {
      clips().forEach(ThorClips::register);
   }

   /** Third-person clips of the A moves; each ends at its server length (MjolnirModule.DEFINITIONS). */
   public static Map<ThorMove, ThorClips.Clip> clips() {
      Map<ThorMove, ThorClips.Clip> out = new EnumMap<>(ThorMove.class);
      int windup = MjolnirTuning.THROW_WINDUP;
      int hold = windup + MjolnirTuning.THROW_HOLD_MAX;
      int end = length(ThorMove.HAMMER_THROW);
      int release = hold + 3;
      // Arm back over the shoulder, held while charging, then the throw forward and the follow-through.
      out.put(ThorMove.HAMMER_THROW, new ThorClips.Clip(new float[][]{
         under(0), key(windup, -160.0F, 10.0F, 14.0F), key(hold, -168.0F, 12.0F, 16.0F), key(release, -55.0F, -8.0F, 0.0F), under(end)
      }, new float[][]{
         under(0), key(windup, -55.0F, 15.0F, -8.0F), key(hold, -58.0F, 15.0F, -8.0F), key(release, -20.0F, 0.0F, 0.0F), under(end)
      }, new float[][]{
         under(0), key(windup, 0.0F, 22.0F, 0.0F), key(hold, 0.0F, 26.0F, 0.0F), key(release, 8.0F, -18.0F, 0.0F), under(end)
      }));
      // RECALL is an overlay (never on the timeline): an empty clip keeps the authored-move invariant.
      out.put(ThorMove.RECALL, new ThorClips.Clip(new float[][]{under(0), under(length(ThorMove.RECALL))}, null, null));
      // Y: the arm stretched to the flying hammer while pulled.
      ThorMoveDefinition pull = MjolnirModule.DEFINITIONS.get(ThorMove.HAMMER_PULL);
      int reach = pull.intro();
      int letGo = pull.intro() + pull.main();
      out.put(ThorMove.HAMMER_PULL, new ThorClips.Clip(new float[][]{
         under(0), key(reach, -165.0F, 0.0F, 6.0F), key(letGo, -165.0F, 0.0F, 6.0F), under(length(ThorMove.HAMMER_PULL))
      }, null, new float[][]{under(0), key(reach, -12.0F, 0.0F, 0.0F), key(letGo, -12.0F, 0.0F, 0.0F), under(length(ThorMove.HAMMER_PULL))}));
      return out;
   }

   private static int length(ThorMove move) {
      return MjolnirModule.DEFINITIONS.get(move).length();
   }

   /** The world hammer near a player, or null. */
   @Nullable
   static MjolnirEntity near(Player player, double radius) {
      AABB box = player.getBoundingBox().inflate(radius);
      for (MjolnirEntity hammer : player.level().getEntitiesOfClass(MjolnirEntity.class, box)) {
         return hammer;
      }

      return null;
   }

   /** This player tries to lift the hammer right now (synced lifter ids). */
   public static boolean lifting(Player player) {
      MjolnirEntity hammer = near(player, 6.0);
      if (hammer == null) {
         return false;
      }

      for (int id : hammer.lifters()) {
         if (id == player.getId()) {
            return true;
         }
      }

      return false;
   }

   /** The hammer this Thor called back and that is on its way (for the reaching arm), or null. */
   @Nullable
   public static MjolnirEntity calledBy(Player player) {
      MjolnirEntity hammer = near(player, 96.0);
      return hammer != null && hammer.phase() == MjolnirPhase.RETURNING && player.getUUID().equals(hammer.owner()) ? hammer : null;
   }

   /** Called by MjolnirEntity on its first client tick. */
   public static void flightSound(MjolnirEntity hammer) {
      Minecraft.getInstance().getSoundManager().play(new FlightSound(hammer));
   }

   /**
    * №162 / §5.2: positional sound bound to the real hammer: distance
    * attenuation only (vanilla never muffles by walls), low far hum turning
    * into a rising whistle as it nears and speeds up.
    */
   static final class FlightSound extends AbstractTickableSoundInstance {
      private final MjolnirEntity hammer;

      FlightSound(MjolnirEntity hammer) {
         super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.hammer = hammer;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.attenuation = SoundInstance.Attenuation.LINEAR;
         this.x = hammer.getX();
         this.y = hammer.getY();
         this.z = hammer.getZ();
      }

      @Override
      public void tick() {
         if (this.hammer.isRemoved()) {
            this.stop();
            return;
         }

         this.x = this.hammer.getX();
         this.y = this.hammer.getY();
         this.z = this.hammer.getZ();
         MjolnirPhase phase = this.hammer.phase();
         boolean flying = phase == MjolnirPhase.THROWN || phase == MjolnirPhase.RETURNING || phase == MjolnirPhase.DEATH_IMPACT
            || phase == MjolnirPhase.WORLD_FALLING;
         float speed = (float)this.hammer.getDeltaMovement().length();
         // Volume above 1 widens the hearing range (LINEAR: 16 blocks per unit); loudness is capped at 1.
         this.volume = flying ? Mth.clamp(speed / 2.0F, 0.2F, 1.0F) * 4.0F : 0.0F;
         this.pitch = Mth.clamp(0.45F + speed * 0.18F, 0.45F, 1.6F);
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
