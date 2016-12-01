package com.media.downloadmanager.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.StatFs;

import com.media.downloadmanager.application.ApplicationClass;

import java.io.File;

public class DownloadUtils {

    private static final long sFreeSpaceBuffer = 512 * 1024 * 1024; //512MB free space is reserved

    public static boolean isConnectedToWifi() {
        Context context = ApplicationClass.getContext();
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            return networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        } else {
            return false;
        }
    }

    public static boolean isMemoryAvailable(String path, long downloadSize) {
        long remainingSpaceAvailable = getAvailableFreeMemory(new File(path));
        return ((remainingSpaceAvailable - downloadSize) > sFreeSpaceBuffer);
    }

    private static long getAvailableFreeMemory(File path) {
        if (path == null) return -1;
        StatFs stat = new StatFs(path.getPath());
        long blockSize = getBlockSize(stat);
        long availableBlocks = getAvailableBlocks(stat);
        double size = availableBlocks * blockSize;
        return (long) size;
    }

    private static long getBlockSize(StatFs stat) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return stat.getBlockSizeLong();
        }
        return stat.getBlockSize();
    }

    private static long getAvailableBlocks(StatFs stat) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return stat.getAvailableBlocksLong();
        }
        return stat.getAvailableBlocks();
    }
}
