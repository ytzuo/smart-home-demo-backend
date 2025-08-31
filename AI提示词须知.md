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

### Publication (发布者) 模板

基于zrdds模块的标准发布者实现步骤：

1. **加载本地库**
   ```java
   System.loadLibrary("ZRDDS_JAVA");
   ```

2. **创建 DomainParticipant**
   ```java
   DomainParticipant dp = DomainParticipantFactory.get_instance()
           .create_participant(domain_id,
                   DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                   null,
                   StatusKind.STATUS_MASK_NONE);
   ```

3. **注册数据类型**
   ```java
   VehicleStatusTypeSupport ts = (VehicleStatusTypeSupport) VehicleStatusTypeSupport.get_instance();
   ReturnCode_t rtn = ts.register_type(dp, "VehicleStatus");
   ```

4. **创建 Topic**
   ```java
   Topic tp = dp.create_topic(
           "VehicleStatusTopic",
           ts.get_type_name(),
           DomainParticipant.TOPIC_QOS_DEFAULT,
           null,
           StatusKind.STATUS_MASK_NONE);
   ```

5. **创建 Publisher**
   ```java
   Publisher pub = dp.create_publisher(DomainParticipant.PUBLISHER_QOS_DEFAULT, 
           null, StatusKind.STATUS_MASK_NONE);
   ```

6. **配置 DataWriter QoS**
   ```java
   DataWriterQos dwQos = new DataWriterQos();
   pub.get_default_datawriter_qos(dwQos);
   dwQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
   dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
   dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
   dwQos.history.depth = 1;
   ```

7. **创建 DataWriter**
   ```java
   VehicleStatusDataWriter dw = (VehicleStatusDataWriter) 
           pub.create_datawriter(tp, dwQos, null, StatusKind.STATUS_MASK_NONE);
   ```

8. **发布数据**
   ```java
   VehicleStatus vehicle = new VehicleStatus();
   // 设置数据字段
   vehicle.engineOn = true;
   vehicle.doorsLocked = true;
   vehicle.fuelPercent = 100;
   vehicle.location = "HomeBase";
   vehicle.timeStamp = String.valueOf((System.currentTimeMillis() / 1000L));
   
   dw.write(vehicle, InstanceHandle_t.HANDLE_NIL_NATIVE);
   ```

### Subscription (订阅者) 模板

基于zrdds模块的标准订阅者实现步骤：

1. **加载本地库**
   ```java
   System.loadLibrary("ZRDDS_JAVA");
   ```

2. **创建 DomainParticipant**
   ```java
   DomainParticipant dp = DomainParticipantFactory.get_instance()
           .create_participant(domain_id,
                   DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                   null,
                   StatusKind.STATUS_MASK_NONE);
   ```

3. **注册数据类型**
   ```java
   VehicleStatusTypeSupport ts = (VehicleStatusTypeSupport) VehicleStatusTypeSupport.get_instance();
   ReturnCode_t rtn = ts.register_type(dp, "VehicleStatus");
   ```

4. **创建 Topic**
   ```java
   Topic tp = dp.create_topic(
           "VehicleStatusTopic",
           ts.get_type_name(),
           DomainParticipant.TOPIC_QOS_DEFAULT,
           null,
           StatusKind.STATUS_MASK_NONE);
   ```

5. **创建 Subscriber**
   ```java
   Subscriber sub = dp.create_subscriber(
           DomainParticipant.SUBSCRIBER_QOS_DEFAULT,
           null,
           StatusKind.STATUS_MASK_NONE);
   ```

6. **配置 DataReader QoS**
   ```java
   DataReaderQos drQos = new DataReaderQos();
   sub.get_default_datareader_qos(drQos);
   drQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
   drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
   drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
   drQos.history.depth = 1;
   ```

7. **创建 DataReaderListener**
   ```java
   class VehicleStatusListener implements DataReaderListener {
       @Override
       public void on_data_available(DataReader reader) {
           VehicleStatusDataReader dr = (VehicleStatusDataReader) reader;
           VehicleStatusSeq dataSeq = new VehicleStatusSeq();
           SampleInfoSeq infoSeq = new SampleInfoSeq();

           ReturnCode_t rtn = dr.take(
                   dataSeq,
                   infoSeq,
                   -1,
                   SampleStateKind.ANY_SAMPLE_STATE,
                   ViewStateKind.ANY_VIEW_STATE,
                   InstanceStateKind.ANY_INSTANCE_STATE);

           if (rtn == ReturnCode_t.RETCODE_OK) {
               for (int i = 0; i < dataSeq.length(); i++) {
                   if (!infoSeq.get_at(i).valid_data) continue;
                   VehicleStatus vehicle = dataSeq.get_at(i);
                   // 处理接收到的数据
                   System.out.println("接收车辆状态: " + vehicle.location);
               }
               dr.return_loan(dataSeq, infoSeq);
           }
       }
   }
   ```

8. **创建 DataReader**
   ```java
   VehicleStatusDataReader dr = (VehicleStatusDataReader) 
           sub.create_datareader(tp, drQos, listener, StatusKind.STATUS_MASK_ALL);
   ```