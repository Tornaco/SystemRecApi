package dev.nick.library;

import android.content.Context;
import android.content.pm.PackageManager;

import lombok.experimental.var;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public class LibDetector {

    public static boolean isInstalled(Context context) {
        PackageManager packageManager = context.getPackageManager();
//        Intent intent = new Intent();
//        intent.setAction("dev.nick.app.action.BINDE_REC_BRIDGE_SERVICE");
//        intent.setClassName("dev.nick.library", "dev.nick.library.RecBridgeService");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                var out = packageManager.getPackageInfo("dev.nick.systemrecapi", PackageManager.MATCH_UNINSTALLED_PACKAGES);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        } else {
            try {
                var out = packageManager.getPackageInfo("dev.nick.systemrecapi", PackageManager.GET_UNINSTALLED_PACKAGES);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return false;
    }
}
