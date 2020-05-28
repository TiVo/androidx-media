
package com.tivo.exoplayer.demo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.ArraySet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

  private Spinner urlSpinner;
  private Spinner encryptionSpinner;
  EditText editUrl;
  Switch tunnelingSwitch;
  Switch chunklessSwitch;
  protected ArrayAdapter<CharSequence> urlAdapter;

  private ArrayList<String> urlList;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);
    urlSpinner = (Spinner) findViewById(R.id.url_spinner);
    encryptionSpinner = (Spinner) findViewById(R.id.encrypt_spinner);

    urlAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);

    if (savedInstanceState != null) {
      ArrayList<String> urls = savedInstanceState.getStringArrayList("URL_LIST");
      if (urls != null) {
        urlList = urls;
      }
    } else {
      SharedPreferences preferences = getPreferences(MODE_PRIVATE);
      Set<String> urls = preferences.getStringSet("URL_LIST", null);
      if (urls != null) {
        urlList = new ArrayList<>(urls);
      }
    }

    if (getIntent() != null) {
      processIntent(getIntent());
    }

    if (urlList == null) {
      List<String> video_urls = Arrays.asList(getResources().getStringArray(R.array.video_urls));
      urlList = new ArrayList<>(video_urls);
    }
    urlAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    urlSpinner.setAdapter(urlAdapter);

    editUrl = (EditText) findViewById(R.id.url_string);
    urlSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        editUrl.setText(parent.getItemAtPosition(position).toString());
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    tunnelingSwitch = (Switch) findViewById(R.id.enable_tunneling);
    chunklessSwitch = (Switch) findViewById(R.id.enable_chunkless_prepare);
    ArrayAdapter<CharSequence> encryptAdapter = ArrayAdapter.createFromResource(this,
        R.array.encryption_type, android.R.layout.simple_spinner_item);
    encryptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    encryptionSpinner.setAdapter(encryptAdapter);

    Button playButton = (Button) findViewById(R.id.play_button);
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent startVideoIntent;
        startVideoIntent = new Intent(MainActivity.this, ViewActivity.class);
        startVideoIntent.setAction(ViewActivity.ACTION_VIEW);
        String uriString = editUrl.getText().toString();
        uriString = uriString.trim();
        Uri videoUri = Uri.parse(uriString);
        addUrlToList(uriString);
        startVideoIntent.setData(videoUri);
        startVideoIntent.putExtra(ViewActivity.CHUNKLESS_PREPARE, chunklessSwitch.isChecked());
        startVideoIntent.putExtra(ViewActivity.ENABLE_TUNNELED_PLAYBACK, tunnelingSwitch.isChecked());
        startActivity(startVideoIntent);
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    urlAdapter.clear();
    urlAdapter.addAll(urlList);
  }

  @Override
  @SuppressLint("NewApi")
  protected void onPause() {
    super.onPause();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      savePreferences();
    }
  }

  @RequiresApi(23)
  private void savePreferences() {
    SharedPreferences preferences = getPreferences(MODE_PRIVATE);
    SharedPreferences.Editor edit = preferences.edit();
    Set<String> urls = new ArraySet<>(urlList.size());
    urls.addAll(urlList);
    edit.putStringSet("URL_LIST", urls);
    edit.commit();
  }

  private void addUrlToList(String url) {
    if (urlList == null) {
      urlList = new ArrayList<>();
    }
    if (! urlList.contains(url)) {
      urlList.add(url);
    }
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    ArrayList<String> list = savedInstanceState.getStringArrayList("URL_LIST");
    if (list != null) {
      urlList = list;
      urlAdapter.addAll(urlList);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putStringArrayList("URL_LIST", urlList);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    processIntent(intent);
  }

  @SuppressLint("NewApi")
  private void processIntent(Intent intent) {
    String[] uriStrings = intent.getStringArrayExtra(ViewActivity.URI_LIST_EXTRA);
    if (uriStrings != null) {
      urlList.clear();
      for (String url : uriStrings) {
        addUrlToList(url);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        savePreferences();
      }
    }
  }


}
