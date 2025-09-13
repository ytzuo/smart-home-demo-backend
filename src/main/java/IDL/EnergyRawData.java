package IDL;

public class EnergyRawData{
    public String deviceId = "";// @ID(0)
    public String deviceType = "";// @ID(1)
    public com.zrdds.infrastructure.FloatSeq currentPowerSeq = new com.zrdds.infrastructure.FloatSeq();// @ID(2)
    public com.zrdds.infrastructure.FloatSeq dailyConsumptionSeq = new com.zrdds.infrastructure.FloatSeq();// @ID(3)
    public com.zrdds.infrastructure.FloatSeq weeklyConsumptionSeq = new com.zrdds.infrastructure.FloatSeq();// @ID(4)
    public com.zrdds.infrastructure.StringSeq timeSeq = new com.zrdds.infrastructure.StringSeq();// @ID(5)
    public String timeStamp = "";// @ID(6)

    public EnergyRawData(){

    }

    public EnergyRawData(EnergyRawData other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        EnergyRawData typedSrc = (EnergyRawData)src;
        this.deviceId =  typedSrc.deviceId;
        this.deviceType =  typedSrc.deviceType;
        this.currentPowerSeq.copy(typedSrc.currentPowerSeq);
        this.dailyConsumptionSeq.copy(typedSrc.dailyConsumptionSeq);
        this.weeklyConsumptionSeq.copy(typedSrc.weeklyConsumptionSeq);
        this.timeSeq.copy(typedSrc.timeSeq);
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}