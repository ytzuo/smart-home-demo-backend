package AppSimulator.DDS;

import IDL.HomeStatus;
import IDL.HomeStatusDataReader;
import IDL.HomeStatusSeq;
import IDL.VehicleStatus;
import IDL.VehicleStatusDataReader;
import IDL.VehicleStatusSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;
import org.json.JSONException;
import org.json.JSONObject;

public class StatusSubscriber {

    public boolean start(Subscriber sub, Topic homeStatusTopic, Topic vehicleStatusTopic) {
        // 配置QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 10;

        // 创建HomeStatus的DataReader
        HomeStatusDataReader homeStatusReader = (HomeStatusDataReader) sub.create_datareader(
                homeStatusTopic,
                drQos,
                new HomeStatusListener(),
                StatusKind.STATUS_MASK_ALL);

        if (homeStatusReader == null) {
            System.out.println("创建 HomeStatusDataReader 失败");
            return false;
        }

        // 创建VehicleStatus的DataReader
        VehicleStatusDataReader vehicleStatusReader = (VehicleStatusDataReader) sub.create_datareader(
                vehicleStatusTopic,
                drQos,
                new VehicleStatusListener(),
                StatusKind.STATUS_MASK_ALL);

        if (vehicleStatusReader == null) {
            System.out.println("创建 VehicleStatusDataReader 失败");
            return false;
        }

        System.out.println("StatusSubscriber 已启动，等待状态消息...");
        return true;
    }

    // HomeStatus监听器（核心修改：适配deviceIds/deviceTypes/deviceStatus字段）
    static class HomeStatusListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            HomeStatusDataReader dr = (HomeStatusDataReader) reader;
            HomeStatusSeq dataSeq = new HomeStatusSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                ReturnCode_t rtn = dr.take(dataSeq, infoSeq, -1,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                if (rtn != ReturnCode_t.RETCODE_OK) {
                    System.out.println("读取 HomeStatus 数据失败，返回码: " + rtn);
                    return;
                }

                for (int i = 0; i < dataSeq.length(); i++) {
                    if (infoSeq.get_at(i).valid_data) {
                        HomeStatus data = dataSeq.get_at(i);
                        int deviceCount = data.deviceIds.length();
                        for (int idx = 0; idx < deviceCount; idx++) {
                            String deviceId = data.deviceIds.get_at(idx);
                            String deviceType = data.deviceTypes.get_at(idx);
                            String statusJson = data.deviceStatus.get_at(idx);

                            // ======== 新增：JSON格式验证 ========
                            // 1. 非空检查
                            if (statusJson == null || statusJson.trim().isEmpty()) {
                                System.err.printf("设备 %s (类型: %s) 状态为空，跳过解析%n", deviceId, deviceType);
                                continue;
                            }
                            // 2. JSON格式预验证（必须以 '{' 开头）
                            if (!statusJson.trim().startsWith("{")) {
                                System.err.printf("设备 %s (类型: %s) 状态格式错误，非JSON对象: %s%n",
                                        deviceId, deviceType, statusJson);
                                continue;
                            }

                            // 解析JSON（仅处理格式正确的数据）
                            try {
                                JSONObject statusObj = new JSONObject(statusJson);
                                System.out.println("\n===== 接收到家居状态数据 =====");
                                System.out.printf("设备 %d: ID=%s, 类型=%s%n", idx+1, deviceId, deviceType);
                                System.out.println("原始状态JSON: " + statusJson);
                                System.out.println("解析后状态: " + statusObj.toString(4));
                            } catch (JSONException e) {
                                System.err.printf("设备 %s (类型: %s) JSON解析失败: %s%n",
                                        deviceId, deviceType, e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("处理HomeStatus数据异常: " + e.getMessage());
                e.printStackTrace();
            } finally {
                dr.return_loan(dataSeq, infoSeq);
            }
        }

        @Override public void on_liveliness_changed(DataReader dr, LivelinessChangedStatus s) {}
        @Override public void on_requested_deadline_missed(DataReader dr, RequestedDeadlineMissedStatus s) {}
        @Override public void on_requested_incompatible_qos(DataReader dr, RequestedIncompatibleQosStatus s) {}
        @Override public void on_sample_lost(DataReader dr, SampleLostStatus s) {}
        @Override public void on_sample_rejected(DataReader dr, SampleRejectedStatus s) {}
        @Override public void on_subscription_matched(DataReader dr, SubscriptionMatchedStatus s) {}
        @Override public void on_data_arrived(DataReader dr, Object o, SampleInfo si) {}
    }

    // VehicleStatus监听器
    static class VehicleStatusListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            VehicleStatusDataReader dr = (VehicleStatusDataReader) reader;
            VehicleStatusSeq dataSeq = new VehicleStatusSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            ReturnCode_t rtn = dr.take(dataSeq, infoSeq, -1, SampleStateKind.ANY_SAMPLE_STATE, ViewStateKind.ANY_VIEW_STATE, InstanceStateKind.ANY_INSTANCE_STATE);

            if (rtn != ReturnCode_t.RETCODE_OK) {
                System.out.println("take VehicleStatus 数据失败");
                return;
            }

            for (int i = 0; i < dataSeq.length(); i++) {
                if (infoSeq.get_at(i).valid_data) {
                    VehicleStatus data = dataSeq.get_at(i);
                    // 打印接收到的Vehicle状态，使用IDL中定义的字段
                    System.out.printf("接收到Vehicle状态: EngineOn=%b, DoorsLocked=%b, Fuel=%f%%%n", 
                                      data.engineOn, data.doorsLocked, data.fuelPercent);
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