package AppSimulator.DDS;


import com.zrdds.domain.DomainParticipant;
import com.zrdds.domain.DomainParticipantFactory;
import com.zrdds.domain.DomainParticipantQos;
import com.zrdds.publication.Publisher;
import com.zrdds.publication.PublisherQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.subscription.SubscriberQos;
import com.zrdds.topic.Topic;
import com.zrdds.topic.TopicQos;
import com.zrdds.topic.TypeSupport;

public class DdsParticipant {
    private static class Holder {
        private static final DdsParticipant INSTANCE = new DdsParticipant();
    }

    public static DdsParticipant getInstance() {
        return Holder.INSTANCE;
    }
    //dds实体
    private DomainParticipant dp;
    private DomainParticipantFactory dpf;
    private Publisher pub;
    private Subscriber sub;
    //私有构造方法
    private DdsParticipant() {
        //初始化dp
        dpf=DomainParticipantFactory.get_instance();
        DomainParticipantQos dpQos=new DomainParticipantQos();
        dpf.get_default_participant_qos(dpQos);
        dp=dpf.create_participant(0,dpQos,null,0);
        System.out.println("DomainParticipant 创建: " + dp);
        // 添加DomainParticipant创建检查
        if (dp == null) {
            System.err.println("DomainParticipant 创建失败!");
            return;
        }
        //初始化pub
        PublisherQos pubQos=new PublisherQos();
        dp.get_default_publisher_qos(pubQos);
        pub=dp.create_publisher(pubQos,null,0);
        System.out.println("Publisher 创建: " + pub);
        // 添加Publisher创建检查
        if (pub == null) {
            System.err.println("Publisher 创建失败!");
            return;
        }
        //初始化sub
        SubscriberQos subQos=new SubscriberQos();
        dp.get_default_subscriber_qos(subQos);
        sub=dp.create_subscriber(subQos,null,0);
        System.out.println("Subscriber 创建: " + sub);
        // 添加Subscriber创建检查
        if (sub == null) {
            System.err.println("Subscriber 创建失败!");
            return;
        }
        //释放资源
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }
    //创建topic
    public Topic createTopic(String topicName, TypeSupport type){
        TopicQos topicQos=new TopicQos();
        dp.get_default_topic_qos(topicQos);
        Topic topic=dp.create_topic(topicName,type.get_type_name(),topicQos,null,0);
        // 添加Topic创建失败的详细错误信息
        if (topic == null) {
            System.err.println("Topic创建失败! 名称: " + topicName + ", 类型: " + type.get_type_name());
        } else {
            System.out.println("Topic 创建成功: " + topicName);
        }
        return topic;
    }
    //得到publisher
    public Publisher getPublisher(){
        return pub;
    }
    //得到subscriber
    public Subscriber getSubscriber(){
        return sub;
    }
    //注册idl类型
    public void registerType(TypeSupport type){
        type.register_type(dp,type.get_type_name());
        System.out.println("TypeSupport 注册: " + type);
    }
    //得到DomainParticipant
    public DomainParticipant getDomainParticipant(){
        return dp;
    }
    //关闭
    public void close(){
        if (dp != null) {
            dp.delete_contained_entities();
            DomainParticipantFactory.get_instance().delete_participant(dp);
            System.out.println("[DdsParticipant] DDS resources released");
        }
    }
}