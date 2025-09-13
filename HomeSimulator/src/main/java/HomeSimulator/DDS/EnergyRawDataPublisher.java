package HomeSimulator.DDS;

import IDL.EnergyRawData;
import IDL.EnergyRawDataDataWriter;
import IDL.EnergyRawDataTypeSupport;
import com.zrdds.domain.DomainParticipant;
import com.zrdds.domain.DomainParticipantFactory;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.infrastructure.StatusKind;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.util.concurrent.atomic.AtomicBoolean;

public class EnergyRawDataPublisher {
    private DomainParticipant participant;
    private Publisher publisher;
    private Topic energyRawDataTopic;
    private EnergyRawDataDataWriter dataWriter;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public boolean initialize() {
        if(initialized.get()) {
            System.out.println("[EnergyRawDataPublisher] DDS资源已初始化");
            return true;
        }

        try {
            participant = DomainParticipantFactory.get_instance().create_participant(
                    0,
                    DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (participant == null) {
                throw new RuntimeException("创建DomainParticipant失败");
            }

            EnergyRawDataTypeSupport.get_instance().register_type(participant, EnergyRawDataTypeSupport.get_instance().get_type_name());

            energyRawDataTopic = participant.create_topic(
                    "EnergyRawData",
                    EnergyRawDataTypeSupport.get_instance().get_type_name(),
                    DomainParticipant.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);

            if (energyRawDataTopic == null) {
                throw new RuntimeException("创建EnergyRawDataTopic失败");
            }

            // 4. 创建Publisher（发布器）
            publisher = participant.create_publisher(
                    DomainParticipant.PUBLISHER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (publisher == null) {
                throw new RuntimeException("创建Publisher失败");
            }

            // 创建DataWriter
            DataWriterQos dwQos = new DataWriterQos();
            publisher.get_default_datawriter_qos(dwQos);
            dwQos.durability.kind = com.zrdds.infrastructure.DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
            dwQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
            dwQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
            dwQos.history.depth = 10;

            dataWriter = (EnergyRawDataDataWriter) publisher.create_datawriter(
                    energyRawDataTopic,
                    dwQos,
                    null,
                    StatusKind.STATUS_MASK_NONE);

            if (dataWriter == null) {
                throw new RuntimeException("创建EnergyRawDataDataWriter失败");
            }

            initialized.set(true);
            System.out.println("[EnergyRawDataPublisher] DDS资源初始化成功");

            return true;
        } catch (Exception e) {
            System.err.println("[EnergyRawDataPublisher] DDS初始化失败: " + e.getMessage());
            cleanup(); // 失败时清理资源
            return false;
        }
    }

    /**
     * 释放DDS资源
     */
    public void cleanup() {
        if (participant != null) {
            if (publisher != null) {
                participant.delete_publisher(publisher);
            }
            if (energyRawDataTopic != null) {
                participant.delete_topic(energyRawDataTopic);
            }
            DomainParticipantFactory.get_instance().delete_participant(participant);
        }
        initialized.set(false);
        System.out.println("[EnergyRawDataPublisher] DDS资源已释放");
    }

    public Publisher getPublisher() {
        return initialized.get() ? publisher : null;
    }

    public Topic getEnergyRawDataTopic() {
        return initialized.get() ? energyRawDataTopic : null;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 发送EnergyRawData数据
     * @param energyRawData 能耗原始数据对象
     * @return 是否发送成功
     */
    public boolean publishEnergyRawData(EnergyRawData energyRawData) {
        if (!initialized.get() || dataWriter == null || energyRawData == null) {
            System.err.println("[EnergyRawDataPublisher] 发送失败：DDS未初始化或参数为空");
            return false;
        }

        try {
            ReturnCode_t result = dataWriter.write(energyRawData, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (result == ReturnCode_t.RETCODE_OK) {
                System.out.println("[EnergyRawDataPublisher] EnergyRawData发送成功");
                return true;
            } else {
                System.err.println("[EnergyRawDataPublisher] EnergyRawData发送失败，返回码: " + result);
                return false;
            }
        } catch (Exception e) {
            System.err.println("[EnergyRawDataPublisher] EnergyRawData发送异常: " + e.getMessage());
            return false;
        }
    }

}
