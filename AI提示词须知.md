# AI提示词须知

## 项目概述

本项目是一个基于DDS（数据分发服务）实现的人、家、车智慧控制系统。系统通过自研DDS中间件实现设备间的实时通信，支持家庭设备和车辆的远程监控与控制。

## 技术架构

### DDS通信框架

本项目使用DDS（Data Distribution Service）作为核心通信中间件。DDS是一种面向实时系统的发布-订阅通信协议，具有以下特点：

1. **发布-订阅模式**：设备可以发布状态信息和订阅感兴趣的主题，实现松耦合通信
2. **实时性**：低延迟、高吞吐量的数据传输，满足智能家居和车联网场景的实时控制需求
3. **可靠性**：支持QoS（服务质量）策略，确保关键消息的可靠传递
4. **可扩展性**：易于添加新设备和新功能，无需修改现有系统架构

### 数据结构定义

系统使用IDL（接口定义语言）文件`SmartDemo.idl`统一定义了所有通信数据结构。主要数据类型包括：

1. **Presence**：设备在线状态信息，用于设备发现和网络拓扑维护
2. **HomeStatus**：家庭设备状态信息，包含多个家具设备的状态数据
3. **VehicleStatus**：车辆状态信息，包含车辆的各项状态数据
4. **Alert**：报警信息，用于通知异常状态
5. **AlertMedia**：报警相关的多媒体数据，如图片、视频等
6. **Command**：控制命令，用于远程控制设备

### 局域网与云端通信

系统支持两种通信模式：

1. **局域网通信**：当设备在同一局域网内时，通过DDS直接通信，无需经过云端
2. **云端转发**：当设备不在同一局域网时，通过CloudRouter进行消息转发

## 子模块说明

系统由三个主要子模块组成：

1. **HomeSimulator**：模拟家庭设备，包含多个家具线程
2. **CarSimulator**：模拟车辆设备
3. **CloudRouterSim**：模拟云端路由器，负责跨网络的消息转发

## 开发注意事项

1. **Topic命名规范**：Topic名称应遵循`{模块名}/{数据类型}`的格式，如`Home/Status`
2. **QoS配置**：根据数据重要性和实时性要求，选择合适的QoS策略
3. **异常处理**：妥善处理通信异常，确保系统稳定性
4. **数据一致性**：确保发布的数据结构符合IDL定义
5. **性能优化**：避免发送过大的数据包，合理设置发布频率

## 测试方法

1. **模拟器测试**：使用HomeSimulator和CarSimulator进行功能测试
2. **网络切换测试**：测试设备在局域网内外的通信切换
3. **异常恢复测试**：测试网络中断后的系统恢复能力

## 常见问题

1. **Topic不匹配**：检查Topic名称是否一致，大小写敏感
2. **QoS不兼容**：检查发布者和订阅者的QoS设置是否兼容
3. **序列化错误**：检查数据结构是否符合IDL定义
4. **网络发现问题**：检查Presence消息是否正确发送和接收

## zrdds DDS开发模板 

### 项目通信架构
- **Command通道**：App → 设备（控制指令）
- **Status通道**：设备 → App（状态上报）
- **Presence通道**：设备发现和网络拓扑维护

注意：IDL文件为自动生成的数据结构传输文件，无需且不能手动修改，详细可看根目录下的SmartDemo.idl文件。

### 1. 核心DDS实体管理 (`DdsParticipant`)

项目级DDS管理器，已在CarSimulator和HomeSimulator中验证的实战版本。

**`DdsParticipant.java` 项目实战模板:**
```java
package CarSimulator.DDS; // 根据实际模块调整包名

import com.zrdds.domain.*;
import com.zrdds.publication.Publisher;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;
import com.zrdds.topic.TypeSupport;

public class DdsParticipant {
    private static final DdsParticipant INSTANCE = new DdsParticipant();

    private DomainParticipant dp;
    private Publisher pub;
    private Subscriber sub;

    private DdsParticipant() {
        // 1. 加载DDS本地库
        System.loadLibrary("ZRDDS_JAVA");

        // 2. 创建DomainParticipant
        DomainParticipantFactory dpf = DomainParticipantFactory.get_instance();
        dp = dpf.create_participant(0, DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT, null, 0);
        if (dp == null) {
            System.err.println("[ERROR] DomainParticipant创建失败");
            return;
        }

        // 3. 创建Publisher（用于状态上报）
        pub = dp.create_publisher(DomainParticipant.PUBLISHER_QOS_DEFAULT, null, 0);
        if (pub == null) {
            System.err.println("[ERROR] Publisher创建失败");
            return;
        }

        // 4. 创建Subscriber（用于命令接收）
        sub = dp.create_subscriber(DomainParticipant.SUBSCRIBER_QOS_DEFAULT, null, 0);
        if (sub == null) {
            System.err.println("[ERROR] Subscriber创建失败");
            return;
        }

        // 5. 注册项目所需的IDL类型
        registerRequiredTypes();
        
        // 6. 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        
        System.out.println("[INFO] DDS初始化完成");
    }

    private void registerRequiredTypes() {
        // 注册Command类型（App → 设备）
        registerType(CommandTypeSupport.get_instance(), "Command");
        
        // 注册VehicleStatus类型（车辆 → App）
        registerType(VehicleStatusTypeSupport.get_instance(), "VehicleStatus");
        
        // 注册HomeStatus类型（家庭 → App）
        registerType(HomeStatusTypeSupport.get_instance(), "HomeStatus");
        
        // 注册Presence类型（设备发现）
        registerType(PresenceTypeSupport.get_instance(), "Presence");
    }

    public static DdsParticipant getInstance() {
        return INSTANCE;
    }

    public Publisher getPublisher() { return pub; }
    public Subscriber getSubscriber() { return sub; }
    public DomainParticipant getDomainParticipant() { return dp; }

    public void registerType(TypeSupport type, String typeName) {
        if (type.register_type(dp, typeName) != com.zrdds.infrastructure.ReturnCode_t.RETCODE_OK) {
            System.err.println("[ERROR] 类型注册失败: " + typeName);
        }
    }

    public Topic createTopic(String topicName, String typeName) {
        Topic topic = dp.create_topic(topicName, typeName, DomainParticipant.TOPIC_QOS_DEFAULT, null, 0);
        if (topic == null) {
            System.err.println("[ERROR] Topic创建失败: " + topicName);
        } else {
            System.out.println("[INFO] Topic创建成功: " + topicName);
        }
        return topic;
    }

    public void close() {
        if (dp != null) {
            dp.delete_contained_entities();
            DomainParticipantFactory.get_instance().delete_participant(dp);
            System.out.println("[INFO] DDS资源已释放");
        }
    }
}
```

### 2. 状态发布者 (StatusPublisher) 模板

用于设备向App上报状态的发布者，已在CarSimulator中验证。

**`StatusPublisher.java` 实战模板:**
```java
package CarSimulator.DDS;

import com.zrdds.infrastructure.*;
import com.zrdds.publication.*;
import com.zrdds.topic.Topic;

public class StatusPublisher {
    private VehicleStatusDataWriter vehicleStatusWriter;
    private Topic vehicleStatusTopic;

    public boolean initialize(DdsParticipant dds) {
        // 1. 创建VehicleStatus Topic
        vehicleStatusTopic = dds.createTopic("VehicleStatus", "VehicleStatus");
        if (vehicleStatusTopic == null) {
            return false;
        }

        // 2. 配置DataWriter QoS
        DataWriterQos dwQos = new DataWriterQos();
        dds.getPublisher().get_default_datawriter_qos(dwQos);
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 1;

        // 3. 创建DataWriter
        vehicleStatusWriter = (VehicleStatusDataWriter) dds.getPublisher().create_datawriter(
                vehicleStatusTopic,
                dwQos,
                null,
                StatusKind.STATUS_MASK_NONE);

        return vehicleStatusWriter != null;
    }

    public void publishVehicleStatus(boolean engineOn, boolean doorsLocked, float fuelPercent) {
        VehicleStatus status = new VehicleStatus();
        status.engineOn = engineOn;
        status.doorsLocked = doorsLocked;
        status.fuelPercent = fuelPercent;
        
        vehicleStatusWriter.write(status, InstanceHandle_t.HANDLE_NIL_NATIVE);
    }

    // 便捷的状态更新方法
    public void updateEngineStatus(boolean engineOn) {
        publishVehicleStatus(engineOn, getCurrentDoorsLocked(), getCurrentFuelLevel());
    }

    public void updateDoorStatus(boolean doorsLocked) {
        publishVehicleStatus(getCurrentEngineStatus(), doorsLocked, getCurrentFuelLevel());
    }

    public void updateFuelLevel(float fuelPercent) {
        publishVehicleStatus(getCurrentEngineStatus(), getCurrentDoorsLocked(), fuelPercent);
    }

    // 需要维护当前状态（简化示例）
    private boolean getCurrentEngineStatus() { /* 返回当前状态 */ return false; }
    private boolean getCurrentDoorsLocked() { /* 返回当前状态 */ return false; }
    private float getCurrentFuelLevel() { /* 返回当前状态 */ return 0.0f; }
}
```

### 3. 命令订阅者 (CommandSubscriber) 模板

用于设备接收App控制命令的订阅者，已在CarSimulator中验证。

**`CommandSubscriber.java` 实战模板:**
```java
package CarSimulator.DDS;

import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;

public class CommandSubscriber {
    private CommandDataReader commandReader;
    private Topic commandTopic;
    private CommandHandler commandHandler;

    public interface CommandHandler {
        void handleCommand(String deviceType, String action);
    }

    public boolean initialize(DdsParticipant dds) {
        // 1. 创建Command Topic
        commandTopic = dds.createTopic("Command", "Command");
        if (commandTopic == null) {
            return false;
        }

        // 2. 配置DataReader QoS
        DataReaderQos drQos = new DataReaderQos();
        dds.getSubscriber().get_default_datareader_qos(drQos);
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 1;

        // 3. 创建DataReader并绑定监听器
        commandReader = (CommandDataReader) dds.getSubscriber().create_datareader(
                commandTopic,
                drQos,
                new CommandListener(),
                StatusKind.STATUS_MASK_ALL);

        return commandReader != null;
    }

    public void setCommandHandler(CommandHandler handler) {
        this.commandHandler = handler;
    }

    private class CommandListener extends DataReaderAdapter {
        @Override
        public void on_data_available(DataReader reader) {
            CommandDataReader dr = (CommandDataReader) reader;
            CommandSeq dataSeq = new CommandSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            ReturnCode_t rtn = dr.take(
                    dataSeq,
                    infoSeq,
                    ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                    SampleStateKind.ANY_SAMPLE_STATE,
                    ViewStateKind.ANY_VIEW_STATE,
                    InstanceStateKind.ANY_INSTANCE_STATE);

            if (rtn != ReturnCode_t.RETCODE_OK) {
                return;
            }

            try {
                for (int i = 0; i < dataSeq.size(); i++) {
                    if (infoSeq.get(i).valid_data) {
                        Command cmd = (Command) dataSeq.get(i);
                        System.out.println("[INFO] 收到命令: " + cmd.deviceType + " -> " + cmd.action);
                        
                        if (commandHandler != null) {
                            commandHandler.handleCommand(cmd.deviceType, cmd.action);
                        }
                    }
                }
            } finally {
                dr.return_loan(dataSeq, infoSeq);
            }
        }
    }
}
```

### 4. 完整集成示例 (CarSimulator实战)

展示如何将DDS组件集成到设备模拟器中，实现完整的双向通信。

**`CarSimulator.java` 完整集成示例:**
```java
package CarSimulator;

import CarSimulator.DDS.*;
import java.util.Scanner;

public class CarSimulator {
    private DdsParticipant dds;
    private CommandSubscriber commandSubscriber;
    private StatusPublisher statusPublisher;
    
    // 车辆状态
    private boolean engineOn = false;
    private boolean doorsLocked = true;
    private float fuelPercent = 85.0f;

    public static void main(String[] args) {
        CarSimulator car = new CarSimulator();
        car.initialize();
        car.start();
    }

    public void initialize() {
        // 1. 初始化DDS
        dds = DdsParticipant.getInstance();
        
        // 2. 初始化状态发布者
        statusPublisher = new StatusPublisher();
        statusPublisher.initialize(dds);
        
        // 3. 初始化命令订阅者
        commandSubscriber = new CommandSubscriber();
        commandSubscriber.initialize(dds);
        commandSubscriber.setCommandHandler(this::handleCommand);
        
        System.out.println("[INFO] CarSimulator初始化完成");
    }

    private void handleCommand(String deviceType, String action) {
        if (!"Car".equals(deviceType)) return;
        
        switch (action) {
            case "engine_on":
                engineOn = true;
                System.out.println("[ACTION] 发动机启动");
                break;
            case "engine_off":
                engineOn = false;
                System.out.println("[ACTION] 发动机关闭");
                break;
            case "lock_doors":
                doorsLocked = true;
                System.out.println("[ACTION] 车门上锁");
                break;
            case "unlock_doors":
                doorsLocked = false;
                System.out.println("[ACTION] 车门解锁");
                break;
        }
        
        // 状态变更后自动上报
        statusPublisher.publishVehicleStatus(engineOn, doorsLocked, fuelPercent);
    }

    public void start() {
        // 初始状态上报
        statusPublisher.publishVehicleStatus(engineOn, doorsLocked, fuelPercent);
        
        // 本地控制界面
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== CarSimulator控制台 ===");
            System.out.println("1. 切换发动机状态");
            System.out.println("2. 切换车门锁状态");
            System.out.println("3. 调整油量");
            System.out.println("4. 退出");
            System.out.print("请选择: ");
            
            int choice = scanner.nextInt();
            switch (choice) {
                case 1:
                    engineOn = !engineOn;
                    statusPublisher.publishVehicleStatus(engineOn, doorsLocked, fuelPercent);
                    break;
                case 2:
                    doorsLocked = !doorsLocked;
                    statusPublisher.publishVehicleStatus(engineOn, doorsLocked, fuelPercent);
                    break;
                case 3:
                    System.out.print("请输入油量百分比(0-100): ");
                    fuelPercent = scanner.nextFloat();
                    statusPublisher.publishVehicleStatus(engineOn, doorsLocked, fuelPercent);
                    break;
                case 4:
                    System.out.println("[INFO] 程序退出");
                    return;
            }
        }
    }
}
```

### 5. 项目级Topic命名规范

| Topic名称 | 数据类型 | 方向 | 描述 |
|-----------|----------|------|------|
| `Command` | Command | App → 设备 | 控制命令 |
| `VehicleStatus` | VehicleStatus | 车辆 → App | 车辆状态 |
| `HomeStatus` | HomeStatus | 家庭 → App | 家庭设备状态 |
| `Presence` | Presence | 双向 | 设备在线状态 |

### 6. QoS配置建议

**状态上报（VehicleStatus/HomeStatus）:**
- Reliability: RELIABLE（确保状态不丢失）
- History: KEEP_LAST, depth=1（只保留最新状态）
- Durability: VOLATILE（不保留历史数据）

**命令通道（Command）:**
- Reliability: RELIABLE（确保命令到达）
- History: KEEP_LAST, depth=10（保留最近命令）
- Durability: VOLATILE（不保留历史命令）

**设备发现（Presence）:**
- Reliability: BEST_EFFORT（轻量级）
- History: KEEP_LAST, depth=1
- Durability: TRANSIENT_LOCAL（保留本地发现信息）