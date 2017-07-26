package dev.nick.systemrecapi;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Observable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Tornaco on 2017/7/25.
 * Licensed with Apache.
 */
@AllArgsConstructor
class SettingsProvider extends Observable {

    private static final String PREF_NAME = "rec_bridge_settings";

    public enum Key {
        APP_REC_ALLOWED
    }

    @Getter
    private Context context;

    private SharedPreferences getPref() {
        return getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String toPrefKey(Key key) {
        return key.name().toLowerCase();
    }

    public boolean isAppRecAllowed(String packageName) {
        return getPref().getBoolean(toPrefKey(Key.APP_REC_ALLOWED) + "_" + packageName, false);
    }

    public void setAppRecAllowed(String packageName, boolean allow) {
        getPref().edit().putBoolean(toPrefKey(Key.APP_REC_ALLOWED) + "_" + packageName, allow).apply();
        setChanged();
        notifyObservers(Key.APP_REC_ALLOWED);
    }
}
