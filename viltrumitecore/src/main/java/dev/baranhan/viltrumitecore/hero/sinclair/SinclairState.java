package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.act.StageAct;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScene;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server state of one Sinclair (spec §5, §8). Cooldowns and the magazine are saved; everything else is transient. */
public final class SinclairState {
   public enum Ult {
      NONE,
      CAPTURE,
      WINDOW,
      PERFORM
   }

   // Prestige
   public int magazine = SinclairRules.MAGAZINE;
   public int reload;
   public boolean triggerHeld;
   public int burstShotsLeft;
   public int burstShotTimer;
   public int burstCooldown;
   public int sinceShot = 999;
   public int shots;
   // Cooldowns
   public int boltCooldown;
   public int assistCooldown;
   public int hexCooldown;
   public int misCooldown;
   public int ultCooldown;
   // Body move
   @Nullable
   public SinclairMove move;
   public int moveTick;
   // Vexing Bolt
   public int boltId = -1;
   public int assistBoltId = -1;
   public boolean boltRedirected;
   // Assistant
   @Nullable
   public UUID assistant;
   public int assistantId = -1;
   public int swapsUsed;
   public int lastSwapAge = -999;
   public boolean assistLeaving;
   @Nullable
   public Vec3 summonPoint;
   // Rabbit Hex
   @Nullable
   public Vec3 hexPoint;
   // Misdirection
   public int veil;
   @Nullable
   public UUID decoy;
   public boolean veilStrikeReady;
   /** Ticks in which the first landed hit after the veil lifts is "Ta-da!". */
   public int tadaWindow;
   // Audience Participation
   public Ult ult = Ult.NONE;
   public int volunteerId = -1;
   @Nullable
   public UUID volunteer;
   @Nullable
   public StageAct captured;
   public SinclairRepertoire.Source capturedFrom = SinclairRepertoire.Source.FALLBACK;
   public int window;
   @Nullable
   public ActScene scene;
   /** The copy cinematic's local stop while the move COPY runs (design 2026-11-02 §2), else null. */
   @Nullable
   public dev.baranhan.viltrumitecore.hero.sinclair.act.CopyScene copy;
   /** Server cinematic switch, mirrored each tick for the snapshot (SinclairFlags.STAGE_CAMERA). */
   public boolean cinema = true;
   // Ovation
   public int ovationPulse;
   public int ovations;

   public boolean busy() {
      return this.move != null;
   }

   public void startMove(SinclairMove move) {
      this.move = move;
      this.moveTick = 0;
   }

   public void clearMove() {
      this.move = null;
      this.moveTick = 0;
   }

   /** Every transient piece back to rest; cooldowns, magazine and Ovation count stay. */
   public void resetTransient() {
      this.reload = 0;
      this.triggerHeld = false;
      this.burstShotsLeft = 0;
      this.burstShotTimer = 0;
      this.burstCooldown = 0;
      this.sinceShot = 999;
      this.move = null;
      this.moveTick = 0;
      this.boltId = -1;
      this.assistBoltId = -1;
      this.boltRedirected = false;
      this.assistant = null;
      this.assistantId = -1;
      this.swapsUsed = 0;
      this.lastSwapAge = -999;
      this.assistLeaving = false;
      this.summonPoint = null;
      this.hexPoint = null;
      this.veil = 0;
      this.decoy = null;
      this.veilStrikeReady = false;
      this.tadaWindow = 0;
      this.ult = Ult.NONE;
      this.volunteerId = -1;
      this.volunteer = null;
      this.captured = null;
      this.capturedFrom = SinclairRepertoire.Source.FALLBACK;
      this.window = 0;
      this.scene = null;
      this.copy = null;
      this.ovationPulse = 0;
   }

   /** §8: the respawned body keeps cooldowns and Ovation, nothing else. */
   public SinclairState respawnCopy() {
      SinclairState copy = new SinclairState();
      copy.boltCooldown = this.boltCooldown;
      copy.assistCooldown = this.assistCooldown;
      copy.hexCooldown = this.hexCooldown;
      copy.misCooldown = this.misCooldown;
      copy.ultCooldown = this.ultCooldown;
      copy.ovations = this.ovations;
      return copy;
   }

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      tag.putInt("magazine", this.magazine);
      tag.putInt("bolt", this.boltCooldown);
      tag.putInt("assist", this.assistCooldown);
      tag.putInt("hex", this.hexCooldown);
      tag.putInt("mis", this.misCooldown);
      tag.putInt("ult", this.ultCooldown);
      tag.putInt("ovations", this.ovations);
      nbt.put("sinclair", tag);
   }

   public void load(CompoundTag nbt) {
      CompoundTag tag = nbt.getCompound("sinclair");
      this.magazine = tag.contains("magazine") ? clamp(tag.getInt("magazine"), 0, SinclairRules.MAGAZINE) : SinclairRules.MAGAZINE;
      this.boltCooldown = clamp(tag.getInt("bolt"), 0, SinclairRules.BOLT_COOLDOWN);
      this.assistCooldown = clamp(tag.getInt("assist"), 0, SinclairRules.ASSIST_COOLDOWN);
      this.hexCooldown = clamp(tag.getInt("hex"), 0, SinclairRules.HEX_COOLDOWN);
      this.misCooldown = clamp(tag.getInt("mis"), 0, SinclairRules.MIS_COOLDOWN);
      this.ultCooldown = clamp(tag.getInt("ult"), 0, SinclairRules.ULT_COOLDOWN);
      this.ovations = Math.max(0, tag.getInt("ovations"));
      if (this.magazine <= 0) {
         this.reload = SinclairRules.RELOAD_TICKS;
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   /** Debug reset (/hero cooldown reset): ability timers only (bolt, assist, hex, misdirection, ult, burst). */
   public void clearCooldowns() {
      this.boltCooldown = 0;
      this.assistCooldown = 0;
      this.hexCooldown = 0;
      this.misCooldown = 0;
      this.ultCooldown = 0;
      this.burstCooldown = 0;
   }

   @Nullable
   public static SinclairState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.SINCLAIR) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof SinclairState state ? state : null;
      }

      return null;
   }

   public static SinclairState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer) player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof SinclairState existing) {
         return existing;
      }

      SinclairState state = new SinclairState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
