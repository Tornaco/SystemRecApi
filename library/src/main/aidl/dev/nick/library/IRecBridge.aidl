package dev.nick.library;

import dev.nick.library.IParam;
import dev.nick.library.IWatcher;

interface IRecBridge {
    String getVersionName();
    int getVersionCode();

    void start(in IParam param);
    void stop();

    boolean isRecording();

    void watch(in IWatcher w);
    void unWatch(in IWatcher w);

    boolean checkSelfPermission();
}
