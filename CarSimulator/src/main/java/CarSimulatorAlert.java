import CarSimulator.DDS.DdsParticipant;
import IDL.Alert;
import IDL.AlertDataWriter;
import IDL.AlertTypeSupport;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CarSimulatorAlert {
    private static final String ALERT_TOPIC = "CarAlert";
    
    private AlertDataWriter alertWriter;
    private DdsParticipant ddsParticipant;
    private Alert alert;
    private boolean isRunning = false;
    private ScheduledExecutorService alertChecker;
    
    // 车辆状态监控
    private CarSimulator carSimulator;
    
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
    
    public CarSimulatorAlert() {
        this.alert = new Alert();
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
        
        System.out.println("[CarSimulatorAlert] 车辆报警系统初始化完成");
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
        }
        
        // 检查车门未锁
        if (!carSimulator.isDoorsLocked() && carSimulator.isEngineOn()) {
            triggerAlert(CarAlertType.DOOR_UNLOCKED, 
                "车门未锁，请检查所有车门");
        }
        
        // 检查发动机过热（模拟）
        if (carSimulator.isEngineOn() && Math.random() < 0.01) {
            triggerAlert(CarAlertType.ENGINE_OVERHEAT, 
                "发动机过热，请立即停车检查");
        }
    }
    
    public void triggerAlert(CarAlertType alertType, String message) {
        if (alertWriter == null) {
            System.err.println("[CarSimulatorAlert] AlertDataWriter未初始化");
            return;
        }
        
        try {
            // 填充报警信息
            alert.deviceId = "car_001";
            alert.deviceType = "car";
            alert.alert_id = alertType.getAlertId();
            alert.level = "ALERT"; // 中等级别
            alert.description = message;
            alert.timeStamp = getCurrentTimeStamp();
            
            // 发布报警
            alertWriter.write(alert, InstanceHandle_t.HANDLE_NIL_NATIVE);
            
            System.out.printf("[CarSimulatorAlert] 报警已发送: %s - %s%n", 
                alertType.getDescription(), message);
                
        } catch (Exception e) {
            System.err.println("[CarSimulatorAlert] 发送报警失败: " + e.getMessage());
        }
    }
    
    public void clearAlert(int alertId) {
        if (alertWriter == null) {
            return;
        }
        
        try {
            alert.deviceId = "car_001";
            alert.deviceType = "car";
            alert.alert_id = alertId;
            alert.level = "INFO"; // 清除报警
            alert.description = "报警已清除";
            alert.timeStamp = getCurrentTimeStamp();
            
            alertWriter.write(alert, InstanceHandle_t.HANDLE_NIL_NATIVE);
            
            System.out.printf("[CarSimulatorAlert] 报警已清除: %d%n", alertId);
            
        } catch (Exception e) {
            System.err.println("[CarSimulatorAlert] 清除报警失败: " + e.getMessage());
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