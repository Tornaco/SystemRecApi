package dev.nick.library;

import android.app.Application;
import android.media.projection.MediaProjection;

import org.newstand.logger.Logger;
import org.newstand.logger.Settings;

import dev.nick.eventbus.EventBus;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeApp extends Application {

    @Getter
    @Setter
    private MediaProjection projection;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.config(Settings.builder().tag("RecBridge").logLevel(Logger.LogLevel.ALL).build());
        EventBus.create(this).setDebuggable(false);
    }
}
