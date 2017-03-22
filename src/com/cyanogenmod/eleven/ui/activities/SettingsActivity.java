/*
 * Copyright (C) 2012 Andrew Neal
 *
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Copyright (C) 2017 DarkKat
 *
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.cyanogenmod.eleven.ui.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.view.MenuItem;
import android.widget.Toast;

import com.cyanogenmod.eleven.R;
import com.cyanogenmod.eleven.cache.ImageFetcher;
import com.cyanogenmod.eleven.utils.MusicUtils;
import com.cyanogenmod.eleven.utils.PreferenceUtils;

/**
 * Settings.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener{

    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SwitchPreference mShowVisualizer;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // UP
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Add the preferences
        addPreferencesFromResource(R.xml.settings);

        mShowVisualizer = (SwitchPreference) findPreference(PreferenceUtils.SHOW_VISUALIZER);

        // Removes the cache entries
        deleteCache();

        PreferenceUtils.getInstance(this).setOnSharedPreferenceChangeListener(this);

        checkRecordAudioPermission();
        updateShowVisualizerPreference();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Removes all of the cache entries.
     */
    private void deleteCache() {
        final Preference deleteCache = findPreference("delete_cache");
        deleteCache.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                new AlertDialog.Builder(SettingsActivity.this).setMessage(R.string.delete_warning)
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            ImageFetcher.getInstance(SettingsActivity.this).clearCaches();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
                return true;
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
             String key) {
        if (key.equals(PreferenceUtils.SHOW_VISUALIZER)) {
            checkRecordAudioPermission();
        } else if (key.equals(PreferenceUtils.SHAKE_TO_PLAY)) {
            MusicUtils.setShakeToPlayEnabled(sharedPreferences.getBoolean(key, false));
        } else if (key.equals(PreferenceUtils.SHOW_ALBUM_ART_ON_LOCKSCREEN)) {
            MusicUtils.setShowAlbumArtOnLockscreen(sharedPreferences.getBoolean(key, true));
        }
    }

    private void checkRecordAudioPermission() {
        if (!isRecordAudioPermissionGranted() && mShowVisualizer.isChecked()) {
            if (showRationaleDialog()) {
                showPermissionInfoDialog();
            } else {
                requestRecordAudioPermission();
            }
        }
    }

    private void showPermissionInfoDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_permission_record_audio_title)
            .setMessage(R.string.dialog_permission_record_audio_message)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    requestRecordAudioPermission();
                }
            })
            .setNegativeButton(R.string.dialog_cancel, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    updateShowVisualizerPreference();
                }
            })
            .create().show();
    }

    public void requestRecordAudioPermission() {
        requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO },
                PERMISSIONS_REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (!alreadyAskedForPermission()) {
                        setAlreadyAskedForPermission();
                    }
                    if (showRationaleDialog()) {
                        showPermissionInfoDialog();
                    }
                }
                updateShowVisualizerPreference();
                break;
            }
        }
    }

    private void updateShowVisualizerPreference() {
        boolean disablePreference = !isRecordAudioPermissionGranted() && !showRationaleDialog()
                && alreadyAskedForPermission();
        mShowVisualizer.setEnabled(!disablePreference);
        if (disablePreference) {
            mShowVisualizer.setSummary(R.string.settings_show_music_visualization_summary_disabled);
        } else {
            mShowVisualizer.setSummary(null);
        }
        if (!isRecordAudioPermissionGranted() && mShowVisualizer.isChecked()) {
            mShowVisualizer.setChecked(false);
            Toast toast = Toast.makeText(this, R.string.toast_music_visualization_disabled,
                    Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private boolean isRecordAudioPermissionGranted() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean showRationaleDialog() {
        return shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
    }

    private boolean alreadyAskedForPermission() {
        return PreferenceUtils.getInstance(this).getAlreadyAskedForPermission();
    }

    private void setAlreadyAskedForPermission() {
        PreferenceUtils.getInstance(this).setAlreadyAskedForPermission(true);
    }
}
