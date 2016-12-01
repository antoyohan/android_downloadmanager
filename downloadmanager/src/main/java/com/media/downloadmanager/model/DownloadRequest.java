package com.media.downloadmanager.model;


import android.util.Log;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class DownloadRequest extends RealmObject implements Comparable<DownloadRequest> {

    @PrimaryKey
    private String mArticleId;
    private String mUrl;
    private boolean mDownloadOnWiFi;
    private int mProgress = 0;

    private String mDestinationPath;
    private int mDownloadState;
    private long mDownloadedBytes;
    private long mTotalBytes;
    private long mTimeStamp;
    private int mPriority = Priority.NORMAL.getValue();

    public static final String ARTICLE_ID = "mArticleId";
    public static final String PROGRESS = "mProgress";
    public static final String DOWNLOAD_STATE = "mDownloadState";
    public static final String TIME_STAMP = "mTimeStamp";

    public DownloadRequest(String articleId, String url, boolean downloadOnWiFi,
                           String destinationPath, long timeStamp) {
        this.mArticleId = articleId;
        this.mUrl = url;
        this.mDownloadOnWiFi = downloadOnWiFi;
        this.mDestinationPath = destinationPath;
        this.mTimeStamp = timeStamp;
    }

    public DownloadRequest() {

    }

    public String getArticleId() {
        return mArticleId;
    }

    public String getUrl() {
        return mUrl;
    }

    public boolean isDownloadOnWiFi() {
        return mDownloadOnWiFi;
    }

    public int getProgress() {
        return mProgress;
    }

    public String getDestinationPath() {
        return mDestinationPath;
    }

    public long getDownloadedBytes() {
        return mDownloadedBytes;
    }

    public void setDownloadedBytes(long mDownloadedBytes) {
        this.mDownloadedBytes = mDownloadedBytes;
    }

    public long getTotalBytes() {
        return mTotalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.mTotalBytes = totalBytes;
    }

    public int getDownloadState() {
        return mDownloadState;
    }

    public void setDownloadState(int downloadState) {
        this.mDownloadState = downloadState;
    }

    public int getPriority() {
        return mPriority;
    }

    public void setPriority(Priority priority) {
        this.mPriority = priority.getValue();
    }

    public void setArticleId(String articleId) {
        this.mArticleId = articleId;
    }

    public void setUrl(String url) {
        this.mUrl = url;
    }

    public void setDownloadOnWiFi(boolean downloadOnWiFi) {
        this.mDownloadOnWiFi = downloadOnWiFi;
    }

    public void setmDestinationPath(String mDestinationPath) {
        this.mDestinationPath = mDestinationPath;
    }

    /**
     * Priority values.  Requests will be processed from higher priorities to
     * lower priorities, in FIFO order.
     */
    public enum Priority {
        LOW(101),
        NORMAL(102),
        HIGH(103),
        IMMEDIATE(104);
        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @Override
    public int compareTo(DownloadRequest other) {
        int left = this.getPriority();
        int right = other.getPriority();

        // High-priority requests are "lesser" so they are sorted to the front.
        // Equal priorities are not sorted (otherwise provide the logic here)
        return left == right ? 1 : right - left;
    }

    public String toString() {
        Log.d("DownloadRequest", " id " + mArticleId + "/n url " + mUrl + "/n path " + mDestinationPath);
        return null;
    }
}
