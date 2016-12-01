package com.media.downloadmanager.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.media.downloadmanager.DownloadManager;
import com.media.downloadmanager.DownloadService;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private static final String TAG = NetworkChangeReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        DownloadManager.setIsConnectedtoWifi(isConnectedToWifi(context));
        if (isOnline(context)) {
            Log.d(TAG, "is online");
            context.startService(new Intent(context, DownloadService.class));
        } else {
            Log.d(TAG, "is offline");
            DownloadManager.getInstance(context).stopDownloadService();
        }
        Log.d(TAG, "isConnectedTo wifi " + isConnectedToWifi(context));
    }

    private boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        //should check null because in airplane mode it will be null
        return (netInfo != null && netInfo.isConnected());
    }

    public boolean isConnectedToWifi(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            return networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        } else {
            return false;
        }
    }
}
