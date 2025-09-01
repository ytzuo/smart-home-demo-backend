package AppSimulator.DDS;

import AppTestIDL.HomeStatus;
import AppTestIDL.HomeStatusDataReader;
import AppTestIDL.HomeStatusSeq;
import AppTestIDL.VehicleStatus;
import AppTestIDL.VehicleStatusDataReader;
import AppTestIDL.VehicleStatusSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.*;
import com.zrdds.topic.Topic;

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

    // HomeStatus监听器
    static class HomeStatusListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            HomeStatusDataReader dr = (HomeStatusDataReader) reader;
            HomeStatusSeq dataSeq = new HomeStatusSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            ReturnCode_t rtn = dr.take(dataSeq, infoSeq, -1, SampleStateKind.ANY_SAMPLE_STATE, ViewStateKind.ANY_VIEW_STATE, InstanceStateKind.ANY_INSTANCE_STATE);

            if (rtn != ReturnCode_t.RETCODE_OK) {
                System.out.println("take HomeStatus 数据失败");
                return;
            }

            for (int i = 0; i < dataSeq.length(); i++) {
                if (infoSeq.get_at(i).valid_data) {
                    HomeStatus data = dataSeq.get_at(i);
                    // 打印接收到的Home状态，使用IDL中定义的字段
                    if (data.acStatus.length() > 0 && data.lightOn.length() > 0) {
                        System.out.printf("接收到Home状态: AC Status=%s, Light On=%b%n", 
                                          data.acStatus.get_at(0), data.lightOn.get_at(0));
                    } else {
                        System.out.println("接收到Home状态 (数据不完整)");
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