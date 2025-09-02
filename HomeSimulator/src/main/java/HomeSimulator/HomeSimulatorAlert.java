package HomeSimulator;

import HomeSimulator.DDS.DdsParticipant;
import HomeSimulator.furniture.*;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import IDL.HomeStatus;
import IDL.HomeStatusDataWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 家居报警系统
 * 负责监测和发布家庭安全警报
 */
public class HomeSimulatorAlert {
    // 报警类型枚举
    public enum AlertType {
        NONE("none"),
        FIRE("fire"),
        INTRUSION("intrusion"),
        GAS_LEAK("gas_leak"),
        WATER_LEAK("water_leak"),
        // 家具设备相关报警类型
        DEVICE_OFFLINE("device_offline"),
        DEVICE_MALFUNCTION("device_malfunction"),
        DEVICE_OVERHEAT("device_overheat"),
        LIGHT_ABNORMAL("light_abnormal"),
        AC_ABNORMAL("ac_abnormal");

        private final String value;

        AlertType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static AlertType fromString(String type) {
            for (AlertType t : AlertType.values()) {
                if (t.value.equalsIgnoreCase(type)) {
                    return t;
                }
            }
            return NONE;
        }
    }

    private Publisher publisher;
    private Topic homeStatusTopic;
    private HomeStatusDataWriter homeStatusWriter;
    private AtomicBoolean alertActive;
    private AlertType currentAlertType;
    private String alertMessage;
    private ScheduledExecutorService alertReporter;
    private List<AlertListener> listeners;
    
    // 家具设备监控相关
    private FurnitureManager furnitureManager;
    private Map<String, DeviceStatus> deviceStatusMap;
    private ScheduledExecutorService deviceMonitor;
    
    /**
     * 设备状态记录类
     */
    private static class DeviceStatus {
        String id;
        String type;
        String status;
        int abnormalCount;
        boolean alertTriggered;
        
        DeviceStatus(String id, String type, String status) {
            this.id = id;
            this.type = type;
            this.status = status;
            this.abnormalCount = 0;
            this.alertTriggered = false;
        }
    }

    /**
     * 报警状态监听器接口
     */
    public interface AlertListener {
        void onAlertStatusChanged(AlertType type, String message, boolean isActive);
    }

    /**
     * 构造函数
     * @param publisher DDS发布器
     * @param homeStatusTopic 家庭状态主题
     */
    public HomeSimulatorAlert(Publisher publisher, Topic homeStatusTopic) {
        this.publisher = publisher;
        this.homeStatusTopic = homeStatusTopic;
        this.alertActive = new AtomicBoolean(false);
        this.currentAlertType = AlertType.NONE;
        this.alertMessage = "";
        this.listeners = new ArrayList<>();
        this.deviceStatusMap = new HashMap<>();
    }
    
    /**
     * 设置家具管理器，用于监控设备状态
     * @param furnitureManager 家具管理器
     */
    public void setFurnitureManager(FurnitureManager furnitureManager) {
        this.furnitureManager = furnitureManager;
    }

    /**
     * 启动报警系统
     */
    public void start() {
        System.out.println("[HomeSimulatorAlert] 启动家居报警系统...");
        
        // 创建HomeStatus数据写入器
        homeStatusWriter = (HomeStatusDataWriter) publisher.create_datawriter(
                homeStatusTopic, 
                Publisher.DATAWRITER_QOS_DEFAULT, 
                null, 
                0);
        
        if (homeStatusWriter == null) {
            System.err.println("[HomeSimulatorAlert] 创建HomeStatus数据写入器失败");
            return;
        }

        // 创建定时报告服务
        alertReporter = Executors.newSingleThreadScheduledExecutor();
        alertReporter.scheduleAtFixedRate(
                this::reportAlertStatus, 
                0, 
                5, 
                TimeUnit.SECONDS);
                
        // 启动设备监控
        if (furnitureManager != null) {
            startDeviceMonitoring();
        }

        System.out.println("[HomeSimulatorAlert] 家居报警系统启动完成");
    }
    
    /**
     * 启动设备监控
     */
    private void startDeviceMonitoring() {
        System.out.println("[HomeSimulatorAlert] 启动设备监控...");
        
        // 初始化设备状态映射
        initializeDeviceStatusMap();
        
        // 创建设备监控定时任务
        deviceMonitor = Executors.newSingleThreadScheduledExecutor();
        deviceMonitor.scheduleAtFixedRate(
                this::monitorDevices, 
                0, 
                3, 
                TimeUnit.SECONDS);
    }
    
    /**
     * 初始化设备状态映射
     */
    private void initializeDeviceStatusMap() {
        // 清空现有映射
        deviceStatusMap.clear();
        
        // 获取所有灯具
        List<Furniture> lights = furnitureManager.getFurnitureByType("light");
        for (Furniture light : lights) {
            deviceStatusMap.put(light.getId(), 
                    new DeviceStatus(light.getId(), "light", light.getStatus()));
        }
        
        // 获取所有空调
        List<Furniture> acs = furnitureManager.getFurnitureByType("ac");
        for (Furniture ac : acs) {
            deviceStatusMap.put(ac.getId(), 
                    new DeviceStatus(ac.getId(), "ac", ac.getStatus()));
        }
        
        System.out.printf("[HomeSimulatorAlert] 初始化设备状态映射，共%d个设备%n", 
                deviceStatusMap.size());
    }

    /**
     * 停止报警系统
     */
    public void stop() {
        System.out.println("[HomeSimulatorAlert] 停止家居报警系统...");
        
        if (alertReporter != null) {
            alertReporter.shutdown();
            try {
                alertReporter.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 停止设备监控
        if (deviceMonitor != null) {
            deviceMonitor.shutdown();
            try {
                deviceMonitor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 清除所有报警
        clearAlert();
        
        System.out.println("[HomeSimulatorAlert] 家居报警系统已停止");
    }

    /**
     * 触发报警
     * @param type 报警类型
     * @param message 报警信息
     */
    public void triggerAlert(AlertType type, String message) {
        if (type == AlertType.NONE) {
            return;
        }
        
        this.currentAlertType = type;
        this.alertMessage = message;
        this.alertActive.set(true);
        
        System.out.printf("[HomeSimulatorAlert] 触发报警: 类型=%s, 信息=%s%n", 
                type.getValue(), message);
        
        // 立即报告状态
        reportAlertStatus();
        
        // 通知监听器
        notifyListeners();
    }

    /**
     * 清除报警
     */
    public void clearAlert() {
        if (!alertActive.get()) {
            return;
        }
        
        System.out.printf("[HomeSimulatorAlert] 清除报警: 类型=%s%n", 
                currentAlertType.getValue());
        
        this.alertActive.set(false);
        this.currentAlertType = AlertType.NONE;
        this.alertMessage = "";
        
        // 立即报告状态
        reportAlertStatus();
        
        // 通知监听器
        notifyListeners();
    }

    /**
     * 获取当前报警状态
     * @return 是否有活跃报警
     */
    public boolean isAlertActive() {
        return alertActive.get();
    }

    /**
     * 获取当前报警类型
     * @return 报警类型
     */
    public AlertType getCurrentAlertType() {
        return currentAlertType;
    }

    /**
     * 获取当前报警信息
     * @return 报警信息
     */
    public String getAlertMessage() {
        return alertMessage;
    }

    /**
     * 添加报警状态监听器
     * @param listener 监听器
     */
    public void addAlertListener(AlertListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除报警状态监听器
     * @param listener 监听器
     */
    public void removeAlertListener(AlertListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器
     */
    private void notifyListeners() {
        for (AlertListener listener : listeners) {
            listener.onAlertStatusChanged(
                    currentAlertType, 
                    alertMessage, 
                    alertActive.get());
        }
    }

    /**
     * 报告当前报警状态
     */
    private void reportAlertStatus() {
        if (homeStatusWriter == null) {
            return;
        }

        try {
            // 创建HomeStatus实例
            HomeStatus status = new HomeStatus();
            
            // 设置设备ID (StringSeq不能使用add方法，需要先设置大小)
            status.deviceIds.maximum(1);
            status.deviceIds.length(1);
            status.deviceIds.set_at(0, "alert_system");
            
            // 设置设备类型
            status.deviceTypes.maximum(1);
            status.deviceTypes.length(1);
            status.deviceTypes.set_at(0, "alert");
            
            // 设置设备状态
            status.deviceStatus.maximum(1);
            status.deviceStatus.length(1);
            status.deviceStatus.set_at(0, alertActive.get() ? 
                    currentAlertType.getValue() : "none");
            
            // 设置时间戳
            status.timeStamp = getCurrentTimeStamp();

            // 发布状态
            try {
                homeStatusWriter.write(status, InstanceHandle_t.HANDLE_NIL_NATIVE);
                
                if (alertActive.get()) {
                    System.out.printf("[HomeSimulatorAlert] 报告报警状态: 类型=%s, 信息=%s%n", 
                            currentAlertType.getValue(), alertMessage);
                }
            } catch (Exception e) {
                System.err.println("[HomeSimulatorAlert] 写入DDS数据失败: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulatorAlert] 报告状态时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取当前时间戳
     * @return 格式化的时间戳字符串
     */
    private String getCurrentTimeStamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }
    
    /**
     * 监控设备状态
     * 定期检查所有设备的状态，发现异常时触发报警
     */
    private void monitorDevices() {
        if (furnitureManager == null) {
            return;
        }
        
        try {
            // 检查所有实现了AlertableDevice接口的设备
            checkAlertableDevices();
            
        } catch (Exception e) {
            System.err.println("[HomeSimulatorAlert] 监控设备时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 检查所有可报警设备
     */
    private void checkAlertableDevices() {
        // 获取所有家具
        List<Furniture> allDevices = new ArrayList<>();
        allDevices.addAll(furnitureManager.getFurnitureByType("light"));
        allDevices.addAll(furnitureManager.getFurnitureByType("ac"));
        
        // 检查每个设备是否实现了AlertableDevice接口
        for (Furniture device : allDevices) {
            if (device instanceof AlertableDevice) {
                AlertableDevice alertableDevice = (AlertableDevice) device;
                
                // 调用设备自身的异常检测方法
                boolean isAbnormal = alertableDevice.checkAbnormal();
                
                if (isAbnormal) {
                    // 设备报告异常，触发报警
                    handleDeviceAlert(alertableDevice);
                }
            }
        }
    }
    
    /**
     * 处理设备报警
     * @param device 报警设备
     */
    private void handleDeviceAlert(AlertableDevice device) {
        String deviceId = device.getDeviceId();
        String deviceType = device.getDeviceType();
        String alertType = device.getAlertType();
        String alertMessage = device.getAlertMessage();
        
        // 根据设备类型和报警类型确定系统报警类型
        AlertType systemAlertType = mapToSystemAlertType(deviceType, alertType);
        
        // 触发系统报警
        triggerAlert(systemAlertType, alertMessage);
        
        System.out.printf("[HomeSimulatorAlert] 收到设备报警: ID=%s, 类型=%s, 消息=%s%n", 
                deviceId, alertType, alertMessage);
    }
    
    /**
     * 将设备报警类型映射为系统报警类型
     * @param deviceType 设备类型
     * @param alertType 设备报警类型
     * @return 系统报警类型
     */
    private AlertType mapToSystemAlertType(String deviceType, String alertType) {
        if ("light".equals(deviceType)) {
            if (alertType.contains("overheat")) {
                return AlertType.DEVICE_OVERHEAT;
            } else {
                return AlertType.LIGHT_ABNORMAL;
            }
        } 
        else if ("ac".equals(deviceType)) {
            if (alertType.contains("temperature")) {
                return AlertType.DEVICE_MALFUNCTION;
            } else {
                return AlertType.AC_ABNORMAL;
            }
        }
        
        // 默认返回设备故障
        return AlertType.DEVICE_MALFUNCTION;
    }
    
    /**
     * 触发设备报警
     * @param device 设备
     * @param statusRecord 状态记录
     */
    private void triggerDeviceAlert(Furniture device, DeviceStatus statusRecord) {
        AlertType alertType;
        String message;
        
        if ("light".equals(statusRecord.type)) {
            alertType = AlertType.LIGHT_ABNORMAL;
            message = String.format("灯具 %s 工作异常，可能存在过热或电路问题", device.getName());
        } 
        else if ("ac".equals(statusRecord.type)) {
            alertType = AlertType.AC_ABNORMAL;
            message = String.format("空调 %s 工作异常，可能存在温控故障", device.getName());
        }
        else {
            alertType = AlertType.DEVICE_MALFUNCTION;
            message = String.format("设备 %s 工作异常", device.getName());
        }
        
        // 触发报警
        triggerAlert(alertType, message);
        
        System.out.printf("[HomeSimulatorAlert] 设备报警: ID=%s, 类型=%s, 消息=%s%n", 
                device.getId(), statusRecord.type, message);
    }
    
    /**
     * 清除设备报警
     * @param device 设备
     * @param statusRecord 状态记录
     */
    private void clearDeviceAlert(Furniture device, DeviceStatus statusRecord) {
        // 只有当当前报警类型是设备相关的报警时才清除
        if (isDeviceAlertType(currentAlertType)) {
            clearAlert();
            
            System.out.printf("[HomeSimulatorAlert] 设备恢复正常: ID=%s, 类型=%s%n", 
                    device.getId(), statusRecord.type);
        }
    }
    
    /**
     * 判断报警类型是否为设备相关报警
     * @param type 报警类型
     * @return 是否为设备相关报警
     */
    private boolean isDeviceAlertType(AlertType type) {
        return type == AlertType.DEVICE_OFFLINE || 
               type == AlertType.DEVICE_MALFUNCTION || 
               type == AlertType.DEVICE_OVERHEAT ||
               type == AlertType.LIGHT_ABNORMAL ||
               type == AlertType.AC_ABNORMAL;
    }
}
