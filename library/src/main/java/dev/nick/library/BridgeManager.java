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

    static final String PKG_NAME = "dev.nick.systemrecapi";

    public static BridgeManager getInstance() {
        return Instance;
    }

    public boolean isInstalled(Context context) {
        try {
            var packageInfo = context.getPackageManager().getPackageInfo(
                    PKG_NAME, 0);
            return packageInfo != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean isInstalledInSystem(Context context) {
        try {
            var packageInfo = context.getPackageManager().getPackageInfo(
                    PKG_NAME, 0);
            return packageInfo != null && packageInfo.sharedUserId.equals("android.uid.system");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public String getVersionName(Context context) {
        try {
            var packageInfo = context.getPackageManager().getPackageInfo(
                    PKG_NAME, 0);
            return packageInfo != null ? packageInfo.packageName : null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public int getVersionCode(Context context) {
        try {
            var packageInfo = context.getPackageManager().getPackageInfo(
                    PKG_NAME, 0);
            return packageInfo != null ? packageInfo.versionCode : -1;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }
}
