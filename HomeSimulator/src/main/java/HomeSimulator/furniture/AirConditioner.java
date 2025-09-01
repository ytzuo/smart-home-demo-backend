package HomeSimulator.furniture;

import java.util.ArrayList;
import java.util.List;

/**
 * 空调类 - 实现Furniture接口
 */
public class AirConditioner implements Furniture {
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

    public AirConditioner(String id, String name) {
        this.id = id;
        this.name = name;
        this.currentMode = Mode.OFF;
        this.targetTemperature = 25.0;
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

    // 新增：处理带温度参数的复合命令（格式如"cool_26"或"heat_28"）
    public boolean setModeWithTemperature(String command) {
        try {
            // 解析命令格式：模式_温度（如"cool_26"）
            String[] parts = command.split("_");
            if (parts.length != 2) {
                return false;
            }

            Mode newMode = Mode.fromString(parts[0]);
            double temperature = Double.parseDouble(parts[1]);

            // 验证温度范围
            if (temperature < 16.0 || temperature > 30.0) {
                return false;
            }

            // 更新模式和温度
            String oldStatus = getStatus();
            this.currentMode = newMode;
            this.targetTemperature = temperature;

            // 通知状态变更
            notifyStatusChanged(oldStatus, newMode.getValue());
            System.out.printf("[HomeSimulator] 空调 %s 设置为 %s模式, 温度 %.1f°C%n",
                    name, newMode.getValue(), temperature);
            return true;

        } catch (Exception e) {
            System.err.println("[HomeSimulator] 解析空调命令失败: " + command);
            return false;
        }
    }
}