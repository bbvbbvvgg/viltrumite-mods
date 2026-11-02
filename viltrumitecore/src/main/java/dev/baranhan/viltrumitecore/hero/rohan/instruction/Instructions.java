package dev.baranhan.viltrumitecore.hero.rohan.instruction;

import dev.baranhan.viltrumitecore.entity.directive.Directive;
import dev.baranhan.viltrumitecore.entity.directive.DirectiveEffect;
import dev.baranhan.viltrumitecore.entity.directive.MobDirectives;
import dev.baranhan.viltrumitecore.hero.book.BookClass;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Registry of Heaven's Door lines (docs/rohan/heavens-door-design.md §5 table). Ids are stable (saved in directives,
 * lang keys); new lines are registered with {@link #register} (an addon may add its own). Durations: bodies / bosses /
 * players, in ticks.
 */
public final class Instructions {
   private static final Map<String, Instruction> REGISTRY = new LinkedHashMap<>();

   public static final Instruction SAFETY_LOCK = register(new DirectiveInstruction("safety_lock", InstructionParam.NONE, 10.0F,
      DirectiveEffect.SAFETY_LOCK.bit(), 0, 1200, 80, 100, null, null));
   public static final Instruction FORGET_TARGET = register(new DirectiveInstruction("forget_target", InstructionParam.NONE, 10.0F,
      DirectiveEffect.FORGET_TARGET.bit(), 0, 400, 60, 0, ctx -> ctx.target() instanceof Mob mob && mob.getTarget() != null, "no_target") {
      @Override
      protected java.util.UUID subjectId(InstructionContext ctx) {
         return ctx.target() instanceof Mob mob && mob.getTarget() != null ? mob.getTarget().getUUID() : null;
      }
   });
   public static final Instruction KEEP_AWAY = register(new DirectiveInstruction("keep_away", InstructionParam.NONE, 10.0F,
      DirectiveEffect.mask(DirectiveEffect.KEEP_AWAY, DirectiveEffect.SAFETY_LOCK), 0, 600, 60, 0, Instructions::walks, "cannot_walk"));
   public static final Instruction STAND_STILL = register(new DirectiveInstruction("stand_still", InstructionParam.NONE, 15.0F,
      DirectiveEffect.ROOT.bit(), Directive.BREAK_ON_HURT, 200, 30, 20, null, null));
   public static final Instruction SPARE_VILLAGERS = register(new DirectiveInstruction("spare_villagers", InstructionParam.NONE, 10.0F,
      DirectiveEffect.SPARE_PEACEFUL.bit(), 0, 2400, 80, 0, null, null));
   public static final Instruction GUARD = register(new DirectiveInstruction("guard", InstructionParam.ENTITY, 15.0F,
      DirectiveEffect.GUARD.bit(), Directive.BREAK_ON_AUTHOR_HIT, 1200, 0, 0, Instructions::walks, "cannot_walk") {
      @Override
      public String refusalWithParam(InstructionContext ctx) {
         String base = super.refusalWithParam(ctx);
         return base != null ? base : ctx.subject() == ctx.target() ? "guard_self" : null;
      }
   });
   public static final Instruction GO_TO = register(new DirectiveInstruction("go_to", InstructionParam.POINT, 10.0F,
      DirectiveEffect.GO_TO.bit(), Directive.BREAK_ON_AUTHOR_HIT, 600, 0, 0, Instructions::walks, "cannot_walk"));
   public static final Instruction NO_ATTACK = register(new DirectiveInstruction("no_attack", InstructionParam.KIND, 15.0F,
      0, 0, 400, 60, 80, null, null));
   public static final Instruction FLY_BACKWARDS = register(new DirectiveInstruction("fly_backwards", InstructionParam.NONE, 10.0F,
      DirectiveEffect.FLY.bit(), 0, 20, 10, 10, ctx -> dev.baranhan.viltrumitecore.hero.HeroRegistry.allowsImpulse(ctx.target()), "too_heavy") {
      @Override
      public void apply(InstructionContext ctx, int ticks) {
         // the flight is short by nature: 1 s on bodies, 0.5 s on bosses and players
         int t = Math.min(ticks, this.baseTicks(ctx.profile().bookClass()));
         MobDirectives.apply(ctx.target(), new Directive(this.id, this.effects, ctx.author().getUUID(), null, null,
            MobDirectives.backwards(ctx.target(), dev.baranhan.viltrumitecore.hero.rohan.RohanRules.SELF_EDIT_SPEED), ctx.gameTime() + t, 0, null));
      }
   });
   public static final Instruction FOLLOW_ROHAN = register(new DirectiveInstruction("follow_rohan", InstructionParam.NONE, 15.0F,
      DirectiveEffect.mask(DirectiveEffect.FOLLOW, DirectiveEffect.SAFETY_LOCK), Directive.BREAK_ON_AUTHOR_HIT, 900, 0, 0, Instructions::walks,
      "cannot_walk"));
   public static final Instruction CALM = register(new DirectiveInstruction("calm", InstructionParam.NONE, 15.0F,
      DirectiveEffect.CALM.bit(), Directive.BREAK_ON_HURT, 600, 0, 0, null, null));
   public static final Instruction FORGET_FACES = register(new DirectiveInstruction("forget_faces", InstructionParam.NONE, 20.0F,
      DirectiveEffect.FORGET_FACES.bit(), 0, 1200, 1200, 0, ctx -> MobDirectives.supports(ctx.target(), DirectiveEffect.FORGET_FACES),
      "no_faces"));

   private Instructions() {
   }

   public static synchronized Instruction register(Instruction instruction) {
      if (REGISTRY.containsKey(instruction.id())) {
         throw new IllegalStateException("duplicate Heaven's Door instruction " + instruction.id());
      }
      REGISTRY.put(instruction.id(), instruction);
      return instruction;
   }

   @Nullable
   public static Instruction byId(@Nullable String id) {
      return id == null ? null : REGISTRY.get(id);
   }

   public static Collection<Instruction> all() {
      return Collections.unmodifiableCollection(REGISTRY.values());
   }

   public static List<String> ids() {
      return new ArrayList<>(REGISTRY.keySet());
   }

   /** Bodies that walk by themselves (not players, not rooted giants). */
   static boolean walks(InstructionContext ctx) {
      return ctx.target() instanceof Mob && !(ctx.target() instanceof Player);
   }

   /** Is this class allowed this instruction at all (for the GUI list). */
   public static boolean classAllows(Instruction instruction, BookClass bookClass) {
      return instruction.baseTicks(bookClass) > 0;
   }
}
