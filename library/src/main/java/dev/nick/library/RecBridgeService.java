package dev.nick.library;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.Toast;

import org.newstand.logger.Logger;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import dev.nick.eventbus.Event;
import dev.nick.eventbus.EventBus;
import dev.nick.eventbus.EventReceiver;
import dev.nick.library.cast.MediaTools;
import dev.nick.library.cast.RecordingDevice;
import dev.nick.library.cast.ThreadUtil;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeService extends Service implements Handler.Callback {

    private static final String SCREENCASTER_NAME = "hidden:screen-recording";
    private static final String ACTION_STOP_SCREENCAST = "stop.recording";

    private static final int SENSOR_SHAKE = 10;

    private final List<IWatcher> mWatchers = new ArrayList<>();

    RecordingDevice mRecorder;
    boolean mIsCasting;

    Handler sensorEventHandler;

    private MediaProjection mProjection;
    private long startTime;
    private Timer timer;
    private Notification.Builder mBuilder;
    private Logger mLogger;
    private SoundPool mSoundPool;
    private int mStartSound, mStopSound;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.i("onReceive:" + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_USER_BACKGROUND) ||
                    intent.getAction().equals(ACTION_STOP_SCREENCAST) ||
                    intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
                stop();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {

            }
        }
    };
    private SensorManager sensorManager;
    private Vibrator vibrator;

    private SensorEventListener sensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            float x = values[0];
            float y = values[1];
            float z = values[2];
            int medumValue = 19;
            if (Math.abs(x) > medumValue || Math.abs(y) > medumValue || Math.abs(z) > medumValue) {
                Message msg = new Message();
                msg.what = SENSOR_SHAKE;
                sensorEventHandler.sendMessage(msg);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private EventReceiver eventReceiver = new EventReceiver() {
        @Override
        public void onReceive(@NonNull Event event) {
            int e = event.getEventType();
            switch (e) {
                case Events.PROJECTION_READY:
                    onProjectionReady();
                    break;
                case Events.PROJECTION_STOP:
                    onProjectionStop();
                    break;
            }
        }

        @Override
        public int[] events() {
            return new int[]{
                    Events.PROJECTION_READY,
                    Events.PROJECTION_STOP
            };
        }
    };

    private void onProjectionReady() {
        RecBridgeApp app = (RecBridgeApp) getApplication();
        MediaProjection projection = app.getProjection();
        Logger.i("onProjectionReady:%s", projection);
        preStart();
    }

    private void onProjectionStop() {
        Logger.i("onProjectionStop");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new RecBridgeBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (EventBus.getInstance() == null) {
            EventBus.create(getApplication()).setDebuggable(false);
        }
        EventBus.getInstance().subscribe(eventReceiver);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        sensorEventHandler = new Handler(this);

        sensorManager.registerListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build())
                .build();

        mStopSound = mSoundPool.load(this, R.raw.video_stop, 1);
        mStartSound = mSoundPool.load(this, R.raw.video_record, 1);
        stopCasting();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(ACTION_STOP_SCREENCAST);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, filter);
    }

    void cleanup() {
        String recorderPath = null;
        if (mRecorder != null) {
            recorderPath = mRecorder.getRecordingFilePath();
            mRecorder.stop();
            mRecorder = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        stopForeground(true);
        if (recorderPath != null) {
            sendShareNotification(recorderPath);
        }
        if (mProjection != null)
            mProjection.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getInstance().unSubscribe(eventReceiver);

        stopCasting();
        unregisterReceiver(mBroadcastReceiver);
        mSoundPool.release();
        mSoundPool = null;
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
        super.onDestroy();
        if (mProjection != null) try {
            mProjection.stop();
        } catch (Exception ignored) {
        }
    }

    public void start(IParam param) {
        Logger.d("Start called with param:%s", param);
        Intent intent = new Intent(this, RecBridgeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(RecBridgeActivity.ACTION_START_REC);
        startActivity(intent);
    }

    private void preStart() {
        if (mProjection == null) {
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), R.string.not_projection, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        if (!hasAvailableSpace()) {
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), R.string.not_enough_storage, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        ThreadUtil.getWorkThreadHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startInternal();
            }
        }, 100);// FIXME
        return;
    }

    boolean startInternal() {
        try {
            if (!hasAvailableSpace()) {
                Toast.makeText(this, R.string.not_enough_storage, Toast.LENGTH_LONG).show();
                return false;
            }

            mIsCasting = true;
            notifyCasting();
            startTime = SystemClock.elapsedRealtime();

            registerScreencaster(true);//FIXME

            if (true) { //FIXME
                mSoundPool.play(mStartSound, 1.0f, 1.0f, 0, 0, 1.0f);
            }

            mBuilder = createNotificationBuilder();

            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    updateNotification(RecBridgeService.this);
                }
            }, 100, 1000);
            return true;
        } catch (Exception e) {
            Logger.e(e, "Fail start");
            return false;
        }
    }

    private boolean hasAvailableSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long megAvailable = bytesAvailable / 1048576;
        return megAvailable >= 30;
    }

    public void updateNotification(Context context) {
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;
        String time = getString(R.string.video_length,
                DateUtils.formatElapsedTime(timeElapsed / 1000));
        mBuilder.setContentText(time);
        startForeground(1, mBuilder.build());
        notifyTimeChange(DateUtils.formatElapsedTime(timeElapsed / 1000));
    }

    protected Point getNativeResolution() {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Point ret = new Point();
        try {
            display.getRealSize(ret);
        } catch (Exception e) {
            try {
                Method mGetRawH = Display.class.getMethod("getRawHeight");
                Method mGetRawW = Display.class.getMethod("getRawWidth");
                ret.x = (Integer) mGetRawW.invoke(display);
                ret.y = (Integer) mGetRawH.invoke(display);
            } catch (Exception ex) {
                display.getSize(ret);
            }
        }

        // Find user preferred one.
        boolean landscape = false;//FIXME
        int width, height;
        int preferredResIndex = ValidResolutions.INDEX_MASK_AUTO;//FIXME
        if (preferredResIndex != ValidResolutions.INDEX_MASK_AUTO) {
            int[] resolution = ValidResolutions.$[preferredResIndex];
            if (landscape) {
                width = resolution[0];
                height = resolution[1];
            } else {
                height = resolution[0];
                width = resolution[1];
            }
            ret.x = width;
            ret.y = height;
        }

        return ret;
    }

    void registerScreencaster(boolean withAudio) throws RemoteException {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        assert mRecorder == null;
        Point size = getNativeResolution();
        // size = new Point(1080, 1920);

        String path = Environment.getExternalStorageDirectory().getPath()
                + File.separator + "test.mp4";//FIXME
        mRecorder = new RecordingDevice(this, size.x, size.y, withAudio, AudioSource.R_SUBMIX, Orientations.AUTO, path);
        mRecorder.setProjection(mProjection);
        VirtualDisplay vd = mRecorder.registerVirtualDisplay(this,
                SCREENCASTER_NAME, size.x, size.y, metrics.densityDpi);
        if (vd == null) {
            cleanup();
        }
    }

    private void stopCasting() {
        cleanup();
        if (!hasAvailableSpace()) {
            Toast.makeText(this, R.string.insufficient_storage, Toast.LENGTH_LONG).show();
        }
        if (mIsCasting && true) {//FIXME
            mSoundPool.play(mStopSound, 1.0f, 1.0f, 0, 0, 1.0f);
        }
        mIsCasting = false;
        notifyUncasting();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_NOT_STICKY;
        if (ACTION_STOP_SCREENCAST.equals(intent.getAction())) {
            stop();
        }
        return START_STICKY;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification.Builder createNotificationBuilder() {
        Notification.Builder builder = new Notification.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_device_access_video)
                .setContentTitle(getString(R.string.recording));
        Intent stopRecording = new Intent(ACTION_STOP_SCREENCAST);
        stopRecording.setClass(this, RecBridgeService.class);
        builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
                PendingIntent.getService(this, 0, stopRecording, PendingIntent.FLAG_UPDATE_CURRENT));
        return builder;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void sendShareNotification(String recordingFilePath) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // share the screencast file
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        notificationManager.notify(0, mBuilder.build());
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification.Builder createShareNotificationBuilder(String file) {
        Intent sharingIntent = MediaTools.buildSharedIntent(this, new File(file));
        Intent chooserIntent = Intent.createChooser(sharingIntent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;

        Logger.d("Video complete: " + file);

        Intent open = MediaTools.buildOpenIntent(this, new File(file));
        PendingIntent contentIntent =
                PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_CANCEL_CURRENT);

        return new Notification.Builder(this)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_stat_device_access_video)
                .setContentTitle(getString(R.string.recording_ready_to_share))
                .setContentText(getString(R.string.video_length,
                        DateUtils.formatElapsedTime(timeElapsed / 1000)))
                .addAction(R.drawable.ic_share, getString(R.string.share),
                        PendingIntent.getActivity(this, 0, chooserIntent, PendingIntent.FLAG_CANCEL_CURRENT))
                .setContentIntent(contentIntent);
    }

    private void notifyTimeChange(final String time) {
        synchronized (mWatchers) {
            final List<IWatcher> tmp = new ArrayList<>(mWatchers.size());
            tmp.addAll(mWatchers);
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    for (IWatcher w : tmp) {
                        try {
                            w.onElapsedTimeChange(time);
                        } catch (RemoteException ignored) {

                        }
                    }
                }
            });
        }
    }

    private void notifyCasting() {
        synchronized (mWatchers) {
            final List<IWatcher> tmp = new ArrayList<>(mWatchers.size());
            tmp.addAll(mWatchers);
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    for (IWatcher w : tmp) {
                        try {
                            w.onStart();
                        } catch (RemoteException ignored) {

                        }
                    }
                }
            });
        }
    }

    private void notifyUncasting() {
        synchronized (mWatchers) {
            final List<IWatcher> tmp = new ArrayList<>(mWatchers.size());
            tmp.addAll(mWatchers);
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    for (IWatcher w : tmp) {
                        try {
                            w.onStop();
                        } catch (RemoteException ignored) {

                        }
                    }
                }
            });
        }
    }

    public void stop() {
        stopCasting();
    }

    public boolean isRecording() {
        return mIsCasting;
    }

    public void watch(IWatcher w) {
        synchronized (mWatchers) {
            if (!mWatchers.contains(w)) {
                mWatchers.add(w);
                notifySticky(w);
            }
        }
    }

    void notifySticky(final IWatcher watcher) {
        ThreadUtil.getMainThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                if (mIsCasting) {
                    try {
                        watcher.onStart();
                    } catch (RemoteException ignored) {

                    }
                } else {
                    try {
                        watcher.onStop();
                    } catch (RemoteException ignored) {

                    }
                }
            }
        });
    }

    public boolean checkSelfPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAPTURE_AUDIO_OUTPUT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void unWatch(IWatcher w) throws RemoteException {
        synchronized (mWatchers) {
            mWatchers.remove(w);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case SENSOR_SHAKE:
                boolean shouldHandle = true;//FIXME
                if (!shouldHandle) return true;
                if (mIsCasting) {
                    vibrator.vibrate(100);
                    stop();
                }
                return true;
        }
        return false;
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
