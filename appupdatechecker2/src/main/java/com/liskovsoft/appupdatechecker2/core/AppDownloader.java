package com.liskovsoft.appupdatechecker2.core;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.URLUtil;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManager;
import com.liskovsoft.appupdatechecker2.other.downloadmanager.DownloadManager.MyRequest;
import com.liskovsoft.sharedutils.helpers.FileHelpers;

import java.io.File;

/**
 * Usage:
 * <pre>
 *   downloader = new AppDownloader(ctx, listener);
 *   downloader.download(new String[]{"http://serverurl/appfile.apk"});
 * </pre>
 */
public class AppDownloader {
    private static final String TAG = AppDownloader.class.getSimpleName();
    private static final String CURRENT_APK = "update.apk";
    private final Context mContext;
    private boolean mInProgress;
    private final AppDownloaderListener mListener;
    private final int mMinApkSizeBytes;
    private AppDownloadTask mDownloadTask;

    public AppDownloader(Context context, AppDownloaderListener listener, int minApkSizeBytes) {
        mContext = context;
        mListener = listener;
        mMinApkSizeBytes = minApkSizeBytes;
    }

    /**
     * Uses first available url in the list.
     */
    public void download(Uri[] downloadUris) {
        if (!mInProgress) {
            if (mDownloadTask == null) {
                mDownloadTask = new AppDownloadTask();
                mDownloadTask.execute(downloadUris);
            } else {
                Log.e(TAG, "DownloadTask not null. Strange...");
            }
        } else {
            Log.e(TAG, "Another downloading in progress. Canceling...");
        }
    }

    private class AppDownloadTask extends AsyncTask<Uri[],Void,String> {
        @Override
        protected String doInBackground(Uri[]... args) {
            mInProgress = true;

            Uri[] uris = args[0];

            String path = null;
            for (Uri uri : uris) {
                if (URLUtil.isValidUrl(uri.toString())) {
                    path = downloadPackage(uri.toString());
                    if (path != null)
                        break;
                }
            }

            return path;
        }

        @Override
        protected void onPostExecute(String path) {
            if (path != null) {
                mListener.onApkDownloaded(path);
            } else {
                String msg = "Error while download. Install path is null";
                Log.e(TAG, msg);
                mListener.onDownloadError(new IllegalStateException(msg));
            }

            mInProgress = false;
            mDownloadTask = null;
        }

        private String downloadPackage(String uri) {
            File cacheDir = mContext.getCacheDir();
            if (cacheDir == null) {
                return null;
            }
            File outputFile = new File(cacheDir, CURRENT_APK);
            String path = null;
            try {
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(outputFile)){
                    SecureUpdateTransport.fetch(uri,output,250L*1024*1024);
                }
                if(outputFile.length()>mMinApkSizeBytes)path=outputFile.getAbsolutePath();
            }catch(Exception failure){outputFile.delete();}
            return path;
        }
    }

    public boolean isInProgress() {
        return mInProgress;
    }
}
