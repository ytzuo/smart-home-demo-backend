package AppSimulator;

import AppSimulator.DDS.CommandPublisher;
import AppSimulator.DDS.DdsParticipant;
import AppSimulator.DDS.StatusSubscriber;
import IDL.Command;
import IDL.CommandTypeSupport;
import IDL.HomeStatusTypeSupport;
import IDL.VehicleStatusTypeSupport;
import com.zrdds.topic.Topic;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class MobileAppSimulator {
    private static boolean hasLoad = false;

    private CommandPublisher commandPublisher;
    private StatusSubscriber statusSubscriber;
    private AtomicBoolean running;

    public MobileAppSimulator() {
        loadLibrary();
        running = new AtomicBoolean(true);
    }

    private void initDDS() {
        DdsParticipant participant = DdsParticipant.getInstance();

        // 注册IDL类型
        CommandTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "Command");
        HomeStatusTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "HomeStatus");
        VehicleStatusTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "VehicleStatus");

        // 创建Topic
        Topic commandTopic = participant.createTopic("Command", CommandTypeSupport.get_instance());
        Topic homeStatusTopic = participant.createTopic("HomeStatus", HomeStatusTypeSupport.get_instance());
        Topic vehicleStatusTopic = participant.createTopic("VehicleStatus", VehicleStatusTypeSupport.get_instance());

        // 初始化Publisher和Subscriber
        commandPublisher = new CommandPublisher();
        commandPublisher.start(participant.getPublisher(), commandTopic);

        statusSubscriber = new StatusSubscriber();
        statusSubscriber.start(participant.getSubscriber(), homeStatusTopic, vehicleStatusTopic);

        System.out.println("DDS 初始化完成");
    }

    public void start() {
        initDDS();
        startUserInterface();
    }

    private void startUserInterface() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 手机App控制界面 ===");
        System.out.println("1. 控制车辆 (car)");
        System.out.println("2. 控制家居 (home)");
        System.out.println("0. 退出App");
        System.out.println("=======================");

        while (running.get()) {
            System.out.print("\n请输入命令编号> ");
            String cmd = scanner.nextLine();

            switch (cmd) {
                case "1":
                    handleCarCommands(scanner);
                    break;
                case "2":
                    handleHomeCommands(scanner);
                    break;
                case "0":
                    System.out.println("正在退出手机App...");
                    shutdown();
                    break;
                default:
                    System.out.println("无效命令，请重新输入");
            }
        }
    }

    private void handleCarCommands(Scanner scanner) {
        System.out.println("--- 车辆控制 ---");
        System.out.println(" a. 启动发动机 (engine_on)");
        System.out.println(" b. 关闭发动机 (engine_off)");
        System.out.println(" c. 锁车 (lock)");
        System.out.println(" d. 解锁 (unlock)");
        System.out.print("请输入车辆命令> ");
        String action = scanner.nextLine();
        sendCommand("car", action);
    }

    private void handleHomeCommands(Scanner scanner) {
        System.out.println("--- 家居控制 ---");
        System.out.println(" a. 开灯 (light_on)");
        System.out.println(" b. 关灯 (light_off)");
        System.out.println(" c. 打开空调 (ac_on)");
        System.out.println(" d. 关闭空调 (ac_off)");
        System.out.print("请输入家居命令> ");
        String action = scanner.nextLine();
        sendCommand("home", action);
    }

    private void sendCommand(String target, String action) {
        if (commandPublisher != null) {
            if (target == null || target.isEmpty() || action == null || action.isEmpty()) {
                System.err.println("命令参数无效：target=" + target + ", action=" + action);
                return;
            }
            commandPublisher.publishCommand(target, action);
        } else {
            System.err.println("命令发布器未初始化");
        }
    }

    public void shutdown() {
        running.set(false);
        DdsParticipant.getInstance().close();
        System.out.println("手机App已关闭");
        System.exit(0);
    }

    private static void loadLibrary() {
        if (!hasLoad) {
            try {
                System.loadLibrary("ZRDDS_JAVA");
                hasLoad = true;
            } catch (UnsatisfiedLinkError e) {
                System.err.println("警告: 无法加载ZRDDS_JAVA库，DDS功能将不可用");
                System.err.println("请确保ZRDDS_JAVA库在系统路径中");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("启动手机App模拟器...");
        MobileAppSimulator app = new MobileAppSimulator();
        app.start();
    }
}