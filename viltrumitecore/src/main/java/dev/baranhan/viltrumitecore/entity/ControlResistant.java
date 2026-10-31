package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.hero.control.ControlKind;

/**
 * A non-player creature that refuses some hero controls (a 30-block Leviathan cannot be pulled
 * or grabbed like a zombie). Checked by HeroRegistry.allowsExternalControl for non-players.
 */
public interface ControlResistant {
   boolean allowsControl(ControlKind kind);
}
