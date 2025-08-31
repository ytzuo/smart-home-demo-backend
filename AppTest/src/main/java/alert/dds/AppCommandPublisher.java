package alert.dds;

import CarSimulator.Command;
import CarSimulator.CommandDataWriter;
import CarSimulator.CommandTypeSupport;
import DDS.*;

public class AppCommandPublisher {
    private final int domainId;
    private DomainParticipant domainParticipant;
    private Publisher publisher;
    private CommandDataWriter commandWriter;
    private Topic commandTopic;

    public AppCommandPublisher(int domainId) {
        this.domainId = domainId;
    }

    public void init() {
        try {
            // 加载本地库
            System.loadLibrary("ZRDDS_JAVA");
            
            // 创建DomainParticipant
            domainParticipant = DomainParticipantFactory.get_instance()
                    .create_participant(domainId,
                            DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                            null,
                            StatusKind.STATUS_MASK_NONE);
            if (domainParticipant == null) {
                System.err.println("手机App: 创建DomainParticipant失败");
                return;
            }

            // 注册数据类型
            CommandTypeSupport typeSupport = new CommandTypeSupport();
            if (typeSupport.register_type(domainParticipant, "Command") != ReturnCode_t.RETCODE_OK) {
                System.err.println("手机App: 注册Command类型失败");
                return;
            }

            // 创建Topic
            String typeName = typeSupport.get_type_name();
            commandTopic = domainParticipant.create_topic(
                    "VehicleCommandTopic",
                    typeName,
                    DomainParticipant.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (commandTopic == null) {
                System.err.println("手机App: 创建Topic失败");
                return;
            }

            // 创建Publisher
            publisher = domainParticipant.create_publisher(
                    DomainParticipant.PUBLISHER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (publisher == null) {
                System.err.println("手机App: 创建Publisher失败");
                return;
            }

            // 配置DataWriter QoS
            DataWriterQos dwQos = new DataWriterQos();
            publisher.get_default_datawriter_qos(dwQos);
            dwQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
            dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
            dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
            dwQos.history.depth = 1;

            // 创建DataWriter
            commandWriter = (CommandDataWriter) publisher.create_datawriter(
                    commandTopic,
                    dwQos,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (commandWriter == null) {
                System.err.println("手机App: 创建CommandDataWriter失败");
                return;
            }

            System.out.println("手机App: 命令发布器初始化成功");
        } catch (Exception e) {
            System.err.println("手机App: 初始化异常 - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishCommand(Command command) {
        if (commandWriter != null) {
            commandWriter.write(command, InstanceHandle_t.HANDLE_NIL_NATIVE);
            System.out.println("手机App: 命令已发布 - " + command.action + "=" + command.value);
        }
    }

    public void close() {
        try {
            if (publisher != null && commandWriter != null) {
                publisher.delete_datawriter(commandWriter);
            }
            if (domainParticipant != null && publisher != null) {
                domainParticipant.delete_publisher(publisher);
            }
            if (domainParticipant != null) {
                domainParticipant.delete_contained_entities();
                DomainParticipantFactory.get_instance().delete_participant(domainParticipant);
            }
            System.out.println("手机App: 命令发布器已关闭");
        } catch (Exception e) {
            System.err.println("手机App: 关闭异常 - " + e.getMessage());
        }
    }
}