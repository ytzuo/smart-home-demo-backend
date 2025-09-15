package HomeSimulator.furniture;

import IDL.HomeStatus;
import IDL.HomeStatusDataWriter;
import HomeSimulator.HomeSimulator;
import HomeSimulator.HomeSimulatorAlert;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 家具管理器 - 管理DDS资源、家具状态汇总及全局HomeStatus维护
 */
public class FurnitureManager {
    private final Map<String, Furniture> furnitureMap;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicBoolean running;
    private final List<StatusUpdateListener> statusListeners;

    // 新增：设备静默状态跟踪Map
    private final Map<String, Boolean> deviceSilentStatus = new ConcurrentHashMap<>();

    // DDS核心资源（全局唯一，供所有家具共用）
    private final Publisher ddsPublisher;
    private final Topic homeStatusTopic;
    private HomeStatusDataWriter homeStatusDataWriter;
    private final HomeStatus aggregatedHomeStatus = new HomeStatus(); // 全局汇总状态

    // 状态更新监听器接口
    public interface StatusUpdateListener {
        void onStatusUpdate(String furnitureId, String furnitureType, String newStatus);
    }

    /**
     * 构造函数（接收DDS发布器和主题）
     */
    public FurnitureManager(Publisher ddsPublisher, Topic homeStatusTopic) {
        this.furnitureMap = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(4);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(2);
        this.running = new AtomicBoolean(false);
        this.statusListeners = new CopyOnWriteArrayList<>();
        this.ddsPublisher = ddsPublisher;
        this.homeStatusTopic = homeStatusTopic;

        initializeDDSResources(); // 初始化DDS数据写入器
        initializeDefaultFurniture(); // 初始化默认家具（传入DDS写入器和管理器）
    }

    /**
     * 初始化DDS数据写入器（配置QoS并创建HomeStatusDataWriter）
     */
    private void initializeDDSResources() {
        try {
            DataWriterQos dwQos = new DataWriterQos();
            ddsPublisher.get_default_datawriter_qos(dwQos);
            // 沿用原QoS配置（可靠传输+瞬态本地持久性）
            dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
            dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
            dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
            dwQos.history.depth = 10;

            // 创建全局唯一的HomeStatusDataWriter（供所有家具共用）
            homeStatusDataWriter = (HomeStatusDataWriter) ddsPublisher.create_datawriter(
                    homeStatusTopic, dwQos, null, StatusKind.STATUS_MASK_NONE);
            System.out.println("[FurnitureManager] DDS数据写入器初始化完成");
        } catch (Exception e) {
            System.err.println("[FurnitureManager] DDS初始化失败: " + e.getMessage());
            throw new RuntimeException("DDS资源初始化失败，无法启动家具管理器", e);
        }
    }

    /**
     * 初始化默认家具（传入DDS写入器和管理器引用）
     */
    private void initializeDefaultFurniture() {
        // 初始化灯具（传入DDS写入器和管理器实例）
        Light livingRoomLight = new Light("light1", "客厅灯", homeStatusDataWriter, this);
        Light bedroomLight = new Light("light2", "卧室灯", homeStatusDataWriter, this);

        // 初始化空调（其他家具类型类似）
       AirConditioner livingRoomAC = new AirConditioner("ac1", "客厅空调", homeStatusDataWriter, this);
       AirConditioner bedroomAC = new AirConditioner("ac2", "卧室空调", homeStatusDataWriter, this);

        // 注册家具
        registerFurniture(livingRoomLight);
        registerFurniture(bedroomLight);
       registerFurniture(livingRoomAC);
        registerFurniture(bedroomAC);

        // 设置报警系统引用（通过HomeSimulator获取）
        HomeSimulatorAlert alertSystem = HomeSimulator.getAlertSystem();
        if (alertSystem != null) {
            livingRoomLight.setAlertSystem(alertSystem);
            bedroomLight.setAlertSystem(alertSystem);
            livingRoomAC.setAlertSystem(alertSystem);
            bedroomAC.setAlertSystem(alertSystem);
            System.out.println("[FurnitureManager] 已为所有设备设置报警系统引用");
        }

        System.out.println("[FurnitureManager] 默认家具初始化完成（共" + furnitureMap.size() + "个）");
    }

    /**
     * 设置设备的静默状态
     * @param deviceId 设备ID
     * @param isSilent 是否静默（true表示不发送presence和homestatus数据）
     * @return 设置是否成功
     */
    public boolean setDeviceSilentStatus(String deviceId, boolean isSilent) {
        if (!furnitureMap.containsKey(deviceId)) {
            System.err.println("[FurnitureManager] 设备不存在: " + deviceId);
            return false;
        }

        deviceSilentStatus.put(deviceId, isSilent);
        System.out.printf("[FurnitureManager] 设置设备静默状态: %s -> %s\n",
                deviceId, isSilent ? "静默" : "正常");
        return true;
    }

    /**
     * 获取设备的静默状态
     * @param deviceId 设备ID
     * @return 设备是否静默
     */
    public boolean isDeviceSilent(String deviceId) {
        return deviceSilentStatus.getOrDefault(deviceId, false);
    }


    /**
     * 启动管理器（开始状态监控）
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            System.out.println("[FurnitureManager] 已启动");
            startStatusMonitoring(); // 启动定期状态检查
        }
    }

    /**
     * 停止管理器（释放资源）
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("[FurnitureManager] 正在停止...");
            // ======== 新增：停止所有家具的定时任务 ========
            for (Furniture furniture : furnitureMap.values()) {
                if (furniture instanceof Light) { // 针对Light类型调用stop()
                    ((Light) furniture).stop();
                }
                else if (furniture instanceof AirConditioner) {
                    ((AirConditioner) furniture).stop();
                }
            }
            executorService.shutdown();
            scheduledExecutorService.shutdown();

            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            System.out.println("[FurnitureManager] 已停止");
        }
    }

    /**
     * 定期监控家具状态（10秒一次）
     */
    private void startStatusMonitoring() {
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (running.get()) {
                checkAllFurnitureStatus();
                publishGlobalHomeStatus(); // 新增：定时上报全局状态
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * 检查所有家具状态（触发状态更新通知）
     */
    private void checkAllFurnitureStatus() {
        for (Furniture furniture : furnitureMap.values()) {
            final String deviceId = furniture.getId();
            // 检查设备是否静默，如果静默则跳过状态检查和上报
            if (isDeviceSilent(deviceId)) {
                continue;
            }
            executorService.submit(() -> {
                try {
                    String currentStatus = furniture.getStatus();
                    notifyStatusUpdate(furniture.getId(), furniture.getType(), currentStatus);
                } catch (Exception e) {
                    System.err.println("[FurnitureManager] 状态检查异常: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 注册家具（添加状态变更监听器）
     */
    public void registerFurniture(Furniture furniture) {
        if (furniture == null) {
            throw new IllegalArgumentException("家具不能为空");
        }
        furnitureMap.put(furniture.getId(), furniture);

        // 添加状态变更监听器（打印日志）
        furniture.addStatusChangeListener((id, oldStatus, newStatus) ->
                System.out.printf("[FurnitureManager] 家具状态变更: %s(%s) 从 %s → %s%n",
                        furniture.getName(), id, oldStatus, newStatus));

        // 初始化全局状态数组（确保aggregatedHomeStatus包含所有设备）
        initializeGlobalStatusArrays();

        System.out.printf("[FurnitureManager] 注册家具: %s(%s)%n", furniture.getName(), furniture.getType());
    }

    /**
     * 初始化全局状态数组（确保所有设备都包含在aggregatedHomeStatus中）
     */
    private void initializeGlobalStatusArrays() {
        int totalDevices = furnitureMap.size();
        if (totalDevices == 0) {
            return;
        }

        try {
            // 确保数组长度足够容纳所有设备
            aggregatedHomeStatus.deviceIds.ensure_length(totalDevices, totalDevices);
            aggregatedHomeStatus.deviceTypes.ensure_length(totalDevices, totalDevices);
            aggregatedHomeStatus.deviceStatus.ensure_length(totalDevices, totalDevices);

            // 重新填充所有设备信息
            int index = 0;
            for (Furniture furniture : furnitureMap.values()) {
                aggregatedHomeStatus.deviceIds.set_at(index, furniture.getId());
                aggregatedHomeStatus.deviceTypes.set_at(index, furniture.getType());
                
                // 根据设备类型生成对应的JSON状态
                String jsonStatus = generateDeviceJsonStatus(furniture);
                aggregatedHomeStatus.deviceStatus.set_at(index, jsonStatus);
                
                index++;
            }

            System.out.println("[FurnitureManager] 全局状态数组初始化完成，共 " + totalDevices + " 个设备");
        } catch (Exception e) {
            System.err.println("[FurnitureManager] 初始化全局状态数组异常: " + e.getMessage());
        }
    }

    /**
     * 根据设备类型生成对应的JSON状态
     */
    private String generateDeviceJsonStatus(Furniture furniture) {
        try {
            JSONObject statusJson = new JSONObject();
            JSONArray statusArray = new JSONArray();
            JSONArray paramsArray = new JSONArray();

            if (furniture instanceof Light) {
                Light light = (Light) furniture;
                statusArray.put(light.isOn() ? 1 : 0);
                statusArray.put(light.getColorTempCode());
                statusArray.put(light.getSceneModeCode());
                
                paramsArray.put((float) light.getBrightness());
                paramsArray.put(light.getColorTempValue());
            } else if (furniture instanceof AirConditioner) {
                AirConditioner ac = (AirConditioner) furniture;
                statusArray.put(ac.isOn() ? 1 : 0);
                statusArray.put(ac.getModeCode());
                statusArray.put(ac.getSwingModeCode());
                statusArray.put(ac.getDehumidifyModeCode());
                
                paramsArray.put((float) ac.getTemperature());
            }

            statusJson.put("status", statusArray);
            statusJson.put("params", paramsArray);
            return statusJson.toString();
        } catch (Exception e) {
            System.err.println("[FurnitureManager] 生成设备JSON状态异常: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * 移除家具
     */
    public void unregisterFurniture(String furnitureId) {
        Furniture removed = furnitureMap.remove(furnitureId);
        if (removed != null) {
            System.out.printf("[FurnitureManager] 移除家具: %s%n", removed.getName());
        }
    }

    /**
     * 获取单个家具
     */
    public Furniture getFurniture(String furnitureId) {
        return furnitureMap.get(furnitureId);
    }

    /**
     * 获取所有家具列表
     */
    public List<Furniture> getAllFurniture() {
        return new ArrayList<>(furnitureMap.values());
    }

    /**
     * 按类型获取家具列表
     */
    public List<Furniture> getFurnitureByType(String type) {
        List<Furniture> result = new ArrayList<>();
        for (Furniture furniture : furnitureMap.values()) {
            if (furniture.getType().equals(type)) {
                result.add(furniture);
            }
        }
        return result;
    }

    /**
     * 更新家具状态
     */
    public boolean updateFurnitureStatus(String furnitureId, String newStatus) {
        Furniture furniture = furnitureMap.get(furnitureId);
        if (furniture != null) {
            return furniture.setStatus(newStatus);
        }
        System.err.println("[FurnitureManager] 未找到家具: " + furnitureId);
        return false;
    }

    /**
     * 添加状态更新监听器
     */
    public void addStatusUpdateListener(StatusUpdateListener listener) {
        statusListeners.add(listener);
    }

    /**
     * 移除状态更新监听器
     */
    public void removeStatusUpdateListener(StatusUpdateListener listener) {
        statusListeners.remove(listener);
    }

    /**
     * 通知状态更新监听器
     */
    private void notifyStatusUpdate(String furnitureId, String furnitureType, String newStatus) {
        for (StatusUpdateListener listener : statusListeners) {
            executorService.submit(() -> {
                try {
                    listener.onStatusUpdate(furnitureId, furnitureType, newStatus);
                } catch (Exception e) {
                    System.err.println("[FurnitureManager] 通知监听器异常: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 更新设备状态到全局HomeStatus（同步所有设备类型的JSON状态）
     */
    public synchronized void updateDeviceStatus(String deviceId) {
        Furniture furniture = getFurniture(deviceId);
        if (furniture == null) {
            System.err.println("[FurnitureManager] 未找到设备: " + deviceId);
            return;
        }

        try {
            // 查找设备索引
            int index = -1;
            for (int i = 0; i < aggregatedHomeStatus.deviceIds.length(); i++) {
                if (deviceId.equals(aggregatedHomeStatus.deviceIds.get_at(i))) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                // 如果设备不在全局状态中，重新初始化
                initializeGlobalStatusArrays();
                return;
            }

            // 根据设备类型生成对应的JSON状态
            String jsonStatus = generateDeviceJsonStatus(furniture);
            aggregatedHomeStatus.deviceStatus.set_at(index, jsonStatus);

            // 更新全局HomeStatus的时间戳
            DateTimeFormatter timestampFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            aggregatedHomeStatus.timeStamp =
                    LocalDateTime.now().format(timestampFormatter);

            System.out.printf("[FurnitureManager] 全局状态更新: %s=%s%n",
                    furniture.getName(), furniture.getStatus());
        } catch (Exception e) {
            System.err.println("[FurnitureManager] 更新设备JSON状态异常: " + e.getMessage());
        }
    }

    /**
     * 更新灯具状态到全局HomeStatus（已废弃，使用updateDeviceStatus替代）
     */
    @Deprecated
    public synchronized void updateLightStatus(String lightId, boolean isOn) {
        updateDeviceStatus(lightId);
    }

    /**
     * 获取所有家具状态汇总
     */
    public Map<String, String> getAllFurnitureStatus() {
        Map<String, String> statusMap = new HashMap<>();
        for (Furniture furniture : furnitureMap.values()) {
            statusMap.put(furniture.getId(), furniture.getStatus());
        }
        return statusMap;
    }

    /**
     * 获取家具状态汇总描述
     */
    public String getFurnitureStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("家具状态汇总:\n");

        for (Furniture furniture : furnitureMap.values()) {
            summary.append(String.format("  %s: %s\n",
                    furniture.getName(), furniture.getStatusDescription()));
        }

        return summary.toString();
    }

    /**
     * 获取全局汇总的HomeStatus
     */
    public HomeStatus getAggregatedHomeStatus() {
        return aggregatedHomeStatus; // 返回当前汇总状态（深拷贝可选，视需求而定）
    }

    /**
     * 发布全局HomeStatus到DDS（定时上报所有家具状态）
     */
    public void publishGlobalHomeStatus() {
        if (homeStatusDataWriter == null || !running.get()) {
            return;
        }

        try {
            // 创建临时HomeStatus对象，只包含非静默设备
            HomeStatus filteredHomeStatus = new HomeStatus();
            List<Integer> nonSilentIndices = new ArrayList<>();

            // 找出所有非静默设备的索引
            for (int i = 0; i < aggregatedHomeStatus.deviceIds.length(); i++) {
                String deviceId = aggregatedHomeStatus.deviceIds.get_at(i);
                if (!isDeviceSilent(deviceId)) {
                    nonSilentIndices.add(i);
                } else {
                    System.out.printf("[FurnitureManager] 设备 %s 处于静默状态，跳过全局状态上报\n", deviceId);
                }
            }

            // 如果没有非静默设备，就不发送
            if (nonSilentIndices.isEmpty()) {
                System.out.println("[FurnitureManager] 所有设备都处于静默状态，不发送全局状态");
                return;
            }

            // 初始化过滤后的状态数组
            filteredHomeStatus.deviceIds.ensure_length(nonSilentIndices.size(), nonSilentIndices.size());
            filteredHomeStatus.deviceTypes.ensure_length(nonSilentIndices.size(), nonSilentIndices.size());
            filteredHomeStatus.deviceStatus.ensure_length(nonSilentIndices.size(), nonSilentIndices.size());

            // 更新时间戳
            DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            filteredHomeStatus.timeStamp = LocalDateTime.now().format(timestampFormatter);

            // 填充非静默设备的状态信息
            int index = 0;
            for (Integer originalIndex : nonSilentIndices) {
                filteredHomeStatus.deviceIds.set_at(index, aggregatedHomeStatus.deviceIds.get_at(originalIndex));
                filteredHomeStatus.deviceTypes.set_at(index, aggregatedHomeStatus.deviceTypes.get_at(originalIndex));
                filteredHomeStatus.deviceStatus.set_at(index, aggregatedHomeStatus.deviceStatus.get_at(originalIndex));
                index++;
            }

            // 发布过滤后的全局状态
            ReturnCode_t result = homeStatusDataWriter.write(
                    filteredHomeStatus,
                    InstanceHandle_t.HANDLE_NIL_NATIVE
            );

            if (result == ReturnCode_t.RETCODE_OK) {
                System.out.println("[FurnitureManager] 全局状态定时上报成功 - " +
                        filteredHomeStatus.deviceIds.length() + " 个设备");
                // 输出所有设备状态详情
                System.out.println("[FurnitureManager] 设备状态详情:");
                for (int i = 0; i < filteredHomeStatus.deviceIds.length(); i++) {
                    String deviceId = filteredHomeStatus.deviceIds.get_at(i);
                    String deviceType = filteredHomeStatus.deviceTypes.get_at(i);
                    String deviceStatus = filteredHomeStatus.deviceStatus.get_at(i);
                    System.out.printf("[FurnitureManager]   设备ID: %s, 类型: %s, 状态: %s%n",
                            deviceId, deviceType, deviceStatus);
                }
            } else {
                System.err.println("[FurnitureManager] 全局状态定时上报失败，返回码: " + result);
            }
        } catch (Exception e) {
            System.err.println("[FurnitureManager] 全局状态定时上报异常: " + e.getMessage());
        }
    }
}