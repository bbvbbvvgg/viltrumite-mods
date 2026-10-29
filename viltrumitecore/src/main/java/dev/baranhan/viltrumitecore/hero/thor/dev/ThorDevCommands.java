package dev.baranhan.viltrumitecore.hero.thor.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The one dev-only {@code /thor} root. Sessions add children with
 * {@link #registerChild} (e.g. A: "debug hammer", B: "debug combat", C:
 * "grab", "fatality", "debug grab-window"); paths are unique per owner-free
 * registry and merge under the root. The whole tree is added to the
 * dispatcher outside production (or in a QA build started with
 * {@code -Dviltrumitecore.thor.qa=true}) and only for operators (level 2).
 * Foundation fixtures live under {@code /thor foundation}: they set bridge
 * state directly; they never start a move, spawn a hammer or stand in for a
 * mechanic.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThorDevCommands {
   public static final int PERMISSION = 2;
   public static final String FOUNDATION = "foundation";

   record Child(String owner, String path, UnaryOperator<LiteralArgumentBuilder<CommandSourceStack>> leaf) {
   }

   private static final Map<String, Child> CHILDREN = new LinkedHashMap<>();

   static {
      registerFoundation();
   }

   private ThorDevCommands() {
   }

   /** System property that also enables the tree in a production build (QA builds, like reinhardqa). */
   public static final String QA_PROPERTY = "viltrumitecore.thor.qa";

   /** Pure gate: dev / test environment, or a production build started with -Dviltrumitecore.thor.qa=true. */
   public static boolean enabled(boolean production, boolean qa) {
      return !production || qa;
   }

   public static boolean enabled(boolean production) {
      return enabled(production, Boolean.getBoolean(QA_PROPERTY));
   }

   /**
    * Add {@code /thor <path>}: {@code path} is one or more space-separated
    * literals, {@code leaf} completes the last literal (arguments, executes).
    * A path is registered once; prefixes may be shared ("debug hammer" and
    * "debug combat" merge under "debug").
    */
   public static synchronized void registerChild(String owner, String path, UnaryOperator<LiteralArgumentBuilder<CommandSourceStack>> leaf) {
      if (owner == null || owner.isBlank() || path == null || path.isBlank() || leaf == null) {
         throw new IllegalArgumentException("owner, path and leaf are required");
      }

      String key = normalize(path);
      Child existing = CHILDREN.get(key);
      if (existing != null) {
         throw new IllegalStateException("/thor " + key + " already registered by " + existing.owner());
      }

      String conflict = conflict(owner, key, CHILDREN.values());
      if (conflict != null) {
         throw new IllegalStateException("/thor " + key + " collides with " + conflict);
      }

      CHILDREN.put(key, new Child(owner, key, leaf));
   }

   /** Registered child paths, in registration order. */
   public static synchronized List<String> paths() {
      return new ArrayList<>(CHILDREN.keySet());
   }

   public static synchronized String owner(String path) {
      Child child = CHILDREN.get(normalize(path));
      return child == null ? null : child.owner();
   }

   /** Tests only. */
   public static synchronized void resetForTests() {
      CHILDREN.clear();
      registerFoundation();
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!enabled(FMLEnvironment.production)) {
         return;
      }

      List<Child> children;
      synchronized (ThorDevCommands.class) {
         children = new ArrayList<>(CHILDREN.values());
      }

      for (Child child : children) {
         event.getDispatcher().register(Commands.literal("thor").requires(source -> source.hasPermission(PERMISSION)).then(build(child)));
      }
   }

   private static LiteralArgumentBuilder<CommandSourceStack> build(Child child) {
      String[] parts = child.path().split(" ");
      LiteralArgumentBuilder<CommandSourceStack> node = child.leaf().apply(Commands.literal(parts[parts.length - 1]));
      for (int i = parts.length - 2; i >= 0; i--) {
         node = Commands.literal(parts[i]).then(node);
      }

      return node;
   }

   /**
    * The foundation's root literal is reserved for the foundation, and one
    * owner's path is never a prefix of another owner's (a leaf of one owner
    * cannot hide another owner's subtree). Shared intermediate literals
    * ("debug") stay allowed. Null when the path is free.
    */
   static String conflict(String owner, String key, Iterable<Child> existing) {
      if (!FOUNDATION.equals(owner) && (key.equals(FOUNDATION) || key.startsWith(FOUNDATION + " "))) {
         return "the reserved '" + FOUNDATION + "' root";
      }

      for (Child child : existing) {
         if (child.owner().equals(owner)) {
            continue;
         }

         if (key.startsWith(child.path() + " ") || child.path().startsWith(key + " ")) {
            return child.path() + " of " + child.owner();
         }
      }

      return null;
   }

   private static String normalize(String path) {
      String key = path.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
      if (!key.matches("[a-z0-9_\\-]+( [a-z0-9_\\-]+)*")) {
         throw new IllegalArgumentException("bad command path: " + path);
      }

      return key;
   }

   // --- foundation fixtures: /thor foundation ... ---

   private static void registerFoundation() {
      CHILDREN.put("foundation state", new Child(FOUNDATION, "foundation state", node -> node.executes(ThorDevCommands::state)));
      // Read-only: the world hammer store (session A, /thor debug hammer ...) is the one writer of where Mjölnir is.
      CHILDREN.put("foundation hammer", new Child(FOUNDATION, "foundation hammer", node -> node.executes(ThorDevCommands::hammer)));
      CHILDREN.put("foundation mode", new Child(FOUNDATION, "foundation mode", node -> node.then(Commands.argument("mode", StringArgumentType.word())
         .suggests((c, builder) -> SharedSuggestionProvider.suggest(modeKeys(), builder))
         .executes(c -> mode(c, StringArgumentType.getString(c, "mode"))))));
      // The window always names its target: the paired GRAB refuses a partner-less start (StagedTimeline).
      CHILDREN.put("foundation grab_window", new Child(FOUNDATION, "foundation grab_window", node -> node.then(
         Commands.argument("ticks", IntegerArgumentType.integer(1, 200)).then(Commands.argument("target", EntityArgument.entity())
            .executes(c -> grabWindow(c, IntegerArgumentType.getInteger(c, "ticks"), EntityArgument.getEntity(c, "target")))))));
      CHILDREN.put("foundation release_locks", new Child(FOUNDATION, "foundation release_locks", node -> node.executes(ThorDevCommands::releaseLocks)));
   }

   /** Lower-case keys of the Thor modes. */
   public static List<String> modeKeys() {
      List<String> keys = new ArrayList<>();
      for (ThorMode mode : ThorMode.values()) {
         keys.add(mode.name().toLowerCase(Locale.ROOT));
      }

      return keys;
   }

   /** Null for an unknown word (never silently NORMAL). */
   public static ThorMode parseMode(String key) {
      for (ThorMode mode : ThorMode.values()) {
         if (mode.name().equalsIgnoreCase(key)) {
            return mode;
         }
      }

      return null;
   }

   private static ThorState thor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorState state = ThorState.of(player);
      if (state == null) {
         context.getSource().sendFailure(Component.literal("Not Thor"));
      }

      return state;
   }

   private static int state(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      String text = ThorBridge.hammer(player.getUUID(), state) + " " + ThorBridge.state(state);
      context.getSource().sendSuccess(() -> Component.literal(text), false);
      return 1;
   }

   private static int hammer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      String text = "hammer = " + ThorBridge.hammer(player.getUUID(), state) + " (move it with /thor debug hammer throw|recall|spawn)";
      context.getSource().sendSuccess(() -> Component.literal(text), false);
      return 1;
   }

   private static int mode(CommandContext<CommandSourceStack> context, String key) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ThorMode mode = parseMode(key);
      if (mode == null) {
         context.getSource().sendFailure(Component.literal("Unknown mode '" + key + "', expected one of " + modeKeys()));
         return 0;
      }

      state.setMode(mode);
      context.getSource().sendSuccess(() -> Component.literal("mode = " + state.mode()), false);
      return 1;
   }

   private static int grabWindow(CommandContext<CommandSourceStack> context, int ticks, Entity target) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ThorBridge.openGrabWindow(state, ticks, target.getId());
      context.getSource().sendSuccess(() -> Component.literal("grab window = " + state.grabWindowTicks()), false);
      return 1;
   }

   private static int releaseLocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      String lock = state.sceneLock();
      String lease = state.cameraLease();
      // A lifecycle stop: the running move hears onStop and every session drops what it owns
      // (scenes end, the lease owner lets go) before the flags clear; nothing vanishes behind an owner.
      ThorActions.stop(player, state, ThorLifecycle.Cause.DEV_RESET);
      context.getSource().sendSuccess(() -> Component.literal("lifecycle reset: scene lock " + lock + ", camera lease " + lease + " released through their owners"), false);
      return 1;
   }
}
