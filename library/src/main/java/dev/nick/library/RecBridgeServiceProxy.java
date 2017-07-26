package dev.nick.library;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import dev.nick.library.common.Holder;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */
public class RecBridgeServiceProxy extends ServiceProxy implements IRecBridge {

    private IRecBridge bridge;

    private RecBridgeServiceProxy(Context context, Intent intent) {
        super(context, intent);
    }

    public static Intent getIntent() {
        Intent intent = new Intent();
        intent.setClassName("dev.nick.systemrecapi", "dev.nick.systemrecapi.RecBridgeService");
        return intent;
    }

    public static RecBridgeServiceProxy from(Context context) {
        return new RecBridgeServiceProxy(context, getIntent());
    }

    @Override
    public void onConnected(IBinder binder) {
        bridge = IRecBridge.Stub.asInterface(binder);
    }

    @Override
    public String getVersionName() throws RemoteException {
        final Holder<String> res = new Holder<>();
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                res.setData(bridge.getVersionName());
            }
        });
        waitForCompletion();
        return res.getData();
    }

    @Override
    public int getVersionCode() throws RemoteException {
        final Holder<Integer> res = new Holder<>();
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                res.setData(bridge.getVersionCode());
            }
        });
        waitForCompletion();
        return res.getData();
    }

    @Override
    public void start(final IParam param, final IToken token) throws RemoteException {
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                bridge.start(param, token);
            }
        });
    }

    @Override
    public void stop() throws RemoteException {
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                bridge.stop();
            }
        });
    }

    @Override
    public boolean isRecording() throws RemoteException {
        final Holder<Boolean> res = new Holder<>();
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                res.setData(bridge.isRecording());
            }
        });
        waitForCompletion();
        return res.getData();
    }

    @Override
    public void watch(final IWatcher w) throws RemoteException {
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                bridge.watch(w);
            }
        });
    }

    @Override
    public void unWatch(final IWatcher w) throws RemoteException {
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                bridge.unWatch(w);
            }
        });
    }

    @Override
    public boolean checkSelfPermission() throws RemoteException {
        final Holder<Boolean> res = new Holder<>();
        setTask(new ProxyTask() {
            @Override
            public void run() throws RemoteException {
                res.setData(bridge.checkSelfPermission());
            }
        });
        waitForCompletion();
        return res.getData();
    }

    @Override
    public IBinder asBinder() {
        return bridge.asBinder();
    }
}
