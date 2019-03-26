package com.buluyizhi.apwifi;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.buluyizhi.apwifi.listener.WifiMessageListener;

public class ServerActivity extends AppCompatActivity implements WifiMessageListener {
    private static final String TAG = "ServerActivity";

    private TextView clientTv;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WifiConfigManager.getInstance().init(getApplicationContext());
        clientTv = findViewById(R.id.client_tv);
        findViewById(R.id.toserver_btn).setVisibility(View.GONE);
        WifiConfigManager.getInstance().setOnMessageListener(this);

        findViewById(R.id.scan_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //检查WriteSetting权限是否开启
                showCheckWriteSettingDialog();
            }
        });
    }

    /**
     * 创建AP并开启UDP服务端
     */
    private void toStartApAndUdpServer() {
        clientTv.setText("开始等待消息");
        //关闭wifi
        WifiConfigManager.getInstance().closeWifi();
        //创建AP，SSID为AndroidAP，密码为123456789
        WifiConfigManager.getInstance().startWifiAp("AndroidAp", "123456789", false);
        //开启UDP Server
        WifiConfigManager.getInstance().startServerSocket();
    }

    /**
     * 关闭AP和UDP，并连接外部wifi
     *
     * @param ssid
     * @param pwd
     */
    public void toStopApAndUdpServer(String ssid, String pwd) {
        //开启wifi
        WifiConfigManager.getInstance().openWifi();
        //关闭UDP Server
        WifiConfigManager.getInstance().stopServerSocket();
        //连接外部wifi
        WifiConfigManager.getInstance().addNetWork(ssid, pwd, WifiConfigManager.WIFI_CIPHER_WEP);
    }

    /**
     * 检测是否已开启修改系统设置权限
     */
    public void showCheckWriteSettingDialog() {
        //Settings.System.canWrite(MainActivity.this)检测是否拥有写入系统权限的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(ServerActivity.this)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("授权提示");
                builder.setMessage("我们的应用需要您授权\"修改系统设置\"的权限,请点击\"去设置\"确认开启");
                builder.setNegativeButton("取消",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                builder.setPositiveButton("去设置",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Android 6.0以上系统需手动开启WRITE_SETTINGS权限
                                Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                                goToSettings.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(goToSettings);
                            }
                        });
                builder.setCancelable(false);
                builder.show();
            } else {
                toStartApAndUdpServer();
            }
        } else {
            toStartApAndUdpServer();
        }
    }

    @Override
    public void findTargetWifi(String name) {
        clientTv.append("\n 发现目标Wifi = " + name + "   并开始连接");
    }

    @Override
    public void connectTargetWifi(String name) {
        clientTv.append("\n 连接上目标Wifi = " + name);
    }

    @Override
    public void connectFail() {
        clientTv.append("\n 连接失败Wifi");
    }

    @Override
    public void reciveMsg(String reciveMsg) {
        Log.i(TAG, "reciveMsg: " + reciveMsg);
        clientTv.append("\n 接收到消息 = " + reciveMsg);
        //解析消息中的ssid和pwd，然后扫描附近热点，若匹配到此wifi则去连接
        toStopApAndUdpServer("BLYZ", "158158158");
    }

    @Override
    public void sendMsg(String sendMsg) {
        clientTv.append("\n 发送消息 = " + sendMsg);
    }

    @Override
    public void connectSuccess() {
        clientTv.append("\n 关闭连接");
    }
}
