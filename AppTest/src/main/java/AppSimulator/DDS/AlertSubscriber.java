package AppSimulator.DDS;

import AppSimulator.MobileAppSimulator;
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
    private final MobileAppSimulator appSimulator;
    private AlertDataReader alertReader;

    public AlertSubscriber(MobileAppSimulator appSimulator) {
        this.appSimulator = appSimulator;
    }

    /**
     * 启动报警订阅器，使用监听器模式
     */
    public boolean start(Subscriber sub, Topic alertTopic) {
        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建Alert的DataReader
        alertReader = (AlertDataReader) sub.create_datareader(
                alertTopic,
                drQos,
                new AlertListener(),
                StatusKind.STATUS_MASK_ALL);

        if (alertReader == null) {
            System.err.println("[AlertSubscriber] 无法创建AlertDataReader");
            return false;
        }

        System.out.println("[AlertSubscriber] 已启动，等待报警消息...");
        return true;
    }

    /**
     * Alert监听器实现，处理接收到的报警消息
     */
    private class AlertListener implements DataReaderListener {
        
        @Override
        public void on_data_available(DataReader reader) {
            AlertDataReader alertReader = (AlertDataReader) reader;
            AlertSeq alertSeq = new AlertSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                // 获取 take() 方法的返回码（RETCODE_NO_DATA 是返回码而非异常）
                ReturnCode_t retCode = alertReader.take(alertSeq, infoSeq,
                        ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                        SampleStateKind.ANY_SAMPLE_STATE, ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                // 检查返回码：无数据时直接返回（无需处理）
                if (retCode == ReturnCode_t.RETCODE_NO_DATA) {
                    return;
                }
                // 其他错误返回码需处理
                else if (retCode != ReturnCode_t.RETCODE_OK) {
                    System.err.println("[AlertSubscriber] take() 失败，返回码: " + retCode);
                    return;
                }

                // 处理接收到的报警数据
                for (int i = 0; i < alertSeq.length(); i++) {
                    Alert alert = (Alert) alertSeq.get_at(i);
                    SampleInfo info = (SampleInfo) infoSeq.get_at(i);

                    if (info.valid_data) {
                        handleAlert(alert);
                    }
                }
            }
            catch (Exception e) {
                System.err.println("[AlertSubscriber] 处理报警消息时出错: " + e.getMessage());
            } finally {
                if (alertSeq != null) {
                    alertReader.return_loan(alertSeq, infoSeq);
                }
            }
        }

        @Override
        public void on_sample_lost(DataReader dataReader, SampleLostStatus sampleLostStatus) {

        }

        @Override
        public void on_requested_deadline_missed(DataReader dataReader, RequestedDeadlineMissedStatus requestedDeadlineMissedStatus) {

        }

        @Override
        public void on_requested_incompatible_qos(DataReader dataReader, RequestedIncompatibleQosStatus requestedIncompatibleQosStatus) {

        }

        @Override
        public void on_sample_rejected(DataReader dataReader, SampleRejectedStatus sampleRejectedStatus) {

        }

        @Override
        public void on_liveliness_changed(DataReader reader, LivelinessChangedStatus status) {
            System.out.println("[AlertSubscriber] 报警发布者活跃度变化: " + 
                    "活跃数量=" + status.alive_count + 
                    ", 不活跃数量=" + status.not_alive_count);
        }

        @Override
        public void on_subscription_matched(DataReader reader, SubscriptionMatchedStatus status) {
            System.out.println("[AlertSubscriber] 订阅匹配变化: " + 
                    "当前匹配数=" + status.current_count);
        }

        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {

        }
    }

    /**
     * 处理单个报警消息
     */
    private void handleAlert(Alert alert) {
        try {
            String deviceName = "未知设备";
            String alertDescription = "";

            // 根据报警类型ID生成描述
            switch (alert.alert_id) {
                case 1: // FIRE
                    alertDescription = "🚨 火灾警报！";
                    break;
                case 2: // INTRUSION
                    alertDescription = "⚠️ 入侵警报！";
                    break;
                case 3: // DEVICE_OFFLINE
                    alertDescription = "📱 设备离线警报";
                    break;
                case 4: // DEVICE_MALFUNCTION
                    alertDescription = "🔧 设备故障警报";
                    break;
                case 5: // DEVICE_OVERHEAT
                    alertDescription = "🔥 设备过热警报";
                    break;
                default:
                    alertDescription = "❓ 未知警报类型";
            }

            // 获取设备名称
            if (alert.deviceType != null && !alert.deviceType.isEmpty()) {
                deviceName = getDeviceDisplayName(alert.deviceType);
            }

            // 构建完整报警信息
            String fullAlert = String.format("%s\n设备: %s\n类型: %s\n时间: %s\n详情: %s",
                    alertDescription,
                    deviceName,
                    alert.deviceType,
                    alert.timeStamp,
                    alert.description);

            // 通知手机应用显示报警
            if (appSimulator != null) {
                appSimulator.displayAlert(fullAlert);
            }

            System.out.println("[AlertSubscriber] 收到报警: " + fullAlert);

        } catch (Exception e) {
            System.err.println("[AlertSubscriber] 处理报警消息失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备显示名称
     */
    private String getDeviceDisplayName(String deviceType) {
        // 简单的设备ID到显示名称的映射
        if (deviceType.contains("light")) {
            return "灯具 " + deviceType.replaceAll("[^0-9]", "");
        } else if (deviceType.contains("ac")) {
            return "空调 " + deviceType.replaceAll("[^0-9]", "");
        } else if (deviceType.contains("door")) {
            return "门禁系统";
        } else if (deviceType.contains("window")) {
            return "窗户传感器";
        }
        return deviceType;
    }
}