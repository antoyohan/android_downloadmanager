package com.media.anto.downloadvideotest;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.media.downloadmanager.DownloadManager;
import com.media.downloadmanager.DownloadService;
import com.media.downloadmanager.application.ApplicationClass;
import com.media.downloadmanager.interfaces.DownloadStatusListener;
import com.media.downloadmanager.model.DownloadRequest;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private TextView mTxtId;
    private TextView mTxtBytes;
    private TextView mTxtProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        ApplicationClass.setContext(getApplicationContext());
    }

    private void initView() {
        findViewById(R.id.btn_start).setOnClickListener(this);
        findViewById(R.id.btn_stop).setOnClickListener(this);
        findViewById(R.id.btn_resume).setOnClickListener(this);

        mTxtId = (TextView) findViewById(R.id.tv_id);
        mTxtBytes = (TextView) findViewById(R.id.tv_bytes);
        mTxtProgress = (TextView) findViewById(R.id.tv_progress);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                startDownload();
                break;

            case R.id.btn_stop:
                stopDownload();
                stopService(new Intent(this, DownloadService.class));
                break;

            case R.id.btn_resume:
                resumeDownload();
                break;
        }
    }

    private void resumeDownload() {
        DownloadManager.getInstance(this).resume("6");
    }

    private void stopDownload() {
        DownloadManager.getInstance(this).pause("6");
    }

    private void startDownload() {
        String destinationPath = getDestinationPath();
        DownloadRequest req = new DownloadRequest("6", "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp6",
                false, destinationPath, getCurrentTimeStamp());
        Log.d(TAG, "path " + destinationPath);
        DownloadManager.getInstance(this).add(req);
        DownloadManager.getInstance(this).setDownloadStatusListener(new DownloadStatusListener() {
            @Override
            public void onDownloadComplete(String id) {

            }

            @Override
            public void onDownloadFailed(String id, int errorCode, String errorMessage) {

            }

            @Override
            public void onProgress(final String id, final long downloadedBytes, final int progress) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTxtProgress != null)
                            mTxtProgress.setText(String.valueOf(progress));
                        if (mTxtBytes != null)
                            mTxtBytes.setText(String.valueOf((int) downloadedBytes));
                        if (mTxtId != null)
                            mTxtId.setText(id);
                    }
                });

            }
        });
    }

    private long getCurrentTimeStamp() {
        return Long.parseLong(new SimpleDateFormat("yyMMddHHmmssSSS").format(new Date()));
    }

    public String getDestinationPath() {
        return Environment.getExternalStorageDirectory() + "/Downloaded/" + "test6.mp4";
    }
}
