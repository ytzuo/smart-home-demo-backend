package HomeSimulator.DDS;

import IDL.EnergyReport;
import IDL.EnergyReportDataWriter;
import HomeSimulator.furniture.Furniture;
import HomeSimulator.furniture.FurnitureManager;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 能耗报告发布器
 * 定时采集设备能耗数据并通过DDS发布
 */
public class EnergyReportPublisher {
    private EnergyReportDataWriter writer;
    private FurnitureManager furnitureManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private final Random random = new Random();
    // 设备能耗基础参数（用于模拟）
    private static final float LIGHT_POWER_ON = 40.0f;      // 灯具开启时功率（W）
    private static final float LIGHT_POWER_OFF = 0.5f;      // 灯具关闭时功率（W）
    private static final float AC_POWER_ON = 1200.0f;       // 空调开启时功率（W）
    private static final float AC_POWER_OFF = 1.0f;         // 空调关闭时功率（W）
    private static final float DAILY_CONSUMPTION_FACTOR = 0.25f; // 日能耗因子（基于运行时间）
    private static final float WEEKLY_CONSUMPTION_FACTOR = 1.075f; // 周能耗因子（基于运行时间）
    // 新增：增加功率波动范围，使变化更明显
    private static final float LIGHT_POWER_VARIATION = 0.3f; // 灯具功率波动幅度（±30%）
    private static final float AC_POWER_VARIATION = 0.4f;    // 空调功率波动幅度（±40%）
    private static final float DEFAULT_POWER_VARIATION = 0.25f; // 其他设备功率波动幅度（±25%）
    private static final float ENERGY_CONSUMPTION_VARIATION = 0.5f; // ±50%的随机波动
    /**
     * 初始化能耗发布器
     * @param pub DDS发布器
     * @param topic DDS主题
     * @param furnitureManager 家具管理器，用于获取设备信息
     * @return 是否初始化成功
     */
    public boolean start(Publisher pub, Topic topic, FurnitureManager furnitureManager) {
        if (running.get()) {
            System.out.println("[EnergyReportPublisher] 能耗发布器已启动");
            return true;
        }

        this.furnitureManager = furnitureManager;

        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = com.zrdds.infrastructure.DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;

        // 创建DataWriter
        writer = (EnergyReportDataWriter) pub.create_datawriter(
                topic,
                dwQos,
                null,
                com.zrdds.infrastructure.StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.err.println("[EnergyReportPublisher] 创建 EnergyReport DataWriter 失败");
            return false;
        }

        running.set(true);
        System.out.println("[EnergyReportPublisher] 能耗发布器启动成功");
        return true;
    }

    /**
     * 启动定时发布任务
     * @param intervalSeconds 发布间隔（秒）
     */
    public void startPeriodicReporting(int intervalSeconds) {
        if (!running.get() || writer == null) {
            System.err.println("[EnergyReportPublisher] 能耗发布器未初始化，无法启动定时任务");
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                this::publishEnergyReports,
                0,  // 立即开始
                intervalSeconds,
                TimeUnit.SECONDS);

        System.out.printf("[EnergyReportPublisher] 已启动定时能耗报告任务，间隔: %d秒\n", intervalSeconds);
    }

    /**
     * 发布所有设备的能耗报告
     */
    public void publishEnergyReports() {
        if (!running.get() || writer == null || furnitureManager == null) {
            return;
        }

        try {
            // 获取所有设备
            List<Furniture> allDevices = furnitureManager.getAllFurniture();
            for (Furniture device : allDevices) {
                publishDeviceEnergyReport(device);
            }
        } catch (Exception e) {
            System.err.println("[EnergyReportPublisher] 发布能耗报告时发生异常: " + e.getMessage());
        }
    }

    /**
     * 发布单个设备的能耗报告
     * @param device 设备对象
     */
    private void publishDeviceEnergyReport(Furniture device) {
        if (device == null) {
            return;
        }

        try {
            // 创建能耗报告对象
            EnergyReport report = new EnergyReport();
            report.deviceId = device.getId();
            report.deviceType = device.getType();

            // 根据设备类型和状态模拟能耗数据
            float currentPower = calculateCurrentPower(device);

            // 修改能耗计算公式，增加更大幅度的随机波动
            float consumptionVariation = 1.0f - ENERGY_CONSUMPTION_VARIATION + 2 * ENERGY_CONSUMPTION_VARIATION * random.nextFloat();
            float dailyConsumption = currentPower * DAILY_CONSUMPTION_FACTOR * consumptionVariation;
            float weeklyConsumption = dailyConsumption * WEEKLY_CONSUMPTION_FACTOR;

            // 设置能耗数据
            report.currentPower = currentPower;
            report.dailyConsumption = dailyConsumption;
            report.weeklyConsumption = weeklyConsumption;
            report.timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // 新增：将数据添加到历史缓存
            EnergyDataHistory.getInstance().addEnergyData(
                    report.deviceId,
                    report.deviceType,
                    report.currentPower,
                    report.dailyConsumption,
                    report.weeklyConsumption);
            // 发布数据
            ReturnCode_t rtn = writer.write(report, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (rtn == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[EnergyReportPublisher] 发布能耗报告: 设备ID=%s, 类型=%s, 当前功率=%.2fW, 当日能耗=%.2fkW, 时间=%s\n",
                        report.deviceId, report.deviceType, report.currentPower, report.dailyConsumption, report.timeStamp);
            } else {
                System.err.printf("[EnergyReportPublisher] 发布能耗报告失败: 设备ID=%s, 返回码=%s\n",
                        report.deviceId, rtn);
            }
        } catch (Exception e) {
            System.err.printf("[EnergyReportPublisher] 发布设备能耗报告异常: 设备ID=%s, 错误=%s\n",
                    device.getId(), e.getMessage());
        }
    }

    /**
     * 计算设备当前功率（模拟）
     * @param device 设备对象
     * @return 当前功率（W）
     */
    private float calculateCurrentPower(Furniture device) {
        String deviceType = device.getType();
        String status = device.getStatus();
        boolean isOn = "on".equals(status) || "cool".equals(status) || "heat".equals(status) || "fan".equals(status) || "auto".equals(status);

        // 根据设备类型和状态返回模拟功率，并增加较大范围的随机波动
        if ("light".equals(deviceType)) {
            if (isOn) {
                // 灯具：基础功率 * (0.7-1.3)的随机系数
                return LIGHT_POWER_ON * (1.0f - LIGHT_POWER_VARIATION + 2 * LIGHT_POWER_VARIATION * random.nextFloat());
            } else {
                // 关闭时也有小幅度波动
                return LIGHT_POWER_OFF * (0.95f + 0.1f * random.nextFloat());
            }
        } else if ("ac".equals(deviceType)) {
            if (isOn) {
                // 空调：基础功率 * (0.6-1.4)的随机系数
                float variation = 1.0f - AC_POWER_VARIATION + 2 * AC_POWER_VARIATION * random.nextFloat();
                // 可以根据空调模式进一步调整功率变化
                if ("cool".equals(status) || "heat".equals(status)) {
                    // 制冷或制热模式下，功率波动更大一些
                    return AC_POWER_ON * variation * (0.95f + 0.1f * random.nextFloat());
                } else {
                    return AC_POWER_ON * variation;
                }
            } else {
                // 关闭时也有小幅度波动
                return AC_POWER_OFF * (0.9f + 0.2f * random.nextFloat());
            }
        }

        // 其他设备类型默认功率
        if (isOn) {
            return 50.0f * (1.0f - DEFAULT_POWER_VARIATION + 2 * DEFAULT_POWER_VARIATION * random.nextFloat());
        } else {
            return 1.0f * (0.9f + 0.2f * random.nextFloat());
        }
    }

    /**
     * 停止发布器并释放资源
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        System.out.println("[EnergyReportPublisher] 能耗发布器已停止");
    }

    public boolean publishSingleReport(EnergyReport report) {
        if (writer == null || report == null) {
            return false;
        }

        try {
            ReturnCode_t rtn = writer.write(report, InstanceHandle_t.HANDLE_NIL_NATIVE);
            return rtn == ReturnCode_t.RETCODE_OK;
        } catch (Exception e) {
            System.err.println("[EnergyReportPublisher] 发送单条报告时发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

