package CarSimulator.DDS;

import AppTestIDL.Command;
import AppTestIDL.CommandDataReader;
import AppTestIDL.CommandSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CommandSubscriber {
    
    private CommandDataReader reader;
    private AtomicBoolean running;
    private Consumer<Command> commandHandler;
    
    public CommandSubscriber() {
        this.running = new AtomicBoolean(false);
    }
    
    public boolean start(Subscriber sub, Topic commandTopic) {
        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建Command的DataReader
        reader = (CommandDataReader) sub.create_datareader(
                commandTopic,
                drQos,
                new CommandListener(),
                StatusKind.STATUS_MASK_ALL);

        if (reader == null) {
            System.out.println("创建 CommandDataReader 失败");
            return false;
        }

        running.set(true);
        System.out.println("CommandSubscriber 已启动，等待App命令...");
        return true;
    }
    
    public void setCommandHandler(Consumer<Command> handler) {
        this.commandHandler = handler;
    }
    
    public void stop() {
        running.set(false);
        System.out.println("CommandSubscriber 已停止");
    }
    
    // Command监听器
    class CommandListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader dataReader) {
            CommandDataReader dr = (CommandDataReader) dataReader;
            CommandSeq dataSeq = new CommandSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            ReturnCode_t rtn = dr.take(dataSeq, infoSeq, -1, 
                    SampleStateKind.ANY_SAMPLE_STATE, 
                    ViewStateKind.ANY_VIEW_STATE, 
                    InstanceStateKind.ANY_INSTANCE_STATE);

            if (rtn != ReturnCode_t.RETCODE_OK) {
                System.out.println("take Command 数据失败");
                return;
            }

            for (int i = 0; i < dataSeq.length(); i++) {
                if (infoSeq.get_at(i).valid_data) {
                    Command command = dataSeq.get_at(i);
                    System.out.printf("接收到App命令: DeviceType=%s, Action=%s%n", 
                            command.deviceType, command.action);
                    
                    // 调用命令处理器
                    if (commandHandler != null) {
                        commandHandler.accept(command);
                    }
                }
            }

            dr.return_loan(dataSeq, infoSeq);
        }

        @Override public void on_liveliness_changed(DataReader dr, LivelinessChangedStatus s) {}
        @Override public void on_requested_deadline_missed(DataReader dr, RequestedDeadlineMissedStatus s) {}
        @Override public void on_requested_incompatible_qos(DataReader dr, RequestedIncompatibleQosStatus s) {}
        @Override public void on_sample_lost(DataReader dr, SampleLostStatus s) {}
        @Override public void on_sample_rejected(DataReader dr, SampleRejectedStatus s) {}
        @Override public void on_subscription_matched(DataReader dr, SubscriptionMatchedStatus s) {}
        @Override public void on_data_arrived(DataReader dr, Object o, SampleInfo si) {}
    }
}