package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client seam for hero slot edges: a hero may take a generic slot edge over
 * (its own prediction and packet) instead of {@code HeroInputC2SPacket}. One
 * interceptor per hero, registered by the hero's client setup. Client only.
 */
public final class HeroClientInput {
   /** Handles one slot edge locally. */
   @FunctionalInterface
   public interface Interceptor {
      /** true = the edge was handled here and the generic packet is not sent. */
      boolean intercept(LocalPlayer player, HeroAction action, boolean down);
   }

   private static final Map<HeroId, Interceptor> INTERCEPTORS = new EnumMap<>(HeroId.class);

   private HeroClientInput() {
   }

   public static void register(HeroId hero, Interceptor interceptor) {
      INTERCEPTORS.put(hero, interceptor);
   }

   /** true = this hero handled the edge itself. */
   public static boolean intercepted(LocalPlayer player, HeroId hero, HeroAction action, boolean down) {
      Interceptor interceptor = INTERCEPTORS.get(hero);
      return interceptor != null && interceptor.intercept(player, action, down);
   }
}
