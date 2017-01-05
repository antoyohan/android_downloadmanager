package com.media.downloadmanager;

import android.net.Uri;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.media.downloadmanager.interfaces.DownloadError;
import com.media.downloadmanager.interfaces.DownloadStatusListener;
import com.media.downloadmanager.interfaces.IDownloadState;
import com.media.downloadmanager.model.DownloadRequest;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

import io.realm.Realm;
import io.realm.RealmObject;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

public class DownloadDispatcher extends Thread {

    /**
     * The queue of download requests to service.
     */
    private final BlockingQueue<DownloadRequest> mQueue;
    private final ArrayList<DownloadRequest> mDownloadList;

    /**
     * Used to tell the dispatcher to die.
     */
    private volatile boolean mQuit = false;

    /**
     * Current Download request that this dispatcher is working
     */
    private DownloadRequest mRequest;

    /**
     * The buffer size used to stream the data
     */
    public final int BUFFER_SIZE = 4096;

    /**
     * How many times redirects happened during a download request.
     */
    private int mRedirectionCount = 0;

    /**
     * Listener for updating the download status to ui
     */
    private DownloadStatusListener mDownloadStatusListener;

    /**
     * The maximum number of redirects.
     */
    public final int MAX_REDIRECTS = 0;

    private final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    private final int HTTP_TEMP_REDIRECT = 307;

    private long mContentLength;
    private long mCurrentBytes;
    boolean shouldAllowRedirects = true;

    /**
     * Tag used for debugging/logging
     */
    public static final String TAG = "DownloadDispatcher";

    private String mCurrentDownloadId = "";
    private String mPauseId = "";
    private DownloadRequest mDbInstance;
    private Realm mRealmInstance;
    private boolean mShouldRetry = false;
    private String mQueueId = "";
    private String mCancelId = "";

    /**
     * Constructor take the dependency (DownloadRequest queue) that all the Dispatcher needs
     */
    public DownloadDispatcher(BlockingQueue<DownloadRequest> queue,
                              ArrayList<DownloadRequest> queueList) {
        mQueue = queue;
        mDownloadList = queueList;
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        while (true) {
            try {
                Log.d(TAG, "Waiting for object");
                clearVariables();
                mRequest = mQueue.take();
                mCurrentDownloadId = mRequest.getArticleId();
                mDownloadList.remove(mRequest);

                Log.d(TAG, "Download initiated for " + mCurrentDownloadId);
                getDbObject();
                updateDownloadInProgress();
                executeDownload(mRequest.getUrl());
                mRealmInstance.close();
            } catch (InterruptedException e) {
                Log.d(TAG, "Thread Interrupted");
                if (mCurrentDownloadId != null && !mCurrentDownloadId.isEmpty()) {
                    updateDownloadQueued();
                }
            }
        }
    }

    private void clearVariables() {
        mCurrentDownloadId = "";
        mQueueId = "";
        mQuit = false;
        mContentLength = 0l;
        mPauseId = "";
        mCancelId = "";
        mCurrentBytes = 0l;
        mRedirectionCount = 0;
        mShouldRetry = false;
        shouldAllowRedirects = true;
    }

    public void quit() {
        mQuit = true;
        interrupt();
    }

    private boolean shoudResumeDownload() {
        return isDestinationFilePresent() && mDbInstance.getDownloadedBytes() > 0;
    }

    private boolean isDestinationFilePresent() {
        File destinationFile = new File(mRequest.getDestinationPath());
        Log.d(TAG, "is File present " + destinationFile.exists() + mRequest.getDestinationPath());
        return destinationFile.exists();
    }


    private void executeDownload(String downloadUrl) {
        URL url;
        if (shoudResumeDownload()) {
            mCurrentBytes = mRequest.getDownloadedBytes();
            mContentLength = mRequest.getTotalBytes();
            resumeDownload(downloadUrl);
            return;
        }
        cleanupDestination();
        try {
            url = new URL(downloadUrl);
        } catch (MalformedURLException e) {
            updateDownloadFailed(DownloadError.MALFORMED_URL, "MalformedURLException: URI passed is malformed.");
            return;
        }

        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            /*conn.setConnectTimeout(mRequest.getRetryPolicy().getCurrentTimeout());
            conn.setReadTimeout(mRequest.getRetryPolicy().getCurrentTimeout());*/

            HashMap<String, String> customHeaders = new HashMap<>();
            if (customHeaders != null) {
                for (String headerName : customHeaders.keySet()) {
                    conn.addRequestProperty(headerName, customHeaders.get(headerName));
                }
            }

            // Status Connecting is set here before
            // urlConnection is trying to connect to destination.
            //updateDownloadState(DownloadManager.STATUS_CONNECTING);

            final int responseCode = conn.getResponseCode();

            Log.v(TAG, "Response code obtained for downloaded Id "
                    + mRequest.getArticleId()
                    + " : httpResponse Code "
                    + responseCode);

            switch (responseCode) {
                case HTTP_PARTIAL:
                case HTTP_OK:
                    shouldAllowRedirects = false;
                    if (readResponseHeaders(conn) == 1) {
                        mRequest.setTotalBytes(mContentLength);
                        transferData(conn);
                    } else {
                        updateDownloadFailed(DownloadError.UNKNOWN_SIZE, "Transfer-Encoding not found as well as can't know size of download, giving up");
                    }
                    return;
                case HTTP_MOVED_PERM:
                case HTTP_MOVED_TEMP:
                case HTTP_SEE_OTHER:
                case HTTP_TEMP_REDIRECT:
                    // Take redirect url and call executeDownload recursively until
                    // MAX_REDIRECT is reached.
                    attemptRetryOnHTTPException();
                    break;
                case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
                    updateDownloadFailed(HTTP_REQUESTED_RANGE_NOT_SATISFIABLE, conn.getResponseMessage());
                    break;
                case HTTP_UNAVAILABLE:
                    updateDownloadFailed(HTTP_UNAVAILABLE, conn.getResponseMessage());
                    break;
                case HTTP_INTERNAL_ERROR:
                    updateDownloadFailed(HTTP_INTERNAL_ERROR, conn.getResponseMessage());
                    break;
                default:
                    updateDownloadFailed(DownloadError.HTTP_ERROR, "Unhandled HTTP response:" + responseCode + " message:" + conn.getResponseMessage());
                    break;
            }
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            // Retry.
            attemptRetryOnHTTPException();
        } catch (ConnectTimeoutException e) {
            e.printStackTrace();
            attemptRetryOnHTTPException();
        } catch (IOException e) {
            e.printStackTrace();
            attemptRetryOnHTTPException();
        } finally {
            if (mShouldRetry) {
                executeDownload(downloadUrl);
            } else {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    private void resumeDownload(String downloadUrl) {
        Log.e(TAG, "resume Download..");
        URL url;
        try {
            url = new URL(downloadUrl);
        } catch (MalformedURLException e) {
             updateDownloadFailed(DownloadError.MALFORMED_URL,"MalformedURLException: URI passed is malformed.");
            return;
        }

        HttpURLConnection conn = null;

        try {
            if (url.getProtocol().toLowerCase().equals("https")) {
                //trustAllHosts();
            }
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            // conn.setConnectTimeout(mRequest.getRetryPolicy().getCurrentTimeout());
            // conn.setReadTimeout(mRequest.getRetryPolicy().getCurrentTimeout());

            HashMap<String, String> customHeaders = new HashMap<String, String>();
            ;
            if (customHeaders != null && !customHeaders.keySet().isEmpty()) {
                for (String headerName : customHeaders.keySet()) {
                    if (headerName.equals("Range")) {
                        String rangeEnd = customHeaders.get(headerName);
                        if (!TextUtils.isEmpty(rangeEnd)) {
                            int index = rangeEnd.indexOf("-");
                            if (index > 0) {
                                String end = "";
                                try {
                                    end = rangeEnd.substring(index + 1);

                                } catch (IndexOutOfBoundsException e) {
                                    //ignore
                                }
                                if (!TextUtils.isEmpty(end)) {
                                    conn.addRequestProperty(headerName, "bytes=" + mRequest.getDownloadedBytes() + "-" + end);
                                } else {
                                    conn.addRequestProperty(headerName, "bytes=" + mRequest.getDownloadedBytes() + "-");
                                }
                            } else {
                                conn.addRequestProperty(headerName, "bytes=" + mRequest.getDownloadedBytes() + "-");
                            }
                        }
                    } else {
                        conn.addRequestProperty(headerName, customHeaders.get(headerName));
                    }
                }
            } else {
                conn.addRequestProperty("Range", "bytes=" + mRequest.getDownloadedBytes() + "-");
            }

            // Status Connecting is set here before
            // urlConnection is trying to connect to destination.
            //updateDownloadState(DownloadManager.STATUS_CONNECTING);

            final int responseCode = conn.getResponseCode();

            Log.v(TAG, "Response code obtained for downloaded Id "
                    + mRequest.getArticleId()
                    + " : httpResponse Code "
                    + responseCode);

            switch (responseCode) {
                case HTTP_PARTIAL:
                case HTTP_OK:
                    shouldAllowRedirects = false;
                    if (isResponseContentPresent(conn) == 1) {
                        // readResponseHeaders(conn);
                        transferData(conn);
                    } else {
                         updateDownloadFailed(DownloadError.UNKNOWN_SIZE, "Transfer-Encoding not found as well as can't know size of download, giving up");
                    }
                    return;
                case HTTP_MOVED_PERM:
                case HTTP_MOVED_TEMP:
                case HTTP_SEE_OTHER:
                case HTTP_TEMP_REDIRECT:
                    // Take redirect url and call executeDownload recursively until
                    // MAX_REDIRECT is reached.
                    attemptRetryOnHTTPException();
                    break;
                case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
                    updateDownloadFailed(HTTP_REQUESTED_RANGE_NOT_SATISFIABLE, conn.getResponseMessage());
                    break;
                case HTTP_UNAVAILABLE:
                    updateDownloadFailed(HTTP_UNAVAILABLE, conn.getResponseMessage());
                    break;
                case HTTP_INTERNAL_ERROR:
                    updateDownloadFailed(HTTP_INTERNAL_ERROR, conn.getResponseMessage());
                    break;
                default:
                    updateDownloadFailed(DownloadError.HTTP_ERROR, "Unhandled HTTP response:" + responseCode + " message:" + conn.getResponseMessage());
                    break;
            }
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            // Retry.
            attemptRetryOnHTTPException();
        } catch (ConnectTimeoutException e) {
            e.printStackTrace();
            attemptRetryOnHTTPException();
        } catch (IOException e) {
            e.printStackTrace();
            attemptRetryOnHTTPException();
        } finally {
            if (mShouldRetry) {
                executeDownload(downloadUrl);
            } else {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    private int isResponseContentPresent(HttpURLConnection conn) {
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        long contentLength = -1;

        if (transferEncoding == null) {
            contentLength = getHeaderFieldLong(conn, "Content-Length", -1);
        } else {
            Log.v(TAG, "Ignoring Content-Length since Transfer-Encoding is also defined for Downloaded Id " + mRequest.getArticleId());
        }

        if (contentLength != -1) {
            return 1;
        } else if (transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked")) {
            return -1;
        } else {
            return 1;
        }
    }

    private void transferData(HttpURLConnection conn) {
        InputStream in = null;
        OutputStream out = null;
        FileDescriptor outFd = null;
        try {
            try {
                in = conn.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Uri desturi = Uri.parse(mRequest.getDestinationPath());
            File destinationFile = new File(desturi.getPath());

            // Create destination file if it doesn't exists
            boolean errorCreatingDestinationFile = createFile(destinationFile);

            // If Destination file couldn't be created. Abort the data transfer.
            if (!errorCreatingDestinationFile) {
                try {
                    out = new FileOutputStream(destinationFile, true);
                    outFd = ((FileOutputStream) out).getFD();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (in == null) {
                    updateDownloadFailed(DownloadError.FILE_ERROR, "Error in creating input stream");
                } else if (out == null) {
                    updateDownloadFailed(DownloadError.FILE_ERROR, "Error in writing download contents to the destination file");
                } else {
                    // Start streaming data
                    transferData(in, out);
                }
            }

        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (out != null) {
                    out.flush();
                }
                if (outFd != null) {
                    outFd.sync();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void transferData(InputStream in, OutputStream out) {
        final byte data[] = new byte[BUFFER_SIZE];
        Log.d(TAG, "Content Length: " + mContentLength + " for Download Id " + mRequest.getArticleId());
        for (; ; ) {
            if (mPauseId.equals(mCurrentDownloadId)) {
                //Stop the download and make the item in paused state
                Log.d(TAG, "Content PAUSED ------");
                updateDownloadPaused();
                return;
            }
            if (mQueueId.equals(mCurrentDownloadId)) {
                //Stop the download and make the item in queued state
                Log.d(TAG, "Content QUEUED ------");
                updateDownloadQueued();
                return;
            }
            if (mCancelId.equals(mCurrentDownloadId)) {
                //Stop the download after this, content will be deleted so don't update db.
                Log.d(TAG, "Content Cancelled ------");
                updateDownloadCancelled();
                return;
            }
            int bytesRead = readFromResponse(data, in);

            if (mContentLength != -1 && mContentLength > 0) {
                int progress = (int) ((mCurrentBytes * 100) / mContentLength);
                updateDownloadProgress(progress, mCurrentBytes);
            }

            if (bytesRead == -1) { // success, end of stream already reached
                updateDownloadComplete();
                return;
            } else if (bytesRead == Integer.MIN_VALUE) {
                return;
            }

            if (writeDataToDestination(data, bytesRead, out)) {
                Log.d(TAG, "data written " + mCurrentBytes);
                mCurrentBytes += bytesRead;
                updateDownloadDatabaseStatus(mCurrentBytes);
            } else {
                finish();
                return;
            }
        }
    }

    private void finish() {
        if (mDownloadList.isEmpty()) {
            //this.quit();
        }
    }

    private int readFromResponse(byte[] data, InputStream entityStream) {
        try {
            return entityStream.read(data);
        } catch (IOException ex) {
            if ("unexpected end of stream".equals(ex.getMessage())) {
                return -1;
            }
            // return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private boolean writeDataToDestination(byte[] data, int bytesRead, OutputStream out) {
        boolean successInWritingToDestination = true;
        try {
            out.write(data, 0, bytesRead);
        } catch (IOException ex) {
            successInWritingToDestination = false;
        } catch (Exception e) {
            successInWritingToDestination = false;
        }
        return successInWritingToDestination;
    }

    private int readResponseHeaders(HttpURLConnection conn) {
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        mContentLength = -1;

        if (transferEncoding == null) {
            mContentLength = getHeaderFieldLong(conn, "Content-Length", -1);
        } else {
            Log.v(TAG, "Ignoring Content-Length since Transfer-Encoding is also defined for Downloaded Id " + mRequest.getArticleId());
        }

        if (mContentLength != -1) {
            return 1;
        } else if (transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked")) {
            return -1;
        } else {
            return 1;
        }
    }

    private long getHeaderFieldLong(URLConnection conn, String field, long defaultValue) {
        try {
            return Long.parseLong(conn.getHeaderField(field));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void attemptRetryOnHTTPException() {
        //Code to retry the Download
        if (mRedirectionCount < MAX_REDIRECTS && shouldAllowRedirects) {
            mShouldRetry = true;
            mRedirectionCount++;
        } else {
            mShouldRetry = false;
            updateDownloadFailed(DownloadError.ERROR_TOO_MANY_REDIRECTS, "too many redirects");
        }
    }

    /**
     * Called just before the thread finishes, regardless of status, to take any necessary action on
     * the downloaded file.
     */
    private void cleanupDestination() {
        Log.d(TAG, "cleanupDestination() deleting " + mRequest.getDestinationPath());
        File destinationFile = new File(mRequest.getDestinationPath());
        if (destinationFile.exists()) {
            destinationFile.delete();
        }
    }

    private void updateDownloadComplete() {
        Log.d(TAG, "updateDownlaodComplete");
        if (mDownloadStatusListener != null) {
            mDownloadStatusListener.onDownloadComplete(mCurrentDownloadId);
        }
        updateDownloadState(IDownloadState.COMPLETE);
    }

    private void updateDownloadFailed(int errorCode, String errorMsg) {
        Log.d(TAG, "updateDownlaodFailed " + errorCode + "  " + errorMsg);
        if (mDownloadStatusListener != null) {
            mDownloadStatusListener.onDownloadFailed(mCurrentDownloadId, errorCode, errorMsg);
        }
        //updateDownloadState(IDownloadState.FAILED);
        updateDownloadState(IDownloadState.IN_QUEUE);//Moved into queue state to retry if reloaded
    }

    private void updateDownloadInProgress() {
        updateDownloadState(IDownloadState.IN_PROGRESS);
    }

    private void updateDownloadState(int state) {
        mRequest.setDownloadState(state);
        update(mRequest);
    }

    private void updateDownloadQueued() {
        updateDownloadState(IDownloadState.IN_QUEUE);
    }

    private void updateDownloadPaused() {
        updateDownloadState(IDownloadState.PAUSED);
    }

    private void updateDownloadCancelled() {
        cleanupDestination();
        deleteFromDb();
    }

    private void updateDownloadProgress(int progress, long downloadedBytes) {
        Log.d(TAG, " id " + mCurrentDownloadId + " progress " + progress + " bytes " + downloadedBytes);
        if (mDownloadStatusListener != null) {
            mDownloadStatusListener.onProgress(mCurrentDownloadId, downloadedBytes, progress);
        }
    }

    private void updateDownloadDatabaseStatus(long downloadedBytes) {
        mRequest.setDownloadedBytes(downloadedBytes);
        mRequest.setDownloadState(IDownloadState.IN_PROGRESS);
        update(mRequest);
    }

    private void getDbObject() {
        if (mRealmInstance == null) {
            mRealmInstance = Realm.getDefaultInstance();
        }
        DownloadRequest downloadRequest = mRealmInstance.where(DownloadRequest.class).
                equalTo(DownloadRequest.ARTICLE_ID, mCurrentDownloadId).findFirst();
        if (downloadRequest != null) {
            mDbInstance = mRealmInstance.copyFromRealm(downloadRequest);
        }
    }

    private void update(final RealmObject obj) {
        mRealmInstance.beginTransaction();
        mRealmInstance.copyToRealmOrUpdate(obj);
        mRealmInstance.commitTransaction();
    }

    String getCurrentDownloadId() {
        return mCurrentDownloadId;
    }

    void setDownloadStatusListener(DownloadStatusListener listener) {
        mDownloadStatusListener = listener;
    }

    private boolean createFile(File file) {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                Log.d(TAG, "creating file " + file.getPath());
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return true;
            }
        }
        return false;
    }

    void setPauseId(String pauseId) {
        mPauseId = pauseId;
    }

    void setQueueId(String downloadId) {
        mQueueId = downloadId;
    }

    void setCancelId(String cancelId) {
        mCancelId = cancelId;
    }

    private void deleteFromDb() {
        Realm realm = Realm.getDefaultInstance();
        DownloadRequest downloadRequest = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.ARTICLE_ID, mCurrentDownloadId).findFirst();
        if (downloadRequest != null) {
            realm.beginTransaction();
            downloadRequest.deleteFromRealm();
            realm.commitTransaction();
        }
        realm.close();
    }
}
