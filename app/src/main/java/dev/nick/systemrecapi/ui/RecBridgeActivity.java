package dev.nick.systemrecapi.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.tbruyelle.rxpermissions2.RxPermissions;

import org.newstand.logger.Logger;

import dev.nick.eventbus.EventBus;
import dev.nick.systemrecapi.Events;
import dev.nick.systemrecapi.R;
import dev.nick.systemrecapi.RecBridgeApp;
import dev.nick.systemrecapi.cast.ThreadUtil;
import io.reactivex.functions.Consumer;
import lombok.experimental.var;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 1;

    private static final long REQUEST_HANDLE_DELAY = 500;

    public static final String ACTION_START_REC = "nick.action.start";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_rec_bridge);
        resolveIntentChecked();
    }

    private void resolveIntentChecked() {
        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions.request(Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean granted) throws Exception {
                        if (granted) {
                            ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    resolveIntent();
                                }
                            }, REQUEST_HANDLE_DELAY);
                        } else {
                            finish();
                        }
                    }
                });
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
