package dev.nick.library.cast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.nick.library.RecBridgeService;

public class RecControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(intent.setClass(context, RecBridgeService.class));
    }
}
