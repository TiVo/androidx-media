# Handling 2.15.1 Merge Conflicts

For our upcoming merge of Google's `r2.15.1` release tag  to our `release` branch there are conflicts in a few categories:

1. Re-resolve of conflicts for change we have made to `r2.12.3`, `r2.11.6` that have not been integrated to Google's code
2. Changes since our last merge (merge of Google's `r2.12.3`)

This document lists each of our changes post r2.13.3 merge, by module, and a plan to incorporate them.  Also the changes that have been cherry-picked from Google from between 2.13.3 and 2.15.1 and post 2.15.1 are covered in the sections "Cherry Picked From Future"'

The two branches to work with are:

1. `t-google-release-v2-r2.15.1` &mdash; Read-only checkout of Google's tree at `r2.15.1`, this is our upstream merge source
2. `t-merge-google-release-r2.15.1`  &mdash; This is our merge target, what will eventually be a non-conflicted pull request to merge back to our `release`.  Will keep this branch re-based to release (note, this may require locking down the `release` branch a bit to insure a clean pull request)

So, the basic workflow is to:

1. checkout latest `t-merge-google-release-r2.15.1` 
2. do `git merge t-google-release-v2-r2.15.1` 
3. work on conflicts, write up resulting resolution
4. abort the merge `git merge --abort`

## Top Level Project 

The commit ` [local gradle changes in separate file facilitate merges](https://github.com/tivocorp/exoplayerprvt/commit/709fdae964f7eee69808a99b7188a556d68ca741) aligns our version to match 2.15.1 closely enough that the resolve is fairly simple for the 4 conflicts

### Merge Conflicts

Resolve with these steps, details follow.

```
git checkout --ours -- build.gradle
git checkout --ours -- publish.gradle
git checkout --theirs -- gradle/wrapper/gradle-wrapper.properties
git checkout --theirs -- constants.gradle
```

#### Take Theirs

- **gradle/wrapper/gradle-wrapper.properties**  we pulled forward update [Update to Gradle plugin 4.1](https://github.com/google/ExoPlayer/commit/f4f312738b), but 2.15.1 later updated it again ([Bump dependency versions](https://github.com/google/ExoPlayer/commit/dea52048cb)) to 6.9, so the final version should be 6.9 (take theirs)
- **constants.gradle**   Our version will be 2.15.1-1.0, our first 2.15.1 release.  Otherwise take theirs.

#### Take Ours

- **publish.gradle**  Ours now matches their pattern, but only publishs to our Artifactory, so simply overwrite with ours to resolve

- **build.gradle** &mdash; Ours adds our artifactory and removes jCenter() (both are same line, hence the conflict).  We removed jCenter to prevent our build from failing periodically when we could not reach jCenter quickly enough.  

## Module`library-common` 

### Merge Conflicts

Resolve with these steps, details follow.

```
git checkout --theirs -- library/common/src/main/java/com/google/android/exoplayer2/ExoPlayerLibraryInfo.java
git checkout --theirs -- library/common/src/main/java/com/google/android/exoplayer2/util/TimestampAdjuster.java
```



#### Take Theirs

- **TimestampAdjuster**  &mdash; We cherry-picked in this commit: [Fix issues with Metronet out of sync discontinuity sequence updates](https://github.com/tivocorp/exoplayerprvt/commit/5d7fa23401e72f9670f9fa4a4db593a4c091d22d), from this commit [HLS: Allow audio variants to initialize the timestamp adjuster](https://github.com/google/ExoPlayer/commit/6f8a8fbc1c).   Since the cherry-pick there have been a number of changes that conflict, up to the most recent: [HLS: Avoid stuck-buffering issues](https://github.com/google/ExoPlayer/commit/8732f2f030)
- **ExoPlayerLibraryInfo** &mdash; Our local update to the version can be done once we have a good build, for now take theirs

#### Merge but Open Pull Request

- **AacUtil.java**  &mdash; Our change [Adding bound check for AAC audioConfig input buffer](https://github.com/tivocorp/exoplayerprvt/commit/1b3c24bd2d769128ce881913222a82013a412c37) is a modified cherry-pick of [Throw ParserException if AAC config is invalid](https://github.com/google/ExoPlayer/commit/6db6d14b16c1324bde1677cce8cfa3571d201d9a).  This needs to be rectified with a pull request for Google, perhaps we can suggest new exception types other then `ParserException` like, audio stream invalid?

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

#### Other

These changes will need a test case to verify, the code auto-merged

* [aeed7dc0a4](https://github.com/tivocorp/exoplayerprvt/commit/aeed7dc0a4) Tue Apr 5 23:01:32 2022 -0700 mbolaris Workaround to handle 5-channel AC3 audio.


## Module `library-core`

### Merge Conflicts

The merge conflicts in this `library-core` in some cases require a bit more study, they are grouped below into categories of the proposed action.  This basic first set of commands does the take ours options, detaills are described in "Take Theirs" section:

```
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/source/UnrecognizedInputFormatException.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/SimpleExoPlayer.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImplInternal.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImpl.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayer.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/BaseRenderer.java
```


#### Take Theirs

Here is the list of conflicted files where we simply take the upstream version, these are caused by cherry-picks we have taken that are already merged in some cases.  We will need to re-cherry-pick the change [Require playback to be stuck for a minimum period before failing](https://github.com/google/ExoPlayer/commit/c19d1da6ed)

- **BaseRenderer**  &mdash; formatting change were we reverted out making method protected

- **ExoPlayer**, **ExoPlayerImpl**, **SimpleExoPlayer** and **ExoPlayerImplInternal** &mdash; the experimental stuck buffering code was removed (a good thing, this was causing issues with seek in paused mode (SCRUB)), this conflicted with our cherry pick of the release timeout API (which is now included.)
- **ExoPlayerFactory.java**  &mdash; This was deleted (after being deprecated), replaced with `SimpleExoPlayer.Builder()` which we are using everywhere now.
- **UnrecognizedInputFormatException** &mdash; The conflicted constructor is no longer used.  The original change, [Improved exception handling](https://github.com/tivocorp/exoplayerprvt/commit/c6fb6c5e7f) is no longer valid, worthy of a larger project to share with Google to improve diagnosing `ParserException`

#### Take Theirs and Rework

Here the conflicts are so massive that it is best to simply take Googles version and either re-think (can we avoid changing their code) or re-implment our required change with new code.

- **trackselection.DefaultTrackSelector.java** &mdash; The track selector was re-worked with "builder pattern" to replace large constructor argument lists.  This lead to pretty extensive conflicts because of Googles changes, so see the action under "Our Changes" below.

#### Basic Resolve

Simple merge with some textual fix-ups

- **audio.AudioCapabilitiesReceiver**   &mdash; formatting change broke our added `MediaRouter.Callback` bluetooth speaker support that Google did not take, worth revistiing why.

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

1. **Track Selection Change** &mdash; [163be9ed76](https://github.com/tivocorp/exoplayerprvt/commit/163be9ed76) Thu Jul 7 12:11:34 2022 -0700 mbolaris Added setApplyConstraintsFrameRate() track selection parameter
2. **Custom Logger** [2a9ef44fa1](https://github.com/tivocorp/exoplayerprvt/commit/2a9ef44fa1) Thu Apr 21 10:49:51 2022 -0700 Shashikant Enabls using a custom logger instead of android.util.Log
3. **Buffered Tunneling Fix** &mdash;[31fb98bd50](https://github.com/tivocorp/exoplayerprvt/commit/31fb98bd50) Tue Mar 15 15:43:25 2022 -0700 Spencer Alves Fix for WSIPCL-12725, regression for buffering state in tunneling mode

##### Buffered Tunneling Fix

One method we were calling, `MediaCodecRenderer.getLargestQueuedPresentationTimeUs()` was removed by Google in this 2.13.0 commit, [Move last-buffer timestamp fix to better location](https://github.com/google/ExoPlayer/commit/1fb675e8769357ec161bcf268d5981ef1e108e25).  The auto-merge removes this method from our `MediaCodecRenderer` so our change to `MediaCodecVideoRenderer` no longer compiles.  Fix is to put it back in `MediaCodecRenderer`

```java
  /** Returns the largest queued input presentation time, in microseconds. */
  protected final long getLargestQueuedPresentationTimeUs() {
    return largestQueuedPresentationTimeUs;
  }

```

This code obviously needs to be retested, perhaps we can get a pull request for the fix that  Google will merge.

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

This set of changes are to the `DefaultTrackSelector`, the conflicts are numerous and too risky to merge.  Best to re-implement after the merge.  

For 1 and 2 we can add code to achive the same result wihtout modifying core, while we wait for the outcome of the pull request, [Added setApplyConstraintsFrameRate() track selection parameter](https://github.com/google/ExoPlayer/pull/10499).   Based on [Google's comment](https://github.com/google/ExoPlayer/pull/10499#issuecomment-1204900397) I would estimate the chances of changing the API as very low, so better to use the first suggestion (prune tracks with overrides)

The change for tunneling can be re applied after a "take theirs" merge, we also need a long term solution for this (a pull request).


1. [163be9ed76](https://github.com/tivocorp/exoplayerprvt/commit/163be9ed76) Thu Jul 7 12:11:34 2022 -0700 mbolaris Added setApplyConstraintsFrameRate() track selection parameter
2. [e21ace4164](https://github.com/tivocorp/exoplayerprvt/commit/e21ace4164) Tue Mar 22 14:17:53 2022 -0700 mbolaris Removed spacing only diff. Added comments to help with future Exo merging.
3. [cb738131f9](https://github.com/tivocorp/exoplayerprvt/commit/cb738131f9) Tue Mar 22 14:17:53 2022 -0700 mbolaris Changes for Broadcom tunneling mode no black flash seek-based VTP.
4. [2aae14403b](https://github.com/tivocorp/exoplayerprvt/commit/2aae14403b) Tue Mar 22 14:17:53 2022 -0700 mbolaris Remain in tunneling mode with just video when the platform has tunneling VTP support.



## Module `library-extractor`

### Merge Conflicts

#### Basic Resolve

- **TsExtractor**, **PsExtractor** &mdash; take theirs on the conflict (changes to seek() code we don't use)
- **SectionReader** &mdash; take ours, but resolve spacing issue that causes the conflict
- **ElementaryStreamReader** &mdash; take ours, but resolve spacing issue that causes the conflict

#### Take Theirs

- **FragmentedMp4Extractor**  simply a cherry-pick that came full circle.

### Auto Merges

These are all the result of the code in the open pull requests.

```
library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/TsPayloadReader.java
library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/PesReader.java
library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/H264Reader.java
```

### Our Changes

#### In Open Pull request

There is a Jira story to add a test for the first commit (filler data), the other commit has test case already.  Both will be submitted to Google.

1.  [059e639524](https://github.com/tivocorp/exoplayerprvt/commit/059e639524) Tue Apr 5 19:30:30 2022 -0700 mbolaris Add handling for iframe transport segments with filler data.
2. [cb3b671021](https://github.com/tivocorp/exoplayerprvt/commit/cb3b671021) Wed Dec 15 14:20:07 2021 -0800 Steve Mayhew Commits IDR NALU on end of stream

#### Changes cherry-picked from future

This change causes a conflict as we had to modify the original 2.15.0 commit:  [Fix issue where a trun atom could be associated with the wrong track [olly]](https://github.com/google/ExoPlayer/commit/4e8895d5cb) we should simply take theirs.

* [274c40bda8](https://github.com/tivocorp/exoplayerprvt/commit/274c40bda8) Wed Dec 1 14:28:27 2021 -0800 olly Fix issue where a trun atom could be associated with the wrong track

## Module `library-hls`

This is the oldest set of changes to the Google ExoPlayer code we have, there are several changes (e.g. VCAS support, TiVoCrypt) that we will never be able to share with Google.  These merge conflicts will simply need to be re-addressed. 

Some of the changes since 2.13.3 have been shared in pull requests

### Merge Conflicts

#### Resolve With Fix-ups

- **HlsChunkSource** &mdash; Our changes with conflicts resolved as follows:
  - VCAS changes - our key changes in `getNextChunk()` conflict, take ours and change `segment` to `segmentBaseHolder.segmentBase` This is because of their changes to support HLS-LL
  - Our `getAdjustedSeekPositionUs()` in same spot as `getChunkPublicationState()`, take ours then append theirs


* **HlsMediaChunk** &mdash; Our change [Allows discard of overlapping iFrame only chunks](https://github.com/tivocorp/exoplayerprvt/commit/b7850d322a) conflicts, the `shouldSpliceIn` and logic around it is moved.  Best to take their version of lines ??? and cherry pick later version from our pull request, 10484
* **HlsMediaSource** &mdash; our changes to `onPrimaryPlaylistRefreshed()`
* **HlsPlaylistParser** &mdash; our change to add `parseTimeSecondsToUs()`
* **HlsSampleStreamWrapper** &mdash; our change to add `getBufferedPositionUs()`, check for `hasSamples()`

### Our Changes

#### In Open Pull request

These changes to nearest SYNC are in pull request [10484](https://github.com/google/ExoPlayer/pull/10484), they are also covered by unit tests.

* [18f824064b](https://github.com/tivocorp/exoplayerprvt/commit/18f824064b) Mon Aug 1 19:33:31 2022 -0700 Steve Mayhew Seek nearest SYNC does not adjust stale playlists
* [b7850d322a](https://github.com/tivocorp/exoplayerprvt/commit/b7850d322a) Wed Jul 6 11:30:47 2022 -0700 Steve Mayhew Allows discard of overlapping iFrame only chunks

#### Changes cherry-picked from future

The first change was cherry picked from 2.15.1 so it is already in, the other two changes are in 2.17.0 so will need to check it.  Note it is covered by a test case.

1. [e5b5ed8817](https://github.com/tivocorp/exoplayerprvt/commit/e5b5ed8817) Fri Apr 29 07:37:22 2022 -0700 christosts Forward FRAME-RATE from the master playlist to renditions
2. [5c6165251f](https://github.com/tivocorp/exoplayerprvt/commit/5c6165251f) Tue Jan 4 11:57:23 2022 -0800 Steve Mayhew Uses correct index for playlist URL
3. [1746aa8bf6](https://github.com/tivocorp/exoplayerprvt/commit/1746aa8bf6) Tue Jan 4 17:53:01 2022 -0800 Steve Mayhew Timestamp init wait occurs after dataSource.open()

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

