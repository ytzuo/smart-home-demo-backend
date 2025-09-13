package AppSimulator.DDS;

import IDL.EnergyReport;
import IDL.EnergyReportDataReader;
import IDL.EnergyReportSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;

/**
 * 能耗报告订阅器
 * 用于接收家居模拟器发送的设备能耗统计数据
 */
public class EnergyReportSubscriber {
    //数据回调监听器接口
    public interface DataListener {
        void onEnergyReportReceived(EnergyReport report);
    }

    private DataListener dataListener; // 监听器实例

    // 设置监听器的方法
    public void setDataListener(DataListener listener) {
        this.dataListener = listener;
    }

    /**
     * 启动能耗报告订阅器，使用监听器模式
     */
    public boolean start(Subscriber sub, Topic energyReportTopic) {
        System.out.println("[EnergyReportSubscriber] 正在启动能耗报告订阅器...");
        System.out.println("[EnergyReportSubscriber] Topic名称: " + energyReportTopic.get_name());

        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建EnergyReport的DataReader
        EnergyReportDataReader reportReader = (EnergyReportDataReader) sub.create_datareader(
                energyReportTopic,
                drQos,
                new EnergyReportListener(),
                StatusKind.STATUS_MASK_ALL);

        if (reportReader == null) {
            System.err.println("[EnergyReportSubscriber] 无法创建EnergyReportDataReader");
            return false;
        }

        System.out.println("[EnergyReportSubscriber] EnergyReportDataReader创建成功");
        System.out.println("[EnergyReportSubscriber] 已启动，等待能耗数据...");
        return true;
    }

    /**
     * 能耗报告监听器实现，处理接收到的能耗数据
     */
    class EnergyReportListener implements DataReaderListener{

        @Override
        public void on_requested_deadline_missed(DataReader dataReader, RequestedDeadlineMissedStatus requestedDeadlineMissedStatus) {
            System.err.println("[EnergyReportSubscriber] QoS不兼容: " + requestedDeadlineMissedStatus.total_count);
        }

        @Override
        public void on_requested_incompatible_qos(DataReader dataReader, RequestedIncompatibleQosStatus requestedIncompatibleQosStatus) {

        }

        @Override
        public void on_sample_rejected(DataReader dataReader, SampleRejectedStatus sampleRejectedStatus) {

        }

        @Override
        public void on_liveliness_changed(DataReader dataReader, LivelinessChangedStatus livelinessChangedStatus) {

        }

        @Override
        public void on_data_available(DataReader reader) {
            EnergyReportDataReader reportReader = (EnergyReportDataReader) reader;
            EnergyReportSeq reportSeq = new EnergyReportSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                ReturnCode_t retCode = reportReader.take(reportSeq, infoSeq, -1,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                if (retCode != ReturnCode_t.RETCODE_OK) {
                    System.out.println("[EnergyReportSubscriber] 接收能耗数据失败: " + retCode);
                    return;
                }

                for (int i = 0; i < reportSeq.length(); i++) {
                    if (infoSeq.get_at(i).valid_data) {
                        EnergyReport report = reportSeq.get_at(i);
                        handleEnergyReport(report);
                    }
                }

            } catch (Exception e) {
                System.err.println("[EnergyReportSubscriber] 处理能耗数据失败: " + e.getMessage());
            } finally {
                reportReader.return_loan(reportSeq, infoSeq);
            }
        }

        @Override
        public void on_sample_lost(DataReader dataReader, SampleLostStatus sampleLostStatus) {

        }

        @Override
        public void on_subscription_matched(DataReader dataReader, SubscriptionMatchedStatus subscriptionMatchedStatus) {
            if (subscriptionMatchedStatus.current_count > 0) {
                System.out.println("[EnergyReportSubscriber] 已连接到能耗数据发布者");
            }
        }

        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {

        }

        /**
         * 处理单个能耗报告数据
         */
        private void handleEnergyReport(EnergyReport report) {
            System.out.println("[EnergyReportSubscriber] 收到能耗数据:");
            System.out.println("  设备ID: " + report.deviceId);
            System.out.println("  设备类型: " + report.deviceType);
            System.out.println("  当前功率: " + report.currentPower + "W");
            System.out.println("  当日能耗: " + report.dailyConsumption + "kWh");
            System.out.println("  时间戳: " + report.timeStamp);
            // 新增：通过回调将数据传递给MobileAppSimulator
            if (dataListener != null) {
                dataListener.onEnergyReportReceived(report);
            }
        }
    }
}
