package dev.nick.systemrecapi;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
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
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.Toast;

import com.google.common.base.Preconditions;

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
import dev.nick.library.IParam;
import dev.nick.library.IRecBridge;
import dev.nick.library.IToken;
import dev.nick.library.IWatcher;
import dev.nick.library.Orientations;
import dev.nick.library.ValidResolutions;
import dev.nick.library.common.Holder;
import dev.nick.systemrecapi.cast.RecordingDevice;
import dev.nick.systemrecapi.cast.ThreadUtil;
import dev.nick.systemrecapi.ui.RecBridgeActivity;
import dev.nick.systemrecapi.ui.RecRequestAsker;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Delegate;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class RecBridgeService extends Service implements Handler.Callback {

    private static final String NOTIFICATION_CHANNEL_ID = "dev.nick.systemrecapi.notification.channel.id.RecBridgeService";

    private static final String SCREENCASTER_NAME = "hidden:screen-recording";

    private static final String ACTION_STOP_SCREENCAST = "stop.recording";
    private static final String ACTION_START_SCREENCAST = "start.recording";

    private static final int SENSOR_SHAKE = 10;

    private final List<IWatcher> mWatchers = new ArrayList<>();

    private RecordingDevice mRecorder;
    private boolean mIsCasting;

    private Handler mSensorEventHandler;

    private MediaProjection mProjection;
    private RecRequest mRecRequest;

    private long startTime;
    private Timer timer;
    private Notification.Builder mBuilder;

    private SoundPool mSoundPool;
    private int mStartSound, mStopSound;

    private SettingsProvider mSettingsProvider;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.i("onReceive:" + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_USER_BACKGROUND) ||
                    intent.getAction().equals(ACTION_STOP_SCREENCAST) ||
                    intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
                stop();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (mIsCasting && mRecRequest != null && mRecRequest.isStopOnScreenOff()) {
                    stop();
                }
            }
        }
    };

    private SensorManager mSensorManager;
    private Vibrator mVibrator;

    private SensorEventListener mSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            float x = values[0];
            float y = values[1];
            float z = values[2];
            int mediumValue = 19;
            if (Math.abs(x) > mediumValue || Math.abs(y) > mediumValue || Math.abs(z) > mediumValue) {
                Message msg = new Message();
                msg.what = SENSOR_SHAKE;
                mSensorEventHandler.sendMessage(msg);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private EventReceiver mEventReceiver = new EventReceiver() {
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

    private void onStartWithoutProjection() {
        Logger.i("onStartWithoutProjection");
        startInternal();
    }

    private void onProjectionReady() {
        RecBridgeApp app = (RecBridgeApp) getApplication();
        MediaProjection projection = app.getProjection();
        Logger.i("onProjectionReady:%s", projection);
        mProjection = projection;

        startInternal();
    }

    private void onProjectionStop() {
        Logger.i("onProjectionStop");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        startService(new Intent(this, RecBridgeService.class));
        return new RecBridgeBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (EventBus.getInstance() == null) {
            EventBus.create(getApplication()).setDebuggable(false);
        }
        EventBus.getInstance().subscribe(mEventReceiver);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        mSensorEventHandler = new Handler(this);

        mSettingsProvider = new SettingsProvider(getApplicationContext());

        mSensorManager.registerListener(mSensorEventListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build())
                .build();

        mStopSound = mSoundPool.load(this, dev.nick.library.R.raw.video_stop, 1);
        mStartSound = mSoundPool.load(this, dev.nick.library.R.raw.video_record, 1);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(ACTION_STOP_SCREENCAST);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, filter);

        createNotificationChannelForO();
    }

    synchronized void cleanup() {
        String recorderPath = null;
        if (mRecorder != null) {
            recorderPath = mRecorder.getRecordingFilePath();
            Logger.i("recorderPath:%s", recorderPath);
            mRecorder.stop();
            mRecorder = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        stopForeground(true);

        if (recorderPath != null && mRecRequest.isShowNotification()) {
            sendShareNotification(recorderPath);
        }

        if (mProjection != null) {
            mProjection.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getInstance().unSubscribe(mEventReceiver);

        stopCasting();
        unregisterReceiver(mBroadcastReceiver);
        mSoundPool.release();
        mSoundPool = null;
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mSensorEventListener);
        }
        super.onDestroy();
        if (mProjection != null) try {
            mProjection.stop();
        } catch (Exception ignored) {
        }
    }

    public synchronized void start(final RecRequest recRequest) {
        // Check if we are recording.
        if (isRecording()) {
            Logger.w("Ignore start request in recording");
            return;
        }
        // Ask for user first.
        final String pkgName = getApplicationContext().getPackageManager().getNameForUid(Binder.getCallingUid());

        if (TextUtils.isEmpty(pkgName)) {
            Logger.w("Ignored, bad pkg name from client");
            return;
        }

        Logger.d("Start called with recRequest:%s, name:%s", recRequest, pkgName);
        mRecRequest = recRequest;

        final Holder<String> description = new Holder<>();
        try {
            description.setData(recRequest.getClient().getDescription());
        } catch (RemoteException e) {
            Logger.w("Ignored, bad description from client");
            return;
        }

        boolean allowed = mSettingsProvider.isAppRecAllowed(pkgName);
        if (!allowed) {
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    RecRequestAsker.askForUser(getApplicationContext(),
                            pkgName,
                            description.getData(),
                            new RecRequestAsker.Callback() {
                                @Override
                                public void onAllow() {
                                    handleRequest();
                                }

                                @Override
                                public void onDeny() {
                                    mSettingsProvider.setAppRecAllowed(pkgName, false);
                                    try {
                                        recRequest.getClient().onDeny();
                                    } catch (Throwable e) {
                                        Logger.e(e, "Error call onDeny");
                                    }
                                }

                                @Override
                                public void onRemember() {
                                    mSettingsProvider.setAppRecAllowed(pkgName, true);
                                    handleRequest();
                                }
                            });
                }
            });
            return;
        }

        handleRequest();
    }

    private void handleRequest() {
        boolean useProjection = mRecRequest.isUseMediaProjection();
        Logger.d("useProjection? %s", useProjection);

        if (useProjection) {
            startRecBridgeActivity();
        } else {
            onStartWithoutProjection();
        }
    }

    private void startRecBridgeActivity() {
        Logger.i("startRecBridgeActivity");
        Intent intent = new Intent(this, RecBridgeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(RecBridgeActivity.ACTION_START_REC);
        startActivity(intent);
    }

    boolean startInternal() {
        Logger.d("startInternal");

        if (mRecRequest == null) return false;

        if (!hasAvailableSpace()) {
            ThreadUtil.getMainThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),
                            R.string.not_enough_storage, Toast.LENGTH_LONG).show();
                }
            });
            return false;
        }

        try {
            mIsCasting = true;
            notifyCasting();

            updateShowTouchSettings(mRecRequest.isShowTouch());

            startTime = SystemClock.elapsedRealtime();

            registerScreenCaster();

            if (mRecRequest.isShutterSound()) {
                mSoundPool.play(mStartSound, 1.0f, 1.0f, 0, 0, 1.0f);
            }

            mBuilder = createNotificationBuilder();

            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    onElapsedTimeChanged();

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
        boolean landscape = mRecRequest.getOrientation() == Orientations.L;
        int width, height;
        int preferredResIndex = ValidResolutions.indexOf(mRecRequest.getResolution());
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

    void registerScreenCaster() throws RemoteException {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        assert mRecorder == null;
        Point size = getNativeResolution();
        mRecorder = new RecordingDevice(this, size.x, size.y,
                mRecRequest.getAudioSource(),
                mRecRequest.getOrientation(),
                mRecRequest.getFrameRate(),
                mRecRequest.getAudioBitrate(),
                mRecRequest.getPath());
        boolean useProjection = mRecRequest.isUseMediaProjection();
        VirtualDisplay vd =
                useProjection ?
                        mRecorder.registerVirtualDisplay(mProjection, SCREENCASTER_NAME)
                        : mRecorder.registerVirtualDisplay(this, SCREENCASTER_NAME);
        Logger.i("VirtualDisplay, vd:%s", vd);
        if (vd == null) {
            cleanup();
        }
    }

    private void stopCasting() {
        Logger.d("stopCasting");
        cleanup();

        if (mIsCasting && mRecRequest != null && mRecRequest.isShutterSound()) {
            mSoundPool.play(mStopSound, 1.0f, 1.0f, 0, 0, 1.0f);
        }
        mIsCasting = false;

        // Clean up request.
        if (mRecRequest != null) {
            mRecRequest.getClient().unLinkToDeath();
            mRecRequest = null;
        }

        notifyUncasting();
        updateShowTouchSettings(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_NOT_STICKY;
        if (ACTION_STOP_SCREENCAST.equals(intent.getAction())) {
            stop();
        }
        if (ACTION_START_SCREENCAST.equals(intent.getAction())) {

        }
        return START_STICKY;
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

    public void onClientDie(TokenClient client) {
        if (client == mRecRequest.getClient() && isRecording()) {
            stop();
        } else {
            client.unLinkToDeath();
        }
    }

    public boolean isRecording() {
        return mIsCasting;
    }

    public void watch(IWatcher w) {
        synchronized (mWatchers) {
            if (!mWatchers.contains(w)) {
                mWatchers.add(w);
            }
        }
        notifySticky(w);
    }

    void notifySticky(final IWatcher watcher) {
        Logger.i("notifySticky:%s", watcher);
        try {
            if (isRecording())
                watcher.onStart();
            else
                watcher.onStop();
        } catch (Throwable e) {
            Logger.e(e, "Error call watcher");
        }
    }

    public boolean checkSelfPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.CAPTURE_AUDIO_OUTPUT)
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
                boolean shouldHandle = isRecording() && mRecRequest != null && mRecRequest.isStopOnShake();
                if (!shouldHandle) return true;
                if (mIsCasting) {
                    mVibrator.vibrate(100);
                    stop();
                }
                return true;
        }
        return false;
    }

    public void onElapsedTimeChanged() {
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;
        String timeStr = DateUtils.formatElapsedTime(timeElapsed / 1000);
        String time = getString(R.string.video_length, timeStr);
        mBuilder.setContentText(time);
        if (mRecRequest.isShowNotification()) {
            startForeground(1, mBuilder.build());
        }

        notifyTimeChange(timeStr);
    }

    private void updateShowTouchSettings(boolean show) {
        Logger.d("updateShowTouchSettings: " + show);
        try {
            Settings.System.putInt(getContentResolver(), "show_touches", show ? 1 : 0);
        } catch (Throwable e) {
            Logger.e("Fail updateShowTouchSettings: " + Logger.getStackTraceString(e));
        }
    }

    private Notification.Builder createNotificationBuilder() {
        Notification.Builder builder = new Notification.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_device_access_video)
                .setContentTitle(getString(R.string.recording));
        Intent stopRecording = new Intent(ACTION_STOP_SCREENCAST);
        stopRecording.setClass(this, RecBridgeService.class);
        builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
                PendingIntent.getService(this, 0, stopRecording, PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID);
        }
        return builder;
    }

    private void createNotificationChannelForO() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel;
            notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    "REC_BRIDGE",
                    NotificationManager.IMPORTANCE_LOW);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    private void sendShareNotification(String recordingFilePath) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        notificationManager.notify(0, mBuilder.build());
    }

    private Notification.Builder createShareNotificationBuilder(String file) {
        Intent sharingIntent = buildSharedIntent(this, new File(file));
        Intent chooserIntent = Intent.createChooser(sharingIntent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;

        Intent open = buildOpenIntent(this, new File(file));
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

    public static Intent buildSharedIntent(Context context, File imageFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("video/mp4");
            sharingIntent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", imageFile));
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, imageFile.getName());
            Intent chooserIntent = Intent.createChooser(sharingIntent, null);
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            return chooserIntent;
        } else {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("video/mp4");
            Uri uri = Uri.parse("file://" + imageFile.getAbsolutePath());
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, imageFile.getName());
            return sharingIntent;

        }
    }

    public static Intent buildOpenIntent(Context context, File imageFile) {

        Intent open = new Intent(Intent.ACTION_VIEW);
        Uri contentUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            open.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            contentUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", imageFile);

        } else {
            contentUri = Uri.parse("file://" + imageFile.getAbsolutePath());
        }
        open.setDataAndType(contentUri, "video/mp4");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return open;
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
        public void start(IParam param, IToken token) throws RemoteException {
            TokenClient tokenClient = new TokenClient(token);
            RecBridgeService.this.start(new RecRequest(param, tokenClient));
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
            RecBridgeService.this.watch(Preconditions.checkNotNull(w));
        }

        @Override
        public void unWatch(IWatcher w) throws RemoteException {
            RecBridgeService.this.unWatch(Preconditions.checkNotNull(w));
        }

        @Override
        public boolean checkSelfPermission() throws RemoteException {
            return RecBridgeService.this.checkSelfPermission();
        }

    }

    @AllArgsConstructor
    @Getter
    @ToString
    private class RecRequest {
        @Delegate
        IParam param;
        TokenClient client;
    }

    private class TokenClient implements IBinder.DeathRecipient {

        @Delegate
        IToken token;

        TokenClient(IToken token) {
            this.token = token;
            Logger.d("IToken.Binder:%s", token.asBinder());
            try {
                token.asBinder().linkToDeath(this, 0);
            } catch (Throwable e) {
                Logger.e(e, "Fail link to death");
            }
        }

        @Override
        public void binderDied() {
            Logger.w("binderDied:%s", token);
            onClientDie(this);
        }

        void unLinkToDeath() {
            try {
                token.asBinder().unlinkToDeath(this, 0);
            } catch (Throwable e) {
                Logger.e(e, "Fail unlink to death");
            }
        }
    }

}
