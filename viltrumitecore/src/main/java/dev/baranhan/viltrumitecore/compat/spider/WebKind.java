package dev.baranhan.viltrumitecore.compat.spider;

/** Our name for the kind of a web projectile: a sticky pellet, a cocoon net, or something we cannot read. */
public enum WebKind {
   PELLET,
   NET,
   UNKNOWN;

   /** Pure: the other mod's kind number (0 pellet, 1 net) in our terms. */
   public static WebKind of(int raw) {
      return raw == 0 ? PELLET : raw == 1 ? NET : UNKNOWN;
   }
}
