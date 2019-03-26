//
// Created by Administrator on 2019/3/23.
//

 #include <stdio.h>
 #include <stdlib.h>
 #include <jni.h>
 //#include <com_buluyizhi_apwifi_JavaCallNative.h>
 //方法名必须为本地方法的全类名点改为下划线，穿入的两个参数必须这样写，
 //第一个参数为Java虚拟机的内存地址的二级指针，用于本地方法与java虚拟机在内存中交互
 //第二个参数为一个java对象，即是哪个对象调用了这个 c方法
 jstring Java_com_buluyizhi_apwifi_JavaCallNative_getStringFromC(JNIEnv* env,
                                                                jobject obj){
     //定义一个C语言字符串
     char* cstr = "hello form c";
     //返回值是java字符串，所以要将C语言的字符串转换成java的字符串
     //在jni.h 中定义了字符串转换函数的函数指针
     //jstring   (*NewStringUTF)(JNIEnv*, const char*);
     //第一种方法：很少用
     jstring jstr1 = (*(*env)).NewStringUTF(env, cstr);
     //第二种方法，推荐
     jstring jstr2 = (*env) -> NewStringUTF(env, cstr);
     return jstr2;
 }

