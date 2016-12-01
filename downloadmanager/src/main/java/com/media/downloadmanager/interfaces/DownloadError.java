package com.media.downloadmanager.interfaces;

public interface DownloadError {
    int THREAD_INTERUPTED = 101;
    int HTTP_ERROR = 102;
    int MALFORMED_URL = 103;
    int FILE_ERROR = 104;
    int ERROR_TOO_MANY_REDIRECTS = 105;
    int UNKNOWN_SIZE = 106;
}
