
package com.tivo.exoplayer.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {

  @Override
  protected void onStart() {
    super.onStart();

    Intent playKqed = new Intent();
    playKqed.setAction(ViewActivity.ACTION_VIEW);
    playKqed.setData(Uri.parse("http://live1.nokia.tivo.com/ktvu/vxfmt=dp/playlist.m3u8?device_profile=hlsclr"));

    startActivity(playKqed);
  }
}
