#!/bin/bash
SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

######################
# Top level project resolve
######################
git checkout --ours -- build.gradle
git add --ignore-errors -A -f -- build.gradle
git checkout --theirs -- constants.gradle
git add --ignore-errors -A -f -- constants.gradle
git checkout --ours -- publish.gradle
git add --ignore-errors -A -f -- publish.gradle
git checkout --theirs -- gradle/wrapper/gradle-wrapper.properties
git add --ignore-errors -A -f -- gradle/wrapper/gradle-wrapper.properties
# will fix up later
git checkout --ours -- RELEASENOTES.md
git add --ignore-errors -A -f -- RELEASENOTES.md

######################
# library-common -- will add our own version number after
######################
git checkout --theirs -- library/common/src/main/java/com/google/android/exoplayer2/ExoPlayerLibraryInfo.java
git add --ignore-errors -A -f -- library/common/src/main/java/com/google/android/exoplayer2/ExoPlayerLibraryInfo.java
git checkout --theirs -- library/common/src/main/java/com/google/android/exoplayer2/util/TimestampAdjuster.java
git add --ignore-errors -A -f -- library/common/src/main/java/com/google/android/exoplayer2/util/TimestampAdjuster.java

######################
# library-extractor -- will fixup after with pull request/cherry-pick of NALU change
######################
git checkout --theirs -- library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/PsExtractor.java
git add --ignore-errors -A -f -- library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/PsExtractor.java
git checkout --theirs -- library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/TsExtractor.java
git add --ignore-errors -A -f -- library/extractor/src/main/java/com/google/android/exoplayer2/extractor/ts/TsExtractor.java

######################
# library-core -- many fixups needed, see the HANDLING_2.15.1_MERGE_CONFLICTS.md doc
######################
#
# take upstream (theirs) versions, unconditionally
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/BaseRenderer.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/BaseRenderer.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayer.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayer.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImpl.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImpl.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImplInternal.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerImplInternal.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/SimpleExoPlayer.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/SimpleExoPlayer.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/source/UnrecognizedInputFormatException.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/source/UnrecognizedInputFormatException.java
git rm -- library/core/src/main/java/com/google/android/exoplayer2/ExoPlayerFactory.java
#
# take upstream (theirs), but some review is required
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/mediacodec/AsynchronousMediaCodecBufferEnqueuer.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/mediacodec/AsynchronousMediaCodecBufferEnqueuer.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/audio/DefaultAudioSink.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/audio/DefaultAudioSink.java
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/drm/DefaultDrmSessionManager.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/drm/DefaultDrmSessionManager.java
#
# again use theirs, our changes for session management need to be re-implemented (and shared)
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/analytics/DefaultPlaybackSessionManager.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/analytics/DefaultPlaybackSessionManager.java
git checkout --theirs -- library/core/src/test/java/com/google/android/exoplayer2/analytics/PlaybackStatsListenerTest.java
git add --ignore-errors -A -f -- library/core/src/test/java/com/google/android/exoplayer2/analytics/PlaybackStatsListenerTest.java

#
# Added API for non-default PlaybackSessionManager, this needs a pull requst. An RFE bug is alread submitted
#
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/analytics/PlaybackStatsListener.java
patch -p0 library/core/src/main/java/com/google/android/exoplayer2/analytics/PlaybackStatsListener.java ${SCRIPTPATH}/PlaybackStatsListener.patch
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/analytics/PlaybackStatsListener.java

#
# "theirs" is our shared pull request for tunneling fixes
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/mediacodec/MediaCodecRenderer.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/mediacodec/MediaCodecRenderer.java
#
# take theirs, need patch or cherry-pick with our service block fixes
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/text/cea/Cea708Decoder.java
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/text/cea/Cea708Decoder.java

#
# take theirs, patch in our change, but fixed for new API for creating a ParserException
git checkout --theirs -- library/common/src/main/java/com/google/android/exoplayer2/audio/AacUtil.java
patch library/common/src/main/java/com/google/android/exoplayer2/audio/AacUtil.java -p0 -<<'EOF'
--- a/library/common/src/main/java/com/google/android/exoplayer2/audio/AacUtil.java
+++ b/library/common/src/main/java/com/google/android/exoplayer2/audio/AacUtil.java
@@ -347,11 +347,16 @@ public final class AacUtil {
     int samplingFrequency;
     int frequencyIndex = bitArray.readBits(4);
     if (frequencyIndex == AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY) {
+      if (bitArray.bitsLeft() < 24) {
+        throw ParserException.createForMalformedContainer(
+            /* message= */ "AAC header insufficient data", /* cause= */ null);
+      }
       samplingFrequency = bitArray.readBits(24);
     } else if (frequencyIndex < 13) {
       samplingFrequency = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
     } else {
-      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
+      throw ParserException.createForMalformedContainer(
+          /* message= */ "AAC header wrong Sampling Frequency Index", /* cause= */ null);
     }
     return samplingFrequency;
   }
EOF
git add --ignore-errors -A -f -- library/common/src/main/java/com/google/android/exoplayer2/audio/AacUtil.java

#
#
git checkout --theirs -- library/core/src/test/java/com/google/android/exoplayer2/ExoPlayerTest.java
git add --ignore-errors -A -f -- library/core/src/test/java/com/google/android/exoplayer2/ExoPlayerTest.java

#
# Add our fix to AudioCapabilitiesReceiver manually
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/audio/AudioCapabilitiesReceiver.java
patch -p0 library/core/src/main/java/com/google/android/exoplayer2/audio/AudioCapabilitiesReceiver.java ${SCRIPTPATH}/AudioCapabilitiesReceiver.patch
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/audio/AudioCapabilitiesReceiver.java

#
# Our unshared changes to track selection (tunneling, trickplay tracks include)
git checkout --theirs -- library/core/src/main/java/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.java
patch -p0 library/core/src/main/java/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.java ${SCRIPTPATH}/DefaultTrackSelector.patch
git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.java

#
# These require a manual merge, the auto 
#git add --ignore-errors -A -f -- library/core/src/main/java/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.java


######################
# library-hls -- much of this requires manual mergess then generate a patch file for our un-shared changes
######################
#e
# take upstream (theirs) versions, unconditionally
git checkout --theirs -- library/hls/src/test/java/com/google/android/exoplayer2/source/hls/playlist/HlsMediaPlaylistParserTest.java
git add --ignore-errors -A -f -- library/hls/src/test/java/com/google/android/exoplayer2/source/hls/playlist/HlsMediaPlaylistParserTest.java
git checkout --theirs -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/playlist/HlsPlaylistParser.java
git add --ignore-errors -A -f -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/playlist/HlsPlaylistParser.java

#
# take theirs (merge source), our changes are correctly cherry-picked in
git checkout --theirs -- library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsChunkSourceTest.java
git add --ignore-errors -A -f -- library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsChunkSourceTest.java

#
# take theirs, log timestamp error and ignore load error for VTP segments patched in
git checkout --theirs -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsSampleStreamWrapper.java 
patch -p0 library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsSampleStreamWrapper.java ${SCRIPTPATH}/HlsSampleStreamWrapper.patch
git add --ignore-errors -A -f -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsSampleStreamWrapper.java 

#
# Take theirs, fix up the VCAS chagnges with a patch
git checkout --theirs -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsChunkSource.java
patch -p0 library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsChunkSource.java ${SCRIPTPATH}/HlsChunkSource.patch
git add --ignore-errors -A -f -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsChunkSource.java

git checkout --theirs -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsMediaChunk.java
patch -p0 library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsMediaChunk.java ${SCRIPTPATH}/HlsMediaChunk.patch
git add --ignore-errors -A -f -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/HlsMediaChunk.java

# also test case (added as cherry-pick from pull request) needs keyUri parameter, it is not conflicted but will not build
#
patch library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsMediaChunkTest.java -p0 -<<'EOF2'
diff --git a/library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsMediaChunkTest.java b/library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsMediaChunkTest.java
index b96f4fece9..455de96036 100644
--- a/library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsMediaChunkTest.java
+++ b/library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsMediaChunkTest.java
@@ -251,6 +251,7 @@ public class HlsMediaChunkTest {
           null,
           null,
           null,
+          null,
           shouldSpliceIn);
   }
 }
EOF2
git add -- library/hls/src/test/java/com/google/android/exoplayer2/source/hls/HlsMediaChunkTest.java

#
# Take theiss, patch in the Dual Mode changes and isUpdateValid() not shared with Google
git checkout --theirs -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/playlist/HlsMediaPlaylist.java
patch -p0 library/hls/src/main/java/com/google/android/exoplayer2/source/hls/playlist/HlsMediaPlaylist.java ${SCRIPTPATH}/HlsMediaPlaylist.patch
git add --ignore-errors -A -f -- library/hls/src/main/java/com/google/android/exoplayer2/source/hls/playlist/HlsMediaPlaylist.java



