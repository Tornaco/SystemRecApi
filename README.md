# SystemRecApi

> 基于CS模式，服务端安装于System下，拥有系统权限。客户端安装于data下（普通安装），通过aidl和服务断通讯。

### 客户端Gradle编译
[![](https://jitpack.io/v/Tornaco/SystemRecApi.svg)](https://jitpack.io/#Tornaco/SystemRecApi)

1. Add it in your root build.gradle at the end of repositories:
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
2. Add the dependency
```
dependencies {
	        compile 'com.github.Tornaco:SystemRecApi:v1.3'
	}
```

### Build in travis
[![Build Status](https://travis-ci.org/Tornaco/SystemRecApi.svg?branch=master)](https://travis-ci.org/Tornaco/SystemRecApi)

### 服务端的API调用（先确保服务端已经安装）

所有API均集成于```RecBridgeServiceProxy```

1. 获取其实例
```
RecBridgeServiceProxy proxy = RecBridgeServiceProxy.from(this);
```

2. 开始录制
```
 RecBridgeServiceProxy.from(context)
                    .start(IParam.builder()
                                    .audioSource(settingsProvider.getInt(SettingsProvider.Key.AUDIO_SOURCE))
                                    .frameRate(settingsProvider.getInt(SettingsProvider.Key.FAME_RATE))
                                    .audioBitrate(settingsProvider.getInt(SettingsProvider.Key.AUDIO_BITRATE_RATE_K))
                                    .orientation(settingsProvider.getInt(SettingsProvider.Key.ORIENTATION))
                                    .resolution(settingsProvider.getString(SettingsProvider.Key.RESOLUTION))
                                    .stopOnScreenOff(settingsProvider.getBoolean(SettingsProvider.Key.SCREEN_OFF_STOP))
                                    .useMediaProjection(!isPlatformBridge)
                                    .stopOnShake(settingsProvider.getBoolean(SettingsProvider.Key.SHAKE_STOP))
                                    .shutterSound(settingsProvider.getBoolean(SettingsProvider.Key.SHUTTER_SOUND))
                                    .path(SettingsProvider.get().createVideoFilePath())
                                    .showNotification(true)
                                    .showTouch(settingsProvider.getBoolean(SettingsProvider.Key.SHOW_TOUCH))
                                    .build(),
```

3. 停止录制
```
proxy.stop();
```

4. 监听(sticky)
```
proxy.watch(new IWatcher.Stub() {
            @Override
            public void onStart() throws RemoteException {
                
            }

            @Override
            public void onStop() throws RemoteException {

            }

            @Override
            public void onElapsedTimeChange(String s) throws RemoteException {

            }
        });
```
