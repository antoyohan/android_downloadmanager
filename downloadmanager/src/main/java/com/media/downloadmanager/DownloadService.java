package com.media.downloadmanager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class DownloadService extends Service {

    protected static final String TAG = "DownloadService";
    private static boolean isRunning;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void init() {
        Log.d(TAG, "Starting Download service...");
        DownloadManager downloadManager = DownloadManager.getInstance(this);

        if (downloadManager.isDownloadableItemsPresent()) {
            //repopulate the queue and start download
            DownloadManager.getInstance(this).reloadQueue();
            downloadManager.startDownloading();
        }
    }

    public void stop() {
        DownloadManager.getInstance(getApplicationContext()).release();
        stopSelf();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        Log.e(TAG, "onStartCommand");
        init();
        return START_STICKY;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved");
        DownloadManager.getInstance(this).placeEverythingInQueue();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        isRunning = false;
        DownloadManager.getInstance(this).placeEverythingInQueue();
        DownloadManager.getInstance(this).release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
