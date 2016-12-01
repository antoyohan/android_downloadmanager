package com.media.downloadmanager.database;

import android.util.Log;

import com.media.downloadmanager.interfaces.IDownloadState;
import com.media.downloadmanager.model.DownloadRequest;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;

public class DbManager {

    private static DbManager sManagerInstance;
    private IDbCallback mTransactionCallback;
    private static final String TAG = DbManager.class.getName();

    private DbManager() {
    }

    public static DbManager getInstance() {
        if (sManagerInstance == null) {
            sManagerInstance = new DbManager();
        }
        return sManagerInstance;
    }

    /**
     * Set listener for database transactions
     *
     * @param listener Listener instance for all callbacks
     */
    public void setTransactionCallbackListener(IDbCallback listener) {
        this.mTransactionCallback = listener;
    }

    /**
     * Insert the specified object into database
     *
     * @param object              RealmObject to be inserted
     * @param transactionListener callback listener for the transaction
     */
    public void insert(DownloadRequest object, final IDbCallback transactionListener) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(object);
        realm.commitTransaction();
        realm.close();
    }

    /**
     * Returns the entire list of entries.
     */
    public RealmList<DownloadRequest> getAllDownloads() {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<DownloadRequest> results = realm.where(DownloadRequest.class).
                findAllSorted(DownloadRequest.TIME_STAMP, Sort.DESCENDING);
        RealmList<DownloadRequest> list = new RealmList<>();
        for (DownloadRequest d : results) {
            list.add(realm.copyFromRealm(d));
        }
        realm.close();
        return list;
    }


    /**
     * Returns the item
     *
     * @param value the id of the item to be returned
     */
    public DownloadRequest getDownloadRequest(String value) {
        Realm realm = Realm.getDefaultInstance();
        DownloadRequest results = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.ARTICLE_ID, value).findFirst();
        DownloadRequest downloadRequest = null;
        if (results != null) {
            downloadRequest = realm.copyFromRealm(results);
        }
        realm.close();
        return downloadRequest;
    }

    /**
     * Returns if any unfinished download is there
     */
    public boolean ifPendingItemPresent() {
        Realm realm = Realm.getDefaultInstance();
        RealmObject result = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.DOWNLOAD_STATE, IDownloadState.IN_QUEUE).or().
                equalTo(DownloadRequest.DOWNLOAD_STATE, IDownloadState.IN_PROGRESS).findFirst();
        boolean value = result != null && result.isValid();
        realm.close();
        Log.d(TAG, "Pending items " + value);
        return value;
    }

    /**
     * Update the specified objects into database.
     * Database should contain the entry with the primary key specified in param object.
     * Otherwise insert the entry.
     *
     * @param object RealmObject to be updated
     */
    public void update(DownloadRequest object) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(object);
        realm.commitTransaction();
        realm.close();
    }

    public void delete(String articleId) {
        Realm realm = Realm.getDefaultInstance();
        DownloadRequest downloadRequest = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.ARTICLE_ID, articleId).findFirst();
        if (downloadRequest != null) {
            realm.beginTransaction();
            downloadRequest.deleteFromRealm();
            realm.commitTransaction();
        }
        realm.close();
    }

    public List<DownloadRequest> getPendingItems() {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<DownloadRequest> results = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.DOWNLOAD_STATE, IDownloadState.IN_QUEUE).or().
                equalTo(DownloadRequest.DOWNLOAD_STATE, IDownloadState.IN_PROGRESS).
                findAllSorted(DownloadRequest.TIME_STAMP, Sort.DESCENDING);
        List<DownloadRequest> resultList = realm.copyFromRealm(results);
        realm.close();
        return resultList;
    }

    public boolean isDownloaded(String id) {
        Realm realm = Realm.getDefaultInstance();
        DownloadRequest result = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.ARTICLE_ID, id).findFirst();
        boolean value = result != null && result.isValid() && result.getDownloadState() == IDownloadState.COMPLETE;
        realm.close();
        return value;

    }

    public boolean isAlreadyAddedToDb(String articleId) {
        Realm realm = Realm.getDefaultInstance();
        DownloadRequest result = realm.where(DownloadRequest.class).
                equalTo(DownloadRequest.ARTICLE_ID, articleId).findFirst();
        boolean value = result != null && result.getDownloadState() != IDownloadState.FAILED;
        realm.close();
        return value;
    }
}
