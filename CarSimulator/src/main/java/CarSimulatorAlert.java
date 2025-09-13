import CarSimulator.DDS.DdsParticipant;
import CarSimulator.DDS.MediaPublisher;
import IDL.Alert;
import IDL.AlertDataWriter;
import IDL.AlertMediaTypeSupport;
import IDL.AlertTypeSupport;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.topic.Topic;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.FileInputStream;

public class CarSimulatorAlert {
    private static final String ALERT_TOPIC = "Alert";
    // 修改ALERT_MEDIA_TOPIC为AlertMedia
    private static final String ALERT_MEDIA_TOPIC = "AlertMedia";
    private AlertDataWriter alertWriter;
    private DdsParticipant ddsParticipant;
    private Alert alert;
    private boolean isRunning = false;
    private ScheduledExecutorService alertChecker;
    private Set<CarAlertType> activeAlerts;

    // 新增：媒体发布器相关成员变量
    private MediaPublisher mediaPublisher;
    private Topic alertMediaTopic;
    // 车辆状态监控
    private CarSimulator carSimulator;
    // 新增：存储当前报警ID的成员变量
    private int alert_id;

    public enum CarAlertType {
            LOW_FUEL(1, "燃油不足"),
            ENGINE_OVERHEAT(2, "发动机过热"),
            DOOR_UNLOCKED(3, "车门未锁");
        
        private final int alertId;
        private final String description;
        
        CarAlertType(int alertId, String description) {
            this.alertId = alertId;
            this.description = description;
        }
        
        public int getAlertId() {
            return alertId;
        }
        
        public String getDescription() {
            return description;
        }
    }
    // 添加获取当前alertId的方法
    int return_alertid() {
        return this.alert_id;
    }
    // 新增：根据报警类型获取alertId的辅助方法
    private void getAlertIdByType(CarAlertType type) {
        this.alert_id = (int) (System.currentTimeMillis() % 1000000); // 生成唯一报警ID
        System.out.printf("[CarSimulatorAlert] 生成报警ID: %d, 类型: %s\n", this.alert_id, type.getDescription());
    }
    public CarSimulatorAlert() {
        this.alert = new Alert();
        this.activeAlerts = new HashSet<>();
    }
    
    public void initialize(DdsParticipant ddsParticipant, CarSimulator carSimulator) {
        this.ddsParticipant = ddsParticipant;
        this.carSimulator = carSimulator;
        
        // 注册报警类型
        AlertTypeSupport.get_instance().register_type(
            ddsParticipant.getDomainParticipant(), "Alert");
        
        // 创建报警主题
        Topic alertTopic = ddsParticipant.createTopic(
            ALERT_TOPIC, AlertTypeSupport.get_instance());
        
        // 配置DataWriter QoS
        DataWriterQos dwQos = new DataWriterQos();
        ddsParticipant.getPublisher().get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;
        
        // 创建DataWriter
        alertWriter = (AlertDataWriter) ddsParticipant.getPublisher().create_datawriter(
            alertTopic, dwQos, null, StatusKind.STATUS_MASK_NONE);
            
        if (alertWriter == null) {
            System.err.println("[CarSimulatorAlert] 创建AlertDataWriter失败");
            return;
        }

        // 新增：初始化媒体发布器
        initializeMediaPublisher();

        System.out.println("[CarSimulatorAlert] 车辆报警系统初始化完成");
    }

    // 新增：初始化媒体发布器
    private void initializeMediaPublisher() {
        try {
            // 注册AlertMedia类型
            AlertMediaTypeSupport.get_instance().register_type(
                    ddsParticipant.getDomainParticipant(), "AlertMedia");

            // 创建AlertMedia主题
            alertMediaTopic = ddsParticipant.createTopic(
                    ALERT_MEDIA_TOPIC, AlertMediaTypeSupport.get_instance());

            // 初始化MediaPublisher
            mediaPublisher = new MediaPublisher();
            boolean started = mediaPublisher.start(ddsParticipant.getPublisher(), alertMediaTopic);

            if (started) {
                System.out.println("[CarSimulatorAlert] 媒体发布器初始化成功");
            } else {
                System.err.println("[CarSimulatorAlert] 媒体发布器初始化失败");
            }
        } catch (Exception e) {
            System.err.println("[CarSimulatorAlert] 初始化媒体发布器时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void startMonitoring() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        alertChecker = Executors.newSingleThreadScheduledExecutor();
        
        // 每10秒检查一次车辆状态
        alertChecker.scheduleWithFixedDelay(() -> {
            if (isRunning) {
                checkCarStatus();
            }
        }, 0, 10, TimeUnit.SECONDS);
        
        System.out.println("[CarSimulatorAlert] 车辆状态监控已启动");
    }
    
    public void stopMonitoring() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        if (alertChecker != null) {
            alertChecker.shutdown();
            try {
                if (!alertChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    alertChecker.shutdownNow();
                }
            } catch (InterruptedException e) {
                alertChecker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("[CarSimulatorAlert] 车辆状态监控已停止");
    }
    
    private void checkCarStatus() {
        if (carSimulator == null) {
            return;
        }
        
        // 检查低油量
        if (carSimulator.getFuelPercent() < 20.0f) {
            triggerAlert(CarAlertType.LOW_FUEL, 
                String.format("燃油不足，当前油量：%.1f%%", carSimulator.getFuelPercent()));
        } else {
            clearAlert(CarAlertType.LOW_FUEL.getAlertId());
        }
        
        // 检查车门未锁
        if (!carSimulator.isDoorsLocked() && carSimulator.isEngineOn()) {
            triggerAlert(CarAlertType.DOOR_UNLOCKED, 
                "车门未锁，请检查所有车门");
        } else {
            clearAlert(CarAlertType.DOOR_UNLOCKED.getAlertId());
        }
        
        // 检查发动机过热（模拟）
        if (carSimulator.isEngineOn() && Math.random() < 0.01) {
            triggerAlert(CarAlertType.ENGINE_OVERHEAT, 
                "发动机过热，请立即停车检查");
        } else {
            clearAlert(CarAlertType.ENGINE_OVERHEAT.getAlertId());
        }
    }
    
    public void triggerAlert(CarAlertType alertType, String message) {
        if (alertWriter == null) {
            System.err.println("[CarSimulatorAlert] AlertDataWriter未初始化");
            return;
        }

        if (activeAlerts.contains(alertType)) {
            // 报警已存在，不重复发送
            return;
        }
        
        try {
            // 生成报警ID
            getAlertIdByType(alertType);
            int alertId = this.alert_id;
            // 填充报警信息
            alert.deviceId = "car_001";
            alert.deviceType = "car";
            alert.alert_id = alertId;
            alert.level = "ALERT"; // 中等级别
            alert.description = message;
            alert.timeStamp = getCurrentTimeStamp();
            
            // 发布报警
            alertWriter.write(alert, InstanceHandle_t.HANDLE_NIL_NATIVE);
            activeAlerts.add(alertType); // 添加到活动报警列表
            
            System.out.printf("[CarSimulatorAlert] 报警已发送: %s - %s%n", 
                alertType.getDescription(), message);
            System.out.printf("[CarSimulatorAlert] 🔢  生成报警ID: %d, this.alert_id: %d%n", alertId, this.alert_id);
            // 新增：发送报警的同时发送图片
            try {
                String deviceId = "car_001";
                String deviceType = "car";
                // 媒体类型：1表示图片
                int mediaType = 1;

                // 获取与报警类型相关的图片数据
                byte[] mediaData = getSampleImageData(alertType);

                // 发送媒体数据
                sendMedia(deviceId, deviceType, mediaType, mediaData, alertId);
            } catch (Exception e) {
                // 如果发送媒体失败，不影响报警的正常触发
                System.err.println("[CarSimulatorAlert] 发送媒体数据失败: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("[CarSimulatorAlert] 发送报警失败: " + e.getMessage());
        }
    }

    // 新增：发送媒体数据的方法
    private boolean sendMedia(String deviceId, String deviceType, int mediaType, byte[] fileData, int alertId) {
        if (mediaPublisher != null) {
            return mediaPublisher.publishMedia(deviceId, deviceType, mediaType, fileData, alertId);
        }
        return false;
    }

    // 新增：获取示例图片数据
    private byte[] getSampleImageData(CarAlertType alertType) {
        try {
            // 根据报警类型获取对应的图片路径
            String sampleImagePath = getImagePathForAlertType(alertType);
            File imageFile = new File(sampleImagePath);

            if (imageFile.exists() && imageFile.isFile()) {
                byte[] data = new byte[(int) imageFile.length()];
                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    fis.read(data);
                }
                return data;
            } else {
                // 如果文件不存在，返回一个默认的图片数据
                System.out.println("[CarSimulatorAlert] 未找到图片文件: " + sampleImagePath);
                // 返回一个简单的示例数据（PNG文件头）
                return new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
            }
        } catch (Exception e) {
            System.err.println("[CarSimulatorAlert] 获取图片数据失败: " + e.getMessage());
            return new byte[0];
        }
    }

    // 新增：根据报警类型获取对应的图片路径
    private String getImagePathForAlertType(CarAlertType alertType) {
        // 这里只是一个示例实现，实际应用中应该根据不同的报警类型返回不同的图片路径
        // 请根据实际环境修改图片路径
        String basePath = "C:\\Users\\Xiao_Chen\\Pictures\\image_IO\\";

        switch (alertType) {
            case LOW_FUEL:
                return basePath + "testImage.jpg";
            case ENGINE_OVERHEAT:
                return basePath + "testImage.jpg";
            case DOOR_UNLOCKED:
                return basePath + "testImage.jpg";
            default:
                return basePath + "testImage.jpg";
        }
    }

    public void clearAlert(int alertId) {
        if (alertWriter == null) {
            return;
        }

        CarAlertType typeToClear = null;
        for (CarAlertType type : CarAlertType.values()) {
            if (type.getAlertId() == alertId) {
                typeToClear = type;
                break;
            }
        }

        if (typeToClear != null && activeAlerts.contains(typeToClear)) {
            try {
                // 生成新的alertId用于清除报警消息
                getAlertIdByType(typeToClear);
                alert.deviceId = "car_001";
                alert.deviceType = "car";
                alert.alert_id = alertId;
                alert.level = "INFO"; // 清除报警
                alert.description = "报警已清除";
                alert.timeStamp = getCurrentTimeStamp();

                activeAlerts.remove(typeToClear); // 从活动报警列表移除
                
                System.out.printf("[CarSimulatorAlert] 报警已清除: %d%n", alertId);
                
            } catch (Exception e) {
                System.err.println("[CarSimulatorAlert] 清除报警失败: " + e.getMessage());
            }
        }
    }
    
    private String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
    
    public void close() {
        stopMonitoring();
        if (alertWriter != null) {
            ddsParticipant.getPublisher().delete_datawriter(alertWriter);
        }
    }
}