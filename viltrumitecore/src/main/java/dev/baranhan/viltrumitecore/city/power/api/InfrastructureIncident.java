package dev.baranhan.viltrumitecore.city.power.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One hit on the city infrastructure, as the power grid recorded it. {@code cause} is a short category
 * ({@code meteor}, {@code chitauri}, {@code explosion}, {@code destruction}, {@code lightning}, {@code command} or
 * whatever an add-on passes, e.g. {@code ultron}, {@code collapse}); {@code source} identifies the reporter
 * ({@code chitauri:leviathan}, a module id); {@code severity} is 0..1; {@code radius} in blocks; {@code gameTime}
 * the level's game time of the hit.
 */
public record InfrastructureIncident(long id, ResourceKey<Level> dimension, double x, double y, double z, String cause, String source, float radius,
   float severity, long gameTime) {
   public Vec3 position() {
      return new Vec3(this.x, this.y, this.z);
   }
}
