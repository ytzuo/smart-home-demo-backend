package HomeSimulator.furniture;

import java.util.ArrayList;
import java.util.List;

/**
 * 灯具类 - 实现Furniture接口
 */
public class Light implements Furniture {
    private final String id;
    private final String name;
    private boolean isOn;
    private final List<StatusChangeListener> listeners;

    public Light(String id, String name) {
        this.id = id;
        this.name = name;
        this.isOn = false;
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
        return "light";
    }

    @Override
    public String getStatus() {
        return isOn ? "on" : "off";
    }

    @Override
    public boolean setStatus(String status) {
        String oldStatus = getStatus();
        boolean newState = "on".equalsIgnoreCase(status);
        
        if (this.isOn != newState) {
            this.isOn = newState;
            notifyStatusChanged(oldStatus, getStatus());
            return true;
        }
        return false;
    }

    @Override
    public String getStatusDescription() {
        return String.format("灯具 %s: %s", name, isOn ? "开启" : "关闭");
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

    public boolean isOn() {
        return isOn;
    }

    public void turnOn() {
        setStatus("on");
    }

    public void turnOff() {
        setStatus("off");
    }
}