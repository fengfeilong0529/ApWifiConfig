package com.buluyizhi.apwifi.listener;

/**
 * Description:
 * Created by FL
 * Time 2019/3/6.
 */

public interface WifiMessageListener {

    void findTargetWifi(String name);   //找到目标
    void connectTargetWifi(String name);  //连接上目标
    void connectFail();   //连接失败
    void reciveMsg(String reciveMsg);   //接收消息
    void sendMsg(String sendMsg);   //发送消息
    void connectSuccess();  //关闭连接


}
