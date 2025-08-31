package alert.dds;

import AppSimulator.MobileAppSimulator;
import CarSimulator.VehicleStatus;
import CarSimulator.VehicleStatusDataReader;
import CarSimulator.VehicleStatusSeq;
import CarSimulator.VehicleStatusTypeSupport;
import DDS.*;

public class AppStatusSubscriber {
    private final int domainId;
    private final MobileAppSimulator appSimulator;
    
    private DomainParticipant domainParticipant;
    private Subscriber subscriber;
    private VehicleStatusDataReader statusReader;
    private Topic statusTopic;

    public AppStatusSubscriber(int domainId, MobileAppSimulator appSimulator) {
        this.domainId = domainId;
        this.appSimulator = appSimulator;
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
            VehicleStatusTypeSupport typeSupport = new VehicleStatusTypeSupport();
            if (typeSupport.register_type(domainParticipant, "VehicleStatus") != ReturnCode_t.RETCODE_OK) {
                System.err.println("手机App: 注册VehicleStatus类型失败");
                return;
            }

            // 创建Topic
            String typeName = typeSupport.get_type_name();
            statusTopic = domainParticipant.create_topic(
                    "VehicleStatusTopic",
                    typeName,
                    DomainParticipant.TOPIC_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (statusTopic == null) {
                System.err.println("手机App: 创建Topic失败");
                return;
            }

            // 创建Subscriber
            subscriber = domainParticipant.create_subscriber(
                    DomainParticipant.SUBSCRIBER_QOS_DEFAULT,
                    null,
                    StatusKind.STATUS_MASK_NONE);
            if (subscriber == null) {
                System.err.println("手机App: 创建Subscriber失败");
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
            statusReader = (VehicleStatusDataReader) subscriber.create_datareader(
                    statusTopic,
                    drQos,
                    listener,
                    StatusKind.STATUS_MASK_ALL);
            if (statusReader == null) {
                System.err.println("手机App: 创建VehicleStatusDataReader失败");
                return;
            }

            System.out.println("手机App: 状态订阅器初始化成功");
        } catch (Exception e) {
            System.err.println("手机App: 初始化异常 - " + e.getMessage());
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
                            appSimulator.onVehicleStatusReceived(status);
                        }
                    }
                    statusReader.return_loan(dataSeq, infoSeq);
                }
            } catch (Exception e) {
                System.err.println("手机App: 接收数据异常 - " + e.getMessage());
                e.printStackTrace();
            }
        }
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
            System.out.println("手机App: 状态订阅器已关闭");
        } catch (Exception e) {
            System.err.println("手机App: 关闭异常 - " + e.getMessage());
        }
    }
}