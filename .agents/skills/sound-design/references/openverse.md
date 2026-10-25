# Openverse audio: API, quotas, and licenses

Official API: https://api.openverse.org/v1/ (read its current documentation when changing API code).

## Working request

`GET https://api.openverse.org/v1/audio/?q=laser&license=cc0&source=freesound&page_size=10`

Do **not** force `category=sound_effect`. Freesound audio records can have `category: null`; the extra filter can discard all valid matches. The response contains `results`, each with `id`, `title`, `url`, `foreign_landing_url`, `creator`, `license`, `license_url`, `source`, `filetype`, `duration` (milliseconds), and `attribution`. Fields may be null. The `url` may be a CDN preview, not the source master.

`/v1/audio/stats/` lists indexed sources. `freesound` is the best starting point for effects. Do not assume `jamendo` music is cleared for this project.

## Rate limits and authentication

- The live anonymous audio endpoint reported `X-RateLimit-Limit-anon_burst: 20/min` and `X-RateLimit-Limit-anon_sustained: 200/day` on 2026-10-25. **20/min is a burst cap, not a guarantee of 20 searches every minute indefinitely.** Read headers; limits may change.
- Openverse permits free anonymous access. Registration and verified email can provide higher limits. Exact registered limits are not promised by this skill. Registration: `POST /v1/auth_tokens/register/`; OAuth client credentials token: `POST /v1/auth_tokens/token/`. Never commit credentials.
- The helper accepts `OPENVERSE_API_TOKEN` for a short-lived bearer token. Alternatively set both `OPENVERSE_CLIENT_ID` and `OPENVERSE_CLIENT_SECRET`; it obtains a fresh token automatically. Without any of these, it uses anonymous access. Never commit credentials or print them in logs.
- Cache each query for seven days by default. Use one search per sound family. If the API responds `429`, stop and respect `Retry-After`; do not rotate proxies or accounts to evade quotas. `401` means invalid token; retry anonymously only after removing the bad token intentionally.

## License gate for Viltrumite Mods

- Accept **CC0 1.0** (or verified original work) by default. The current `add_hero` skill explicitly requires CC0 or own sounds. `PDM` is a public-domain *mark* and not the same grant as CC0; `CC BY` requires attribution and is not within the project's existing asset policy.
- Check the creator and landing page if the sound is likely ripped from another game or soundtrack. CC0 metadata does not override someone else's copyright.
- Copy the `attribution` string without editing into the local scratch `credits.txt`. For committed sources, also record URL, author, license URL, processed output, and transformations in the existing per-hero source list. `CREDITS.md` is the overview.
- Preserve provenance for all source layers in a composite cue, not just the dominant layer.

## Audio quality

A Freesound result can link to a preview MP3 (e.g. `cdn.freesound.org/previews/...-hq.mp3`). Audition it. If it has compression artifacts or is truncated, seek the original via permitted download methods and recheck its license. Never pretend the preview is lossless. Keep raw recordings out of Git.
