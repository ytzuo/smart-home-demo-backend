package com.example.alert;

import com.example.alert.dds.AlertPublisher;
import com.example.alert.dds.AlertSubscriber;
import com.example.alert.dds.DdsParticipant;
import com.example.alert.AlertDevice.AirConditioner;
import com.example.alert.AlertDevice.Fridge;
import com.example.alert.AlertDevice.Light;
import com.example.alert.idl.AlertTypeSupport;
import com.zrdds.topic.Topic;

import java.util.Scanner;

public class AlertStart {

    //设置一个变量，用于控制是否发布警告
    public static boolean isPublishAlert=false;
    public static AlertPublisher alertPublisher=new AlertPublisher(); // 静态引用，供设备类使用

    public static String targetDevice = ""; // 当前选中的设备

    public static void main(String[] args) {
        //创建dds
        DdsParticipant dds = DdsParticipant.getInstance();
        //注册类型
        dds.registerType(AlertTypeSupport.get_instance());
        //创建topic
        Topic tp = dds.createTopic("AlertTopic", AlertTypeSupport.get_instance());
        //创建subscriber
        AlertSubscriber alertSubscriber = new AlertSubscriber();
        if (!alertSubscriber.start(dds.getSubscriber(), tp)) {
            System.out.println("Subscriber 启动失败！");
            return;
        }

        if (!alertPublisher.start(dds.getPublisher(), tp)) {
            System.out.println("Publisher 启动失败！");
            return;
        }
        //创建设备
        Light light=new Light("1");
        Fridge fridge=new Fridge("2");
        AirConditioner airConditioner=new AirConditioner("3");
        //启动设备线程
        Thread lightThread=new Thread(light);
        Thread fridgeThread=new Thread(fridge);
        Thread airConditionerThread=new Thread(airConditioner);
        lightThread.start();
        fridgeThread.start();
        airConditionerThread.start();

        new Thread(()->{
            // 添加用户输入控制逻辑
            Scanner scanner = new Scanner(System.in);
            System.out.println("智能家居模拟器启动成功！");
            System.out.println("请输入命令控制设备报警 (格式: true/false 设备名称，例如: true 灯)");

            while (true) {
                String input = scanner.nextLine();
                String[] parts = input.split(" ");

                if (parts.length == 2) {
                    try {
                        isPublishAlert = Boolean.parseBoolean(parts[0]);
                        targetDevice = parts[1];
                        System.out.println("已设置 " + targetDevice + " 的报警状态为: " + isPublishAlert);
                    } catch (Exception e) {
                        System.out.println("输入格式错误，请重试！");
                    }
                } else {
                    System.out.println("输入格式错误，请使用: true/false 设备名称");
                }
            }
        }).start();
    }
}
