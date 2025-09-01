package AppSimulator.DDS;

import AppTestIDL.Command;
import AppTestIDL.CommandDataWriter;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

public class CommandPublisher {

    private CommandDataWriter writer;

    public boolean start(Publisher pub, Topic topic) {
        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;

        // 创建DataWriter
        writer = (CommandDataWriter) pub.create_datawriter(
                topic,
                dwQos,
                null,
                StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.out.println("创建 DataWriter 失败");
            return false;
        }
        System.out.println("CommandPublisher 已启动");
        return true;
    }

    public void publishCommand(String target, String action) {
        if (writer == null) {
            System.out.println("DataWriter 尚未初始化");
            return;
        }

        Command command = new Command();
        command.deviceType = target; // 使用 deviceType 字段
        command.action = action;

        ReturnCode_t rtn = writer.write(command, InstanceHandle_t.HANDLE_NIL_NATIVE);
        if (rtn == ReturnCode_t.RETCODE_OK) {
            System.out.printf("已发送命令: DeviceType=%s, Action=%s%n", command.deviceType, command.action);
        } else {
            System.out.println("写入数据失败");
        }
    }
}