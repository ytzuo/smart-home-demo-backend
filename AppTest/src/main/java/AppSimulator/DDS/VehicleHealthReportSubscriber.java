package AppSimulator.DDS;

import IDL.VehicleHealthReport;
import IDL.VehicleHealthReportDataReader;
import IDL.VehicleHealthReportSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;

/**
 * 车辆健康报告订阅器
 * 用于接收汽车模拟器发送的车辆部件状态及健康数据
 */
public class VehicleHealthReportSubscriber {
    // 数据回调监听器接口
    public interface DataListener {
        void onVehicleHealthReportReceived(VehicleHealthReport report);
    }

    private DataListener dataListener; // 监听器实例

    // 设置监听器的方法
    public void setDataListener(DataListener listener) {
        this.dataListener = listener;
    }

    /**
     * 启动车辆健康报告订阅器，使用监听器模式
     */
    public boolean start(Subscriber sub, Topic vehicleHealthTopic){
        System.out.println("[VehicleHealthReportSubscriber] 正在启动车辆健康报告订阅器...");
        System.out.println("[VehicleHealthReportSubscriber] Topic名称: " + vehicleHealthTopic.get_name());

        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建VehicleHealthReport的DataReader
        VehicleHealthReportDataReader reportReader = (VehicleHealthReportDataReader) sub.create_datareader(
                vehicleHealthTopic,
                drQos,
                new VehicleHealthReportListener(),
                StatusKind.STATUS_MASK_ALL);

        if (reportReader == null) {
            System.err.println("[VehicleHealthReportSubscriber] 无法创建VehicleHealthReportDataReader");
            return false;
        }

        System.out.println("[VehicleHealthReportSubscriber] VehicleHealthReportDataReader创建成功");
        System.out.println("[VehicleHealthReportSubscriber] 已启动，等待车辆健康数据...");
        return true;
    }

    /**
     * 车辆健康报告监听器实现，处理接收到的车辆健康数据
     */
    class VehicleHealthReportListener implements DataReaderListener{

        @Override
        public void on_requested_deadline_missed(DataReader dataReader, RequestedDeadlineMissedStatus requestedDeadlineMissedStatus) {

        }

        @Override
        public void on_requested_incompatible_qos(DataReader dataReader, RequestedIncompatibleQosStatus requestedIncompatibleQosStatus) {
            System.err.println("[VehicleHealthReportSubscriber] QoS不兼容: " + requestedIncompatibleQosStatus.total_count);
        }

        @Override
        public void on_sample_rejected(DataReader dataReader, SampleRejectedStatus sampleRejectedStatus) {

        }

        @Override
        public void on_liveliness_changed(DataReader dataReader, LivelinessChangedStatus livelinessChangedStatus) {

        }

        @Override
        public void on_data_available(DataReader dataReader) {
            VehicleHealthReportDataReader reportReader = (VehicleHealthReportDataReader) dataReader;
            VehicleHealthReportSeq reportSeq = new VehicleHealthReportSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                ReturnCode_t retCode = reportReader.take(reportSeq, infoSeq, -1,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                if (retCode != ReturnCode_t.RETCODE_OK) {
                    System.out.println("[VehicleHealthReportSubscriber] 接收车辆健康数据失败: " + retCode);
                    return;
                }

                for (int i = 0; i < reportSeq.length(); i++) {
                    if (infoSeq.get_at(i).valid_data) {
                        VehicleHealthReport report = reportSeq.get_at(i);
                        handleVehicleHealthReport(report);
                    }
                }

            } catch (Exception e) {
                System.err.println("[VehicleHealthReportSubscriber] 处理车辆健康数据失败: " + e.getMessage());
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
                System.out.println("[VehicleHealthReportSubscriber] 已连接到车辆健康数据发布者");
            }
        }

        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {

        }
        /**
         * 处理单个车辆健康报告数据
         */
        private void handleVehicleHealthReport(VehicleHealthReport report) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("📱 手机端收到车辆健康报告");
            System.out.println("=".repeat(50));
            System.out.println("🚗 车辆ID: " + report.vehicleId);
            System.out.println("📅 下次保养: " + report.nextMaintenance);
            System.out.println("⏰ 报告时间: " + report.timeStamp);
            
            System.out.println("\n🔧 部件状态详情:");
            int warningCount = 0;
            int errorCount = 0;
            
            for (int i = 0; i < report.componentTypes.length(); i++) {
                String component = report.componentTypes.get_at(i);
                String status = report.componentStatuses.get_at(i);
                float metric = report.metrics.get_at(i);
                
                String statusIcon = getStatusIcon(status);
                String statusColor = getStatusColor(status);
                
                System.out.printf("   %s %s: %s%s%s (指标: %.2f)\n",
                        statusIcon, component, statusColor, status, "\u001B[0m", metric);
                
                if ("warning".equalsIgnoreCase(status)) warningCount++;
                if ("error".equalsIgnoreCase(status)) errorCount++;
            }
            
            System.out.println("\n📊 状态统计:");
            System.out.println("   ✅ 正常部件: " + (report.componentTypes.length() - warningCount - errorCount));
            if (warningCount > 0) System.out.println("   ⚠️  警告部件: " + warningCount);
            if (errorCount > 0) System.out.println("   ❌ 故障部件: " + errorCount);
            
            if (warningCount == 0 && errorCount == 0) {
                System.out.println("\n🎉 车辆状态良好！");
            } else if (errorCount > 0) {
                System.out.println("\n🚨 车辆存在故障，需要立即检修！");
            } else if (warningCount > 0) {
                System.out.println("\n⚠️  车辆存在警告，建议关注！");
            }
            
            System.out.println("=".repeat(50));
            
            // 通过回调将数据传递给MobileAppSimulator
            if (dataListener != null) {
                dataListener.onVehicleHealthReportReceived(report);
            }
        }
        
        private String getStatusIcon(String status) {
            switch (status.toLowerCase()) {
                case "normal": return "✅";
                case "warning": return "⚠️";
                case "error": return "❌";
                default: return "❓";
            }
        }
        
        private String getStatusColor(String status) {
            switch (status.toLowerCase()) {
                case "normal": return "\u001B[32m"; // 绿色
                case "warning": return "\u001B[33m"; // 黄色
                case "error": return "\u001B[31m"; // 红色
                default: return "\u001B[0m"; // 默认
            }
        }
    }
}

