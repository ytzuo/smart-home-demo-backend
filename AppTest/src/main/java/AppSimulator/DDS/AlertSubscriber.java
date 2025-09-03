package AppSimulator.DDS;

import IDL.Alert;
import IDL.AlertDataReader;
import IDL.AlertSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;


/**
 * 手机端报警订阅器
 * 用于接收家居模拟器发送的家具异常报警信息
 */
public class AlertSubscriber {

    /**
     * 启动报警订阅器，使用监听器模式
     */
    public boolean start(Subscriber sub, Topic alertTopic) {
        System.out.println("[AlertSubscriber] 正在启动报警订阅器...");
        System.out.println("[AlertSubscriber] Topic名称: " + alertTopic.get_name());

        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建Alert的DataReader
        AlertDataReader alertReader = (AlertDataReader) sub.create_datareader(
                alertTopic,
                drQos,
                new AlertListener(),
                StatusKind.STATUS_MASK_ALL);

        if (alertReader == null) {
            System.err.println("[AlertSubscriber] 无法创建AlertDataReader");
            return false;
        }

        System.out.println("[AlertSubscriber] AlertDataReader创建成功");
        System.out.println("[AlertSubscriber] 已启动，等待报警消息...");
        return true;
    }

    /**
     * Alert监听器实现，处理接收到的报警消息（改为静态内部类，参考StatusSubscriber）
     */
    static class AlertListener implements DataReaderListener {

        @Override
        public void on_data_available(DataReader reader) {
            AlertDataReader alertReader = (AlertDataReader) reader;
            AlertSeq alertSeq = new AlertSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                ReturnCode_t retCode = alertReader.take(alertSeq, infoSeq, -1,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                if (retCode != ReturnCode_t.RETCODE_OK) {
                    System.out.println("[AlertSubscriber] 接收报警失败: " + retCode);
                    return;
                }

                for (int i = 0; i < alertSeq.length(); i++) {
                    if (infoSeq.get_at(i).valid_data) {
                        Alert alert = alertSeq.get_at(i);
                        handleAlert(alert);
                    }
                }

            } catch (Exception e) {
                System.err.println("[AlertSubscriber] 接收报警失败: " + e.getMessage());
            } finally {
                    alertReader.return_loan(alertSeq, infoSeq);
            }
        }

        @Override
        public void on_requested_incompatible_qos(DataReader dataReader, RequestedIncompatibleQosStatus requestedIncompatibleQosStatus) {
            System.err.println("[AlertSubscriber] QoS不兼容: " + requestedIncompatibleQosStatus.total_count);
        }

        @Override
        public void on_subscription_matched(DataReader reader, SubscriptionMatchedStatus status) {
            if (status.current_count > 0) {
                System.out.println("[AlertSubscriber] 已连接到报警发布者");
            }
        }

        @Override
        public void on_liveliness_changed(DataReader dr, LivelinessChangedStatus status) {}

        @Override
        public void on_requested_deadline_missed(DataReader dr, RequestedDeadlineMissedStatus status) {}

        @Override
        public void on_sample_lost(DataReader dr, SampleLostStatus status) {}

        @Override
        public void on_sample_rejected(DataReader dr, SampleRejectedStatus status) {}

        @Override
        public void on_data_arrived(DataReader dr, Object o, SampleInfo si) {}

        /**
         * 处理单个报警消息
         */
        private void handleAlert(Alert alert) {
            System.out.println("[AlertSubscriber] 收到设备报警:");
            System.out.println("  设备ID: " + alert.deviceId);
            System.out.println("  设备类型: " + alert.deviceType);
            System.out.println("  报警ID: " + alert.alert_id);
            System.out.println("  报警级别: " + alert.level);
            System.out.println("  描述: " + alert.description);
            System.out.println("  时间戳: " + alert.timeStamp);
        }
    }
}