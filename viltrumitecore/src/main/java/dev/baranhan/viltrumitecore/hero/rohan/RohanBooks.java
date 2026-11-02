package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.entity.directive.Directive;
import dev.baranhan.viltrumitecore.entity.directive.DirectiveEffect;
import dev.baranhan.viltrumitecore.entity.directive.MobDirectives;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.HeroTargets;
import dev.baranhan.viltrumitecore.hero.book.BookAccess;
import dev.baranhan.viltrumitecore.hero.book.BookClass;
import dev.baranhan.viltrumitecore.hero.book.BookPages;
import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import dev.baranhan.viltrumitecore.hero.book.BookProfiles;
import dev.baranhan.viltrumitecore.hero.book.BookReader;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.AttackKind;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instruction;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.InstructionContext;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.InstructionParam;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.Instructions;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.RohanBookS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.RohanFxS2CPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Heaven's Door book sessions (docs/rohan/heavens-door-design.md §4–§6): opening bodies as books, sending the true
 * pages, validating and applying written lines, ripping pages, struggling, closing. Server authoritative: the client
 * only names a registered instruction and an index into server-made lists.
 */
public final class RohanBooks {
   /** Instruction id of the OPEN directive (lang key book.viltrumitecore.instruction.open). */
   public static final String OPEN_ID = "open";
   public static final String RIP_ID = "rip";
   public static final String REFLECT_ID = "reflect";
   static final int UPDATE_INTERVAL = 5;
   static final int MAX_CANDIDATES = 8;
   private static final Map<UUID, BookSession> SESSIONS = new HashMap<>();
   /** Bodies each Rohan has written into (to lift his lines when he leaves). */
   private static final Map<UUID, Set<UUID>> WRITTEN = new HashMap<>();
   private static int nextId = 1;

   private RohanBooks() {
   }

   // ------------------------------------------------------------------ queries

   @Nullable
   static BookSession session(ServerPlayer reader) {
      return SESSIONS.get(reader.getUUID());
   }

   public static boolean reading(ServerPlayer reader) {
      return SESSIONS.containsKey(reader.getUUID());
   }

   /** Entity id of the primary open book of this reader (-1 = none). */
   public static int primaryTarget(ServerPlayer reader) {
      BookSession s = SESSIONS.get(reader.getUUID());
      BookSession.Book b = s == null ? null : s.primary();
      return b == null ? -1 : b.entityId;
   }

   /** Why this body's book cannot be opened by this reader now (lang key under book.viltrumitecore.refuse.), or null. */
   @Nullable
   public static String openRefusal(ServerPlayer reader, LivingEntity target, BookOrigin origin, BookProfile profile) {
      if (target == reader || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand || target.level() != reader.level()) {
         return "invalid";
      }
      if (target instanceof Player p) {
         if (!ViltrumiteCoreConfig.INSTANCE.heavensDoorPlayerReading) {
            return "players_forbidden";
         }
         if (p.isCreative() || !HeroTargets.canHit(reader, p)) {
            return "protected";
         }
      }
      if (profile.access() == BookAccess.CLOSED) {
         return "closed";
      }
      if (origin.sight && profile.sightImmune()) {
         return "sight_immune";
      }
      if (origin.sight && blind(target)) {
         return "blind";
      }
      if (origin == BookOrigin.TOUCH && profile.guardsFront() && target instanceof ServerPlayer sp
         && HeroRegistry.get(sp).guardFaces(sp, reader.getEyePosition())) {
         return "guarded";
      }
      return null;
   }

   /** Closed eyes, blindness, darkness or sleep: the drawing is never seen. */
   static boolean blind(LivingEntity e) {
      return e.isSleeping() || e.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || e.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
   }

   // ------------------------------------------------------------------ opening

   /**
    * Opens these bodies' books for {@code reader}. A sight session (sketch, manuscript) adds to an open session of the
    * same origin; anything else replaces it. Returns how many books opened.
    */
   public static int open(ServerPlayer reader, List<LivingEntity> targets, BookOrigin origin, int windowOverride, int linesOverride) {
      RohanState state = RohanState.of(reader);
      if (state == null || targets.isEmpty()) {
         return 0;
      }
      BookSession session = SESSIONS.get(reader.getUUID());
      if (session != null && (session.origin != origin || !origin.sight)) {
         close(reader, session, false);
         session = null;
      }
      boolean fresh = session == null;
      if (fresh) {
         session = new BookSession(nextId++, reader.getUUID(), origin);
      }
      int opened = 0;
      String lastRefusal = null;
      LivingEntity refused = null;
      for (LivingEntity target : targets) {
         if (session.book(target.getUUID()) != null) {
            continue;
         }
         BookProfile profile = BookProfiles.resolve(target);
         String refusal = openRefusal(reader, target, origin, profile);
         if (refusal != null) {
            lastRefusal = refusal;
            refused = target;
            continue;
         }
         int window = windowOverride > 0 ? windowOverride : profile.windowTicks();
         MobDirectives.apply(target, new Directive(OPEN_ID, DirectiveEffect.OPEN.bit(), reader.getUUID(), null, null, null,
            reader.level().getGameTime() + window, Directive.BREAK_ON_AUTHOR_HIT, null));
         session.books.add(new BookSession.Book(target.getUUID(), target.getId(), profile));
         session.ticksLeft = Math.max(session.ticksLeft, window);
         opened++;
         inspire(reader, state, target, profile);
         fx(reader, RohanFxS2CPacket.BOOK_OPEN, target, window);
         if (target instanceof ServerPlayer victim) {
            CoreMessages.sendToPlayer(RohanBookS2CPacket.victim(session.id, reader.getId(), window), victim);
            HeroRegistry.get(victim).onBookOpened(victim, reader, profile);
         }
         if (profile.access() == BookAccess.READ_ONLY) {
            fx(reader, RohanFxS2CPacket.RESIST, target, 20);
         }
      }
      if (opened == 0) {
         if (fresh) {
            refuse(reader, refused, lastRefusal == null ? "invalid" : lastRefusal);
         }
         return 0;
      }
      if (fresh) {
         int lines = 0;
         for (BookSession.Book b : session.books) {
            lines = Math.max(lines, b.profile.writable() ? b.profile.lines() : 0);
         }
         session.linesLeft = linesOverride >= 0 && lines > 0 ? linesOverride : lines;
         SESSIONS.put(reader.getUUID(), session);
      }
      RohanSounds.play(reader, RohanSounds.BOOK_OPEN, 1.0F, RohanSounds.jitter(reader, 1.0F));
      state.targetId = session.primary() == null ? -1 : session.primary().entityId;
      sendOpen(reader, session);
      return opened;
   }

   private static void inspire(ServerPlayer reader, RohanState state, LivingEntity target, BookProfile profile) {
      String key = BookReader.typeKey(target);
      boolean first = state.read.add(key);
      state.inspiration = Math.min(RohanRules.INSPIRATION_MAX, state.inspiration + RohanRules.inspiration(first, profile.bookClass(), target instanceof Player));
      if (first) {
         HeroSpeech.say(reader, RohanLines.READ);
      }
   }

   static void sendOpen(ServerPlayer reader, BookSession session) {
      BookSession.Book primary = session.primary();
      if (primary == null || !(reader.level() instanceof ServerLevel level)) {
         return;
      }
      LivingEntity target = level.getEntity(primary.uuid) instanceof LivingEntity l ? l : null;
      if (target == null) {
         return;
      }
      RohanState state = RohanState.ensure(reader);
      RohanBookS2CPacket p = new RohanBookS2CPacket(RohanBookS2CPacket.OPEN, session.id);
      p.targetId = target.getId();
      p.targets = session.books.stream().mapToInt(b -> b.entityId).toArray();
      p.targetName = session.books.size() > 1 ? target.getName().getString() + " +" + (session.books.size() - 1) : target.getName().getString();
      p.pages = BookReader.read(target, primary.profile, RohanChronicle.of(target.getUUID()));
      p.ticks = session.ticksLeft;
      p.lines = session.linesLeft;
      p.bookFlags = (primary.profile.access() == BookAccess.READ_ONLY ? 1 : 0) | (primary.profile.partialReading() ? 2 : 0) | (session.mass() ? 4 : 0);
      p.ink = state.ink;
      // parameter lists first, so the refusals can be judged against a real context
      session.candidates.clear();
      session.points.clear();
      session.pointLabels.clear();
      session.candidates.add(reader.getId());
      p.candidates.add(new RohanBookS2CPacket.Candidate(reader.getId(), reader.getName().getString()));
      List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(16.0),
         e -> e != target && e != reader && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator());
      near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));
      for (int i = 0; i < near.size() && session.candidates.size() < MAX_CANDIDATES; i++) {
         session.candidates.add(near.get(i).getId());
         p.candidates.add(new RohanBookS2CPacket.Candidate(near.get(i).getId(), near.get(i).getName().getString()));
      }
      addPoint(session, p, reader.blockPosition(), "book.viltrumitecore.point.rohan");
      BlockHitResult look = level.clip(new ClipContext(reader.getEyePosition(), reader.getEyePosition().add(reader.getLookAngle().scale(32.0)),
         ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, reader));
      if (look.getType() == HitResult.Type.BLOCK) {
         addPoint(session, p, look.getBlockPos().relative(look.getDirection()), "book.viltrumitecore.point.look");
      }
      Vec3 away = target.position().subtract(reader.position()).multiply(1, 0, 1);
      away = away.lengthSqr() < 1.0E-4 ? reader.getLookAngle().multiply(1, 0, 1) : away.normalize();
      BlockPos far = BlockPos.containing(target.position().add(away.scale(16.0)));
      addPoint(session, p, level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far),
         "book.viltrumitecore.point.away");
      for (Instruction instruction : Instructions.all()) {
         InstructionContext ctx = new InstructionContext(reader, target, primary.profile, null, null, null, level.getGameTime(), primary.struggles);
         String refusal = globalRefusal(reader, target, primary.profile);
         if (refusal == null) {
            refusal = instruction.refusal(ctx);
         }
         p.entries.add(new RohanBookS2CPacket.Entry(instruction.id(), instruction.param().ordinal(), RohanRules.lineCost(instruction.inkCost(),
            primary.profile), refusal == null ? "" : refusal));
      }
      CoreMessages.sendToPlayer(p, reader);
   }

   private static void addPoint(BookSession session, RohanBookS2CPacket p, BlockPos pos, String label) {
      session.points.add(pos);
      RohanBookS2CPacket.Point point = new RohanBookS2CPacket.Point(pos, label);
      session.pointLabels.add(point);
      p.points.add(point);
   }

   /** Refusals that hold for every line of this book. */
   @Nullable
   static String globalRefusal(ServerPlayer reader, LivingEntity target, BookProfile profile) {
      if (profile.access() != BookAccess.OPEN || profile.lines() <= 0) {
         return "read_only";
      }
      if (target instanceof Player && !ViltrumiteCoreConfig.INSTANCE.heavensDoorPlayerInstructions) {
         return "players_forbidden";
      }
      return null;
   }

   // ------------------------------------------------------------------ client input

   public static void onClient(ServerPlayer sender, int op, int sessionId, String instruction, int param) {
      if (op == dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket.STRUGGLE) {
         struggle(sender, sessionId);
         return;
      }
      BookSession session = SESSIONS.get(sender.getUUID());
      if (session == null || session.id != sessionId) {
         return;
      }
      switch (op) {
         case dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket.WRITE -> write(sender, session, instruction, param);
         case dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket.RIP -> rip(sender, session);
         case dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket.CLOSE -> close(sender, session, true);
         case dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket.FLIP -> {
            if (session.age % 4 == 0 || session.age < 4) {
               RohanSounds.play(sender, RohanSounds.PAGE_FLIP, 0.6F, RohanSounds.jitter(sender, 1.0F));
            }
         }
         default -> {
         }
      }
   }

   static void write(ServerPlayer reader, BookSession session, String id, int param) {
      RohanState state = RohanState.of(reader);
      Instruction instruction = Instructions.byId(id);
      if (state == null || instruction == null || session.closing >= 0 || !(reader.level() instanceof ServerLevel level)) {
         return;
      }
      if (session.linesLeft <= 0) {
         result(reader, session, false, "no_lines");
         return;
      }
      LivingEntity subject = null;
      BlockPos point = null;
      AttackKind kind = null;
      if (instruction.param() == InstructionParam.ENTITY && param >= 0 && param < session.candidates.size()) {
         Entity e = level.getEntity(session.candidates.get(param));
         subject = e instanceof LivingEntity l && l.isAlive() && l.distanceTo(reader) < 48.0 ? l : null;
      } else if (instruction.param() == InstructionParam.POINT && param >= 0 && param < session.points.size()) {
         point = session.points.get(param);
      } else if (instruction.param() == InstructionParam.KIND) {
         kind = AttackKind.byIndex(param);
      }
      List<LivingEntity> targets = new ArrayList<>();
      List<BookSession.Book> books = new ArrayList<>();
      for (BookSession.Book b : session.books) {
         if (level.getEntity(b.uuid) instanceof LivingEntity l && l.isAlive()) {
            targets.add(l);
            books.add(b);
         }
      }
      if (targets.isEmpty()) {
         close(reader, session, false);
         return;
      }
      float cost = 0.0F;
      for (BookSession.Book b : books) {
         cost = Math.max(cost, RohanRules.lineCost(instruction.inkCost(), b.profile));
      }
      if (state.ink + 1.0E-3F < cost) {
         result(reader, session, false, "no_ink");
         RohanSounds.play(reader, RohanSounds.REFUSE, 0.6F, 1.0F);
         return;
      }
      int took = 0;
      String refusal = null;
      for (int i = 0; i < targets.size(); i++) {
         LivingEntity target = targets.get(i);
         BookSession.Book book = books.get(i);
         InstructionContext ctx = new InstructionContext(reader, target, book.profile, subject, point, kind, level.getGameTime(), book.struggles);
         String r = globalRefusal(reader, target, book.profile);
         if (r == null) {
            r = instruction.refusal(ctx);
         }
         if (r == null) {
            r = instruction.refusalWithParam(ctx);
         }
         if (r != null) {
            refusal = r;
            if ("read_only".equals(r) && book.profile.reflectTicks() > 0) {
               reflect(reader, target, book.profile);
            }
            continue;
         }
         if (book.profile.resistChance() > 0.0F && reader.getRandom().nextFloat() < book.profile.resistChance()) {
            refusal = "resisted";
            fx(reader, RohanFxS2CPacket.RESIST, target, 20);
            RohanSounds.play(target, RohanSounds.RESIST, 0.8F, 1.0F);
            if (book.profile.bookClass() == BookClass.BOSS) {
               reader.hurt(reader.damageSources().indirectMagic(target, target), RohanRules.BACKLASH_DAMAGE);
               Vec3 push = reader.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(0.8);
               reader.push(push.x, 0.25, push.z);
               reader.hurtMarked = true;
            }
            continue;
         }
         int ticks = RohanRules.lineTicks(instruction.baseTicks(book.profile.bookClass()), book.profile, book.struggles);
         instruction.apply(ctx, ticks);
         WRITTEN.computeIfAbsent(reader.getUUID(), k -> new HashSet<>()).add(target.getUUID());
         fx(reader, RohanFxS2CPacket.BOOK_WRITE, target, ticks);
         RohanChronicle.record(target.getUUID(), "book.viltrumitecore.line.written", "@book.viltrumitecore.instruction." + instruction.id());
         took++;
      }
      state.ink = Math.max(0.0F, state.ink - cost);
      state.idle = 0;
      session.linesLeft--;
      state.start(RohanMove.WRITE);
      RohanSounds.play(reader, RohanSounds.WRITE, 0.9F, RohanSounds.jitter(reader, 1.0F));
      if (took > 0) {
         if (instruction == Instructions.SAFETY_LOCK) {
            HeroSpeech.say(reader, RohanLines.SAFETY);
         }
         result(reader, session, true, "written", "@book.viltrumitecore.instruction." + instruction.id(), Integer.toString(took));
      } else {
         HeroSpeech.say(reader, RohanLines.REFUSED);
         result(reader, session, false, refusal == null ? "not_applicable" : refusal);
      }
      if (session.linesLeft <= 0) {
         session.closing = RohanRules.CLOSE_DELAY;
      }
      update(reader, session);
   }

   /** Writing into a read-only book: the book writes back "cannot attack <owner>" into the reader. */
   static void reflect(ServerPlayer reader, LivingEntity owner, BookProfile profile) {
      MobDirectives.apply(reader, new Directive(REFLECT_ID, DirectiveEffect.FORGET_TARGET.bit(), owner.getUUID(), owner.getUUID(), null, null,
         reader.level().getGameTime() + profile.reflectTicks(), 0, null));
      fx(reader, RohanFxS2CPacket.REFLECT, reader, profile.reflectTicks());
      RohanSounds.play(reader, RohanSounds.RESIST, 1.0F, 0.7F);
   }

   static void rip(ServerPlayer reader, BookSession session) {
      RohanState state = RohanState.of(reader);
      BookSession.Book book = session.primary();
      if (state == null || book == null || session.mass() || session.closing >= 0 || !(reader.level() instanceof ServerLevel level)) {
         return;
      }
      LivingEntity target = level.getEntity(book.uuid) instanceof LivingEntity l ? l : null;
      if (target == null) {
         return;
      }
      String refusal = globalRefusal(reader, target, book.profile);
      if (refusal == null && session.linesLeft <= 0) {
         refusal = "no_lines";
      }
      if (refusal == null && state.ink < RohanRules.COST_RIP) {
         refusal = "no_ink";
      }
      if (refusal != null) {
         result(reader, session, false, refusal);
         return;
      }
      int ticks = RohanRules.lineTicks(RohanRules.RIP_TICKS, book.profile, book.struggles);
      MobDirectives.apply(target, new Directive(RIP_ID, DirectiveEffect.RIPPED.bit(), reader.getUUID(), null, null, null,
         level.getGameTime() + ticks, Directive.RESTORE_ON_HEAVY, null));
      WRITTEN.computeIfAbsent(reader.getUUID(), k -> new HashSet<>()).add(target.getUUID());
      target.hurt(level.damageSources().magic(), RohanRules.RIP_DAMAGE);
      state.ink -= RohanRules.COST_RIP;
      session.linesLeft--;
      state.start(RohanMove.RIP);
      fx(reader, RohanFxS2CPacket.PAGE_RIP, target, ticks);
      RohanSounds.play(target, RohanSounds.PAGE_RIP, 1.0F, RohanSounds.jitter(target, 1.0F));
      result(reader, session, true, "ripped");
      if (session.linesLeft <= 0) {
         session.closing = RohanRules.CLOSE_DELAY;
      }
      update(reader, session);
   }

   /** An opened player fights the book: shorter window, shorter lines (rate-limited). */
   static void struggle(ServerPlayer victim, int sessionId) {
      for (BookSession session : SESSIONS.values()) {
         if (session.id != sessionId) {
            continue;
         }
         BookSession.Book book = session.book(victim.getUUID());
         if (book == null) {
            return;
         }
         int second = victim.tickCount / 20;
         if (book.pressSecond != second) {
            book.pressSecond = second;
            book.pressCount = 0;
         }
         if (++book.pressCount > RohanRules.STRUGGLE_PER_SECOND) {
            return;
         }
         book.struggles++;
         Directive open = openDirective(victim, session.reader);
         if (open != null) {
            MobDirectives.apply(victim, open.withExpiry(open.expiresAt() - RohanRules.STRUGGLE_WINDOW));
         }
         if (session.books.size() == 1) {
            session.ticksLeft = Math.max(0, session.ticksLeft - RohanRules.STRUGGLE_WINDOW);
         }
         return;
      }
   }

   @Nullable
   static Directive openDirective(LivingEntity target, UUID reader) {
      for (Directive d : MobDirectives.active(target)) {
         if (d.has(DirectiveEffect.OPEN) && d.author().equals(reader)) {
            return d;
         }
      }
      return null;
   }

   // ------------------------------------------------------------------ ticking and closing

   public static void tick(MinecraftServer server) {
      if (SESSIONS.isEmpty()) {
         return;
      }
      Iterator<Map.Entry<UUID, BookSession>> it = SESSIONS.entrySet().iterator();
      List<Runnable> closes = new ArrayList<>();
      while (it.hasNext()) {
         BookSession session = it.next().getValue();
         ServerPlayer reader = server.getPlayerList().getPlayer(session.reader);
         if (reader == null || RohanState.of(reader) == null || !reader.isAlive()) {
            closes.add(() -> closeQuiet(server, session));
            it.remove();
            continue;
         }
         session.age++;
         ServerLevel level = reader.serverLevel();
         session.books.removeIf(b -> {
            Entity e = level.getEntity(b.uuid);
            boolean gone = !(e instanceof LivingEntity l) || !l.isAlive() || l.distanceTo(reader) > RohanRules.BOOK_KEEP_RANGE
               || openDirective(l, reader.getUUID()) == null;
            if (gone && e instanceof LivingEntity l) {
               shut(reader, l);
            }
            return gone;
         });
         if (session.books.isEmpty()) {
            closes.add(() -> close(reader, session, false));
            continue;
         }
         session.ticksLeft--;
         if (session.closing >= 0 && --session.closing < 0 || session.ticksLeft <= 0) {
            closes.add(() -> close(reader, session, false));
            continue;
         }
         float burn = 0.0F;
         for (BookSession.Book b : session.books) {
            burn = Math.max(burn, b.profile.burnPerSecond());
         }
         if (burn > 0.0F && session.age % 20 == 0) {
            reader.hurt(reader.damageSources().onFire(), burn);
         }
         if (session.age % UPDATE_INTERVAL == 0) {
            update(reader, session);
         }
      }
      closes.forEach(Runnable::run);
   }

   static void update(ServerPlayer reader, BookSession session) {
      RohanState state = RohanState.of(reader);
      CoreMessages.sendToPlayer(RohanBookS2CPacket.update(session.id, session.ticksLeft, session.linesLeft, state == null ? 0.0F : state.ink), reader);
   }

   /** Closes the reader's session (if any). */
   public static void close(ServerPlayer reader) {
      BookSession session = SESSIONS.get(reader.getUUID());
      if (session != null) {
         close(reader, session, true);
      }
   }

   static void close(ServerPlayer reader, BookSession session, boolean byReader) {
      SESSIONS.remove(reader.getUUID(), session);
      if (reader.level() instanceof ServerLevel level) {
         for (BookSession.Book b : session.books) {
            if (level.getEntity(b.uuid) instanceof LivingEntity l) {
               shut(reader, l);
            }
         }
      }
      session.books.clear();
      CoreMessages.sendToPlayer(RohanBookS2CPacket.close(session.id), reader);
      RohanSounds.play(reader, RohanSounds.BOOK_CLOSE, 0.9F, RohanSounds.jitter(reader, 1.0F));
      RohanState state = RohanState.of(reader);
      if (state != null) {
         state.targetId = -1;
      }
   }

   private static void closeQuiet(MinecraftServer server, BookSession session) {
      for (ServerLevel level : server.getAllLevels()) {
         for (BookSession.Book b : session.books) {
            if (level.getEntity(b.uuid) instanceof LivingEntity l) {
               MobDirectives.remove(l, d -> d.id().equals(OPEN_ID) && d.author().equals(session.reader));
               fxAt(l, RohanFxS2CPacket.BOOK_CLOSE, -1, l, 0);
               if (l instanceof ServerPlayer victim) {
                  CoreMessages.sendToPlayer(RohanBookS2CPacket.victimEnd(session.id), victim);
               }
            }
         }
      }
      session.books.clear();
   }

   private static void shut(ServerPlayer reader, LivingEntity target) {
      MobDirectives.remove(target, d -> d.id().equals(OPEN_ID) && d.author().equals(reader.getUUID()));
      fx(reader, RohanFxS2CPacket.BOOK_CLOSE, target, 0);
      if (target instanceof ServerPlayer victim) {
         BookSession s = SESSIONS.get(reader.getUUID());
         CoreMessages.sendToPlayer(RohanBookS2CPacket.victimEnd(s == null ? 0 : s.id), victim);
      }
   }

   /**
    * Rohan leaves (death, logout, hero change, other dimension): his session closes and every line he wrote is lifted
    * from the bodies the server still has loaded (unloaded ones run out by game time).
    */
   public static void release(ServerPlayer reader) {
      close(reader);
      Set<UUID> written = WRITTEN.remove(reader.getUUID());
      if (written == null || reader.getServer() == null) {
         return;
      }
      for (ServerLevel level : reader.getServer().getAllLevels()) {
         for (UUID id : written) {
            if (level.getEntity(id) instanceof LivingEntity l) {
               MobDirectives.remove(l, d -> d.author().equals(reader.getUUID()));
            }
         }
      }
   }

   /** Canon: badly hurt, Rohan loses his grip: lines in players, heroes and bosses return to normal. */
   public static void releaseStrong(ServerPlayer reader) {
      Set<UUID> written = WRITTEN.get(reader.getUUID());
      if (written == null || !(reader.level() instanceof ServerLevel level)) {
         return;
      }
      for (UUID id : written) {
         if (level.getEntity(id) instanceof LivingEntity l && (l instanceof Player || BookProfiles.resolve(l).bookClass() == BookClass.BOSS)) {
            MobDirectives.remove(l, d -> d.author().equals(reader.getUUID()) && !d.id().equals(OPEN_ID));
         }
      }
   }

   /** Rohan hits a body he made safe: its Safety Lock is halved (and an open book closes, see BREAK_ON_AUTHOR_HIT). */
   public static void onRohanHit(ServerPlayer reader, LivingEntity target) {
      long now = reader.level().getGameTime();
      for (Directive d : MobDirectives.active(target)) {
         if (d.author().equals(reader.getUUID()) && d.has(DirectiveEffect.SAFETY_LOCK)) {
            long left = d.expiresAt() - now;
            MobDirectives.apply(target, d.withExpiry(now + (long)(left * RohanRules.SAFETY_HIT_FACTOR)));
         }
      }
   }

   public static void clearAll() {
      SESSIONS.clear();
      WRITTEN.clear();
   }

   // ------------------------------------------------------------------ feedback

   static void result(ServerPlayer reader, BookSession session, boolean ok, String key, String... args) {
      CoreMessages.sendToPlayer(RohanBookS2CPacket.result(session.id, ok, key, args), reader);
   }

   static void refuse(ServerPlayer reader, @Nullable LivingEntity target, String reason) {
      reader.displayClientMessage(net.minecraft.network.chat.Component.translatable("book.viltrumitecore.refuse." + reason), true);
      RohanSounds.play(reader, RohanSounds.REFUSE, 0.7F, 1.0F);
      if (target != null && ("guarded".equals(reason) || "closed".equals(reason))) {
         fx(reader, "guarded".equals(reason) ? RohanFxS2CPacket.GUARDED : RohanFxS2CPacket.RESIST, target, 20);
      }
   }

   static void fx(ServerPlayer reader, int kind, LivingEntity target, int ticks) {
      fxAt(target, kind, reader.getId(), target, ticks);
   }

   static void fxAt(Entity tracked, int kind, int sourceId, Entity target, int ticks) {
      CoreMessages.sendToTrackingAndSelf(new RohanFxS2CPacket(kind, sourceId, target.getId(), ticks, target.getEyePosition()), tracked);
   }
}
