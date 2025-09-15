package HomeSimulator.furniture;

import HomeSimulator.HomeSimulatorAlert;
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
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 灯具类 - 独立实现状态上报逻辑，支持独立报警
 */
public class Light implements Furniture, AlertableDevice {
    // 报警相关属性
    private boolean isAbnormal = false;
    private String alertMessage = "";
    private String alertType = "light_abnormal";
    private final Random random = new Random();
    private int abnormalCounter = 0;
    private static final int ABNORMAL_THRESHOLD = 2;  // 降低阈值，更容易触发报警
    // ======== 预设状态字段（常驻显示用） ========
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
    private HomeSimulatorAlert alertSystem; // 报警系统引用
    private final List<StatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>(); // 状态监听器集合
    // ======== 新增：状态调度与动态变化相关属性 ========
    private ScheduledExecutorService statusScheduler; // 状态调度器
    private int consecutiveHighBrightnessCount = 0; // 高亮度连续计数（用于异常检测）
    
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

        // ======== 新增：初始化状态调度器并启动动态变化 ========
        this.statusScheduler = Executors.newSingleThreadScheduledExecutor();
        startDynamicStatusChanges();
    }

    private void startDynamicStatusChanges() {
        // 每20-40秒随机调整一次状态（仅在灯具开启时）
        statusScheduler.scheduleAtFixedRate(() -> {
            if (isOn && random.nextDouble() < 0.8) { // 80%概率触发状态变化
                autoAdjustStatus();
            }
            // 无论是否变化，均检测异常状态
            detectAbnormalStatus();
        }, 5, random.nextInt(10000) + 10000, TimeUnit.MILLISECONDS); // 初始延迟5秒，周期10-20秒
    }

    // ======== 异常状态检测 ========
    private void detectAbnormalStatus() {
        if (!isOn) {
            consecutiveHighBrightnessCount = 0; // 灯具关闭时重置计数
            return;
        }

        // 检测高亮度异常（连续3次检测到亮度≥90%触发过热报警）
        if (brightness >= 90) {
            consecutiveHighBrightnessCount++;
            System.out.printf("[Light] %s 高亮度预警(%d/3): %d%%%n",
                    name, consecutiveHighBrightnessCount, brightness);

            if (consecutiveHighBrightnessCount >= 3) {
                triggerOverheatAlert(); // 触发过热报警
                consecutiveHighBrightnessCount = 0; // 报警后重置计数
            }
        } else {
            consecutiveHighBrightnessCount = Math.max(0, consecutiveHighBrightnessCount - 1); // 正常状态递减计数
        }
    }

    // ======== 自动调整状态（亮度/色温/场景） ========
    private void autoAdjustStatus() {
        try {
            // 随机选择调整类型：亮度(50%)/色温(30%)/场景(20%)
            double adjustType = random.nextDouble();

            if (adjustType < 0.5) { // 50%概率调整亮度
                int newBrightness = brightness + random.nextInt(41) - 20; // -10~+10范围内调整
                newBrightness = Math.max(10, Math.min(100, newBrightness)); // 限制在10-100
                setBrightness(newBrightness);
                System.out.printf("[Light] %s 自动调整亮度: %d%%%n", name, newBrightness);
            }
            else if (adjustType < 0.8) { // 30%概率调整色温
                List<String> validTemps = Arrays.asList("暖白", "冷白", "中性");
                String newTemp = validTemps.get(random.nextInt(validTemps.size()));
                if (!newTemp.equals(colorTemp)) {
                    setColorTemp(newTemp);
                    System.out.printf("[Light] %s 自动切换色温: %s%n", name, newTemp);
                }
            }
            else { // 20%概率调整场景模式
                List<String> validModes = Arrays.asList("日常", "阅读", "睡眠", "影院");
                String newMode = validModes.get(random.nextInt(validModes.size()));
                if (!newMode.equals(sceneMode)) {
                    setSceneMode(newMode);
                    System.out.printf("[Light] %s 自动切换场景: %s%n", name, newMode);
                }
            }

            publishStatus(); // 状态变化后主动上报
        } catch (Exception e) {
            System.err.println("[Light] 自动调整状态失败: " + e.getMessage());
        }
    }

    /**
     * 设置报警系统引用
     * @param alertSystem 报警系统
     */
    public void setAlertSystem(HomeSimulatorAlert alertSystem) {
        this.alertSystem = alertSystem;
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

    public boolean isOn() {
        return isOn;
    }

    // ======== 预设状态的getter/setter（供外部修改） ========
    public int getBrightness() { return brightness; }
    public void setBrightness(int brightness) { this.brightness = Math.max(0, Math.min(100, brightness)); } // 限制0-100
    
    // ======== 实现AlertableDevice接口方法 ========
    @Override
    public boolean checkAbnormal() {
        // 原有的概率检测逻辑已移除，改为手动触发
        return isAbnormal;
    }
    
    /**
     * 手动触发灯具状态异常报警
     */
    public void triggerStatusAbnormalAlert() {
        if (!isAbnormal) {
            isAbnormal = true;
            alertType = "light_status_abnormal";
            alertMessage = String.format("灯具 %s 状态异常，可能存在电路问题", name);
            
            System.out.printf("[Light] 触发电路警报: ID=%s, 类型=%s, 消息=%s%n",
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
     * 手动触发灯具过热报警
     */
    public void triggerOverheatAlert() {
        if (!isAbnormal) {
            isAbnormal = true;
            alertType = "light_overheat";
            alertMessage = String.format("灯具 %s 温度异常，可能存在过热风险", name);
            
            System.out.printf("[Light] 触发过热报警: ID=%s, 类型=%s, 消息=%s%n",
                    id, alertType, alertMessage);
            
            if (alertSystem != null) {
                alertSystem.receiveDeviceAlert(id, type, alertType, alertMessage);
            }

            // ======== 自动重置逻辑（5秒后恢复正常） ========
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 延时5秒（可根据需求调整）
                    if (isAbnormal) { // 确保未被手动重置过
                        resetAlert();
                        // 异常恢复后适当降低亮度
                        if (brightness >= 90) {
                            setBrightness(brightness - 20);
                            System.out.printf("[Light] %s 异常恢复，自动降低亮度至: %d%%%n", name, brightness);
                            publishStatus();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 保留中断状态
                }
            }).start();

        }
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
        System.out.printf("[Light] 灯具 %s 恢复正常%n", name);
        
        // 通知报警系统清除报警
        if (alertSystem != null) {
            alertSystem.clearDeviceAlert(id);
        }
    }
    
    @Override
    public String getDeviceId() {
        return id;
    }
    
    @Override
    public String getDeviceType() {
        return type;
    }

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
        // 检查设备是否静默，如果静默则不发送状态数据
        if (manager != null && manager.isDeviceSilent(this.id)) {
            System.out.printf("[Light] %s 处于静默状态，跳过状态上报\n", name);
            return;
        }
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
                manager.updateDeviceStatus(getId()); // 同步更新全局状态
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

    // ======== 停止定时任务（确保资源释放） ========
    public void stop() {
        if (statusScheduler != null) {
            statusScheduler.shutdownNow();
            System.out.printf("[Light] %s 停止状态调度任务%n", name);
        }
    }

}

