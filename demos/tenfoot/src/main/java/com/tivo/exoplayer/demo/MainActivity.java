
package com.tivo.exoplayer.demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.ArraySet;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.library.ima.ImaSDKHelper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends FragmentActivity {

  private Spinner urlSpinner;
  EditText editUrl;
  Switch tunnelingSwitch;
  Switch chunklessSwitch;
  Switch geekStatsSwitch;
  Switch asyncRenderSwitch;
  Switch fastSyncSwitch;
  EditText liveOffset;
  protected ArrayAdapter<CharSequence> urlAdapter;

  private List<String> urlList;
  private Map<Pattern, Map<String, String>> drmSettings;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ImaSDKHelper.Builder.warmStartIMA(getApplicationContext());
    setContentView(R.layout.main_activity);
    urlSpinner = (Spinner) findViewById(R.id.url_spinner);

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
    urlAdapter.setNotifyOnChange(true);

    tunnelingSwitch = (Switch) findViewById(R.id.enable_tunneling);
    geekStatsSwitch = (Switch) findViewById(R.id.geek_stats_switch);
    asyncRenderSwitch = (Switch) findViewById(R.id.async_render_mode);
    fastSyncSwitch = (Switch) findViewById(R.id.fast_sync_enable);
    liveOffset = (EditText) findViewById(R.id.live_offset);
    chunklessSwitch = (Switch) findViewById(R.id.enable_chunkless_prepare);

    Button playButton = (Button) findViewById(R.id.play_button);
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent startVideoIntent = createPlayIntent(ViewActivity.ACTION_VIEW);
        String uriString = editUrl.getText().toString();
        uriString = uriString.trim();
        Uri videoUri = Uri.parse(uriString);
        addUrlToList(uriString);
        startVideoIntent.setData(videoUri);
        addVcasExtrasToIntent(new String[]{uriString}, startVideoIntent);
        issuePlayIntent(startVideoIntent);
      }
    });

    Button clearUriButton = findViewById(R.id.clear_url);
    clearUriButton.setOnClickListener(v -> {
      editUrl.setText("");
    });

    Button playAllButton = findViewById(R.id.play_all);
    playAllButton.setOnClickListener(v -> {
      Intent playAll = createPlayIntent(ViewActivity.ACTION_VIEW_LIST);
      Adapter spinnerAdapter = urlSpinner.getAdapter();
      String[] urls = new String[spinnerAdapter.getCount()];
      for (int i = 0; i < spinnerAdapter.getCount(); i++) {
        urls[i] = spinnerAdapter.getItem(i).toString();
      }
      if (urls.length > 0) {
        addVcasExtrasToIntent(urls, playAll);
        playAll.putExtra(ViewActivity.URI_LIST_EXTRA, urls);
        issuePlayIntent(playAll);
      }
    });

    Button loadButton = findViewById(R.id.load_urls);
    loadButton.setOnClickListener(v -> {
      try {
        urlList = loadUrlsFromFile();
        urlAdapter.clear();
        urlAdapter.addAll(urlList);
      } catch (FileNotFoundException e) {
        Toast.makeText(getApplicationContext(), "No file " + e.toString() + " found.", Toast.LENGTH_LONG).show();
      } catch (IOException e) {
        Log.w(ViewActivity.TAG, "No URLS loaded from play_urls", e);
      }
    });

    Button saveButton = findViewById(R.id.save_urls);
    saveButton.setOnClickListener(v -> {
      try {
        saveUrlsToFile();
      } catch (IOException e) {
        Log.w(ViewActivity.TAG, "No URLS saved", e);
      }
    });

    TextView instructions = findViewById(R.id.load_instructions);
    if (instructions != null) {
      String base = instructions.getText().toString();
      instructions.setText(base + "  " + getSavedUrlsFile().getAbsolutePath());
    }

    drmSettings = loadDrmSettings();
    Log.d(ViewActivity.TAG, "text: " + drmSettings);
  }


  protected void issuePlayIntent(Intent startVideoIntent) {
    startVideoIntent.putExtra(ViewActivity.CHUNKLESS_PREPARE, chunklessSwitch.isChecked());
    startVideoIntent.putExtra(ViewActivity.SHOW_GEEK_STATS, geekStatsSwitch.isChecked());
    startVideoIntent.putExtra(ViewActivity.ENABLE_ASYNC_RENDER, asyncRenderSwitch.isChecked());
    startVideoIntent.putExtra(ViewActivity.ENABLE_TUNNELED_PLAYBACK, tunnelingSwitch.isChecked());

    if (fastSyncSwitch.isChecked()) {
      startVideoIntent.putExtra(ViewActivity.FAST_RESYNC, 30.0f);
      startVideoIntent.putExtra(ViewActivity.LIVE_OFFSET, Float.valueOf(liveOffset.getText().toString()));
    }
    startActivity(startVideoIntent);
  }

  protected Intent createPlayIntent(String action) {
    Intent startVideoIntent;
    startVideoIntent = new Intent(this, ViewActivity.class);
    startVideoIntent.setAction(action);
    return startVideoIntent;
  }

  private Map<Pattern, Map<String, String>> loadDrmSettings() {
    File appFiles = getApplicationContext().getExternalFilesDir(null);
    File settings = new File(appFiles, "drm_settings.json");
    StringBuffer buffer = new StringBuffer();
    Map<Pattern, Map<String, String>> value = new HashMap<>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(settings));
      String line;
      while ((line = reader.readLine()) != null){
        buffer.append(line);
      }
      JSONObject jsonObject = new JSONObject(buffer.toString());
      value = parseDrmSettings(jsonObject);
    } catch (IOException e) {
      Log.d(ViewActivity.TAG, "failed to read drm_settings from " + settings.getAbsolutePath());
    } catch (JSONException e) {
      Log.e(ViewActivity.TAG, "failed to parse JSON from " + settings.getAbsolutePath(), e);
    }
    return value;
  }


  private Map<Pattern, Map<String, String>> parseDrmSettings(JSONObject drmSettings) throws JSONException {
    Map<Pattern, Map<String, String>> urlPatternToSetings = new LinkedHashMap<>();
    JSONArray settingsList = drmSettings.getJSONArray("settings");
    for (int i=0; i < settingsList.length(); i++) {
      JSONObject setting = settingsList.getJSONObject(i);
      JSONArray urlPatterns = (JSONArray) setting.remove("url_patterns");
      Map<String, String> intentExtras = new HashMap<>();
      Iterator<String> keys = setting.keys();
      while(keys.hasNext()) {
        String key = keys.next();
        intentExtras.put(key, setting.getString(key));
      }
      for (int j=0; j < urlPatterns.length(); j++) {
        urlPatternToSetings.put(Pattern.compile((String) urlPatterns.get(j)), intentExtras);
      }
    }
    return urlPatternToSetings;
  }

  private void addVcasExtrasToIntent(String uris[], Intent startVideoIntent) {
    for (String videoUri : uris) {
      for (Map.Entry<Pattern, Map<String, String>> setting : drmSettings.entrySet()) {
        if (setting.getKey().matcher(videoUri).matches()) {
          for (Map.Entry<String, String> intentExtra : setting.getValue().entrySet()) {
            startVideoIntent.putExtra(intentExtra.getKey(), intentExtra.getValue());
          }
        }
      }
    }
  }

  public static String getDeviceIdFromHSNT(String hsnt) throws UnsupportedEncodingException {
    byte[] hsntBytes = hsnt.getBytes("US-ASCII");
    return UUID.nameUUIDFromBytes(hsntBytes).toString();
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

  /**
   * List of files can be pushd to and loaded from
   *   /storage/emulated/0/Android/data/com.tivo.exoplayer.demo/files
   *
   * @return list of URLs to load to the URL list spinner
   * @throws IOException, FileNotFoundException
   */
  private List<String> loadUrlsFromFile() throws IOException {
    ArrayList<String> urls = new ArrayList<>();
    File play_urls = getSavedUrlsFile();
    if (play_urls.canRead()) {
      BufferedReader reader = new BufferedReader(new FileReader(play_urls));
      String line;
      while ((line = reader.readLine()) != null) {
        urls.add(line);
      }
    } else {
      throw new FileNotFoundException(play_urls.toString());
    }
    return urls;
  }


  private void saveUrlsToFile() throws IOException {
    File saved_urls = getSavedUrlsFile();
    boolean existsForAppend = saved_urls.canWrite();
    String message;
    if (existsForAppend || saved_urls.createNewFile()) {
      BufferedWriter writer = new BufferedWriter(new FileWriter(saved_urls, true));
      for (String url : urlList) {
        writer.write(url);
        writer.newLine();
      }
      writer.close();

      if (existsForAppend) {
        message = "added " + urlList.size() + " URLs to file " + saved_urls.getAbsolutePath();
      } else {
        message = "saved " + urlList.size() + " URLs to file " + saved_urls.getAbsolutePath();
      }
    } else {
      message = "created failed for " + saved_urls.getAbsolutePath();
    }
    Log.d(ViewActivity.TAG, message);
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

  }

  private File getSavedUrlsFile() {
    File appFiles = getApplicationContext().getExternalFilesDir(null);
    return new File(appFiles, "play_urls");
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
    outState.putStringArrayList("URL_LIST", new ArrayList<>(urlList));
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
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
