package dev.baranhan.viltrumitecore.hero.rohan.instruction;

/**
 * What a line needs besides the instruction itself. Every value is chosen from a server-made list (never typed text):
 * an entity from the session's candidates, a point from its presets, an attack kind from {@link AttackKind}.
 */
public enum InstructionParam {
   NONE,
   ENTITY,
   POINT,
   KIND
}
