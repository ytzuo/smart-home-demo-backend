package alert.dds;

import alert.idl.VehicleStatus;
import com.zrdds.domain.DomainParticipant;
import com.zrdds.domain.DomainParticipantFactory;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderAdapter;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;
import com.zrdds.topic.TypeSupport;
import com.zrdds.infrastructure.*;

public class SimpleVehicleStatusSubscriber {
    private final int domainId;
    
    private DomainParticipant domainParticipant;
    private Subscriber subscriber;
    private DataReader statusReader;
    private Topic statusTopic;

    public SimpleVehicleStatusSubscriber(int domainId) {
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
                System.err.println("SimpleVehicleStatusSubscriber: 创建DomainParticipant失败");
                return;
            }

            // 注册数据类型
            TypeSupport typeSupport = new VehicleStatusTypeSupport();
            if (typeSupport.register_type(domainParticipant, "VehicleStatus") != ReturnCode_t.RETCODE_OK) {
                System.err.println("SimpleVehicleStatusSubscriber: 注册VehicleStatus类型失败");
                return;
            }

            // 创建Topic
            statusTopic = domainParticipant.create_topic(
                    "CarStatusTopic",
                    typeSupport.get_type_name(),
                    DomainParticipant.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (statusTopic == null) {
                System.err.println("SimpleVehicleStatusSubscriber: 创建Topic失败");
                return;
            }

            // 创建Subscriber
            subscriber = domainParticipant.create_subscriber(
                    DomainParticipant.SUBSCRIBER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (subscriber == null) {
                System.err.println("SimpleVehicleStatusSubscriber: 创建Subscriber失败");
                return;
            }

            // 配置DataReader QoS
            DataReaderQos drQos = new DataReaderQos();
            subscriber.get_default_datareader_qos(drQos);
            drQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
            drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
            drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
            drQos.history.depth = 1;

            // 创建监听器
            VehicleStatusListener listener = new VehicleStatusListener();

            // 创建DataReader
            statusReader = subscriber.create_datareader(
                    statusTopic,
                    drQos,
                    listener,
                    StatusKind.STATUS_MASK_ALL);
            if (statusReader == null) {
                System.err.println("SimpleVehicleStatusSubscriber: 创建DataReader失败");
                return;
            }

            System.out.println("SimpleVehicleStatusSubscriber: 状态订阅器初始化成功");
        } catch (Exception e) {
            System.err.println("SimpleVehicleStatusSubscriber: 初始化异常 - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private class VehicleStatusListener extends DataReaderAdapter {
        @Override
        public void on_data_available(DataReader reader) {
            VehicleStatusSeq dataSeq = new VehicleStatusSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                ReturnCode_t ret = statusReader.take(
                        dataSeq,
                        infoSeq,
                        ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                if (ret == ReturnCode_t.RETCODE_OK) {
                    for (int i = 0; i < dataSeq.size(); i++) {
                        if (infoSeq.get(i).valid_data) {
                            VehicleStatus status = (VehicleStatus) dataSeq.get(i);
                            onVehicleStatusReceived(status);
                        }
                    }
                    statusReader.return_loan(dataSeq, infoSeq);
                }
            } catch (Exception e) {
                System.err.println("SimpleVehicleStatusSubscriber: 接收数据异常 - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void onVehicleStatusReceived(VehicleStatus status) {
        System.out.printf("SimpleVehicleStatusSubscriber: 接收到车辆状态 - 引擎:%s 门锁:%s 油量:%.1f%% 位置:%s 时间:%s%n",
                status.engineOn ? "开启" : "关闭",
                status.doorsLocked ? "锁定" : "解锁",
                status.fuelPercent,
                status.location,
                status.timeStamp);
    }

    public void close() {
        try {
            if (subscriber != null && statusReader != null) {
                subscriber.delete_datareader(statusReader);
            }
            if (domainParticipant != null && subscriber != null) {
                domainParticipant.delete_subscriber(subscriber);
            }
            if (domainParticipant != null) {
                domainParticipant.delete_contained_entities();
                DomainParticipantFactory.get_instance().delete_participant(domainParticipant);
            }
            System.out.println("SimpleVehicleStatusSubscriber: 状态订阅器已关闭");
        } catch (Exception e) {
            System.err.println("SimpleVehicleStatusSubscriber: 关闭异常 - " + e.getMessage());
        }
    }

    // 内部类：VehicleStatusTypeSupport
    private static class VehicleStatusTypeSupport extends TypeSupport {
        public VehicleStatusTypeSupport() {
            super("AppSimulator::idl::VehicleStatus");
        }
    }

    // 内部类：VehicleStatusSeq
    private static class VehicleStatusSeq extends com.zrdds.util.Sequence {
        public VehicleStatusSeq() {
            super(VehicleStatus.class);
        }
    }
}