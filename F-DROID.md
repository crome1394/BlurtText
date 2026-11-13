# F-Droid

Prepared, **not submitted**. The recipe points at v0.1.5. The setup matches Point Forecast, which is live on F-Droid:
**exclusive upstream-signed reproducible builds**. F-Droid rebuilds each tagged release
from source, checks it matches our GitHub APK apart from the signature, and then
publishes **our** APK. Users can switch between GitHub and F-Droid updates without
reinstalling. If a rebuild doesn't match, F-Droid skips that version; it never falls
back to its own key.

## Files

| File | Purpose |
|---|---|
| [`docs/fdroid/app.crome1394.blurttext.yml`](docs/fdroid/app.crome1394.blurttext.yml) | Recipe for the fdroiddata merge request |
| `fastlane/metadata/android/en-US/` | Store title, descriptions, changelogs (`changelogs/<versionCode>.txt`), icon, feature graphic, screenshots |
| [`scripts/sign-release.sh`](scripts/sign-release.sh) | Sign the unsigned APK with Build Tools 34 `apksigner` and check the certificate |
| [`scripts/verify-reproducible.sh`](scripts/verify-reproducible.sh) | Rebuild a tag in a fresh clone and run F-Droid's `apksigcopier compare` against the published APK |
| [`scripts/expected-release-cert.sha256`](scripts/expected-release-cert.sha256) | Release certificate; must equal `AllowedAPKSigningKeys` |

F-Droid reads the store text and graphics from this repo at the commit it builds,
so they only appear for releases tagged after they were added.

## Release contract

| | |
|---|---|
| Tag | `v<versionName>`, e.g. `v0.1.5` |
| Asset | `blurttext-<versionName>.apk` |
| Signing key | `~/.local/share/blurttext/blurttext-release.jks` (never change it) |
| Cert SHA-256 | `86553f85b064710091bbf1df7e82a9c90ebb6dba310037bcf902d21440c20ad4` |
| Build | Gradle, `subdir: app`, `submodules: true` (LanText); unsigned `assembleRelease` |

Steps are in [docs/building.md](docs/building.md#release). The two rules that matter:
build from a **clean clone of the tag**, and sign with **`scripts/sign-release.sh`**.

## Status

| Release | Reproducible | Why |
|---|---|---|
| v0.1.4 and older | No | Built from a working tree that differed from the tag (`classes2.dex` and the baseline profile differ) and carry a v1 signature |
| v0.1.5 | Yes | Clean-clone build signed with the release key passes `apksigcopier compare` against an independent rebuild (OpenJDK 21, Gradle 8.11.1, AGP 8.8.2) |

v0.1.5 is the first version to list. The published APK passes `scripts/verify-reproducible.sh`.

## Submitting

1. Cut the release as above and run `sh scripts/verify-reproducible.sh v0.1.5 dist/blurttext-0.1.5.apk`.
2. In the recipe, set `commit` to `git rev-parse v0.1.5^{}` and check `versionName`, `versionCode`, `CurrentVersion`, and `CurrentVersionCode`.
3. Fork https://gitlab.com/fdroid/fdroiddata, add the recipe as `metadata/app.crome1394.blurttext.yml`, and open the merge request with the "App inclusion" template. Allow maintainer commits.
4. Title: `New app: BlurtText (app.crome1394.blurttext)`.

**NonFreeNet** is already declared for the optional KLIPY GIF search. Reviewers added it
to Point Forecast on their own, so declaring it up front saves a round trip.
