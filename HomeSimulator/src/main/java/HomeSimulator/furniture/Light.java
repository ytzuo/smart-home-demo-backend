package HomeSimulator.furniture;

import IDL.HomeStatus;
import IDL.HomeStatusDataWriter;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 灯具类 - 独立实现状态上报逻辑
 */
public class Light implements Furniture {
    // ======== 新增：预设状态字段（常驻显示用） ========
    private int brightness = 80; // 默认亮度（0-100）
    private String colorTemp = "暖白"; // 默认色温（暖白/冷白/中性）
    private String sceneMode = "日常"; // 默认场景模式（日常/阅读/睡眠/影院）
    // 家具基础信息（构造函数初始化）
    private final String id;
    private final String name;
    private final String type;

    private boolean isOn; // 灯具状态（true=开，false=关）
    private HomeStatusDataWriter ddsWriter; // DDS数据写入器（用于上报状态）
    private FurnitureManager manager; // 家具管理器（用于更新全局状态）
    private final List<StatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>(); // 状态监听器集合

    /**
     * 构造函数
     */
    public Light(String id, String name, HomeStatusDataWriter ddsWriter, FurnitureManager manager) {
        super();
        this.id = id;          // 初始化ID（非null）
        this.name = name;      // 初始化名称（非null）
        this.type = "light";   // 固定类型为"light"
        this.isOn = false;     // 默认关闭
        this.ddsWriter = ddsWriter;
        this.manager = manager;
    }

    // ======== 实现Furniture接口方法（核心修复：返回非null值） ========
    @Override
    public String getId() {
        return id; // 返回构造函数初始化的id（非null）
    }

    @Override
    public String getName() {
        return name; // 返回构造函数初始化的name（非null）
    }

    @Override
    public String getType() {
        return type; // 返回固定类型"light"（非null）
    }

    @Override
    public String getStatus() {
        return isOn ? "on" : "off";
    }

    @Override
    public String getStatusDescription() {
        return getName() + "状态: " + (isOn ? "开启" : "关闭"); // 补充状态描述实现
    }

    @Override
    public boolean setStatus(String newStatus) {
        if (!"on".equals(newStatus) && !"off".equals(newStatus)) {
            System.err.println("[Light] 无效状态: " + newStatus);
            return false;
        }

        boolean oldStatus = isOn;
        isOn = "on".equals(newStatus);
        if (oldStatus != isOn) {
            publishStatus(); // 状态变更时上报DDS
            notifyStatusChange(oldStatus ? "on" : "off", newStatus); // 通知监听器
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

    // ======== 预设状态的getter/setter（供外部修改） ========
    public int getBrightness() { return brightness; }
    public void setBrightness(int brightness) { this.brightness = Math.max(0, Math.min(100, brightness)); } // 限制0-100

    public String getColorTemp() { return colorTemp; }
    public void setColorTemp(String colorTemp) {
        // 仅允许预设值（避免无效状态）
        List<String> validTemps = Arrays.asList("暖白", "冷白", "中性", "RGB");
        if (validTemps.contains(colorTemp)) {
            this.colorTemp = colorTemp;
        }
    }

    public String getSceneMode() { return sceneMode; }
    public void setSceneMode(String sceneMode) {
        List<String> validModes = Arrays.asList("日常", "阅读", "睡眠", "影院");
        if (validModes.contains(sceneMode)) {
            this.sceneMode = sceneMode;
        }
    }


    /**
     * 独立上报状态到DDS（使用标准化JSON模板，确保字符串一致性）
     */
    public void publishStatus() {
        if (ddsWriter == null) {
            System.err.println("[Light] DDS写入器未初始化，无法上报状态");
            return;
        }

        try {
            HomeStatus status = new HomeStatus();
            int lightIndex = getLightIndex();

            // ======== 为HomeStatus添加时间戳 ========
            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            status.timeStamp = LocalDateTime.now().format(timestampFormatter);

            // 构建标准化JSON模板（含设备基础信息+状态参数）
            JSONObject statusJson = new JSONObject();

            // 1. 基础信息（设备标识与元数据）
//            statusJson.put("deviceId", this.id);          // 设备唯一ID（如"light1"）
//            statusJson.put("deviceName", this.name);      // 设备名称（如"客厅灯"）
//            statusJson.put("type", this.type);            // 设备类型（固定"light"）
//            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//            String formattedTime = LocalDateTime.now().format(formatter);
//            statusJson.put("timestamp", formattedTime);

            // 2. 核心状态（布尔/枚举型状态序列）
            JSONArray statusArray = new JSONArray();
            statusArray.put(isOn ? 1 : 0);                // status[0]：开关状态（1=开，0=关）
            statusArray.put(getColorTempCode());          // status[1]：色温模式编码（0=暖白，1=冷白，2=中性，3=RGB）
            statusArray.put(getSceneModeCode());          // status[2]：场景模式编码（0=日常，1=阅读，2=睡眠，3=影院）
            statusJson.put("status", statusArray);        // 状态序列

            // 3. 参数信息（数值型参数序列）
            JSONArray paramsArray = new JSONArray();
            paramsArray.put((float) brightness);          // params[0]：亮度（0-100浮点）
            paramsArray.put(getColorTempValue());         // params[1]：色温值（K值，如暖白=3000K）
            statusJson.put("params", paramsArray);        // 参数序列

            // 更新deviceStatus字段（存储紧凑JSON字符串，无缩进）
            if (status.deviceStatus.length() <= lightIndex) {
                status.deviceStatus.ensure_length(lightIndex + 1, lightIndex + 1);
            }
            status.deviceStatus.set_at(lightIndex, statusJson.toString()); // 关键：使用无缩进toString()

            // 关联设备ID和类型（与deviceStatus索引对应）
            if (status.deviceIds.length() <= lightIndex) {
                status.deviceIds.ensure_length(lightIndex + 1, lightIndex + 1);
                status.deviceTypes.ensure_length(lightIndex + 1, lightIndex + 1);
            }
            status.deviceIds.set_at(lightIndex, this.id);
            status.deviceTypes.set_at(lightIndex, this.type);

            // 发布状态
            ReturnCode_t result = ddsWriter.write(status, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (result == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[Light] %s状态上报: %s%n", getName(), statusJson.toString());
                manager.updateLightStatus(getId(), isOn); // 同步更新全局状态
            } else {
                System.err.printf("[Light] 上报失败，返回码: %s%n", result);
            }
        } catch (Exception e) {
            System.err.println("[Light] 上报异常: " + e.getMessage());
        }
    }

    /**
     * 解析灯具索引（从ID提取，如"light1"→0）
     */
    public int getLightIndex() {
        try {
            String numericPart = getId().replaceAll("[^0-9]", "");
            return Integer.parseInt(numericPart) - 1;
        } catch (NumberFormatException e) {
            System.err.println("[Light] 灯具ID格式错误（应为lightX）: " + getId());
            return -1;
        }
    }

    // ======== 状态文本转编码/数值 ========
    public int getColorTempCode() {
        switch (colorTemp) {
            case "冷白": return 1;
            case "中性": return 2;
            case "RGB": return 3;
            default: return 0; // 默认"暖白"
        }
    }

    public int getSceneModeCode() {
        switch (sceneMode) {
            case "阅读": return 1;
            case "睡眠": return 2;
            case "影院": return 3;
            default: return 0; // 默认"日常"
        }
    }

    public float getColorTempValue() {
        switch (colorTemp) {
            case "暖白": return 3000f;  // 暖白3000K
            case "冷白": return 6500f;  // 冷白6500K
            case "中性": return 4500f;  // 中性4500K
            case "RGB": return 0f;      // RGB模式无固定K值
            default: return 3000f;
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

