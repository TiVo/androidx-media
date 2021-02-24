# ExoPlayer Shared UI library module #

## Summary
Library for constructing and using a SimpleExoPLayer with TiVo extensions and debug tools

The TiVo created tenfoot UI demo (demos/tenfoot) demonstrates use of this library with a simple
player.

## Basic Usage
This library depends on the TiVo required ExoPlayer libraries:

1. `library-trickplay` &mdash; provides full screen trickplay with iFrame playlist
2. `library-hls` &mdash; needed for HLS playback (TODO if we need dash)
3. `library-core` &mdash; the core ExoPlayer with TiVo specific mods
4. `library-ui` &mdash; the ExoPlayer ui components library

The main class in this library is the `SimpleExoPlayerFactory` this class creates the `SimpleExoPlayer` and manages it's lifecycle as well as creating all the factories and ancillary objects required by `SimpleExoPlayer` to support trick-play.  This object is very light-weight in itself, you can create the factory and keep it around till your app is destroyed.

~~~java
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Context context = getApplicationContext();
    SimpleExoPlayerFactory.initializeLogging(context, com.google.android.exoplayer2.util.Log.LOG_LEVEL_INFO);
    exoPlayerFactory = new SimpleExoPlayerFactory(context, false);
~~~

Use the factory in your start and stop methods create the player.  Assuming you are using ExoPlayer's `PlayerView` code would look like this

~~~java
  @Override
   public void onStart() {
     super.onStart();
    SimpleExoPlayerFactory.initializeLogging(context, com.google.android.exoplayer2.util.Log.LOG_LEVEL_INFO);
     SimpleExoPlayer player = exoPlayerFactory.createPlayer(true);
     playerView.setPlayer(player);
     geekStats.setPlayer(player, exoPlayerFactory.getCurrentTrickPlayControl());
~~~

And finally to free up the player and resources it uses, do this in onStop:

~~~java
   @Override
   public void onStop() {
     super.onStop();
     exoPlayerFactory.releasePlayer();
~~~

At anytime you can access the player and trick-play interfaces with the calls `exoPlayerFactory.getCurrentPlayer()` and `exoPlayerFactory.getCurrentTrickPlayControl()`.   Use the `exoPlayerFactory.playUrl()` method to play content, this allows this library to manage the `MediaSource` and error recovery thereof.

## Logging
The method `SimpleExoPlayerFactory.initializeLogging()` sets the default logging level for ExoPlayer (the build default is `ALL`).  This call allows a local file pushed to sdcard to override the logging level, the file is located in the App's external storage area.  For example, for the demo app that is

```
/sdcard/Android/data/com.tivo.exoplayer.demo/files
```

To set logging level to all use:

```
cat > exo.properties
debugLevel=0
^D

adb push exo.properties /sdcard/Android/data/com.tivo.exoplayer.demo/files
```

The level values are:

```java
  /** Log level to log all messages. */
  public static final int LOG_LEVEL_ALL = 0;
  /** Log level to only log informative, warning and error messages. */
  public static final int LOG_LEVEL_INFO = 1;
  /** Log level to only log warning and error messages. */
  public static final int LOG_LEVEL_WARNING = 2;
  /** Log level to only log error messages. */
  public static final int LOG_LEVEL_ERROR = 3;
  /** Log level to disable all logging. */
  public static final int LOG_LEVEL_OFF = Integer.MAX_VALUE;
```
  
## Extending

The internals of this module are extensible, two factory methods in the `SimpleExoPlayerFactory` allow clients to add their own or extend the provided `MediaSourceLifeCycle` and error handling.

### MediaSourceLifeCycle
The `SimpleExoPlayerFactory` method `createMediaSourceLifeCycle()` creates the implementation of this interface.  This object is responsible for creating the `MediaSource` and recovering any playback errors that are `MediaSource` related (eg. `BehindLiveWindowException`).  

Clients can either extend `DefaultMediaSourceLifeCycle` to customize the implementation or create their own.

### TivoCrypt DRM

Integration of TivoCrypt DRM can be done with `DefaultMediaSourceLifeCycle`. TivoCrypt DRM requires a native library hosted in artifactory.
To include the native library we need to make following changes in build.gradle,
```
compile(group: 'com.tivo.android.tivocrypt', name: 'tivoCrypt', version: '1.0', classifier: "release", ext: 'aar')
```
Need to create `TivoCryptDrmInfo` with required values and call `DefaultMediaSourceLifeCycle.playUrl()`.