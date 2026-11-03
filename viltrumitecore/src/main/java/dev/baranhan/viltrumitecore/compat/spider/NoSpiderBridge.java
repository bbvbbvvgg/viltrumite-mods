package dev.baranhan.viltrumitecore.compat.spider;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** No Spider-Man mod (or no adapter fits): every answer is the neutral one. Venom runs in its base mode. */
public final class NoSpiderBridge implements SpiderBridge {
   private final String why;

   public NoSpiderBridge(String why) {
      this.why = why;
   }

   @Override
   public String id() {
      return "none";
   }

   @Override
   public Set<SpiderFeature> features() {
      return EnumSet.noneOf(SpiderFeature.class);
   }

   @Override
   public List<String> diagnostics() {
      return List.of(this.why);
   }

   @Override
   public boolean isSpider(Player player) {
      return false;
   }

   @Override
   public Optional<SpiderLine> line(Player player) {
      return Optional.empty();
   }

   @Override
   public boolean cutLine(ServerPlayer spider) {
      return false;
   }

   @Override
   public boolean isWebProjectile(Entity entity) {
      return false;
   }

   @Override
   public WebKind webKind(Entity entity) {
      return WebKind.UNKNOWN;
   }

   @Override
   public int webStacks(LivingEntity body) {
      return 0;
   }

   @Override
   public boolean cocooned(LivingEntity body) {
      return false;
   }

   @Override
   public boolean tearWeb(LivingEntity body) {
      return false;
   }

   @Override
   public void releaseInputs(ServerPlayer spider) {
   }

   @Override
   public boolean drive(ServerPlayer spider, SpiderAbility ability, boolean down) {
      return false;
   }

   @Override
   public boolean drivePrimary(ServerPlayer spider, boolean down) {
      return false;
   }

   @Override
   @Nullable
   public Vec3 wallNormal(Player player) {
      return null;
   }

   @Override
   @Nullable
   public String inputChannel() {
      return null;
   }
}
