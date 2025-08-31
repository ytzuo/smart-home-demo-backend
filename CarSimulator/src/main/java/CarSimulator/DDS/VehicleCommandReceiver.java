package CarSimulator.DDS;

import CarSimulator.CarSimulator;
import CarSimulator.Command;
import CarSimulator.CommandDataReader;
import CarSimulator.CommandSeq;
import CarSimulator.CommandTypeSupport;
import com.zrdds.domain.*;
import com.zrdds.infrastructure.SampleInfoSeq;
import com.zrdds.infrastructure.StatusKind;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;

public class VehicleCommandReceiver {
    private final int domainId;
    private final CarSimulator carSimulator;
    
    private DomainParticipant domainParticipant;
    private Subscriber subscriber;
    private CommandDataReader commandReader;
    private Topic commandTopic;

    public VehicleCommandReceiver(int domainId, CarSimulator carSimulator) {
        this.domainId = domainId;
        this.carSimulator = carSimulator;
    }

    public void init() {
        try {
            // 创建DomainParticipant
            domainParticipant = DomainParticipantFactory.get_instance()
                    .create_participant(domainId,
                            DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                            null,
                            StatusKind.STATUS_MASK_NONE);
            if (domainParticipant == null) {
                System.err.println("车辆: 创建DomainParticipant失败");
                return;
            }

            // 注册数据类型
            CommandTypeSupport typeSupport = (CommandTypeSupport) CommandTypeSupport.get_instance();
            if (typeSupport.register_type(domainParticipant, "Command") != 0) {
                System.err.println("车辆: 注册Command类型失败");
                return;
            }

            // 创建Topic
            String typeName = typeSupport.get_type_name();
            commandTopic = domainParticipant.create_topic(
                    "VehicleCommandTopic",
                    typeName,
                    DomainParticipantFactory.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (commandTopic == null) {
                System.err.println("车辆: 创建Topic失败");
                return;
            }

            // 创建Subscriber
            subscriber = domainParticipant.create_subscriber(
                    DomainParticipantFactory.SUBSCRIBER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (subscriber == null) {
                System.err.println("车辆: 创建Subscriber失败");
                return;
            }

            // 创建监听器
            CommandListener listener = new CommandListener();

            // 创建DataReader
            commandReader = (CommandDataReader) subscriber.create_datareader(
                    commandTopic,
                    DomainParticipantFactory.DATAREADER_QOS_DEFAULT,
                    listener,
                    StatusKind.STATUS_MASK_NONE);
            if (commandReader == null) {
                System.err.println("车辆: 创建CommandDataReader失败");
                return;
            }

            System.out.println("车辆: 命令接收器初始化成功");
        } catch (Exception e) {
            System.err.println("车辆: 初始化异常 - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private class CommandListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            CommandSeq dataSeq = new CommandSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                commandReader.take(dataSeq, infoSeq,
                        ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                for (int i = 0; i < dataSeq.size(); i++) {
                    if (infoSeq.get(i).valid_data) {
                        Command command = (Command) dataSeq.get(i);
                        processCommand(command);
                    }
                }

                commandReader.return_loan(dataSeq, infoSeq);
            } catch (Exception e) {
                System.err.println("车辆: 接收命令异常 - " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void processCommand(Command command) {
            System.out.printf("[车辆接收命令] deviceId=%s, deviceType=%s, action=%s, value=%.1f\n",
                    command.deviceId, command.deviceType, command.action, command.value);

            // 处理位置特殊命令
            if ("location".equals(command.action)) {
                if (command.timeStamp != null && command.timeStamp.startsWith("LOCATION_")) {
                    String location = command.timeStamp.substring(9); // 移除"LOCATION_"前缀
                    carSimulator.getVehicleStatus().location = location;
                    System.out.println("车辆: 位置已设置为: " + location);
                    carSimulator.publishVehicleStatus();
                    return;
                }
            }

            // 处理状态请求
            if ("status_request".equals(command.action)) {
                System.out.println("车辆: 接收到状态请求");
                carSimulator.publishVehicleStatus();
                return;
            }

            // 处理常规命令
            if (!"vehicle".equals(command.deviceType)) {
                System.out.println("车辆: 设备类型不匹配，忽略命令");
                return;
            }

            switch (command.action) {
                case "engine":
                    boolean engineState = command.value > 0;
                    carSimulator.getVehicleStatus().engineOn = engineState;
                    System.out.println("车辆: 发动机状态已设置为: " + (engineState ? "启动" : "熄火"));
                    break;

                case "lock":
                    boolean lockState = command.value > 0;
                    carSimulator.getVehicleStatus().doorsLocked = lockState;
                    System.out.println("车辆: 车门状态已设置为: " + (lockState ? "锁定" : "解锁"));
                    break;

                case "fuel":
                    float fuelLevel = Math.max(0, Math.min(100, command.value));
                    carSimulator.getVehicleStatus().fuelPercent = fuelLevel;
                    System.out.println("车辆: 油量已设置为: " + fuelLevel);
                    break;

                default:
                    System.out.println("车辆: 未知命令: " + command.action);
            }
            
            carSimulator.publishVehicleStatus();
        }

        @Override
        public void on_requested_deadline_missed(DataReader reader, RequestedDeadlineMissedStatus status) {}

        @Override
        public void on_requested_incompatible_qos(DataReader reader, RequestedIncompatibleQosStatus status) {}

        @Override
        public void on_sample_rejected(DataReader reader, SampleRejectedStatus status) {}

        @Override
        public void on_liveliness_changed(DataReader reader, LivelinessChangedStatus status) {}

        @Override
        public void on_subscription_matched(DataReader reader, SubscriptionMatchedStatus status) {}

        @Override
        public void on_sample_lost(DataReader reader, SampleLostStatus status) {}
    }

    public void close() {
        try {
            if (subscriber != null) {
                subscriber.delete_datareader(commandReader);
                domainParticipant.delete_subscriber(subscriber);
            }
            
            if (domainParticipant != null) {
                domainParticipant.delete_contained_entities();
                DomainParticipantFactory.get_instance().delete_participant(domainParticipant);
            }
            
            System.out.println("车辆: 命令接收器已关闭");
        } catch (Exception e) {
            System.err.println("车辆: 关闭异常 - " + e.getMessage());
        }
    }
}