package com.buluyizhi.apwifi;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

public class JavaCallNative extends Activity{

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    static {
        System.loadLibrary("hello");
    }

    public native String getStringFromC();

 }
