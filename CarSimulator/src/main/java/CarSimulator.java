import CarSimulator.DDS.CommandSubscriber;
import CarSimulator.DDS.DdsParticipant;
import CarSimulator.DDS.StatusPublisher;
import CarSimulator.DDS.VehicleHealthPublisher;
import CarSimulator.VehicleHealthManager;
import IDL.Command;
import IDL.CommandTypeSupport;
import IDL.VehicleHealthReport;
import IDL.VehicleHealthReportTypeSupport;
import IDL.VehicleStatusTypeSupport;
import com.zrdds.topic.Topic;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CarSimulator {
    private static final String VEHICLE_ID = "car_001";
    private static boolean hasLoad = false;

    // DDS Components
    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private StatusPublisher statusPublisher;
    private VehicleHealthPublisher vehicleHealthPublisher;

    // Schedulers
    private ScheduledExecutorService statusUpdater; // For realistic status changes
    private ScheduledExecutorService statusReporter; // For publishing status
    private ScheduledExecutorService healthReporter; // For publishing health reports

    // Vehicle State
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean engineOn = false;
    private boolean doorsLocked = true;
    private float fuelPercent = 100.0f;
    private boolean acOn = false;
    private String location = "Garage";
    private String timeStamp;
    private long engineRunningTime = 0;

    // Modules
    private CarSimulatorAlert alertSystem;
    private VehicleHealthManager vehicleHealthManager;

    // Constants
    private static final float BASE_FUEL_CONSUMPTION = 0.08f;
    private static final float AC_EXTRA_CONSUMPTION = 0.04f;

    public CarSimulator() {
        loadLibrary();
        alertSystem = new CarSimulatorAlert();
        vehicleHealthManager = new VehicleHealthManager(VEHICLE_ID);
        timeStamp = getCurrentTimeStamp();
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

        try {
            running.set(true);
            initDDS();
            startSchedulers();
            startConsoleInteraction();
            keepRunning();
        } catch (Exception e) {
            System.err.println("[CarSimulator] 启动过程中发生严重错误: " + e.getMessage());
            e.printStackTrace();
            shutdown();
        }
    }

    private void startSchedulers() {
        startRealisticStatusUpdates();
        startStatusReporting();
        startHealthReporting();
    }

    private void startRealisticStatusUpdates() {
        statusUpdater = Executors.newSingleThreadScheduledExecutor();

        // 每1秒更新一次状态
        statusUpdater.scheduleAtFixedRate(() -> {
            if (running.get()) {
                updateFuelConsumption(); // 更新油耗
                updateLocationByDriving(); // 根据行驶状态更新位置
            }
        }, 0, 1, TimeUnit.SECONDS);

        System.out.println("[CarSimulator] 车辆状态自动更新已启动");
    }

    // 根据发动机和空调状态更新油耗
    private void updateFuelConsumption() {
        if (engineOn && fuelPercent > 0) {
            // 基础油耗 + 空调额外油耗
            float totalConsumption = BASE_FUEL_CONSUMPTION + (acOn ? AC_EXTRA_CONSUMPTION : 0);
            fuelPercent = Math.max(0, fuelPercent - totalConsumption);

            // 每10秒打印一次油耗变化（避免日志刷屏）
            if (System.currentTimeMillis() % 10000 < 1000) {
                System.out.printf("[CarSimulator] 油耗更新: 当前油量=%.1f%%%n", fuelPercent);
            }

            // 低油量报警（燃油低于15%时触发）
            if (fuelPercent <= 15 && fuelPercent + totalConsumption > 15) {
                alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.LOW_FUEL, "MEDIUM");
            }

            // 燃油耗尽时自动关闭发动机
            if (fuelPercent == 0) {
                stopEngine();
                System.out.println("[CarSimulator] 燃油耗尽，发动机已自动关闭");
            }
        }
    }

    // 根据发动机运行时间更新位置（仅使用location、engineOn）
    private void updateLocationByDriving() {
        if (engineOn) {
            engineRunningTime++;
            // 发动机运行30秒后随机更新位置（模拟行驶）
            if (engineRunningTime % 30 == 0) {
                String[] locations = {"Street", "Highway", "Parking Lot", "Store", "Garage"};
                String newLocation = locations[(int)(Math.random() * locations.length)];
                // 避免与当前位置重复
                if (!newLocation.equals(location)) {
                    location = newLocation;
                    System.out.printf("[CarSimulator] 行驶中，位置更新为: %s%n", location);
                    reportCurrentStatus(); // 位置变化时立即上报
                }
            }
        } else {
            engineRunningTime = 0; // 发动机关闭时重置运行时间
        }
    }

    private void initDDS() {
        try {
            ddsParticipant = DdsParticipant.getInstance();

            registerDdsTypes();

            Topic commandTopic = ddsParticipant.createTopic("Command", CommandTypeSupport.get_instance());
            Topic vehicleStatusTopic = ddsParticipant.createTopic("VehicleStatus", VehicleStatusTypeSupport.get_instance());
            Topic vehicleHealthTopic = ddsParticipant.createTopic("VehicleHealthReport", VehicleHealthReportTypeSupport.get_instance());

            commandSubscriber = new CommandSubscriber();
            commandSubscriber.setCommandHandler(this::handleCommand);
            commandSubscriber.start(ddsParticipant.getSubscriber(), commandTopic);

            statusPublisher = new StatusPublisher();
            statusPublisher.start(ddsParticipant.getPublisher(), vehicleStatusTopic);

            vehicleHealthPublisher = new VehicleHealthPublisher();
            vehicleHealthPublisher.start(ddsParticipant.getPublisher(), vehicleHealthTopic);

            alertSystem.initialize(ddsParticipant, this);
            alertSystem.startMonitoring();

            reportCurrentStatus();

            System.out.println("[CarSimulator] DDS初始化完成");
        } catch (Exception e) {
            System.err.println("[CarSimulator] DDS初始化失败: " + e.getMessage());
            throw new RuntimeException("DDS Initialization failed", e);
        }
    }

    private void registerDdsTypes() {
        CommandTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "Command");
        VehicleStatusTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "VehicleStatus");
        VehicleHealthReportTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "VehicleHealthReport");
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
            }
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
            case"get_status":
                reportCurrentStatus();
                break;
            default:
                System.out.println("[CarSimulator] 未知命令: " + action);
        }
    }

    private void startEngine() {
        if (fuelPercent <= 0) {
            System.out.println("[CarSimulator] 油量不足，无法启动发动机！");
            // 触发低油量报警
            alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.LOW_FUEL, "HIGH");
            return;
        }

        if (!engineOn) {
            engineOn = true;
            // 重置运行时间
            engineRunningTime = 0;
            System.out.println("[CarSimulator] 发动机已启动");
            statusPublisher.updateEngineStatus(true);
            // 状态变化时上报
            reportCurrentStatus();
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

    private void startHealthReporting() {
        healthReporter = Executors.newSingleThreadScheduledExecutor();

        // 每30秒上报一次健康状况
        healthReporter.scheduleAtFixedRate(() -> {
            if (running.get()) {
                try {
                    VehicleHealthReport report = vehicleHealthManager.generateReport();
                    vehicleHealthPublisher.publish(report);
                } catch (Exception e) {
                    System.err.println("[CarSimulator] 发布车辆健康报告时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, 30, TimeUnit.SECONDS);

        System.out.println("[CarSimulator] 车辆健康报告已启动");
    }

    private void reportCurrentStatus() {
        if (statusPublisher == null) {
            return;
        }

        try {
            // 每次上报时都获取当前时间戳
            String currentTimeStamp = getCurrentTimeStamp();
            System.out.println("[CarSimulator] 发布车辆状态: engineOn=" + engineOn + ", doorsLocked=" + doorsLocked + ", acOn=" + acOn + ", fuelPercent=" + fuelPercent + ", location=" + location + ", timeStamp=" + currentTimeStamp);
            statusPublisher.publishVehicleStatus(engineOn, doorsLocked, acOn, fuelPercent, location, currentTimeStamp);
            
            // 更新内部状态的时间戳
            this.timeStamp = currentTimeStamp;
        } catch (Exception e) {
            System.err.println("[CarSimulator] 上报状态时发生错误: " + e.getMessage());
        }
    }
    
    private String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
    
    /**
     * 获取当前车辆状态的快照，包含最新时间戳
     */
    public String getCurrentStatusSnapshot() {
        return String.format("时间: %s, 发动机: %s, 车门: %s, 空调: %s, 油量: %.1f%%, 位置: %s",
                getCurrentTimeStamp(),
                engineOn ? "开启" : "关闭",
                doorsLocked ? "上锁" : "解锁",
                acOn ? "开启" : "关闭",
                fuelPercent,
                location);
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

        shutdownSchedulers();
        shutdownDds();

        System.out.println("[CarSimulator] 车辆模拟器已关闭");
    }

    private void shutdownSchedulers() {
        shutdownExecutor(statusUpdater, "Status Updater");
        shutdownExecutor(statusReporter, "Status Reporter");
        shutdownExecutor(healthReporter, "Health Reporter");
    }

    private void shutdownDds() {
        if (alertSystem != null) {
            alertSystem.close();
        }
        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }
        if (ddsParticipant != null) {
            ddsParticipant.close();
        }
    }

    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("[" + name + "] 已关闭");
        }
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
