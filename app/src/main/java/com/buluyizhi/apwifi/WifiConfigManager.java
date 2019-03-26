package com.buluyizhi.apwifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.buluyizhi.apwifi.bean.WifiBean;
import com.buluyizhi.apwifi.listener.WifiMessageListener;
import com.google.gson.Gson;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Description:
 * Created by FL
 * Time 2019/3/4.
 * 首先需要请求权限： Manifest.permission.ACCESS_COARSE_LOCATION
 * 第一步：检查wifi是否可用
 * 第二步：搜索目标wifi并连接
 * 第三步：传递账号密码 ，并接受返回数据
 * 第四步：判断状态，关闭搜索状态
 */

public class WifiConfigManager {
    private static final String TAG = "WifiConfigManager";

    //无密码
    static final public int WIFI_CIPHER_NPW = 0;
    //WEP加密
    static final public int WIFI_CIPHER_WEP = 1;
    //WAP加密
    static final public int WIFI_CIPHER_WAP = 2;


    static final public int WIFI_TIMES_TOU = 10;


    static final public int APWIFI_CONNECT_SUCCESS = 30;
    static final public int APWIFI_CONNECT_FAIL = 31;
    static final public int APWIFI_FOUND_SUCCESS = 32;
    static final public int APWIFI_RECIVE_MSG = 33;
    static final public int APWIFI_SEND_MSG = 34;
    static final public int APWIFI_CONNECT_CLOSE = 35;
    static final public int APWIFI_RECIVEPWD_MSG = 36;
    static final public int APWIFI_SCANRESULT_MSG = 37;
    private int count = 1;

    private static WifiConfigManager instance;
    private WifiResultCastReciver resultCastReciver;
    private Context mContext;
    private WifiManager wifiManger;
    private String wifiName;
    private String wifiPWD;
    private WifiBean publicBean;


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case APWIFI_CONNECT_SUCCESS:
                    Log.d("---", "查找结果成功" + "    执行次数= " + count);
                    if (onWifiMessageListener != null)
                        onWifiMessageListener.connectTargetWifi((String) msg.obj);
                    WifiBean bean = new WifiBean();
                    bean.msgType = 1;
                    bean.wifiName = wifiName;
                    bean.wifiPWD = wifiPWD;
                    String msgStr = new Gson().toJson(bean);
                    UDPSocket udpSocket = new UDPSocket(mContext, mHandler);
                    udpSocket.startUDPSocket();
                    udpSocket.startHeartbeatTimer(getWifiBroadcastIP(mContext), msgStr);
                    break;
                case APWIFI_CONNECT_FAIL:
                    Log.d("---", "查找结果失败" + "    执行次数= " + count);
                    if (onWifiMessageListener != null) onWifiMessageListener.connectFail();
                    break;
                case APWIFI_RECIVE_MSG:
                    if (onWifiMessageListener != null)
                        onWifiMessageListener.reciveMsg((String) msg.obj);
                    break;
                case APWIFI_SEND_MSG:
                    if (onWifiMessageListener != null)
                        onWifiMessageListener.sendMsg((String) msg.obj);
                    break;
                case APWIFI_CONNECT_CLOSE:
                    if (onWifiMessageListener != null) onWifiMessageListener.connectSuccess();
                    break;
                case APWIFI_RECIVEPWD_MSG:
                    publicBean = (WifiBean) msg.obj;
                    startSearchPublicWifi();
                    break;
                case APWIFI_SCANRESULT_MSG:
//                    startConnectPublicWifi();
                    break;
                default:
            }
        }
    };

    public static WifiConfigManager getInstance() {
        if (instance == null) {
            synchronized (WifiConfigManager.class) {
                if (instance == null) {
                    instance = new WifiConfigManager();
                }
            }
        }
        return instance;
//        return new WifiConfigManager();
    }

    /**
     * 初始化
     *
     * @param context
     */
    public void init(Context context) {
        this.mContext = context;
        wifiManger = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiName = "";
        wifiPWD = "";

        resultCastReciver = new WifiResultCastReciver();
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mContext.registerReceiver(resultCastReciver, intentFilter);
    }


    /**
     * @param targetName ap的名称
     * @param wifiPwd    传递的参数：wifi密码
     */
    public void startSearchWifiWithConnect(String targetName, String targetPwd, String wifiName, String wifiPwd) {
        count = 1;
        publicBean = null;
        if (TextUtils.isEmpty(targetName)) {
            Toast.makeText(mContext, "目标不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!wifiManger.isWifiEnabled()) {
            Toast.makeText(mContext, "请先打开wifi", Toast.LENGTH_SHORT).show();
            wifiManger.setWifiEnabled(true);
        } else {
            this.wifiName = wifiName;
            this.wifiPWD = wifiPwd;
            ScanResult scanResult = getTargetWifi(targetName);
            WifiInfo wifiInfo = getCurrentConnectWifi();
            Log.d("---", "连接的 = " + wifiInfo.getSSID() + "  " + wifiInfo.getIpAddress());

            if (scanResult != null) {
                if (onWifiMessageListener != null)
                    onWifiMessageListener.findTargetWifi(scanResult.SSID);
                addNetWork(scanResult.SSID, "", WIFI_CIPHER_NPW);
                checkConnectInfo(scanResult.SSID);
            } else {
                Toast.makeText(mContext, "没找到目标", Toast.LENGTH_SHORT).show();
                if (onWifiMessageListener != null) onWifiMessageListener.connectFail();
            }
        }
    }

    /**
     * @param ssid
     */
    private void checkConnectInfo(final String ssid) {

        Log.d("---", "正在查找 次数= " + count);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ++count;
                if (count <= WIFI_TIMES_TOU) {
                    WifiInfo wifiInfo = getCurrentConnectWifi();
                    if (wifiInfo != null) {
                        if (wifiInfo.getSSID().equals("\"" + ssid + "\"")) {
                            Log.d("---", wifiInfo.getBSSID() + "获得ip = " + wifiInfo.getIpAddress() + "  name = " + wifiInfo.getSSID());
                            Message message = new Message();
                            message.obj = ssid;
                            message.what = APWIFI_CONNECT_SUCCESS;
                            mHandler.sendMessage(message);
                        } else {
                            checkConnectInfo(ssid);
                        }
                    } else {
                        checkConnectInfo(ssid);
                    }
                } else {
                    Toast.makeText(mContext, "连接超时" + count, Toast.LENGTH_SHORT).show();
                    mHandler.sendEmptyMessage(APWIFI_CONNECT_FAIL);
                }
            }
        }, 1000);
    }

    /**
     * 连接指定wifi
     * 6.0以上版本，直接查找时候有连接过，连接过的拿出wifiConfiguration用
     * 不要去创建新的wifiConfiguration,否者失败
     */
    public void addNetWork(String SSID, String password, int Type) {
        int netId = -1;
        /*先执行删除wifi操作，1.如果删除的成功说明这个wifi配置是由本APP配置出来的；
                           2.这样可以避免密码错误之后，同名字的wifi配置存在，无法连接；
                           3.wifi直接连接成功过，不删除也能用, netId = getExitsWifiConfig(SSID).networkId;*/
        if (removeWifi(SSID)) {
            //移除成功，就新建一个
            netId = wifiManger.addNetwork(createWifiInfo(SSID, password, Type));
        } else {
            //删除不成功，要么这个wifi配置以前就存在过，要么是还没连接过的
            if (getExitsWifiConfig(SSID) != null) {
                //这个wifi是连接过的，如果这个wifi在连接之后改了密码，那就只能手动去删除了
                netId = getExitsWifiConfig(SSID).networkId;
            } else {
                //没连接过的，新建一个wifi配置
                netId = wifiManger.addNetwork(createWifiInfo(SSID, password, Type));
            }
        }

        //这个方法的第一个参数是需要连接wifi网络的networkId，第二个参数是指连接当前wifi网络是否需要断开其他网络
        //无论是否连接上，都返回true。。。。
        wifiManger.enableNetwork(netId, true);
    }

    /**
     * 移除wifi
     *
     * @param SSID wifi名
     */
    public boolean removeWifi(String SSID) {
        if (getExitsWifiConfig(SSID) != null) {
            return removeWifi(getExitsWifiConfig(SSID).networkId);
        } else {
            return false;
        }
    }

    /**
     * 移除wifi，因为权限，无法移除的时候，需要手动去翻wifi列表删除
     * 注意：！！！只能移除自己应用创建的wifi。
     * 删除掉app，再安装的，都不算自己应用，具体看removeNetwork源码
     *
     * @param netId wifi的id
     */
    public boolean removeWifi(int netId) {
        return wifiManger.removeNetwork(netId);
    }

    /**
     * 断开指定ID的网络
     *
     * @param netId 网络id
     */
    public void disconnectWifi(int netId) {
        wifiManger.disableNetwork(netId);
        wifiManger.disconnect();
    }

    /**
     * 断开指定SSID的网络
     *
     * @param SSID wifi名
     */
    public void disconnectWifi(String SSID) {
        if (getExitsWifiConfig(SSID) != null) {
            disconnectWifi(getExitsWifiConfig(SSID).networkId);
        }
    }


    /**
     * 创建一个wifiConfiguration
     *
     * @param SSID     wifi名称
     * @param password wifi密码
     * @param Type     加密类型
     * @return
     */
    public WifiConfiguration createWifiInfo(String SSID, String password, int Type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + SSID + "\"";

        //如果有相同配置的，就先删除
        WifiConfiguration tempConfig = getExitsWifiConfig(SSID);
        if (tempConfig != null) {
            wifiManger.removeNetwork(tempConfig.networkId);
            wifiManger.saveConfiguration();
        }

        //无密码
        if (Type == WIFI_CIPHER_NPW) {
            //config.wepKeys[0] = "";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            //config.wepTxKeyIndex = 0;
        }
        //WEP加密
        else if (Type == WIFI_CIPHER_WEP) {
            config.hiddenSSID = true;
            config.wepKeys[0] = "\"" + password + "\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        }
        //WPA加密
        else if (Type == WIFI_CIPHER_WAP) //WIFICIPHER_WPA
        {
            config.preSharedKey = "\"" + password + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            //config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }
        return config;
    }


    /**
     * 获取配置过的wifiConfiguration
     */
    public WifiConfiguration getExitsWifiConfig(String SSID) {
        List<WifiConfiguration> wifiConfigurationList = wifiManger.getConfiguredNetworks();
        for (WifiConfiguration wifiConfiguration : wifiConfigurationList) {
            if (wifiConfiguration.SSID.equals("\"" + SSID + "\"")) {
                return wifiConfiguration;
            }
        }
        return null;
    }


    /**
     * 获取当前连接的wifi
     *
     * @return
     */
    private WifiInfo getCurrentConnectWifi() {
        return wifiManger.getConnectionInfo();
    }

    /**
     * 开始连接外网wifi
     */
    public void startSearchPublicWifi() {
        if (wifiManger.isWifiEnabled()) {
            //开始扫描wifi列表
            wifiManger.startScan();
        } else {
            wifiManger.setWifiEnabled(true);
            //开始扫描wifi列表
            wifiManger.startScan();
        }
    }

    /**
     * 开始连接外网wifi
     */
    public void startConnectPublicWifi() {
        ScanResult scanResult = null;
        List<ScanResult> scanResultList = wifiManger.getScanResults();
        if (scanResultList != null && scanResultList.size() > 0) {
            for (ScanResult result : scanResultList) {
                Log.d("---", result.SSID + "  " + result.capabilities);
                if (result.SSID.equals(publicBean.wifiName)) {
                    scanResult = result;
                    break;
                }
            }
        }

        if (scanResult != null) {
            addNetWork(scanResult.SSID, publicBean.wifiPWD, WIFI_CIPHER_NPW);
            checkConnectInfo(scanResult.SSID);
        }

    }

    /**
     * 获取目标连接wifi
     *
     * @param wifiName
     * @return
     */
    private ScanResult getTargetWifi(String wifiName) {
        ScanResult scanResult = null;
        List<ScanResult> scanResultList = wifiManger.getScanResults();
        if (scanResultList != null && scanResultList.size() > 0) {
            for (ScanResult result : scanResultList) {
                Log.d("---", result.SSID + "  " + result.capabilities);
                if (result.SSID.equals(wifiName)) {
                    scanResult = result;
                    break;
                }
            }
        }
        return scanResult;
    }

    //获取局域网广播码
    public byte[] getWifiBroadcastIP(Context context) {
        DhcpInfo dhcpInfo = wifiManger.getDhcpInfo();

        int bcIp = ~dhcpInfo.netmask | dhcpInfo.ipAddress;
        byte[] retVal = new byte[4];
        retVal[0] = (byte) (bcIp & 0xff);
        retVal[1] = (byte) (bcIp >> 8 & 0xff);
        retVal[2] = (byte) (bcIp >> 16 & 0xff);
        retVal[3] = (byte) (bcIp >> 24 & 0xff);

        return retVal;
    }


    public void startServerSocket() {
        UDPServerSocket udpSocket = new UDPServerSocket(mContext, mHandler);
        udpSocket.startUDPSocket();
//        udpSocket.startHeartbeatTimer(getWifiBroadcastIP(mContext),"----告诉设备");
    }

    public void stopServerSocket() {
        UDPServerSocket udpSocket = new UDPServerSocket(mContext, mHandler);
        udpSocket.stopUDPSocket();
    }

    private WifiMessageListener onWifiMessageListener;

    public void setOnMessageListener(WifiMessageListener wifiMessageListener) {
        this.onWifiMessageListener = wifiMessageListener;
    }

    private class WifiResultCastReciver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                boolean isScanned = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true);
                if (isScanned) {
                    mHandler.sendEmptyMessage(APWIFI_SCANRESULT_MSG);
                }
            }
        }
    }

    /**
     * 关闭wifi
     */
    public void closeWifi() {
        if (wifiManger.isWifiEnabled()) {
            wifiManger.setWifiEnabled(false);
        }
    }

    /**
     * 开启wifi
     */
    public void openWifi() {
        if (!wifiManger.isWifiEnabled()) {
            wifiManger.setWifiEnabled(true);
        }
    }

    /**
     * 创建热点
     * Android 6.0需要开启WRITE_SETTINGS
     *
     * @param ssid   热点名称
     * @param pwd 热点密码
     * @param isOpen  是否是开放热点
     */
    public void startWifiAp(String ssid, String pwd, boolean isOpen) {
        Method method = null;
        try {
            method = wifiManger.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            WifiConfiguration netConfig = new WifiConfiguration();

            netConfig.SSID = ssid;
            netConfig.preSharedKey = pwd;
            netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            if (isOpen) {
                netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            }
            netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            method.invoke(wifiManger, netConfig, true);

            Log.i(TAG, "startWifiAp: 热点开启成功");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 销毁
     */
    public void onDestory() {
        mHandler.removeCallbacksAndMessages(null);
        wifiName = "";
        wifiPWD = "";
        mContext.unregisterReceiver(resultCastReciver);
        count = 1;
        wifiManger = null;
    }
}
