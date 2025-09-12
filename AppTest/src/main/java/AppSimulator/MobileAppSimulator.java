package AppSimulator;

import AppSimulator.DDS.*;
import IDL.*;
import com.zrdds.topic.Topic;
import IDL.EnergyReportTypeSupport;
import IDL.VehicleHealthReportTypeSupport;
import AppSimulator.DDS.EnergyReportSubscriber;
import AppSimulator.DDS.VehicleHealthReportSubscriber;
import IDL.EnergyReport;
import IDL.VehicleHealthReport;
import AppSimulator.DDS.MediaSubscriber;
import IDL.AlertMediaTypeSupport;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class MobileAppSimulator {
    private static boolean hasLoad = false;
    // 在类的成员变量部分添加
    private MediaSubscriber mediaSubscriber;
    private CommandPublisher commandPublisher;
    private AlertSubscriber alertSubscriber;
    private AlertSubscriber carAlertSubscriber;
    private final AtomicBoolean running;
    //能耗/车辆健康报告订阅器及数据缓存
    private EnergyReportSubscriber energyReportSubscriber;
    private VehicleHealthReportSubscriber vehicleHealthSubscriber;
    // 缓存最新能耗数据
    private EnergyReport latestEnergyReport;
    // 缓存最新车辆健康数据
    private VehicleHealthReport latestVehicleHealthReport;
    // 能耗趋势图订阅器
    private ReportMediaSubscriber reportMediaSubscriber;
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
        AlertTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "Alert");
        // 注册AlertMedia类型
        AlertMediaTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "AlertMedia");
        // 注册ReportMedia类型（能耗趋势图专用）
        IDL.ReportMediaTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "ReportMedia");
        // 添加Presence类型注册
        PresenceTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "Presence");
        // 注册能耗报告和车辆健康报告类型
        EnergyReportTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "EnergyReport");
        VehicleHealthReportTypeSupport.get_instance().register_type(participant.getDomainParticipant(), "VehicleHealthReport");

        // 创建Topic
        Topic commandTopic = participant.createTopic("Command", CommandTypeSupport.get_instance());
        Topic homeStatusTopic = participant.createTopic("HomeStatus", HomeStatusTypeSupport.get_instance());
        Topic vehicleStatusTopic = participant.createTopic("VehicleStatus", VehicleStatusTypeSupport.get_instance());
        // 添加Presence Topic
        Topic presenceTopic = participant.createTopic("Presence", PresenceTypeSupport.get_instance());
        // 新增：创建AlertMedia Topic
        Topic alertMediaTopic = participant.createTopic(
                "AlertMedia", AlertMediaTypeSupport.get_instance());
        // 创建ReportMedia Topic（能耗趋势图专用）
        Topic reportMediaTopic = participant.createTopic(
                "ReportMedia", IDL.ReportMediaTypeSupport.get_instance());
        // 新增：创建能耗报告和车辆健康报告 Topic
        Topic energyReportTopic = participant.createTopic("EnergyReport", EnergyReportTypeSupport.get_instance());
        Topic vehicleHealthTopic = participant.createTopic("VehicleHealthReport", VehicleHealthReportTypeSupport.get_instance());

        // 初始化Publisher和Subscriber
        commandPublisher = new CommandPublisher();
        commandPublisher.start(participant.getPublisher(), commandTopic);

        StatusSubscriber statusSubscriber = new StatusSubscriber();
        statusSubscriber.start(participant.getSubscriber(), homeStatusTopic, vehicleStatusTopic,presenceTopic);

        // 初始化家居报警订阅器
        Topic alertTopic = participant.createTopic("Alert", AlertTypeSupport.get_instance());
        alertSubscriber = new AlertSubscriber();
        if (alertSubscriber.start(participant.getSubscriber(), alertTopic)) {
            System.out.println("家居报警监听已启动");
        } else {
            System.err.println("家居报警监听初始化失败");
        }
        
        // 初始化车辆报警订阅器
        Topic carAlertTopic = participant.createTopic("CarAlert", AlertTypeSupport.get_instance());
        carAlertSubscriber = new AlertSubscriber();
        if (carAlertSubscriber.start(participant.getSubscriber(), carAlertTopic)) {
            System.out.println("车辆报警监听已启动");
        } else {
            System.err.println("车辆报警监听初始化失败");
        }

        // 初始化MediaSubscriber
        mediaSubscriber = new MediaSubscriber();
        mediaSubscriber.start(
               participant.getSubscriber(),
                alertMediaTopic);
        // 初始化能耗趋势图订阅器（ReportMediaSubscriber）
        reportMediaSubscriber = new ReportMediaSubscriber();
        reportMediaSubscriber.start(participant.getSubscriber(), reportMediaTopic);
        // 设置监听器，接收图片接收通知
        reportMediaSubscriber.setReportMediaListener((deviceId, reportId, imageData) ->
                System.out.printf("\n📊 能耗趋势图已接收: 设备ID=%s, 保存路径=./received_media/energy_trends/%s.jpg\n",
                        deviceId, reportId));
        // 初始化能耗报告订阅器
        energyReportSubscriber = new EnergyReportSubscriber();
        if (energyReportSubscriber.start(participant.getSubscriber(), energyReportTopic)) {
            System.out.println("能耗报告监听已启动");
            // 设置数据更新回调（缓存最新报告）
            energyReportSubscriber.setDataListener(report -> latestEnergyReport = report);
        } else {
            System.err.println("能耗报告监听初始化失败");
        }

        // 新增：初始化车辆健康报告订阅器
        vehicleHealthSubscriber = new VehicleHealthReportSubscriber();
        if (vehicleHealthSubscriber.start(participant.getSubscriber(), vehicleHealthTopic)) {
            System.out.println("车辆健康报告监听已启动");
            vehicleHealthSubscriber.setDataListener(report -> latestVehicleHealthReport = report);
        } else {
            System.err.println("车辆健康报告监听初始化失败");
        }
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
        System.out.println(" e. 查看健康报告");
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
                displayVehicleHealthReport();
                break;  // 状态会通过DDS自动更新
            default:
                System.out.println("无效命令");
        }
    }

    // 新增：车辆健康报告展示方法
    private void displayVehicleHealthReport() {
        if (latestVehicleHealthReport == null) {
            System.out.println("暂无车辆健康数据，请稍后重试");
            return;
        }
        System.out.println("\n" + "=".repeat(50));
        System.out.println("🚗 车辆健康诊断报告");
        System.out.println("时间: " + latestVehicleHealthReport.timeStamp);
        System.out.println("车辆ID: " + latestVehicleHealthReport.vehicleId);
        System.out.println("下次保养: " + latestVehicleHealthReport.nextMaintenance);
        System.out.println("\n部件状态:");
        for (int i = 0; i < latestVehicleHealthReport.componentTypes.length(); i++) {
            System.out.printf("• %s: %s (指标: %.2f)\n",
                    latestVehicleHealthReport.componentTypes.get_at(i),
                    latestVehicleHealthReport.componentStatuses.get_at(i),
                    latestVehicleHealthReport.metrics.get_at(i));
        }
        System.out.println("=".repeat(50) + "\n");
    }

    private void handleHomeCommands(Scanner scanner) {
        System.out.println("--- 家居控制 ---");
        System.out.println(" a. 灯光控制 (进入子菜单)");
        System.out.println(" b. 空调控制 (进入子菜单)");
        // 添加获取所有设备状态的选项
        System.out.println(" c. 获取所有设备状态");
        System.out.println(" d. 查看能耗报告");
        System.out.println(" e. 请求能耗趋势图");
        System.out.print("请输入家居命令> ");
        String input = scanner.nextLine().trim();

        switch (input.toLowerCase()) {
            case "a":
                handleLightCommands(scanner);
                break;
            case "b":
                handleAirConditionerCommands(scanner);
                break;
            // 添加处理获取所有设备状态的逻辑
            case "c":
                System.out.println("正在请求所有家居设备状态...");
                sendAllStatusRequest();
                break;
            case "d":
                displayEnergyReport();
                break;
            case "e":
                System.out.print("请输入目标设备ID (如light1/ac1): ");
                String deviceId = scanner.nextLine().trim();
                System.out.println("正在请求设备 " + deviceId + " 的能耗趋势图...");
                // 发送趋势图请求命令
                sendCommand("home", "get_energy_trend_" + deviceId);
                break;
            default:
                System.out.println("无效命令，请重新输入");
        }
    }

    // 能耗报告展示方法
    private void displayEnergyReport() {
        if (latestEnergyReport == null) {
            System.out.println("暂无能耗数据，请稍后重试");
            return;
        }
        System.out.println("\n" + "=".repeat(50));
        System.out.println("🏠 家居能耗分析报告");
        System.out.println("时间: " + latestEnergyReport.timeStamp);
        System.out.println("设备: " + latestEnergyReport.deviceId + " (" + latestEnergyReport.deviceType + ")");
        System.out.println("当前功率: " + latestEnergyReport.currentPower + "W");
        System.out.println("当日能耗: " + String.format("%.2f", latestEnergyReport.dailyConsumption) + "kWh");
        System.out.println("本周能耗: " + String.format("%.2f", latestEnergyReport.weeklyConsumption) + "kWh");
        System.out.println("=".repeat(50) + "\n");
    }

    // 新增：发送获取所有设备状态的请求
    private void sendAllStatusRequest() {
        // 向HomeSimulator发送请求所有状态的命令
        sendCommand("home", "request_all_status");
        System.out.println("已发送获取所有设备状态的请求，请查看状态更新");
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
        if (alertSubscriber != null) {
            // 监听器模式自动处理，无需手动停止
        }
        DdsParticipant.getInstance().close();
        System.out.println("手机App已关闭");
        System.exit(0);
    }

    /**
     * 显示报警信息
     */
    public void displayAlert(String alertMessage) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📱 收到新的报警信息:");
        System.out.println(alertMessage);
        System.out.println("=".repeat(50));
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