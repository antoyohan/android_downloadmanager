package com.media.downloadmanager.database;

public interface IDbCallback {

    public void onSuccess();

    public void onError(Throwable error);
}
