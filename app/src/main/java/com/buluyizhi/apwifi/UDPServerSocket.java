package com.buluyizhi.apwifi;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


import com.buluyizhi.apwifi.bean.WifiBean;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;


public class UDPServerSocket {

    private static final String LOG_TAG = "UDPServerSocket";

    private static final int BUFFER_LENGTH = 502;
    private Context context;
    private byte[] receiveByte = new byte[BUFFER_LENGTH];


    // 端口号，发送端接收端协商好，端口号唯一
    public int CLIENT_PORT = 8333;

    private boolean LISTEN;

    private DatagramSocket client;
    private DatagramPacket receivePacket;

    private long lastReceiveTime = 0;
    private static final long TIME_OUT = 120 * 1000;
    private static final long HEARTBEAT_MESSAGE_DURATION = 10 * 1000;


    private HeartbeatTimer timer;
    private InetAddress address;
    private Handler handler;

    static final public int APWIFI_RECIVEPWD_MSG = 36;
    static final public int APWIFI_RECIVE_MSG = 33;
    static final public int APWIFI_SEND_MSG = 34;
    static final public int APWIFI_CONNECT_CLOSE = 35;


    public UDPServerSocket(Context context, Handler handler) {
        this.context = context;
        lastReceiveTime = System.currentTimeMillis();
        this.handler = handler;
    }

    public void startUDPSocket() {
        if (client != null) return;
        try {
            // 表明这个 Socket 在设置的端口上监听数据。
//            client = new DatagramSocket(CLIENT_PORT);

            client = new DatagramSocket(null);
            client.setReuseAddress(true);
            client.bind(new InetSocketAddress(CLIENT_PORT));

            if (receivePacket == null) {
                // 创建接受数据的 packet
                receivePacket = new DatagramPacket(receiveByte, BUFFER_LENGTH);
            }

            startSocketThread();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启发送数据的线程
     */
    public void startSocketThread() {
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "clientThread is running...");
                receiveMessage();


            }
        });
        LISTEN = true;
        clientThread.start();

    }

    /**
     * 处理接受到的消息
     */
    private void receiveMessage() {
        try {
            Log.i(LOG_TAG, "Listener started!");
            //  DatagramSocket socket = new DatagramSocket(3333);
            client.setSoTimeout(1500);
            byte[] buffer = new byte[BUFFER_LENGTH];
            DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
            while (LISTEN) {
                try {
                    Log.i(LOG_TAG, "Listening for packets");
                    client.receive(packet);

                    //返回值
                    String data = new String(buffer, 0, packet.getLength());
                    Log.i(LOG_TAG, "Packet received from " + packet.getAddress() + " with contents: " + data);
                    String socketAddress = packet.getSocketAddress() + "";
                    // String s = socketAddress.split(":")[0];
                    String address = packet.getAddress() + "";
                    int port = packet.getPort();
                    Log.i(LOG_TAG, "Packet received from " + socketAddress + " with port: " + port);

                    Log.i(LOG_TAG, "收到数据");
                    Message message = new Message();
                    message.obj = data;
                    message.what = APWIFI_RECIVE_MSG;
                    handler.sendMessage(message);
                    try {
                        Gson gson = new Gson();
                        WifiBean wifiBean = gson.fromJson(data, WifiBean.class);
                        if (wifiBean != null && (wifiBean.msgType == 1)) {
                            Message messagePWD = new Message();
                            messagePWD.obj = wifiBean;
                            messagePWD.what = APWIFI_RECIVEPWD_MSG;
                            handler.sendMessage(messagePWD);
                            Log.i(LOG_TAG, "收到服务器发送数据  关闭广播");
                            WifiBean bean = new WifiBean();
                            bean.msgType = 2;
                            sendUDP(packet.getAddress(), gson.toJson(bean));
                            stopUDPSocket();//主动关闭广播
                        }
                    } catch (Exception e) {
                    }


//                            sendUDP(packet.getAddress(),"客户端，收到数据了，可以停止了");
                    if (port == CLIENT_PORT) {//判断是不是硬件方发过来的udp信息,接口筛选
                        //  do something
                        //保存硬件发送过来的IP,就是address，拼接成"http:/"+ address
//                                Log.i(LOG_TAG, "收到数据");
//                                sendUDP(address.getBytes(),"客户端，收到数据了，可以停止了");
//                                stopUDPSocket();//主动关闭广播
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "IOException in Listener " + e);
                }
            }
            Log.i(LOG_TAG, "Listener ending");

        } catch (SocketException e) {
            Log.e(LOG_TAG, "SocketException in Listener " + e);
        }

        // 每次接收完UDP数据后，重置长度。否则可能会导致下次收到数据包被截断。
        if (receivePacket != null) {
            receivePacket.setLength(BUFFER_LENGTH);
        }


    }

    public void stopUDPSocket() {
        LISTEN = false;
        receivePacket = null;
        handler.sendEmptyMessage(APWIFI_CONNECT_CLOSE);
        if (client != null) {
            client.close();
            client = null;
        }
        if (timer != null) {
            timer.exit();
        }
    }

    /**
     * 启动心跳，timer 间隔十秒
     */
    public void startHeartbeatTimer(final byte[] addr, final String pId) {
        timer = new HeartbeatTimer();
        timer.setOnScheduleListener(new HeartbeatTimer.OnScheduleListener() {
            @Override
            public void onSchedule() {
                Log.d(LOG_TAG, "timer is onSchedule...");
                long duration = System.currentTimeMillis() - lastReceiveTime;
                Log.d(LOG_TAG, "duration:" + duration);
                if (duration > TIME_OUT) {//若超过两分钟都没收到信息，进行重新广播。
                    Log.d(LOG_TAG, "超时");
                    // 刷新时间，重新进入下一个心跳周期
                    lastReceiveTime = System.currentTimeMillis();
                } else if (duration > HEARTBEAT_MESSAGE_DURATION) {//若超过十秒他没收到我的心跳包，则重新发一个。
                    String string = "hello,this is a heartbeat message";
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sendUDP(addr, pId);
                        }
                    }).start();
                }
            }

        });
        timer.startTimer(0, HEARTBEAT_MESSAGE_DURATION);

    }


    private void sendUDP(byte[] addr, String pId) {
        if (LISTEN) {
            try {
                byte[] data = new byte[BUFFER_LENGTH];
                InetAddress bcIP = Inet4Address.getByAddress(addr);
                DatagramSocket udp = new DatagramSocket();
                udp.setBroadcast(true);
                Log.d(LOG_TAG, "InetAddress:" + bcIP);
                DatagramPacket dp = new DatagramPacket(data, data.length, bcIP, CLIENT_PORT);
                // String s = new String("0x00"  + "ACESPillow_neck20180906000000001");
                String s = new String("0x00" + pId);//这里的广播内容随便定义
                Log.i(LOG_TAG, "Packet send from " + dp.getAddress() + " with address: " + dp.getSocketAddress());
                // 设置发送广播内容
                byte[] buff = s.getBytes();
                dp.setData(buff, 0, buff.length);

                //一次性广播，用完即关闭
                udp.send(dp);
                udp.disconnect();
                udp.close();
                Message message = new Message();
                message.obj = s;
                message.what = APWIFI_SEND_MSG;
                handler.sendMessage(message);
                Log.d("UDP", "[Tx]" + data);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendUDP(InetAddress addr, String pId) {
        if (LISTEN) {
            try {
                byte[] data = new byte[BUFFER_LENGTH];
//                InetAddress bcIP = Inet4Address.getByAddress(addr);
                DatagramSocket udp = new DatagramSocket();
                udp.setBroadcast(true);
//                Log.d(LOG_TAG, "InetAddress:" + bcIP);
                DatagramPacket dp = new DatagramPacket(data, data.length, addr, CLIENT_PORT);
                // String s = new String("0x00"  + "ACESPillow_neck20180906000000001");
//                String s = new String("0x00"  + pId);//这里的广播内容随便定义
                String s = new String(pId);//这里的广播内容随便定义
                Log.i(LOG_TAG, "Packet send from " + dp.getAddress() + " with address: " + dp.getSocketAddress());
                // 设置发送广播内容
                byte[] buff = s.getBytes();
                dp.setData(buff, 0, buff.length);

                //一次性广播，用完即关闭
                udp.send(dp);
                udp.disconnect();
                udp.close();
                Message message = new Message();
                message.obj = s;
                message.what = APWIFI_SEND_MSG;
                handler.sendMessage(message);
                Log.d("UDP", "[Tx]" + data);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
