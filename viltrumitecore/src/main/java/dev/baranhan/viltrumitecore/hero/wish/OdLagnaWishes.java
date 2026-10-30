package dev.baranhan.viltrumitecore.hero.wish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The central wish registry (both sides, same order on server and client). A new hero needs only one
 * {@link #register} call: the screen, the ritual and the scene read everything from here.
 */
public final class OdLagnaWishes {
   /** Key of the plain human (HeroId.HUMAN.key()). */
   public static final String HUMAN = "human";
   public static final String RETURN = "return";
   private static final Map<String, Wish> WISHES = new LinkedHashMap<>();

   private OdLagnaWishes() {
   }

   /** The shipped wishes, in screen order; the return to a human is always last. */
   public static void registerDefaults() {
      if (!WISHES.isEmpty()) {
         return;
      }
      register(new Wish("time", "dio", "time", WishGrant.hero("dio")));
      register(new Wish("death", "jinwoo", "shadow", WishGrant.hero("jinwoo")));
      register(new Wish("thunder", "thor", "thunder", WishGrant.hero("thor")));
      register(new Wish("reality", "sinclair", "cards", WishGrant.hero("sinclair")));
      register(new Wish("genius", "ironman", "tech", WishGrant.hero("ironman")));
      register(new Wish("crimson", "homelander", "crimson", WishGrant.hero("homelander")));
      register(new Wish("sword", "reinhard", "holy", WishGrant.hero("reinhard")));
      register(new Wish("untouchable", "regulus", "still", WishGrant.hero("regulus")));
      register(new Wish(RETURN, HUMAN, "mortal", WishGrant.hero(HUMAN)));
   }

   public static void register(Wish wish) {
      if (WISHES.containsKey(wish.id())) {
         throw new IllegalStateException("Duplicate wish id: " + wish.id());
      }
      WISHES.put(wish.id(), wish);
   }

   @Nullable
   public static Wish byId(@Nullable String id) {
      return id == null ? null : WISHES.get(id);
   }

   /** Every wish in screen order (the return wish last). */
   public static List<Wish> all() {
      List<Wish> list = new ArrayList<>(WISHES.values());
      Wish back = WISHES.get(RETURN);
      if (back != null) {
         list.remove(back);
         list.add(back);
      }
      return Collections.unmodifiableList(list);
   }

   /** Tests only. */
   static void clearForTests() {
      WISHES.clear();
   }
}
