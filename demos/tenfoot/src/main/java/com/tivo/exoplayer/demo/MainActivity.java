
package com.tivo.exoplayer.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

public class MainActivity extends Activity {

  private Spinner urlSpinner;
  private Spinner encryptionSpinner;
  EditText editUrl;
  Switch tunnelingSwitch;
  Switch chunklessSwitch;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);
    urlSpinner = (Spinner) findViewById(R.id.url_spinner);
    encryptionSpinner = (Spinner) findViewById(R.id.encrypt_spinner);
    ArrayAdapter<CharSequence> urlAdapter = ArrayAdapter.createFromResource(this,
        R.array.video_urls, android.R.layout.simple_spinner_item);
    urlAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    urlSpinner.setAdapter(urlAdapter);

    editUrl = (EditText) findViewById(R.id.url_string);
    editUrl.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        urlSpinner.setEnabled(charSequence == null || charSequence.length() == 0);
      }

      @Override
      public void afterTextChanged(Editable editable) {

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
        Uri videoUri = (editUrl.getText().length() > 0) ? Uri.parse(editUrl.getText().toString())
            : Uri.parse(urlSpinner.getSelectedItem().toString());
        startVideoIntent.setData(videoUri);
        startVideoIntent.putExtra(ViewActivity.CHUNKLESS_PREPARE, chunklessSwitch.isChecked());
        startVideoIntent.putExtra(ViewActivity.ENABLE_TUNNELED_PLAYBACK, tunnelingSwitch.isChecked());
        startActivity(startVideoIntent);
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();

  }

}
