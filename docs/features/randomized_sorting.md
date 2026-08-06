# Playlist Randomized Sorting

## Summary

Randomized Sorting is a destructive playlist action rather than a generated smart queue. From Playlist Detail, the user chooses Least Played, Most Played, Most Liked, or Recently Added. Resn8 removes every disliked membership (`likeScore < 0`), shuffles tracks within equal metadata groups, orders those groups by the selected method, and persists the result as the playlist's new manual order.

The checked-in baseline already provides durable playlist positions, collision-safe two-phase reordering, explicit saved queues with playlist provenance, and playback controlled exclusively through the application-scoped `PlaybackConnection`. This feature builds on those contracts and does not introduce a dynamic playlist or a stored randomization preference.

## Domain and Persistence

- Add `PlaylistRandomizedSortMethod` with exactly `LEAST_PLAYED`, `MOST_PLAYED`, `MOST_LIKED`, and `RECENTLY_ADDED`.
- Implement a pure sorter with an injectable random source. Exclude negative scores, group by the selected exact value, shuffle every group independently, then traverse play-count groups ascending/descending, like-score groups descending, or exact `firstIndexedAt` groups descending.
- Add one atomic playlist-repository operation that reads current membership and media metadata, deletes disliked memberships, replaces all remaining positions using the existing collision-safe rank scheme, and updates the playlist timestamp. Return the final full order, available playback order, and removed count; unavailable tracks remain members unless disliked.
- Persist no selected method or seed. Repeating an action uses current metadata and fresh randomness and intentionally overwrites any prior manual or randomized order.

## Playlist and Playback Behavior

- Add an accessible Randomized Sorting icon and menu to Playlist Detail. The menu discloses that disliked tracks are removed, disables duplicate submissions, and reports success or failure. A successful rewrite refreshes the reactive list and scrolls it to the top.
- Apply the operation to the entire playlist regardless of visible search results. Existing Play All and track-tap behavior continues to use the newly persisted order.
- If the active queue originated from this exact playlist, replace it with a fresh explicit saved-queue snapshot at the first available track and position zero. Preserve whether playback was active: playing restarts automatically, paused remains paused. This creates a fresh traversal occurrence and immediately skips a disliked current item.
- Leave playback from every other source untouched. If the matching playlist has no playable tracks after rewriting, stop and clear its active queue mapping. If queue/controller replacement fails after the Room commit, keep the durable playlist result and report that playback could not be refreshed.
- `Resn8MediaService` remains the only ExoPlayer and MediaSession owner; screens and ViewModels use `PlaybackConnection` only.

## Verification

- Pure tests cover all four group orders, exact-key grouping, randomized ties with an injectable source, and the normative like-score input `3,3,1,0,0,-1`.
- Room and fake-repository tests cover atomic disliked deletion and rank replacement, unavailable retention, empty/single/all-disliked inputs, durable order, and failure safety.
- ViewModel and Compose tests cover all menu choices, destructive-action disclosure, loading/error/success feedback, removed counts, full-playlist behavior, and scroll-to-top signaling.
- Playback tests cover playing and paused matching queues, immediate removal of a disliked current item, a fresh occurrence at the first available item, no-playable cleanup, and isolation of unrelated queues.
- Run `testDebugUnitTest`, `lintDebug assembleDebug`, and `assembleDebugAndroidTest` with Android Studio's bundled JDK. Do not execute connected-device tasks without the required device inventory and explicit data-loss approval.

## Acceptance Criteria

A user can choose any of the four methods from Playlist Detail and see disliked tracks removed and the remaining playlist durably reordered from current metadata. Repeating the action produces new tie ordering. A matching active playlist queue resets safely to the new first playable track with its prior play/pause intent, while unrelated playback is unchanged. Relaunch, Play All, and track taps observe the rewritten playlist order.

## Verification Status

- `testDebugUnitTest`: passed, including pure grouping/tie behavior, the normative score case, all-disliked output, Room persistence/rollback, unavailable retention, and fake parity.
- `lintDebug assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed; the Randomized Sorting menu/disclosure Compose test compiles.
- Connected-device execution and manual playback/scroll verification remain pending on a disposable API 34+ target; no APK was installed during this implementation.
