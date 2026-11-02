package dev.baranhan.viltrumitecore.compat.spider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for Falbiks Heroes 0.1.x (studied JAR: falbiksheroes-PRE-BETA.jar, SHA-256 75d793e3…b90a). Reflection
 * only: no import of their classes, no mixin into them. Every member is looked up with its exact types when the
 * adapter binds; a miss switches off only the features that need it, a failing call switches its feature off for the
 * session (one ERROR line). See docs/venom/spiderman-compatibility.md §2 for what each member does on their side.
 */
public final class FalbiksSpiderV0 implements SpiderBridge {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/SpiderCompat");
   public static final String ID = "falbiks-v0";
   static final String P = "dev.falbiks.heroes.";
   public static final String SPIDER_HERO_ID = "falbiksheroes:spider";
   public static final String WEB_TYPE = "falbiksheroes:spider_web";
   public static final String CHANNEL = "falbiksheroes:main";
   static final String STUN = "falbiksheroes:stun";
   static final String WEB_TAG_FALLBACK = "falbiksheroes_spider_web";
   static final String COCOON_TAG_FALLBACK = "falbiksheroes_spider_cocoon";

   // HeroData capability
   @Nullable final Method dataGet;
   @Nullable final Method dataHeroId;
   @Nullable final Method dataState;
   @Nullable final Field dataHeld;
   // HeroState / SpiderState
   @Nullable final Field revision;
   @Nullable final Field clock;
   @Nullable final Method startCooldown;
   @Nullable final Class<?> spiderState;
   @Nullable final Field swinging;
   @Nullable final Field ax;
   @Nullable final Field ay;
   @Nullable final Field az;
   @Nullable final Field rope;
   @Nullable final Field swingGame;
   @Nullable final Field noFallUntil;
   // SpiderHero statics
   @Nullable final Method hand;
   @Nullable final Method endSwing;
   @Nullable final Method wallNormal;
   @Nullable final Method cocoonedM;
   @Nullable final Method webStacksM;
   @Nullable final Field webTag;
   @Nullable final Field cocoonTag;
   // SpiderWeb
   @Nullable final Class<?> webClass;
   @Nullable final Method kind;
   // HeroManager input
   @Nullable final Method handleInput;
   @Nullable final Method handlePrimary;
   final Map<SpiderAbility, Object> slots = new EnumMap<>(SpiderAbility.class);
   final boolean channelKnown;

   private final String version;
   private final List<String> diagnostics = new ArrayList<>();
   private volatile Set<SpiderFeature> active;
   private final boolean cutByFields;
   @Nullable private String webTagValue;
   @Nullable private String cocoonTagValue;

   FalbiksSpiderV0(ClassLoader loader, String version) {
      this.version = version;
      ReflectionProbe p = new ReflectionProbe(loader);
      Class<?> heroData = p.type(P + "hero.HeroData");
      Class<?> heroState = p.type(P + "hero.HeroState");
      this.spiderState = p.type(P + "heroes.spider.SpiderState");
      Class<?> spiderHero = p.type(P + "heroes.spider.SpiderHero");
      this.webClass = p.type(P + "heroes.spider.SpiderWeb");
      Class<?> manager = p.type(P + "hero.HeroManager");
      Class<?> slot = p.type(P + "ability.AbilitySlot");

      this.dataGet = p.method(heroData, "get", true, heroData, Player.class);
      this.dataHeroId = p.method(heroData, "heroId", false, ResourceLocation.class);
      this.dataState = p.method(heroData, "state", false, heroState);
      this.dataHeld = p.field(heroData, "held", EnumSet.class);
      this.revision = p.field(heroState, "revision", int.class);
      this.clock = p.field(heroState, "clock", long.class);
      this.startCooldown = p.method(heroState, "startCooldown", false, Void.class, String.class, int.class);
      this.swinging = p.field(this.spiderState, "swinging", boolean.class);
      this.ax = p.field(this.spiderState, "ax", double.class);
      this.ay = p.field(this.spiderState, "ay", double.class);
      this.az = p.field(this.spiderState, "az", double.class);
      this.rope = p.field(this.spiderState, "rope", float.class);
      this.swingGame = p.field(this.spiderState, "swingGame", long.class);
      this.noFallUntil = p.field(this.spiderState, "noFallUntil", long.class);
      this.hand = p.method(spiderHero, "hand", true, Vec3.class, Player.class);
      this.endSwing = p.method(spiderHero, "endSwing", true, Void.class, ServerPlayer.class, this.spiderState, boolean.class);
      this.wallNormal = p.method(spiderHero, "wallNormal", true, Vec3.class, Player.class);
      this.cocoonedM = p.method(spiderHero, "cocooned", true, Boolean.class, LivingEntity.class);
      this.webStacksM = p.method(spiderHero, "webStacks", true, Integer.class, LivingEntity.class);
      this.webTag = p.field(spiderHero, "WEB_TAG", String.class);
      this.cocoonTag = p.field(spiderHero, "COCOON_TAG", String.class);
      this.kind = p.method(this.webClass, "kind", false, Integer.class);
      this.handleInput = p.method(manager, "handleInput", true, Void.class, ServerPlayer.class, slot, boolean.class);
      this.handlePrimary = p.method(manager, "handlePrimary", true, Void.class, ServerPlayer.class, boolean.class);
      for (SpiderAbility a : SpiderAbility.values()) {
         Object c = p.constant(slot, a.slot);
         if (c != null) {
            this.slots.put(a, c);
         }
      }
      this.channelKnown = p.type(P + "net.FHNetwork") != null && p.type(P + "net.AbilityInputPacket") != null;

      EnumSet<SpiderFeature> on = EnumSet.noneOf(SpiderFeature.class);
      boolean detect = this.dataGet != null && this.dataHeroId != null;
      boolean state = detect && this.dataState != null && this.spiderState != null;
      if (detect) on.add(SpiderFeature.DETECT_HERO);
      if (state && this.swinging != null && this.ax != null && this.ay != null && this.az != null && this.rope != null) {
         on.add(SpiderFeature.LINE_STATE);
      }
      boolean fieldsCut = this.swinging != null && this.revision != null;
      if (on.contains(SpiderFeature.LINE_STATE) && (this.endSwing != null || fieldsCut)) {
         on.add(SpiderFeature.CUT_LINE);
      }
      this.cutByFields = this.endSwing == null;
      if (this.endSwing == null && fieldsCut) {
         p.miss("CUT_LINE: fallback to field writes (endSwing missing)");
      }
      if (this.webClass != null) on.add(SpiderFeature.WEB_PROJECTILE);
      if (this.cocoonedM != null && this.webStacksM != null) on.add(SpiderFeature.WEB_STATUS);
      if (this.webTag != null && this.cocoonTag != null) on.add(SpiderFeature.TEAR_WEB);
      boolean input = this.handleInput != null && this.dataGet != null;
      if (input && this.handlePrimary != null) on.add(SpiderFeature.INPUT_RELEASE);
      if (input && this.handlePrimary != null && this.slots.size() == SpiderAbility.values().length) {
         on.add(SpiderFeature.DRIVE_ABILITIES);
      }
      if (this.channelKnown) on.add(SpiderFeature.INPUT_GATE);
      if (this.wallNormal != null) on.add(SpiderFeature.WALL_STATE);
      this.active = Collections.unmodifiableSet(on);
      this.diagnostics.addAll(p.misses());
   }

   @Override
   public String id() {
      return ID;
   }

   public String version() {
      return this.version;
   }

   @Override
   public Set<SpiderFeature> features() {
      return this.active;
   }

   @Override
   public List<String> diagnostics() {
      return List.copyOf(this.diagnostics);
   }

   /** True when CUT_LINE writes the fields itself because endSwing is gone. */
   public boolean cutsByFields() {
      return this.cutByFields;
   }

   private boolean on(SpiderFeature f) {
      return this.active.contains(f);
   }

   /** A call failed: this feature is off until the next start, with one ERROR line. */
   synchronized void fail(SpiderFeature f, Throwable t) {
      if (!this.active.contains(f)) {
         return;
      }
      EnumSet<SpiderFeature> next = EnumSet.noneOf(SpiderFeature.class);
      next.addAll(this.active);
      next.remove(f);
      this.active = Collections.unmodifiableSet(next);
      Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
      String line = f + ": disabled after a failing call (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")";
      this.diagnostics.add(line);
      LOG.error("[SpiderCompat] {} {}", ID, line, cause);
   }

   // ---- raw operations on plain objects (also used by the tests with stub classes) -------------------------------

   @Nullable
   Object dataOf(@Nullable Object player) throws ReflectiveOperationException {
      return this.dataGet == null ? null : this.dataGet.invoke(null, player);
   }

   boolean isSpiderData(@Nullable Object data) throws ReflectiveOperationException {
      if (data == null || this.dataHeroId == null) {
         return false;
      }
      Object id = this.dataHeroId.invoke(data);
      return id != null && SPIDER_HERO_ID.equals(id.toString());
   }

   @Nullable
   Object spiderStateOf(@Nullable Object data) throws ReflectiveOperationException {
      if (data == null || this.dataState == null || this.spiderState == null) {
         return null;
      }
      Object s = this.dataState.invoke(data);
      return this.spiderState.isInstance(s) ? s : null;
   }

   /** The line of a state object, or null when it does not swing. {@code handPos} is used when hand() is missing. */
   @Nullable
   SpiderLine lineOf(Object state, Vec3 handPos) throws ReflectiveOperationException {
      if (!this.swinging.getBoolean(state)) {
         return null;
      }
      Vec3 anchor = new Vec3(this.ax.getDouble(state), this.ay.getDouble(state), this.az.getDouble(state));
      return new SpiderLine(handPos, anchor, this.rope.getFloat(state));
   }

   /** Ends a swing on a state object, exactly as their endSwing does when it is missing. */
   boolean cutState(@Nullable Object player, Object state, long gameTime) throws ReflectiveOperationException {
      if (!this.swinging.getBoolean(state)) {
         return false;
      }
      if (this.endSwing != null) {
         this.endSwing.invoke(null, player, state, true);
         return true;
      }
      this.swinging.setBoolean(state, false);
      if (this.swingGame != null) {
         this.swingGame.setLong(state, gameTime);
      }
      if (this.noFallUntil != null && this.clock != null) {
         this.noFallUntil.setLong(state, Math.max(this.noFallUntil.getLong(state), this.clock.getLong(state) + 60L));
      }
      if (this.startCooldown != null) {
         this.startCooldown.invoke(state, "swing", 6);
      }
      this.revision.setInt(state, this.revision.getInt(state) + 1);
      return true;
   }

   @SuppressWarnings("unchecked")
   void releaseData(@Nullable Object player, @Nullable Object data) throws ReflectiveOperationException {
      if (data == null) {
         return;
      }
      List<Object> held = new ArrayList<>();
      if (this.dataHeld != null) {
         held.addAll((Set<Object>)this.dataHeld.get(data));
      } else if (this.slots.containsKey(SpiderAbility.SWING)) {
         held.add(this.slots.get(SpiderAbility.SWING));
      }
      for (Object s : held) {
         this.handleInput.invoke(null, player, s, false);
      }
      this.handlePrimary.invoke(null, player, false);
   }

   boolean driveRaw(@Nullable Object player, SpiderAbility a, boolean down) throws ReflectiveOperationException {
      Object s = this.slots.get(a);
      if (s == null) {
         return false;
      }
      this.handleInput.invoke(null, player, s, down);
      return true;
   }

   int kindOf(Object web) throws ReflectiveOperationException {
      return this.kind == null ? 0 : (Integer)this.kind.invoke(web);
   }

   // ---- the bridge -------------------------------------------------------------------------------------------

   @Override
   public boolean isSpider(Player player) {
      if (!on(SpiderFeature.DETECT_HERO) || player == null) {
         return false;
      }
      try {
         return isSpiderData(dataOf(player));
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.DETECT_HERO, e);
         return false;
      }
   }

   @Override
   public Optional<SpiderLine> line(Player player) {
      if (!on(SpiderFeature.LINE_STATE) || player == null || player.level().isClientSide) {
         return Optional.empty();
      }
      try {
         Object data = dataOf(player);
         if (!isSpiderData(data)) {
            return Optional.empty();
         }
         Object state = spiderStateOf(data);
         if (state == null) {
            return Optional.empty();
         }
         Vec3 handPos = player.getEyePosition().add(0.0, -0.35, 0.0);
         if (this.hand != null) {
            Object h = this.hand.invoke(null, player);
            if (h instanceof Vec3 v) {
               handPos = v;
            }
         }
         return Optional.ofNullable(lineOf(state, handPos));
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.LINE_STATE, e);
         return Optional.empty();
      }
   }

   @Override
   public boolean cutLine(ServerPlayer spider) {
      if (!on(SpiderFeature.CUT_LINE) || spider == null) {
         return false;
      }
      try {
         Object data = dataOf(spider);
         Object state = isSpiderData(data) ? spiderStateOf(data) : null;
         return state != null && cutState(spider, state, spider.level().getGameTime());
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.CUT_LINE, e);
         return false;
      }
   }

   @Override
   public boolean isWebProjectile(Entity entity) {
      if (entity == null) {
         return false;
      }
      if (on(SpiderFeature.WEB_PROJECTILE) && this.webClass.isInstance(entity)) {
         return true;
      }
      ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
      return key != null && WEB_TYPE.equals(key.toString());
   }

   @Override
   public WebKind webKind(Entity entity) {
      if (!isWebProjectile(entity)) {
         return WebKind.UNKNOWN;
      }
      if (this.kind == null || this.webClass == null || !this.webClass.isInstance(entity)) {
         return WebKind.PELLET;
      }
      try {
         return WebKind.of(kindOf(entity));
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.WEB_PROJECTILE, e);
         return WebKind.PELLET;
      }
   }

   @Override
   public int webStacks(LivingEntity body) {
      if (!on(SpiderFeature.WEB_STATUS) || body == null) {
         return 0;
      }
      try {
         return (Integer)this.webStacksM.invoke(null, body);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.WEB_STATUS, e);
         return 0;
      }
   }

   @Override
   public boolean cocooned(LivingEntity body) {
      if (!on(SpiderFeature.WEB_STATUS) || body == null) {
         return false;
      }
      try {
         return (Boolean)this.cocoonedM.invoke(null, body);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.WEB_STATUS, e);
         return false;
      }
   }

   /** The tag names, read once from their constants (the class is initialised by their mod long before a fight). */
   String[] tags() throws ReflectiveOperationException {
      if (this.webTagValue == null) {
         Object w = this.webTag.get(null);
         Object c = this.cocoonTag.get(null);
         this.webTagValue = w instanceof String s && !s.isEmpty() ? s : WEB_TAG_FALLBACK;
         this.cocoonTagValue = c instanceof String s && !s.isEmpty() ? s : COCOON_TAG_FALLBACK;
      }
      return new String[]{this.webTagValue, this.cocoonTagValue};
   }

   @Override
   public boolean tearWeb(LivingEntity body) {
      if (!on(SpiderFeature.TEAR_WEB) || body == null || body.level().isClientSide) {
         return false;
      }
      try {
         String[] tags = tags();
         CompoundTag pd = body.getPersistentData();
         boolean had = pd.contains(tags[0]) || pd.getLong(tags[1]) > body.level().getGameTime();
         pd.remove(tags[0]);
         pd.remove(tags[1]);
         MobEffect stun = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(STUN));
         if (stun != null && body.hasEffect(stun)) {
            body.removeEffect(stun);
            had = true;
         }
         MobEffectInstance slow = body.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
         if (had && slow != null && slow.getDuration() <= 100) {
            body.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); // their web slow (60 t, or the cocoon's level 5)
         }
         return had;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.TEAR_WEB, e);
         return false;
      }
   }

   @Override
   public void releaseInputs(ServerPlayer spider) {
      if (!on(SpiderFeature.INPUT_RELEASE) || spider == null) {
         return;
      }
      try {
         Object data = dataOf(spider);
         if (isSpiderData(data)) {
            releaseData(spider, data);
         }
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.INPUT_RELEASE, e);
      }
   }

   @Override
   public boolean drive(ServerPlayer spider, SpiderAbility ability, boolean down) {
      if (!on(SpiderFeature.DRIVE_ABILITIES) || spider == null || !isSpider(spider)) {
         return false;
      }
      try {
         return driveRaw(spider, ability, down);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.DRIVE_ABILITIES, e);
         return false;
      }
   }

   @Override
   public boolean drivePrimary(ServerPlayer spider, boolean down) {
      if (!on(SpiderFeature.DRIVE_ABILITIES) || spider == null || !isSpider(spider)) {
         return false;
      }
      try {
         this.handlePrimary.invoke(null, spider, down);
         return true;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.DRIVE_ABILITIES, e);
         return false;
      }
   }

   @Override
   @Nullable
   public Vec3 wallNormal(Player player) {
      if (!on(SpiderFeature.WALL_STATE) || player == null || !isSpider(player)) {
         return null;
      }
      try {
         return this.wallNormal.invoke(null, player) instanceof Vec3 v ? v : null;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         fail(SpiderFeature.WALL_STATE, e);
         return null;
      }
   }

   @Override
   @Nullable
   public String inputChannel() {
      return on(SpiderFeature.INPUT_GATE) ? CHANNEL : null;
   }
}
