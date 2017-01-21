package net.bytten.comicviewer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SettingsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean value = prefs.getBoolean("useZoomControls",
                !VersionHacks.isIncredible() && VersionHacks.getSdkInt() >= 5);
        ((CheckBoxPreference)findPreference("useZoomControls"))
            .setChecked(value);
    }

}
