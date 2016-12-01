package com.media.downloadmanager.interfaces;


import com.media.downloadmanager.model.DownloadRequest;

public interface IDownloadManager {

    int add(DownloadRequest request);

    int pause(String id);

    void pauseAll();

    int resume(String id);

    int cancel(String id);

    void release();
}
