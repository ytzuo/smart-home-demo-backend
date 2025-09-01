package HomeSimulator;

import HomeSimulator.DDS.DdsParticipant;
import HomeSimulator.DDS.CommandSubscriber;
import HomeSimulator.DDS.HomeStatusPublisher;
import HomeSimulator.furniture.FurnitureManager;
import AppTestIDL.Command;
import AppTestIDL.CommandTypeSupport;
import AppTestIDL.HomeStatusTypeSupport;
import com.zrdds.topic.Topic;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HomeSimulator主控制器
 * 协调DDS通信、家具管理和状态上报等各模块工作
 */
public class HomeSimulator {
    private static boolean hasLoad = false;
    
    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private HomeStatusPublisher homeStatusPublisher;
    private FurnitureManager furnitureManager;
    private ScheduledExecutorService statusReporter;
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
        
        // 初始化家具管理器
        furnitureManager = new FurnitureManager();
        furnitureManager.start();
        
        // 初始化DDS
        initDDS();
        
        // 启动状态上报
        startStatusReporting();
        
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
        
        // 初始化订阅者和发布者
        commandSubscriber = new CommandSubscriber();
        commandSubscriber.start(
                ddsParticipant.getSubscriber(), 
                commandTopic, 
                this::handleCommand);
        
        homeStatusPublisher = new HomeStatusPublisher();
        homeStatusPublisher.start(
                ddsParticipant.getPublisher(), 
                homeStatusTopic);
        
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
            
            // 命令处理后立即上报状态
            reportCurrentStatus();
            
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
            ac.setStatus("cool"); // 默认制冷模式
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
    
    private void startStatusReporting() {
        statusReporter = Executors.newSingleThreadScheduledExecutor();
        
        // 每10秒上报一次状态
        statusReporter.scheduleWithFixedDelay(() -> {
            if (running.get()) {
                reportCurrentStatus();
            }
        }, 0, 10, TimeUnit.SECONDS);
        
        System.out.println("[HomeSimulator] 状态上报已启动");
    }
    
    private void reportCurrentStatus() {
        if (homeStatusPublisher == null || !homeStatusPublisher.isStarted()) {
            return;
        }
        
        try {
            // 获取当前状态
            Map<String, String> allStatus = furnitureManager.getAllFurnitureStatus();
            
            // 汇总状态信息
            boolean anyLightOn = false;
            String acStatus = "off";
            
            for (Map.Entry<String, String> entry : allStatus.entrySet()) {
                String furnitureId = entry.getKey();
                String status = entry.getValue();
                
                if (furnitureId.startsWith("light") && "on".equals(status)) {
                    anyLightOn = true;
                } else if (furnitureId.startsWith("ac") && !"off".equals(status)) {
                    acStatus = status;
                }
            }
            
            // 发布状态
            homeStatusPublisher.publishHomeStatus(acStatus, anyLightOn);
            
            // 打印状态汇总
            System.out.println("[HomeSimulator] " + furnitureManager.getFurnitureStatusSummary());
            
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 上报状态时发生错误: " + e.getMessage());
        }
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
        
        // 停止状态上报
        if (statusReporter != null) {
            statusReporter.shutdown();
        }
        
        // 停止家具管理器
        if (furnitureManager != null) {
            furnitureManager.stop();
        }
        
        // 停止DDS组件
        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }
        if (homeStatusPublisher != null) {
            homeStatusPublisher.stop();
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
