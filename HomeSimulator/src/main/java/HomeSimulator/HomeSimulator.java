package HomeSimulator;
import IDL.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.StandardEntityCollection;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.Day;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.ChartRenderingInfo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import HomeSimulator.DDS.*;
import HomeSimulator.furniture.*;
import HomeSimulator.HomeSimulatorAlert.AlertType;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HomeSimulator主控制器
 * 协调DDS通信、家具管理和状态上报等各模块工作
 */
public class HomeSimulator {
    private static boolean hasLoad = false;
    private static HomeSimulator instance; // 单例实例

    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private FurnitureManager furnitureManager; // 家具管理器（需DDS资源初始化）
    private HomeSimulatorAlert alertSystem;    // 报警系统
    private AtomicBoolean running;
    private PresenceDataWriter presenceDataWriter;
    private Topic presenceTopic;
    // 在类的成员变量部分添加
    private MediaPublisher mediaPublisher;
    private Topic alertMediaTopic;
    // 新增：Presence定时发送调度器
    private ScheduledExecutorService presenceScheduler;
    // 新增：能耗报告发布器
    private EnergyReportPublisher energyReportPublisher;
    private Topic energyReportTopic;
    // 在类顶部添加成员变量
    private EnergyDataHistory energyDataHistory;
    private ReportMediaPublisher reportMediaPublisher;
    private Topic energyRawDataTopic;
    private EnergyRawDataPublisher energyRawDataPublisher;

    public HomeSimulator() {
        loadLibrary();
        this.running = new AtomicBoolean(false);
        instance = this; // 设置单例实例
    }
    
    /**
     * 获取报警系统实例
     * @return 报警系统实例
     */
    public static HomeSimulatorAlert getAlertSystem() {
        return instance != null ? instance.alertSystem : null;
    }

    public static HomeSimulator getInstance() {
        return instance;
    }

    private void loadLibrary() {
        if (!hasLoad) {
            try {
                System.loadLibrary("ZRDDS_JAVA");
                hasLoad = true;
                System.out.println("[HomeSimulator] ZRDDS_JAVA库加载成功");
            } catch (UnsatisfiedLinkError e) {
                System.err.println("[HomeSimulator] 警告: 无法加载ZRDDS_JAVA库，DDS功能将不可用");
                System.err.println("[HomeSimulator] 请确保ZRDDS_JAVA库在系统路径中");
                System.exit(1);
            }
        }
    }

    public void start() {
        System.out.println("[HomeSimulator] 启动家居模拟器...");

        running.set(true);

        // 1. 初始化DDS（获取Publisher和Topic，供FurnitureManager使用）
        initDDS();

        // 2. 初始化家具管理器（传入DDS资源）
        furnitureManager.start();
        
        // 3. 启动报警系统
        alertSystem.start();

        // 4. 发送一次Presence状态
        publishPresenceStatus();

        // 新增：发送一次能耗报告
        if (energyReportPublisher != null) {
            energyReportPublisher.publishEnergyReports();
        }
// ======== 新增：启动Presence定时发送任务（每10秒一次） ========
        presenceScheduler = Executors.newSingleThreadScheduledExecutor();
        presenceScheduler.scheduleAtFixedRate(
                this::publishPresenceStatus, 10, 10, TimeUnit.SECONDS); // 首次延迟30秒，之后每30秒执行

        // 新增：启动能耗报告定时发布任务（每10秒一次）
        if (energyReportPublisher != null) {
            energyReportPublisher.startPeriodicReporting(10);
        }
        System.out.println("[HomeSimulator] 家居模拟器启动完成");
        System.out.println("[HomeSimulator] 使用控制台命令触发报警: lt1(灯具状态异常), lh1(灯具过热), at1(空调温度异常), ap1(空调性能异常)");

        // 保持运行
        keepRunning();
    }

    private void initDDS() {
        ddsParticipant = DdsParticipant.getInstance();

        // 注册IDL类型
        CommandTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "Command");
        HomeStatusTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "HomeStatus");
        PresenceTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "Presence");
        AlertTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "Alert");
        // 新增：注册AlertMedia类型
        AlertMediaTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "AlertMedia");
        // 新增：注册EnergyReport类型
        EnergyReportTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "EnergyReport");
        // 新增：注册ReportMedia类型（能耗趋势图专用）
        IDL.ReportMediaTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "ReportMedia");
        // 新增：注册EnergyRawData类型
        EnergyRawDataTypeSupport.get_instance().register_type(
                ddsParticipant.getDomainParticipant(), "EnergyRawData");

        // 创建Topic
        Topic commandTopic = ddsParticipant.createTopic(
                "Command", CommandTypeSupport.get_instance());
        Topic homeStatusTopic = ddsParticipant.createTopic(
                "HomeStatus", HomeStatusTypeSupport.get_instance());
        Topic presenceTopic = ddsParticipant.createTopic(
                "Presence", PresenceTypeSupport.get_instance());
        Topic alertTopic = ddsParticipant.createTopic(
                "Alert", AlertTypeSupport.get_instance());
        // 新增：创建AlertMedia Topic
        alertMediaTopic = ddsParticipant.createTopic(
                "AlertMedia", AlertMediaTypeSupport.get_instance());
        // 新增：创建EnergyReport Topic
        energyReportTopic = ddsParticipant.createTopic(
                "EnergyReport", EnergyReportTypeSupport.get_instance());
        // 新增：创建ReportMedia Topic
        Topic reportMediaTopic = ddsParticipant.createTopic(
                "ReportMedia", IDL.ReportMediaTypeSupport.get_instance());
        energyRawDataTopic = ddsParticipant.createTopic(
                "EnergyRawData", EnergyRawDataTypeSupport.get_instance());

        // 初始化订阅者（命令接收）
        commandSubscriber = new CommandSubscriber();
        commandSubscriber.start(
                ddsParticipant.getSubscriber(),
                commandTopic,
                this::handleCommand);

        // 创建家具管理器（传入DDS发布器和HomeStatus主题）
        Publisher ddsPublisher = ddsParticipant.getPublisher();
        furnitureManager = new FurnitureManager(ddsPublisher, homeStatusTopic);
        
        // 创建报警系统
        alertSystem = new HomeSimulatorAlert(ddsPublisher, homeStatusTopic, alertTopic);
        alertSystem.setFurnitureManager(furnitureManager);

        // 新增：初始化MediaPublisher
        mediaPublisher = new MediaPublisher();
        mediaPublisher.start(ddsPublisher, alertMediaTopic);
        // 新增：初始化EnergyReportPublisher
        energyReportPublisher = new EnergyReportPublisher();
        energyReportPublisher.start(ddsPublisher, energyReportTopic, furnitureManager);

        // 新增：初始化ReportMediaPublisher
        reportMediaPublisher = new ReportMediaPublisher();
        reportMediaPublisher.start(ddsPublisher, reportMediaTopic);

        // 新增：初始化EnergyDataHistory
        energyDataHistory = EnergyDataHistory.getInstance();

        energyRawDataPublisher = new EnergyRawDataPublisher();
        energyRawDataPublisher.initialize();


        // 创建Presence DataWriter
        DataWriterQos presenceQos = new DataWriterQos();
        ddsPublisher.get_default_datawriter_qos(presenceQos);
        // 添加QoS配置
        presenceQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        presenceQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        presenceQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        presenceQos.history.depth = 10;
        presenceDataWriter = (PresenceDataWriter) ddsPublisher.create_datawriter(
                presenceTopic, presenceQos, null, StatusKind.STATUS_MASK_NONE);

        System.out.println("[HomeSimulator] DDS初始化完成");
    }
//    // 新增：添加发送媒体的公共方法，供其他组件调用
//    public boolean sendMedia(String deviceId, String deviceType, int mediaType, byte[] fileData) {
//        if (mediaPublisher != null) {
//            return mediaPublisher.publishMedia(deviceId, deviceType, mediaType, fileData);
//        }
//        return false;
//    }
    // 新增：添加支持传递alertId的媒体发送方法
    public boolean sendMedia(String deviceId, String deviceType, int mediaType, byte[] fileData, int alertId) {
        if (mediaPublisher != null) {
            return mediaPublisher.publishMedia(deviceId, deviceType, mediaType, fileData, alertId);
        }
        return false;
    }
    // 添加Presence单次发送方法
    private void publishPresenceStatus() {
        if (presenceDataWriter == null) {
            System.err.println("[HomeSimulator] Presence DataWriter未初始化");
            return;
        }

        try {
            // 获取所有设备状态
            List<Furniture> allDevices = furnitureManager.getAllFurniture();
            for (Furniture device : allDevices) {
                // 检查设备是否处于静默状态，如果是则跳过发送presence数据
                if (furnitureManager.isDeviceSilent(device.getId())) {
                    System.out.printf("[HomeSimulator] 设备 %s(%s) 处于静默状态，跳过发送Presence数据\n",
                            device.getName(), device.getId());
                    continue;
                }
                Presence presence = new Presence();
                presence.deviceId = device.getId();
                presence.deviceType = device.getType();
                presence.inRange = true; // 假设初始状态为在线
                presence.timeStamp = LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // 发布状态
                ReturnCode_t rtn = presenceDataWriter.write(presence, InstanceHandle_t.HANDLE_NIL_NATIVE);
                if (rtn == ReturnCode_t.RETCODE_OK) {
                    System.out.printf("[HomeSimulator] 发送Presence: %s(%s) - %s%n",
                            device.getName(), device.getId(), presence.inRange ? "在线" : "离线");
                } else {
                    System.out.println("上报设备状态失败");
                }
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 发送Presence失败: " + e.getMessage());
        }
    }


    private void handleCommand(Command command) {
        if (command == null) {
            System.err.println("[HomeSimulator] 接收到空命令");
            return;
        }

        String deviceType = command.deviceType;
        String action = command.action;

        System.out.printf("[HomeSimulator] 处理命令: deviceType=%s, action=%s%n", deviceType, action);

        try {

            // 新增：处理请求所有设备状态的命令
            if ("request_all_status".equalsIgnoreCase(action)) {
                System.out.println("[HomeSimulator] 接收到获取所有设备状态的请求，正在上报...");
                furnitureManager.publishGlobalHomeStatus();
                return;
            }

            // 新增：处理能耗趋势图请求命令
            if (action.startsWith("get_energy_trend_")) {
                String deviceId = action.substring("get_energy_trend_".length());
                System.out.printf("[HomeSimulator] 接收到设备 %s 的能耗趋势图请求\n", deviceId);
                handleEnergyTrendRequest(deviceId);
                return;
            }
            // 新增：处理原始能耗数据请求命令
            if (action.startsWith("get_raw_energy_data_")) {
                String deviceId = action.substring("get_raw_energy_data_".length());
                System.out.printf("[HomeSimulator] 接收到设备 %s 的原始能耗数据请求\n", deviceId);
                handleRawEnergyDataRequest(deviceId);
                return;
            }

            // 新增：处理设置设备静默状态命令
            if (deviceType.equalsIgnoreCase("home") && action.startsWith("set_device_silent_")) {
                String params = action.substring("set_device_silent_".length());
                if (!params.isEmpty()) {
                    String[] parts = params.split(",");
                    if (parts.length == 2) {
                        String deviceId = parts[0];
                        boolean isSilent = Boolean.parseBoolean(parts[1]);
                        boolean result = furnitureManager.setDeviceSilentStatus(deviceId, isSilent);
                        System.out.printf("[HomeSimulator] 设置设备静默状态%s: %s -> %s\n",
                                result ? "成功" : "失败", deviceId, isSilent ? "静默" : "正常");
                    }
                }
                return;
            }
            switch (deviceType.toLowerCase()) {
                case "home":
                    handleHomeCommand(action);
                    break;
                case "light":
                    handleLightCommand(action);
                    break;
                case "ac":
                    handleAirConditionerCommand(action);
                    break;
                default:
                    System.err.println("[HomeSimulator] 未知的设备类型: " + deviceType);
            }

            // 命令处理后，家具会自动独立上报状态（无需手动触发汇总）

        } catch (Exception e) {
            System.err.println("[HomeSimulator] 处理命令时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理原始能耗数据请求
     */
    private void handleRawEnergyDataRequest(String deviceId) {
        try {
            // 检查设备是否存在
            Furniture device = furnitureManager.getAllFurniture().stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);

            if (device == null) {
                System.err.printf("[HomeSimulator] 未找到设备: %s\n", deviceId);
                return;
            }

            // 从缓存获取历史数据（过去24小时）
            List<EnergyDataHistory.EnergyDataPoint> historyData = energyDataHistory.getHistoryData(deviceId, "24h");

            if (historyData.isEmpty()) {
                System.out.printf("[HomeSimulator] 设备 %s 暂无足够的历史数据\n", deviceId);
                return;
            }

            // 通过DDS发送原始能耗数据到前端
            sendRawEnergyDataToFrontend(deviceId, device.getType(), historyData);

        } catch (Exception e) {
            System.err.println("[HomeSimulator] 处理原始能耗数据请求时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将原始能耗数据发送到前端
     */
    private void sendRawEnergyDataToFrontend(String deviceId, String deviceType, List<EnergyDataHistory.EnergyDataPoint> historyData) {
        try {
            EnergyRawData rawData = new EnergyRawData();
            int len = historyData.size();
            rawData.deviceId   = deviceId;
            rawData.deviceType = deviceType;
            rawData.currentPowerSeq.ensure_length(len, len);
            rawData.dailyConsumptionSeq.ensure_length(len, len);
            rawData.weeklyConsumptionSeq.ensure_length(len, len);
            rawData.timeSeq.ensure_length(len, len);
            // 遍历数据点并通过EnergyReportPublisher发送
            int i = 0;
            for (EnergyDataHistory.EnergyDataPoint point : historyData) {
                // 创建EnergyReport对象
//                EnergyReport report = new EnergyReport();
//                report.deviceId = deviceId;
//                report.deviceType = deviceType;
//                report.currentPower = point.getPowerConsumption();
//                report.dailyConsumption = 0; // 可以根据需要计算或设置
//                report.weeklyConsumption = 0; // 可以根据需要计算或设置
//
//                // 将时间戳格式化为字符串
//                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(point.getTimestamp(), 0, ZoneOffset.UTC);
//                report.timeStamp = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//
//                // 使用energyReportPublisher发送数据
//                if (energyReportPublisher != null) {
//                    energyReportPublisher.publishSingleReport(report);
//                }

                rawData.currentPowerSeq.set_at(i, point.getCurrentPower());
                rawData.dailyConsumptionSeq.set_at(i, point.getDailyConsumption());
                rawData.weeklyConsumptionSeq.set_at(i, point.getWeeklyConsumption());
                // 将时间戳格式化为字符串
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(point.getTimestamp()/1000, 0, ZoneOffset.UTC);
                rawData.timeSeq.set_at(i, dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                i++;
            }
            energyRawDataPublisher.publishEnergyRawData(rawData);

            System.out.printf("[HomeSimulator] 设备 %s 的原始能耗数据发送完成，共发送 %d 个数据点\n",
                    deviceId, historyData.size());

        } catch (Exception e) {
            System.err.println("[HomeSimulator] 发送原始能耗数据时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * 处理能耗趋势图请求
     */
    private void handleEnergyTrendRequest(String deviceId) {
        try {
            // 检查设备是否存在
            Furniture device = furnitureManager.getAllFurniture().stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);

            if (device == null) {
                System.err.printf("[HomeSimulator] 未找到设备: %s\n", deviceId);
                return;
            }

            // 生成报表ID
            String reportId = "energy_trend_" + deviceId + "_" + System.currentTimeMillis();

            // 从缓存获取历史数据（过去24小时）
            List<EnergyDataHistory.EnergyDataPoint> historyData = energyDataHistory.getHistoryData(deviceId, "24h");

            if (historyData.isEmpty()) {
                System.out.printf("[HomeSimulator] 设备 %s 暂无足够的历史数据生成趋势图\n", deviceId);
                return;
            }

            // 生成趋势图
            byte[] chartImageData = generateEnergyTrendChart(deviceId, device.getType(), historyData);

            if (chartImageData == null || chartImageData.length == 0) {
                System.err.println("[HomeSimulator] 生成趋势图失败");
                return;
            }

            // 通过ReportMediaPublisher发送图片
            if (reportMediaPublisher != null) {
                boolean result = reportMediaPublisher.publishReportMedia(
                        reportId,
                        "energy_trend",
                        deviceId,
                        chartImageData);

                if (result) {
                    System.out.printf("[HomeSimulator] 设备 %s 的能耗趋势图发送成功\n", deviceId);
                } else {
                    System.err.printf("[HomeSimulator] 设备 %s 的能耗趋势图发送失败\n", deviceId);
                }
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 处理能耗趋势图请求时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 生成能耗趋势图（不使用ChartUtilities）
     */
    private byte[] generateEnergyTrendChart(String deviceId, String deviceType, List<EnergyDataHistory.EnergyDataPoint> historyData) {
        try {
            if (historyData == null || historyData.isEmpty()) {
                System.out.println("[HomeSimulator] 没有足够的历史数据生成趋势图");
                return null;
            }

            System.out.printf("[HomeSimulator] 为设备 %s(%s) 生成能耗趋势图，使用%d个数据点\n",
                    deviceId, deviceType, historyData.size());

            // 创建数据集
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            TimeSeries series = new TimeSeries("能耗趋势");

            // 添加数据点到时间序列（修复时间戳问题）
            for (EnergyDataHistory.EnergyDataPoint point : historyData) {
                // 获取时间戳并验证
                long timestamp = point.getTimestamp();

                // 验证时间戳是否有效（转换为Date后年份在1900-9999范围内）
                Date date;
                if (timestamp < 0) {
                    // 如果时间戳为负，使用当前时间
                    date = new Date();
                    System.out.println("[HomeSimulator] 警告: 使用了负值时间戳，已替换为当前时间");
                } else {
                    // 检查时间戳是否在有效范围内
                    // 1900-01-01 00:00:00的时间戳约为-2208988800
                    // 9999-12-31 23:59:59的时间戳约为253402300799
                    if (timestamp < -2208988800L || timestamp > 253402300799L) {
                        // 使用当前时间，但保持数据点的相对顺序
                        long currentTime = System.currentTimeMillis() / 1000L;
                        // 为了保持数据点之间的相对间隔，将所有数据点映射到最近的时间段
                        long timeSpan = (currentTime - 86400L * 30); // 30天前到现在
                        int index = historyData.indexOf(point);
                        long adjustedTimestamp = timeSpan + (index * (86400L * 30 / historyData.size()));
                        date = new Date(adjustedTimestamp * 1000L);
                        System.out.println(date.toString());
                        System.out.println("[HomeSimulator] 警告: 时间戳超出有效范围，已调整到合理时间");
                    } else {
                        date = new Date(timestamp * 1000L);
                    }
                }

                try {
                    series.addOrUpdate(new Day(date), point.getCurrentPower());
                } catch (IllegalArgumentException e) {
                    // 如果Day构造仍然失败，使用替代方案
                    System.out.println("[HomeSimulator] Day构造失败，使用Millisecond替代: " + e.getMessage());
                    series.addOrUpdate(new Millisecond(date), point.getCurrentPower());
                }
            }
            dataset.addSeries(series);

            // 创建图表 - 使用中文标题和标签
            JFreeChart chart = ChartFactory.createTimeSeriesChart(
                    deviceId + "(" + deviceType + ") 能耗趋势", // 标题
                    "时间", // X轴标签 - 使用中文
                    "功率(W)", // Y轴标签 - 使用中文
                    dataset, // 数据集
                    true, // 是否显示图例
                    true, // 是否显示工具提示
                    false // 是否显示URL链接
            );

            // 设置图表背景色
            chart.setBackgroundPaint(Color.WHITE);

            // 自定义图表样式
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.LIGHT_GRAY);
            plot.setDomainGridlinePaint(Color.WHITE);
            plot.setRangeGridlinePaint(Color.WHITE);
            plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
            plot.setDomainCrosshairVisible(true);
            plot.setRangeCrosshairVisible(true);

            // 设置时间轴显示格式
            DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
            // 根据数据点数量设置合适的日期格式
            if (historyData.size() <= 7) {
                // 数据点较少时，显示完整日期和时间
                domainAxis.setDateFormatOverride(new SimpleDateFormat("yyyy-MM-dd HH:mm"));
            } else if (historyData.size() <= 30) {
                // 数据点适中时，显示日期
                domainAxis.setDateFormatOverride(new SimpleDateFormat("yyyy-MM-dd"));
            } else {
                // 数据点较多时，只显示月份和日期
                domainAxis.setDateFormatOverride(new SimpleDateFormat("MM-dd"));
            }
            // 设置轴标签字体，解决中文显示问题
            Font labelFont = new Font("SimHei", Font.PLAIN, 12);
            domainAxis.setLabelFont(labelFont);
            domainAxis.setTickLabelFont(labelFont);

            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setLabelFont(labelFont);
            rangeAxis.setTickLabelFont(labelFont);

            // 设置标题字体
            chart.getTitle().setFont(new Font("SimHei", Font.BOLD, 14));
            // 设置图例字体
            chart.getLegend().setItemFont(labelFont);

            // 替代方案：使用BufferedImage和ImageIO
            int width = 800;
            int height = 400;
            BufferedImage image = chart.createBufferedImage(width, height);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            baos.flush();
            byte[] imageData = baos.toByteArray();
            baos.close();

            System.out.println("[HomeSimulator] 成功生成能耗趋势图");
            return imageData;
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 生成能耗趋势图时发生错误: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void handleHomeCommand(String action) {
        switch (action.toLowerCase()) {
            case "light_on":
                turnOnAllLights();
                break;
            case "light_off":
                turnOffAllLights();
                break;
            case "ac_on":
                turnOnAllAirConditioners();
                break;
            case "ac_off":
                turnOffAllAirConditioners();
                break;
            default:
                System.err.println("[HomeSimulator] 未知的家居命令: " + action);
        }
    }


    private void handleLightCommand(String action) {
        String[] parts = action.split("_");
        if (parts.length < 3) {
            System.err.println("[HomeSimulator] 灯光命令格式错误: " + action);
            return;
        }
        String operation = parts[0];
        String lightId = parts[1];
        String param = parts[2];

        // 获取目标灯具
        Light targetLight = (Light) furnitureManager
                .getFurnitureByType("light").stream()
                .filter(light -> lightId.equals(light.getId()))
                .findFirst()
                .orElse(null);

        if (targetLight == null) {
            System.err.println("[HomeSimulator] 未找到灯具: " + lightId);
            return;
        }

        switch (operation.toLowerCase()) {
            case "switch":
                if ("on".equals(param)) {
                    turnOnLight(lightId);
                } else if ("off".equals(param)) {
                    turnOffLight(lightId);
                }
                break;
            case "brightness":
                try {
                    int brightness = Integer.parseInt(param);
                    targetLight.setBrightness(brightness);
                    targetLight.publishStatus();
                } catch (NumberFormatException e) {
                    System.err.println("[HomeSimulator] 亮度值格式错误: " + param);
                }
                break;
            case "temp":
                targetLight.setColorTemp(param);
                targetLight.publishStatus();
                break;
            case "mode":
                targetLight.setSceneMode(param);
                targetLight.publishStatus();
                break;
            default:
                System.err.println("[HomeSimulator] 未知的灯光操作: " + operation);
        }
    }

    // 添加单个灯具控制方法
    private void turnOnLight(String id) {
        furnitureManager.getFurnitureByType("light").stream()
                .filter(light -> id.equals(light.getId()))
                .findFirst()
                .ifPresent(light -> {
                    light.setStatus("on");
                    System.out.println("[HomeSimulator] 已开启灯具: " + id);
                });
    }

    private void turnOffLight(String id) {
        furnitureManager.getFurnitureByType("light").stream()
                .filter(light -> id.equals(light.getId()))
                .findFirst()
                .ifPresent(light -> {
                    light.setStatus("off");
                    System.out.println("[HomeSimulator] 已关闭灯具: " + id);
                });
    }

    private void handleAirConditionerCommand(String action) {
        String[] parts = action.split("_");
        if (parts.length < 2) {
            System.err.println("[HomeSimulator] 空调命令格式错误: " + action);
            return;
        }

        String operation = parts[0];
        String acId = parts[1];
        AirConditioner targetAc = (AirConditioner) furnitureManager
                .getFurnitureByType("ac")
                .stream()
                .filter(ac -> acId.equals(ac.getId()))
                .findFirst()
                .orElse(null);

        if (targetAc == null) {
            System.err.println("[HomeSimulator] 未找到空调: " + acId);
            return;
        }

        switch (operation.toLowerCase()) {
            case "switch":
                if (parts.length >= 3) {
                    String state = parts[2];
                    if ("on".equals(state)) {
                        targetAc.setOn(true);
                    } else if ("off".equals(state)) {
                        targetAc.setOn(false);
                    }
                    targetAc.publishStatus();
                }
                break;
            case "cool":
                targetAc.setCoolingMode(true);
                targetAc.publishStatus();
                break;
            case "swing":
                targetAc.setSwingMode(!targetAc.isSwingMode());
                targetAc.publishStatus();
                break;
            case "dehumidify":
                targetAc.setDehumidificationMode(!targetAc.isDehumidificationMode());
                targetAc.publishStatus();
                break;
            case "temp":
                if (parts.length >= 3) {
                    try {
                        int temp = Integer.parseInt(parts[2]);
                        targetAc.setTemperature(temp);
                        targetAc.publishStatus();
                    } catch (NumberFormatException e) {
                        System.err.println("[HomeSimulator] 温度值格式错误: " + parts[2]);
                    }
                }
                break;
            default:
                System.err.println("[HomeSimulator] 未知的空调操作: " + operation);
        }
    }

    private void turnOnAllLights() {
        furnitureManager.getFurnitureByType("light").forEach(light -> {
            light.setStatus("on");
        });
        System.out.println("[HomeSimulator] 已开启所有灯光");
    }

    private void turnOffAllLights() {
        furnitureManager.getFurnitureByType("light").forEach(light -> {
            light.setStatus("off");
        });
        System.out.println("[HomeSimulator] 已关闭所有灯光");
    }

    private void turnOnAllAirConditioners() {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus("cool");
        });
        System.out.println("[HomeSimulator] 已开启所有空调");
    }

    private void turnOffAllAirConditioners() {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus("off");
        });
        System.out.println("[HomeSimulator] 已关闭所有空调");
    }

    private void setAllAirConditionersMode(String mode) {
        furnitureManager.getFurnitureByType("ac").forEach(ac -> {
            ac.setStatus(mode);
        });
        System.out.printf("[HomeSimulator] 已设置所有空调为%s模式%n", mode);
    }

    private void keepRunning() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                shutdown();
            }
        }));

        // 启动控制台交互线程
        startConsoleInteraction();

        try {
            while (running.get()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 启动控制台交互功能
     */
    private void startConsoleInteraction() {
        Thread consoleThread = new Thread(() -> {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            System.out.println("=== 控制台报警控制 ===");
            System.out.println("输入命令触发报警：");
            System.out.println("  lt1 - 触发light1状态异常报警");
            System.out.println("  lh1 - 触发light1过热报警");
            System.out.println("  at1 - 触发ac1温度异常报警");
            System.out.println("  ap1 - 触发ac1性能异常报警");
            System.out.println("  sendimg - 发送图片到Mobile端");
            System.out.println("  q   - 退出程序");
            System.out.println("====================");

            while (running.get()) {
                System.out.print("\n报警控制> ");
                String input = scanner.nextLine().trim().toLowerCase();

                switch (input) {
                    case "lt1":
                        triggerLightAlert("light1", "status");
                        break;
                    case "lh1":
                        triggerLightAlert("light1", "overheat");
                        break;
                    case "at1":
                        triggerAirConditionerAlert("ac1", "temperature");
                        break;
                    case "ap1":
                        triggerAirConditionerAlert("ac1", "performance");
                        break;
                    case "sendimg":
                        sendImageToMobile();
                        break;
                    case "q":
                        System.out.println("正在退出...");
                        shutdown();
                        break;
                    default:
                        System.out.println("无效命令，请重新输入");
                }
            }
            scanner.close();
        });
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    /**
     * 读取图片文件并转换为字节数组
     */
    private byte[] readImageFile(String imagePath) throws IOException {
        File file = new File(imagePath);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("图片文件不存在: " + imagePath);
        }

        System.out.println("[HomeSimulator] 正在读取图片文件: " + imagePath + ", 大小: " + file.length() + " bytes");
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }
        return data;
    }

    /**
     * 发送图片到MobileAppSimulator
     */
    private void sendImageToMobile() {
        try {
            java.util.Scanner scanner = new java.util.Scanner(System.in);

            System.out.print("请输入图片文件路径: ");
            String imagePath = scanner.nextLine().trim();

            // 默认使用light1设备ID和类型
            String deviceId = "light1";
            String deviceType = "light";
            int alertId=(int) (System.currentTimeMillis() % 1000000);
            // 读取图片文件
            byte[] imageData = readImageFile(imagePath);

            // 通过mediaPublisher发送图片
            if (mediaPublisher != null) {
                // 媒体类型：1代表图片
                boolean result = mediaPublisher.publishMedia(deviceId, deviceType, 1, imageData,alertId);

                if (result) {
                    System.out.println("[HomeSimulator] 图片发送成功！Mobile端应该能够接收到图片数据");
                } else {
                    System.err.println("[HomeSimulator] 图片发送失败");
                }
            } else {
                System.err.println("[HomeSimulator] MediaPublisher未初始化");
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 发送图片时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * 触发灯具报警
     */
    private void triggerLightAlert(String deviceId, String alertType) {
        try {
            Light light = (Light)
                furnitureManager.getFurnitureByType("light").stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);
            
            if (light != null) {
                if ("status".equals(alertType)) {
                    light.triggerStatusAbnormalAlert();
                } else if ("overheat".equals(alertType)) {
                    light.triggerOverheatAlert();
                }
            } else {
                System.out.println("未找到设备: " + deviceId);
            }
        } catch (Exception e) {
            System.out.println("触发报警失败: " + e.getMessage());
        }
    }

    /**
     * 触发空调报警
     */
    private void triggerAirConditionerAlert(String deviceId, String alertType) {
        try {
            AirConditioner ac = (AirConditioner)
                furnitureManager.getFurnitureByType("ac").stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);
            
            if (ac != null) {
                if ("temperature".equals(alertType)) {
                    ac.triggerTemperatureAbnormalAlert();
                } else if ("performance".equals(alertType)) {
                    ac.triggerPerformanceAbnormalAlert();
                }
            } else {
                System.out.println("未找到设备: " + deviceId);
            }
        } catch (Exception e) {
            System.out.println("触发报警失败: " + e.getMessage());
        }
    }

    /**
     * 重置设备报警
     */
    private void resetDeviceAlert(String deviceId) {
        try {
            // 先从所有设备中查找，不限制类型
            Furniture device = furnitureManager.getFurnitureByType("light").stream()
                .filter(f -> deviceId.equals(f.getId()))
                .findFirst()
                .orElse(null);
            if (device == null) {
                device = furnitureManager.getFurnitureByType("ac").stream()
                    .filter(f -> deviceId.equals(f.getId()))
                    .findFirst()
                    .orElse(null);
            }

            if (device instanceof AlertableDevice) {
                ((AlertableDevice) device).resetAlert();
                System.out.println("已重置设备报警: " + deviceId);
                
                // 确保报警系统也清除该设备的报警
                if (alertSystem != null) {
                    alertSystem.clearDeviceAlert(deviceId);
                }
            } else {
                System.out.println("设备不支持报警重置: " + deviceId);
            }
        } catch (Exception e) {
            System.out.println("重置报警失败: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (!running.get()) {
            return;
        }

        System.out.println("[HomeSimulator] 正在关闭家居模拟器...");
        running.set(false);

        // 关闭家具管理器（包含定时上报任务）
        if (furnitureManager != null) {
            furnitureManager.stop();
        }
        
        if (alertSystem != null) {
            alertSystem.stop();
        }

        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }

        if (ddsParticipant != null) {
            ddsParticipant.close();
        }
        // ======== 新增：停止Presence定时发送任务 ========
        if (presenceScheduler != null) {
            presenceScheduler.shutdownNow();
        }

        // 新增：停止能耗报告发布器
        if (energyReportPublisher != null) {
            energyReportPublisher.stop();
        }
        // 新增：停止ReportMediaPublisher和释放EnergyDataHistory资源
        if (energyDataHistory != null) {
            energyDataHistory.shutdown();
        }

        System.out.println("[HomeSimulator] 家居模拟器已关闭");
    }

    public static void main(String[] args) {
        System.out.println("[HomeSimulator] 启动家居模拟器...");
        HomeSimulator simulator = new HomeSimulator();
        simulator.start();
    }
}
