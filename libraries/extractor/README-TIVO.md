## Changes for `lib-extractor`

Our changes include a work-around for an issue with AC3 for an encoder as well as other planned pull requests

1. Fixes to commit a single iFrame sample from a segment without waiting for next segment load
2. Fix for Filler NALU bytes
3. Don't automatically create ID3/EMSG tracks for HLS (used to prevent streaming video when video is muted).  This change was requested to be shared by [Google in AndroidX issue 294](https://github.com/androidx/media/issues/294)
4. Change to work around where Dolby 5.1 channels do not correctly set the lfeon bit

### Needs Pull Request

* Steve Mayhew 2026-01-14 Commit final sample at end-of-stream for MPEG-TS HLS segments &mdash; combines a series of changes into one:
   * [95fe025261 2026-01-12 Steve Mayhew -- Removes extra file created by extractor test](https://github.com/tivocorp/exoplayerprvt/commit/95fe025261)
   * [61526426c8 2024-06-26 Steve Mayhew -- Only commit sample on EOS if it is an i-frame](https://github.com/tivocorp/exoplayerprvt/commit/61526426c8)
   * [73c568f1b2 2024-06-11 Maciej Dobrzynski -- Final sample is commited on EOS for MPEG-TS](https://github.com/tivocorp/exoplayerprvt/commit/73c568f1b2)
   * [3a0717df70 2023-01-24 Steve Mayhew -- Commits HLS iFrame segment key frame only segment on EOS](https://github.com/tivocorp/exoplayerprvt/commit/3a0717df70)
   * [683e493c12 2023-01-30 Steve Mayhew -- Bounds of EOS sampleMetadata() commit are correct](https://github.com/tivocorp/exoplayerprvt/commit/683e493c12)
   * [87390fc77b 2022-10-10 mbolaris -- Commits IDR sample on end of single IDR stream](https://github.com/tivocorp/exoplayerprvt/commit/87390fc77b)
   * [902f720296 2021-11-02 Steve Mayhew -- Commits IDR NALU on end of stream](https://github.com/tivocorp/exoplayerprvt/commit/902f720296)

* Sada .. Don't automatically create ID3/EMSG tracks for HLS 
   * [ed27ccac40 2023-09-10 sneelavara -- Prevents Creation of ID3 and EMSG Tracks for HLS](https://github.com/tivocorp/exoplayerprvt/commit/ed27ccac40)

### Local Only Changes

*  Workaround to handle 5-channel AC3 audio
   * [aeed7dc0a4 2022-04-03 mbolaris -- Workaround to handle 5-channel AC3 audio.](https://github.com/tivocorp/exoplayerprvt/commit/aeed7dc0a4)

* Workaround for WebVTT Header format issue 
   * [2d7e46f77b 2020-06-29 Steve Mayhew -- Fix so heeader parse does not break test cases](https://github.com/tivocorp/exoplayerprvt/commit/2d7e46f77b)
