package HomeSimulator;

import HomeSimulator.DDS.DdsParticipant;
import HomeSimulator.DDS.CommandSubscriber;
import HomeSimulator.furniture.Furniture;
import HomeSimulator.furniture.FurnitureManager;
import IDL.Command;
import IDL.CommandTypeSupport;
import IDL.HomeStatusTypeSupport;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import HomeSimulator.HomeSimulatorAlert.AlertType;

/**
 * HomeSimulator主控制器
 * 协调DDS通信、家具管理和状态上报等各模块工作
 */
public class HomeSimulator {
    private static boolean hasLoad = false;

    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private FurnitureManager furnitureManager; // 家具管理器（需DDS资源初始化）
    private HomeSimulatorAlert alertSystem; // 报警系统
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

        // 1. 先初始化DDS（获取Publisher和Topic，供FurnitureManager使用）
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
        switch (action.toLowerCase()) {
            case "light_on":
            case "on":
                turnOnAllLights();
                break;
            case "light_off":
            case "off":
                turnOffAllLights();
                break;
            default:
                System.err.println("[HomeSimulator] 未知的灯光命令: " + action);
        }
    }

    private void handleAirConditionerCommand(String action) {
        switch (action.toLowerCase()) {
            case "ac_on":
            case "on":
                turnOnAllAirConditioners();
                break;
            case "ac_off":
            case "off":
                turnOffAllAirConditioners();
                break;
            case "cool":
                setAllAirConditionersMode("cool");
                break;
            case "heat":
                setAllAirConditionersMode("heat");
                break;
            default:
                System.err.println("[HomeSimulator] 未知的空调命令: " + action);
        }
    }

    private void turnOnAllLights() {
        furnitureManager.getFurnitureByType("light").forEach(light -> {
            light.setStatus("on"); // 触发灯具独立上报状态
        });
        System.out.println("[HomeSimulator] 已开启所有灯光");
    }

    private void turnOffAllLights() {
        furnitureManager.getFurnitureByType("light").forEach(light -> {
            light.setStatus("off"); // 触发灯具独立上报状态
        });
        System.out.println("[HomeSimulator] 已关闭所有灯光");
    }

    private void turnOnAllAirConditioners() {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus("cool"); // 默认制冷模式，触发空调独立上报状态
        });
        System.out.println("[HomeSimulator] 已开启所有空调");
    }

    private void turnOffAllAirConditioners() {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus("off"); // 触发空调独立上报状态
        });
        System.out.println("[HomeSimulator] 已关闭所有空调");
    }

    private void setAllAirConditionersMode(String mode) {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus(mode); // 触发空调独立上报状态
        });
        System.out.printf("[HomeSimulator] 已设置所有空调为%s模式%n", mode);
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

        System.out.println("[HomeSimulator] 正在关闭家居模拟器...");
        running.set(false);

        // 停止家具管理器
        if (furnitureManager != null) {
            furnitureManager.stop();
        }
        
        // 停止报警系统
        if (alertSystem != null) {
            alertSystem.stop();
        }

        // 停止DDS订阅者
        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }

        // 关闭DDS连接
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