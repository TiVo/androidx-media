## Multi-Player View Package

This package contains classes to support multiple ExoPlayer `PlayerVIew`'s in a single window.

The [MultiExoPlayerView][MultiExoPlayerView.java] class is a [GridLayout](https://developer.android.com/reference/android/widget/GridLayout) (mosaic view) that contains and manages a set of N-PlayerView's with each associated ExoPlayer playing content independently.   One view of the set is focused (selected), this view has audio focus while the rest of the views disable the audio track.

The view is styled by the [multi_view_player_container.xml][../../../../../../res/layout/multi_view_player_container.xml], a merge layout that can be overriden by the client of this library.  Also the layout of the `PlayerView` is defined in [multi_view_player.xml][../../../../../../res/layout/multi_view_player_container.xml] which can also be overriden.

The tenfoot demo includes the `MultiViewActivity` as an example of how to use these classes.

The steps are:

1. Create layout and instantiate a `MultiExoPlayerView`
2. Call `MultiExoPlayerView.createExoPlayerViews()` to create the `PlayerViews`
3. Start playback in each grid cell (PlayerView) using `MultiViewPlayerController.playMediaItem()`

#### Creating the `MultiExoPlayerView`

Sample Layout XML (for example, store as `res/layout/multi_view_main.xml`)

````xml
<?xml version="1.0" encoding="utf-8"?>
<GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent">

  <com.tivo.exoplayer.library.multiview.MultiExoPlayerView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="2dp"
    android:id="@+id/multi_player_view"/>
</GridLayout>
````

Instatiate with the `LayoutInflator` or reference it by id if it is imbedded in your main activity layout.  Example of the former:

```java
LayoutInflater inflater = LayoutInflater.from(context);
MultiExoPlayerView multiPlayerView = (MultiExoPlayerView) inflater.inflate(R.layout.multi_view_main, null, false);

```

