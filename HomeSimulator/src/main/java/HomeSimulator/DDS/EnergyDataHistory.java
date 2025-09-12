package HomeSimulator.DDS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 能耗数据历史记录管理器
 * 负责存储和管理所有设备的历史能耗数据，为ReportMedia提供数据源
 */
public class EnergyDataHistory {
    // 单例实例
    private static volatile EnergyDataHistory instance;

    // 存储每个设备的历史数据点
    private final Map<String, CopyOnWriteArrayList<EnergyDataPoint>> deviceHistoryData;

    // 最大数据点数量
    private static final int MAX_DATA_POINTS = 1000;

    // 数据保留时间（分钟），默认为24小时
    private static final long DATA_RETENTION_MINUTES = 1440;

    // 清理过期数据的调度器
    private final ScheduledExecutorService cleanupScheduler;

    // 私有构造函数
    private EnergyDataHistory() {
        deviceHistoryData = new ConcurrentHashMap<>();

        // 初始化清理调度器，每小时清理一次过期数据
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "energy-data-cleanup-thread");
            thread.setDaemon(true);
            return thread;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredData,
                60, 60, TimeUnit.MINUTES);
    }

    // 获取单例实例
    public static EnergyDataHistory getInstance() {
        if (instance == null) {
            synchronized (EnergyDataHistory.class) {
                if (instance == null) {
                    instance = new EnergyDataHistory();
                }
            }
        }
        return instance;
    }

    /**
     * 添加设备能耗数据
     */
    public void addEnergyData(String deviceId, String deviceType,
                              float currentPower, float dailyConsumption, float weeklyConsumption) {
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        EnergyDataPoint dataPoint = new EnergyDataPoint(
                deviceId, deviceType, currentPower, dailyConsumption, weeklyConsumption, timestamp);

        // 获取或创建设备的历史数据列表
        CopyOnWriteArrayList<EnergyDataPoint> historyData = deviceHistoryData.computeIfAbsent(
                deviceId, k -> new CopyOnWriteArrayList<>());

        // 添加新数据点
        historyData.add(dataPoint);

        // 限制数据点数量
        if (historyData.size() > MAX_DATA_POINTS) {
            // 删除最早的数据点
            historyData.remove(0);
        }
    }

    /**
     * 获取指定设备的历史能耗数据
     */
    public List<EnergyDataPoint> getHistoryData(String deviceId, String timeRange) {
        if (deviceId == null || !deviceHistoryData.containsKey(deviceId)) {
            return Collections.emptyList();
        }

        CopyOnWriteArrayList<EnergyDataPoint> allData = deviceHistoryData.get(deviceId);
        long currentTime = System.currentTimeMillis();
        long timeThreshold = 0;

        // 根据时间范围计算时间阈值
        if (timeRange != null) {
            if (timeRange.endsWith("h")) {
                try {
                    int hours = Integer.parseInt(timeRange.substring(0, timeRange.length() - 1));
                    timeThreshold = currentTime - (hours * 60L * 60L * 1000L);
                } catch (NumberFormatException e) {
                    // 格式无效，返回所有数据
                }
            } else if (timeRange.endsWith("d")) {
                try {
                    int days = Integer.parseInt(timeRange.substring(0, timeRange.length() - 1));
                    timeThreshold = currentTime - (days * 24L * 60L * 60L * 1000L);
                } catch (NumberFormatException e) {
                    // 格式无效，返回所有数据
                }
            }
        }

        // 过滤时间范围内的数据
        List<EnergyDataPoint> filteredData = new ArrayList<>();
        for (EnergyDataPoint point : allData) {
            if (point.getTimestamp() >= timeThreshold) {
                filteredData.add(point);
            }
        }

        return filteredData;
    }

    /**
     * 获取所有设备的历史能耗数据
     */
    public List<EnergyDataPoint> getAllHistoryData() {
        List<EnergyDataPoint> allData = new ArrayList<>();
        for (CopyOnWriteArrayList<EnergyDataPoint> deviceData : deviceHistoryData.values()) {
            allData.addAll(deviceData);
        }

        // 按时间戳排序
        allData.sort((p1, p2) -> Long.compare(p1.getTimestamp(), p2.getTimestamp()));

        return allData;
    }

    /**
     * 清理过期数据
     */
    private void cleanupExpiredData() {
        long expirationTime = System.currentTimeMillis() - (DATA_RETENTION_MINUTES * 60L * 1000L);

        for (Map.Entry<String, CopyOnWriteArrayList<EnergyDataPoint>> entry : deviceHistoryData.entrySet()) {
            CopyOnWriteArrayList<EnergyDataPoint> dataPoints = entry.getValue();

            // 移除过期数据点
            dataPoints.removeIf(point -> point.getTimestamp() < expirationTime);

            // 如果设备没有数据了，从map中移除
            if (dataPoints.isEmpty()) {
                deviceHistoryData.remove(entry.getKey());
            }
        }
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 能耗数据点内部类
     */
    public static class EnergyDataPoint {
        private final String deviceId;
        private final String deviceType;
        private final float currentPower;
        private final float dailyConsumption;
        private final float weeklyConsumption;
        private final long timestamp;

        public EnergyDataPoint(String deviceId, String deviceType,
                               float currentPower, float dailyConsumption, float weeklyConsumption, long timestamp) {
            this.deviceId = deviceId;
            this.deviceType = deviceType;
            this.currentPower = currentPower;
            this.dailyConsumption = dailyConsumption;
            this.weeklyConsumption = weeklyConsumption;
            this.timestamp = timestamp;
        }

        // Getters
        public String getDeviceId() { return deviceId; }
        public String getDeviceType() { return deviceType; }
        public float getCurrentPower() { return currentPower; }
        public float getDailyConsumption() { return dailyConsumption; }
        public float getWeeklyConsumption() { return weeklyConsumption; }
        public long getTimestamp() { return timestamp; }

        public float getPowerConsumption() {
            return currentPower;
        }
    }
}
