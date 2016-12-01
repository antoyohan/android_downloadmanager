package com.media.downloadmanager.interfaces;

public interface IDownloadState {
    int IN_QUEUE = 1;
    int IN_PROGRESS = 2;
    int PAUSED = 3;
    int FAILED = 4;
    int COMPLETE = 5;
}
