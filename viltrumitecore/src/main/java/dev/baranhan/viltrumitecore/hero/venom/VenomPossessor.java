package dev.baranhan.viltrumitecore.hero.venom;

/** A non-player Venom body that can ride a host (the boss): told when the session ends, whatever the reason. */
public interface VenomPossessor {
   /** The engulf is over: the body is a husk now and rides {@code host}. */
   void beganPossessing(net.minecraft.server.level.ServerPlayer host);

   void onPossessionEnd(VenomPossession.End reason);
}
