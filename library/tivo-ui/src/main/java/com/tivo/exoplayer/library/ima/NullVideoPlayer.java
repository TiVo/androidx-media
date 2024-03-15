package com.tivo.exoplayer.library.ima;

import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;

/**
 *
 */
public class NullVideoPlayer implements VideoAdPlayer {

  @Override
  public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
  }

  @Override
  public void loadAd(AdMediaInfo adMediaInfo, AdPodInfo adPodInfo) {
  }

  @Override
  public void pauseAd(AdMediaInfo adMediaInfo) {
  }

  @Override
  public void playAd(AdMediaInfo adMediaInfo) {
  }

  @Override
  public void release() {
  }

  @Override
  public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
  }

  @Override
  public void stopAd(AdMediaInfo adMediaInfo) {
  }

  @Override
  public VideoProgressUpdate getAdProgress() {
    return new VideoProgressUpdate(0, 0);
  }

  @Override
  public int getVolume() {
    return 0;
  }
}
