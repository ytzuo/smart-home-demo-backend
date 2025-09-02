package HomeSimulator;

import HomeSimulator.DDS.DdsParticipant;
import HomeSimulator.DDS.CommandSubscriber;
import HomeSimulator.furniture.Furniture;
import HomeSimulator.furniture.AirConditioner;
import HomeSimulator.furniture.Light;
import HomeSimulator.furniture.FurnitureManager;
import HomeSimulator.HomeSimulatorAlert.AlertType;

import IDL.Command;
import IDL.CommandTypeSupport;
import IDL.HomeStatusTypeSupport;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HomeSimulator主控制器
 * 协调DDS通信、家具管理和状态上报等各模块工作
 */
public class HomeSimulator {
    private static boolean hasLoad = false;

    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private FurnitureManager furnitureManager; // 家具管理器（需DDS资源初始化）
    private HomeSimulatorAlert alertSystem;    // 报警系统
    private AtomicBoolean running;

    public HomeSimulator() {
        loadLibrary();
        this.running = new AtomicBoolean(false);
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

        System.out.println("[HomeSimulator] 家居模拟器启动完成");

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

        // 创建Topic
        Topic commandTopic = ddsParticipant.createTopic(
                "Command", CommandTypeSupport.get_instance());
        Topic homeStatusTopic = ddsParticipant.createTopic(
                "HomeStatus", HomeStatusTypeSupport.get_instance());

        // 初始化订阅者（命令接收）
        commandSubscriber = new CommandSubscriber();
        commandSubscriber.start(
                ddsParticipant.getSubscriber(),
                commandTopic,
                this::handleCommand);

        // 3. 创建家具管理器（传入DDS发布器和HomeStatus主题）
        Publisher ddsPublisher = ddsParticipant.getPublisher();
        furnitureManager = new FurnitureManager(ddsPublisher, homeStatusTopic);
        
        // 4. 创建报警系统
        alertSystem = new HomeSimulatorAlert(ddsPublisher, homeStatusTopic);

        System.out.println("[HomeSimulator] DDS初始化完成");
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
            // 添加报警相关命令处理
            case "alert_test":
                testDeviceAlert();
                break;
            case "alert_clear":
                if (alertSystem != null) {
                    alertSystem.clearAlert();
                }
                break;
            default:
                System.err.println("[HomeSimulator] 未知的家居命令: " + action);
        }
    }
    
    /**
     * 测试设备报警功能
     * 模拟触发一个灯具异常报警
     */
    private void testDeviceAlert() {
        if (alertSystem != null) {
            List<Furniture> lights = furnitureManager.getFurnitureByType("light");
            if (!lights.isEmpty()) {
                Furniture light = lights.get(0);
                alertSystem.triggerAlert(
                    HomeSimulatorAlert.AlertType.LIGHT_ABNORMAL,
                    String.format("灯具 %s 测试报警", light.getName())
                );
                System.out.println("[HomeSimulator] 触发测试报警");
            }
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

        System.out.println("[HomeSimulator] 正在关闭家居模拟器...");
        running.set(false);

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
