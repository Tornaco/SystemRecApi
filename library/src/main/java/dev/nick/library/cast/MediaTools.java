package dev.nick.library.cast;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;

import java.io.File;

import dev.nick.library.BuildConfig;

public abstract class MediaTools {

    public static Intent buildSharedIntent(Context context, File imageFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("video/mp4");
            sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + imageFile.getAbsolutePath()));
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
            contentUri = FileProvider.getUriForFile(context,
                    BuildConfig.APPLICATION_ID + ".provider", imageFile);//FIXME

        } else {
            contentUri = Uri.parse("file://" + imageFile.getAbsolutePath());
        }
        open.setDataAndType(contentUri, "video/mp4");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return open;
    }
}
