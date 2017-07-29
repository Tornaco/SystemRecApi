package dev.nick.systemrecapi.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.WindowManager;

import dev.nick.systemrecapi.R;


/**
 * Created by Tornaco on 2017/7/25.
 * Licensed with Apache.
 */

public class RecRequestAsker {

    public interface Callback {
        void onAllow();

        void onDeny();

        void onRemember();
    }

    public static void askForUser(Context context, String packageName, String description,
                                  final Callback callback) {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.rec_bridge_request_title)
                .setMessage(description == null ?
                        context.getString(R.string.rec_bridge_request_message,
                                getApplicationName(context, packageName))
                        :
                        context.getString(R.string.rec_bridge_request_message,
                                getApplicationName(context, packageName))
                                + "\n"
                                + description)
                .setCancelable(false)
                .setPositiveButton(R.string.rec_bridge_request_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        callback.onAllow();
                    }
                })
                .setNegativeButton(R.string.rec_bridge_request_deny, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        callback.onDeny();
                    }
                })
                .setNeutralButton(R.string.rec_bridge_request_remember, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        callback.onRemember();
                    }
                })
                .create();
        //noinspection ConstantConditions
        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alertDialog.show();
    }

    private static String getApplicationName(Context context, String pkg) {
        PackageManager packageManager = null;
        ApplicationInfo applicationInfo = null;
        try {
            packageManager = context.getPackageManager();
            applicationInfo = packageManager.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        return (String) packageManager.getApplicationLabel(applicationInfo);
    }
}
