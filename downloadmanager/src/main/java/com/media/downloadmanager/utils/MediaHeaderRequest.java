package com.media.downloadmanager.utils;


import android.os.AsyncTask;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Class to fetch the http header values
 * */
public class MediaHeaderRequest extends AsyncTask<String, Void, String> {

    private OnDownloadCompleteListener mListener;
    private String TAG = MediaHeaderRequest.class.getName();

    public MediaHeaderRequest(OnDownloadCompleteListener listener) {
        mListener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... params) {
        URL url = null;
        try {
            url = new URL(params[0]);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            String contentLength = conn.getHeaderField("Content-Length");
            conn.disconnect();
            return contentLength;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String contentlength) {
        super.onPostExecute(contentlength);
        if (mListener != null) {
            mListener.onDownloadComplete(contentlength);
        }
    }

    public interface OnDownloadCompleteListener {
        void onDownloadComplete(String result);
    }
}
