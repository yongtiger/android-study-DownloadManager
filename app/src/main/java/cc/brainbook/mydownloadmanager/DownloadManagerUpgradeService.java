package cc.brainbook.mydownloadmanager;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import static cc.brainbook.mydownloadmanager.BuildConfig.DEBUG;

public class DownloadManagerUpgradeService extends Service {
    private static final String TAG = "TAG";

    private String mApkUrl;
    private String mApkDownloadFileName;
    private Uri mApkDownloadFileUri;

    /**
     * 下载管理器（DownloadManager）
     */
    private DownloadManager mDownloadManager;

    /**
     * 下载管理器（DownloadManager）的广播接收器（BroadcastReceiver）
     */
    private DownloadManagerReceiver mDownloadManagerReceiver;

    /**
     * 下载管理器（DownloadManager）为当前的下载请求（Request）分配的唯一的ID
     *
     * 可以通过这个ID重新获得该下载任务，进行一些操作或者查询
     */
    private long mDownloadReference = -1;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# onCreate()# ");
        super.onCreate();

        ///通过系统服务获得下载管理器（DownloadManager）的实例
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        ///注册下载管理器（DownloadManager）的广播接收器（BroadcastReceiver）
        ///注意：局部广播接收器（内部类）只能动态注册，无法静态注册！
        ///注意：UpgradeService只有一个广播接收器（BroadcastReceiver），但可以有多个下载任务，所以在广播接收器处理时用下载任务ID即mDownloadReference区分
        mDownloadManagerReceiver = new DownloadManagerReceiver();
        registerReceiver(mDownloadManagerReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        ///注册点击通知栏下载任务的广播接收器
        registerReceiver(mDownloadManagerReceiver, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# onStartCommand()# ");

        mApkUrl = intent.getStringExtra("apk_url");
        mApkDownloadFileName = intent.getStringExtra("apk_download_file_name");
        mApkDownloadFileUri = getDownloadFileUri();


//        /* ------------------------ 避免重复下载 ------------------------ */ACTION_DOWNLOAD_COMPLETE = STATUS_SUCCESSFUL or STATUS_FAILED
//        ///如果当前下载任务ID已经存在，并且状态为STATUS_FAILED或ERROR_UNKNOWN或STATUS_SUCCESSFUL，则删除该下载任务，并且下载任务ID（mDownloadReference）置-1准备重新下载
//        if (mDownloadReference >= 0 && (DownloadManager.STATUS_SUCCESSFUL == getDownloadStatus(mDownloadReference)
//                || DownloadManager.STATUS_FAILED == getDownloadStatus(mDownloadReference)
//                || DownloadManager.ERROR_UNKNOWN == getDownloadStatus(mDownloadReference))
//        ) {
//            ///删除下载任务
//            mDownloadManager.remove(mDownloadReference);
//
//            mDownloadReference = -1;
//        }
//
//        ///如果当前下载任务ID不存在，则开始下载任务
//        if (mDownloadReference == -1) {
        ///初始化下载管理器（DownloadManager）
        DownloadManager.Request request = initRequest(mApkUrl, mApkDownloadFileName);

        ///将下载请求（Request）加入到下载管理器（DownloadManager）的下载队列
        mDownloadReference = mDownloadManager.enqueue(request);
//        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# onDestroy()# ");
        super.onDestroy();

        ///注销下载广播接收器
        if (mDownloadManagerReceiver != null) {
            unregisterReceiver(mDownloadManagerReceiver);
        }
    }

    /**
     * 初始化下载请求
     */
    private DownloadManager.Request initRequest(String apkUrl, String apkDownloadFileName) {
        if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# initRequest()# apkUrl: " + apkUrl + ", apkDownloadFileName: " + apkDownloadFileName);

        ///设置下载地址
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));

        ///设置下载文件存放到external files directory目录（建议！）
        ///注意：下载Apk时最好不要让用户申请SD卡读写权限！影响用户体验。而放在app应用目录虽然不必额外申请SD卡读写权限，但外部应用访问不到、无法安装！所以apk应保存在App的外部files或cache目录！
        ///the application's external files directory (as returned by getExternalFilesDir(String)
        ///如果下载的这个文件是App应用所专用的，应把这个文件放在App外部存储中的一个专有文件夹中，如果App应用卸载了，那么在这个文件夹也会被删除
        ///注意：这个文件夹不提供访问控制，所以其他的应用也可以访问这个文件夹！
//        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, apkDownloadFileName);
//        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Android_Rock.mp3"); ///配合使用request.allowScanningByMediaScanner()
        request.setDestinationUri(mApkDownloadFileUri);

        ///设置文件类型
        ///下载管理Ui中点击某个已下载完成文件及下载完成点击通知栏提示都会根据mimeType去打开文件，所以我们可以利用这个属性。
        // 比如上面设置了mimeType为application/com.trinea.download.file，
        // 我们可以同时设置某个Activity的intent-filter为application/com.trinea.download.file，用于响应点击的打开文件。
        ///https://blog.csdn.net/a907763895/article/details/12753149
        ///设置APK文件类型
        request.setMimeType("application/vnd.android.package-archive");
        ///设置其它文件类型
//        String url = "http://10.0.2.2/android/film/G3.mp4";
//        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
//        String mimeString = mimeTypeMap.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url));
//        //String mimeString = mimeTypeMap.getMimeTypeFromExtension(url)
//        request.setMimeType(mimeString);

        ///设置允许使用的网络类型（可选！By default, all network types are allowed.）
        ///比如允许移动网络和wifi：DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);

//        ///DownloadManager.getMaxBytesOverMobile()返回一个当前手机网络连接下的最大建议字节数，可以来判断下载是否应该限定在WiFi条件下
//        long maxBytesOverMobile = DownloadManager.getMaxBytesOverMobile(getApplicationContext());
//        if (maxBytesOverMobile > 1000000){
//            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
//        } else {
//            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE);
//        }

        ///设置允许漫游状态下下载（可选！缺省为true）
        request.setAllowedOverRoaming(false);

        ///设置是否通知栏显示进度（可选！By default, VISIBILITY_VISIBLE, a notification is shown only when the download is in progress.）
        ///It can take the following values:
        // VISIBILITY_VISIBLE：在下载过程中通知栏中会一直显示Notification，当下载完成时该Notification会被移除。这是默认的参数值
        // VISIBILITY_VISIBLE_NOTIFY_COMPLETED：在下载过程中通知栏会一直显示Notification，在下载完成后该Notification会继续显示，直到用户点击该Notification或者消除该Notification
        // VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION：只有在下载完成后该Notification才会被显示
        // VISIBILITY_HIDDEN：不显示该下载请求的Notification。
        ///[DownloadManager#DownloadManager.Request.VISIBILITY_HIDDEN]
        ///If set to VISIBILITY_HIDDEN, this requires the permission:
        ///     android.permission.DOWNLOAD_WITHOUT_NOTIFICATION
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        ///设置通知栏通知标题、描述
        request.setTitle(mApkDownloadFileName);
        request.setDescription("我的描述");

        ///Android H 3.0 (API level 11)允许MediaScanner扫描（默认不允许）
        ///https://blog.csdn.net/qq_29428215/article/details/80570034
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            request.allowScanningByMediaScanner();
        }

        ///设置是否显示下载界面（可选！缺省为true）
        ///如果我们希望下载的文件可以被系统的Downloads应用扫描到并管理，需要调用Request对象的setVisibleInDownloadsUi方法，传递参数true
        request.setVisibleInDownloadsUi(false); ///下载APK无需显示系统Downloads应用！

        return request;
    }

    /**
     * 下载管理器（DownloadManager）的广播接收器（BroadcastReceiver）
     *
     * 注意：局部广播接收器（内部类）只能动态注册，无法静态注册！
     */
    private class DownloadManagerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# onReceive()# ");

            ///判断是否下载完成的广播
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# DownloadManagerReceiver# onReceive()# ACTION_DOWNLOAD_COMPLETE: ");
                ///获取刚刚下载完的文件ID
                long reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

                if (reference == mDownloadReference) {
                    ///安装apk
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        installApk28(mApkDownloadFileUri);
//                        installApk28(mDownloadManager.getUriForDownloadedFile(mDownloadReference));
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        installApk26(mApkDownloadFileUri);
//                        installApk26(mDownloadManager.getUriForDownloadedFile(mDownloadReference));
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        installApk25(mApkDownloadFileUri);
//                        installApk25(mDownloadManager.getUriForDownloadedFile(mDownloadReference));
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {///////？？？
                        installApk24(mApkDownloadFileUri);///////？？？
//                        installApk24(mDownloadManager.getUriForDownloadedFile(mDownloadReference));///////？？？
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        installApk23(mApkDownloadFileUri);
                    } else {
                        installApk0(mApkDownloadFileUri);
                    }

                    ///停止服务并关闭广播
                    DownloadManagerUpgradeService.this.stopSelf();
                }

            } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
                if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# DownloadManagerReceiver# onReceive()# ACTION_NOTIFICATION_CLICKED: ");

                ///https://blog.csdn.net/sir_zeng/article/details/8983430
                long[] references = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
                for (long reference : references)
                    if (reference == mDownloadReference) {
                        if (DownloadManager.STATUS_SUCCESSFUL == getDownloadStatus(reference)) {
                            ///通知栏点击事件：已经完成下载的任务
                            Log.d(TAG, "DownloadManagerUpgradeService# DownloadManagerReceiver# onReceive()# 通知栏点击事件：已经完成下载的任务: ");
                            // todo ...
                        } else {
                            ///通知栏点击事件：没有完成下载的任务
                            Log.d(TAG, "DownloadManagerUpgradeService# DownloadManagerReceiver# onReceive()# 通知栏点击事件：没有完成下载的任务: ");
                            // todo ...
                        }

                    }
            }
        }

        /**
         * Android M 6.0 (API level 23)以下安装APK
         *
         * @param apkUri
         */
        private void installApk0(Uri apkUri) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk0()# apkUri: " + apkUri);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        /**
         * Android M 6.0 (API level 23)安装APK方式一：直接安装下载文件
         *
         * 注意：如果重复下载，DownloadManager不删除已有的而是后缀递增数字！所以有可能文件名不是原来的了
         *
         * @param apkUri
         */
        private void installApk23(Uri apkUri) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk23()# apkUri: " + apkUri);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

//        /**
//         * Android M 6.0安装APK方式二：通过DownloadManager.Query查询到下载文件路径名
//         *
//         * @param apkUri
//         */
//        private void installApk23(Uri apkUri) {
//            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk23()# apkUri: " + apkUri);
//
//            String path = getDownloadFileUri(mDownloadReference);
//            Uri downloadFileUri = Uri.parse(path);
//            Intent intent = new Intent(Intent.ACTION_VIEW);
//            intent.setDataAndType(downloadFileUri, "application/vnd.android.package-archive");
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent);
//        }

        /**
         * Android N 7.0 (API level 24)的安装APK方式一：使用StrictMode.setVmPolicy(localBuilder.build())避开FileProvider
         *
         * @param apkUri
         */
        private void installApk24(Uri apkUri) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk24()# apkUri: " + apkUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ///注意：使用installApk7(mApkDownloadFileUri);时必须加下面两行代码：
                ///https://blog.csdn.net/qq_29428215/article/details/80570034
                StrictMode.VmPolicy.Builder localBuilder = new StrictMode.VmPolicy.Builder();
                StrictMode.setVmPolicy(localBuilder.build());
            }

            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    ///对目标应用临时授权该Uri所代表的文件
            startActivity(intent);
        }

//        /**
//         * Android N 7.0 (API level 24)的安装APK方式二：使用Provider
//         *
//         * @param apkUri
//         */
//        private void installApk24(Uri apkUri) {
//            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk24()# apkUri: " + apkUri);
//
//            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
//            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    ///对目标应用临时授权该Uri所代表的文件
//            startActivity(intent);
//        }

        /**
         * Android N 7.1 (API level 25)的安装APK方式一：使用StrictMode.setVmPolicy(localBuilder.build())避开FileProvider
         *
         * @param apkUri
         */
        private void installApk25(Uri apkUri) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk25()# apkUri: " + apkUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ///注意：使用installApk7(mApkDownloadFileUri);时必须加下面两行代码：
                ///https://blog.csdn.net/qq_29428215/article/details/80570034
                StrictMode.VmPolicy.Builder localBuilder = new StrictMode.VmPolicy.Builder();
                StrictMode.setVmPolicy(localBuilder.build());
            }

            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    ///对目标应用临时授权该Uri所代表的文件
            startActivity(intent);
        }

//        /**
//         * Android N 7.1 (API level 25)的安装APK方式二：使用Provider
//         *
//         * @param apkUri
//         */
//        private void installApk25(Uri apkUri) {
//            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk25()# apkUri: " + apkUri);
//
//            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
//            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    ///对目标应用临时授权该Uri所代表的文件
//            startActivity(intent);
//        }

        /**
         * Android O 8.0 (API level 26)安装APK方式
         *
         * @param apkUri
         */
        private void installApk26(Uri apkUri) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk26()# apkUri: " + apkUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(getApplicationContext(),"Error: 没有未知应用来源的权限",Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ///注意：使用installApk7(mApkDownloadFileUri);时必须加下面两行代码：
                ///https://blog.csdn.net/qq_29428215/article/details/80570034
                StrictMode.VmPolicy.Builder localBuilder = new StrictMode.VmPolicy.Builder();
                StrictMode.setVmPolicy(localBuilder.build());
            }

            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    ///对目标应用临时授权该Uri所代表的文件
            startActivity(intent);
        }

        /**
         * Android P 9.0 (API level 28)安装APK方式
         *
         * 注意：Android 9.0 (API level 28)强制使用https，会阻塞http请求，如果app使用的第三方sdk有http，将全部被阻塞
         * https://www.jianshu.com/p/57047a84e559
         * https://blog.csdn.net/csdn_aiyang/article/details/85780925
         *
         * @param apkUri
         */
        private void installApk28(Uri apkUri) {
            if (DEBUG) Log.d(TAG, "DownloadManagerUpgradeService# installApk28()# apkUri: " + apkUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(getApplicationContext(),"Error: 没有未知应用来源的权限",Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ///注意：使用installApk7(mApkDownloadFileUri);时必须加下面两行代码：
                ///https://blog.csdn.net/qq_29428215/article/details/80570034
                StrictMode.VmPolicy.Builder localBuilder = new StrictMode.VmPolicy.Builder();
                StrictMode.setVmPolicy(localBuilder.build());
            }

            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    ///对目标应用临时授权该Uri所代表的文件
            startActivity(intent);
        }
    }

    /**
     * 获取下载文件Uri
     *
     * 参考：request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, apkSaveFileName);
     *
     * @return
     */
    private Uri getDownloadFileUri() {
        return Uri.parse("file://" + getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + File.separator + mApkDownloadFileName);
    }

    /**
     * 获取下载任务的文件路径
     *
     * 注意：如果下载网址相同，保存在本地的文件不覆盖！而是按照“文件名+下划线+序号.文件后缀”新建文件保存
     *
     * @param downloadReference 下载任务ID
     * @return
     */
    private  String getDownloadFileUri(long downloadReference) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadReference);
        Cursor cursor = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).query(query);
        if(cursor != null) {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
            }
            cursor.close();
        }
        return null;
    }

    /**
     * 获取下载任务的状态
     *
     * @param downloadReference 下载任务ID
     * @return  如果Cursor为null则返回-1，其它返回值如下：
     *          DownloadManager.STATUS_PENDING      1
     *          DownloadManager.STATUS_RUNNING      2
     *          DownloadManager.STATUS_PAUSED       4
     *          DownloadManager.STATUS_SUCCESSFUL   8
     *          DownloadManager.STATUS_FAILED       16
     */
    private int getDownloadStatus(long downloadReference) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadReference);
        Cursor cursor = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).query(query);
        if(cursor != null) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
            cursor.close();
        }
        return -1;
    }

    ///https://www.codeproject.com/Articles/1112730/Android-Download-Manager-Tutorial-How-to-Download
//    private void DownloadStatus(Cursor cursor, long DownloadId){
//
//        //column for download  status
//        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
//        int status = cursor.getInt(columnIndex);
//        //column for reason code if the download failed or paused
//        int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
//        int reason = cursor.getInt(columnReason);
//        //get the download filename
//        int filenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
//        String filename = cursor.getString(filenameIndex);
//
//        String statusText = "";
//        String reasonText = "";
//
//        switch(status){
//            case DownloadManager.STATUS_FAILED:
//                statusText = "STATUS_FAILED";
//                switch(reason){
//                    case DownloadManager.ERROR_CANNOT_RESUME:
//                        reasonText = "ERROR_CANNOT_RESUME";
//                        break;
//                    case DownloadManager.ERROR_DEVICE_NOT_FOUND:
//                        reasonText = "ERROR_DEVICE_NOT_FOUND";
//                        break;
//                    case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
//                        reasonText = "ERROR_FILE_ALREADY_EXISTS";
//                        break;
//                    case DownloadManager.ERROR_FILE_ERROR:
//                        reasonText = "ERROR_FILE_ERROR";
//                        break;
//                    case DownloadManager.ERROR_HTTP_DATA_ERROR:
//                        reasonText = "ERROR_HTTP_DATA_ERROR";
//                        break;
//                    case DownloadManager.ERROR_INSUFFICIENT_SPACE:
//                        reasonText = "ERROR_INSUFFICIENT_SPACE";
//                        break;
//                    case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
//                        reasonText = "ERROR_TOO_MANY_REDIRECTS";
//                        break;
//                    case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
//                        reasonText = "ERROR_UNHANDLED_HTTP_CODE";
//                        break;
//                    case DownloadManager.ERROR_UNKNOWN:
//                        reasonText = "ERROR_UNKNOWN";
//                        break;
//                }
//                break;
//            case DownloadManager.STATUS_PAUSED:
//                statusText = "STATUS_PAUSED";
//                switch(reason){
//                    case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
//                        reasonText = "PAUSED_QUEUED_FOR_WIFI";
//                        break;
//                    case DownloadManager.PAUSED_UNKNOWN:
//                        reasonText = "PAUSED_UNKNOWN";
//                        break;
//                    case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
//                        reasonText = "PAUSED_WAITING_FOR_NETWORK";
//                        break;
//                    case DownloadManager.PAUSED_WAITING_TO_RETRY:
//                        reasonText = "PAUSED_WAITING_TO_RETRY";
//                        break;
//                }
//                break;
//            case DownloadManager.STATUS_PENDING:
//                statusText = "STATUS_PENDING";
//                break;
//            case DownloadManager.STATUS_RUNNING:
//                statusText = "STATUS_RUNNING";
//                break;
//            case DownloadManager.STATUS_SUCCESSFUL:
//                statusText = "STATUS_SUCCESSFUL";
//                reasonText = "Filename:\n" + filename;
//                break;
//        }
//
//        if(DownloadId == Music_DownloadId) {
//
//            Toast toast = Toast.makeText(MainActivity.this,
//                    "Music Download Status:" + "\n" + statusText + "\n" +
//                            reasonText,
//                    Toast.LENGTH_LONG);
//            toast.setGravity(Gravity.TOP, 25, 400);
//            toast.show();
//
//        }
//        else {
//
//            Toast toast = Toast.makeText(MainActivity.this,
//                    "Image Download Status:"+ "\n" + statusText + "\n" +
//                            reasonText,
//                    Toast.LENGTH_LONG);
//            toast.setGravity(Gravity.TOP, 25, 400);
//            toast.show();
//
//            // Make a delay of 3 seconds so that next toast (Music Status) will not merge with this one.
//            final Handler handler = new Handler();
//            handler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                }
//            }, 3000);
//        }
//    }
}
