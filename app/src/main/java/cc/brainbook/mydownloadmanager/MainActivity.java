package cc.brainbook.mydownloadmanager;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import static cc.brainbook.mydownloadmanager.BuildConfig.DEBUG;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "TAG";

    private static final int INSTALL_PACKAGES_REQUEST_CODE = 1;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 2;
    private static final int ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE = 1;
    private static final int ACTION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 2;

    private static final String APK_URL = "http://ljdy.tv/app/ljdy.apk";
    private static final String APK_DOWNLOAD_FILE_NAME = "demo.apk";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "MainActivity# onCreate()# ");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ///[Android O 8.0安装APK权限]需要未知应用来源的权限“android.permission.REQUEST_INSTALL_PACKAGES”
        ///https://blog.csdn.net/github_2011/article/details/78589514
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Log.d(TAG, "shouldShowRequestPermissionRationale(): ");
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("对话框的标题")
                        .setMessage("向用户解释：为什么我们需要某权限")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ///请求WRITE_EXTERNAL_STORAGE权限
                                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                            }
                        })
                        .show();
            } else {
                // No explanation needed, we can request the permission.
                ///请求WRITE_EXTERNAL_STORAGE权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
            }
        } else
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            ///请求安装未知应用来源的权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.REQUEST_INSTALL_PACKAGES}, INSTALL_PACKAGES_REQUEST_CODE);

            Toast.makeText(this, "请同意未知应用来源的权限！否则无法版本升级", Toast.LENGTH_LONG).show();
        } else {
            ///启动UpgradeService
            startUpgradeService(APK_URL, APK_DOWNLOAD_FILE_NAME);
        }

        ///查看下载项及历史记录
        ///https://blog.csdn.net/qq_29428215/article/details/80570034
        ///http://wptrafficanalyzer.in/blog/opening-downloads-list-using-downloadmanager-in-android-application/
        Button btnViewDownloads = findViewById(R.id.btn_view_downloads);
        btnViewDownloads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                startActivity(i);
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (DEBUG) Log.d(TAG, "MainActivity# onRequestPermissionsResult()# requestCode: " + requestCode);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case INSTALL_PACKAGES_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ///启动UpgradeService
                    startUpgradeService(APK_URL, APK_DOWNLOAD_FILE_NAME);
                } else {
                    ///启动未知应用来源的权限授权页面
                    ///https://github.com/yjfnypeu/UpdatePlugin/issues/51
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE);
                }
                break;
            case WRITE_EXTERNAL_STORAGE_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ///启动UpgradeService
                    startUpgradeService(APK_URL, APK_DOWNLOAD_FILE_NAME);
                } else {
                    Toast.makeText(this,"拒绝了权限",Toast.LENGTH_SHORT).show();
//                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS, Uri.parse("package:" + getPackageName()));////??????
//                    startActivityForResult(intent, ACTION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (DEBUG) Log.d(TAG, "MainActivity# onActivityResult()# requestCode: " + requestCode + ", resultCode: " + resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ACTION_MANAGE_UNKNOWN_APP_SOURCES_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    ///启动UpgradeService
                    startUpgradeService(APK_URL, APK_DOWNLOAD_FILE_NAME);
                }
                break;
            case ACTION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    ///启动UpgradeService
                    startUpgradeService(APK_URL, APK_DOWNLOAD_FILE_NAME);
                }
                break;
        }
    }

    /**
     * 启动UpgradeService
     */
    private void startUpgradeService(String apkUrl, String apkDownloadFileName) {
        if (DEBUG) Log.d(TAG, "MainActivity# startUpgradeService()# ");

        Intent intent = new Intent(this, DownloadManagerUpgradeService.class);
        intent.putExtra("apk_url", apkUrl);
        intent.putExtra("apk_download_file_name", apkDownloadFileName);
        startService(intent);
    }
}
