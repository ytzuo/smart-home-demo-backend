package HomeSimulator.furniture;

import IDL.HomeStatus;
import IDL.HomeStatusDataWriter;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 空调类 - 实现Furniture接口，遵循与Light.java相同的设计模式
 */
public class AirConditioner implements Furniture {
    // 空调状态字段
    private boolean isOn; // 开关状态
    private boolean coolingMode; // 制冷模式
    private boolean swingMode; // 扫风模式
    private boolean dehumidificationMode; // 除湿模式
    private int temperature; // 温度设置(16-30℃)

    // 家具基础信息
    private final String id;
    private final String name;
    private final String type;

    // DDS相关
    private HomeStatusDataWriter ddsWriter; // DDS数据写入器
    private FurnitureManager manager; // 家具管理器
    private final List<StatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>(); // 状态监听器集合

    /**
     * 构造函数
     */
    public AirConditioner(String id, String name, HomeStatusDataWriter ddsWriter, FurnitureManager manager) {
        this.id = id;          // 初始化ID
        this.name = name;      // 初始化名称
        this.type = "ac";      // 固定类型为"ac"
        this.ddsWriter = ddsWriter;
        this.manager = manager;

        // 默认状态初始化
        this.isOn = false;     // 默认关闭
        this.coolingMode = false; // 默认关闭制冷
        this.swingMode = false; // 默认关闭扫风
        this.dehumidificationMode = false; // 默认关闭除湿
        this.temperature = 25; // 默认温度25℃
    }

    // ======== 实现Furniture接口方法 ========
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
        return type;
    }

    @Override
    public String getStatus() {
        return isOn ? "on" : "off";
    }

    @Override
    public String getStatusDescription() {
        if (!isOn) {
            return getName() + "状态: 关闭";
        }
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
            publishStatus(); // 状态变更时上报DDS
            notifyStatusChange(oldStatus ? "on" : "off", newStatus);
        }
        return true;
    }

    @Override
    public void addStatusChangeListener(StatusChangeListener listener) {
        statusChangeListeners.add(listener);
    }

    @Override
    public void removeStatusChangeListener(StatusChangeListener listener) {
        statusChangeListeners.remove(listener);
    }

    // ======== 状态字段的getter/setter方法 ========
    public boolean isOn() { return isOn; }
    public void setOn(boolean on) { this.isOn = on; publishStatus(); }

    public boolean isCoolingMode() { return coolingMode; }
    public void setCoolingMode(boolean coolingMode) { this.coolingMode = coolingMode; publishStatus(); }

    public boolean isSwingMode() { return swingMode; }
    public void setSwingMode(boolean swingMode) { this.swingMode = swingMode; publishStatus(); }

    public boolean isDehumidificationMode() { return dehumidificationMode; }
    public void setDehumidificationMode(boolean dehumidificationMode) { this.dehumidificationMode = dehumidificationMode; publishStatus(); }

    public int getTemperature() { return temperature; }
    public void setTemperature(int temperature) {
        // 限制温度范围16-30℃
        this.temperature = Math.max(16, Math.min(30, temperature));
        publishStatus();
    }

    /**
     * 上报状态到DDS
     */
    public void publishStatus() {
        if (ddsWriter == null) {
            System.err.println("[AirConditioner] DDS写入器未初始化，无法上报状态");
            return;
        }

        try {
            HomeStatus status = new HomeStatus();
            int acIndex = getAcIndex();

            // 添加时间戳
            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            status.timeStamp = LocalDateTime.now().format(timestampFormatter);

            // 构建JSON对象
            JSONObject statusJson = new JSONObject();

            // 构建状态数组 [开关, 制冷, 扫风, 除湿]
            JSONArray statusArray = new JSONArray();
            statusArray.put(isOn ? 1 : 0);          // status[0]: 开关状态
            statusArray.put(coolingMode ? 1 : 0);   // status[1]: 制冷模式
            statusArray.put(swingMode ? 1 : 0);     // status[2]: 扫风模式
            statusArray.put(dehumidificationMode ? 1 : 0); // status[3]: 除湿模式
            statusJson.put("status", statusArray);

            // 构建参数数组 [温度]
            JSONArray paramsArray = new JSONArray();
            paramsArray.put(temperature);           // params[0]: 温度
            statusJson.put("params", paramsArray);

            // 更新deviceStatus字段
            if (status.deviceStatus.length() <= acIndex) {
                status.deviceStatus.ensure_length(acIndex + 1, acIndex + 1);
            }
            status.deviceStatus.set_at(acIndex, statusJson.toString());

            // 关联设备ID和类型
            if (status.deviceIds.length() <= acIndex) {
                status.deviceIds.ensure_length(acIndex + 1, acIndex + 1);
                status.deviceTypes.ensure_length(acIndex + 1, acIndex + 1);
            }
            status.deviceIds.set_at(acIndex, this.id);
            status.deviceTypes.set_at(acIndex, this.type);

            // 发布状态
            ReturnCode_t result = ddsWriter.write(status, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (result == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[AirConditioner] %s状态上报: %s%n", getName(), statusJson.toString());
            } else {
                System.err.printf("[AirConditioner] 上报失败，返回码: %s%n", result);
            }
        } catch (Exception e) {
            System.err.println("[AirConditioner] 上报异常: " + e.getMessage());
        }
    }

    /**
     * 获取空调索引
     */
    private int getAcIndex() {
        try {
            String numericPart = getId().replaceAll("[^0-9]", "");
            return Integer.parseInt(numericPart) - 1;
        } catch (NumberFormatException e) {
            System.err.println("[AirConditioner] 空调ID格式错误（应为acX）: " + getId());
            return -1;
        }
    }

    /**
     * 通知状态变更监听器
     */
    public void notifyStatusChange(String oldStatus, String newStatus) {
        for (StatusChangeListener listener : statusChangeListeners) {
            listener.onStatusChanged(getId(), oldStatus, newStatus);
        }
    }
}