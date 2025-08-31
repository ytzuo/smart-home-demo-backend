package alert.AlertDevice.dds;

import com.example.alert.idl.Alert;
import com.example.alert.idl.AlertDataReader;
import com.example.alert.idl.AlertSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;


public class AlertSubscriber {

    public boolean start(Subscriber sub, Topic tp) {

        // 配置 DataReader QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建 DataReader
        AlertDataReader reader = (AlertDataReader) sub.create_datareader(
                tp,
                drQos,
                new AlertListener(),
                StatusKind.STATUS_MASK_ALL);

        if (reader == null) {
            System.out.println("创建 DataReader 失败");
            return false;
        }
        System.out.println("AlertSubscriber 已启动，等待告警消息...");
        return true;
    }

    // 内部类：监听器
    static class AlertListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            AlertDataReader dr = (AlertDataReader) reader;
            AlertSeq dataSeq = new AlertSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            ReturnCode_t rtn = dr.take(
                    dataSeq,
                    infoSeq,
                    -1,
                    SampleStateKind.ANY_SAMPLE_STATE,
                    ViewStateKind.ANY_VIEW_STATE,
                    InstanceStateKind.ANY_INSTANCE_STATE);

            if (rtn != ReturnCode_t.RETCODE_OK) {
                System.out.println("take 数据失败");
                return;
            }

            for (int i = 0; i < dataSeq.length(); i++) {
                if (!infoSeq.get_at(i).valid_data) continue;

                Alert data = dataSeq.get_at(i);
                String time = data.timeStamp;
                System.out.printf("接收告警 -> 设备:%s-%s [ID:%d] 等级:%s 描述:%s 时间:%s%n",
                        data.deviceType, data.deviceId, data.alert_id, data.level, data.description, time);

                // 2. 添加日志记录逻辑（核心修改点）
                // 假设日志功能提供了如下接口（需根据实际日志实现调整）
                // Logger.logAlert(data.deviceType, data.deviceId, data.alert_id, data.level, data.description, time);
            }

            dr.return_loan(dataSeq, infoSeq);
        }


        @Override public void on_liveliness_changed(DataReader dr, LivelinessChangedStatus s) {}
        @Override public void on_requested_deadline_missed(DataReader dr, RequestedDeadlineMissedStatus s) {}
        @Override public void on_requested_incompatible_qos(DataReader dr, RequestedIncompatibleQosStatus s) {}
        @Override public void on_sample_lost(DataReader dr, SampleLostStatus s) {}
        @Override public void on_sample_rejected(DataReader dr, SampleRejectedStatus s) {}
        @Override public void on_subscription_matched(DataReader dr, SubscriptionMatchedStatus s) {}
        @Override public void on_data_arrived(DataReader dr, Object o, SampleInfo s) {}
    }
}
