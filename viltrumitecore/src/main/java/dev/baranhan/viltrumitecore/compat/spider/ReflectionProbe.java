package dev.baranhan.viltrumitecore.compat.spider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Looks members of another mod up by name with exact types and keeps one line per miss. Never initialises a class
 * ({@code Class.forName(name, false, loader)}), never throws: a miss is null plus a diagnostic.
 */
final class ReflectionProbe {
   private final ClassLoader loader;
   private final List<String> misses = new ArrayList<>();

   ReflectionProbe(ClassLoader loader) {
      this.loader = loader;
   }

   List<String> misses() {
      return this.misses;
   }

   void miss(String line) {
      this.misses.add(line);
   }

   @Nullable
   Class<?> type(String name) {
      try {
         return Class.forName(name, false, this.loader);
      } catch (ClassNotFoundException | LinkageError e) {
         this.misses.add("class " + name + " missing (" + e.getClass().getSimpleName() + ")");
         return null;
      }
   }

   /** A method with these exact parameter types whose return type is assignable to {@code returns} (null = any). */
   @Nullable
   Method method(@Nullable Class<?> owner, String name, boolean isStatic, @Nullable Class<?> returns, Class<?>... params) {
      if (owner == null || Arrays.asList(params).contains(null)) {
         this.misses.add(sig(owner, name, params) + " unreachable (owner or a parameter type missing)");
         return null;
      }
      try {
         Method m = owner.getDeclaredMethod(name, params);
         if (Modifier.isStatic(m.getModifiers()) != isStatic) {
            this.misses.add(sig(owner, name, params) + (isStatic ? " is no longer static" : " became static"));
            return null;
         }
         if (returns != null && !wrap(returns).isAssignableFrom(wrap(m.getReturnType()))) {
            this.misses.add(sig(owner, name, params) + " returns " + m.getReturnType().getSimpleName() + ", expected " + returns.getSimpleName());
            return null;
         }
         if (!m.trySetAccessible()) {
            this.misses.add(sig(owner, name, params) + " not accessible");
            return null;
         }
         return m;
      } catch (NoSuchMethodException e) {
         this.misses.add(sig(owner, name, params) + " missing");
         return null;
      } catch (LinkageError | SecurityException e) {
         this.misses.add(sig(owner, name, params) + " unreadable (" + e.getClass().getSimpleName() + ")");
         return null;
      }
   }

   /** A public instance field (or a static one when asked) with exactly this type. */
   @Nullable
   Field field(@Nullable Class<?> owner, String name, Class<?> type) {
      if (owner == null) {
         this.misses.add("field ?." + name + " unreachable (owner missing)");
         return null;
      }
      try {
         Field f = owner.getField(name);
         if (f.getType() != type) {
            this.misses.add("field " + owner.getSimpleName() + "." + name + " is " + f.getType().getSimpleName() + ", expected " + type.getSimpleName());
            return null;
         }
         return f;
      } catch (NoSuchFieldException e) {
         this.misses.add("field " + owner.getSimpleName() + "." + name + " missing");
         return null;
      } catch (LinkageError | SecurityException e) {
         this.misses.add("field " + owner.getSimpleName() + "." + name + " unreadable (" + e.getClass().getSimpleName() + ")");
         return null;
      }
   }

   /** An enum constant by name. */
   @Nullable
   Object constant(@Nullable Class<?> enumType, String name) {
      if (enumType == null || !enumType.isEnum()) {
         this.misses.add("enum constant ?." + name + " unreachable (enum missing)");
         return null;
      }
      for (Object c : enumType.getEnumConstants()) {
         if (((Enum<?>)c).name().equals(name)) {
            return c;
         }
      }
      this.misses.add("enum constant " + enumType.getSimpleName() + "." + name + " missing");
      return null;
   }

   static String sig(@Nullable Class<?> owner, String name, Class<?>... params) {
      return (owner == null ? "?" : owner.getSimpleName()) + "." + name + "("
         + Arrays.stream(params).map(p -> p == null ? "?" : p.getSimpleName()).collect(Collectors.joining(",")) + ")";
   }

   private static Class<?> wrap(Class<?> c) {
      if (!c.isPrimitive()) return c;
      if (c == boolean.class) return Boolean.class;
      if (c == int.class) return Integer.class;
      if (c == long.class) return Long.class;
      if (c == float.class) return Float.class;
      if (c == double.class) return Double.class;
      if (c == void.class) return Void.class;
      return c;
   }
}
