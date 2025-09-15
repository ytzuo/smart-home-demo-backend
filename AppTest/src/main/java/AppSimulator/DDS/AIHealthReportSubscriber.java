package AppSimulator.DDS;

import IDL.AIVehicleHealthReport;
import IDL.AIVehicleHealthReportDataReader;
import IDL.AIVehicleHealthReportSeq;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.LivelinessChangedStatus;
import com.zrdds.infrastructure.SampleInfo;
import com.zrdds.infrastructure.SampleInfoSeq;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;

/**
 * AI健康报告订阅器
 * 用于接收CarSimulator发送的AI车辆健康分析报告
 */
public class AIHealthReportSubscriber {

    public boolean start(Subscriber sub, Topic topic) {
        System.out.println("[AIHealthReportSubscriber] 正在启动AI健康报告订阅器...");
        System.out.println("[AIHealthReportSubscriber] Topic名称: " + topic.get_name());

        // 配置DataReader QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.durability.kind = com.zrdds.infrastructure.DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建DataReader
        DataReader reader = sub.create_datareader(
                topic,
                drQos,
                new AIHealthReportListener(),
                com.zrdds.infrastructure.StatusKind.DATA_AVAILABLE_STATUS);

        if (reader == null) {
            System.err.println("[AIHealthReportSubscriber] 创建DataReader失败");
            return false;
        }

        System.out.println("[AIHealthReportSubscriber] AI健康报告订阅器启动成功");
        return true;
    }

    private class AIHealthReportListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            AIVehicleHealthReportDataReader reportReader = (AIVehicleHealthReportDataReader) reader;
            AIVehicleHealthReportSeq dataSeq = new AIVehicleHealthReportSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                reportReader.take(dataSeq, infoSeq,
                        com.zrdds.infrastructure.ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                        com.zrdds.infrastructure.SampleStateKind.ANY_SAMPLE_STATE,
                        com.zrdds.infrastructure.ViewStateKind.ANY_VIEW_STATE,
                        com.zrdds.infrastructure.InstanceStateKind.ANY_INSTANCE_STATE);

                for (int i = 0; i < dataSeq.length(); i++) {
                    if (infoSeq.get_at(i).valid_data) {
                        AIVehicleHealthReport report = dataSeq.get_at(i);
                        processAIHealthReport(report);
                    }
                }
            } catch (Exception e) {
                System.err.println("[AIHealthReportSubscriber] 读取AI健康报告数据异常: " + e.getMessage());
                e.printStackTrace();
            } finally {
                reportReader.return_loan(dataSeq, infoSeq);
            }
        }

        @Override
        public void on_requested_deadline_missed(DataReader arg0, com.zrdds.infrastructure.RequestedDeadlineMissedStatus arg1) {}
        @Override
        public void on_requested_incompatible_qos(DataReader arg0, com.zrdds.infrastructure.RequestedIncompatibleQosStatus arg1) {}
        @Override
        public void on_sample_lost(DataReader arg0, com.zrdds.infrastructure.SampleLostStatus arg1) {}
        @Override
        public void on_sample_rejected(DataReader arg0, com.zrdds.infrastructure.SampleRejectedStatus arg1) {}
        @Override
        public void on_liveliness_changed(DataReader dataReader, LivelinessChangedStatus livelinessChangedStatus) {}
        @Override
        public void on_subscription_matched(DataReader arg0, com.zrdds.infrastructure.SubscriptionMatchedStatus arg1) {}
        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {}
    }

    /**
     * 处理接收到的AI健康报告
     */
    private void processAIHealthReport(AIVehicleHealthReport report) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[AIHealthReportSubscriber] 📋 接收到AI车辆健康分析报告");
        System.out.println("=".repeat(80));
        System.out.printf("🚗 车辆ID: %s\n", report.vehicleId);
        System.out.printf("📊 报告ID: %s\n", report.reportId);
        System.out.printf("🤖 生成模型: %s\n", report.generationModel);
        System.out.printf("⏰ 生成时间: %s\n", report.timeStamp);
        System.out.println("-".repeat(80));
        System.out.println("📝 AI分析报告内容:");
        System.out.println(report.reportContent);
        System.out.println("=".repeat(80));
        System.out.println("[AIHealthReportSubscriber] ✅ AI健康报告处理完成\n");
    }
}