package com.buluyizhi.apwifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.buluyizhi.apwifi.listener.WifiMessageListener;

public class MainActivity extends AppCompatActivity implements WifiMessageListener {

    private TextView clientTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WifiConfigManager.getInstance().init(getApplicationContext());
        clientTv = findViewById(R.id.client_tv);
        final EditText etSsid = (EditText) findViewById(R.id.et_ssid);
        final EditText etPwd = (EditText) findViewById(R.id.et_pwd);
        WifiConfigManager.getInstance().setOnMessageListener(this);
        findViewById(R.id.scan_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(etSsid.getText().toString().trim()) || TextUtils.isEmpty(etPwd.getText().toString().trim())){
                    Toast.makeText(MainActivity.this, "请输入wifi账号密码", Toast.LENGTH_SHORT).show();
                    return;
                }
                WifiConfigManager.getInstance().startSearchWifiWithConnect("AndroidAP", "123456789",etSsid.getText().toString().trim(), etPwd.getText().toString().trim());
                clientTv.setText("开始连接");
                //                WifiConfigManager.getInstance().startSocket();
            }
        });

        findViewById(R.id.toserver_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ServerActivity.class));
            }
        });
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
        clientTv.append("\n 接收到消息 = " + reciveMsg);
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
