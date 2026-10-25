package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Scene-owned adapter to the EXISTING dash_impact chain; neutral until Task 6 binds it. */
public final class ErasurePost {
   private static BooleanSupplier running = () -> false;
   private static Function<Float,ErasureUniforms> sample = pt -> ErasureUniforms.ZERO;
   private ErasurePost() {}
   public static void bind(BooleanSupplier active, Function<Float,ErasureUniforms> uniforms) {running=Objects.requireNonNull(active); sample=Objects.requireNonNull(uniforms);}
   public static boolean active() {return running.getAsBoolean();}
   public static ErasureUniforms uniforms(float partialTick) {return active() ? sample.apply(partialTick) : ErasureUniforms.ZERO;}
   public static void reset() {running=()->false; sample=pt->ErasureUniforms.ZERO;}
}
