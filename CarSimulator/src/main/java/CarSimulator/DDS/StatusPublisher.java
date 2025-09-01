package CarSimulator.DDS;

import IDL.VehicleStatus;
import IDL.VehicleStatusDataWriter;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

public class StatusPublisher {

    private VehicleStatusDataWriter writer;
    private VehicleStatus vehicleStatus;

    public StatusPublisher() {
        this.vehicleStatus = new VehicleStatus();

    }


    public boolean start(Publisher pub, Topic vehicleStatusTopic) {
        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;

        // 创建VehicleStatus的DataWriter
        writer = (VehicleStatusDataWriter) pub.create_datawriter(
                vehicleStatusTopic,
                dwQos,
                null,
                StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.out.println("创建 VehicleStatusDataWriter 失败");
            return false;
        }

        // 初始化车辆状态
        vehicleStatus.engineOn = false;
        vehicleStatus.doorsLocked = true;
        vehicleStatus.fuelPercent = 100.0f;
        vehicleStatus.acOn = false;
        vehicleStatus.location = "";
        vehicleStatus.timeStamp = "";
        System.out.println("StatusPublisher 已启动");
        return true;
    }

    public void publishVehicleStatus(boolean engineOn, boolean doorsLocked, boolean acOn,float fuelPercent,
                                     String location,String timeStamp) {
        if (writer == null) {
            System.out.println("VehicleStatusDataWriter 尚未初始化");
            return;
        }

        // 更新车辆状态
        vehicleStatus.engineOn = engineOn;
        vehicleStatus.doorsLocked = doorsLocked;
        vehicleStatus.fuelPercent = fuelPercent;
        vehicleStatus.acOn = acOn;
        vehicleStatus.location = location;
        vehicleStatus.timeStamp = timeStamp;

        // 发布状态
        ReturnCode_t rtn = writer.write(vehicleStatus, InstanceHandle_t.HANDLE_NIL_NATIVE);
        if (rtn == ReturnCode_t.RETCODE_OK) {
            System.out.printf("已上报车辆状态: EngineOn=%b, DoorsLocked=%b, Fuel=%.1f%%%n",
                    vehicleStatus.engineOn, vehicleStatus.doorsLocked, vehicleStatus.fuelPercent);
        } else {
            System.out.println("上报车辆状态失败");
        }
    }

    public void publishVehicleStatus() {
        publishVehicleStatus(vehicleStatus.engineOn, vehicleStatus.doorsLocked, vehicleStatus.acOn,
                             vehicleStatus.fuelPercent,vehicleStatus.location,vehicleStatus.timeStamp);
    }
    public void updateEngineStatus(boolean engineOn) {
        vehicleStatus.engineOn = engineOn;
        publishVehicleStatus();
    }

    public void updateDoorStatus(boolean doorsLocked) {
        vehicleStatus.doorsLocked = doorsLocked;
        publishVehicleStatus();
    }

    public void updateFuelLevel(float fuelPercent) {
        vehicleStatus.fuelPercent = Math.max(0.0f, Math.min(100.0f, fuelPercent));
        publishVehicleStatus();
    }

    public VehicleStatus getCurrentStatus() {
        return vehicleStatus;
    }
}