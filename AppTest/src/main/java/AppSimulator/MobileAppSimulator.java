package AppSimulator;

import alert.dds.AppCommandPublisher;
import alert.dds.AppStatusSubscriber;
import CarSimulator.Command;
import CarSimulator.VehicleStatus;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class MobileAppSimulator {
    private static final int DOMAIN_ID = 80;
    private static boolean hasLoad = false;
    
    private AppCommandPublisher commandPublisher;
    private AppStatusSubscriber statusSubscriber;
    private AtomicBoolean running;

    public MobileAppSimulator() {
        loadLibrary();
        running = new AtomicBoolean(true);
    }

    public void initDDS() {
        try {
            commandPublisher = new AppCommandPublisher(DOMAIN_ID);
            commandPublisher.init();
            
            statusSubscriber = new AppStatusSubscriber(DOMAIN_ID, this);
            statusSubscriber.init();
            
            System.out.println("手机App DDS初始化成功");
        } catch (Exception e) {
            System.err.println("手机App DDS初始化异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start() {
        initDDS();
        startUserInterface();
    }

    private void startUserInterface() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 手机App车辆控制界面 ===");
        System.out.println("可发送的命令：");
        System.out.println("1. 启动发动机 (engine 1)");
        System.out.println("2. 熄火发动机 (engine 0)");
        System.out.println("3. 锁定车门 (lock 1)");
        System.out.println("4. 解锁车门 (lock 0)");
        System.out.println("5. 设置油量 (fuel [0-100])");
        System.out.println("6. 设置位置 (location [地址])");
        System.out.println("7. 查看当前状态");
        System.out.println("0. 退出App");
        System.out.println("==============================");

        while (running.get()) {
            System.out.print("\n请输入命令编号> ");
            String cmd = scanner.nextLine();

            switch (cmd) {
                case "1":
                    sendCommand("engine", 1.0f);
                    break;
                case "2":
                    sendCommand("engine", 0.0f);
                    break;
                case "3":
                    sendCommand("lock", 1.0f);
                    break;
                case "4":
                    sendCommand("lock", 0.0f);
                    break;
                case "5":
                    System.out.print("请输入油量值(0-100): ");
                    try {
                        float fuel = Float.parseFloat(scanner.nextLine());
                        if (fuel >= 0 && fuel <= 100) {
                            sendCommand("fuel", fuel);
                        } else {
                            System.out.println("油量值必须在0-100之间");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("输入无效，请输入数字");
                    }
                    break;
                case "6":
                    System.out.print("请输入位置: ");
                    String location = scanner.nextLine();
                    sendLocationCommand(location);
                    break;
                case "7":
                    requestCurrentStatus();
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

    private void sendCommand(String action, float value) {
        Command command = new Command();
        command.deviceId = "vehicle_001";
        command.deviceType = "vehicle";
        command.action = action;
        command.value = value;
        command.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
        
        if (commandPublisher != null) {
            commandPublisher.publishCommand(command);
            System.out.println("命令已发送: " + action + "=" + value);
        } else {
            System.err.println("命令发布器未初始化");
        }
    }

    private void sendLocationCommand(String location) {
        Command command = new Command();
        command.deviceId = "vehicle_001";
        command.deviceType = "vehicle";
        command.action = "location";
        command.value = 0.0f;
        command.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
        
        // 这里简化处理，将位置信息编码到timeStamp字段
        command.timeStamp = "LOCATION_" + location;
        
        if (commandPublisher != null) {
            commandPublisher.publishCommand(command);
            System.out.println("位置命令已发送: " + location);
        }
    }

    private void requestCurrentStatus() {
        Command command = new Command();
        command.deviceId = "vehicle_001";
        command.deviceType = "vehicle";
        command.action = "status_request";
        command.value = 0.0f;
        command.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
        
        if (commandPublisher != null) {
            commandPublisher.publishCommand(command);
            System.out.println("状态请求已发送");
        }
    }

    public void onVehicleStatusReceived(VehicleStatus status) {
        System.out.println("\n=== 接收到车辆状态 ===");
        System.out.println("发动机状态: " + (status.engineOn ? "启动" : "熄火"));
        System.out.println("车门状态: " + (status.doorsLocked ? "锁定" : "解锁"));
        System.out.println("油量: " + status.fuelPercent + "%");
        System.out.println("位置: " + status.location);
        System.out.println("时间戳: " + status.timeStamp);
        System.out.println("========================");
    }

    public void shutdown() {
        running.set(false);
        
        if (commandPublisher != null) {
            commandPublisher.close();
        }
        if (statusSubscriber != null) {
            statusSubscriber.close();
        }
        
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