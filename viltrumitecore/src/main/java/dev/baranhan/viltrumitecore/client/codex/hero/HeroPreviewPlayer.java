package dev.baranhan.viltrumitecore.client.codex.hero;

import com.mojang.authlib.GameProfile;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A client-only dummy player for the 3D preview on the hero select screen. It is never added to the level;
 * it only carries a hero id, so the usual hero skin path (HeroSkins.resolve via the player mixin) dresses it
 * like that hero. All skin layers except the cape.
 */
class HeroPreviewPlayer extends RemotePlayer {
   static final UUID ID = UUID.nameUUIDFromBytes("viltrumitecore:hero_select_preview".getBytes(StandardCharsets.UTF_8));
   private HeroId wearing;

   HeroPreviewPlayer(ClientLevel level) {
      super(level, new GameProfile(ID, "Hero"));
      this.getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte)0x7E);
   }

   void wear(HeroId hero) {
      if (hero != this.wearing && (Object)this instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroId(hero);
         this.wearing = hero;
      }
   }

   public boolean isSkinLoaded() {
      return true;
   }

   public boolean shouldShowName() {
      return false;
   }
}
