# SystemRecApi

### Use in gradle
[![](https://jitpack.io/v/Tornaco/SystemRecApi.svg)](https://jitpack.io/#Tornaco/SystemRecApi)

### Build status in travis
[![Build Status](https://travis-ci.org/Tornaco/SystemRecApi.svg?branch=master)](https://travis-ci.org/Tornaco/SystemRecApi)

### Usage

## All op can be achieved by RecBridgeServiceProxy

1. How to get an instance of proxy.
```
RecBridgeServiceProxy proxy = RecBridgeServiceProxy.from(this);
```

2. Service intent.
```
RecBridgeServiceProxy.getIntent();
```

3. Start
```
proxy.start(IParam.builder()
                .stopOnShake(true)
                .path("path")
                .orientation(Orientations.AUTO)
                .audioSource(AudioSource.R_SUBMIX)
                .frameRate(60)
                .shutterSound(true)
                .build(), new IToken.Stub() {
            @Override
            public String getDescription() throws RemoteException {
                return "Description about your app";
            }

            @Override
            public void onDeny() throws RemoteException {

            }

            @Override
            public void onAllow() throws RemoteException {

            }

            @Override
            public void onAllowRemember() throws RemoteException {

            }
        });
```

4. Stop
```
proxy.stop();
```

5. Watch(sticky)
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

6. Check permission(unnecesary)
```
proxy.checkSelfPermission()
```
