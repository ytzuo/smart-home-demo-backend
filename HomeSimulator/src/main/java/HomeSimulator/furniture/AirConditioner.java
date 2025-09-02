package HomeSimulator.furniture;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 空调类 - 实现Furniture接口和AlertableDevice接口
 */
public class AirConditioner implements Furniture, AlertableDevice {
    public enum Mode {
        OFF("off"),
        COOL("cool"),
        HEAT("heat"),
        FAN("fan"),
        AUTO("auto");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Mode fromString(String mode) {
            for (Mode m : Mode.values()) {
                if (m.value.equalsIgnoreCase(mode)) {
                    return m;
                }
            }
            return OFF;
        }
    }

    private final String id;
    private final String name;
    private Mode currentMode;
    private double targetTemperature;
    private final List<StatusChangeListener> listeners;
    
    // 报警相关属性
    private boolean isAbnormal = false;
    private String alertMessage = "";
    private String alertType = "ac_abnormal";
    private final Random random = new Random();
    private int abnormalCounter = 0;
    private static final int ABNORMAL_THRESHOLD = 3;
    private double currentTemperature;

    public AirConditioner(String id, String name) {
        this.id = id;
        this.name = name;
        this.currentMode = Mode.OFF;
        this.targetTemperature = 25.0;
        this.currentTemperature = 25.0;
        this.listeners = new ArrayList<>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return "ac";
    }

    @Override
    public String getStatus() {
        return currentMode.getValue();
    }

    @Override
    public boolean setStatus(String status) {
        String oldStatus = getStatus();
        Mode newMode = Mode.fromString(status);
        
        if (this.currentMode != newMode) {
            this.currentMode = newMode;
            notifyStatusChanged(oldStatus, getStatus());
            return true;
        }
        return false;
    }

    @Override
    public String getStatusDescription() {
        if (currentMode == Mode.OFF) {
            return String.format("空调 %s: 关闭", name);
        } else {
            return String.format("空调 %s: %s模式, 目标温度 %.1f°C", 
                    name, currentMode.getValue(), targetTemperature);
        }
    }

    @Override
    public void addStatusChangeListener(StatusChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeStatusChangeListener(StatusChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyStatusChanged(String oldStatus, String newStatus) {
        for (StatusChangeListener listener : listeners) {
            listener.onStatusChanged(id, oldStatus, newStatus);
        }
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public double getTargetTemperature() {
        return targetTemperature;
    }

    public void setTargetTemperature(double temperature) {
        if (temperature >= 16.0 && temperature <= 30.0) {
            this.targetTemperature = temperature;
            System.out.printf("[HomeSimulator] 空调 %s 目标温度设置为 %.1f°C%n", name, temperature);
        }
    }
    
    public double getCurrentTemperature() {
        return currentTemperature;
    }
    
    public void updateCurrentTemperature() {
        if (currentMode == Mode.OFF) {
            // 空调关闭时，温度缓慢向室温靠近
            double roomTemp = 28.0; // 假设室温28度
            currentTemperature += (roomTemp - currentTemperature) * 0.1;
        } else if (currentMode == Mode.COOL) {
            // 制冷模式
            currentTemperature -= 0.5 + random.nextDouble() * 0.3;
            if (currentTemperature < targetTemperature) {
                currentTemperature = targetTemperature;
            }
        } else if (currentMode == Mode.HEAT) {
            // 制热模式
            currentTemperature += 0.5 + random.nextDouble() * 0.3;
            if (currentTemperature > targetTemperature) {
                currentTemperature = targetTemperature;
            }
        }
    }
    
    // ======== 实现AlertableDevice接口方法 ========
    @Override
    public boolean checkAbnormal() {
        // 更新当前温度
        updateCurrentTemperature();
        
        // 检测逻辑1：温度异常波动
        boolean tempAbnormal = false;
        if (currentMode != Mode.OFF) {
            double tempDiff = Math.abs(currentTemperature - targetTemperature);
            tempAbnormal = tempDiff > 5.0 && random.nextDouble() < 0.1; // 温差大且10%概率异常
        }
        
        // 检测逻辑2：制冷/制热效果异常
        boolean performanceAbnormal = false;
        if (currentMode == Mode.COOL && currentTemperature > targetTemperature + 3) {
            performanceAbnormal = random.nextDouble() < 0.15; // 15%概率制冷效果异常
        } else if (currentMode == Mode.HEAT && currentTemperature < targetTemperature - 3) {
            performanceAbnormal = random.nextDouble() < 0.15; // 15%概率制热效果异常
        }
        
        // 如果检测到异常
        if (tempAbnormal || performanceAbnormal) {
            abnormalCounter++;
            
            // 连续异常达到阈值时触发报警
            if (abnormalCounter >= ABNORMAL_THRESHOLD && !isAbnormal) {
                isAbnormal = true;
                
                // 设置报警信息
                if (tempAbnormal) {
                    alertType = "ac_temperature_abnormal";
                    alertMessage = String.format("空调 %s 温度异常波动，当前温度%.1f℃，目标温度%.1f℃", 
                            name, currentTemperature, targetTemperature);
                } else {
                    alertType = "ac_performance_abnormal";
                    alertMessage = String.format("空调 %s %s效果异常，可能需要维修", 
                            name, currentMode == Mode.COOL ? "制冷" : "制热");
                }
                
                System.out.printf("[AirConditioner] 检测到异常: ID=%s, 类型=%s, 消息=%s%n", 
                        id, alertType, alertMessage);
                return true;
            }
        } else {
            // 正常状态，重置计数器
            if (abnormalCounter > 0) {
                abnormalCounter = 0;
                
                // 如果之前有报警，现在恢复正常
                if (isAbnormal) {
                    resetAlert();
                }
            }
        }
        
        return isAbnormal;
    }
    
    @Override
    public String getAlertMessage() {
        return alertMessage;
    }
    
    @Override
    public String getAlertType() {
        return alertType;
    }
    
    @Override
    public void resetAlert() {
        isAbnormal = false;
        alertMessage = "";
        abnormalCounter = 0;
        System.out.printf("[AirConditioner] 空调 %s 恢复正常%n", name);
    }
    
    @Override
    public String getDeviceId() {
        return id;
    }
    
    @Override
    public String getDeviceType() {
        return "ac";
    }

    public void turnOn(Mode mode) {
        if (mode != Mode.OFF) {
            this.currentMode = mode;
            notifyStatusChanged("off", mode.getValue());
        }
    }

    public void turnOff() {
        setStatus("off");
    }

    public boolean isOn() {
        return currentMode != Mode.OFF;
    }
}