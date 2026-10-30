# Od Lagna's Wish — the universal transformation system

Spec: the user's task «Желание Од Лагуны» (2026-10-30). This file is the implementation plan and the
record of the decisions taken while building it.

## Goal

One reusable item, **Od Lagna's Wish**, opens the «чего ты желаешь?» screen. The player picks a
first-person wish, confirms it, a 3.2 s magic scene plays, the server changes the hero through the
existing `HeroRegistry.changeHero` lifecycle and the owner sees «твоё желание услышано.». One more wish
returns the player to a plain human. The wish screen also replaces the old first-join race screen.

## Architecture

| Layer | Class | Role |
|---|---|---|
| data (common) | `hero/wish/Wish`, `WishGrant`, `OdLagnaWishes` | Ordered registry: id, lang keys, hero key, scene style, grant. New hero = one `register` call. |
| grant (common) | `WishGrant.hero(key)` | Default grant: `HeroRegistry.changeHero`. Checks `admitsEntry` first (one-holder heroes such as Thor). |
| grant (Iron Man) | `hero/ironman/hulkbuster/HulkbusterWish` | Iron Man + nano + the Mark 48 layer at once (`HulkbusterLayer.grantByWish`, the same ACTIVE + fit check path as a loaded save). |
| server ritual | `hero/wish/WishRitual` | Validation, one ritual per player, root, timeline (CALL 0-16, FULFIL 16-48, swap at 40, END 48-64), interrupts. |
| sync | `WishChoiceC2SPacket`, `WishSceneS2CPacket` | C2S carries only the wish id; S2C START / SWAP / END / ABORT with the elapsed tick to the player and every tracking player (late trackers get a catch-up START). |
| shared lock | `ActionLocks` provider | A player inside a ritual cannot start hero or kit abilities on either side. |
| client screen | `client/wish/WishScreen`, `WishLayout` | The wish GUI: fade-in title, cards, hover, scroll, confirm/cancel, fade-out. Layout is a pure class with tests. |
| client scene | `client/wish/WishSceneClient`, `WishStyles`, `WishMotes`, `WishCamera` | Camera (owner only), letterbox, input lock, base Od Lagna light, per-hero climax, sounds, final title. |

`HeroId` constants are not used in shared code (`NoHeroBranchTest`): wishes name their hero by its stable
key string. The hero files under parallel work (Thor, DIO, Sinclair) are not touched.

## Wishes

| id | ru | hero | style |
|---|---|---|---|
| time | я желаю повелевать временем | dio | time (gold/violet clock rings, space ripple) |
| death | я желаю властвовать над смертью | jinwoo | shadow (violet shadows rising, light swallowed) |
| thunder | я желаю обрести силу бога грома | thor | thunder (ElectricVfx bolts, Thor thunder sounds) |
| reality | я желаю переписать саму реальность | sinclair | cards (cards, stage sparkles, "ta-da") |
| indestructible | я желаю стать несокрушимым | ironman + Hulkbuster | armor (plates closing, reactor glow) |
| genius | я желаю облачиться в броню собственного гения | ironman | tech (hex grid, nano wave) |
| crimson | я желаю, чтобы мир склонился передо мной | homelander | crimson (red heat beams) |
| sword | я желаю стать сильнейшим из рыцарей | reinhard | holy (white light pillar) |
| untouchable | я желаю, чтобы ничто не могло меня коснуться | regulus | still (frozen white shell) |
| return | я желаю вернуть свою прежнюю жизнь | human | mortal (light fades, warm dust) |

`viltrumite` is a save alias of Homelander and gets no wish.

## Lifecycle and safety

- Server checks: wish exists and its hero is registered; the player holds the item or has not made the
  first choice yet; alive, not spectator; no ritual running; not already granted; grant pre-check
  (`admitsEntry`, Hulkbuster room); no world pause (time stop) and no world control on the player.
- The hero changes only at the swap tick, after a second check. Before the swap any interrupt leaves
  the old hero untouched; after it the new hero stays.
- Interrupts: death, logout, dimension change, world pause or control before the swap, server stop.
  Every end removes the root modifier (transient, never saved) and sends ABORT or END.
- Selecting the active hero is refused with a message, never a re-entry.
- Hero → hero goes straight through `changeHero` (old cleanup → new enter), no stop at Human.

## Old race screen

`OpenRaceScreenS2CPacket` now opens the wish screen in first-choice mode. `RaceSelectionScreen`,
`HeroChoiceC2SPacket` and the operator commands stay (debug and compatibility). The item is not
given out on login: it comes from its creative tab or `/give`.
