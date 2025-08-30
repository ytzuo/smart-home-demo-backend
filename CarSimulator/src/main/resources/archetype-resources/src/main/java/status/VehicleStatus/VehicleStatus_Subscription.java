package org.example.status.VehicleStatus;

import com.zrdds.domain.*;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;

public class VehicleStatus_Subscription {
    public static int domain_id = 80;
    private static boolean hasLoad = false;

    public static void main(String[] args) {
        loadLibrary();

        // 1. 创建 DomainParticipant
        DomainParticipant dp = DomainParticipantFactory.get_instance()
                .create_participant(domain_id,
                        DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT,
                        null,
                        StatusKind.STATUS_MASK_NONE);
        if (dp == null) {
            System.out.println("create dp failed");
            return;
        }

        // 2. 注册类型
        VehicleStatusTypeSupport ts = (VehicleStatusTypeSupport) VehicleStatusTypeSupport.get_instance();
        ReturnCode_t rtn = ts.register_type(dp, "VehicleStatus");
        if (rtn != ReturnCode_t.RETCODE_OK) {
            System.out.println("register type failed");
            return;
        }

        // 3. 创建 Topic
        Topic tp = dp.create_topic(
                "VehicleStatusTopic",
                ts.get_type_name(),
                DomainParticipant.TOPIC_QOS_DEFAULT,
                null,
                StatusKind.STATUS_MASK_NONE
        );
        if (tp == null) {
            System.out.println("create topic failed");
            return;
        }

        // 4. 创建 Subscriber
        Subscriber sub = dp.create_subscriber(
                DomainParticipant.SUBSCRIBER_QOS_DEFAULT,
                null,
                StatusKind.STATUS_MASK_NONE
        );
        if (sub == null) {
            System.out.println("create subscriber failed");
            return;
        }

        // 5. 创建 DataReaderListener
        VehicleStatusListener listener = new VehicleStatusListener();

        // 6. 获取默认 DataReader QoS 并修改
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 1;

        // 7. 创建 DataReader 并绑定 listener
        VehicleStatusDataReader dr = (VehicleStatusDataReader) sub.create_datareader(
                tp,
                drQos,
                listener,
                StatusKind.STATUS_MASK_ALL
        );
        if (dr == null) {
            System.out.println("create datareader failed");
            return;
        }

        System.out.println("车辆状态订阅者已启动，等待接收数据...");

        // 8. 保持程序运行
        try {
            while (true) {
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 9. 释放资源
        dp.delete_contained_entities();
        DomainParticipantFactory.get_instance().delete_participant(dp);
        DomainParticipantFactory.finalize_instance();
    }

    public static void loadLibrary() {
        if (!hasLoad) {
            System.loadLibrary("ZRDDS_JAVA");
            hasLoad = true;
        }
    }
}

// DataReaderListener 用于处理接收到的车辆状态
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
                InstanceStateKind.ANY_INSTANCE_STATE
        );

        if (rtn != ReturnCode_t.RETCODE_OK) {
            System.out.println("take data failed");
            return;
        }

        for (int i = 0; i < dataSeq.length(); i++) {
            if (!infoSeq.get_at(i).valid_data) continue;
            VehicleStatus vehicle = dataSeq.get_at(i);
            System.out.println("接收车辆状态, 时间: " + vehicle.timeStamp);
            System.out.printf("engineOn=%b, doorsLocked=%b, fuelPercent=%.1f, location=%s\n",
                    vehicle.engineOn, vehicle.doorsLocked, vehicle.fuelPercent, vehicle.location);
            System.out.println("--------------------------------------------------");
        }

        dr.return_loan(dataSeq, infoSeq);
    }

    @Override public void on_liveliness_changed(DataReader dr, LivelinessChangedStatus s) {}
    @Override public void on_requested_deadline_missed(DataReader dr, RequestedDeadlineMissedStatus s) {}
    @Override public void on_requested_incompatible_qos(DataReader dr, RequestedIncompatibleQosStatus s) {}
    @Override public void on_sample_lost(DataReader dr, SampleLostStatus s) {}
    @Override public void on_sample_rejected(DataReader dr, SampleRejectedStatus s) {}
    @Override public void on_subscription_matched(DataReader dr, SubscriptionMatchedStatus s) {}
    @Override public void on_data_arrived(DataReader dr, Object o, SampleInfo info) {}
}
