package HomeSimulator.DDS;

import IDL.Command;
import IDL.CommandDataReader;
import IDL.CommandSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CommandSubscriber {
    private CommandDataReader reader;
    private ExecutorService executorService;
    private Consumer<Command> commandHandler;
    private volatile boolean running = false;

    public CommandSubscriber() {
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public boolean start(Subscriber sub, Topic commandTopic, Consumer<Command> commandHandler) {
        this.commandHandler = commandHandler;
        
        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建DataReader
        reader = (CommandDataReader) sub.create_datareader(
                commandTopic,
                drQos,
                new CommandListener(),
                StatusKind.STATUS_MASK_ALL);

        if (reader == null) {
            System.err.println("[HomeSimulator] 创建 CommandDataReader 失败");
            return false;
        }

        running = true;
        System.out.println("[HomeSimulator] CommandSubscriber 已启动，等待App命令...");
        return true;
    }

    public void stop() {
        running = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // Command监听器
    private class CommandListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            CommandDataReader dr = (CommandDataReader) reader;
            CommandSeq dataSeq = new CommandSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            ReturnCode_t rtn = dr.take(dataSeq, infoSeq, -1, 
                    SampleStateKind.ANY_SAMPLE_STATE, 
                    ViewStateKind.ANY_VIEW_STATE, 
                    InstanceStateKind.ANY_INSTANCE_STATE);

            if (rtn != ReturnCode_t.RETCODE_OK) {
                System.err.println("[HomeSimulator] take Command 数据失败");
                return;
            }

            for (int i = 0; i < dataSeq.length(); i++) {
                if (infoSeq.get_at(i).valid_data) {
                    Command command = dataSeq.get_at(i);
                    
                    // 使用线程池异步处理命令，避免阻塞DDS回调线程
                    executorService.submit(() -> {
                        try {
                            System.out.printf("[HomeSimulator] 接收到App命令: DeviceType=%s, Action=%s%n", 
                                    command.deviceType, command.action);
                            
                            if (commandHandler != null) {
                                commandHandler.accept(command);
                            }
                        } catch (Exception e) {
                            System.err.println("[HomeSimulator] 处理命令时发生错误: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
            }

            dr.return_loan(dataSeq, infoSeq);
        }

        @Override
        public void on_liveliness_changed(DataReader dr, LivelinessChangedStatus s) {
            System.out.println("[HomeSimulator] Command订阅者活性状态改变");
        }

        @Override
        public void on_requested_deadline_missed(DataReader dr, RequestedDeadlineMissedStatus s) {
            System.err.println("[HomeSimulator] Command订阅者错过请求截止时间");
        }

        @Override
        public void on_requested_incompatible_qos(DataReader dr, RequestedIncompatibleQosStatus s) {
            System.err.println("[HomeSimulator] Command订阅者QoS不兼容");
        }

        @Override
        public void on_sample_lost(DataReader dr, SampleLostStatus s) {
            System.err.println("[HomeSimulator] Command样本丢失");
        }

        @Override
        public void on_sample_rejected(DataReader dr, SampleRejectedStatus s) {
            System.err.println("[HomeSimulator] Command样本被拒绝");
        }

        @Override
        public void on_subscription_matched(DataReader dr, SubscriptionMatchedStatus s) {
            System.out.println("[HomeSimulator] Command订阅者匹配成功");
        }

        @Override
        public void on_data_arrived(DataReader dr, Object o, SampleInfo si) {
            // 已处理
        }
    }
}