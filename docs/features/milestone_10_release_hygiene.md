# Milestone 10 Implementation Plan: Release Hygiene

**Status:** Planned; not implemented

**Task:** T049 — Prepare release hygiene

## Goal

Prepare an optimized, privacy-reviewed, signed test release whose manifest, backup policy, foreground-service behavior, metadata, signing process, and verification evidence are explicit and reproducible. No user media, storage path, content URI, signing key, or signing credential may enter logs, backups, source control, or published artifacts unintentionally.

This plan prepares release mechanics and evidence. It does not publish to an app store, create a production signing identity, add analytics/network services, implement post-MVP export/import, or change the local-first SAF model.

## Checked-in baseline

- The application targets API 37 with minimum API 34, version code `1`, version name `1.0`, and application ID `com.app.resn8`.
- The main manifest currently enables backup and points at template `backup_rules.xml` and `data_extraction_rules.xml` files that do not define an intentional policy.
- The manifest declares media-playback and data-sync foreground services plus notification permission. `Resn8MediaService` is exported for Media3 session discovery; the debug-only test activity is isolated in the debug manifest.
- Release optimization is deliberately disabled through the AGP 9.3 `optimization` block.
- No repository signing configuration or release keep-rule source set exists, and `.gitignore` does not yet cover keystores or signing-properties files.
- Production logging is mostly structured and privacy-conscious, but some storage-facing failures still pass a throwable and therefore require audit against URI/path-bearing exception messages.

## Backup and restore policy

The MVP policy is no Android-managed backup or device transfer of Resn8 app-private state.

The Room database and preferences contain content URIs, collection/source identity, browsing state, queues, ratings, history, and playlists. Restoring those records without the original persisted SAF grants can produce stale private metadata and unusable sources. Explicit export/import remains the post-MVP mechanism for moving durable user-authored state without bundling source audio.

Implementation requirements:

1. Set `android:allowBackup="false"` explicitly.
2. Replace both template XML files with explicit deny rules rather than relying on empty sections or defaults.
3. In `data_extraction_rules.xml`, exclude `root`, `file`, `database`, `sharedpref`, `external`, and all corresponding `device_*` domains from both `<cloud-backup>` and `<device-transfer>`.
4. Give `backup_rules.xml` equivalent deny-all coverage for legacy/full-backup tooling even though the supported runtime begins at API 34.
5. Do not include cache, artwork, Room files, preferences, logs, or generated reports in backup.
6. Document that disabling `allowBackup` alone is insufficient evidence for every Android 12+ manufacturer; verify the merged manifest and explicit device-transfer exclusions.
7. On a disposable target, exercise available backup tooling and inspect the transport result or manifest-derived rules to confirm that no Resn8 payload is eligible.

This policy intentionally means Android reinstall/device transfer does not preserve ratings, playlists, history, or settings until explicit export/import is implemented.

## Privacy and logging audit

### Sensitive values

Treat the following as sensitive and forbidden in production logs and release reports:

- Absolute or relative user paths, filenames, track titles, artist/album names, playlist names, and collection display names.
- SAF tree/document URIs, provider document IDs, persisted-grant details, and source media bytes or artwork.
- Database rows or serialized queue/session payloads.
- Keystore locations when they reveal user paths, aliases when confidential, passwords, private keys, and signing-property contents.

Opaque application-generated collection, source, scan, queue, work, and occurrence IDs may be logged only when needed to correlate lifecycle events. User-visible errors must remain actionable without exposing the sensitive values above.

### Audit procedure

1. Inventory every production `Log`, standard-output, exception, WorkManager diagnostic, and report-writing call.
2. Replace storage-facing throwable logging with a privacy-safe event name, operation phase, stable error category, and optional opaque ID. Do not interpolate exception messages or storage objects.
3. Preserve full throwable visibility only in test/debug-only code where fixtures contain no user data.
4. Create test media with unique canary directory names, filenames, metadata, playlist names, and URI components.
5. Exercise indexing success/failure, corrupt metadata, revoked permission, re-index, artwork failure, playback failure, collection lifecycle, queue restoration, and playlist operations in the optimized signed build.
6. Capture release logcat and generated reports, then search for every canary. Any match fails the audit.
7. Inspect the APK/AAB contents to confirm that test fixtures, local reports, databases, media, signing properties, and keys are absent.

## Manifest, service, notification, and metadata review

Inspect the merged release manifest rather than relying only on source XML.

- Confirm application ID, version code/name, minimum/target SDK, app label, launcher icon, round icon, theme, RTL support, and release `debuggable=false` state.
- Confirm every permission is required by a demonstrated MVP workflow. Do not add broad storage or network permissions.
- Confirm `Resn8MediaService` remains the sole player/session owner, has only the required Media3 intent filter and `mediaPlayback` foreground-service type, and exposes no unintended component.
- Confirm WorkManager's merged foreground service declares only the required `dataSync` type for indexing.
- Verify startup, background playback, lock-screen controls, notification actions, dismissal, stop, and task removal with notification permission allowed and denied.
- Verify indexing foreground behavior with notification permission allowed and denied, including visible progress or actionable in-app state and no silent loss of work.
- Confirm debug-only activities, test instrumentation, fixture seeders, and tooling metadata are absent from the release manifest and artifact.
- Review launcher presentation and version metadata on an installed signed-test build; any product-name/icon/version change requires explicit user approval rather than being invented by this task.

## Release optimization

Use the checked-in AGP 9.3 DSL:

```kotlin
release {
    optimization {
        enable = true
    }
}
```

Enabling this block activates code and optimized resource shrinking. Keep-rule work must follow these constraints:

1. Place custom rules in the AGP 9.3 keep-rule source set using `.keep` files.
2. Start with no broad application-wide keep rule.
3. Run the R8 Configuration Analyzer and inspect its release report.
4. Add the narrowest rule only when an optimized release test or analyzer evidence proves that reflection, serialization, or framework discovery requires it.
5. Never suppress missing-class warnings or keep whole packages merely to obtain a successful build.
6. Retain the mapping and configuration reports as local release evidence, not committed artifacts containing workstation paths.

The optimized smoke suite must cover Room open/migration, Kotlin serialization of persisted queue/session state, WorkManager indexing, Media3 service/controller discovery, notification controls, Coil/artwork loading, navigation restoration, and process-death restoration.

## Signing design

Support a local signing-properties contract with these keys:

```properties
storeFile=<absolute path outside the repository>
storePassword=<secret>
keyAlias=<local test alias>
keyPassword=<secret>
```

- Load an optional root `keystore.properties` file before Android configuration and create the release signing configuration only when all four properties are present.
- Keep unsigned `assembleRelease`/`bundleRelease` builds possible when the local file is absent; a signed-test artifact requires the complete local configuration.
- Add `keystore.properties`, `*.jks`, and `*.keystore` to `.gitignore` before generating or referencing a local key.
- Store the keystore outside the repository. Never print property values or copy the key into Gradle output, reports, documentation, or CI artifacts.
- Use a dedicated local test/upload key, not the Android debug key. This task does not establish the permanent production app-signing identity.
- Fail a requested signed build with a clear missing-configuration message rather than silently signing it with the debug certificate.

## Signed-test release procedure

1. Run the complete T048 host gates with Android Studio's bundled JDK.
2. Run the R8 analyzer and resolve evidence-backed issues.
3. Build the optimized release APK and AAB.
4. Verify the APK with `apksigner verify --verbose --print-certs` and record SHA-256 artifact hashes plus the signing certificate SHA-256 fingerprint.
5. Inspect the APK/AAB and merged manifest for debug components, test fixtures, private data, unexpected permissions, and unexpected native/code resources.
6. Inventory devices and install only on the exact disposable serial. Installation on a data-bearing device requires the immediate approval and backup protections in `AGENTS.md`.
7. Run a release smoke test covering onboarding, internal SAF selection, indexing, browsing, queue creation, playback, background notification control, rating/history, playlist operations, process restoration, permission repair, and optimized artwork behavior.
8. Run the canary log/privacy audit and backup-policy verification.
9. Re-run after any keep-rule, manifest, backup, logging, or signing-configuration change.

## Signed-test checklist

During implementation, complete a checklist in this document containing:

- Source commit and clean/dirty worktree description.
- Application ID, version code/name, min/target API, build-tools/AGP/JDK versions.
- APK and AAB names, sizes, SHA-256 hashes, and build timestamps.
- Signing certificate SHA-256 fingerprint and verification result; never record secrets.
- R8 enabled state, analyzer result, mapping-file location, and keep-rule rationale.
- Merged-manifest permission/component review.
- Backup and device-transfer policy verification.
- Canary privacy audit result.
- T048 automated and device evidence references.
- Disposable device/provider model, API, serial, storage type, and post-run package/data verification.
- Known limitations and an explicit release-ready/not-ready decision.

## Exit criteria

T049 is complete only when:

- Backup and device-transfer exclusions are explicit and verified.
- No canary user data or signing secret appears in logs, reports, backups, source control, or artifacts.
- The merged release manifest and metadata match the reviewed MVP behavior.
- R8 optimization is enabled and the optimized release passes the required smoke suite.
- APK and AAB artifacts build, the APK signature verifies, and hashes/fingerprint are recorded.
- The signed-test checklist is complete with no unresolved release-blocking item.
- No physical-device app data was lost; any approved mutating device run includes post-run data verification.

## Assumptions

- Android-managed backup is disabled for MVP; explicit export/import remains post-MVP.
- A local ignored test key is used for signed-test evidence, while production key ownership is deferred to the actual publishing decision.
- Release optimization failures are fixed through code correctness or narrowly justified keep rules, not by disabling optimization.
- Publishing, store listing creation, analytics, crash reporting, and network services remain out of scope.

