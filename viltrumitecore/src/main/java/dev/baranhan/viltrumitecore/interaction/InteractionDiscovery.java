package dev.baranhan.viltrumitecore.interaction;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.codex.CodexProgress;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.slf4j.Logger;

/**
 * Integration of #107 (contextual interactions) with #108 (the encyclopedia "Chronicles of Viltrum"): when an
 * interaction really fires, every player taking part in it discovers its entry ({@link CodexProgress#discoverInteraction},
 * saved per player and synced to his client only). A participant is the attacking player, the hit player, the
 * shooter of the projectile, the player behind the damage source, and the owner of a hero summon (Legion suit,
 * Avenger, Quinjet, shadow) on either side. Mobs and bystanders discover nothing.
 */
public final class InteractionDiscovery {
   private static final Logger LOGGER = LogUtils.getLogger();

   private InteractionDiscovery() {
   }

   /** The players who took part in {@code ctx}, each once, in a stable order (attacker side first). */
   public static Set<ServerPlayer> participants(InteractionContext ctx) {
      Set<ServerPlayer> out = new LinkedHashSet<>();
      add(out, ctx.attacker());
      add(out, ctx.direct());
      Projectile projectile = ctx.projectile();
      if (projectile != null) {
         add(out, projectile.getOwner());
      }
      if (ctx.source() != null) {
         add(out, ctx.source().getEntity());
         add(out, ctx.source().getDirectEntity());
      }
      add(out, ctx.target());
      return out;
   }

   static void add(Set<ServerPlayer> out, @Nullable Entity e) {
      if (e instanceof ServerPlayer p) {
         out.add(p);
         return;
      }
      if (e instanceof Projectile p && p.getOwner() instanceof ServerPlayer shooter) {
         out.add(shooter);
         return;
      }
      ServerPlayer owner = SummonOwners.ownerOf(e);
      if (owner != null) {
         out.add(owner);
      }
   }

   /** Called once per real firing of {@code spec}; never throws into combat code. */
   public static void fired(InteractionSpec spec, InteractionContext ctx) {
      try {
         for (ServerPlayer p : participants(ctx)) {
            if (CodexProgress.discoverInteraction(p, spec.id())) {
               LOGGER.debug("codex: {} discovered interaction {}", p.getGameProfile().getName(), spec.id());
            }
         }
      } catch (RuntimeException e) {
         LOGGER.error("codex discovery of interaction {} failed", spec.id(), e);
      }
   }
}
