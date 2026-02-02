# Contributing to TiVo `androidx/media` Fork 

## TL;DR
Our local changes to `androidx/media` are contained in this repository.   The changes and bulid/publish is restricted to the set of modules which we have local changes:

* ***demo*** &mdash; change the gradle script to build with our local version and take the rest from google() maven
* ***lib-exoplayer*** &mdash; multiple changes, see the [README-TIVO.md](libraries/exoplayer/README-TIVO.md).
* ***lib-exoplayer-hls*** &mdash; multiple changes, see the [README-TIVO.md](libraries/exoplayer_hls/README-TIVO.md)
* ***lib-extractor***  &mdash; multiple changes, see the [README-TIVO.md](libraries/extractor/README-TIVO.md)
* ***lib-ui***  &mdash; Hook in `PlayerView` to control show/hide of controls, etc.  See [README-TIVO.md](libraries/ui/README-TIVO.md)
* **test-data**  &mdash; test samples for our unit tests.

Our goal is always to share everything from this repository upstream, keeping track of the open pull requests and cherry-picks in the respective README-TIVO.md files.

## Top Level Changes

We have strictly avoided chaining files outside of modules we have changes to avoid merge conflicts when updating.  The following files are added:

* ***local_settings.gradle*** &mdash; allows us to restrict the set of modules in the full build, and apply our local versioning.

* ***publish-tivo.gradle***  &mdash; our version of `publish.gradle` that will publish to an internal artifactory and apply our versioning

And modfied:

* ***settings.gradle*** &mdash; subset the modules in the project to just our changes.  Basically include the `local_settings.gradle` in precidence over `core-settings.gradle`

## Versioning

Our versions are all based on the upstream base AndroidX Media3 version, in such a way that our local modules are "newer".  The version scheme examples are:

**Published artifacts:** `1.1.1.1-tivo` is the first iteration of TiVo's modified 1.1.1 code.

- **Base version:** `1.1.1` - Upstream AndroidX Media3 version
- **TiVo suffix:** `.1-tivo` - TiVo fork iteration number
- **Dev builds:** `1.1.1-tivo.1-dev-{RUN_NUMBER}` - Development snapshots from CI

Updating the version by simply editing the `local_settings.gradle`

## Building and Publishing

### Maven Local

Maven local build publishes the modules into the user's local filesystem maven,  typically `~/.m2/...`.  The gradle command for this is

```shell
./gradlew --parallel lib-extractor:publishToMavenLocal lib-exoplayer-hls:publishToMavenLocal :lib-exoplayer:publishToMavenLocal
```

### CI Build

T.B.S.

### TiVo Internal Jenkins Build

T.B.S.

## Using The Library

To include library modules simply reference them in your gradle build dependencies.  For example,
this includes the TiVo patch 1 version of the AndroidX 1.1.1 libraries 

```jkgroovy

dependencies {
    implementation 'androidx.annotation:annotation:1.0.2'
    implementation 'androidx.media3:media3-exoplayer-hls:1.1.1.1-tivo-dev-1'
    implementation 'androidx.media3:media3-exoplayer:1.1.1.1-tivo-dev-1'
    implementation 'androidx.media3:media3-test-utils:1.1.1'
    testImplementation 'androidx.media3:media3-common:1.1.1'
    testImplementation 'org.robolectric:robolectric:4.8.1'
    androidTestImplementation 'org.mockito:mockito-core:3.12.4'
}
```
