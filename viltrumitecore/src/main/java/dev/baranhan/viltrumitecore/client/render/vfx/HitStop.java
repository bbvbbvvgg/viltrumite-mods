package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Shared client hit-stop (any hero): for a few ticks after a heavy contact an
 * entity holds still on screen. The server makes the pause real (the launch
 * lands {@code ticks} after the contact); here the target's interpolation is
 * frozen ({@link RenderLivingEvent.Pre} translates it back to where it was at
 * the trigger) and a hero renderer can hold its clip clock with
 * {@link #heldTicks}. Triggered by {@code HeroFxS2CPacket.HIT_STOP}. Render
 * thread only; the pure methods take the clock explicitly (unit tested).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HitStop {
   public static final HitStop INSTANCE = new HitStop();
   private final Map<Integer, Entry> entries = new HashMap<>();
   private final Map<Integer, Vec3> frozenPos = new HashMap<>();
   private final Map<Integer, Vec3> applied = new HashMap<>();

   private record Entry(double start, int ticks, float partial) {
      double end() {
         return this.start + this.ticks;
      }
   }

   /** Starts (or extends) a hit-stop of {@code ticks} for an entity at game time + partial. */
   public void trigger(int entityId, int ticks, long gameTime, float partial) {
      double now = gameTime + partial;
      this.entries.values().removeIf(e -> e.end() <= now);
      Entry old = this.entries.get(entityId);
      double start = old != null && old.end() > now ? old.start() : now;
      float frozen = old != null && old.end() > now ? old.partial() : partial;
      int length = (int) Math.ceil(now + ticks - start);
      this.entries.put(entityId, new Entry(start, length, frozen));
   }

   public boolean active(int entityId, long gameTime, float partial) {
      Entry entry = this.entries.get(entityId);
      double now = gameTime + partial;
      return entry != null && now >= entry.start() && now < entry.end();
   }

   /** The partial tick frozen at the trigger while active, else {@code partial}. */
   public float frozenPartial(int entityId, long gameTime, float partial) {
      return this.active(entityId, gameTime, partial) ? this.entries.get(entityId).partial() : partial;
   }

   /** Ticks spent in the running stop (subtract from a clip clock to hold it); 0 when none runs. */
   public double heldTicks(int entityId, long gameTime, float partial) {
      if (!this.active(entityId, gameTime, partial)) {
         return 0.0;
      }

      return gameTime + partial - this.entries.get(entityId).start();
   }

   // ---- client glue ----

   /** HeroFxS2CPacket.HIT_STOP: every listed entity holds still. */
   public static void trigger(int entityId, int ticks) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }

      float partial = mc.getFrameTime();
      INSTANCE.trigger(entityId, ticks, mc.level.getGameTime(), partial);
      Entity entity = mc.level.getEntity(entityId);
      if (entity != null) {
         INSTANCE.frozenPos.put(entityId, entity.getPosition(partial));
      }
   }

   public static float frozenPartial(Entity entity, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? partialTick : INSTANCE.frozenPartial(entity.getId(), mc.level.getGameTime(), partialTick);
   }

   public static double heldTicks(Entity entity, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0.0 : INSTANCE.heldTicks(entity.getId(), mc.level.getGameTime(), partialTick);
   }

   @SubscribeEvent
   public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
      LivingEntity entity = event.getEntity();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || INSTANCE.entries.isEmpty() || !INSTANCE.active(entity.getId(), mc.level.getGameTime(), event.getPartialTick())) {
         return;
      }

      Vec3 frozen = INSTANCE.frozenPos.get(entity.getId());
      if (frozen == null) {
         return;
      }

      // Hold the body where it was at the contact: undo the interpolated movement since then.
      Vec3 delta = frozen.subtract(entity.getPosition(event.getPartialTick()));
      if (delta.lengthSqr() > 4.0) {
         return;
      }

      event.getPoseStack().translate(delta.x, delta.y, delta.z);
      INSTANCE.applied.put(entity.getId(), delta);
   }

   @SubscribeEvent
   public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
      Vec3 delta = INSTANCE.applied.remove(event.getEntity().getId());
      if (delta != null) {
         PoseStack stack = event.getPoseStack();
         stack.translate(-delta.x, -delta.y, -delta.z);
      }

      if (INSTANCE.applied.isEmpty() && Minecraft.getInstance().level != null) {
         long time = Minecraft.getInstance().level.getGameTime();
         Iterator<Map.Entry<Integer, Entry>> it = INSTANCE.entries.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<Integer, Entry> e = it.next();
            if (e.getValue().end() < time - 1) {
               it.remove();
               INSTANCE.frozenPos.remove(e.getKey());
            }
         }
      }
   }
}
