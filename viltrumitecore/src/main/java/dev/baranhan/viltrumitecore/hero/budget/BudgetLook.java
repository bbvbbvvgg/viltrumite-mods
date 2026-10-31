package dev.baranhan.viltrumitecore.hero.budget;

/**
 * Recolour recipe for the generated 64x64 skin plus primitive cube details.
 * Face tokens paint the skin ("blindfold:111111"); detail tokens add cubes
 * ("spiky:f4f4f4", "cape:c01818"). Unknown tokens are ignored.
 */
public record BudgetLook(int skin, int hair, int shirt, int pants, int shoes, int eyes, String[] face, String[] details) {
}
