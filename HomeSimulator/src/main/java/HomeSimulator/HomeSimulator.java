package HomeSimulator;

import HomeSimulator.DDS.DdsParticipant;
import HomeSimulator.DDS.CommandSubscriber;
import HomeSimulator.furniture.*;
import HomeSimulator.HomeSimulatorAlert.AlertType;
import com.zrdds.infrastructure.*;
import IDL.Command;
import IDL.CommandTypeSupport;
import IDL.HomeStatusTypeSupport;
import IDL.AlertTypeSupport;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import IDL.Presence;
import IDL.PresenceTypeSupport;
import IDL.PresenceDataWriter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HomeSimulator主控制器
 * 协调DDS通信、家具管理和状态上报等各模块工作
 */
public class HomeSimulator {
    private static boolean hasLoad = false;
    private static HomeSimulator instance; // 单例实例

    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private FurnitureManager furnitureManager; // 家具管理器（需DDS资源初始化）
    private HomeSimulatorAlert alertSystem;    // 报警系统
    private AtomicBoolean running;
    private PresenceDataWriter presenceDataWriter;
    private Topic presenceTopic;
    public HomeSimulator() {
        loadLibrary();
        this.running = new AtomicBoolean(false);
        instance = this; // 设置单例实例
    }
    
    /**
     * 获取报警系统实例
     * @return 报警系统实例
     */
    public static HomeSimulatorAlert getAlertSystem() {
        return instance != null ? instance.alertSystem : null;
    }

    private void loadLibrary() {
        if (!hasLoad) {
            try {
                System.loadLibrary("ZRDDS_JAVA");
                hasLoad = true;
                System.out.println("[HomeSimulator] ZRDDS_JAVA库加载成功");
            } catch (UnsatisfiedLinkError e) {
                System.err.println("[HomeSimulator] 警告: 无法加载ZRDDS_JAVA库，DDS功能将不可用");
                System.err.println("[HomeSimulator] 请确保ZRDDS_JAVA库在系统路径中");
                System.exit(1);
            }
        }
    }

    public void start() {
        System.out.println("[HomeSimulator] 启动家居模拟器...");

        running.set(true);

        // 1. 初始化DDS（获取Publisher和Topic，供FurnitureManager使用）
        initDDS();

        // 2. 初始化家具管理器（传入DDS资源）
        furnitureManager.start();
        
        // 3. 启动报警系统
        alertSystem.start();

        // 4. 发送一次Presence状态
        publishPresenceStatus();

        System.out.println("[HomeSimulator] 家居模拟器启动完成");
        System.out.println("[HomeSimulator] 使用控制台命令触发报警: lt1(灯具状态异常), lh1(灯具过热), at1(空调温度异常), ap1(空调性能异常)");

        // 保持运行
        keepRunning();
    }

    private void initDDS() {
        ddsParticipant = DdsParticipant.getInstance();

        // 注册IDL类型
        CommandTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "Command");
        HomeStatusTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "HomeStatus");
        PresenceTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "Presence");
        AlertTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "Alert");
        
        // 创建Topic
        Topic commandTopic = ddsParticipant.createTopic(
                "Command", CommandTypeSupport.get_instance());
        Topic homeStatusTopic = ddsParticipant.createTopic(
                "HomeStatus", HomeStatusTypeSupport.get_instance());
        Topic presenceTopic = ddsParticipant.createTopic(
                "Presence", PresenceTypeSupport.get_instance());
        Topic alertTopic = ddsParticipant.createTopic(
                "Alert", AlertTypeSupport.get_instance());
        
        // 初始化订阅者（命令接收）
        commandSubscriber = new CommandSubscriber();
        commandSubscriber.start(
                ddsParticipant.getSubscriber(),
                commandTopic,
                this::handleCommand);

        // 创建家具管理器（传入DDS发布器和HomeStatus主题）
        Publisher ddsPublisher = ddsParticipant.getPublisher();
        furnitureManager = new FurnitureManager(ddsPublisher, homeStatusTopic);
        
        // 创建报警系统
        alertSystem = new HomeSimulatorAlert(ddsPublisher, homeStatusTopic, alertTopic);
        alertSystem.setFurnitureManager(furnitureManager);

        // 创建Presence DataWriter
        DataWriterQos presenceQos = new DataWriterQos();
        ddsPublisher.get_default_datawriter_qos(presenceQos);
        // 添加QoS配置
        presenceQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        presenceQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        presenceQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        presenceQos.history.depth = 10;
        presenceDataWriter = (PresenceDataWriter) ddsPublisher.create_datawriter(
                presenceTopic, presenceQos, null, StatusKind.STATUS_MASK_NONE);

        System.out.println("[HomeSimulator] DDS初始化完成");
    }

    // 添加Presence单次发送方法
    private void publishPresenceStatus() {
        if (presenceDataWriter == null) {
            System.err.println("[HomeSimulator] Presence DataWriter未初始化");
            return;
        }

        try {
            // 获取所有设备状态
            List<Furniture> allDevices = furnitureManager.getAllFurniture();
            for (Furniture device : allDevices) {
                Presence presence = new Presence();
                presence.deviceId = device.getId();
                presence.deviceType = device.getType();
                presence.inRange = true; // 假设初始状态为在线
                presence.timeStamp = LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // 发布状态
                ReturnCode_t rtn = presenceDataWriter.write(presence, InstanceHandle_t.HANDLE_NIL_NATIVE);
                if (rtn == ReturnCode_t.RETCODE_OK) {
                    System.out.printf("[HomeSimulator] 发送Presence: %s(%s) - %s%n",
                            device.getName(), device.getId(), presence.inRange ? "在线" : "离线");
                } else {
                    System.out.println("上报设备状态失败");
                }
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 发送Presence失败: " + e.getMessage());
        }
    }


    private void handleCommand(Command command) {
        if (command == null) {
            System.err.println("[HomeSimulator] 接收到空命令");
            return;
        }

        String deviceType = command.deviceType;
        String action = command.action;

        System.out.printf("[HomeSimulator] 处理命令: deviceType=%s, action=%s%n", deviceType, action);

        try {
            switch (deviceType.toLowerCase()) {
                case "home":
                    handleHomeCommand(action);
                    break;
                case "light":
                    handleLightCommand(action);
                    break;
                case "ac":
                    handleAirConditionerCommand(action);
                    break;
                default:
                    System.err.println("[HomeSimulator] 未知的设备类型: " + deviceType);
            }

            // 命令处理后，家具会自动独立上报状态（无需手动触发汇总）

        } catch (Exception e) {
            System.err.println("[HomeSimulator] 处理命令时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleHomeCommand(String action) {
        switch (action.toLowerCase()) {
            case "light_on":
                turnOnAllLights();
                break;
            case "light_off":
                turnOffAllLights();
                break;
            case "ac_on":
                turnOnAllAirConditioners();
                break;
            case "ac_off":
                turnOffAllAirConditioners();
                break;
            default:
                System.err.println("[HomeSimulator] 未知的家居命令: " + action);
        }
    }


    private void handleLightCommand(String action) {
        String[] parts = action.split("_");
        if (parts.length < 3) {
            System.err.println("[HomeSimulator] 灯光命令格式错误: " + action);
            return;
        }
        String operation = parts[0];
        String lightId = parts[1];
        String param = parts[2];

        // 获取目标灯具
        Light targetLight = (Light) furnitureManager
                .getFurnitureByType("light").stream()
                .filter(light -> lightId.equals(light.getId()))
                .findFirst()
                .orElse(null);

        if (targetLight == null) {
            System.err.println("[HomeSimulator] 未找到灯具: " + lightId);
            return;
        }

        switch (operation.toLowerCase()) {
            case "switch":
                if ("on".equals(param)) {
                    turnOnLight(lightId);
                } else if ("off".equals(param)) {
                    turnOffLight(lightId);
                }
                break;
            case "brightness":
                try {
                    int brightness = Integer.parseInt(param);
                    targetLight.setBrightness(brightness);
                    targetLight.publishStatus();
                } catch (NumberFormatException e) {
                    System.err.println("[HomeSimulator] 亮度值格式错误: " + param);
                }
                break;
            case "temp":
                targetLight.setColorTemp(param);
                targetLight.publishStatus();
                break;
            case "mode":
                targetLight.setSceneMode(param);
                targetLight.publishStatus();
                break;
            default:
                System.err.println("[HomeSimulator] 未知的灯光操作: " + operation);
        }
    }

    // 添加单个灯具控制方法
    private void turnOnLight(String id) {
        furnitureManager.getFurnitureByType("light").stream()
                .filter(light -> id.equals(light.getId()))
                .findFirst()
                .ifPresent(light -> {
                    light.setStatus("on");
                    System.out.println("[HomeSimulator] 已开启灯具: " + id);
                });
    }

    private void turnOffLight(String id) {
        furnitureManager.getFurnitureByType("light").stream()
                .filter(light -> id.equals(light.getId()))
                .findFirst()
                .ifPresent(light -> {
                    light.setStatus("off");
                    System.out.println("[HomeSimulator] 已关闭灯具: " + id);
                });
    }

    private void handleAirConditionerCommand(String action) {
        String[] parts = action.split("_");
        if (parts.length < 2) {
            System.err.println("[HomeSimulator] 空调命令格式错误: " + action);
            return;
        }

        String operation = parts[0];
        String acId = parts[1];
        AirConditioner targetAc = (AirConditioner) furnitureManager
                .getFurnitureByType("ac")
                .stream()
                .filter(ac -> acId.equals(ac.getId()))
                .findFirst()
                .orElse(null);

        if (targetAc == null) {
            System.err.println("[HomeSimulator] 未找到空调: " + acId);
            return;
        }

        switch (operation.toLowerCase()) {
            case "switch":
                if (parts.length >= 3) {
                    String state = parts[2];
                    if ("on".equals(state)) {
                        targetAc.setOn(true);
                    } else if ("off".equals(state)) {
                        targetAc.setOn(false);
                    }
                    targetAc.publishStatus();
                }
                break;
            case "cool":
                targetAc.setCoolingMode(true);
                targetAc.publishStatus();
                break;
            case "swing":
                targetAc.setSwingMode(!targetAc.isSwingMode());
                targetAc.publishStatus();
                break;
            case "dehumidify":
                targetAc.setDehumidificationMode(!targetAc.isDehumidificationMode());
                targetAc.publishStatus();
                break;
            case "temp":
                if (parts.length >= 3) {
                    try {
                        int temp = Integer.parseInt(parts[2]);
                        targetAc.setTemperature(temp);
                        targetAc.publishStatus();
                    } catch (NumberFormatException e) {
                        System.err.println("[HomeSimulator] 温度值格式错误: " + parts[2]);
                    }
                }
                break;
            default:
                System.err.println("[HomeSimulator] 未知的空调操作: " + operation);
        }
    }

    private void turnOnAllLights() {
        furnitureManager.getFurnitureByType("light").forEach(light -> {
            light.setStatus("on");
        });
        System.out.println("[HomeSimulator] 已开启所有灯光");
    }

    private void turnOffAllLights() {
        furnitureManager.getFurnitureByType("light").forEach(light -> {
            light.setStatus("off");
        });
        System.out.println("[HomeSimulator] 已关闭所有灯光");
    }

    private void turnOnAllAirConditioners() {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus("cool");
        });
        System.out.println("[HomeSimulator] 已开启所有空调");
    }

    private void turnOffAllAirConditioners() {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus("off");
        });
        System.out.println("[HomeSimulator] 已关闭所有空调");
    }

    private void setAllAirConditionersMode(String mode) {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus(mode);
        });
        System.out.printf("[HomeSimulator] 已设置所有空调为%s模式%n", mode);
    }

    private void keepRunning() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                shutdown();
            }
        }));

        // 启动控制台交互线程
        startConsoleInteraction();

        try {
            while (running.get()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 启动控制台交互功能
     */
    private void startConsoleInteraction() {
        Thread consoleThread = new Thread(() -> {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            System.out.println("=== 控制台报警控制 ===");
            System.out.println("输入命令触发报警：");
            System.out.println("  lt1 - 触发light1状态异常报警");
            System.out.println("  lh1 - 触发light1过热报警");
            System.out.println("  at1 - 触发ac1温度异常报警");
            System.out.println("  ap1 - 触发ac1性能异常报警");
            System.out.println("  r1  - 重置light1报警");
            System.out.println("  r2  - 重置ac1报警");
            System.out.println("  q   - 退出程序");
            System.out.println("====================");

            while (running.get()) {
                System.out.print("\n报警控制> ");
                String input = scanner.nextLine().trim().toLowerCase();

                switch (input) {
                    case "lt1":
                        triggerLightAlert("light1", "status");
                        break;
                    case "lh1":
                        triggerLightAlert("light1", "overheat");
                        break;
                    case "at1":
                        triggerAirConditionerAlert("ac1", "temperature");
                        break;
                    case "ap1":
                        triggerAirConditionerAlert("ac1", "performance");
                        break;
                    case "r1":
                        resetDeviceAlert("light1");
                        break;
                    case "r2":
                        resetDeviceAlert("ac1");
                        break;
                    case "q":
                        System.out.println("正在退出...");
                        shutdown();
                        break;
                    default:
                        System.out.println("无效命令，请重新输入");
                }
            }
            scanner.close();
        });
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    /**
     * 触发灯具报警
     */
    private void triggerLightAlert(String deviceId, String alertType) {
        try {
            Light light = (Light)
                furnitureManager.getFurnitureByType("light").stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);
            
            if (light != null) {
                if ("status".equals(alertType)) {
                    light.triggerStatusAbnormalAlert();
                } else if ("overheat".equals(alertType)) {
                    light.triggerOverheatAlert();
                }
            } else {
                System.out.println("未找到设备: " + deviceId);
            }
        } catch (Exception e) {
            System.out.println("触发报警失败: " + e.getMessage());
        }
    }

    /**
     * 触发空调报警
     */
    private void triggerAirConditionerAlert(String deviceId, String alertType) {
        try {
            AirConditioner ac = (AirConditioner)
                furnitureManager.getFurnitureByType("ac").stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);
            
            if (ac != null) {
                if ("temperature".equals(alertType)) {
                    ac.triggerTemperatureAbnormalAlert();
                } else if ("performance".equals(alertType)) {
                    ac.triggerPerformanceAbnormalAlert();
                }
            } else {
                System.out.println("未找到设备: " + deviceId);
            }
        } catch (Exception e) {
            System.out.println("触发报警失败: " + e.getMessage());
        }
    }

    /**
     * 重置设备报警
     */
    private void resetDeviceAlert(String deviceId) {
        try {
            // 先从所有设备中查找，不限制类型
            Furniture device = furnitureManager.getFurnitureByType("light").stream()
                .filter(f -> deviceId.equals(f.getId()))
                .findFirst()
                .orElse(null);
            if (device == null) {
                device = furnitureManager.getFurnitureByType("ac").stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);
            }

            if (device instanceof AlertableDevice) {
                ((AlertableDevice) device).resetAlert();
                System.out.println("已重置设备报警: " + deviceId);
                
                // 确保报警系统也清除该设备的报警
                if (alertSystem != null) {
                    alertSystem.clearDeviceAlert(deviceId);
                }
            } else {
                System.out.println("设备不支持报警重置: " + deviceId);
            }
        } catch (Exception e) {
            System.out.println("重置报警失败: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (!running.get()) {
            return;
        }

        System.out.println("[HomeSimulator] 正在关闭家居模拟器...");
        running.set(false);

        // 关闭家具管理器（包含定时上报任务）
        if (furnitureManager != null) {
            furnitureManager.stop();
        }
        
        if (alertSystem != null) {
            alertSystem.stop();
        }

        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }

        if (ddsParticipant != null) {
            ddsParticipant.close();
        }

        System.out.println("[HomeSimulator] 家居模拟器已关闭");
    }

    public static void main(String[] args) {
        System.out.println("[HomeSimulator] 启动家居模拟器...");
        HomeSimulator simulator = new HomeSimulator();
        simulator.start();
    }
}
