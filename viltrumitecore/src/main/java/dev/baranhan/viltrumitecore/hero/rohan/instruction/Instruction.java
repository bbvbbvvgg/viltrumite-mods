package dev.baranhan.viltrumitecore.hero.rohan.instruction;

import dev.baranhan.viltrumitecore.hero.book.BookClass;
import javax.annotation.Nullable;

/**
 * One kind of line Heaven's Door can write (docs/rohan/heavens-door-design.md §5). Implementations are registered in
 * {@link Instructions}; a client can only name a registered id and pick a parameter from server-made lists, so a
 * line never runs commands, code or NBT. Server only.
 *
 * <p>Lang keys: {@code book.viltrumitecore.instruction.<id>} (the written sentence) and
 * {@code book.viltrumitecore.instruction.<id>.desc} (the tooltip).
 */
public interface Instruction {
   /** Stable id (lowercase, saved in directives). */
   String id();

   InstructionParam param();

   /** Ink for one line before the book's cost scale. */
   float inkCost();

   /** Base duration in ticks for a body of this class; 0 = this class cannot take the line. */
   int baseTicks(BookClass bookClass);

   /**
    * Why this line cannot be written into this book now (a lang key under {@code book.viltrumitecore.refuse.}), or
    * {@code null} when it can. The parameter is not checked here (see {@link #refusalWithParam}).
    */
   @Nullable
   String refusal(InstructionContext ctx);

   /** The parameter-dependent check, run after {@link #refusal}. */
   @Nullable
   default String refusalWithParam(InstructionContext ctx) {
      return switch (this.param()) {
         case ENTITY -> ctx.subject() == null ? "no_subject" : null;
         case POINT -> ctx.point() == null ? "no_point" : null;
         case KIND -> ctx.kind() == null ? "no_kind" : null;
         case NONE -> null;
      };
   }

   /** Server handling: write the directive(s) for {@code ticks}. */
   void apply(InstructionContext ctx, int ticks);
}
