package dev.baranhan.viltrumitecore.scp.scp106;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * One player's stay in the pocket dimension (design §6), saved with the world: where they were taken from, which
 * instance holds them, what they found. ENTERING and RETURNING survive a logout or a restart in the middle of a
 * transfer; the login handler finishes the move, so nobody is ever left between the two worlds.
 */
public final class PocketSession {
   public static final byte ENTERING = 0;
   public static final byte INSIDE = 1;
   public static final byte RETURNING = 2;
   public final UUID player;
   public final int slot;
   public byte phase = ENTERING;
   public String originDim = "minecraft:overworld";
   public double ox;
   public double oy;
   public double oz;
   public float oyaw;
   /** Bits 0..2: sigils I, II, III. */
   public int sigils;
   public boolean key;
   /** Room id of the checkpoint (-1: the arrival). */
   public int checkpoint = -1;
   public long entered;
   public long nextStalk;
   public int deaths;
   public int drags;
   /** One-time hints already shown (bit flags, see Scp106Pocket). */
   public int hints;
   /** Uses of the limited anomalies (portal id to count). */
   public final Map<Integer, Integer> portalUses = new HashMap<>();

   public PocketSession(UUID player, int slot) {
      this.player = player;
      this.slot = slot;
   }

   public int sigilCount() {
      return Integer.bitCount(this.sigils & 7);
   }

   public boolean hint(int bit) {
      if ((this.hints & bit) != 0) {
         return false;
      }
      this.hints |= bit;
      return true;
   }

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putUUID("player", this.player);
      t.putInt("slot", this.slot);
      t.putByte("phase", this.phase);
      t.putString("originDim", this.originDim);
      t.putDouble("ox", this.ox);
      t.putDouble("oy", this.oy);
      t.putDouble("oz", this.oz);
      t.putFloat("oyaw", this.oyaw);
      t.putInt("sigils", this.sigils);
      t.putBoolean("key", this.key);
      t.putInt("checkpoint", this.checkpoint);
      t.putLong("entered", this.entered);
      t.putLong("nextStalk", this.nextStalk);
      t.putInt("deaths", this.deaths);
      t.putInt("drags", this.drags);
      t.putInt("hints", this.hints);
      int[] uses = new int[this.portalUses.size() * 2];
      int i = 0;
      for (Map.Entry<Integer, Integer> e : this.portalUses.entrySet()) {
         uses[i++] = e.getKey();
         uses[i++] = e.getValue();
      }
      t.putIntArray("uses", uses);
      return t;
   }

   public static PocketSession load(CompoundTag t) {
      PocketSession s = new PocketSession(t.getUUID("player"), t.getInt("slot"));
      s.phase = t.getByte("phase");
      s.originDim = t.getString("originDim");
      s.ox = t.getDouble("ox");
      s.oy = t.getDouble("oy");
      s.oz = t.getDouble("oz");
      s.oyaw = t.getFloat("oyaw");
      s.sigils = t.getInt("sigils");
      s.key = t.getBoolean("key");
      s.checkpoint = t.getInt("checkpoint");
      s.entered = t.getLong("entered");
      s.nextStalk = t.getLong("nextStalk");
      s.deaths = t.getInt("deaths");
      s.drags = t.getInt("drags");
      s.hints = t.getInt("hints");
      int[] uses = t.getIntArray("uses");
      for (int i = 0; i + 1 < uses.length; i += 2) {
         s.portalUses.put(uses[i], uses[i + 1]);
      }
      return s;
   }
}
