package com.liskovsoft.appupdatechecker2;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import com.liskovsoft.appupdatechecker2.core.AppDownloader;
import com.liskovsoft.appupdatechecker2.core.AppDownloaderListener;
import com.liskovsoft.appupdatechecker2.core.AppVersionChecker;
import com.liskovsoft.appupdatechecker2.core.AppVersionCheckerListener;
import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.Arrays;
import java.util.List;

public class AppUpdateChecker implements AppVersionCheckerListener, AppDownloaderListener {
    private static final String TAG = AppUpdateChecker.class.getSimpleName();
    private static final int FRESH_TIME_MS = 15 * 60 * 1_000; // 15 minutes
    private static final int MIN_APK_SIZE_BYTES = 1_000_000; // 1 MB
    private final java.util.concurrent.ExecutorService verificationWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Context mContext;
    private final AppVersionChecker mVersionChecker;
    private final AppDownloader mDownloader;
    private final AppUpdateCheckerListener mListener;
    private final SettingsManager mSettingsManager;
    private List<String> mChangeLog;
    private String mLatestVersionName;
    private int mLatestVersionNumber;

    public AppUpdateChecker(Context context, AppUpdateCheckerListener listener) {
        this(context, listener, MIN_APK_SIZE_BYTES);
    }

    public AppUpdateChecker(Context context, AppUpdateCheckerListener listener, int minApkSizeBytes) {
        Log.d(TAG, "Starting...");

        FileHelpers.checkCachePermissions(context); // should be an Activity context

        mContext = context.getApplicationContext();
        mListener = listener;
        mVersionChecker = new AppVersionChecker(mContext, this);
        mDownloader = new AppDownloader(mContext, this, minApkSizeBytes);
        mSettingsManager = new SettingsManager(mContext);

        // Cleanup the storage. I don't want to accidentally install old version.
        //FileHelpers.delete(mSettingsManager.getApkPath());
    }

    /**
     * You normally shouldn't need to call this, as {@link #checkForUpdates(String[] versionListUrls)} checks it before doing any updates.
     *
     * @return true if the updater should check for updates
     */
    private boolean isStale() {
        if (mSettingsManager.getMinIntervalMs() < 0) {
            return false;
        }

        return System.currentTimeMillis() - mSettingsManager.getLastCheckedMs() > mSettingsManager.getMinIntervalMs();
    }

    public void checkForUpdates(String updateManifestUrl) {
        checkForUpdates(new String[]{updateManifestUrl});
    }

    /**
     * Checks for updates if updates haven't been checked for recently and if checking is enabled.
     */
    public void checkForUpdates(String[] updateManifestUrls) {
        if (isUpdateCheckEnabled() && isStale()) {
            checkForUpdatesInt(updateManifestUrls);
        } else {
            mListener.onUpdateError(new IllegalStateException(AppUpdateCheckerListener.UPDATE_CHECK_DISABLED));
        }
    }

    public void forceCheckForUpdates(String updateManifestUrl) {
        forceCheckForUpdates(new String[]{updateManifestUrl});
    }

    public void forceCheckForUpdates(String[] updateManifestUrls) {
        checkForUpdatesInt(updateManifestUrls);
    }

    private void checkForUpdatesInt(String[] updateManifestUrls) {
        if (!checkPostponed()) {
            Uri[] uris = new Uri[updateManifestUrls.length];

            for (int i = 0; i < updateManifestUrls.length; i++) {
                uris[i] = Uri.parse(updateManifestUrls[i]);
            }

            checkForUpdatesInt(uris);
        }
    }

    private void checkForUpdatesInt(Uri[] updateManifestUrls) {
        if (!checkPostponed()) {
            mVersionChecker.checkForUpdates(updateManifestUrls);
        }
    }

    private boolean checkPostponed() {
        return false;
    }

    @Override
    public void onChangelogReceived(boolean isLatestVersion, String latestVersionName, int latestVersionNumber, List<String> changelog, Uri[] downloadUris) {
        if (!isLatestVersion) {
            if (downloadUris != null) {
                mChangeLog = changelog;
                mLatestVersionName = latestVersionName;
                mLatestVersionNumber = latestVersionNumber;

                mDownloader.download(downloadUris);
            }
        } else {
            // No update is needed.
            mSettingsManager.setLastCheckedMs(System.currentTimeMillis());

            // Remove old apks.
            FileHelpers.delete(mSettingsManager.getApkPath());

            mListener.onUpdateError(new IllegalStateException(AppUpdateCheckerListener.LATEST_VERSION));
        }
    }

    @Override
    public void onApkDownloaded(String path) {
        verificationWorker.execute(()->{
            boolean valid=checkApk(path);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                if(!valid){FileHelpers.delete(path);mListener.onUpdateError(new SecurityException("Update verification failed"));return;}
                mSettingsManager.setApkPath(path);
                mSettingsManager.setLatestVersionName(mLatestVersionName);
                mSettingsManager.setLatestVersionNumber(mLatestVersionNumber);
                mListener.onUpdateFound(mLatestVersionName,mChangeLog,path);
            });
        });
    }

    @Override
    public void onCheckError(Exception e) {
        mListener.onUpdateError(e);
    }

    @Override
    public void onDownloadError(Exception e) {
        mListener.onUpdateError(e);
    }

    @Override
    public void processDownloadUrls(Uri[] downloadUrls) {
        String preferredHost = getPreferredHost();

        if (preferredHost == null) {
            return;
        }

        Arrays.sort(downloadUrls, ((o1, o2) -> {
            boolean firstMatch = o1 != null && Helpers.equals(preferredHost, o1.getHost());
            boolean secondMatch = o2 != null && Helpers.equals(preferredHost, o2.getHost());

            return firstMatch == secondMatch ? 0 : firstMatch ? -1 : 1;
        }));
    }

    public void installUpdate() {
        final String path=mSettingsManager.getApkPath();
        verificationWorker.execute(()->{
            boolean valid=checkApk(path);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                if(valid)Helpers.installPackage(mContext,path);
                else mListener.onUpdateError(new SecurityException("Update verification failed"));
            });
        });
    }

    public boolean isUpdateCheckEnabled() {
        return mSettingsManager.getMinIntervalMs() > 0;
    }

    public void setUpdateCheckEnabled(boolean enable) {
        mSettingsManager.setMinIntervalMs(enable ? SettingsManager.CHECK_INTERVAL_DEFAULT_MS : -1);
    }

    public String getPreferredHost() {
        return mSettingsManager.getPreferredHost();
    }

    public void setPreferredHost(String host) {
        mSettingsManager.setPreferredHost(host);
    }

    /**
     * Checks the package is not broken
     */
    @SuppressWarnings("deprecation")
    private boolean checkApk(String path) {
        try {
            if(path==null || mVersionChecker.getExpectedSha256()==null)return false;
            android.content.pm.PackageManager manager=mContext.getPackageManager();
            PackageInfo installed=manager.getPackageInfo(mContext.getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES);
            PackageInfo update=manager.getPackageArchiveInfo(path,android.content.pm.PackageManager.GET_SIGNATURES);
            if(update==null || !installed.packageName.equals(update.packageName) || update.versionCode<=installed.versionCode ||
                update.versionCode!=mVersionChecker.getExpectedVersion() || update.signatures==null || installed.signatures==null ||
                update.signatures.length==0 || update.signatures.length!=installed.signatures.length)return false;
            java.util.Set<String> known=new java.util.HashSet<>(),candidate=new java.util.HashSet<>();
            for(android.content.pm.Signature signature:installed.signatures)known.add(signature.toCharsString());
            for(android.content.pm.Signature signature:update.signatures)candidate.add(signature.toCharsString());
            return com.liskovsoft.appupdatechecker2.core.UpdatePolicy.accepts(installed.packageName,installed.versionCode,known,
                update.packageName,update.versionCode,mVersionChecker.getExpectedVersion(),candidate) &&
                com.liskovsoft.appupdatechecker2.core.UpdatePolicy.sha256(new java.io.File(path)).equalsIgnoreCase(mVersionChecker.getExpectedSha256());
        }catch(Exception failure){return false;}
    }
}
