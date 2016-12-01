package com.media.downloadmanager;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.media.downloadmanager.database.DbManager;
import com.media.downloadmanager.interfaces.DownloadStatusListener;
import com.media.downloadmanager.interfaces.IDownloadManager;
import com.media.downloadmanager.model.DownloadRequest;

public class DownloadManager implements IDownloadManager {

    private static DownloadManager sInstance;
    private DownloadRequestQueue mRequestQueue;
    private Context mContext;
    private final String TAG = "DownloadManager";

    public static DownloadManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DownloadManager(context);
        }
        return sInstance;
    }

    private DownloadManager(Context ctx) {
        mRequestQueue = new DownloadRequestQueue();
        mContext = ctx;
    }

    @Override
    public int add(DownloadRequest request) {
        addTodb(request);
        startDownloadService();
        startDownloading();
        return 0;
    }

    private void addTodb(final DownloadRequest request) {
        Log.d(TAG, "addtodb " + request.getArticleId());
        DbManager.getInstance().insert(request, null);
        mRequestQueue.add(request);
    }

    public void startDownloadService() {
        if (!DownloadService.isRunning()) {
            mContext.startService(new Intent(mContext, DownloadService.class));
        }
    }

    @Override
    public int pause(String id) {
        mRequestQueue.pause(id);
        return 0;
    }

    @Override
    public int resume(String id) {
        DownloadRequest req = DbManager.getInstance().getDownloadRequest(id);
        mRequestQueue.resume(req);
        startDownloadService();
        startDownloading();
        return 0;
    }

    @Override
    public int cancel(String id) {
        return mRequestQueue.cancel(id);
    }

    @Override
    public void release() {
        mRequestQueue.stop();
    }

    boolean isDownloadableItemsPresent() {
        return DbManager.getInstance().ifPendingItemPresent();
    }

    void startDownloading() {
        mRequestQueue.start();
    }

    @Override
    public void pauseAll() {
        mRequestQueue.pauseAll();
    }

    public void setDownloadStatusListener(DownloadStatusListener listener) {
        mRequestQueue.setDownloadStatusListener(listener);
    }

    public boolean isQueueEmpty() {
        return mRequestQueue.isEmpty();
    }

    void placeEverythingInQueue() {
        mRequestQueue.placeEverythingInQueue();
    }

    void reloadQueue() {
        mRequestQueue.reload();
    }

    public static void setIsConnectedtoWifi(boolean connectedToWifi) {
        DownloadRequestQueue.setIsConnectedToWifi(connectedToWifi);
    }

    public String getCurrentDownloadRequestId() {
        return mRequestQueue.getCurrentDownloadId();
    }

    public void stopDownloadService() {
        mContext.stopService(new Intent(mContext, DownloadService.class));
    }
}
