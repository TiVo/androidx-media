## Changes for `lib-exoplayer`

This was formerly the "core" module in ExoPlayer.  We a fairly large number of patches to this module, as it is central to ExoPlayer functionality and changes quite frequently.   Pull request to this module take a longer time to resolve.

### Summary

Our changes include, at a high level:

1. Fixes for tunneling operation on Broadcom platforms
2. Hooks into track selection for our implementation of trick-play
3. Fixes for live position adjustment 

### Needs Pull Request


* 0922c49c78 2026-01-20 Steve Mayhew | [Buffered duration includes only loaded samples](https://github.com/TiVo/androidx-media/commit0922c49c78) — Pull request [google/ExoPlayer#9050](https://github.com/google/ExoPlayer/pull/9050) covers this, it **needs to be re-opened for AndroidX**
* b39657c294 2026-01-20 Steve Mayhew | [Fix bug where start-over ends early for DASH](https://github.com/TiVo/androidx-media/commitb39657c294) &mdash; see pull request in androidx/media#1451
* eafb8df41c 2026-01-21 Steve Mayhew | [Fix OfflineLicenseHelper crash from repeated SettableFuture.get() calls](https://github.com/TiVo/androidx-media/commiteafb8df41c)
* 0922c49c78 2026-01-20 Steve Mayhew | [Buffered duration includes only loaded samples](https://github.com/TiVo/androidx-media/commit0922c49c78)
* c0ad92fefb 2026-01-20 Steve Mayhew | [setPlaybackParameters() not overwriten by live adjusment](https://github.com/TiVo/androidx-media/commitc0ad92fefb)


### Local Only Changes

These changes may be difficult to ever get accepted upstream.

* 5f6fcdf050 2026-01-21 Steve Mayhew | [Add support for I-frame track selection](https://github.com/TiVo/androidx-media/commit5f6fcdf050)

* 018edbf7f7 2026-01-21 Steve Mayhew | [Adds buffered level to all EventLogger messsages](https://github.com/TiVo/androidx-media/commit018edbf7f7)

* 3cd935ac59 2026-01-20 Steve Mayhew | [Enable alternate PlaybackSessionManager implementations](https://github.com/TiVo/androidx-media/commit3cd935ac59)


### Cherry-Picks

* 17c92117fe 2026-01-21 Steve Mayhew | [SntpClient periodically re-syncs the offset to NTP server](https://github.com/TiVo/androidx-media/commit17c92117fe) &mdash; AndroidX 1.5
* efb5267efb 2026-01-20 Steve Mayhew | [Fixes stall on restart after LAN unplugged pd-17601](https://github.com/TiVo/androidx-media/commitefb5267efb)](https://github.com/TiVo/androidx-media/commit17c92117fe) &mdash; AndroidX 1.2.1

### Remaining To Be Split
Split and group this commit to local only or pull request.

* 06cbdcee02 2026-01-21 Steve Mayhew | [WIP: changes for tunneling...](https://github.com/TiVo/androidx-media/commit06cbdcee02)


### All Changes by Sub Package

#### analytics 

This change is used for trick-play anayltics logging, it has general use for seperating analytics sections.   Submit it as a pull request:
* [2021-02-03 Steve Mayhew b3bdf60620 -- Enable alternate PlaybackSessionManager implementations](https://github.com/tivocorp/exoplayerprvt/commit/b3bdf60620)

NOTE: Unit test cases are not implemented for this, the unit test cases in the original commit were not ported over


Changes to add DRM analytics logging have been reverted, we will cherry-pick the AndroidX implemetation.

#### audio

Un-shared changes for tunneling and issues with audio track init failure on Broadcom.

* `DefaultAudioSink` &mdash; our changes to delay audio track init for tunneling
* `MediaCodecAudioRenderer`, `MediaCodecRenderer` &mdash; fixes for fifo ready, tunneling mode.

The relevant commits are:

* [2025-11-25 sneelavara 005fded182 -- Corecting a minor Merge error](https://github.com/tivocorp/exoplayerprvt/commit/005fded182)
* [2025-11-25 sneelavara 19bd36a84a -- Update the comment and make the code more readable, as per review comments](https://github.com/tivocorp/exoplayerprvt/commit/19bd36a84a)
* [2024-09-18 sneelavara 9afbd13dc4 -- Retry AudioTrack with brief delay. Fixes PARTDEFECT-21565](https://github.com/tivocorp/exoplayerprvt/commit/9afbd13dc4)
* [2023-11-08 mbolaris 3617e2d7fc -- Correct tunneling mode fifoReady condition](https://github.com/tivocorp/exoplayerprvt/commit/3617e2d7fc)
* [2022-09-12 sneelavara cefd633999 -- Tunneling mode Audio stall detection logic](https://github.com/tivocorp/exoplayerprvt/commit/cefd633999)


#### drm

These are the DRM changes, the second 2 will require bug/pull request.

1. ***DRM Session Caching*** &mdash; reverted, will apply the change suggested in AndroidX [issue 2048](https://github.com/androidx/media/issues/2048)
2. ***DRM License request analytics loggi*ng** &mdash; changes mostly merged in, AndroidX [pull 1134](https://github.com/androidx/media/pull/1134), file a bug for the changes we have made since then and create a single pull request with these changes, apply the pull request changes to our tree.  This is the change needed for reporting analytics for failed requests:
   - a9a00b359a 2025-10-06 sneelavara| [Include KeyRequestInfo in key fetch failures](https://github.com/tivocorp/exoplayerprvt/commit/a9a00b359a) [sneelavara]
3. ***Hook for MultiView*** &mdash; This change allows us to set the default to L3 for `MediaDrm`
   - b058f50e76 2024-09-10 Steve Mayhew| [Supports hook altering properties post  `MediaDrm` creation](https://github.com/tivocorp/exoplayerprvt/commit/b058f50e76) [Steve Mayhew]

#### mediacodec

Changes to `MediaCodecRenderer` to support trick-play and key rotation fixes

* [2022-10-27 mbolaris 11db07660a -- Make seek based vtp flush skip work with key rotation.](https://github.com/tivocorp/exoplayerprvt/commit/11db07660a)

Note this changes enables trick-play support in our subclass of MediaCodecVideoRenderer.  TODO share this.

#### source

Local cherry-pick of a change in AndroidX versions after 1.1.1.

* `MaskingMediaPeriod` an androidx pull request, [andriodx/753](https://github.com/androidx/media/pull/753) already merged and in AndroidX 1.2.1  

A series of commits make the lineage of the change hard to follow, but net-net our code is different from 1.1.1

* 526cf30e44 2023-10-20 Steve Mayhew| [Fixes stall on restart after LAN unplugged pd-17601](https://github.com/tivocorp/exoplayerprvt/commit/526cf30e44) [Steve Mayhew]
* f30d56a7b7 2023-10-20 Steve Mayhew| [Revert "TrackSelection does not jump to live with HlsMediaPeriod"](https://github.com/tivocorp/exoplayerprvt/commit/f30d56a7b7) [Steve Mayhew]
* 779c626adc 2021-09-02 Steve Mayhew| [TrackSelection does not jump to live with HlsMediaPeriod](https://github.com/tivocorp/exoplayerprvt/commit/779c626adc) [Steve Mayhew]




#### trackselection

Our hooks for trick-play, these do not have a complete plan for a pull request as of yet.   The iFrame code is in ExoPlayer and AndroidX commented out.

* `AdaptiveTrackSelection` &mdash; Expose method for our sub-class to defer switching tracks in trick-play modes to avoid flushing buffered content.
* `DefaultTrackSelector` &mdash; our changes for Broadcom trickplay (tunneling mode)
* `MappingTrackSelector` &mdash;  made `MappedTrackInfo` public from package for a unit test.   We should fix not to do this.
* `TrackSelector` &mdash; made `invalidate()` public for synced video support

The relevant commits from the ExoPlayer tree:
* [2026-01-05 Steve Mayhew ba33b7f41c -- Fixes bad merge for Dual Mode VTP](https://github.com/tivocorp/exoplayerprvt/commit/ba33b7f41c)
* [2025-08-28 sneelavara 53b9d58b5e -- Merges Google release 2.19.1 to our release](https://github.com/tivocorp/exoplayerprvt/commit/53b9d58b5e)
* [2023-02-21 mbolaris 362cc7f2fd -- Enable tunneling mode VTP for jade21 AKA uiw4060](https://github.com/tivocorp/exoplayerprvt/commit/362cc7f2fd)
* [2022-03-21 mbolaris 2aae14403b -- Remain in tunneling mode with just video when the platform has tunneling VTP support.](https://github.com/tivocorp/exoplayerprvt/commit/2aae14403b)
* [2022-03-21 mbolaris cb738131f9 -- Changes for Broadcom tunneling mode no black flash seek-based VTP.](https://github.com/tivocorp/exoplayerprvt/commit/cb738131f9)
* [2022-03-22 mbolaris e21ace4164 -- Removed spacing only diff. Added comments to help with future Exo merging.](https://github.com/tivocorp/exoplayerprvt/commit/e21ace4164)
* [2021-04-01 Steve Mayhew 7649db00e2 -- Dual Mode VTP Phase 0 (#130)](https://github.com/tivocorp/exoplayerprvt/commit/7649db00e2)


#### util

* `EventLogger` &mdash; open up methods to protected for our `ExtendedEventLogger` + DRM 
* `SntpClient` &mdash;  this change has already been shared with AndroidX, we will pickup this change in [AndroidX 1.5](https://github.com/androidx/media/blob/08e55d81ef/RELEASENOTES.md#15), need to re-test sync video playback after this.

The relevant commits on our ExoPlayer tree for our `EventLogger` changes:

* [2021-06-30 Steve Mayhew 7d885f7a2c -- Merge branch 'release' into t-merge-google-2.12.3-to-release](https://github.com/tivocorp/exoplayerprvt/commit/7d885f7a2c)
* [2021-06-23 Steve Mayhew c5b52be661 -- Fixes BZSTREAM-7913, NPE when video is off](https://github.com/tivocorp/exoplayerprvt/commit/c5b52be661)
* [2020-11-24 Steve Mayhew dd2150f05a -- Update EventLogger error logging and document](https://github.com/tivocorp/exoplayerprvt/commit/dd2150f05a)



For `SntpClient`, on our side:
* dfe5358e8e 2023-10-04 Steve Mayhew| [SntpClient periodically re-syncs the offset to NTP server](https://github.com/tivocorp/exoplayerprvt/commit/dfe5358e8e) [Steve Mayhew]

And from the AndroidX side, we may need to call the "setter" introduced in 70f2d516a0
* 08e55d81ef 2024-10-23 | [Merge pull request #1794 from stevemayhew:p-fix-ntp-time-update-main](https://github.com/androidx/media/commit/08e55d81ef) [Copybara-Service]
* 70f2d516a0 2024-10-21 | [Add getter/setter and disable re-initialization by default](https://github.com/androidx/media/commit/70f2d516a0) (stevem/p-fix-ntp-time-update-main) [Marc Baechinger]
* 621a9aedba 2023-10-04 | [SntpClient periodically re-syncs the offset to NTP server](https://github.com/androidx/media/commit/621a9aedba) [Steve Mayhew]



#### Root Folder

Changes that are to files directly in the main Core top package include:

* `ExoPlayer`, `ExoPlayerImplInternal` &mdash; Detailed below in ExoPlayer Main Changes

* `MediaSourceList` &mdash; part of *DRM License request analytics loggi*ng.
* `PlaybackDiscontinuityException` &mdash; currently unused, we can ignore this
* `PlaybackStuckException` &mdash; new file for stuck playback checks, see below.

#### ExoPlayerImplnternal

These changes are commited in the order, 1 commited first, to allow easier pull requests.

1. ***Live Sync Issue*** &mdash; not clear if this happens for other than AC3 (which we do not support for live sync)

   - [2022-12-21 setPlaybackParameters() not overwriten by live adjusment (Steve Mayhew)](https://github.com/tivocorp/exoplayerprvt/commit/24490cdd56) — covered by issue [google/ExoPlayer#10865](https://github.com/google/ExoPlayer/issues/10865), should re-open in AndroidX

2. ***SoCu DASH Issue*** — [AndriodX/media #1441](https://github.com/androidx/media/issues/1441) Google has expressed no commitment to merge this.

   * [2024-06-25 Steve Mayhew 8eea761596 -- Adds debug log for SoCu Position Shift](https://github.com/tivocorp/exoplayerprvt/commit/8eea761596)

   * [2024-06-11 Steve Mayhew c551a0299f -- Fix bug where start-over ends early for DASH](https://github.com/tivocorp/exoplayerprvt/commit/c551a0299f)

3. ***Issue With Long G*ap** &mdash; Could be the source of many playback stall issues.
   - [2022-12-07 Steve Mayhew 3b2e79aebf -- Buffered duration includes only loaded samples](https://github.com/tivocorp/exoplayerprvt/commit/3b2e79aebff185f29201d3c775af042dd7b6ca2f)
