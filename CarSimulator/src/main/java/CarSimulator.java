import CarSimulator.DDS.*;
import CarSimulator.VehicleHealthManager;
import CarSimulator.DDS.AIHealthReportPublisher;
import IDL.*;
import IDL.AIVehicleHealthReportTypeSupport;
import com.zrdds.topic.Topic;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CarSimulator {
    private static final String VEHICLE_ID = "car_001";
    private static final String VEHICLE_MEDIA_TOPIC = "VehicleMedia";
    private static final String VEHICLE_MEDIA_PATH = "resources/images/";
    private static boolean hasLoad = false;
    private Topic vehicleMediaTopic;

    // DDS Components
    private DdsParticipant ddsParticipant;
    private CommandSubscriber commandSubscriber;
    private StatusPublisher statusPublisher;
    private VehicleHealthPublisher vehicleHealthPublisher;
    private MediaPublisher mediaPublisher;
    private AIHealthReportPublisher aiHealthReportPublisher;

    // Schedulers
    private ScheduledExecutorService statusUpdater; // For realistic status changes
    private ScheduledExecutorService statusReporter; // For publishing status
    private ScheduledExecutorService healthReporter; // For publishing health reports

    // Vehicle State
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean engineOn = false;
    private boolean doorsLocked = true;
    private float fuelPercent = 100.0f;
    private boolean acOn = false;
    private String location = "Garage";
    private String timeStamp;
    private long engineRunningTime = 0;

    // Modules
    private CarSimulatorAlert alertSystem;
    private VehicleHealthManager vehicleHealthManager;

    // Constants
    private static final float BASE_FUEL_CONSUMPTION = 0.08f;
    private static final float AC_EXTRA_CONSUMPTION = 0.04f;

    public CarSimulator() {
        loadLibrary();
        alertSystem = new CarSimulatorAlert();
        vehicleHealthManager = new VehicleHealthManager(VEHICLE_ID);
        timeStamp = getCurrentTimeStamp();
    }

    private void loadLibrary() {
        if (!hasLoad) {
            try {
                System.loadLibrary("ZRDDS_JAVA");
                hasLoad = true;
                System.out.println("[CarSimulator] ZRDDS_JAVA库加载成功");
            } catch (UnsatisfiedLinkError e) {
                System.err.println("[CarSimulator] 警告: 无法加载ZRDDS_JAVA库，DDS功能将不可用");
                System.err.println("[CarSimulator] 请确保ZRDDS_JAVA库在系统路径中");
                System.exit(1);
            }
        }
    }

    public void start() {
        System.out.println("[CarSimulator] 启动车辆模拟器...");

        try {
            running.set(true);
            initDDS();
            startSchedulers();
            startConsoleInteraction();
            keepRunning();
        } catch (Exception e) {
            System.err.println("[CarSimulator] 启动过程中发生严重错误: " + e.getMessage());
            e.printStackTrace();
            shutdown();
        }
    }

    private void startSchedulers() {
        startRealisticStatusUpdates();
        startStatusReporting();
        startHealthReporting();
    }

    private void startRealisticStatusUpdates() {
        statusUpdater = Executors.newSingleThreadScheduledExecutor();

        // 每1秒更新一次状态
        statusUpdater.scheduleAtFixedRate(() -> {
            if (running.get()) {
                updateFuelConsumption(); // 更新油耗
                updateLocationByDriving(); // 根据行驶状态更新位置
            }
        }, 0, 1, TimeUnit.SECONDS);

        System.out.println("[CarSimulator] 车辆状态自动更新已启动");
    }

    // 根据发动机和空调状态更新油耗
    private void updateFuelConsumption() {
        if (engineOn && fuelPercent > 0) {
            // 基础油耗 + 空调额外油耗
            float totalConsumption = BASE_FUEL_CONSUMPTION + (acOn ? AC_EXTRA_CONSUMPTION : 0);
            fuelPercent = Math.max(0, fuelPercent - totalConsumption);

            // 每10秒打印一次油耗变化（避免日志刷屏）
            if (System.currentTimeMillis() % 10000 < 1000) {
                System.out.printf("[CarSimulator] 油耗更新: 当前油量=%.1f%%%n", fuelPercent);
            }

            // 低油量报警（燃油低于15%时触发）
            if (fuelPercent <= 15 && fuelPercent + totalConsumption > 15) {
                alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.LOW_FUEL, "MEDIUM");
            }

            // 燃油耗尽时自动关闭发动机
            if (fuelPercent == 0) {
                stopEngine();
                System.out.println("[CarSimulator] 燃油耗尽，发动机已自动关闭");
            }
        }
    }

    // 根据发动机运行时间更新位置（仅使用location、engineOn）
    private void updateLocationByDriving() {
        if (engineOn) {
            engineRunningTime++;
            // 发动机运行30秒后随机更新位置（模拟行驶）
            if (engineRunningTime % 30 == 0) {
                String[] locations = {"Street", "Highway", "Parking Lot", "Store", "Garage"};
                String newLocation = locations[(int)(Math.random() * locations.length)];
                // 避免与当前位置重复
                if (!newLocation.equals(location)) {
                    location = newLocation;
                    System.out.printf("[CarSimulator] 行驶中，位置更新为: %s%n", location);
                    reportCurrentStatus(); // 位置变化时立即上报
                }
            }
        } else {
            engineRunningTime = 0; // 发动机关闭时重置运行时间
        }
    }

    private void initDDS() {
        try {
            ddsParticipant = DdsParticipant.getInstance();

            registerDdsTypes();

            Topic commandTopic = ddsParticipant.createTopic("Command", CommandTypeSupport.get_instance());
            Topic vehicleStatusTopic = ddsParticipant.createTopic("VehicleStatus", VehicleStatusTypeSupport.get_instance());
            Topic vehicleHealthTopic = ddsParticipant.createTopic("VehicleHealthReport", VehicleHealthReportTypeSupport.get_instance());
            Topic aiHealthReportTopic = ddsParticipant.createTopic("AIVehicleHealthReport", AIVehicleHealthReportTypeSupport.get_instance());

            // 新增：创建VehicleMedia主题
            System.out.println("[CarSimulator] 创建VehicleMedia主题");
            vehicleMediaTopic = ddsParticipant.createTopic(VEHICLE_MEDIA_TOPIC, AlertMediaTypeSupport.get_instance());
            if (vehicleMediaTopic == null) {
                System.err.println("[CarSimulator] 创建VehicleMedia主题失败");
            } else {
                System.out.println("[CarSimulator] VehicleMedia主题创建成功");
            }

            commandSubscriber = new CommandSubscriber();
            commandSubscriber.setCommandHandler(this::handleCommand);
            commandSubscriber.start(ddsParticipant.getSubscriber(), commandTopic);

            statusPublisher = new StatusPublisher();
            statusPublisher.start(ddsParticipant.getPublisher(), vehicleStatusTopic);

            vehicleHealthPublisher = new VehicleHealthPublisher();
            vehicleHealthPublisher.start(ddsParticipant.getPublisher(), vehicleHealthTopic);

            // 初始化AI健康报告发布器
            aiHealthReportPublisher = new AIHealthReportPublisher();
            aiHealthReportPublisher.start(ddsParticipant.getPublisher(), aiHealthReportTopic);

            // 新增：初始化媒体发布器
            initializeMediaPublisher();

            alertSystem.initialize(ddsParticipant, this);
            alertSystem.startMonitoring();

            reportCurrentStatus();

            System.out.println("[CarSimulator] DDS初始化完成");
        } catch (Exception e) {
            System.err.println("[CarSimulator] DDS初始化失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("DDS Initialization failed", e);
        }
    }

    //初始化媒体发布器的方法
    private void initializeMediaPublisher() {
        try {
            System.out.println("[CarSimulator] 开始初始化车辆媒体发布器");

            if (ddsParticipant == null) {
                System.err.println("[CarSimulator] DDS Participant为null");
                return;
            }

            if (ddsParticipant.getPublisher() == null) {
                System.err.println("[CarSimulator] DDS Publisher为null");
                return;
            }

            if (vehicleMediaTopic == null) {
                System.err.println("[CarSimulator] VehicleMedia主题为null");
                return;
            }

            // 注册AlertMedia类型
            System.out.println("[CarSimulator] 注册AlertMedia类型");
            AlertMediaTypeSupport.get_instance().register_type(
                    ddsParticipant.getDomainParticipant(), "AlertMedia");

            // 初始化MediaPublisher
            System.out.println("[CarSimulator] 创建MediaPublisher实例");
            mediaPublisher = new MediaPublisher();

            System.out.println("[CarSimulator] 启动MediaPublisher");
            boolean started = mediaPublisher.start(ddsParticipant.getPublisher(), vehicleMediaTopic);

            if (started) {
                System.out.println("[CarSimulator] 车辆媒体发布器初始化成功");
            } else {
                System.err.println("[CarSimulator] 车辆媒体发布器初始化失败");
                mediaPublisher = null;
            }
        } catch (Exception e) {
            System.err.println("[CarSimulator] 初始化车辆媒体发布器时发生错误: " + e.getMessage());
            e.printStackTrace();
            mediaPublisher = null;
        }
    }

    /**
     * 在图片右下角添加时间戳
     * @param originalImage 原始图片数据
     * @return 添加时间戳后的图片数据
     */
    private byte[] addTimestampToImage(byte[] originalImage) {
        try {
            // 将字节数组转换为BufferedImage
            ByteArrayInputStream bais = new ByteArrayInputStream(originalImage);
            BufferedImage image = ImageIO.read(bais);

            // 创建图形上下文
            Graphics2D g2d = image.createGraphics();

            // 设置字体和颜色
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.setColor(Color.WHITE);

            // 设置抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 获取当前时间
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            // 计算文本尺寸
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(timestamp);
            int textHeight = fm.getHeight();

            // 设置文本位置（右下角）
            int x = image.getWidth() - textWidth - 30; // 距离右边10像素
            int y = image.getHeight() - 10; // 距离底部10像素

            // 添加黑色背景以增强可读性
            g2d.setColor(Color.BLACK);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)); // 半透明背景
            g2d.fillRect(x - 5, y - textHeight + fm.getDescent(), textWidth + 10, textHeight);

            // 绘制白色时间戳文本
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // 不透明文本
            g2d.setColor(Color.WHITE);
            g2d.drawString(timestamp, x, y);

            // 释放资源
            g2d.dispose();

            // 将修改后的图片转换回字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String formatName = "jpg";
            if (originalImage.length > 4 &&
                    (originalImage[0] == (byte) 0x89 && originalImage[1] == (byte) 0x50 &&
                            originalImage[2] == (byte) 0x4E && originalImage[3] == (byte) 0x47)) {
                formatName = "png"; // 检测PNG格式
            }
            ImageIO.write(image, formatName, baos);

            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("[CarSimulator] 添加时间戳到图片时发生错误: " + e.getMessage());
            // 出错时返回原始图片
            return originalImage;
        }
    }

    private void registerDdsTypes() {
        System.out.println("[CarSimulator] 注册DDS类型");
        CommandTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "Command");
        VehicleStatusTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "VehicleStatus");
        VehicleHealthReportTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "VehicleHealthReport");
        AIVehicleHealthReportTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "AIVehicleHealthReport");
        // 新增：注册AlertMedia类型
        AlertMediaTypeSupport.get_instance().register_type(ddsParticipant.getDomainParticipant(), "AlertMedia");
        System.out.println("[CarSimulator] DDS类型注册完成");
    }
    private void handleCommand(Command command) {
        if (command == null || command.deviceType == null || command.action == null) {
            System.err.println("[CarSimulator] 接收到无效命令");
            return;
        }

        String deviceType = command.deviceType;
        String action = command.action;

        System.out.printf("[CarSimulator] 处理命令: deviceType=%s, action=%s%n", deviceType, action);

        try {
            // 仅处理car类型的命令
            if ("car".equalsIgnoreCase(deviceType)) {
                handleCarCommand(action);
            }
        } catch (Exception e) {
            System.err.println("[CarSimulator] 处理命令时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleCarCommand(String action) {
        switch (action.toLowerCase()) {
            case "engine_on":
                startEngine();
                break;
            case "engine_off":
                stopEngine();
                break;
            case "lock":
                lockDoors();
                break;
            case "unlock":
                unlockDoors();
                break;
            case "refuel":
                refuel();
                break;
            // 添加空调控制命令
            case "ac_on":
                turnOnAc();
                break;
            case "ac_off":
                turnOffAc();
                break;
            case"get_status":
                reportCurrentStatus();
                break;
            case "car":
                // 处理AI健康分析请求
                System.out.println("[CarSimulator] 接收到AI健康分析请求，开始执行分析...");
                analyzeVehicleHealth();
                break;
            case "analyze_health":
                System.out.println("[CarSimulator] 执行健康分析命令 - 调用大模型API");
                // 获取最新的健康报告
                VehicleHealthReport healthReport = vehicleHealthManager.generateReport();
                if (healthReport != null) {
                    System.out.println("[CarSimulator] 车辆健康报告生成完成，开始AI分析...");
                    
                    // 异步发布AI健康分析报告
                    long startTime = System.currentTimeMillis();
                    aiHealthReportPublisher.analyzeAndPublishHealthReportAsync(healthReport)
                        .thenAccept(success -> {
                            long endTime = System.currentTimeMillis();
                            System.out.println("[CarSimulator] AI分析总耗时: " + (endTime - startTime) + "ms");
                            
                            if (success) {
                                System.out.println("[CarSimulator] ✅ 大模型AI健康分析报告发布成功");
                            } else {
                                System.err.println("[CarSimulator] ❌ 大模型AI健康分析报告发布失败");
                            }
                        })
                        .exceptionally(throwable -> {
                            long endTime = System.currentTimeMillis();
                            System.err.println("[CarSimulator] AI健康分析异步处理失败，耗时: " + (endTime - startTime) + "ms");
                            System.err.println("[CarSimulator] 错误详情: " + throwable.getMessage());
                            throwable.printStackTrace();
                            return null;
                        });
                    System.out.println("[CarSimulator] AI健康分析请求已提交，正在后台处理...");
                } else {
                    System.err.println("[CarSimulator] 无法获取车辆健康报告");
                }
                break;
            default:
                System.out.println("[CarSimulator] 未知命令: " + action);
        }
    }

    private void startEngine() {
        if (fuelPercent <= 0) {
            System.out.println("[CarSimulator] 油量不足，无法启动发动机！");
            // 触发低油量报警
            alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.LOW_FUEL, "HIGH");
            return;
        }

        if (!engineOn) {
            engineOn = true;
            // 重置运行时间
            engineRunningTime = 0;
            System.out.println("[CarSimulator] 发动机已启动");
            statusPublisher.updateEngineStatus(true);
            // 状态变化时上报
            reportCurrentStatus();
        } else {
            System.out.println("[CarSimulator] 发动机已经在运行");
        }
    }

    // 添加空调控制方法
    private void turnOnAc() {
        if (!acOn) {
            acOn = true;
            System.out.println("[CarSimulator] 空调已开启");
            reportCurrentStatus(); // 状态变化时上报
        } else {
            System.out.println("[CarSimulator] 空调已经开启");
        }
    }

    private void turnOffAc() {
        if (acOn) {
            acOn = false;
            System.out.println("[CarSimulator] 空调已关闭");
            reportCurrentStatus(); // 状态变化时上报
        } else {
            System.out.println("[CarSimulator] 空调已经关闭");
        }
    }
    private void stopEngine() {
        if (engineOn) {
            engineOn = false;
            System.out.println("[CarSimulator] 发动机已关闭");
            statusPublisher.updateEngineStatus(false);
        } else {
            System.out.println("[CarSimulator] 发动机已经关闭");
        }
    }

    private void lockDoors() {
        if (!doorsLocked) {
            doorsLocked = true;
            System.out.println("[CarSimulator] 车门已上锁");
            statusPublisher.updateDoorStatus(true);
        } else {
            System.out.println("[CarSimulator] 车门已经上锁");
        }
    }

    private void unlockDoors() {
        if (doorsLocked) {
            doorsLocked = false;
            System.out.println("[CarSimulator] 车门已解锁");
            statusPublisher.updateDoorStatus(false);
        } else {
            System.out.println("[CarSimulator] 车门已经解锁");
        }
    }

    private void refuel() {
        fuelPercent = 100.0f;
        System.out.println("[CarSimulator] 加油完成，当前油量: 100%");
        statusPublisher.updateFuelLevel(fuelPercent);
    }

    /**
     * 分析车辆健康状况
     * 获取最新的车辆健康报告并调用AI进行分析
     */
    private void analyzeVehicleHealth() {
        try {
            System.out.println("[CarSimulator] 开始分析车辆健康状况...");
            
            // 检查AI健康报告发布器是否已初始化
            if (aiHealthReportPublisher == null) {
                System.err.println("[CarSimulator] AI健康报告发布器未初始化");
                return;
            }
            
            // 获取最新的车辆健康报告
            VehicleHealthReport latestReport = vehicleHealthManager.getLatestReport();
            
            if (latestReport == null) {
                System.out.println("[CarSimulator] 没有可用的车辆健康报告，先生成一份新报告...");
                // 如果没有最新报告，先生成一份
                latestReport = vehicleHealthManager.generateReport();
                // 发布健康报告
                vehicleHealthPublisher.publish(latestReport);
            }
            
            // 调用AI分析并发布结果
            boolean success = aiHealthReportPublisher.analyzeAndPublishHealthReport(latestReport);
            
            if (success) {
                System.out.println("[CarSimulator] ✅ 车辆健康分析完成并已发布AI报告");
            } else {
                System.err.println("[CarSimulator] ❌ 车辆健康分析失败");
            }
            
        } catch (Exception e) {
            System.err.println("[CarSimulator] 分析车辆健康状况时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean sendMedia(String deviceId, String deviceType, int mediaType, byte[] fileData, int alertId) {
        if (mediaPublisher == null) {
            System.err.println("[CarSimulator] 媒体发布器未初始化或初始化失败");
            return false;
        }

        boolean result = mediaPublisher.publishMedia(deviceId, deviceType, mediaType, fileData, alertId);
        if (!result) {
            System.err.println("[CarSimulator] 媒体数据发送失败");
        }
        return result;
    }

    // 获取定时发送的图片数据
    private byte[] getPeriodicImageData() {
        try {
            // 构建相对于项目根目录的完整路径
            String projectDir = System.getProperty("user.dir");
            String imagePath = projectDir + File.separator +"src"+File.separator+"main"+File.separator+ "resources" + File.separator + "images" + File.separator + "testImage.jpg";
            File imageFile = new File(imagePath);

            if (imageFile.exists() && imageFile.isFile()) {
                byte[] data = new byte[(int) imageFile.length()];
                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    fis.read(data);
                }
                // 在图片上添加时间戳
                return addTimestampToImage(data);
            } else {
                // 如果文件不存在，返回一个默认的图片数据
                System.out.println("[CarSimulator] 未找到图片文件: " + imagePath);
                // 返回一个简单的示例数据（PNG文件头）并添加时间戳
                byte[] defaultImage = new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
                return addTimestampToImage(defaultImage);
            }
        } catch (Exception e) {
            System.err.println("[CarSimulator] 获取图片数据失败: " + e.getMessage());
            return new byte[0];
        }
    }

    private void sendVehicleStatusImage() {
        try {
            // 生成一个唯一的ID用于本次图片发送
            int imageAlertId = (int) (System.currentTimeMillis() % 1000000);
            String deviceId = "car_001";
            String deviceType = "car";
            int mediaType = 1; // 1表示图片

            // 获取图片数据
            byte[] mediaData = getPeriodicImageData();

            if (mediaData != null && mediaData.length > 8) { // 检查是否是有效的图片数据
                System.out.printf("[CarSimulator] 发送车辆状态图片 (ID: %d)...\n", imageAlertId);
                boolean result = sendMedia(deviceId, deviceType, mediaType, mediaData, imageAlertId);
                if (result) {
                    System.out.println("[CarSimulator] 车辆状态图片发送成功");
                } else {
                    System.out.println("[CarSimulator] 车辆状态图片发送失败");
                }
            } else {
                System.out.println("[CarSimulator] 无法获取有效的图片数据，跳过本次发送。");
            }
        } catch (Exception e) {
            System.err.println("[CarSimulator] 发送车辆状态图片时发生错误: " + e.getMessage());
        }
    }

    private void startStatusReporting() {
        statusReporter = Executors.newSingleThreadScheduledExecutor();

        // 每15秒上报一次状态
        statusReporter.scheduleWithFixedDelay(() -> {
            if (running.get()) {
                reportCurrentStatus();
            }
        }, 0, 15, TimeUnit.SECONDS);

        System.out.println("[CarSimulator] 状态上报已启动");
    }

    private void startHealthReporting() {
        healthReporter = Executors.newSingleThreadScheduledExecutor();

        // 每30秒上报一次健康状况
        healthReporter.scheduleAtFixedRate(() -> {
            if (running.get()) {
                try {
                    VehicleHealthReport report = vehicleHealthManager.generateReport();
                    vehicleHealthPublisher.publish(report);
                } catch (Exception e) {
                    System.err.println("[CarSimulator] 发布车辆健康报告时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, 30, TimeUnit.SECONDS);

        System.out.println("[CarSimulator] 车辆健康报告已启动");
    }

    private void reportCurrentStatus() {
        if (statusPublisher == null) {
            return;
        }

        try {
            // 每次上报时都获取当前时间戳
            String currentTimeStamp = getCurrentTimeStamp();
            System.out.println("[CarSimulator] 发布车辆状态: engineOn=" + engineOn + ", doorsLocked=" + doorsLocked + ", acOn=" + acOn + ", fuelPercent=" + fuelPercent + ", location=" + location + ", timeStamp=" + currentTimeStamp);
            statusPublisher.publishVehicleStatus(engineOn, doorsLocked, acOn, fuelPercent, location, currentTimeStamp);

            // 发送车辆状态图片
            sendVehicleStatusImage();

            // 更新内部状态的时间戳
            this.timeStamp = currentTimeStamp;
        } catch (Exception e) {
            System.err.println("[CarSimulator] 上报状态时发生错误: " + e.getMessage());
        }
    }
    
    private String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
    
    /**
     * 获取当前车辆状态的快照，包含最新时间戳
     */
    public String getCurrentStatusSnapshot() {
        return String.format("时间: %s, 发动机: %s, 车门: %s, 空调: %s, 油量: %.1f%%, 位置: %s",
                getCurrentTimeStamp(),
                engineOn ? "开启" : "关闭",
                doorsLocked ? "上锁" : "解锁",
                acOn ? "开启" : "关闭",
                fuelPercent,
                location);
    }

    private void startConsoleInteraction() {
        Thread consoleThread = new Thread(() -> {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            while (running.get()) {
                System.out.println("请输入命令 (lf: low_fuel, eo: engine_overheat, du: door_unlocked, exit): ");
                String input = scanner.nextLine().trim();

                switch (input.toLowerCase()) {
                    case "lf":
                        alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.LOW_FUEL, "LOW");
                        break;
                    case "eo":
                        alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.ENGINE_OVERHEAT, "HIGH");
                        break;
                    case "du":
                        alertSystem.triggerAlert(CarSimulatorAlert.CarAlertType.DOOR_UNLOCKED, "MEDIUM");
                        break;
                    case "exit":
                        System.out.println("正在退出...");
                        shutdown();
                        break;
                    default:
                        System.out.println("未知命令: " + input + ". 可用命令: lf, eo, du, exit");
                }
            }
            scanner.close();
        });
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void keepRunning() {
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                shutdown();
            }
        }));

        // 保持主线程运行
        try {
            while (running.get()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        if (!running.get()) {
            return;
        }

        System.out.println("[CarSimulator] 正在关闭车辆模拟器...");
        running.set(false);

        shutdownSchedulers();
        shutdownDds();

        System.out.println("[CarSimulator] 车辆模拟器已关闭");
    }

    private void shutdownSchedulers() {
        shutdownExecutor(statusUpdater, "Status Updater");
        shutdownExecutor(statusReporter, "Status Reporter");
        shutdownExecutor(healthReporter, "Health Reporter");
    }

    private void shutdownDds() {
        if (alertSystem != null) {
            alertSystem.close();
        }
        if (commandSubscriber != null) {
            commandSubscriber.stop();
        }
        if (ddsParticipant != null) {
            ddsParticipant.close();
        }
    }

    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("[" + name + "] 已关闭");
        }
    }

    public boolean isEngineOn() { return engineOn; }
    public boolean isDoorsLocked() { return doorsLocked; }
    public boolean isAcOn() { return acOn; }
    public float getFuelPercent() { return fuelPercent; }
    public String getLocation() { return location; }

    public static void main(String[] args) {
        System.out.println("[CarSimulator] 启动车辆模拟器...");
        CarSimulator car = new CarSimulator();
        car.start();
    }
}
