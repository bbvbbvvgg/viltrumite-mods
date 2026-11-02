package dev.baranhan.viltrumitecore.hero.rohan.instruction;

import dev.baranhan.viltrumitecore.entity.directive.Directive;
import dev.baranhan.viltrumitecore.entity.directive.DirectiveEffect;
import dev.baranhan.viltrumitecore.entity.directive.MobDirectives;
import dev.baranhan.viltrumitecore.hero.book.BookClass;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * The common instruction: a fixed set of directive effects and end conditions, durations per body class, optional
 * extra applicability. Most of the registry is built from this class; special lines subclass it.
 */
public class DirectiveInstruction implements Instruction {
   protected final String id;
   protected final InstructionParam param;
   protected final float cost;
   protected final int effects;
   protected final int flags;
   /** Durations in ticks for bodies (animal..summon), bosses and players; 0 = not allowed. */
   protected final int mobTicks;
   protected final int bossTicks;
   protected final int playerTicks;
   @Nullable
   protected final Predicate<InstructionContext> extra;
   @Nullable
   protected final String extraRefusal;

   public DirectiveInstruction(String id, InstructionParam param, float cost, int effects, int flags, int mobTicks, int bossTicks, int playerTicks,
      @Nullable Predicate<InstructionContext> extra, @Nullable String extraRefusal) {
      this.id = id;
      this.param = param;
      this.cost = cost;
      this.effects = effects;
      this.flags = flags;
      this.mobTicks = mobTicks;
      this.bossTicks = bossTicks;
      this.playerTicks = playerTicks;
      this.extra = extra;
      this.extraRefusal = extraRefusal;
   }

   @Override
   public String id() {
      return this.id;
   }

   @Override
   public InstructionParam param() {
      return this.param;
   }

   @Override
   public float inkCost() {
      return this.cost;
   }

   public int effects() {
      return this.effects;
   }

   @Override
   public int baseTicks(BookClass bookClass) {
      return switch (bookClass) {
         case BOSS -> this.bossTicks;
         case PLAYER -> this.playerTicks;
         default -> this.mobTicks;
      };
   }

   @Override
   @Nullable
   public String refusal(InstructionContext ctx) {
      BookClass cls = ctx.profile().bookClass();
      if (this.baseTicks(cls) <= 0) {
         return ctx.target() instanceof Player ? "not_on_players" : cls == BookClass.BOSS ? "not_on_bosses" : "not_applicable";
      }
      if (this.extra != null && !this.extra.test(ctx)) {
         return this.extraRefusal == null ? "not_applicable" : this.extraRefusal;
      }
      return null;
   }

   @Override
   public void apply(InstructionContext ctx, int ticks) {
      int fx = this.effects;
      if (ctx.kind() != null) {
         fx |= ctx.kind().effect.bit();
      }
      int[] sealed = null;
      if (ctx.target() instanceof Player && (ctx.kind() == AttackKind.ABILITIES || DirectiveEffect.NO_ABILITIES.in(fx))) {
         sealed = new int[]{-1};
      }
      MobDirectives.apply(ctx.target(), new Directive(this.id, fx, ctx.author().getUUID(), subjectId(ctx), ctx.point(), null,
         ctx.gameTime() + ticks, this.flags, sealed));
   }

   @Nullable
   protected java.util.UUID subjectId(InstructionContext ctx) {
      return ctx.subject() == null ? null : ctx.subject().getUUID();
   }
}
