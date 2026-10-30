package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server-side per-player DIO state. Blood and cooldowns persist; channels never do. */
public final class DioState {
   public int blood;
   /** Debug ({@code /dio blood infinite on}): blood stays full and casts never spend it. Persists. */
   public boolean infiniteBlood;
   public final int[] cooldowns = new int[6];
   // The World
   public int standPhase = DioRules.STAND_NONE;
   public int standElapsed;
   // Current action (one at a time)
   @Nullable
   public HeroAction action;
   public int elapsed;
   public int length;
   // Combo
   public int comboStep = -1;
   public int lastStep = -1;
   public int sinceStrikeEnd = -1;
   public boolean comboBuffered;
   // Barrage
   public boolean barrageHeld;
   public boolean finisher;
   // Guard
   public boolean guardHeld;
   // Time stop cast
   public int castBlood;
   // Drain
   public int drainTargetId = -1;
   @Nullable
   public UUID drainTarget;
   @Nullable
   public UUID drainEffect;
   public boolean drainOwnsControl;
   // Road Roller
   @Nullable
   public Vec3 rollerTarget;
   @Nullable
   public Vec3 rollerStart;
   // Passives
   public boolean inSun;
   public int regenTimer;
   public int bloodTimer;
   public int burnTimer;

   /** Every blood cost goes through here: the debug infinite mode keeps the bar full. */
   public void spendAllBlood() {
      this.blood = this.infiniteBlood ? DioRules.BLOOD_MAX : 0;
   }

   public boolean standOut() {
      return this.standPhase == DioRules.STAND_SUMMONING || this.standPhase == DioRules.STAND_OUT;
   }

   public boolean busy() {
      return this.action != null;
   }

   public void startAction(HeroAction action, int length) {
      this.action = action;
      this.elapsed = 0;
      this.length = length;
   }

   public void clearAction() {
      this.action = null;
      this.elapsed = 0;
      this.length = 0;
      this.finisher = false;
      this.barrageHeld = false;
      this.comboBuffered = false;
   }

   public void save(CompoundTag nbt) {
      nbt.putInt("DioBlood", this.blood);
      nbt.putBoolean("DioInfiniteBlood", this.infiniteBlood);
      nbt.putIntArray("DioCooldowns", this.cooldowns.clone());
   }

   public void load(CompoundTag nbt) {
      this.blood = DioRules.clampBlood(nbt.getInt("DioBlood"));
      this.infiniteBlood = nbt.getBoolean("DioInfiniteBlood");
      int[] saved = nbt.getIntArray("DioCooldowns");
      for (int i = 0; i < this.cooldowns.length && i < saved.length; i++) {
         this.cooldowns[i] = Math.max(0, saved[i]);
      }
   }

   /** Death keeps the cooldowns and half the blood (spec §7); channels and the stand never carry over. */
   public DioState respawnCopy() {
      DioState copy = new DioState();
      System.arraycopy(this.cooldowns, 0, copy.cooldowns, 0, this.cooldowns.length);
      copy.infiniteBlood = this.infiniteBlood;
      copy.blood = this.infiniteBlood ? DioRules.BLOOD_MAX : DioRules.clampBlood(this.blood / 2);
      return copy;
   }

   @Nullable
   public static DioState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.DIO) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof DioState state ? state : null;
      }

      return null;
   }

   public static DioState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof DioState existing) {
         return existing;
      }

      DioState state = new DioState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
