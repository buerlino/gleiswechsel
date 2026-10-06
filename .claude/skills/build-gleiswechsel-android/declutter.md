# Declutter

A periodic pass, as in gridload and APODroid: scan everything, report what could be removed,
rewritten or is deprecated, plus bugs and design flaws; the user decides; then work through the
checklist and tick items off.

## What to check in a pass

- **Code:** dead or duplicated code, logic repeated between `:core` and `:app`, state the page
  keeps that can go stale.
- **Migrations:** remove migration code two releases after F-Droid has shipped past the version
  that needed it (`CLAUDE.md`, Stack).
- **Docs:** `CLAUDE.md` loads into every session, so keep it to current facts and decisions;
  finished-work narratives go (git keeps them). Look for stale lines, links to headings that
  moved, duplicates between `CLAUDE.md`, the skill and `research/`, and UI text copied into docs
  (it drifts from the code).
- **Repo:** files nothing uses, fastlane changelogs for versions F-Droid never built, loose git
  objects (`git gc`).
- **Build/CI:** `./gradlew :core:test :app:lintDebug :app:lintAnalyzeDebug --rerun`, Kotlin
  compiler warnings (lines starting `w:`), `--warning-mode all`, two clean unsigned
  `assembleRelease` builds from copies that include `.git` with the same sha256, action versions
  in `.github/workflows/`, redundant `gradle.properties`.
