## Reverse Visual Trick-play URLS

Results of testing multiple devices with VTP with the following test case URLs:

1. *Vecima fMP4* &mdash; http://frumos01.tivo.com/rolling-buffer/kqedplus/kqedplus/transmux/index.m3u8?ccur_fmt_type=fmp4
1. *Vecima TS* &mdash; http://frumos01.tivo.com/rolling-buffer/kqedplus/kqedplus/transmux/index.m3u8?ccur_fmt_type=ts
1. *Velocix (TS)* &mdash; http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr

<style>
table th {
  background-color: #F0F0F0;
}
table td:nth-child(1) {
	width: 19%
}table td:nth-child(1) {
	width: 19%
}
table tr:nth-child(1),tr:nth-child(3) { 
background-color: #a2f28a
}
table tr:nth-child(2) { 
background-color: #f5dbae
}
table tr:nth-child(6) { 
background-color: #f5dbae
}
table tr:nth-child(4),tr:nth-child(5) { 
background-color: #f28a8e
}
</style>

| URL  | Platform  | Outcome |
|:-------------|:---------------:|:-------------|
| Velocix (TS)     | SEI-500 | Intial start sometimes fails, otherwise average FPS is >5       |
| Vecima (TS)     | SEI-500 | Timestamp errors, Vecima issue [PARTDEFECT-10647](https://jira.tivo.com/browse/PARTDEFECT-10647)   |
| Vecima (fMP4)     | SEI-500 | near 6 FPS, some minor stalls  |
| Velocix (TS)     | Jade, vip6102w | Fails to render consistently, see Note 1 below and [PARTDEFECT-11896](https://jira.tivo.com/browse/PARTDEFECT-11896)  |
| Vecima (TS)     | Jade, vip6102w | Same issue as Velocix   |
| Vecima (MP4)     | Jade | Similar issue to TS, also getting `java.net.ProtocolException` on downloads   |
| Vecima (MP4)     | vip6102 | Delays in render still, see note 1.  No   |


### Notes

#### Note 1 - Broadcom decoder delay
Renders often take up to multiple seconds from seek to frame rendered.  This appears to be a codec issue.

````
01-24 17:15:11.455 10654 10654 D ScrubTrickPlay: scrubSeek() - SCRUB called, position: 1782507 lastPosition: -9223372036854775807 isMoveThreshold: true renderPending: false
01-24 17:15:11.456 10654 10654 D ScrubTrickPlay: executeSeek() - issue seek, to positionMs: 1782507 currentPositionMs: 1786507
01-24 17:15:11.491 10654 10654 D ScrubTrickPlay: onPositionDiscontinuity() - reason: 1 position: 1782507 lastPosition: -9223372036854775807 renderPending: true
01-24 17:15:11.602 10654 10708 D TrickPlayAwareMediaCodecVideoRenderer: onPositionReset() -  readPosUs: 1782507000 lastRenderTimeUs: -9223372036854775807
01-24 17:15:12.094 10654 10708 D TrickPlayAwareMediaCodecVideoRenderer: queueInputBuffer: timeMs: 1781801 length: 200246 buffersInCodec: 1 isKeyFrame: true isDecodeOnly: true isDiscontinuity: false
01-24 17:15:15.418 10654 10654 D ScrubTrickPlay: scrubSeek() - SCRUB called, position: 1780507 lastPosition: 1782507 isMoveThreshold: true renderPending: true
01-24 17:15:15.492 10654 10708 D TrickPlayAwareMediaCodecVideoRenderer: queueInputBuffer: timeMs: 1783803 length: 191606 buffersInCodec: 2 isKeyFrame: true isDecodeOnly: false isDiscontinuity: false
01-24 17:15:15.916 10654 10708 D TrickPlayAwareMediaCodecVideoRenderer: queueInputBuffer: timeMs: 1787807 length: 184588 buffersInCodec: 3 isKeyFrame: true isDecodeOnly: false isDiscontinuity: false
01-24 17:15:15.927 10654 10708 D TrickPlayAwareMediaCodecVideoRenderer: processOutputBuffer: positionMs: 1782507, bufferTimeMs: 1781801 length: 8 flags: 0 isDecodeOnly: true
01-24 17:15:15.930 10654 10708 D TrickPlayAwareMediaCodecVideoRenderer: renderOutputBufferV21() in trickplay - pts: 1781801367 releaseTimeUs: 23079304264 index:1 timeSinceLastUs: -9223372036854775807
01-24 17:15:15.960 10654 10654 D ScrubTrickPlay: trickFrameRendered() - position: 1781801, seekToRender(ms): 4503
````