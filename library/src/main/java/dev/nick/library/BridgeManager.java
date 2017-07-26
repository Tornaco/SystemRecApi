package dev.nick.library;

import android.content.Context;
import android.content.pm.PackageManager;

import lombok.experimental.var;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */

public enum BridgeManager {

    Instance;

    public static BridgeManager getInstance() {
        return Instance;
    }

    public boolean isInstalled(Context context) {
        try {
            var packageInfo = context.getPackageManager().getPackageInfo(
                    "dev.nick.systemrecapi", 0);
            return packageInfo != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
