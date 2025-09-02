package AppSimulator.DDS;

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

    /**
     * 启动报警订阅器，使用监听器模式
     */
    public boolean start(Subscriber sub, Topic alertTopic) {
        System.out.println("[AlertSubscriber] 正在启动报警订阅器...");
        System.out.println("[AlertSubscriber] Topic名称: " + alertTopic.get_name());

        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建Alert的DataReader
        AlertDataReader alertReader = (AlertDataReader) sub.create_datareader(
                alertTopic,
                drQos,
                new AlertListener(),
                StatusKind.STATUS_MASK_ALL);

        if (alertReader == null) {
            System.err.println("[AlertSubscriber] 无法创建AlertDataReader");
            return false;
        }

        System.out.println("[AlertSubscriber] AlertDataReader创建成功");
        System.out.println("[AlertSubscriber] 已启动，等待报警消息...");
        return true;
    }

    /**
     * Alert监听器实现，处理接收到的报警消息（改为静态内部类，参考StatusSubscriber）
     */
    static class AlertListener implements DataReaderListener {

        @Override
        public void on_data_available(DataReader reader) {
            AlertDataReader alertReader = (AlertDataReader) reader;
            AlertSeq alertSeq = new AlertSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                System.out.println("[AlertSubscriber] 收到on_data_available回调");

                // 获取 take() 方法的返回码
                ReturnCode_t retCode = alertReader.take(alertSeq, infoSeq, -1,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                System.out.println("[AlertSubscriber] take() 返回码: " + retCode + ", 消息数量: " + alertSeq.length());

                // 检查返回码：无数据时直接返回（无需处理）
                if (retCode == ReturnCode_t.RETCODE_NO_DATA) {
                    System.out.println("[AlertSubscriber] 无数据");
                    return;
                }
                // 其他错误返回码需处理
                else if (retCode != ReturnCode_t.RETCODE_OK) {
                    System.err.println("[AlertSubscriber] take() 失败，返回码: " + retCode);
                    return;
                }

                // 处理接收到的报警数据
                for (int i = 0; i < alertSeq.length(); i++) {
                    Alert alert = alertSeq.get_at(i);
                    SampleInfo info = infoSeq.get_at(i);

                    System.out.println("[AlertSubscriber] 处理消息 " + i + ", valid_data: " + info.valid_data);

                    if (info.valid_data) {
                        handleAlert(alert);
                    }
                }
            }
            catch (Exception e) {
                System.err.println("[AlertSubscriber] 处理报警消息时出错: " + e.getMessage());
                e.printStackTrace();
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
            System.err.println("[AlertSubscriber] ❌ QoS不兼容！无法与报警发布者建立连接");
            System.err.println("[AlertSubscriber] 不兼容的QoS策略数量: " + requestedIncompatibleQosStatus.total_count);
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
            if (status.current_count > 0) {
                System.out.println("[AlertSubscriber] ✅ 已成功连接到报警发布者！");
            } else {
                System.out.println("[AlertSubscriber] ⚠️ 未连接到报警发布者，等待连接...");
            }
        }

        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {

        }

        /**
         * 处理单个报警消息（改为静态内部类的方法）
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

                System.out.println("[AlertSubscriber] 收到报警: " + fullAlert);

            } catch (Exception e) {
                System.err.println("[AlertSubscriber] 处理报警消息失败: " + e.getMessage());
            }
        }

        /**
         * 获取设备显示名称（改为静态内部类的方法）
         */
        private String getDeviceDisplayName(String deviceType) {
            // 简单的设备类型到显示名称的映射
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
}