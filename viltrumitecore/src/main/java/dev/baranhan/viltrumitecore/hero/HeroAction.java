package dev.baranhan.viltrumitecore.hero;

/**
 * Bounded hero input/display actions. The wire packet carries the ordinal, so
 * a forged packet can only ever name a value in this list.
 */
public enum HeroAction {
   LIONS_HEART,
   DEBRIS_KICK,
   MANIA,
   GREEDS_EMBRACE,
   COUNTER,
   JUMP,
   RITUAL,
   ASSIGN_HEART,
   LASERS,
   FOCUS,
   ROAR,
   /** Iron Man "Костюм" key: put on / take off the suit. */
   SUIT,
   /** Claimed LMB (Iron Man in flight: fly-by punch; air strike in Task 9). */
   PRIMARY_ATTACK,
   /** Claimed RMB: the current right tool (Iron Man repulsor / nano weapon). */
   SECONDARY_USE,
   /** Claimed MMB: cycle the right tool. */
   TOOL_CYCLE,
   UNIBEAM,
   MISSILES,
   NANO_ARSENAL,
   /** Guard key (F) held: shield. */
   GUARD,
   /** RMB on a HeroInteractable entity instead of the tool. */
   INTERACT,
   /** Iron Man helmet toggle (stage 3). */
   HELMET,
   /** Iron Man scan: press starts, press again cancels. */
   SCAN,
   /** Iron Man flares. */
   COUNTERMEASURES,
   /** Iron Man Stage 4: call Veronica; again while the pod stands = suit menu. */
   VERONICA,
   /** Iron Man Stage 4: the worn mark's signature (page 1 slot 3 in a mark; timeline id). */
   SIGNATURE,
   /** Reinhard: claimed MMB, cycle the mode. */
   MODE_CYCLE,
   /** Reinhard Restrained (stage 3). */
   LIGHT_PUNCH,
   STEP_DASH,
   CATCH_PROJECTILE,
   /** Reinhard Knight (stage 3). */
   FIST_COMBO,
   SKY_KICK,
   GRAB_THROW,
   DASH_BEHIND,
   STOMP,
   REFLECT_PROJECTILE,
   /** Reinhard Holy Sword (stages 4-6). */
   REID,
   SHEATH_STRIKE,
   SAW_SPHERE,
   WORLD_CUT,
   WORLD_ERASURE,
   /** Any hero with airJumps > 0: jump in the air (HeroAirJump; no slot, not an ability). */
   AIR_STEP,
   /** Reinhard cinematic Z; appended to preserve existing wire ordinals. */
   DIAGONAL_WORLD_CUT,
   /** Slot key 1 (Thor R: toggle the electric mode). */
   CONTEXT_POWER,
   /** Slot key 2 (Thor Y: pull to the thrown Mjölnir). */
   CONTEXT_MOBILITY,
   /** Separate ultimate key (Thor; default key not approved, slot 3). */
   ULTIMATE,
   /** Slot key 4 (Thor V: choose the teleport point). */
   CONTEXT_TELEPORT,
   /** DIO: summon / dismiss The World. */
   THE_WORLD,
   /** DIO: held MUDA barrage. */
   MUDA_BARRAGE,
   /** DIO: knife fan throw. */
   KNIFE_THROW,
   /** DIO: The World stops time (local field). */
   TIME_STOP,
   /** DIO: vampiric drain grab. */
   VAMPIRE_DRAIN,
   /** DIO ultimate: Road Roller. */
   ROAD_ROLLER,
   /** Sinclair (spec docs/design/2026-10-28-sinclair-design.md §5, §11); appended to keep wire ordinals. */
   VEXING_BOLT,
   SPECTRAL_ASSISTANT,
   RABBIT_HEX,
   MISDIRECTION,
   AUDIENCE_PARTICIPATION,
   /** Sung Jinwoo (spec docs/design/2026-10-28-jinwoo-design.md); appended, wire ordinals unchanged. */
   DAGGER_RUSH,
   SHADOW_EXTRACTION,
   MONARCH_DOMAIN,
   ARMY_SUMMON,
   ARMY_ATTACK,
   ARMY_GUARD,
   ARMY_SENTINEL,
   MONARCH_CHARGE,
   /** Captain America (spec docs/design/2026-11-03-captain-america-design.md); appended, wire ordinals unchanged. */
   CAPTAIN_THROW,
   CAPTAIN_RUSH,
   CAPTAIN_SLAM,
   CAPTAIN_ROLL,
   CAPTAIN_ASSEMBLE;

   public static HeroAction byId(int ordinal) {
      HeroAction[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
   }
}
