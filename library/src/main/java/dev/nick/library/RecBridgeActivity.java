package dev.nick.library;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.newstand.logger.Logger;

import dev.nick.eventbus.EventBus;
import lombok.experimental.var;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 1;

    public static final String ACTION_START_REC = "nick.action.start";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_rec_bridge);
        resolveIntent();
    }

    private void resolveIntent() {
        if (ACTION_START_REC.equals(getIntent().getAction())) {
            initMediaProjection();
        }
    }

    private void initMediaProjection() {
        var pm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(pm.createScreenCaptureIntent(),
                PERMISSION_CODE);
    }

    private MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            super.onStop();
            onProjectionStop();
        }
    };

    private void onProjectionStop() {
        RecBridgeApp app = (RecBridgeApp) getApplication();
        app.setProjection(null);
        EventBus.getInstance().publishEmptyEvent(Events.PROJECTION_STOP);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            return;
        }
        if (resultCode != RESULT_OK) {
            return;
        }
        var pm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        MediaProjection projection = pm.getMediaProjection(resultCode, data);
        projection.unregisterCallback(projectionCallback);
        projection.registerCallback(projectionCallback, null);
        RecBridgeApp app = (RecBridgeApp) getApplication();
        app.setProjection(projection);
        Logger.d("onActivityResult:%s", projection);
        onProjectionReady();
    }

    private void onProjectionReady() {
        EventBus.getInstance().publishEmptyEvent(Events.PROJECTION_READY);
        finish();
    }
}
