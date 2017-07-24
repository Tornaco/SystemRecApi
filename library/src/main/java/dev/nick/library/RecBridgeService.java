package dev.nick.library;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

import org.newstand.logger.Logger;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new RecBridgeBinder();
    }

    public void start(IParam param) {
        Logger.d("Start called with param:%s", param);
    }

    public void stop() {

    }

    public boolean isRecording() {
        return false;
    }

    public void watch(IWatcher w) {

    }

    public boolean checkSelfPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAPTURE_AUDIO_OUTPUT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void unWatch(IWatcher w) throws RemoteException {

    }

    private class RecBridgeBinder extends IRecBridge.Stub {

        @Override
        public String getVersionName() throws RemoteException {
            return BuildConfig.VERSION_NAME;
        }

        @Override
        public int getVersionCode() throws RemoteException {
            return BuildConfig.VERSION_CODE;
        }

        @Override
        public void start(IParam param) throws RemoteException {
            RecBridgeService.this.start(param);
        }

        @Override
        public void stop() throws RemoteException {
            RecBridgeService.this.stop();
        }

        @Override
        public boolean isRecording() throws RemoteException {
            return RecBridgeService.this.isRecording();
        }

        @Override
        public void watch(IWatcher w) throws RemoteException {
            RecBridgeService.this.watch(w);
        }

        @Override
        public void unWatch(IWatcher w) throws RemoteException {
            RecBridgeService.this.unWatch(w);
        }

        @Override
        public boolean checkSelfPermission() throws RemoteException {
            return RecBridgeService.this.checkSelfPermission();
        }

    }
}
