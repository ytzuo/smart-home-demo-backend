package HomeSimulator;

import HomeSimulator.furniture.*;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.publication.Publisher;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.infrastructure.DurabilityQosPolicyKind;
import com.zrdds.infrastructure.ReliabilityQosPolicyKind;
import com.zrdds.infrastructure.HistoryQosPolicyKind;
import com.zrdds.topic.Topic;
import IDL.HomeStatus;
import IDL.HomeStatusDataWriter;
import IDL.Alert;
import IDL.AlertDataWriter;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.HashSet;
import java.util.Set;

/**
 * 家居报警系统
 * 负责监测和发布家庭安全警报
 */
public class HomeSimulatorAlert {
    private int alert_id;
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
    private Topic alertTopic;
    private AlertDataWriter alertWriter;
    private AtomicBoolean alertActive;
    private AlertType currentAlertType;
    private String alertMessage;
    private ScheduledExecutorService alertReporter;
    private List<AlertListener> listeners;
    private Set<AlertType> activeAlerts;
    
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
     * @param alertTopic 报警主题
     */
    public HomeSimulatorAlert(Publisher publisher, Topic homeStatusTopic, Topic alertTopic) {
        this.publisher = publisher;
        this.homeStatusTopic = homeStatusTopic;
        this.alertTopic = alertTopic;
        this.alertActive = new AtomicBoolean(false);
        this.currentAlertType = AlertType.NONE;
        this.alertMessage = "";
        this.listeners = new ArrayList<>();
        this.deviceStatusMap = new HashMap<>();
        this.activeAlerts = new HashSet<>();
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

        // 创建Alert数据写入器，配置与手机端匹配的QoS
        DataWriterQos dwQos = new DataWriterQos();
        publisher.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;
        
        alertWriter = (AlertDataWriter) publisher.create_datawriter(
                alertTopic, 
                dwQos, 
                null, 
                0);
        
        if (alertWriter == null) {
            System.err.println("[HomeSimulatorAlert] 创建Alert数据写入器失败");
            return;
        }
                
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
     * @param报警信息
     */
    int return_alertid(AlertType type) {
        return this.alert_id;
    }
    public void triggerAlert(AlertType type, String message) {
        if (type == AlertType.NONE) {
            return;
        }

        this.currentAlertType = type;
        this.alertMessage = message;
        this.alertActive.set(true);
        // 获取报警类型对应的alertId
        getAlertIdByType(type);
        int alertId = this.alert_id;
        System.out.printf("[HomeSimulatorAlert] 触发报警: 类型=%s, 信息=%s%n",
                type.getValue(), message);

        // 立即报告状态
        reportAlertStatus();

        // 通知监听器
        notifyListeners();

        // 新增：发送警报的同时发送图片
        try {

            // 尝试获取与报警相关的设备ID（从message中提取）
            String deviceId = "system";
            String deviceType = "system";
            if (message.contains("light1")) {
                deviceId = "light1";
                deviceType = "light";
            } else if (message.contains("light2")) {
                deviceId = "light2";
                deviceType = "light";
            } else if (message.contains("ac1")) {
                deviceId = "ac1";
                deviceType = "ac";
            } else if (message.contains("ac2")) {
                deviceId = "ac2";
                deviceType = "ac";
            }

            // 媒体类型：1表示图片
            int mediaType = 1;

            // 这里应该是获取实际图片数据的逻辑
            // 由于当前系统没有实际的摄像头，我们可以模拟获取一张与报警类型相关的图片
            byte[] mediaData = getSampleImageData(type);

            // 发送媒体数据
            HomeSimulator.getInstance().sendMedia(deviceId, "camera", mediaType, mediaData, alertId);
            System.out.printf("[HomeSimulatorAlert] 已发送与报警关联的图片，设备ID: %s%n", deviceId);
        } catch (Exception e) {
            // 如果发送媒体失败，不影响报警的正常触发
            System.err.println("[HomeSimulatorAlert] 发送媒体数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取示例图片数据
     * 注意：在实际应用中，这里应该从摄像头或文件系统获取真实的图片数据
     */
    private byte[] getSampleImageData(AlertType alertType) {
        try {
            // 这里应该是根据报警类型获取不同的图片
            // 由于没有实际的图片资源，我们返回一个简单的字节数组作为示例
            // 在实际应用中，应该读取真实的图片文件
            String sampleImagePath = getImagePathForAlertType(alertType);
            File imageFile = new File(sampleImagePath);

            if (imageFile.exists() && imageFile.isFile()) {
                byte[] data = new byte[(int) imageFile.length()];
                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    fis.read(data);
                }
                return data;
            } else {
                // 如果文件不存在，返回一个默认的图片数据或空数据
                System.out.println("[HomeSimulatorAlert] 未找到图片文件: " + sampleImagePath);
                // 返回一个简单的示例数据
                return new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A}; // PNG文件头
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulatorAlert] 获取图片数据失败: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 根据报警类型获取对应的图片路径
     */
    private String getImagePathForAlertType(AlertType alertType) {
        // 在实际应用中，应该根据不同的报警类型返回不同的图片路径
        // 这里只是一个示例实现
        switch (alertType) {
            case FIRE:
                return "C:\\Users\\86183\\Pictures\\90.jpg";
            case INTRUSION:
                return "C:\\Users\\86183\\Pictures\\90.jpg";
            case GAS_LEAK:
                return "C:\\Users\\86183\\Pictures\\90.jpg";
            case WATER_LEAK:
                return "C:\\Users\\86183\\Pictures\\90.jpg";
            case DEVICE_OVERHEAT:
                return "C:\\Users\\86183\\Pictures\\90.jpg";
            default:
                return "C:\\Users\\86183\\Pictures\\90.jpg";
        }
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
     * @return 格式化的时间字符串
     */
    private String getCurrentTimeStamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
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

        // 直接发布Alert消息到手机端，使用正确的设备ID和类型
        publishDeviceAlertMessage(deviceId, deviceType, systemAlertType, alertMessage, true);

        System.out.printf("[HomeSimulatorAlert] 收到设备报警: ID=%s, 类型=%s, 消息=%s%n",
                deviceId, alertType, alertMessage);
    }

    /**
     * 接收来自家具设备的独立报警
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param alertType 报警类型
     * @param message 报警消息
     */
    public void receiveDeviceAlert(String deviceId, String deviceType, String alertType, String message) {
        System.out.printf("[HomeSimulatorAlert] 收到独立设备报警: ID=%s, 类型=%s, 报警=%s, 消息=%s%n",
                deviceId, deviceType, alertType, message);

        // 映射到系统报警类型
        AlertType systemAlertType = mapToSystemAlertType(deviceType, alertType);

        // 构建完整的报警消息
        String fullMessage = String.format("设备 %s: %s", deviceId, message);

        // 触发系统报警
        triggerAlert(systemAlertType, fullMessage);

        // 直接发布Alert消息到手机端，使用正确的设备ID和类型
        publishDeviceAlertMessage(deviceId, deviceType, systemAlertType, fullMessage, true);
    }

    /**
     * 清除特定设备的报警
     * @param deviceID 设备ID
     */
    public void clearDeviceAlert(String deviceID) {
        System.out.printf("[HomeSimulatorAlert] 清除设备报警: ID=%s%n", deviceID);

        // 检查当前是否有设备相关的报警，并清除
        if (alertActive.get() && isDeviceAlertType(currentAlertType)) {
            clearAlert();
            System.out.printf("[HomeSimulatorAlert] 已清除设备相关报警: ID=%s%n", deviceID);
        }
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
     * 发布Alert消息到手机端
     * @param type 报警类型
     * @param message 报警消息
     * @param isActive 是否激活报警
     */
    private void publishAlertMessage(AlertType type, String message, boolean isActive) {
        if (alertWriter != null) {
            try {
                Alert alert = new Alert();

                // 从消息中提取设备ID和类型
                String deviceId = "system";
                String deviceType = "system";

                if (message.contains("light1") || message.contains("灯具 light1")) {
                    deviceId = "light1";
                    deviceType = "light";
                } else if (message.contains("light2") || message.contains("灯具 light2")) {
                    deviceId = "light2";
                    deviceType = "light";
                } else if (message.contains("ac1") || message.contains("空调 ac1")) {
                    deviceId = "ac1";
                    deviceType = "ac";
                } else if (message.contains("ac2") || message.contains("空调 ac2")) {
                    deviceId = "ac2";
                    deviceType = "ac";
                } else if (message.contains("灯具")) {
                    deviceId = "light1";
                    deviceType = "light";
                } else if (message.contains("空调")) {
                    deviceId = "ac1";
                    deviceType = "ac";
                } else {
                    deviceId = "system";
                    deviceType = "system";
                }

                publishDeviceAlertMessage(deviceId, deviceType, type, message, isActive);
            } catch (Exception e) {
                System.err.println("[HomeSimulatorAlert] 发布Alert消息失败: " + e.getMessage());
            }
        }
    }

    // 新增：根据报警类型获取alertId的辅助方法
    private void getAlertIdByType(AlertType type) {
        this.alert_id = (int) (System.currentTimeMillis() % 1000000); // 生成唯一报警ID
    }
    /**
     * 直接发布设备Alert消息到手机端
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param type 报警类型
     * @param message 报警消息
     * @param isActive 是否激活报警
     */
    private void publishDeviceAlertMessage(String deviceId, String deviceType, AlertType type, String message, boolean isActive) {
        if (alertWriter != null) {
            try {
                Alert alert = new Alert();

                alert.deviceId = deviceId;
                alert.deviceType = deviceType;

                // 根据报警类型设置对应的alert_id，与手机端匹配
                //int alertId = getAlertIdByType(type); // 使用与图片发送相同的alertId // 默认设备故障

                alert.alert_id = return_alertid(type);

                alert.level = isActive ? "ALERT" : "INFO";
                alert.description = message;
                alert.timeStamp = getCurrentTimeStamp();

                // 调试信息：打印即将发送的Alert对象详情
                System.out.printf("[HomeSimulatorAlert] 📤 准备发送Alert消息 - Topic: Alert, deviceId: %s, deviceType: %s, alert_id: %d, level: %s, description: %s%n",
                        alert.deviceId, alert.deviceType, alert.alert_id, alert.level, alert.description);

                InstanceHandle_t handle = alertWriter.register_instance(alert);
                ReturnCode_t result = alertWriter.write(alert, handle);

                if (result == ReturnCode_t.RETCODE_OK) {
                    System.out.printf("[HomeSimulatorAlert] ✅ 成功发布Alert消息到手机端 - 设备: %s, 类型: %s, 消息: %s, 状态: %s, alert_id: %d, DDS返回码: %s%n",
                            deviceId, deviceType, message, isActive ? "激活" : "解除", alert.alert_id, result.toString());
                } else {
                    System.err.printf("[HomeSimulatorAlert] ❌ 发布Alert消息失败 - DDS返回码: %s%n", result.toString());
                }
            } catch (Exception e) {
                System.err.println("[HomeSimulatorAlert] 发布Alert消息异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("[HomeSimulatorAlert] 警告: alertWriter为null，无法发布Alert消息");
        }
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
            message = String.format("灯具 %s 状态异常，可能存在电路问题", device.getName());
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

        // 直接发布Alert消息到手机端，使用正确的设备ID和类型
        publishDeviceAlertMessage(device.getId(), statusRecord.type, alertType, message, true);
        
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
            String message = String.format("设备 %s 已恢复正常", device.getName());
            
            // 发布Alert消息到手机端
            publishAlertMessage(currentAlertType, message, false);
            
            clearAlert();
            
            System.out.printf("[HomeSimulatorAlert] 设备恢复正常: ID=%s, 类型=%s%n", 
                    device.getId(), statusRecord.type);
        }
    }
//    // 在HomeSimulatorAlert.java中的适当位置添加
//    public void triggerAlertWithMedia(String deviceId, AlertType alertType, byte[] mediaData, int mediaType) {
//        // 触发报警
//        triggerAlert(alertType, deviceId);
//
//        // 发送相关媒体
//        HomeSimulator.getInstance().sendMedia(deviceId, "camera", mediaType, mediaData);
//    }
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
