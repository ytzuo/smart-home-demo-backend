package HomeSimulator.DDS;

import com.zrdds.domain.DomainParticipant;
import com.zrdds.domain.DomainParticipantFactory;
import com.zrdds.infrastructure.StatusKind;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import IDL.HomeStatusTypeSupport;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 重构为DDS资源提供者（提供Publisher和Topic给FurnitureManager）
 */
public class HomeStatusPublisher {
    private DomainParticipant participant;
    private Publisher publisher;
    private Topic homeStatusTopic;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化DDS核心资源（DomainParticipant/Publisher/Topic）
     */
    public boolean initialize() {
        if (initialized.get()) {
            System.out.println("[HomeStatusPublisher] DDS资源已初始化");
            return true;
        }

        try {
            // 1. 创建DomainParticipant（DDS域参与者）
            participant = DomainParticipantFactory.get_instance().create_participant(
                    0, // 默认域ID
                    DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (participant == null) {
                throw new RuntimeException("创建DomainParticipant失败");
            }

            // 2. 注册HomeStatus类型（IDL生成的类型支持类）
            HomeStatusTypeSupport.get_instance().register_type(participant, HomeStatusTypeSupport.get_instance().get_type_name());

            // 3. 创建Topic（主题名称需与订阅端一致）
            homeStatusTopic = participant.create_topic(
                    "HomeStatusTopic", // 主题名称
                    HomeStatusTypeSupport.get_instance().get_type_name(),
                    DomainParticipant.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (homeStatusTopic == null) {
                throw new RuntimeException("创建HomeStatusTopic失败");
            }

            // 4. 创建Publisher（发布器）
            publisher = participant.create_publisher(
                    DomainParticipant.PUBLISHER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (publisher == null) {
                throw new RuntimeException("创建Publisher失败");
            }

            initialized.set(true);
            System.out.println("[HomeStatusPublisher] DDS资源初始化成功");
            return true;
        } catch (Exception e) {
            System.err.println("[HomeStatusPublisher] DDS初始化失败: " + e.getMessage());
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
            if (homeStatusTopic != null) {
                participant.delete_topic(homeStatusTopic);
            }
            DomainParticipantFactory.get_instance().delete_participant(participant);
        }
        initialized.set(false);
        System.out.println("[HomeStatusPublisher] DDS资源已释放");
    }

    // ======== 提供DDS资源访问接口（供FurnitureManager获取） ========
    public Publisher getPublisher() {
        return initialized.get() ? publisher : null;
    }

    public Topic getHomeStatusTopic() {
        return initialized.get() ? homeStatusTopic : null;
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}