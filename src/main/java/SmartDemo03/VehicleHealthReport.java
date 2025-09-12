package SmartDemo03;


public class VehicleHealthReport{
    public String vehicleId = "";// @ID(0)
    public com.zrdds.infrastructure.StringSeq componentTypes = new com.zrdds.infrastructure.StringSeq();// @ID(1)
    public com.zrdds.infrastructure.StringSeq componentStatuses = new com.zrdds.infrastructure.StringSeq();// @ID(2)
    public com.zrdds.infrastructure.FloatSeq metrics = new com.zrdds.infrastructure.FloatSeq();// @ID(3)
    public String nextMaintenance = "";// @ID(4)
    public String timeStamp = "";// @ID(5)

    public VehicleHealthReport(){

        this.componentTypes.maximum(255);
        this.componentStatuses.maximum(255);
        this.metrics.maximum(255);
    }

    public VehicleHealthReport(VehicleHealthReport other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        VehicleHealthReport typedSrc = (VehicleHealthReport)src;
        this.vehicleId =  typedSrc.vehicleId;
        this.componentTypes.copy(typedSrc.componentTypes);
        this.componentStatuses.copy(typedSrc.componentStatuses);
        this.metrics.copy(typedSrc.metrics);
        this.nextMaintenance =  typedSrc.nextMaintenance;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}