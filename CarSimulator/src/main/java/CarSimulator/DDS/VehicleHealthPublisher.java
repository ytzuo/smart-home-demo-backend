package CarSimulator.DDS;

import IDL.VehicleHealthReport;
import IDL.VehicleHealthReportDataWriter;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.publication.DataWriter;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import com.zrdds.publication.DataWriterQos;

public class VehicleHealthPublisher {
    private DataWriter writer;
    private VehicleHealthReportDataWriter healthDataWriter;
    private Publisher publisher;

    public void start(Publisher publisher, Topic topic) {
        this.publisher = publisher;
        try {
            // 配置QoS
            DataWriterQos dwQos = new DataWriterQos();
            publisher.get_default_datawriter_qos(dwQos);
            dwQos.durability.kind = com.zrdds.infrastructure.DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
            dwQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
            dwQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
            dwQos.history.depth = 10;

            // 创建DataWriter时添加适当的QoS配置
            writer = publisher.create_datawriter(topic, dwQos, null, com.zrdds.infrastructure.StatusKind.STATUS_MASK_NONE);
            if (writer == null) {
                System.err.println("[VehicleHealthPublisher] Failed to create DataWriter");
                return;
            }
            
            // 直接使用VehicleHealthReportDataWriter，不需要Helper类窄化
            if (writer instanceof VehicleHealthReportDataWriter) {
                healthDataWriter = (VehicleHealthReportDataWriter) writer;
                System.out.println("[VehicleHealthPublisher] VehicleHealthReportDataWriter initialized successfully");
            } else {
                System.err.println("[VehicleHealthPublisher] DataWriter type mismatch");
            }
        } catch (Exception e) {
            System.err.println("[VehicleHealthPublisher] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publish(VehicleHealthReport report) {
        if (healthDataWriter != null) {
            try {
                ReturnCode_t ret = healthDataWriter.write(report, null);
                if (ret == ReturnCode_t.RETCODE_OK) { // 修正: 直接比较常量值
                    System.out.println("[VehicleHealthPublisher] Successfully published vehicle health report for vehicle: " + report.vehicleId);
                } else {
                    System.err.println("[VehicleHealthPublisher] Failed to publish vehicle health report, error code: " + ret);
                }
            } catch (Exception e) {
                System.err.println("[VehicleHealthPublisher] Exception while publishing vehicle health report: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("[VehicleHealthPublisher] Cannot publish: DataWriter not initialized");
        }
    }

    public void stop() {
        if (publisher != null && writer != null) {
            try {
                publisher.delete_datawriter(writer);
                System.out.println("[VehicleHealthPublisher] DataWriter cleaned up successfully");
            } catch (Exception e) {
                System.err.println("[VehicleHealthPublisher] Failed to delete DataWriter: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 清理引用
        healthDataWriter = null;
        writer = null;
        publisher = null;
    }
}