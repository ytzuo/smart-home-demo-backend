import CarSimulator.DDS.CommandSubscriber;
import CarSimulator.DDS.DdsParticipant;
import CarSimulator.DDS.StatusPublisher;
import IDL.Command;
import IDL.CommandTypeSupport;
import IDL.VehicleStatusTypeSupport;
import com.zrdds.topic.Topic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CarSimulator {
    private static boolean hasLoad = false;

    private CommandSubscriber commandSubscriber;
    private StatusPublisher statusPublisher;
    private DdsParticipant ddsParticipant;
    private AtomicBoolean running; // 控制运行状态
    private ScheduledExecutorService statusReporter; // 定时状态上报
    private CarSimulatorAlert alertSystem; // 车辆报警系统

    // 车辆状态
    private boolean engineOn = false;
    private boolean doorsLocked = true;
    private float fuelPercent = 100.0f;
    // 新增状态参数
    private boolean acOn = false;
    private String location = "Garage";
    private String timeStamp = getCurrentTimeStamp();
    private String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
    public CarSimulator() {
        loadLibrary();
        running = new AtomicBoolean(false);
        alertSystem = new CarSimulatorAlert();
    }

    private void loadLibrary() {
        if (!hasLoad) {
            try {
                System.loadLibrary("ZRDDS_JAVA");
                hasLoad = true;
                System.out.println("[CarSimulator] ZRDDS_JAVA库加载成功");
            } catch (UnsatisfiedLinkError e) {
                System.err.println("[CarSimulator] 警告: 无法加载ZRDDS_JAVA库，DDS功能将不可用");
                System.err.println("[CarSimulator] 请确保ZRDDS_JAVA库在系统路径中");
                System.exit(1);
            }
        }
    }

    public void start() {
        System.out.println("[CarSimulator] 启动车辆模拟器...");

        running.set(true);
        initDDS();
        //startStatusReporting();
        startConsoleInteraction();
        keepRunning(); // 阻塞主线程
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

        // 初始化报警系统
        alertSystem.initialize(ddsParticipant, this);
        alertSystem.startMonitoring();

        // 初始状态上报
        reportCurrentStatus();

        System.out.println("[CarSimulator] DDS初始化完成");
    }

    private void handleCommand(Command command) {
        if (command == null || command.deviceType == null || command.action == null) {
            System.err.println("[CarSimulator] 接收到无效命令");
            return;
        }

        String deviceType = command.deviceType;
        String action = command.action;

        System.out.printf("[CarSimulator] 处理命令: deviceType=%s, action=%s%n", deviceType, action);

        try {
            // 仅处理car类型的命令
            if ("car".equalsIgnoreCase(deviceType)) {
                handleCarCommand(action);
            } else {
                System.err.println("[CarSimulator] 未知的设备类型: " + deviceType);
            }


            //上报状态的函数，暂时不用
            // 命令处理后立即上报状态
            //reportCurrentStatus();



        } catch (Exception e) {
            System.err.println("[CarSimulator] 处理命令时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleCarCommand(String action) {
        switch (action.toLowerCase()) {
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
            case "refuel":
                refuel();
                break;
            // 添加空调控制命令
            case "ac_on":
                turnOnAc();
                break;
            case "ac_off":
                turnOffAc();
                break;
            default:
                System.out.println("[CarSimulator] 未知命令: " + action);
        }
    }
// ... existing code ...

    private void startEngine() {
        if (fuelPercent <= 0) {
            System.out.println("[CarSimulator] 油量不足，无法启动发动机！");
            return;
        }

        if (!engineOn) {
            engineOn = true;
            System.out.println("[CarSimulator] 发动机已启动");
            statusPublisher.updateEngineStatus(true);
        } else {
            System.out.println("[CarSimulator] 发动机已经在运行");
        }
    }

    // 添加空调控制方法
    private void turnOnAc() {
        if (!acOn) {
            acOn = true;
            System.out.println("[CarSimulator] 空调已开启");
            reportCurrentStatus(); // 状态变化时上报
        } else {
            System.out.println("[CarSimulator] 空调已经开启");
        }
    }

    private void turnOffAc() {
        if (acOn) {
            acOn = false;
            System.out.println("[CarSimulator] 空调已关闭");
            reportCurrentStatus(); // 状态变化时上报
        } else {
            System.out.println("[CarSimulator] 空调已经关闭");
        }
    }
    private void stopEngine() {
        if (engineOn) {
            engineOn = false;
            System.out.println("[CarSimulator] 发动机已关闭");
            statusPublisher.updateEngineStatus(false);
        } else {
            System.out.println("[CarSimulator] 发动机已经关闭");
        }
    }

    private void lockDoors() {
        if (!doorsLocked) {
            doorsLocked = true;
            System.out.println("[CarSimulator] 车门已上锁");
            statusPublisher.updateDoorStatus(true);
        } else {
            System.out.println("[CarSimulator] 车门已经上锁");
        }
    }

    private void unlockDoors() {
        if (doorsLocked) {
            doorsLocked = false;
            System.out.println("[CarSimulator] 车门已解锁");
            statusPublisher.updateDoorStatus(false);
        } else {
            System.out.println("[CarSimulator] 车门已经解锁");
        }
    }

    private void refuel() {
        fuelPercent = 100.0f;
        System.out.println("[CarSimulator] 加油完成，当前油量: 100%");
        statusPublisher.updateFuelLevel(fuelPercent);
    }

    private void startStatusReporting() {
        statusReporter = Executors.newSingleThreadScheduledExecutor();

        // 每15秒上报一次状态
        statusReporter.scheduleWithFixedDelay(() -> {
            if (running.get()) {
                reportCurrentStatus();
            }
        }, 0, 15, TimeUnit.SECONDS);

        System.out.println("[CarSimulator] 状态上报已启动");
    }

    private void reportCurrentStatus() {
        if (statusPublisher == null) {
            return;
        }

        try {
            // 更新时间戳
            timeStamp = getCurrentTimeStamp();
            System.out.println("[CarSimulator] 发布车辆状态: engineOn=" + engineOn + ", doorsLocked=" + doorsLocked + ", acOn=" + acOn + ", fuelPercent=" + fuelPercent + ", location=" + location + ", timeStamp=" + timeStamp);
            statusPublisher.publishVehicleStatus(engineOn, doorsLocked, acOn, fuelPercent, location, timeStamp);
        } catch (Exception e) {
            System.err.println("[CarSimulator] 上报状态时发生错误: " + e.getMessage());
        }
    }
    private void startConsoleInteraction() {
        Thread consoleThread = new Thread(() -> {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            while (running.get()) {
                System.out.println("请输入命令 (lf: low_fuel, eo: engine_overheat, du: door_unlocked, exit): ");
                String input = scanner.nextLine().trim();

                switch (input.toLowerCase()) {
                    case "lf":
                        alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.LOW_FUEL, "LOW");
                        break;
                    case "eo":
                        alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.ENGINE_OVERHEAT, "HIGH");
                        break;
                    case "du":
                        alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.DOOR_UNLOCKED, "MEDIUM");
                        break;
                    case "exit":
                        System.out.println("正在退出...");
                        shutdown();
                        break;
                    default:
                        System.out.println("未知命令: " + input + ". 可用命令: lf, eo, du, exit");
                }
            }
            scanner.close();
        });
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void keepRunning() {
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                shutdown();
            }
        }));

        // 保持主线程运行
        try {
            while (running.get()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        if (!running.get()) {
            return;
        }

        System.out.println("[CarSimulator] 正在关闭车辆模拟器...");
        running.set(false);

        // 停止报警系统
        if (alertSystem != null) {
            alertSystem.close();
        }

        // 停止状态上报
        if (statusReporter != null) {
            statusReporter.shutdown();
            try {
                if (!statusReporter.awaitTermination(5, TimeUnit.SECONDS)) {
                    statusReporter.shutdownNow();
                }
            } catch (InterruptedException e) {
                statusReporter.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 停止订阅者
        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }

        // 关闭DDS连接
        if (ddsParticipant != null) {
            ddsParticipant.close();
        }

        System.out.println("[CarSimulator] 车辆模拟器已关闭");
    }


    public boolean isEngineOn() { return engineOn; }
    public boolean isDoorsLocked() { return doorsLocked; }
    public boolean isAcOn() { return acOn; }
    public float getFuelPercent() { return fuelPercent; }
    public String getLocation() { return location; }

    public static void main(String[] args) {
        System.out.println("[CarSimulator] 启动车辆模拟器...");
        CarSimulator car = new CarSimulator();
        car.start();
    }
}
