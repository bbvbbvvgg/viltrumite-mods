package dev.baranhan.viltrumitecore.client.render.erasure;

import com.mojang.brigadier.arguments.FloatArgumentType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Client command {@code /erasuretest [scale]}: plays the original Roundabout sandbox swipe in front of the player,
 * with the original path, timeline and style. Scale 1 is the 1:1 reference; a larger scale previews the size used
 * by World Cut. Local only, nothing is sent to the server. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = Dist.CLIENT)
public final class ErasureTestSwipe implements ErasureRenderer.Provider {
   /** The original sandbox swipe in the caster's view: x = right, y = up, z = forward, blocks from the eyes. */
   static final Vec3[] SWIPE = {
      new Vec3(1.20, 0.05, 1.90),
      new Vec3(0.62, -0.05, 2.12),
      new Vec3(0.00, -0.15, 2.20),
      new Vec3(-0.65, -0.26, 2.12),
      new Vec3(-1.12, -0.36, 1.92),
      new Vec3(-1.30, -0.33, 1.84)
   };
   private static final int MAX = 8;

   /** A copy of the original swipe, for effects built on it (the diagonal World Cut wound). */
   public static Vec3[] originalSwipe() {
      return SWIPE.clone();
   }
   private static final ErasureTestSwipe INSTANCE = new ErasureTestSwipe();
   private static final List<Swipe> SWIPES = new ArrayList<>();

   private record Swipe(Level level, ErasurePath path, float scale, float seed, long cutTick) {
   }

   private ErasureTestSwipe() {
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterClientCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("erasuretest")
         .executes(context -> play(1F))
         .then(Commands.argument("scale", FloatArgumentType.floatArg(0.25F, 8F))
            .executes(context -> play(FloatArgumentType.getFloat(context, "scale")))));
   }

   private static int play(float scale) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null) return 0;
      ErasureRenderer.register(INSTANCE);
      Vec3 eyes = player.getEyePosition();
      Vec3 forward = player.getViewVector(1F);
      Vec3 right = Vec3.directionFromRotation(0F, player.getYRot()).cross(new Vec3(0, 1, 0)).normalize();
      Vec3 up = right.cross(forward);
      List<Vec3> points = new ArrayList<>();
      for (Vec3 local : SWIPE) {
         Vec3 p = local.scale(scale);
         points.add(eyes.add(right.scale(p.x)).add(up.scale(p.y)).add(forward.scale(p.z)));
      }
      if (SWIPES.size() >= MAX) SWIPES.remove(0);
      long cutTick = mc.level.getGameTime() + Math.round(-ErasureStroke.AURA_START * 20F);
      SWIPES.add(new Swipe(mc.level, ErasurePath.smooth(points), scale, player.getRandom().nextFloat(), cutTick));
      player.displayClientMessage(Component.literal("The Hand erasure test, scale " + scale), true);
      return 1;
   }

   @Override
   public void collect(List<ErasureStroke> out, float partialTick, long gameTime) {
      Level level = Minecraft.getInstance().level;
      Iterator<Swipe> it = SWIPES.iterator();
      while (it.hasNext()) {
         Swipe swipe = it.next();
         float t = (gameTime - swipe.cutTick + partialTick) / 20F;
         if (swipe.level != level || t > ErasureStroke.END) {
            it.remove();
            continue;
         }
         out.add(new ErasureStroke(swipe.path, 0F, swipe.path.length(), swipe.scale, swipe.seed, ErasureStyle.CYAN).update(t));
      }
   }

   @SubscribeEvent
   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) SWIPES.clear();
   }
}
