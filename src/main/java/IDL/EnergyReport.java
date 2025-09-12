package IDL;


public class EnergyReport{
    public String deviceId = "";// @ID(0)
    public String deviceType = "";// @ID(1)
    public float currentPower = 0;// @ID(2)
    public float dailyConsumption = 0;// @ID(3)
    public float weeklyConsumption = 0;// @ID(4)
    public String timeStamp = "";// @ID(5)

    public EnergyReport(){

    }

    public EnergyReport(EnergyReport other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        EnergyReport typedSrc = (EnergyReport)src;
        this.deviceId =  typedSrc.deviceId;
        this.deviceType =  typedSrc.deviceType;
        this.currentPower =  typedSrc.currentPower;
        this.dailyConsumption =  typedSrc.dailyConsumption;
        this.weeklyConsumption =  typedSrc.weeklyConsumption;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}