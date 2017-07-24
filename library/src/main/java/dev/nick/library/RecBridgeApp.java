package dev.nick.library;

import android.app.Application;

import org.newstand.logger.Logger;
import org.newstand.logger.Settings;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Logger.config(Settings.builder().tag("RecBridge").logLevel(Logger.LogLevel.ALL).build());
    }
}
