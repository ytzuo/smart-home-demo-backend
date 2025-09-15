package IDL;


public class AIVehicleHealthReport{
    public String vehicleId = "";// @ID(0)
    public String reportContent = "";// @ID(1)
    public String reportId = "";// @ID(2)
    public String generationModel = "";// @ID(3)
    public String timeStamp = "";// @ID(4)

    public AIVehicleHealthReport(){

    }

    public AIVehicleHealthReport(AIVehicleHealthReport other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        AIVehicleHealthReport typedSrc = (AIVehicleHealthReport)src;
        this.vehicleId =  typedSrc.vehicleId;
        this.reportContent =  typedSrc.reportContent;
        this.reportId =  typedSrc.reportId;
        this.generationModel =  typedSrc.generationModel;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}