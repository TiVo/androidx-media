# Encoding HLS Renditions

## Background

### Pantos Spec

The [Pantos Specification, RFC8216](https://datatracker.ietf.org/doc/html/rfc8216) describes how a Varaint Stream can specify a set of Renditions, where Renditons are defined as:

```
  Renditions are alternate versions of the content, such as audio produced in
  different languages or video recorded from different camera angles.
```

The basic rules for these are described in section [4.3.4.2.1 Alternate Renditions](https://datatracker.ietf.org/doc/html/rfc8216#section-4.3.4.2.1).  The player bit-rate adaptation logic chooses the Variant Stream to play and user preferences select the specific Rendition to play from the set of available Renditions. A Rendition is specified using the [EXT-X-MEDIA tag](https://datatracker.ietf.org/doc/html/rfc8216#section-4.3.4.1) and the assignment of a set of Renditions to a group in the [4.3.4.1.1 Rendition Groups](https://datatracker.ietf.org/doc/html/rfc8216#section-4.3.4.1.1) section.   

The "Rendition Groups"  and the "Alternate Renditions" sections contain some ambiguities and conflicting requirements that make it difficult or impossible to determine the set of codecs to use to play a particular Alternate Rendition based on manifest only, that is the player:

1. client logic can select the appropriate AUTOSELECT or DEFAULT (based on language or other attributes)
2. can determine the required CODEC for playback of the members of the group (all members of the group have the same codec)

Section *4.3.4.1.1 Rendition Group* specifies "MUST" requirements that satisfy number 1 but the last two paragraphs actually contradict requirement 2, e.g.

```
   A Playlist MAY contain multiple Groups of the same TYPE in order to
   provide multiple encodings of that media type.  If it does so, each
   Group of the same TYPE MUST have the same set of members, and each
   corresponding member MUST have identical attributes with the
   exception of the URI and CHANNELS attributes.
```

This first paragraph suggests how multiple channel sets for surround sound (2.1, 5.1 and 7.1 for example) can all be in the same group, by simply changing the CHANNELS count.  We strongly suggest changing the name as the player UI may note decode the channel count.  The ExoPlayer auto-select does select based on channel count matching the playback device.

The following paragraph is where the issues come in

```
   Each member in a Group of Renditions MAY have a different sample
   format.  For example, an English Rendition can be encoded with AC-3
   5.1 while a Spanish Rendition is encoded with AAC stereo.  However,
   any EXT-X-STREAM-INF tag (Section 4.3.4.2) or EXT-X-I-FRAME-STREAM-
   INF tag (Section 4.3.4.3) that references such a Group MUST have a
   CODECS attribute that lists every sample format present in any
   Rendition in the Group, or client playback failures can occur.  In
   the example above, the CODECS attribute would include
   "ac-3,mp4a.40.2".
```

Having multiple disparate audio codecs in the CODECS attribute referencing the same Rendition Group ID breaks the first requirement for selecting a DEFAULT based on EXT-X-MEDIA attributes as it is not possible for the player to uniquely  match a codec (e.g. ac-3) with the specific EXT-X-MEDIA urls that contain data in that codec format.  This breaks ExoPlayer's [chunkless prepare algorithm](https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6).  More background on this and the work arounds in ExoPlayer are in this [Issue 7877](https://github.com/google/ExoPlayer/issues/7877).

## Rendition Group Encoding

To begin playback player the player must choose an initial Varaint + Rendition.   To do this, for each Rendition Group (each unique GROUP-ID of a given TYPE), the player will:

1. Creates an track of the specified type (AUDIO, SUBTITLE, CC)
2. Attempt to determine the codec for the specified type by matching the GROUP-ID to a referenced Variant's CODEC string
3. Select the Rendition based on:
   - DEFAULT marking
   - user specified default language
   - codec support of the device

If 2 is not possible (CODEC to GROUP-ID mapping is not unique), the player must probes the stream[s] to determine the actual sample formats.

### Requirements

The player will be unable to produce a valid set of Renditions possible for playback unless the Origin/Edge follows these requirements.

The Edge/Origin will follow all MUST requirements from section  [4.3.4.1.1 Rendition Groups](https://datatracker.ietf.org/doc/html/rfc8216#section-4.3.4.1.1) for each Rendition (EXT-X-MEDIA)

- NAME for the Rendition must be unique within the group
- Only one Rendition is marked as DEFAULT=YES

### Recommendations

For optimal (least un-necessary segment loads) start of playback, the Edge/Origin SHOULD:

-  **NOT** have a different sample format for members of a Rendition Group, as allowed by the last paragraph of section  [4.3.4.1.1 Rendition Groups](https://datatracker.ietf.org/doc/html/rfc8216#section-4.3.4.1.1).  Each Variant's CODEC string must contain at most one audio codec.
- Declare all in-stream captions (CEA608 or 708)) with an `#EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS ...` tag

### Examples

The following examples, as well as the examples in Apple's [Streaming Examples](https://developer.apple.com/streaming/examples/) streams are coded correctly to avoid issues with choosing a Variant and Renditons for initial playback from the manifest alone.

#### AAC Audio w/Multiple Languages

This basic example only has a single audio codec:

```
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac-audio",LANGUAGE="en",NAME="English",AUTOSELECT=YES,DEFAULT=YES,CHANNELS="2",URI="a1/prog_index.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac-audio",LANGUAGE="sp",NAME="Spanish",CHANNELS="2",URI="a2/prog_index.m3u8"
...

#EXT-X-STREAM-INF:BANDWIDTH=2177116,CODECS="avc1.640020,mp4a.40.2",RESOLUTION=960x540,AUDIO="aac-audio"
v5/prog_index.m3u8
...
```

#### AAC and AC-3 Surround Audio w/Multiple Languages

In this example we add surround audio with backup AAC audio (required to support some mobile devices that do not support AC-3).

```
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac-audio",LANGUAGE="en",NAME="English Stereo",AUTOSELECT=YES,DEFAULT=YES,CHANNELS="2",URI="a1/prog_index.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac-audio",LANGUAGE="sp",NAME="Spanish Stereo",CHANNELS="2",URI="a2/prog_index.m3u8"

#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ac3-audio",LANGUAGE="en",NAME="English 2.1",CHANNELS="3",URI="b1a/prog_index.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ac3-audio",LANGUAGE="en",NAME="English 5.1",AUTOSELECT=YES,DEFAULT=YES,CHANNELS="6",URI="b1b/prog_index.m3u8"

#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ac3-audio",LANGUAGE="sp",NAME="Spanish 2.1",CHANNELS="3",URI="b2/prog_index.m3u8"
...

#EXT-X-STREAM-INF:BANDWIDTH=2177116,CODECS="avc1.640020,mp4a.40.2",RESOLUTION=960x540,AUDIO="ac3-audio"
v5/prog_index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2289000,CODECS="avc1.640020,ac3",RESOLUTION=960x540,AUDIO="aac-audio"
v5/prog_index.m3u8
...
```



The playlist presents  **multiple sets** of largely identical Variants each, except each references a respective AUDIO group, these Variants:

1. Have a single audio codec in the CODEC string
2. BANDWIDTH matches that of the referenced Renditions (so, for example, the AC-3 audio has higher bandwidth requirements)

This meets the encoding recommendation that "each Variant's CODEC string must contain at most one audio codec".  In addition this allows easily meeting the requirement of Section 4.3.4.2:

```
  The following attributes are defined:

      BANDWIDTH

      ...

      If all the Media Segments in a Variant Stream have already been
      created, the BANDWIDTH value MUST be the largest sum of peak
      segment bit rates that is produced by any playable combination of
      Renditions.
```

without having to over-report bandwidth requirements.

## Appendix A - Hydra Audio Track Naming

Platform Video (ExoVideoPlayer.hx and friends) construct the `AudioStreamInfo` from ExoPlayerPlayer's `TrackInfo.desc`.  This desc is a `;` separated string with:

1. label &mdash; Using the, `Format.label` set from the `EXT-X-MEDIA` tag `NAME` attribute
2. language &mdash; Using the `Format.language`  that is set from the `EXT-X-MEDIA` tag `LANGUAGE` attribute, or if not from the sample stream (For MPEG-TS the Elementary Stream descriptor tag)
3. codec &mdash; Either the `Format.codecs` or the `Format.sampleMimeType` if `codecs` is empty (impossible at this point, both will be valid)
4. sampleRate &mdash; Directly from the `Format.sampleRate`value which would only come from the ATDS header in the stream
5. role &mdash; This is the descritive audio, read from `Format.role` which is set from the `EXT-X-MEDIA` tag `CHARATERISTICS` attributes

ExoVideoPlayer creates an `AudioStreamInfo` object by transforming these properties  as follows:

* `AudioStreamInfo.type`  &mdash; this Haxe Enum is 'parsed ' from the codec stream (which can either be a mimetype or a value from CODECS in the m3u8),  Very fragile code here in [toAudioType():AudioStreamType](https://opengrok.tivo.com/source/xref/clientcore/mainline/platform/video/video/lib/com/tivo/platform/video/common/AudioStreamInfo.hx#81)
* `AudioStreamInfo.language`  &mdash; this Haxe Enum [AudioLanguage](https://opengrok.tivo.com/source/xref/clientcore/mainline/platform/video/interfaces/lib/com/tivo/platform/video/AudioLanguage.hx#12) is parsed from the `language` property, so pretty direct from either a 2 or 3 character ISO639 language name
* `AudioStreamInfo.label`  &mdash; this string is either empty, or if the `langauge` property has a `-` character in it, it is split as the part after the dash.  Some streams encoded this kind of stuff in the MPEG-TS ES info likely
* `AudioStreamInfo.description`  &mdash; this is the `label` property string, basically the un-molested `NAME` attribute from the m3u8 master playlist
* `AudioStreamInfo.audioServiceType`  &mdash; this enum, [AudioServiceType](https://opengrok.tivo.com/source/xref/clientcore/mainline/shared/modelapi/models/lib/com/tivo/uimodels/stream/AudioServiceType.hx) is pretty strait-forwardly constructed from the `role` property, again direct from the `CHARATERISTICS` attirbute

Lastly the [VideoOverlayUtil](https://opengrok.tivo.com/source/xref/hydra/mainline/hydra/tasks/watchvideo/lib/com/tivo/hydra/watchvideo/overlays/VideoOverlayUtil.hx#45) in the Hydra code constructs the user visible string.  This class uses only the [AudioTrackModel](https://opengrok.tivo.com/source/xref/clientcore/mainline/shared/modelapi/models/lib/com/tivo/uimodels/model/watchvideo/AudioTrackModel.hx), this object condenses the AudioStreamInfo down to serviceType, language and [AudioTrackFormat](https://opengrok.tivo.com/source/xref/clientcore/mainline/shared/modelapi/models/lib/com/tivo/uimodels/model/watchvideo/AudioTrackFormat.hx) (which is yet another cast of the codec / mimetype).  If there are multiple tracks with the same language, they are simply numbered.  Note this naming logic:

1. looks at nothing from the track label (The EXT-X-MEDIA NAME attribute)
2. treats all multi-channel audio the same (ignores CHANNELS)
3. groups AC-3 and E-AC-3 togeather and lables it "Dolby Digital" (latter is DD+ FWIW)

Number 2 means the requirement from Pantos for CHANNELS, is pointless as it will still result in a duplicate track name.

```
		CHANNELS
			... For example, an AC-3 5.1 Rendition
      would have a CHANNELS="6" attribute.  No other CHANNELS parameters
      are currently defined.

      All audio EXT-X-MEDIA tags SHOULD have a CHANNELS attribute.  If a
      Master Playlist contains two Renditions encoded with the same
      codec but a different number of channels, then the CHANNELS
      attribute is REQUIRED; otherwise, it is OPTIONAL.
```



