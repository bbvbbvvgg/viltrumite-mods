package dev.baranhan.viltrumitecore.compat.spider;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * One versioned adapter onto another mod's Spider-Man. Our stable contract (§4.3 of the compatibility report): no
 * method throws, none returns null where a value is promised, and a missing feature answers neutrally. Callers ask
 * {@link SpiderCompat#has} first; the adapter still guards every call itself.
 */
public interface SpiderBridge {
   /** Adapter id for the log and {@code /viltrumite compat spider}. */
   String id();

   /** The features this adapter bound (immutable snapshot). */
   Set<SpiderFeature> features();

   /** One human-readable line per missing or broken member. */
   List<String> diagnostics();

   boolean isSpider(Player player);

   Optional<SpiderLine> line(Player player);

   /** Ends the swing of a Spider whose line was cut. True when a swing was running and is now over. */
   boolean cutLine(ServerPlayer spider);

   boolean isWebProjectile(Entity entity);

   WebKind webKind(Entity entity);

   int webStacks(LivingEntity body);

   boolean cocooned(LivingEntity body);

   /** Tears the webbing and the cocoon off; true when something was removed. */
   boolean tearWeb(LivingEntity body);

   /** Releases every held ability key of a Spider (possession start / end). */
   void releaseInputs(ServerPlayer spider);

   /** Presses or releases one Spider ability on the possessed body. False when not possible. */
   boolean drive(ServerPlayer spider, SpiderAbility ability, boolean down);

   /** Presses or releases the Spider's primary attack. */
   boolean drivePrimary(ServerPlayer spider, boolean down);

   /** Outward wall normal when the Spider clings to a wall, else null. */
   @Nullable
   Vec3 wallNormal(Player player);

   /** The other mod's network channel whose serverbound packets carry its ability input, or null. */
   @Nullable
   String inputChannel();
}
