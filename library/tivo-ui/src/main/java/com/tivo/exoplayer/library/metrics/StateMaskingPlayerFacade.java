package com.tivo.exoplayer.library.metrics;

import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoSize;
import java.util.List;

public class StateMaskingPlayerFacade implements Player {

  private final Player maskedPlayer;
  private final @State int state;

  public StateMaskingPlayerFacade(Player maskedPlayer, @Player.State int state) {
    this.maskedPlayer = maskedPlayer;
    this.state = state;
  }

  @Override
  @State
  public int getPlaybackState() {
    return state;
  }

  //  balance of Player interface simply delegates

  @Override
  public Looper getApplicationLooper() {
    return maskedPlayer.getApplicationLooper();
  }

  @Override
  @Deprecated
  public void addListener(EventListener listener) {
    maskedPlayer.addListener(listener);
  }

  @Override
  public void addListener(Listener listener) {
    maskedPlayer.addListener(listener);
  }

  @Override
  @Deprecated
  public void removeListener(EventListener listener) {
    maskedPlayer.removeListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    maskedPlayer.removeListener(listener);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
    maskedPlayer.setMediaItems(mediaItems);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    maskedPlayer.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
    maskedPlayer.setMediaItems(mediaItems, startWindowIndex, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    maskedPlayer.setMediaItem(mediaItem);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    maskedPlayer.setMediaItem(mediaItem, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    maskedPlayer.setMediaItem(mediaItem, resetPosition);
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    maskedPlayer.addMediaItem(mediaItem);
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    maskedPlayer.addMediaItem(index, mediaItem);
  }

  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    maskedPlayer.addMediaItems(mediaItems);
  }

  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    maskedPlayer.addMediaItems(index, mediaItems);
  }

  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    maskedPlayer.moveMediaItem(currentIndex, newIndex);
  }

  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    maskedPlayer.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public void removeMediaItem(int index) {
    maskedPlayer.removeMediaItem(index);
  }

  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    maskedPlayer.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public void clearMediaItems() {
    maskedPlayer.clearMediaItems();
  }

  @Override
  public boolean isCommandAvailable(int command) {
    return maskedPlayer.isCommandAvailable(command);
  }

  @Override
  public Commands getAvailableCommands() {
    return maskedPlayer.getAvailableCommands();
  }

  @Override
  public void prepare() {
    maskedPlayer.prepare();
  }

  @Override
  @PlaybackSuppressionReason
  public int getPlaybackSuppressionReason() {
    return maskedPlayer.getPlaybackSuppressionReason();
  }

  @Override
  public boolean isPlaying() {
    return maskedPlayer.isPlaying();
  }

  @Override
  @Nullable
  public PlaybackException getPlayerError() {
    return maskedPlayer.getPlayerError();
  }

  @Override
  public void play() {
    maskedPlayer.play();
  }

  @Override
  public void pause() {
    maskedPlayer.pause();
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    maskedPlayer.setPlayWhenReady(playWhenReady);
  }

  @Override
  public boolean getPlayWhenReady() {
    return maskedPlayer.getPlayWhenReady();
  }

  @Override
  public void setRepeatMode(int repeatMode) {
    maskedPlayer.setRepeatMode(repeatMode);
  }

  @Override
  @RepeatMode
  public int getRepeatMode() {
    return maskedPlayer.getRepeatMode();
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    maskedPlayer.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    return maskedPlayer.getShuffleModeEnabled();
  }

  @Override
  public boolean isLoading() {
    return maskedPlayer.isLoading();
  }

  @Override
  public void seekToDefaultPosition() {
    maskedPlayer.seekToDefaultPosition();
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    maskedPlayer.seekToDefaultPosition(windowIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    maskedPlayer.seekTo(positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    maskedPlayer.seekTo(windowIndex, positionMs);
  }

  @Override
  public long getSeekBackIncrement() {
    return maskedPlayer.getSeekBackIncrement();
  }

  @Override
  public void seekBack() {
    maskedPlayer.seekBack();
  }

  @Override
  public long getSeekForwardIncrement() {
    return maskedPlayer.getSeekForwardIncrement();
  }

  @Override
  public void seekForward() {
    maskedPlayer.seekForward();
  }

  @Override
  @Deprecated
  public boolean hasPrevious() {
    return maskedPlayer.hasPrevious();
  }

  @Override
  public boolean hasPreviousWindow() {
    return maskedPlayer.hasPreviousWindow();
  }

  @Override
  @Deprecated
  public void previous() {
    maskedPlayer.previous();
  }

  @Override
  public void seekToPreviousWindow() {
    maskedPlayer.seekToPreviousWindow();
  }

  @Override
  public int getMaxSeekToPreviousPosition() {
    return maskedPlayer.getMaxSeekToPreviousPosition();
  }

  @Override
  public void seekToPrevious() {
    maskedPlayer.seekToPrevious();
  }

  @Override
  @Deprecated
  public boolean hasNext() {
    return maskedPlayer.hasNext();
  }

  @Override
  public boolean hasNextWindow() {
    return maskedPlayer.hasNextWindow();
  }

  @Override
  @Deprecated
  public void next() {
    maskedPlayer.next();
  }

  @Override
  public void seekToNextWindow() {
    maskedPlayer.seekToNextWindow();
  }

  @Override
  public void seekToNext() {
    maskedPlayer.seekToNext();
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    maskedPlayer.setPlaybackParameters(playbackParameters);
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    maskedPlayer.setPlaybackSpeed(speed);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return maskedPlayer.getPlaybackParameters();
  }

  @Override
  public void stop() {
    maskedPlayer.stop();
  }

  @Override
  @Deprecated
  public void stop(boolean reset) {
    maskedPlayer.stop(reset);
  }

  @Override
  public void release() {
    maskedPlayer.release();
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return maskedPlayer.getCurrentTrackGroups();
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return maskedPlayer.getCurrentTrackSelections();
  }

  @Override
  @Deprecated
  public List<Metadata> getCurrentStaticMetadata() {
    return maskedPlayer.getCurrentStaticMetadata();
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    return maskedPlayer.getMediaMetadata();
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    return maskedPlayer.getPlaylistMetadata();
  }

  @Override
  public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    maskedPlayer.setPlaylistMetadata(mediaMetadata);
  }

  @Override
  @Nullable
  public Object getCurrentManifest() {
    return maskedPlayer.getCurrentManifest();
  }

  @Override
  public Timeline getCurrentTimeline() {
    return maskedPlayer.getCurrentTimeline();
  }

  @Override
  public int getCurrentPeriodIndex() {
    return maskedPlayer.getCurrentPeriodIndex();
  }

  @Override
  public int getCurrentWindowIndex() {
    return maskedPlayer.getCurrentWindowIndex();
  }

  @Override
  public int getNextWindowIndex() {
    return maskedPlayer.getNextWindowIndex();
  }

  @Override
  public int getPreviousWindowIndex() {
    return maskedPlayer.getPreviousWindowIndex();
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem() {
    return maskedPlayer.getCurrentMediaItem();
  }

  @Override
  public int getMediaItemCount() {
    return maskedPlayer.getMediaItemCount();
  }

  @Override
  public MediaItem getMediaItemAt(int index) {
    return maskedPlayer.getMediaItemAt(index);
  }

  @Override
  public long getDuration() {
    return maskedPlayer.getDuration();
  }

  @Override
  public long getCurrentPosition() {
    return maskedPlayer.getCurrentPosition();
  }

  @Override
  public long getBufferedPosition() {
    return maskedPlayer.getBufferedPosition();
  }

  @Override
  public int getBufferedPercentage() {
    return maskedPlayer.getBufferedPercentage();
  }

  @Override
  public long getTotalBufferedDuration() {
    return maskedPlayer.getTotalBufferedDuration();
  }

  @Override
  public boolean isCurrentWindowDynamic() {
    return maskedPlayer.isCurrentWindowDynamic();
  }

  @Override
  public boolean isCurrentWindowLive() {
    return maskedPlayer.isCurrentWindowLive();
  }

  @Override
  public long getCurrentLiveOffset() {
    return maskedPlayer.getCurrentLiveOffset();
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    return maskedPlayer.isCurrentWindowSeekable();
  }

  @Override
  public boolean isPlayingAd() {
    return maskedPlayer.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return maskedPlayer.getCurrentAdGroupIndex();
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return maskedPlayer.getCurrentAdIndexInAdGroup();
  }

  @Override
  public long getContentDuration() {
    return maskedPlayer.getContentDuration();
  }

  @Override
  public long getContentPosition() {
    return maskedPlayer.getContentPosition();
  }

  @Override
  public long getContentBufferedPosition() {
    return maskedPlayer.getContentBufferedPosition();
  }

  @Override
  public AudioAttributes getAudioAttributes() {
    return maskedPlayer.getAudioAttributes();
  }

  @Override
  public void setVolume(float audioVolume) {
    maskedPlayer.setVolume(audioVolume);
  }

  @Override
  public float getVolume() {
    return maskedPlayer.getVolume();
  }

  @Override
  public void clearVideoSurface() {
    maskedPlayer.clearVideoSurface();
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    maskedPlayer.clearVideoSurface(surface);
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    maskedPlayer.setVideoSurface(surface);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    maskedPlayer.setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    maskedPlayer.clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    maskedPlayer.setVideoSurfaceView(surfaceView);
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    maskedPlayer.clearVideoSurfaceView(surfaceView);
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    maskedPlayer.setVideoTextureView(textureView);
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    maskedPlayer.clearVideoTextureView(textureView);
  }

  @Override
  public VideoSize getVideoSize() {
    return maskedPlayer.getVideoSize();
  }

  @Override
  public List<Cue> getCurrentCues() {
    return maskedPlayer.getCurrentCues();
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return maskedPlayer.getDeviceInfo();
  }

  @Override
  public int getDeviceVolume() {
    return maskedPlayer.getDeviceVolume();
  }

  @Override
  public boolean isDeviceMuted() {
    return maskedPlayer.isDeviceMuted();
  }

  @Override
  public void setDeviceVolume(int volume) {
    maskedPlayer.setDeviceVolume(volume);
  }

  @Override
  public void increaseDeviceVolume() {
    maskedPlayer.increaseDeviceVolume();
  }

  @Override
  public void decreaseDeviceVolume() {
    maskedPlayer.decreaseDeviceVolume();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    maskedPlayer.setDeviceMuted(muted);
  }
}
