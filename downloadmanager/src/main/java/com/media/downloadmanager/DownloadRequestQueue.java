package com.media.downloadmanager;


import android.util.Log;


import com.media.downloadmanager.database.DbManager;
import com.media.downloadmanager.interfaces.DownloadStatusListener;
import com.media.downloadmanager.interfaces.IDownloadState;
import com.media.downloadmanager.model.DownloadRequest;
import com.media.downloadmanager.utils.DownloadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

public class DownloadRequestQueue {

    private static final String TAG = "DownloadRequestQueue";
    /**
     * The queue of requests that are actually going out to the network.
     */
    private PriorityBlockingQueue<DownloadRequest> mDownloadQueue = new PriorityBlockingQueue<>();

    /**
     * The download dispatcher
     */
    private DownloadDispatcher mDownloadDispatcher;

    /**
     * ArrayList to store queue objects
     */
    private ArrayList<DownloadRequest> mDownloadQueueList = new ArrayList<>();
    private static boolean sIsConnectedToWifi = false;

    void setDownloadStatusListener(DownloadStatusListener listener) {
        if (mDownloadDispatcher != null) {
            mDownloadDispatcher.setDownloadStatusListener(listener);
        }
    }

    /**
     * Default constructor.
     */
    DownloadRequestQueue() {
        initialize();
        setIsConnectedToWifi(DownloadUtils.isConnectedToWifi());
    }

    void start() {
        //stop();
        try {
            if (!mDownloadDispatcher.isAlive()) {
                mDownloadDispatcher.start();
            }
        } catch (IllegalThreadStateException ex) {
            Log.d(TAG, "Thread Already Started");
        }
    }

    /**
     * Adds the download request to the download request queue for the dispatchers pool to act on immediately.
     *
     * @param request Download request to be added
     */
    public void add(DownloadRequest request) {
        boolean alreadyInQueue = false;
        for (DownloadRequest req : mDownloadQueueList) {
            if (request.getArticleId().equals(req.getArticleId())) {
                alreadyInQueue = true;
                break;
            }
        }
        if (mDownloadDispatcher.getCurrentDownloadId().equals(request.getArticleId())) {
            alreadyInQueue = true;
        }
        if (!alreadyInQueue) {
            Log.d(TAG, "adding new request 1 " + request.getArticleId());
            if (request.isDownloadOnWiFi()) {
                if (sIsConnectedToWifi) {
                    request.setPriority(DownloadRequest.Priority.NORMAL);
                    mDownloadQueue.add(request);
                    mDownloadQueueList.add(request);
                }
            } else {
                request.setPriority(DownloadRequest.Priority.NORMAL);
                mDownloadQueue.add(request);
                mDownloadQueueList.add(request);
            }
            updateDownloadStateToDb(IDownloadState.IN_QUEUE, request);
        }
    }

    /**
     * Adds the download request to the download request queue for the dispatchers pool to act on immediately.
     *
     * @param request Download request to be added
     */
    void resume(DownloadRequest request) {
        boolean alreadyInQueue = false;
        for (DownloadRequest req : mDownloadQueueList) {
            if (request.getArticleId().equals(req.getArticleId())) {
                alreadyInQueue = true;
                resumeImmediately(req);
                break;
            }
        }
        if (mDownloadDispatcher.getCurrentDownloadId().equals(request.getArticleId())) {
            alreadyInQueue = true;
        }
        if (!alreadyInQueue) {
            Log.d(TAG, "adding new request 2 " + request.getArticleId());
            Log.d(TAG, "downloadQueue " + mDownloadQueue.size());
            if (request.isDownloadOnWiFi()) {
                if (sIsConnectedToWifi) {
                    request.setPriority(DownloadRequest.Priority.NORMAL);
                    mDownloadQueue.add(request);
                    mDownloadQueueList.add(request);
                }
            } else {
                request.setPriority(DownloadRequest.Priority.NORMAL);
                mDownloadQueue.add(request);
                mDownloadQueueList.add(request);
            }
            updateDownloadStateToDb(IDownloadState.IN_QUEUE, request);
        }
    }

    private void updateDownloadStateToDb(int inQueue, DownloadRequest request) {
        request.setDownloadState(inQueue);
        DbManager.getInstance().update(request);
    }

    private void resumeImmediately(DownloadRequest req) {
        addItToFront(req);
        pauseCurrentDownload();
    }

    private void pauseCurrentDownload() {
        mDownloadDispatcher.setPauseId(getCurrentDownloadId());
    }

    /**
     * Since the priority is set while we add the object to the queue.
     * First we need the remove the request from the queue , set the priority
     * and add it again.
     */
    private void addItToFront(DownloadRequest req) {
        mDownloadQueue.remove(req);
        req.setPriority(DownloadRequest.Priority.IMMEDIATE);
        mDownloadQueue.add(req);
    }

    /**
     * Cancel the dispatchers in work and also stops the dispatchers.
     */
    void pauseAll() {
        mDownloadQueue.clear();
        mDownloadQueueList.clear();
        mDownloadDispatcher.setPauseId(mDownloadDispatcher.getCurrentDownloadId());
    }

    /**
     * Cancel a particular download in progress. Returns 1 if the download Id is found else returns 0.
     *
     * @param cancelId id which should be cancelled
     * @return int
     */
    int cancel(String cancelId) {
        String destinationPath = "";
        if (getCurrentDownloadId().equals(cancelId)) {
            mDownloadDispatcher.setCancelId(cancelId);
            return 0;
        } else {
            for (DownloadRequest req : mDownloadQueueList) {
                if (req.getArticleId().equals(cancelId)) {
                    destinationPath = req.getDestinationPath();
                    mDownloadQueue.remove(req);
                    mDownloadQueueList.remove(req);
                    break;
                }
            }

            if (destinationPath.isEmpty()) {
                destinationPath = DbManager.getInstance().getDownloadRequest(cancelId).getDestinationPath();
            }

            cleanupDestination(destinationPath);
            deleteFromDb(cancelId);
            return 0;
        }
    }

    private void deleteFromDb(String cancelId) {
        DbManager.getInstance().delete(cancelId);
    }

    private void cleanupDestination(String destinationPath) {
        Log.d(TAG, "cleanupDestination() deleting " + destinationPath);
        File destinationFile = new File(destinationPath);
        if (destinationFile.exists()) {
            destinationFile.delete();
        }
    }

    /**
     * Perform construction
     */
    private void initialize() {
        mDownloadDispatcher = new DownloadDispatcher(mDownloadQueue, mDownloadQueueList);
    }

    /**
     * Stops download dispatchers.
     */
    void stop() {
        mDownloadDispatcher.quit();
    }

    void pause(String id) {
        mDownloadDispatcher.setPauseId(id);
    }

    String getCurrentDownloadId() {
        return mDownloadDispatcher.getCurrentDownloadId();
    }

    boolean isEmpty() {
        return mDownloadQueue.isEmpty();
    }

    void placeEverythingInQueue() {
        mDownloadQueue.clear();
        mDownloadQueueList.clear();
        mDownloadDispatcher.setQueueId(mDownloadDispatcher.getCurrentDownloadId());
    }

    void reload() {
        List<DownloadRequest> requestList = DbManager.getInstance().getPendingItems();
        Log.d(TAG, "reload " + requestList.size());
        for (DownloadRequest req : requestList) {
            resume(req);
        }
    }

    static void setIsConnectedToWifi(boolean sIsConnectedToWifi) {
        DownloadRequestQueue.sIsConnectedToWifi = sIsConnectedToWifi;
    }
}

