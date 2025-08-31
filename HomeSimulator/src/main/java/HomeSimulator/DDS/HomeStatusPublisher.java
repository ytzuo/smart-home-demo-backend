package HomeSimulator.DDS;

import AppTestIDL.HomeStatus;
import AppTestIDL.HomeStatusDataWriter;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;
import java.util.concurrent.atomic.AtomicBoolean;

public class HomeStatusPublisher {
    private HomeStatusDataWriter writer;
    private AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean running = false;

    public boolean start(Publisher pub, Topic homeStatusTopic) {
        if (started.get()) {
            System.out.println("[HomeSimulator] HomeStatusPublisher 已经启动");
            return true;
        }

        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;

        // 创建DataWriter
        writer = (HomeStatusDataWriter) pub.create_datawriter(
                homeStatusTopic,
                dwQos,
                null,
                StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.err.println("[HomeSimulator] 创建 HomeStatusDataWriter 失败");
            return false;
        }

        started.set(true);
        running = true;
        System.out.println("[HomeSimulator] HomeStatusPublisher 已启动");
        return true;
    }

    public void stop() {
        running = false;
        started.set(false);
    }

    public void publishHomeStatus(HomeStatus homeStatus) {
        if (!started.get() || writer == null) {
            System.err.println("[HomeSimulator] HomeStatusPublisher 未启动或DataWriter为空");
            return;
        }

        if (!running) {
            System.err.println("[HomeSimulator] HomeStatusPublisher 已停止运行");
            return;
        }

        try {
            ReturnCode_t rtn = writer.write(homeStatus, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (rtn == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[HomeSimulator] 已上报Home状态: AC=%s, Light=%s, Temp=%.1f%n", 
                        homeStatus.acStatus.get_at(0), homeStatus.lightOn.get_at(0), 
                        homeStatus.acTemp.length() > 0 ? homeStatus.acTemp.get_at(0) : 0.0f);
            } else {
                System.err.println("[HomeSimulator] 写入HomeStatus数据失败，返回码: " + rtn);
            }
        } catch (Exception e) {
            System.err.println("[HomeSimulator] 发布HomeStatus时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishHomeStatus(String acStatus, boolean lightOn) {
        HomeStatus homeStatus = new HomeStatus();
        
        // 设置AC状态
        homeStatus.acStatus.append(acStatus);
        
        // 设置灯光状态
        homeStatus.lightOn.append(lightOn);
        
        publishHomeStatus(homeStatus);
    }

    public void publishHomeStatus(String acStatus, boolean lightOn, double temperature) {
        HomeStatus homeStatus = new HomeStatus();
        
        // 设置AC状态
        homeStatus.acStatus.append(acStatus);
        
        // 设置灯光状态
        homeStatus.lightOn.append(lightOn);
        
        // 设置温度
        homeStatus.acTemp.append((float)temperature);
        
        publishHomeStatus(homeStatus);
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isRunning() {
        return running;
    }
}