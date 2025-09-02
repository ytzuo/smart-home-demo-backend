package AppSimulator;

import AppSimulator.DDS.CommandPublisher;
import AppSimulator.DDS.DdsParticipant;
import AppSimulator.DDS.StatusSubscriber;
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
                case "1" -> handleCarCommands(scanner);
                case "2" -> handleHomeCommands(scanner);
                case "0" -> {
                    System.out.println("正在退出手机App...");
                    shutdown();
                }
                default -> System.out.println("无效命令，请重新输入");
            }
        }
    }

    private void handleCarCommands(Scanner scanner) {
        System.out.println("--- 车辆控制 ---");
        System.out.println(" a. 启动发动机 (engine_on)");
        System.out.println(" b. 关闭发动机 (engine_off)");
        System.out.println(" c. 锁车 (lock)");
        System.out.println(" d. 解锁 (unlock)");
        System.out.println(" r. (refuel)");
        System.out.println(" ac-on.  (ac_on)");
        System.out.println(" ac-off. (ac_off)");
        System.out.print("请输入车辆命令> ");
        String action = scanner.nextLine();
        //sendCommand("car", action);
        switch (action.toLowerCase()) {
            case "a":
                sendCommand("car", "engine_on");
                break;
            case "b":
                sendCommand("car", "engine_off");
                break;
            case "c":
                sendCommand("car", "lock");
                break;
            case "d":
                sendCommand("car", "unlock");
                break;
            case "r":
                sendCommand("car", "refuel");
                break;
            case "ac-on":
                sendCommand("car", "ac_on");
                break;
            case "ac-off":
                sendCommand("car", "ac_off");
                break;
            case "e":
                System.out.println("正在刷新车辆状态...");
                break;  // 状态会通过DDS自动更新
            default:
                System.out.println("无效命令");
        }
    }

    private void handleHomeCommands(Scanner scanner) {
        System.out.println("--- 家居控制 ---");
        // 修改灯光控制为子菜单入口
        System.out.println(" a. 灯光控制 (进入子菜单)");
        System.out.println(" b. 空调控制 (进入子菜单)");
        System.out.print("请输入家居命令> ");
        String input = scanner.nextLine().trim();

        switch (input.toLowerCase()) {
            case "a":
                handleLightCommands(scanner); // 新增灯光子菜单处理
                break;
            case "b":
                handleAirConditionerCommands(scanner);
                break;
            default:
                System.out.println("无效命令，请重新输入");
        }
    }

    // 新增：灯光控制子菜单处理方法
    private void handleLightCommands(Scanner scanner) {
        System.out.println("\n--- 灯光控制子菜单 ---");
        System.out.println(" 1. 开关控制");
        System.out.println(" 2. 亮度调节 (0-100)");
        System.out.println(" 3. 色温设置");
        System.out.println(" 4. 场景模式");
        System.out.print("请选择操作(1-4)> ");
        String choice = scanner.nextLine().trim();

        System.out.print("请输入要控制的灯具ID (如light1): ");
        String lightId = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                System.out.print("请选择开关状态(on/off): ");
                String state = scanner.nextLine().trim();
                sendCommand("light", "switch_" + lightId + "_" + state);
                break;
            case "2":
                System.out.print("请输入亮度值(0-100): ");
                String brightness = scanner.nextLine().trim();
                sendCommand("light", "brightness_" + lightId + "_" + brightness);
                break;
            case "3":
                System.out.println("可选色温: 暖白/冷白/中性/RGB");
                System.out.print("请输入色温值: ");
                String temp = scanner.nextLine().trim();
                sendCommand("light", "temp_" + lightId + "_" + temp);
                break;
            case "4":
                System.out.println("可选模式: 日常/阅读/睡眠/影院");
                System.out.print("请输入场景模式: ");
                String mode = scanner.nextLine().trim();
                sendCommand("light", "mode_" + lightId + "_" + mode);
                break;
            default:
                System.out.println("无效选项");
        }
    }
    // 新增：空调子菜单处理方法
    private void handleAirConditionerCommands(Scanner scanner) {
        System.out.println("\n--- 空调控制子菜单 ---");
        System.out.println(" 1. 开关控制");
        System.out.println(" 2. 制冷模式 (设置温度)");
        System.out.println(" 3. 扫风模式切换");
        System.out.println(" 4. 除湿模式切换");
        System.out.println(" 5. 温度调节");
        System.out.print("请选择操作(1-5)> ");
        String choice = scanner.nextLine().trim();

        System.out.print("请输入要控制的空调ID (如ac1): ");
        String acId = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                System.out.print("请选择开关状态(on/off): ");
                String state = scanner.nextLine().trim();
                sendCommand("ac", "switch_" + acId + "_" + state);
                break;
            case "2":
                sendCommand("ac", "cool_" + acId);
                break;
            case "3":
                sendCommand("ac", "swing_" + acId);
                break;
            case "4":
                sendCommand("ac", "dehumidify_" + acId);
                break;
            case "5":
                System.out.print("请输入温度 (16-30)> ");
                String temp = scanner.nextLine().trim();
                sendCommand("ac", "temp_" + acId + "_" + temp);
                break;
            default:
                System.out.println("无效选项");
        }
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