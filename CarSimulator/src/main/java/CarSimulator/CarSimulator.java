package CarSimulator;

import CarSimulator.DDS.CommandSubscriber;
import CarSimulator.DDS.DdsParticipant;
import CarSimulator.DDS.StatusPublisher;
import AppTestIDL.Command;
import AppTestIDL.CommandTypeSupport;
import AppTestIDL.VehicleStatusTypeSupport;
import com.zrdds.topic.Topic;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class CarSimulator {
    private static boolean hasLoad = false;

    private CommandSubscriber commandSubscriber;
    private StatusPublisher statusPublisher;
    private DdsParticipant ddsParticipant;
    private AtomicBoolean running;
    
    // 车辆状态
    private boolean engineOn = false;
    private boolean doorsLocked = true;
    private float fuelPercent = 100.0f;

    public CarSimulator() {
        loadLibrary();
        running = new AtomicBoolean(true);
    }

    private void initDDS() {
        ddsParticipant = DdsParticipant.getInstance();

        // 注册IDL类型
        CommandTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "Command");
        VehicleStatusTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "VehicleStatus");

        // 创建Topic
        Topic commandTopic = ddsParticipant.createTopic("Command", CommandTypeSupport.get_instance());
        Topic vehicleStatusTopic = ddsParticipant.createTopic("VehicleStatus", VehicleStatusTypeSupport.get_instance());

        // 初始化Subscriber和Publisher
        commandSubscriber = new CommandSubscriber();
        commandSubscriber.setCommandHandler(this::handleCommand);
        commandSubscriber.start(ddsParticipant.getSubscriber(), commandTopic);

        statusPublisher = new StatusPublisher();
        statusPublisher.start(ddsParticipant.getPublisher(), vehicleStatusTopic);

        // 初始状态上报
        statusPublisher.publishVehicleStatus(engineOn, doorsLocked, fuelPercent);

        System.out.println("Car DDS 初始化完成");
    }

    public void start() {
        initDDS();
        startUserInterface();
    }

    private void startUserInterface() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 车辆模拟器 ===");
        System.out.println("车辆已启动，等待App指令...");
        System.out.println("本地控制命令:");
        System.out.println("1. 启动发动机");
        System.out.println("2. 关闭发动机");
        System.out.println("3. 锁车");
        System.out.println("4. 解锁");
        System.out.println("5. 加油");
        System.out.println("0. 退出");
        System.out.println("====================");

        while (running.get()) {
            System.out.print("\n请输入本地命令> ");
            String cmd = scanner.nextLine();

            switch (cmd) {
                case "1":
                    startEngine();
                    break;
                case "2":
                    stopEngine();
                    break;
                case "3":
                    lockDoors();
                    break;
                case "4":
                    unlockDoors();
                    break;
                case "5":
                    refuel();
                    break;
                case "0":
                    shutdown();
                    break;
                default:
                    System.out.println("无效命令，请重新输入");
                    showCurrentStatus();
            }
        }
    }

    private void handleCommand(Command command) {
        if (command == null || command.deviceType == null || command.action == null) {
            return;
        }

        // 只处理car相关的命令
        if (!"car".equalsIgnoreCase(command.deviceType)) {
            return;
        }

        System.out.println("处理App命令: " + command.action);
        
        switch (command.action.toLowerCase()) {
            case "engine_on":
                startEngine();
                break;
            case "engine_off":
                stopEngine();
                break;
            case "lock":
                lockDoors();
                break;
            case "unlock":
                unlockDoors();
                break;
            default:
                System.out.println("未知命令: " + command.action);
        }
    }

    private void startEngine() {
        if (fuelPercent <= 0) {
            System.out.println("油量不足，无法启动发动机！");
            return;
        }
        
        if (!engineOn) {
            engineOn = true;
            System.out.println("发动机已启动");
            statusPublisher.updateEngineStatus(true);
            
            // 模拟油耗
            new Thread(() -> {
                while (engineOn && fuelPercent > 0) {
                    try {
                        Thread.sleep(5000); // 每5秒消耗1%油量
                        fuelPercent = Math.max(0, fuelPercent - 1.0f);
                        statusPublisher.updateFuelLevel(fuelPercent);
                        if (fuelPercent <= 10) {
                            System.out.println("警告：油量过低！当前油量: " + fuelPercent + "%");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }).start();
        } else {
            System.out.println("发动机已经在运行");
        }
    }

    private void stopEngine() {
        if (engineOn) {
            engineOn = false;
            System.out.println("发动机已关闭");
            statusPublisher.updateEngineStatus(false);
        } else {
            System.out.println("发动机已经关闭");
        }
    }

    private void lockDoors() {
        if (!doorsLocked) {
            doorsLocked = true;
            System.out.println("车门已上锁");
            statusPublisher.updateDoorStatus(true);
        } else {
            System.out.println("车门已经上锁");
        }
    }

    private void unlockDoors() {
        if (doorsLocked) {
            doorsLocked = false;
            System.out.println("车门已解锁");
            statusPublisher.updateDoorStatus(false);
        } else {
            System.out.println("车门已经解锁");
        }
    }

    private void refuel() {
        fuelPercent = 100.0f;
        System.out.println("加油完成，当前油量: 100%");
        statusPublisher.updateFuelLevel(fuelPercent);
    }

    private void showCurrentStatus() {
        System.out.println("\n当前车辆状态:");
        System.out.println("发动机状态: " + (engineOn ? "运行中" : "已关闭"));
        System.out.println("车门状态: " + (doorsLocked ? "已上锁" : "已解锁"));
        System.out.println("油量: " + fuelPercent + "%");
    }

    public void shutdown() {
        running.set(false);
        
        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }
        
        if (ddsParticipant != null) {
            ddsParticipant.close();
        }
        
        System.out.println("车辆模拟器已关闭");
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
        System.out.println("启动车辆模拟器...");
        CarSimulator car = new CarSimulator();
        car.start();
    }
}