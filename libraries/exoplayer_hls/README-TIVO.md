## Changes for `lib-exoplayer-hls`

We have updates to the HLS module in order to support TiVo features and work-around issues with Vecima and Velocix origin and edge servers.

The features include:

1. Live multi-screen video sync for HLS (supported natively for DASH)
2. Support for Vermatrix VCAS DRM
3. Various fixes for I-Frame only playback.

Validations are:

1. Validating HLS playlist updates (Media Sequence and Program Date Time)

Bug fixes:

1. Support for longer than buffered duration EXT-X-GAP intervals
1. Issues with caption tracks, disabling ID3, seek nearest sync behavior.

### Needs Pull Request

#### Feature Requests

* d60d75ff46 2026-01-28 Steve Mayhew | [Add hook for custom DRM encoded in HLS EXT-X-KEY](https://github.com/TiVo/androidx-media/commit/d60d75ff46)
* 40cf22178d 2026-01-28 Steve Mayhew | [Allow clients to set a default HLS start offset](https://github.com/TiVo/androidx-media/commit/40cf22178d)
* be270e969f 2026-01-28 Steve Mayhew | [Implements caption track check in ExoPlayer](https://github.com/TiVo/androidx-media/commit/be270e969f)

#### Bugs

* 3c73519c22 2026-01-28 Steve Mayhew | [Allows playback through gap > maxBufferMs (HLS)](https://github.com/TiVo/androidx-media/commit/3c73519c22) &mdash; Bug already open on ExoPlayer google/ExoPlayer#8959
* c4f3b469b2 2026-01-27 Steve Mayhew | [HLS: avoid IndexOutOfBounds when optional ID3 TrackGroup has no SampleQueue](https://github.com/TiVo/androidx-media/commit/c4f3b469b2)
* 828eae93d9 2026-01-27 Steve Mayhew | [Seek nearest SYNC does not adjust stale playlists](https://github.com/TiVo/androidx-media/commit/828eae93d9) &mdash; has opened ExoPlayer pull request, just reopen
* 299e34784b 2026-01-27 Steve Mayhew | [Allows discard of overlapping iFrame only chunks](https://github.com/TiVo/androidx-media/commit/299e34784b) &mdash; open pull request google/ExoPlayer#10407 on AndroidX
* d467cb4a2b 2026-01-26 Steve Mayhew | [Disable default ID3 and EMSG track creation for HLS streams](https://github.com/TiVo/androidx-media/commit/d467cb4a2b) &mdash; already open Androidx issue, androidx/media#620
* 7ada7b8054 2026-01-26 Steve Mayhew | [Fix CEA-608 exposure for FMP4 segments to match upstream behavior](https://github.com/TiVo/androidx-media/commit/7ada7b8054)

### Has Active Pull Request

* 4e91450ae1 2026-01-27 Steve Mayhew | [Enables HLS Live Offset Synchronization Across Multiple Devices](https://github.com/TiVo/androidx-media/commit/4e91450ae1) &mdash; Pull request: androidx/media#1752



### Local Only Changes

* 61a6e4c0c6 2026-01-28 Steve Mayhew | [Add copy helpers for media playlists and segments](https://github.com/TiVo/androidx-media/commit/61a6e4c0c6) &mdash; unlikely the use case is justified?
* 765cf52cbe 2026-01-28 Steve Mayhew | [Harden media playlist update validation (internal-only)](https://github.com/TiVo/androidx-media/commit/765cf52cbe) &mdash; this is an Edge/Origin problem to solve.
* 49d530083d 2026-01-28 Steve Mayhew | [HlsSampleStreamWrapper: log out-of-range sample timestamps](https://github.com/TiVo/androidx-media/commit/49d530083d) &mdash; long standing open bug, [google/ExoPlayer#7030](https://github.com/google/ExoPlayer/issues/7030).  Without a proper fix the logging is not welcome.

### Un-Migrated Changes

#### HLS Playlist Polling

The playlist reload logic has been reverted to the default from Google's ExoPlayer, this was proposed by never implemented in the pull request

[tivocorp/exoplayerpvt#381](https://github.com/tivocorp/exoplayerprvt/pull/381).  
