package org.example.status.HomeStatus;

import com.zrdds.domain.DomainParticipant;
import com.zrdds.domain.DomainParticipantFactory;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriter;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.util.Scanner;

public class HomeStatus_Publication {
    public static int domain_id = 80;
    private static boolean hasLoad = false;

    public static void main(String[] args) {
        loadLibrary();
        ReturnCode_t rtn;

        // 1. 创建 DomainParticipant
        DomainParticipant dp = DomainParticipantFactory.get_instance()
                .create_participant(domain_id,
                        DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                        null,
                        StatusKind.STATUS_MASK_NONE);
        if (dp == null) {
            System.out.println("创建 DomainParticipant 失败");
            return;
        }

        // 2. 注册类型
        HomeStatusTypeSupport ts = (HomeStatusTypeSupport) HomeStatusTypeSupport.get_instance();
        rtn = ts.register_type(dp, "HomeStatus");
        if (rtn != ReturnCode_t.RETCODE_OK) {
            System.out.println("注册类型失败");
            return;
        }

        // 3. 创建 Topic
        Topic tp = dp.create_topic(
                "HomeStatus",
                ts.get_type_name(),
                DomainParticipant.TOPIC_QOS_DEFAULT,
                null,
                StatusKind.STATUS_MASK_NONE
        );
        if (tp == null) {
            System.out.println("创建 Topic 失败");
            return;
        }

        // 4. 创建 Publisher
        Publisher pub = dp.create_publisher(DomainParticipant.PUBLISHER_QOS_DEFAULT, null, StatusKind.STATUS_MASK_NONE);
        if (pub == null) {
            System.out.println("创建 Publisher 失败");
            return;
        }

        // 5. 设置 DataWriter QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.BEST_EFFORT_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 1;

        // 6. 创建 DataWriter
        DataWriter _dw = pub.create_datawriter(tp, dwQos, null, StatusKind.STATUS_MASK_NONE);
        HomeStatusDataWriter dw = (HomeStatusDataWriter) _dw;
        if (dw == null) {
            System.out.println("创建 DataWriter 失败");
            return;
        }

        Scanner input = new Scanner(System.in);
        HomeStatus home = new HomeStatus();

        // 7. 输入设备数量
        System.out.println("请输入设备数量（灯 空调 摄像头），用空格分隔：");
        int lampCount = input.nextInt();
        int acCount = input.nextInt();
        int camCount = input.nextInt();
        int totalCount = lampCount + acCount + camCount;
        home.deviceIds = new StringSeq();
        home.deviceTypes = new StringSeq();
        home.acTemp = new FloatSeq();
        home.acStatus = new StringSeq();
        home.lightOn = new BooleanSeq();
        home.lightPercent = new FloatSeq();

        home.cameraOn = new BooleanSeq();
        home.deviceIds.ensure_length(0, totalCount);
        home.deviceTypes.ensure_length(0, totalCount);
        home.acTemp.ensure_length(0, totalCount);
        home.acStatus.ensure_length(0, totalCount);
        home.lightOn.ensure_length(0, totalCount);
        home.lightPercent.ensure_length(0, totalCount);
        home.cameraOn.ensure_length(0, totalCount);
        initDevices(home, lampCount, acCount, camCount);

        // 程序启动时立即上报一次
       home.timeStamp = String.valueOf((System.currentTimeMillis() / 1000L));
        dw.write(home, InstanceHandle_t.HANDLE_NIL_NATIVE);
        System.out.println("[初始上报] 已发送设备状态，总设备数=" + home.deviceIds.length());

        // 7. 输入设备数量
        //System.out.println("请输入设备数量（灯 空调 摄像头），用空格分隔：");
        // lampCount = input.nextInt();
         //acCount = input.nextInt();
         //camCount = input.nextInt();
        //totalCount = lampCount + acCount + camCount;
       // input.nextLine(); // 吸收回车
        //home.deviceIds.ensure_length(0, totalCount);
        //home.deviceTypes.ensure_length(0, totalCount);
        //home.acTemp.ensure_length(0, totalCount);
        //home.acStatus.ensure_length(0, totalCount);
       // home.lightOn.ensure_length(0, totalCount);
        //home.lightPercent.ensure_length(0, totalCount);
        //home.cameraOn.ensure_length(0, totalCount);

        // 10. 定时上报线程
        int reportInterval = 30; // 秒
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(reportInterval * 1000);
                } catch (InterruptedException e) {
                    break;
                }
                home.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
                dw.write(home, InstanceHandle_t.HANDLE_NIL_NATIVE);
                System.out.println("[定时上报] 已发送设备状态，总设备数=" + home.deviceIds.length());
            }
        }).start();
        //home.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
       // dw.write(home, InstanceHandle_t.HANDLE_NIL_NATIVE);
        //System.out.println("[初始上报] 已发送设备状态，总设备数=" + home.deviceIds.length());

        // 11. 命令输入
        System.out.println("输入命令: s=立即发送, t <id>=修改设备状态, e=退出");
        while (true) {
            System.out.print("> ");
            String cmdLine = input.nextLine().trim();
            if (cmdLine.equalsIgnoreCase("e")) {
                break;
            } else if (cmdLine.equalsIgnoreCase("s")) {
                home.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
                dw.write(home, InstanceHandle_t.HANDLE_NIL_NATIVE);
                System.out.println("已发送设备状态，总设备数=" + home.deviceIds.length());
            } else if (cmdLine.startsWith("t ")) {
                String dev = cmdLine.split(" ")[1];
                toggleDevice(home, dev, input);
            } else {
                System.out.println("无效命令, 支持: s / t <id> / e");
            }
        }

        // 12. 清理资源
        dp.delete_contained_entities();
        DomainParticipantFactory.get_instance().delete_participant(dp);
        DomainParticipantFactory.finalize_instance();
        input.close();
    }

    /** 初始化设备 */
    private static void initDevices(HomeStatus home, int lampCount, int acCount, int camCount) {
        for (int i = 0; i < lampCount; i++) {
            home.deviceIds.append("L" + i);
            home.deviceTypes.append("Lamp");
            home.acTemp.append(0f);
            home.acStatus.append("");       // 灯不需要 acStatus
            home.lightOn.append(false);
            home.lightPercent.append(0f);
            home.cameraOn.append(false);
        }
        for (int i = 0; i < acCount; i++) {
            home.deviceIds.append("A" + i);
            home.deviceTypes.append("AirConditioner");
            home.acTemp.append(24f);
            home.acStatus.append("1000");   // 默认状态，电源开
            home.lightOn.append(false);
            home.lightPercent.append(0f);
            home.cameraOn.append(false);
        }
        for (int i = 0; i < camCount; i++) {
            home.deviceIds.append("C" + i);
            home.deviceTypes.append("Camera");
            home.acTemp.append(0f);
            home.acStatus.append("");       // 摄像头不需要 acStatus
            home.lightOn.append(false);
            home.lightPercent.append(0f);
            home.cameraOn.append(false);
        }
    }

    /** 修改设备状态 */
    private static void toggleDevice(HomeStatus home, String dev, Scanner input) {
        int idx = -1;
        for (int i = 0; i < home.deviceIds.length(); i++) {
            if (home.deviceIds.get_at(i).equals(dev)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) {
            System.out.println("设备不存在: " + dev);
            return;
        }

        String type = home.deviceTypes.get_at(idx);
        switch (type) {
            case "Lamp":
                System.out.print("请输入灯泡状态(on/off): ");
                String lampState = input.nextLine().trim().toLowerCase();
                boolean lampOn = lampState.equals("on");
                home.lightOn.set_at(idx, lampOn);

                System.out.print("请输入灯泡亮度(0~100): ");
                try {
                    float percent = Float.parseFloat(input.nextLine().trim());
                    if (percent < 0) percent = 0;
                    if (percent > 100) percent = 100;
                    home.lightPercent.set_at(idx, percent);
                } catch (NumberFormatException e) {
                    System.out.println("亮度输入无效，保持不变");
                }
                System.out.println(dev + " 灯泡状态已设置为 " + lampState + "，亮度=" + home.lightPercent.get_at(idx));
                break;

            case "Camera":
                System.out.print("请输入摄像头状态(on/off): ");
                String camState = input.nextLine().trim().toLowerCase();
                boolean camOn = camState.equals("on");
                home.cameraOn.set_at(idx, camOn);
                System.out.println(dev + " 摄像头状态已设置为 " + camState);
                break;

            case "AirConditioner":
                System.out.print("请输入空调温度(16~30): ");
                try {
                    float temp = Float.parseFloat(input.nextLine().trim());
                    if (temp < 16) temp = 16;
                    if (temp > 30) temp = 30;
                    home.acTemp.set_at(idx, temp);
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，保持原温度");
                }

                System.out.print("请输入空调状态(四位数字，例如 1011): ");
                String status = input.nextLine().trim();
                if (status.matches("[01]{4}")) {
                    home.acStatus.set_at(idx, status);
                } else {
                    System.out.println("输入无效，保持原状态");
                }
                System.out.println(dev + " 空调温度=" + home.acTemp.get_at(idx) + "℃，状态=" + home.acStatus.get_at(idx));
                break;
        }
    }

    /** 加载底层 DDS 库 */
    public static void loadLibrary() {
        if (!hasLoad) {
            System.loadLibrary("ZRDDS_JAVA");
            hasLoad = true;
        }
    }
}
