package org.example.status.HomeStatus;

import com.zrdds.domain.*;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;

public class HomeStatus_Subscription {
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
            System.out.println("创建 DomainParticipant 失败");
            return;
        }

        // 2. 注册类型
        HomeStatusTypeSupport ts = (HomeStatusTypeSupport) HomeStatusTypeSupport.get_instance();
        ReturnCode_t rtn = ts.register_type(dp, "HomeStatus");
        if (rtn != ReturnCode_t.RETCODE_OK) {
            System.out.println("注册类型失败");
            return;
        }

        // 3. 创建 Topic
        Topic tp = dp.create_topic(
                "HomeStatus",
                ts.get_type_name(),
                DomainParticipant.TOPIC_QOS_DEFAULT,
                null,
                StatusKind.STATUS_MASK_NONE
        );
        if (tp == null) {
            System.out.println("创建 Topic 失败");
            return;
        }

        // 4. 创建 Subscriber
        Subscriber sub = dp.create_subscriber(
                DomainParticipant.SUBSCRIBER_QOS_DEFAULT,
                null,
                StatusKind.STATUS_MASK_NONE
        );
        if (sub == null) {
            System.out.println("创建 Subscriber 失败");
            return;
        }

        // 5. 创建 DataReaderListener
        HomeStatusListener listener = new HomeStatusListener();

        // 获取默认 QoS 并修改
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.VOLATILE_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.BEST_EFFORT_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 1;

        // 6. 创建 DataReader 并绑定 listener
        HomeStatusDataReader dr = (HomeStatusDataReader) sub.create_datareader(
                tp,
                drQos,
                listener,
                StatusKind.STATUS_MASK_ALL
        );
        if (dr == null) {
            System.out.println("创建 DataReader 失败");
            return;
        }

        System.out.println("订阅者已启动，等待接收数据...");

        // 7. 保持程序运行
        try {
            while (true) {
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 8. 释放资源
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

// DataReaderListener 用于处理接收到的数据
class HomeStatusListener implements DataReaderListener {
    @Override
    public void on_data_available(DataReader reader) {
        HomeStatusDataReader dr = (HomeStatusDataReader) reader;
        HomeStatusSeq dataSeq = new HomeStatusSeq();
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
            System.out.println("获取数据失败");
            return;
        }

        for (int i = 0; i < dataSeq.length(); i++) {
            if (!infoSeq.get_at(i).valid_data) continue;
            HomeStatus home = dataSeq.get_at(i);
            System.out.println("==== 接收到设备状态 (时间: " + home.timeStamp + ") ====");
            int count = home.deviceIds.length();
            for (int j = 0; j < count; j++) {
                String id = home.deviceIds.get_at(j);
                String type = home.deviceTypes.get_at(j);

                switch (type) {
                    case "Lamp":
                        boolean lampOn = home.lightOn.get_at(j);
                        float pct = home.lightPercent.get_at(j);
                        System.out.printf("%s (灯) : 状态=%s 亮度=%.1f%%%n", id, lampOn ? "开" : "关", pct);
                        break;
                    case "AirConditioner":
                        float temp = home.acTemp.get_at(j);
                        String status = home.acStatus.get_at(j);
                        System.out.printf("%s (空调) : 温度=%.1f℃ 状态=%s%n", id, temp, status);
                        break;
                    case "Camera":
                        boolean camOn = home.cameraOn.get_at(j);
                        System.out.printf("%s (摄像头) : 状态=%s%n", id, camOn ? "开" : "关");
                        break;
                    default:
                        System.out.printf("%s (未知类型)%n", id);
                }
            }
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
