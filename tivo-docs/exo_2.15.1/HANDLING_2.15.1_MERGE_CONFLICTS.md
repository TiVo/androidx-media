# Handling 2.15.1 Merge Conflicts

## Overview

This document describes how we will perform the merge of Google's 2.15.1 release with our local `release` branch.

### Background

When merging  Google's `r2.15.1` release tag  to our `release` branch there are possible conflicts from two broad categories:

1. Changes not shared with Google (these should be very few)
2. Shared but not included in `r2.15.1`

For case 1 changes, the action is always to take theirs and "patch in" our change.  Case 2 changes are mostly (if no conflicts in the cherry-pick) included in the *merge source branch*.

In other words, all deltas in our code from Google's code fall into one of these states:

1. already included in 2.15.1
2. in `release-v2` post 2.15.1
3. in `dev-v2` no release target
4. in open pull request
5. not shared

This document lists the changes by module in the *Our Changes* sections,  case 5 in the *Not Shared* sections, and cases 2, 3 and 4 in the *Cherry-Picked From Future* sections.

We manage the execution of the merge with this set of branches:

1. **Merge Reference** (`t-google-release-v2-r2.15.1`)  &mdash; Checkout of Google's tree at `r2.15.1`, then create a local branch.  This is our upstream  reference branch, it is the base for the *Merge Source* branch
2. **Merge Source**  (`t-google-release-v2-r2.15.1-with-cherry-picks`) &mdash;  branch from the *Merge Source*  then cherry-picks from Google's code base and our open pull requests.  Note the cherry-picked changes are essentially ''future" versions of shared changes in our `release` branch. 
3. **Merge Target**  (`t-merge-google-release-r2.15.1`)  &mdash; This is based from our `release` and the target of the merge .  Will keep this branch re-based to release (note, this may require locking down the `release` branch a bit to insure a clean pull request)

### Mechanics

The overall goal is to reduce the initial set of conflicts and the resolution complexity of the conflicts that remain (that is they should most all be take theirs)

So, the basic workflow is to:

1. checkout latest `t-merge-google-release-r2.15.1` 
2. do the merge and run the resolve script
3. work on conflicts, write up resulting resolution background in here.
4. add the resolution to `initial-merge-script.sh`

These commands perform steps 1 and 2

```shell
git checkout t-merge-google-release-r2.15.1
git merge t-google-release-v2-r2.15.1-with-cherry-picks
./tivo-docs/exo_2.15.1/initial-merge-script.sh
```

To restore your workspace, simply abort the merge,  `git merge --abort`

Our goal is to eliminate as many conflicts as possible, this can be done by:

1. Reverting our conflicted changes in *Merge Target* branch
2. Adding cherry-picks to the *Merge Source* branch.  

Of course do not change the *Merge Source* with any commit that is not from Google's upstream git or an open pull request (as this simply creates possible future conflicts).   Any changes to these two branches **must build and pass all unit tests**.

Once we are done, the draft pull request with the *Merge Target* branch should be an unconflicted merge into our `release`, we will likely do this as a merge commit to keep the history meaningful

## Top Level Project 

The commit  [local gradle changes in separate file facilitate merges](https://github.com/tivocorp/exoplayerprvt/commit/709fdae964f7eee69808a99b7188a556d68ca741) aligns our version to match 2.15.1 closely enough that the resolve is fairly simple for the 4 conflicts

### Merge Conflicts

The merge script handles all of these automatically.

#### Take Theirs

- **gradle/wrapper/gradle-wrapper.properties**  we pulled forward update [Update to Gradle plugin 4.1](https://github.com/google/ExoPlayer/commit/f4f312738b), but 2.15.1 later updated it again ([Bump dependency versions](https://github.com/google/ExoPlayer/commit/dea52048cb)) to 6.9, so the final version should be 6.9 (take theirs)
- **constants.gradle**   Our version will be 2.15.1-1.0, our first 2.15.1 release.  Otherwise take theirs.

#### Take Ours

- **publish.gradle**  Ours now matches their pattern, but only publishs to our Artifactory, so simply overwrite with ours to resolve

- **build.gradle** &mdash; Ours adds our artifactory and removes jCenter() (both are same line, hence the conflict).  We removed jCenter to prevent our build from failing periodically when we could not reach jCenter quickly enough.  

## Module`library-common` 

### Merge Conflicts

#### Take Theirs

- **TimestampAdjuster**  &mdash; We cherry-picked in this commit: [Fix issues with Metronet out of sync discontinuity sequence updates](https://github.com/tivocorp/exoplayerprvt/commit/5d7fa23401e72f9670f9fa4a4db593a4c091d22d), from this commit [HLS: Allow audio variants to initialize the timestamp adjuster](https://github.com/google/ExoPlayer/commit/6f8a8fbc1c).   Since the cherry-pick there have been a number of changes that conflict, up to the most recent: [HLS: Avoid stuck-buffering issues](https://github.com/google/ExoPlayer/commit/8732f2f030)
- **ExoPlayerLibraryInfo** &mdash; Our local update to the version can be done once we have a good build, for now take theirs

#### Merge but Open Pull Request

- **AacUtil.java**  &mdash; Our change [Adding bound check for AAC audioConfig input buffer](https://github.com/tivocorp/exoplayerprvt/commit/1b3c24bd2d769128ce881913222a82013a412c37) is a modified cherry-pick of [Throw ParserException if AAC config is invalid](https://github.com/google/ExoPlayer/commit/6db6d14b16c1324bde1677cce8cfa3571d201d9a).  This needs to be rectified with a pull request for Google, basically we add a message to one ofthe exceptions.  The merge script patches our change sto this file to use the new`ParserException.createForMalformedContainer()`

### Auto Merges

Looks like spacing changes, but worth a second look

```
library/common/src/main/java/com/google/android/exoplayer2/decoder/Buffer.java
library/common/src/main/java/com/google/android/exoplayer2/Format.java
library/common/src/main/java/com/google/android/exoplayer2/C.java
```

These are from our local changes that merged with no conflicts

```
library/common/src/main/java/com/google/android/exoplayer2/util/Util.java
library/common/src/main/java/com/google/android/exoplayer2/util/Log.java
library/common/src/main/java/com/google/android/exoplayer2/audio/Ac3Util.java
```

### Our Changes

#### In Open Pull request

This change auto-merged with no conflict

- [2a9ef44fa1](https://github.com/tivocorp/exoplayerprvt/commit/2a9ef44fa1) Thu Apr 21 10:49:51 2022 -0700 Shashikant Enabls using a custom logger instead of android.util.Log

#### Cherry-Picked From Future

This was from `2.15.1` so it auto merged in `Util.java`

* [81430f31db](https://github.com/tivocorp/exoplayerprvt/commit/81430f31db) Thu Jun 30 16:15:02 2022 -0700 olly Remove max API level for reading TV resolution from system properties

#### Not Shared

These changes will need a test case to verify, the code auto-merged

* [aeed7dc0a4](https://github.com/tivocorp/exoplayerprvt/commit/aeed7dc0a4) Tue Apr 5 23:01:32 2022 -0700 mbolaris Workaround to handle 5-channel AC3 audio.


## Module `library-core`

### Merge Conflicts

The merge conflicts in this `library-core` handled by the initial merge script by mostly taking Google's version.  The first section (Take Theirs) outlines the basic resolutions.  


#### Take Theirs

Here is the list of conflicted files where we simply take the upstream version, these are caused by cherry-picks we have taken that are already merged in some cases.  We will need to re-cherry-pick the change [Require playback to be stuck for a minimum period before failing](https://github.com/google/ExoPlayer/commit/c19d1da6ed)

- **BaseRenderer**  &mdash; formatting change were we reverted out making method protected

- **ExoPlayer**, **ExoPlayerImpl**, **SimpleExoPlayer** and **ExoPlayerImplInternal** &mdash; the experimental stuck buffering code was removed (a good thing, this was causing issues with seek in paused mode (SCRUB)), this conflicted with our cherry pick of the release timeout API (which is now included.)
- **ExoPlayerFactory**  &mdash; This was deleted (after being deprecated), replaced with `SimpleExoPlayer.Builder()` which we are using everywhere now.
- **UnrecognizedInputFormatException** &mdash; The conflicted constructor is no longer used.  The original change, [Improved exception handling](https://github.com/tivocorp/exoplayerprvt/commit/c6fb6c5e7f) is no longer valid, worthy of a larger project to share with Google to improve diagnosing `ParserException`

#### Resolve With Fix-ups

- **trackselection.DefaultTrackSelector.java** &mdash; Un-shared changes conflict, we want to take ours for the lines:
  1. `ROLE_FLAG_TRICK_PLAY` tracks in `selectFixedVideoTrack()` Use magic merge to take ours
  2. keep tuneling on if audio disabled in `maybeConfigureRenderersForTunneling()` use magic merge to take ours.

- **audio.AudioCapabilitiesReceiver**   &mdash; formatting change broke our added `MediaRouter.Callback` bluetooth speaker support that Google did not take, the merge script has a patch file that is the result from taking these steps:
  1. Take all non-conflicting
  2. add our `MediaRouter.Callback` at the end


#### Requires Research

Most likely action is to take theirs, however for these cases a cherry-pick is somehow different from the code we cherry-picked.   This requires some reserach to at least identify areas QE should test.

- **audio.DefaultAudioSink** &mdash; we cherry picked a change from dev-v2 ([79cf52140f](https://github.com/tivocorp/exoplayerprvt/commit/79cf52140f) Applying change 7dfdde9 from Google dev-v2).   This is not exactly the code we cherry-picked.   Google has added recovery logic in the main player (`doSomeWork()` exception handler) and as such some errors are marked to recover at this level, [Retry after offload playback failure](https://github.com/google/ExoPlayer/commit/d97af76280b834e4279bcddac966e21153430740)
- **drm.DefaultDrmSessionManager**  &mdash; we cherry picked [Ensure DefaultDrmSessions keep working if their manager is released (1bf5a273ff)](https://github.com/google/ExoPlayer/commit/1bf5a273ff), however this change is in the middle (2.14.0) of the history of some 30 changes to this file.  Our best action is to take the stock 2.15.1, but should research what bug fixes we may need from this for MDRM.
- **AsynchronousMediaCodecBufferEnqueuer**   &mdash; our change modified a cherry-picked version of this code, either we drop the modification or submit a pull request with the modification.  See the section on "Cherry-picked From Future", we could cherry-pick the final version of the async media codec support (in 2.18 ExoPlayer)
- **Cea708Decoder**, **CeaDecoder**  &mdash; These conflicts arise from the service block work.  Short-term, the conflicted file needs resolve, long-term we need our fixes in Google'c code base.
- **analytics.DefaultPlaybackSessionManager** &mdash; Evaluate Player.stop() behavior, the comment in the TODO on the conflicted line talks about the changes.  See if we can "take theirs" and still get our test cases to pass.
- **analytics.PlaybackStatsListener[Test]** &mdash; Our change was to add a factory method for `DefaultSessionManager` and not create new sessions on stop ([PlaybackStatsListener will not create sessions for Player.stop()](https://github.com/tivocorp/exoplayerprvt/commit/5391296e74)).  Actions should be:
  - Figure out if we can do without our changes or submit pull requests for them
  - Test cases we have should merge and still pass


### Auto Merges

Look at what was changed on our side (cherry-pick, local change, etc) and make sure it is covered

```
library/core/src/test/java/com/google/android/exoplayer2/ExoPlayerTest.java
library/core/src/main/java/com/google/android/exoplayer2/video/MediaCodecVideoRenderer.java
library/core/src/main/java/com/google/android/exoplayer2/util/EventLogger.java
library/core/src/main/java/com/google/android/exoplayer2/trackselection/TrackSelector.java
library/core/src/main/java/com/google/android/exoplayer2/trackselection/AdaptiveTrackSelection.java
library/core/src/main/java/com/google/android/exoplayer2/text/webvtt/WebvttParserUtil.java
library/core/src/main/java/com/google/android/exoplayer2/source/MaskingMediaSource.java
library/core/src/main/java/com/google/android/exoplayer2/source/MaskingMediaPeriod.java
library/core/src/main/java/com/google/android/exoplayer2/mediacodec/MediaCodecRenderer.java
library/core/src/main/java/com/google/android/exoplayer2/audio/AudioTrackPositionTracker.java
library/core/src/main/java/com/google/android/exoplayer2/audio/AudioCapabilities.java
library/core/src/main/java/com/google/android/exoplayer2/analytics/PlaybackSessionManager.java
```

### Our Changes

#### In Open Pull request

1. **Route the logs to custom logger** [#10185](https://github.com/google/ExoPlayer/pull/10185) &mdash; The commit below is in ExoPlayer `2.18` but using our change for now avoids conflicts.
   *  77a3b16d6b 2022-07-13 | [Merge pull request #10185 from TiVo:p-custom-logger](https://github.com/google/ExoPlayer/commit/77a3b16d6b) [Rohit Singh]
2. **Stuck buffering while tunneling** [#6407](https://github.com/google/ExoPlayer/pull/6407) &mdash; This change was superseded by a later fix, [31fb98bd50](https://github.com/tivocorp/exoplayerprvt/commit/31fb98bd50) so the pull request needs to be updated.  The fix is simple enough it does not conflict (for now).
3. **Tunneling mode Audio stall detection logic** [#10613](https://github.com/google/ExoPlayer/pull/10613) &mdash; The cherry-picked of this into the *Merge Source* branch adds a method (`MediaCodecRenderer.getLargestQueuedPresentationTimeUs()` ) needed by the "Stuck buffering..." change.
4. **TrackSelection does not jump to live with HlsMediaPeriod** [#9386](https://github.com/google/ExoPlayer/pull/9386/files)
   * 9f9621d99a 2021-09-08 | [Tests trackselection does not seek to default period start](https://github.com/TiVo/ExoPlayer/commit//9f9621d99a) (p-fix-exo-issue-9347) [Steve Mayhew]
   * 2f8779baff 2021-09-02 | [TrackSelection does not jump to live with HlsMediaPeriod](https://github.com/TiVo/ExoPlayer/commit//2f8779baff) [Steve Mayhew]
#### Cherry-picked From Future

These are conflicted and will need to be re-cherry-picked (if required): 

- [ec8245400f](https://github.com/tivocorp/exoplayerprvt/commit/ec8245400f) Tue Apr 5 17:36:00 2022 -0700 olly Require playback to be stuck for a minimum period before failing

This change is conflicted, either submit a pull request for the conflicted area or have a new cherry-pick with the latest code from the final version for asynchronous queuing, https://github.com/google/ExoPlayer/commit/aa2a4e3055

* [628657a82b](https://github.com/tivocorp/exoplayerprvt/commit/628657a82b) Tue May 17 06:50:25 2022 -0700 mbolaris Allow asynchronous queuing with the trick play aware renderer

These changes are already included in 1.15.1 or earlier, so they are not conflicted

* [dc9ddd9c4c](https://github.com/tivocorp/exoplayerprvt/commit/dc9ddd9c4c) Fri Nov 12 12:28:22 2021 -0800 ibaker Keep secure MediaCodec instances when disabling the renderer
* [e87ff4e8fa](https://github.com/tivocorp/exoplayerprvt/commit/e87ff4e8fa) Fri Nov 12 12:28:22 2021 -0800 ibaker Ensure DefaultDrmSessions keep working if their manager is released

This change is post 1.15.1, but is not conflicted 

- [81430f31db](https://github.com/tivocorp/exoplayerprvt/commit/81430f31db) Thu Jun 30 16:15:02 2022 -0700 olly Remove max API level for reading TV resolution from system properties

These changes are to the text package, this was reset to the state as of 2.12.3 and other changes post 2.12.3 were cherry-picked, still some of our changes are not in and this results in conflicts.

* [2c528686be](https://github.com/tivocorp/exoplayerprvt/commit/2c528686be) Sat May 28 20:52:47 2022 -0700 sneelavara CEA-708 Decoder fixes for issue #1807.
* [98ad3eaedd](https://github.com/tivocorp/exoplayerprvt/commit/98ad3eaedd) Sat May 28 20:52:47 2022 -0700 kim-vde Merge pull request #8415 from TiVo:p-fix-cea708anchor
* [e67958d112](https://github.com/tivocorp/exoplayerprvt/commit/e67958d112) Sat May 28 20:52:47 2022 -0700 sneelavara This pull request is for issue#1807. Refactoring the PR #8356
* [ad95432d1e](https://github.com/tivocorp/exoplayerprvt/commit/ad95432d1e) Wed May 25 21:59:44 2022 -0700 Steve Mayhew Reverts text/cea to state as of google's 2.12.3

#### Conflicted

Out changes to `DefaultTrackSelector` continue to be conflicted.  The long term solution is to:

1. Fully integrate trick play into Google's code, this requires agreement on how track selection should work
2. Propose a solution for tunneling mode without audio tracks

Until then we need to continue to "revert" the ignoring `C.ROLE_FLAG_TRICK_PLAY` part of this Google change:
*  [Don't select trick-play tracks by default (olly, 6cff8a6ad0)](https://github.com/google/ExoPlayer/commit/6cff8a6ad0)

And continue to re-apply / merge these changes:
* 2022-08-05 [Add Jade 21 to the tunneling VTP platforms supported in platformSupportsTunnelingTrickPlay() (mbolaris, ec6155584e)](https://github.com/tivocorp/exoplayerprvt/commit/ec6155584e)
* 2022-07-07 [Added setApplyConstraintsFrameRate() track selection parameter (mbolaris, 163be9ed76)](https://github.com/tivocorp/exoplayerprvt/commit/163be9ed76)
* 2022-03-22 [Removed spacing only diff. Added comments to help with future Exo merging. (mbolaris, e21ace4164)](https://github.com/tivocorp/exoplayerprvt/commit/e21ace4164)
* 2022-03-22 [Changes for Broadcom tunneling mode no black flash seek-based VTP. (mbolaris, cb738131f9)](https://github.com/tivocorp/exoplayerprvt/commit/cb738131f9)
* 2022-03-22 [Remain in tunneling mode with just video when the platform has tunneling VTP support. (mbolaris, 2aae14403b)](https://github.com/tivocorp/exoplayerprvt/commit/2aae14403b)




## Module `library-extractor`

### Merge Conflicts

Resolve with these steps, details follow.

#### Take Theirs

- **TsExtractor**, **PsExtractor** &mdash; take theirs on the conflict (changes to seek() code we don't use)

### Auto Merges

These are all the result of the code in the open pull requests.

```
library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/H264Reader.java
```

### Our Changes

These changes have all been resolved to the merge source (`t-google-release-v2-r2.15.1-with-cherry-picks`) and merge target (`t-merge-google-release-v2-r2.15.1`) branches.  The only exception is pull request *Commits IDR NALU on end of stream [#10514](https://github.com/google/ExoPlayer/pull/10514)* it has been reverted from the merge target and will be cherry-picked after the inital merge checkin (it causes many conflicts)

#### In Open Pull request

1. **Commits IDR NALU on end of stream** [#10514](https://github.com/google/ExoPlayer/pull/10514)

The commits are listed below, there is a Jira story to add a test for the first commit (filler data), the other commit has test case already.

1. 2022-04-05 [Add handling for iframe transport segments with filler data. (mbolaris, 059e639524)](https://github.com/tivocorp/exoplayerprvt/commit/059e639524).  This change auto merges ok, but it will not work without the next change

2. 2021-12-15 [Commits IDR NALU on end of stream (Steve Mayhew, cb3b671021)](https://github.com/tivocorp/exoplayerprvt/commit/cb3b671021) This was submitted as pull request [10514](https://github.com/google/ExoPlayer/pull/10514), the commit was reverted from the merge branch to avoid the conflicts

#### Changes cherry-picked from future

Our cherry-pick was revert, becauses of conflict as we had to modify the original 2.15.0 commit:  [Fix issue where a trun atom could be associated with the wrong track [olly]](https://github.com/google/ExoPlayer/commit/4e8895d5cb).  Our cherry-pick commit below was reverted from the merge target branch:

* [274c40bda8](https://github.com/tivocorp/exoplayerprvt/commit/274c40bda8) Wed Dec 1 14:28:27 2021 -0800 olly Fix issue where a trun atom could be associated with the wrong track

## Module `library-hls`

This is the oldest set of changes to the Google ExoPlayer code we have, there are several changes (e.g. VCAS support, TiVoCrypt, Dual Mode) that we will never be able to share with Google.  These merge conflicts will simply need to be re-addressed. 

Some of the changes since 2.13.3 have been shared in pull requests

### Merge Conflicts

#### Take Theirs

- **HlsPlaylistParser** &mdash; the cherry-picked changes from 2.17.0 (EXTINF rounding issue) were added to the `t-google-release-v2-r2.15.1-with-cherry-picks` so we can "take theirs"

- **HlsMediaPlaylistParserTest** &mdash; Moving test cases with this commit [Fixes conflict in HlsMediaPlaylistParserTest](https://github.com/tivocorp/exoplayerprvt/commit/9bdcdeca41), allows cleaning getting the balance of the changes from `t-google-release-v2-r2.15.1-with-cherry-picks`

- **HlsChunkSourceTest** &mdash; Take theirs (*Merge Source* branch), has cherry-pick from open pull request, note broken diff are result of no common merge base.  This class was added to test the `SeekParameters.NEAREST_*` implementation, first pull request was *Implements SeekParameters.*_SYNC variants for HLS* [#9536](https://github.com/google/ExoPlayer/pull/9536). The changes merged into release-v2 are: 

  - 3dee8e4993 2021-12-14 | [Merge pull request #9767 from TiVo:p-nearest-sync-track-index-bug](https://github.com/google/ExoPlayer/commit/3dee8e4993) [Ian Baker]
  -  7ac24528bc 2021-12-07 | [Uses correct index for playlist URL](https://github.com/google/ExoPlayer/commit/7ac24528bc) (p-nearest-sync-track-index-bug) [Steve Mayhew]  
  -  4a69e1660f 2021-11-26 | [Merge pull request #9536 from TiVo:p-fix-issue-2882](https://github.com/google/ExoPlayer/commit/4a69e1660f) [kim-vde]

  This change is pending in a pull request based to `dev-v2`:

  * f6488d3ea8 2022-07-28 | [Seek nearest SYNC does not adjust stale playlists](https://github.com/TiVo/ExoPlayer/commit//f6488d3ea8) (p-stale-playlist-seek-adjust) [Steve Mayhew]


#### Take Theirs and Patch

- **HlsMediaChunk** &mdash; our not shared changes update `buildDataSource()` to add the `keyUri` parameter to determine we need `HlsDecryptingDataSource`.  Since are other **shared** changes are mixed into this:

  1. b7850d322a 2022-06-30 | [Allows discard of overlapping iFrame only chunks](https://github.com/tivocorp/exoplayerprvt/commit/b7850d322a) [Steve Mayhew]
  2. 1746aa8bf6 2021-12-10 | [Timestamp init wait occurs after dataSource.open()](https://github.com/tivocorp/exoplayerprvt/commit/1746aa8bf6) [Steve Mayhew]
  3. 436ae8e3f8 2021-06-15 | [HLS: Fix issue where new init segment would not be loaded](https://github.com/tivocorp/exoplayerprvt/commit/436ae8e3f8) [olly]
  4. a601378039 2021-06-10 | [no infinite stall if GAP > maxBufferMs](https://github.com/tivocorp/exoplayerprvt/commit/a601378039) [Steve Mayhew]

  The first change is nullified by changes in the *Merge Reference* :

  * 08259f8987 2021-05-20 | [Don't allow spliced-in preload chunks.](https://github.com/google/ExoPlayer/commit/08259f8987) [tonihei]

  But picked up again with cherry-picks to the *Merge Source* :

  * 4b11d01470 2022-07-15 | [Add test cases for `shouldSpliceIn(...)` method](https://github.com/tivocorp/exoplayerprvt/commit/4b11d01470) [Steve Mayhew]
  * 3b84c09aa5 2022-06-30 | [Allows discard of overlapping iFrame only chunks](https://github.com/tivocorp/exoplayerprvt/commit/3b84c09aa5) [Steve Mayhew]

  We simply take theirs (*Merge Source*) and patch in the `keyUri` changes.

- **HlsSampleStreamWrapper** &mdash; all of our changes are shared or from cherry-picks from Google except this one:
  * 3ab1b037b9 2022-01-26 | [Issues forced seek if no render in 8 * target FPS](https://github.com/tivocorp/exoplayerprvt/commit/3ab1b037b9) [Steve Mayhew]
    * change to `onLoadError()` to not retry load if loadable is from a trick-play track
    * uncomment the timestamp checks (https://github.com/google/ExoPlayer/issues/7030.) code and log the issue as warning]

- **HlsChunkSource** &mdash; Handled by taking theirs then a small patch to add the VCAS related code.  Our changes with conflicts are:
  - VCAS changes - our key changes in `getNextChunk()` conflict, this is because of their changes to support HLS-LL

  - Our `getAdjustedSeekPositionUs()` is the same as `t-google-release-v2-r2.15.1-with-cherry-picks` which has the cherry-pick, it is just in  same spot as `getChunkPublicationState()`, with the updates to the merge source branch the method body for `getAdjustedSeekPositionUs()` is identical in the conflict.


- **HlsMediaPlaylist** &mdash; Our changes are all covered by unit tests in TiVo library-trickplay.  They add support for Dual Mode and playlist validation conflict with Google's adding  support for HLS-LL.  The merge involves adding these methods and editing the resulting code as the constructor aguments for `HlsMediaPlaylist` and `HlsMediaPlaylist.Segment` have both changed.  This is done and included as a patch. Our added methods include:
  1. Added two method to clone `HlsMediaPlaylist.Segment`, `copyWithDuration()` and `copyWithUpdates()`
  1. Method to clone the `HlsMediaPlaylist` itself with a new segment list, `copyWithUpdates()`
  1. Added the method `HlsMediaPlaylist.isUpdateValid()` to check for invalid playlist updated (Vecima work around)

  The relevant commits  to check are:
  
  * dba164829f 2022-05-27 | [Validates HLS media playlist updates](https://github.com/tivocorp/exoplayerprvt/commit/dba164829f) [Steve Mayhew]
  * e71e21b39a 2022-04-04 | [Supports dual-mode playlist updates](https://github.com/tivocorp/exoplayerprvt/commit/e71e21b39a) [Steve Mayhew]
  * d68d503b70 2022-04-04 | [Playlist `baseUri` matches curated uri](https://github.com/tivocorp/exoplayerprvt/commit/d68d503b70) [Steve Mayhew] 
  * 00476f43ca 2022-04-04 | [Validates playlist times to MS only](https://github.com/tivocorp/exoplayerprvt/commit/00476f43ca) [Steve Mayhew]
  * cd73550814 2022-03-31 | [Update with review changes.](https://github.com/tivocorp/exoplayerprvt/commit/cd73550814) [Steve Mayhew]
  * ea83607178 2022-03-31 | [Removes work-around for Vecima PDT issue](https://github.com/tivocorp/exoplayerprvt/commit/ea83607178) [Steve Mayhew]
  * 8e227a1eab 2021-05-26 | [Merge branch 'google-2.12.3' into t-merge-google-2.12.3-to-release](https://github.com/tivocorp/exoplayerprvt/commit/8e227a1eab) [Steve Mayhew]
  * 7649db00e2 2021-04-01 | [Dual Mode VTP Phase 0 (#130)](https://github.com/tivocorp/exoplayerprvt/commit/7649db00e2) [Steve Mayhew]

### Our Changes

The first two section of changes have all been resolved to the merge source (`t-google-release-v2-r2.15.1-with-cherry-picks`) and merge target (`t-merge-google-release-v2-r2.15.1`) branches.   The exceptions in *Other Changes* section are chagnes not shared with Google that must be patched in after the merge.

#### In Open Pull request

These changes are in open pull requests to Google that include files in `library-hls` 

1. Allows discard of overlapping iFrame only chunks [#10407](https://github.com/google/ExoPlayer/pull/10407)
   - d79a4504bb 2022-07-15 | [Add test cases for `shouldSpliceIn(...)` method](https://github.com/TiVo/ExoPlayer/commit//d79a4504bb) (p-allow-iframe-queuesize-pruning) [Steve Mayhew]
   - 3db86e6e73 2022-06-30 | [Allows discard of overlapping iFrame only chunks](https://github.com/TiVo/ExoPlayer/commit//3db86e6e73) [Steve Mayhew]
2. Seek nearest SYNC does not adjust stale playlists [#10484](https://github.com/google/ExoPlayer/pull/10484)
   - f6488d3ea8 2022-07-28 | [Seek nearest SYNC does not adjust stale playlists](https://github.com/TiVo/ExoPlayer/commit//f6488d3ea8) (p-stale-playlist-seek-adjust) [Steve Mayhew]
3. HLS: Allows playback through gap > maxBufferMs [https://github.com/google/ExoPlayer/pull/9050](https://github.com/google/ExoPlayer/pull/9050)
   * 09fcab0e89 2021-06-11 | [Allows playback through gap > maxBufferMs](https://github.com/TiVo/ExoPlayer/commit//09fcab0e89) (p-fix-over60s-gap-stall) [Steve Mayhew]

#### Changes cherry-picked from future

Google's commits of our changes that were merged into Google's `release-v2`  released after r2.15.1.  

##### r2.17.0

- 6d8588fcea 2021-12-10 | [Timestamp init wait occurs after dataSource.open()](https://github.com/google/ExoPlayer/commit/6d8588fcea) [Steve Mayhew]
- 7ac24528bc 2021-12-07 | [Uses correct index for playlist URL](https://github.com/google/ExoPlayer/commit/7ac24528bc) (p-nearest-sync-track-index-bug) [Steve Mayhew]
- 701f343ee5 2021-10-18 | [Fixes issues with EXTINF duration conversion to microseconds](https://github.com/google/ExoPlayer/commit/701f343ee5) [Steve Mayhew]
- d3bba3b0e6 2021-09-10 | [Implements SeekParameters.*_SYNC variants for HLS](https://github.com/google/ExoPlayer/commit/d3bba3b0e6) [Steve Mayhew]
- 5689e093da 2021-08-04 | [Set HlsSampleStreamWrapper.trackType for audio-only playlists](https://github.com/google/ExoPlayer/commit/5689e093da) [christosts]
- 4b1609d569 2021-08-04 | [Set HlsSampleStreamWrapper.trackType for audio-only playlists](https://github.com/google/ExoPlayer/commit/4b1609d569) [christosts]
- a035c2e20a 2019-12-17 | [Reformat some javadoc on Cue](https://github.com/google/ExoPlayer/commit/a035c2e20a) [ibaker]



#### All Changes Since 2.11.6 Merge

* 18f824064b 2022-07-28 | [Seek nearest SYNC does not adjust stale playlists](https://github.com/tivocorp/exoplayerprvt/commit/18f824064b) [Steve Mayhew]
* b7850d322a 2022-06-30 | [Allows discard of overlapping iFrame only chunks](https://github.com/tivocorp/exoplayerprvt/commit/b7850d322a) [Steve Mayhew]
* dba164829f 2022-05-27 | [Validates HLS media playlist updates](https://github.com/tivocorp/exoplayerprvt/commit/dba164829f) [Steve Mayhew]
* e5b5ed8817 2021-06-17 | [Forward FRAME-RATE from the master playlist to renditions](https://github.com/tivocorp/exoplayerprvt/commit/e5b5ed8817) [christosts]
* b8ae541396 2022-04-06 | [Updated back to Google cache time](https://github.com/tivocorp/exoplayerprvt/commit/b8ae541396) [Steve Mayhew]
* e71e21b39a 2022-04-04 | [Supports dual-mode playlist updates](https://github.com/tivocorp/exoplayerprvt/commit/e71e21b39a) [Steve Mayhew]
* d68d503b70 2022-04-04 | [Playlist `baseUri` matches curated uri](https://github.com/tivocorp/exoplayerprvt/commit/d68d503b70) [Steve Mayhew]
* 00476f43ca 2022-04-04 | [Validates playlist times to MS only](https://github.com/tivocorp/exoplayerprvt/commit/00476f43ca) [Steve Mayhew]
* cd73550814 2022-03-31 | [Update with review changes.](https://github.com/tivocorp/exoplayerprvt/commit/cd73550814) [Steve Mayhew]
* ea83607178 2022-03-31 | [Removes work-around for Vecima PDT issue](https://github.com/tivocorp/exoplayerprvt/commit/ea83607178) [Steve Mayhew]
* ff7088ccc3 2022-03-18 | [WSIPCL-12356: Almost all channels some time the trick play FF restarts from the beginning](https://github.com/tivocorp/exoplayerprvt/commit/ff7088ccc3) [mdobrzyn71]
* 3ab1b037b9 2022-01-26 | [Issues forced seek if no render in 8 * target FPS](https://github.com/tivocorp/exoplayerprvt/commit/3ab1b037b9) [Steve Mayhew]
* 1746aa8bf6 2021-12-10 | [Timestamp init wait occurs after dataSource.open()](https://github.com/tivocorp/exoplayerprvt/commit/1746aa8bf6) [Steve Mayhew]
* 5c6165251f 2021-12-07 | [Uses correct index for playlist URL](https://github.com/tivocorp/exoplayerprvt/commit/5c6165251f) [Steve Mayhew]
* c8e164c998 2021-10-19 | [Uses parseTimeSecondsToUs for EXT-X-START](https://github.com/tivocorp/exoplayerprvt/commit/c8e164c998) [Steve Mayhew]
* a4ca1102a5 2021-10-18 | [Fixes issues with EXTINF duration conversion to microseconds](https://github.com/tivocorp/exoplayerprvt/commit/a4ca1102a5) [Steve Mayhew]
* d480c1d7d1 2021-10-15 | [Uses MediaPeriod relative positionUs](https://github.com/tivocorp/exoplayerprvt/commit/d480c1d7d1) [Steve Mayhew]
* 0075060ee7 2021-09-28 | [Removes failed "pseudo GAP tag" support](https://github.com/tivocorp/exoplayerprvt/commit/0075060ee7) [Steve Mayhew]
* 319887bdd7 2021-09-20 | [Fixes WSIPCL-11330 - FFWD stuck at SOCU start](https://github.com/tivocorp/exoplayerprvt/commit/319887bdd7) [Steve Mayhew]
* 8e93c4463b 2021-09-10 | [Implements SeekParameters.*_SYNC variants for HLS](https://github.com/tivocorp/exoplayerprvt/commit/8e93c4463b) [Steve Mayhew]
* 2ee3c702c6 2021-08-04 | [Set HlsSampleStreamWrapper.trackType for audio-only playlists](https://github.com/tivocorp/exoplayerprvt/commit/2ee3c702c6) [christosts]
* 436ae8e3f8 2021-06-15 | [HLS: Fix issue where new init segment would not be loaded](https://github.com/tivocorp/exoplayerprvt/commit/436ae8e3f8) [olly]
*   7d885f7a2c 2021-06-30 | [Merge branch 'release' into t-merge-google-2.12.3-to-release](https://github.com/tivocorp/exoplayerprvt/commit/7d885f7a2c) [Steve Mayhew]
|\  
| * 1b144af7c4 2021-06-14 | [Allow multiple PDT to update the playlist startTimeUs](https://github.com/tivocorp/exoplayerprvt/commit/1b144af7c4) [Steve Mayhew]
| * 1df6198360 2021-06-22 | [T merge google commit 2536222fbd (#153)](https://github.com/tivocorp/exoplayerprvt/commit/1df6198360) [ipichkov]
| * a601378039 2021-06-10 | [no infinite stall if GAP > maxBufferMs](https://github.com/tivocorp/exoplayerprvt/commit/a601378039) [Steve Mayhew]
* | 8e227a1eab 2021-05-26 | [Merge branch 'google-2.12.3' into t-merge-google-2.12.3-to-release](https://github.com/tivocorp/exoplayerprvt/commit/8e227a1eab) [Steve Mayhew]
|/  
* 66bd5ec8b8 2021-04-14 | [Fix issue with VOD iframe only with no EXT-X-MAP (#134)](https://github.com/tivocorp/exoplayerprvt/commit/66bd5ec8b8) [Steve Mayhew]
* 7649db00e2 2021-04-01 | [Dual Mode VTP Phase 0 (#130)](https://github.com/tivocorp/exoplayerprvt/commit/7649db00e2) [Steve Mayhew]
* 5d7fa23401 2021-03-24 | [Fix issues with Metronet out of sync discontinuity sequence updates. (#129)](https://github.com/tivocorp/exoplayerprvt/commit/5d7fa23401) [Steve Mayhew]
* 6c60787e2b 2021-01-21 | [implement setting default HLS live-offset](https://github.com/tivocorp/exoplayerprvt/commit/6c60787e2b) [Steve Mayhew]
* 9a3d9f1006 2021-01-14 | [The playlist does not provide any closed caption information. We preemptively declare a closed caption track on channel 0.](https://github.com/tivocorp/exoplayerprvt/commit/9a3d9f1006) [sneelavara]
* 2c8277eda1 2020-10-22 | [Map HLS sample formats to the correct codec string](https://github.com/tivocorp/exoplayerprvt/commit/2c8277eda1) [aquilescanta]
* d7a5f59ca4 2020-10-22 | [Allow multiple codecs with same type in DefaultHlsExtractorFactory](https://github.com/tivocorp/exoplayerprvt/commit/d7a5f59ca4) [aquilescanta]
* 90bdab778d 2020-07-30 | [Remove Bad PlayList StartTime Exception](https://github.com/tivocorp/exoplayerprvt/commit/90bdab778d) [jparekh-tivo]
* a1177d48eb 2020-07-27 | [Merge fix for PARTDEFECT-3462](https://github.com/tivocorp/exoplayerprvt/commit/a1177d48eb) [jparekh-tivo]
* 3697c5cce8 2020-06-08 | [Add a test for 33-bit HLS WebVTT wraparound](https://github.com/tivocorp/exoplayerprvt/commit/3697c5cce8) [ibaker]
* 6cfb62fe1e 2020-06-05 | [Respect 33-bit wraparound when calculating WebVTT timestamps in HLS](https://github.com/tivocorp/exoplayerprvt/commit/6cfb62fe1e) [ibaker]
* 61ea81fb1e 2020-06-26 | [Use AVERAGE_BANDWIDTH where it is avialable over BANDWIDTH](https://github.com/tivocorp/exoplayerprvt/commit/61ea81fb1e) [Steve Mayhew]

#### Other Changes

The support for HLS Dual Mode required some added method to the Google HLS Playlist code,`HlsMediaPlaylist`, to clone a playlist with changes.  These commits include these changes with test cases covering them all:

* [e71e21b39a](https://github.com/tivocorp/exoplayerprvt/commit/e71e21b39a) Tue Apr 12 12:23:31 2022 -0700 Steve Mayhew Supports dual-mode playlist updates
* [d68d503b70](https://github.com/tivocorp/exoplayerprvt/commit/d68d503b70) Tue Apr 12 12:23:31 2022 -0700 Steve Mayhew Playlist `baseUri` matches curated uri

Our own work around for Vecima playlist updates, covered by unit test but not shared:

* [dba164829f](https://github.com/tivocorp/exoplayerprvt/commit/dba164829f) Tue May 31 15:43:50 2022 -0700 Steve Mayhew Validates HLS media playlist updates
* [cd73550814](https://github.com/tivocorp/exoplayerprvt/commit/cd73550814) Fri Apr 1 08:23:49 2022 -0700 Steve Mayhew Update with review changes.
* [ea83607178](https://github.com/tivocorp/exoplayerprvt/commit/ea83607178) Fri Apr 1 08:23:49 2022 -0700 Steve Mayhew Removes work-around for Vecima PDT issue
* [00476f43ca](https://github.com/tivocorp/exoplayerprvt/commit/00476f43ca) Tue Apr 5 13:51:51 2022 -0700 Steve Mayhew Validates playlist times to MS only

The net of these two changes brings us back to Googles vesion, so basically a no-op:

* [b8ae541396](https://github.com/tivocorp/exoplayerprvt/commit/b8ae541396) Tue Apr 12 12:23:31 2022 -0700 Steve Mayhew Updated back to Google cache time
* [ff7088ccc3](https://github.com/tivocorp/exoplayerprvt/commit/ff7088ccc3) Wed Mar 23 10:45:10 2022 -0700 mdobrzyn71 WSIPCL-12356: Almost all channels some time the trick play FF restarts from the beginning

The changes to `HlsSampleStreamWrapper` in this commit can be ignored, they only support logging:

* [3ab1b037b9](https://github.com/tivocorp/exoplayerprvt/commit/3ab1b037b9) Mon Mar 21 08:09:55 2022 -0700 Steve Mayhew Issues forced seek if no render in 8 * target FPS

####  Auto-Merges

```
library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsSampleStreamWrapper.java
library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsMediaPeriod.java
library/hls/src/main/java/com/google/android/exoplayer2/source/hls/DefaultHlsExtractorFactory.java
```



## Appendix A - Resolve Script

The file `initial-merge-script.sh` performs all of the default take theirs/ours resolutions, in addition for a handful of files in the `library-core` it manually merges using patch:

1. partially resolve with "take theirs"
2. apply our changes, matched up to the new API's if required with a patch

Simply run this file after the initial merge, and perform the manual resolves described in the section above.

## Appendix B - Handy Git Commands

To see changes to a file in our local `release` branch that are not in Google's release r2.15.1 use:

````
git log --pretty=format:"%h %ad | %s%d [%an]" --date=short release ^t-google-release-v2-r2.15.1 -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImplInternal.java
````

Note, if the change was "cherry-picked" into our branch from a Google commit the hash will be different, but the commit should be the same (unless their was a resolve on the cherry-pick).  To check this use grep, example:

````
git log --pretty=oneline  release ^t-google-release-v2-r2.15.1 -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImplInternal.java
ec8245400f9fbe8d52b1e37b207bdde61ffcb0f8 Require playback to be stuck for a minimum period before failing
4ff8ed604a00ff2f043a095b29aa2631947812ec Enable release timeout by default and make config non-experimental.
````

Then grep for the commit message:

````
 git log --pretty=format:"%H %ad %s%d [%an]" --date=short t-google-release-v2-r2.15.1 | grep "Enable release timeout"
008c80812b06384b416649196c7601543832cc13 2020-10-06 Enable release timeout by default and make config non-experimental. [tonihei]
````

Once you have done a merge, use this comand to show any files that are conflicted and not yet resolved and staged.

````
git diff --name-only --diff-filter=U --relative
````

If there are local changes of ours that cannot (or simply have not yet) been shared with a pull request, generate a "take theirs" in the merge script and a patch.   To create the patch:

1. resolve the conflict manually

2. build and run test cases

3. create the patch with a command like:
   ````
   git diff --no-ext-diff t-google-release-v2-r2.15.1-with-cherry-picks --  library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsSampleStreamWrapper.java > tivo-docs/exo_2.15.1/HlsSampleStreamWrapper.patch
   ````

   
