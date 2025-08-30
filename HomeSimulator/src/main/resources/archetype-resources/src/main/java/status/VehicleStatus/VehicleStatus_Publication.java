package org.example.status.VehicleStatus;

import com.zrdds.domain.*;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.*;
import com.zrdds.topic.Topic;

import java.util.Scanner;

public class VehicleStatus_Publication {
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
            System.out.println("create dp failed");
            return;
        }

        // 2. 注册类型
        VehicleStatusTypeSupport ts = (VehicleStatusTypeSupport) VehicleStatusTypeSupport.get_instance();
        rtn = ts.register_type(dp, "VehicleStatus");
        if (rtn != ReturnCode_t.RETCODE_OK) {
            System.out.println("register type failed");
            return;
        }

        // 3. 创建 Topic
        Topic tp = dp.create_topic(
                "VehicleStatusTopic",
                ts.get_type_name(),
                DomainParticipant.TOPIC_QOS_DEFAULT,
                null,
                StatusKind.STATUS_MASK_NONE
        );
        if (tp == null) {
            System.out.println("create topic failed");
            return;
        }

        // 4. 创建 Publisher
        Publisher pub = dp.create_publisher(DomainParticipant.PUBLISHER_QOS_DEFAULT, null, StatusKind.STATUS_MASK_NONE);
        if (pub == null) {
            System.out.println("create publisher failed");
            return;
        }

        // 5. 设置 DataWriter QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 1;

        // 6. 创建 DataWriter
        DataWriter _dw = pub.create_datawriter(tp, dwQos, null, StatusKind.STATUS_MASK_NONE);
        VehicleStatusDataWriter dw = (VehicleStatusDataWriter) _dw;
        if (dw == null) {
            System.out.println("create datawriter failed");
            return;
        }

        // 初始化车辆状态
        VehicleStatus vehicle = new VehicleStatus();
        vehicle.engineOn = false;
        vehicle.doorsLocked = true;
        vehicle.fuelPercent = 100;
        vehicle.location = "HomeBase";
        vehicle.timeStamp = String.valueOf((System.currentTimeMillis() / 1000L));
        sendVehicle(dw, vehicle);
        // ----------- 线程 1：手动输入控制 -------------
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.println("输入指令控制车辆状态：\n" +
                    "1: 启动发动机\n" +
                    "2: 熄火发动机\n" +
                    "3: 锁车门\n" +
                    "4: 解锁车门\n" +
                    "5: 设置油量\n" +
                    "6: 设置位置\n" +
                    "7: 手动发送当前状态\n" +
                    "0: 退出");

            while (true) {
                System.out.print("请输入指令编号> ");
                String cmd = scanner.nextLine();

                switch (cmd) {
                    case "1":
                        vehicle.engineOn = true;
                        System.out.println("发动机已启动");
                        break;
                    case "2":
                        vehicle.engineOn = false;
                        System.out.println("发动机已熄火");
                        break;
                    case "3":
                        vehicle.doorsLocked = true;
                        System.out.println("车门已上锁");
                        break;
                    case "4":
                        vehicle.doorsLocked = false;
                        System.out.println("车门已解锁");
                        break;
                    case "5":
                        System.out.print("请输入油量(0-100)> ");
                        vehicle.fuelPercent = Float.parseFloat(scanner.nextLine());
                        break;
                    case "6":
                        System.out.print("请输入位置> ");
                        vehicle.location = scanner.nextLine();
                        break;
                    case "7":
                        sendVehicle(dw, vehicle);
                        break;
                    case "0":
                        System.out.println("退出程序");
                        dp.delete_contained_entities();
                        DomainParticipantFactory.get_instance().delete_participant(dp);
                        DomainParticipantFactory.finalize_instance();
                        System.exit(0);
                        break;
                    default:
                        System.out.println("无效指令");
                }
            }
        });

        // ----------- 线程 2：定时上报状态 -------------
        Thread autoReportThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(15000); // 每隔 15 秒上报一次
                    sendVehicle(dw, vehicle);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        inputThread.start();
        autoReportThread.start();
    }

    private static void sendVehicle(VehicleStatusDataWriter dw, VehicleStatus vehicle) {
        vehicle.timeStamp = String.valueOf((int) (System.currentTimeMillis() / 1000L));
        dw.write(vehicle, InstanceHandle_t.HANDLE_NIL_NATIVE);
        System.out.printf("[发送车辆状态] engineOn=%b, doorsLocked=%b, fuelPercent=%.1f, location=%s\n",
                vehicle.engineOn, vehicle.doorsLocked, vehicle.fuelPercent, vehicle.location);
    }

    public static void loadLibrary() {
        if (!hasLoad) {
            System.loadLibrary("ZRDDS_JAVA");
            hasLoad = true;
        }
    }
}

