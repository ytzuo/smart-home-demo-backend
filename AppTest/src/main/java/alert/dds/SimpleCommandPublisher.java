package alert.dds;

import alert.idl.Command;
import com.zrdds.domain.DomainParticipant;
import com.zrdds.domain.DomainParticipantFactory;
import com.zrdds.publication.DataWriter;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import com.zrdds.topic.TypeSupport;
import com.zrdds.infrastructure.*;

public class SimpleCommandPublisher {
    private final int domainId;
    private DomainParticipant domainParticipant;
    private Publisher publisher;
    private DataWriter commandWriter;
    private Topic commandTopic;

    public SimpleCommandPublisher(int domainId) {
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
                System.err.println("SimpleCommandPublisher: 创建DomainParticipant失败");
                return;
            }

            // 注册数据类型
            TypeSupport typeSupport = new CommandTypeSupport();
            if (typeSupport.register_type(domainParticipant, "Command") != ReturnCode_t.RETCODE_OK) {
                System.err.println("SimpleCommandPublisher: 注册Command类型失败");
                return;
            }

            // 创建Topic
            commandTopic = domainParticipant.create_topic(
                    "CarCommandTopic",
                    typeSupport.get_type_name(),
                    DomainParticipant.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (commandTopic == null) {
                System.err.println("SimpleCommandPublisher: 创建Topic失败");
                return;
            }

            // 创建Publisher
            publisher = domainParticipant.create_publisher(
                    DomainParticipant.PUBLISHER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (publisher == null) {
                System.err.println("SimpleCommandPublisher: 创建Publisher失败");
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
            commandWriter = publisher.create_datawriter(
                    commandTopic,
                    dwQos,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (commandWriter == null) {
                System.err.println("SimpleCommandPublisher: 创建DataWriter失败");
                return;
            }

            System.out.println("SimpleCommandPublisher: 命令发布器初始化成功");
        } catch (Exception e) {
            System.err.println("SimpleCommandPublisher: 初始化异常 - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishCommand(String deviceId, String deviceType, String action, float value) {
        if (commandWriter != null) {
            Command command = new Command();
            command.deviceId = deviceId;
            command.deviceType = deviceType;
            command.action = action;
            command.value = value;
            command.timeStamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            
            commandWriter.write(command, InstanceHandle_t.HANDLE_NIL_NATIVE);
            System.out.println("SimpleCommandPublisher: 命令已发布 - " + action + "=" + value);
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
            System.out.println("SimpleCommandPublisher: 命令发布器已关闭");
        } catch (Exception e) {
            System.err.println("SimpleCommandPublisher: 关闭异常 - " + e.getMessage());
        }
    }

    // 内部类：CommandTypeSupport
    private static class CommandTypeSupport extends TypeSupport {
        public CommandTypeSupport() {
            super("AppSimulator::idl::Command");
        }
    }
}