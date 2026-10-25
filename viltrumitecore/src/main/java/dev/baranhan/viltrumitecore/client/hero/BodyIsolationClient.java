package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.BodyIsolation;
import net.minecraft.world.entity.player.Player;

/** Shared world-role predicate, never a concrete-hero branch or a client-only dependency in common code. */
public final class BodyIsolationClient {
   private BodyIsolationClient() {}
   public static boolean active(String dimension){return BodyIsolation.DIMENSION_ID.equals(dimension);}
   public static boolean active(Player player){return player!=null && active(player.level().dimension().location().toString());}
}
