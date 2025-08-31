package alert.dds;

import com.example.alert.idl.Alert;
import com.example.alert.idl.AlertDataWriter;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

public class AlertPublisher {
    private AlertDataWriter writer;
    public boolean start(Publisher pub, Topic tp) {

        // 配置 DataWriter QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;

        // 创建 DataWriter
        writer = (AlertDataWriter) pub.create_datawriter(
                tp,
                dwQos,
                null,
                StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.out.println("创建 DataWriter 失败");
            return false;
        }
        System.out.println("AlertPublisher 已启动");
        return true;
    }

    public void publishAlert(String deviceId, String deviceType, int alertId, String level, String desc) {
        if (writer == null) {
            System.out.println("DataWriter 尚未初始化");
            return;
        }

        Alert alert = new Alert();
        alert.deviceId = deviceId;
        alert.deviceType = deviceType;
        alert.alert_id = alertId;
        alert.level = level;
        alert.description = desc;
        alert.timeStamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());

        ReturnCode_t rtn = writer.write(alert, InstanceHandle_t.HANDLE_NIL_NATIVE);
        if (rtn == ReturnCode_t.RETCODE_OK) {
            System.out.printf("已发布告警: [%s-%s] #%d | %s | %s%n",
                    alert.deviceType, alert.deviceId, alert.alert_id, alert.level, alert.description);
        } else {
            System.out.println("写入数据失败");
        }
    }

}
