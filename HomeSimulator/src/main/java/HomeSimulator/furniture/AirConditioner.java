package HomeSimulator.furniture;

import HomeSimulator.HomeSimulatorAlert;
import IDL.HomeStatus;
import IDL.HomeStatusDataWriter;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 空调类 - 实现Furniture接口和AlertableDevice接口
 * 合并 feature/alert/9.1 与 master 分支功能
 */
public class AirConditioner implements Furniture, AlertableDevice {
    // ======== 枚举：运行模式 ========
    public enum Mode {
        OFF("off", 0), COOL("cool", 1), HEAT("heat", 2), FAN("fan", 3), AUTO("auto", 4);
        private final String value;
        private final int code;
        Mode(String value, int code) {
            this.value = value;
            this.code = code;
        }
        public String getValue() { return value; }
        public int getCode() { return code; }
        public static Mode fromString(String mode) {
            for (Mode m : Mode.values()) {
                if (m.value.equalsIgnoreCase(mode)) return m;
            }
            return OFF;
        }
    }

    // ======== 基础属性 ========
    private final String id;
    private final String name;
    private final String type = "ac";

    // 状态字段
    private boolean isOn;
    private Mode currentMode;
    private double targetTemperature;
    private double currentTemperature;

    // 功能字段
    private boolean coolingMode;
    private boolean swingMode;
    private boolean dehumidificationMode;
    private int temperature;

    // DDS相关
    private HomeStatusDataWriter ddsWriter;
    private FurnitureManager manager;
    private HomeSimulatorAlert alertSystem; // 报警系统引用
    private final List<StatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>();

    // 报警相关
    private boolean isAbnormal = false;
    private String alertMessage = "";
    private String alertType = "ac_abnormal";
    private final Random random = new Random();
    private int abnormalCounter = 0;
    private static final int ABNORMAL_THRESHOLD = 2;  // 降低阈值，更容易触发报警

    /**
     * 构造函数
     */
    public AirConditioner(String id, String name, HomeStatusDataWriter ddsWriter, FurnitureManager manager) {
        this.id = id;
        this.name = name;
        this.ddsWriter = ddsWriter;
        this.manager = manager;

        this.isOn = false;
        this.currentMode = Mode.OFF;
        this.targetTemperature = 25.0;
        this.currentTemperature = 25.0;
        this.temperature = 25;
        this.coolingMode = false;
        this.swingMode = false;
        this.dehumidificationMode = false;
    }
    
    /**
     * 设置报警系统引用
     * @param alertSystem 报警系统
     */
    public void setAlertSystem(HomeSimulatorAlert alertSystem) {
        this.alertSystem = alertSystem;
    }

    // ======== Furniture接口实现 ========
    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String getType() { return type; }
    @Override public String getStatus() { return isOn ? "on" : "off"; }

    @Override
    public String getStatusDescription() {
        if (!isOn) return getName() + "状态: 关闭";
        return String.format("%s状态: 开启 [温度: %d℃, 制冷: %s, 扫风: %s, 除湿: %s]",
                getName(), temperature,
                coolingMode ? "开启" : "关闭",
                swingMode ? "开启" : "关闭",
                dehumidificationMode ? "开启" : "关闭");
    }

    @Override
    public boolean setStatus(String newStatus) {
        if (!"on".equals(newStatus) && !"off".equals(newStatus)) {
            System.err.println("[AirConditioner] 无效状态: " + newStatus);
            return false;
        }
        boolean oldStatus = isOn;
        isOn = "on".equals(newStatus);
        if (oldStatus != isOn) {
            publishStatus();
            notifyStatusChange(oldStatus ? "on" : "off", newStatus);
        }
        return true;
    }

    public boolean isOn() { return isOn; }
    public void setOn(boolean on) { this.isOn = on; publishStatus(); }
    public boolean isCoolingMode() { return coolingMode; }
    public void setCoolingMode(boolean coolingMode) { this.coolingMode = coolingMode; publishStatus(); }

    public boolean isSwingMode() { return swingMode; }
    public void setSwingMode(boolean swingMode) { this.swingMode = swingMode; publishStatus(); }

    public boolean isDehumidificationMode() { return dehumidificationMode; }
    public void setDehumidificationMode(boolean dehumidificationMode) { this.dehumidificationMode = dehumidificationMode; publishStatus(); }

    public int getModeCode() {
        return currentMode.getCode();
    }

    public int getSwingModeCode() {
        return swingMode ? 1 : 0;
    }

    public int getDehumidifyModeCode() {
        return dehumidificationMode ? 1 : 0;
    }


    public int getTemperature() { return temperature; }
    public void setTemperature(int temperature) {
        // 限制温度范围16-30℃
        this.temperature = Math.max(16, Math.min(30, temperature));
        publishStatus();
    }

    @Override public void addStatusChangeListener(StatusChangeListener l) { statusChangeListeners.add(l); }
    @Override public void removeStatusChangeListener(StatusChangeListener l) { statusChangeListeners.remove(l); }

    // ======== 状态更新 ========
    public void publishStatus() {
        if (ddsWriter == null) {
            System.err.println("[AirConditioner] DDS写入器未初始化，无法上报状态");
            return;
        }
        try {
            HomeStatus status = new HomeStatus();
            int acIndex = getAcIndex();
            status.timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            JSONObject statusJson = new JSONObject();
            JSONArray statusArray = new JSONArray();
            statusArray.put(isOn ? 1 : 0);
            statusArray.put(coolingMode ? 1 : 0);
            statusArray.put(swingMode ? 1 : 0);
            statusArray.put(dehumidificationMode ? 1 : 0);
            statusJson.put("status", statusArray);

            JSONArray paramsArray = new JSONArray();
            paramsArray.put(temperature);
            statusJson.put("params", paramsArray);

            if (status.deviceStatus.length() <= acIndex) {
                status.deviceStatus.ensure_length(acIndex + 1, acIndex + 1);
            }
            status.deviceStatus.set_at(acIndex, statusJson.toString());

            if (status.deviceIds.length() <= acIndex) {
                status.deviceIds.ensure_length(acIndex + 1, acIndex + 1);
                status.deviceTypes.ensure_length(acIndex + 1, acIndex + 1);
            }
            status.deviceIds.set_at(acIndex, this.id);
            status.deviceTypes.set_at(acIndex, this.type);

            ReturnCode_t result = ddsWriter.write(status, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (result == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[AirConditioner] %s状态上报: %s%n", getName(), statusJson.toString());
                if (manager != null) {
                    manager.updateDeviceStatus(getId()); // 同步更新全局状态
                }
            } else {
                System.err.printf("[AirConditioner] 上报失败，返回码: %s%n", result);
            }
        } catch (Exception e) {
            System.err.println("[AirConditioner] 上报异常: " + e.getMessage());
        }
    }

    private int getAcIndex() {
        try {
            String numericPart = getId().replaceAll("[^0-9]", "");
            return Integer.parseInt(numericPart) - 1;
        } catch (NumberFormatException e) {
            System.err.println("[AirConditioner] 空调ID格式错误: " + getId());
            return -1;
        }
    }

    public void notifyStatusChange(String oldStatus, String newStatus) {
        for (StatusChangeListener l : statusChangeListeners) {
            l.onStatusChanged(getId(), oldStatus, newStatus);
        }
    }

    // ======== AlertableDevice接口实现 ========
    public void updateCurrentTemperature() {
        if (currentMode == Mode.OFF) {
            currentTemperature += (28.0 - currentTemperature) * 0.1;
        } else if (currentMode == Mode.COOL) {
            currentTemperature -= 0.5 + random.nextDouble() * 0.3;
            if (currentTemperature < targetTemperature) currentTemperature = targetTemperature;
        } else if (currentMode == Mode.HEAT) {
            currentTemperature += 0.5 + random.nextDouble() * 0.3;
            if (currentTemperature > targetTemperature) currentTemperature = targetTemperature;
        }
    }

    @Override
    public boolean checkAbnormal() {
        updateCurrentTemperature();
        // 原有的概率检测逻辑已移除，改为手动触发
        return isAbnormal;
    }
    
    /**
     * 手动触发空调温度异常报警
     */
    public void triggerTemperatureAbnormalAlert() {
        if (!isAbnormal) {
            isAbnormal = true;
            alertType = "ac_temperature_abnormal";
            alertMessage = String.format("空调 %s 温度异常: 当前%.1f℃, 目标%.1f℃",
                    name, currentTemperature, targetTemperature);
            
            System.out.printf("[AirConditioner] 手动触发异常: ID=%s, 类型=%s, 消息=%s%n", 
                    id, alertType, alertMessage);
            
            if (alertSystem != null) {
                alertSystem.receiveDeviceAlert(id, type, alertType, alertMessage);
            }

            // ======== 添加自动重置逻辑（5秒后恢复正常） ========
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 延时5秒（可根据需求调整）
                    if (isAbnormal) { // 确保未被手动重置过
                        resetAlert();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 保留中断状态
                }
            }).start();

        }
    }
    
    /**
     * 手动触发空调性能异常报警
     */
    public void triggerPerformanceAbnormalAlert() {
        if (!isAbnormal) {
            isAbnormal = true;
            alertType = "ac_performance_abnormal";
            alertMessage = String.format("空调 %s %s效果异常，可能需要维修",
                    name, currentMode == Mode.COOL ? "制冷" : "制热");
            
            System.out.printf("[AirConditioner] 手动触发异常: ID=%s, 类型=%s, 消息=%s%n", 
                    id, alertType, alertMessage);
            
            if (alertSystem != null) {
                alertSystem.receiveDeviceAlert(id, type, alertType, alertMessage);
            }

            // ======== 添加自动重置逻辑（5秒后恢复正常） ========
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 延时5秒（可根据需求调整）
                    if (isAbnormal) { // 确保未被手动重置过
                        resetAlert();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 保留中断状态
                }
            }).start();

        }
    }

    @Override public String getAlertMessage() { return alertMessage; }
    @Override public String getAlertType() { return alertType; }
    @Override public void resetAlert() {
        isAbnormal = false;
        alertMessage = "";
        abnormalCounter = 0;
        System.out.printf("[AirConditioner] 空调 %s 恢复正常%n", name);
        
        // 通知报警系统清除报警
        if (alertSystem != null) {
            alertSystem.clearDeviceAlert(id);
        }
    }
    @Override public String getDeviceId() { return id; }
    @Override public String getDeviceType() { return "ac"; }
}
