package dev.baranhan.viltrumitecore.hero.wish;

/**
 * One wish of Od Lagna's Wish (docs/design/2026-10-30-od-lagna-wish-design.md). Pure data: a stable id
 * (network + lang), the key of the hero it grants ({@code HeroId.key()}), the scene style the clients
 * draw, and the grant that changes the player on the server.
 */
public record Wish(String id, String heroKey, String style, WishGrant grant) {
   public static final int MAX_ID = 32;

   public Wish {
      if (id == null || id.isEmpty() || id.length() > MAX_ID || !id.matches("[a-z0-9_]+")) {
         throw new IllegalArgumentException("Bad wish id: " + id);
      }
      if (heroKey == null || style == null || grant == null) {
         throw new IllegalArgumentException("Incomplete wish: " + id);
      }
   }

   /** «я желаю …» line. */
   public String textKey() {
      return "wish.viltrumitecore." + this.id;
   }

   /** The answer whispered under the selected wish. */
   public String echoKey() {
      return "wish.viltrumitecore." + this.id + ".echo";
   }
}
