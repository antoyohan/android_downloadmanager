package com.media.downloadmanager.interfaces;

public interface DownloadStatusListener {

    //Callback when download is successfully completed
    void onDownloadComplete(String id);

    //Callback if download is failed. Corresponding error code and error messages are provided
    void onDownloadFailed(String id, int errorCode, String errorMessage);

    //Callback provides download progress
    void onProgress(String id, long downloadedBytes, int progress);
}
