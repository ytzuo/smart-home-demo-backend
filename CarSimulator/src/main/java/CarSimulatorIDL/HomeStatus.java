package CarSimulatorIDL;


public class HomeStatus{
    public com.zrdds.infrastructure.StringSeq deviceIds = new com.zrdds.infrastructure.StringSeq();// @ID(0)
    public com.zrdds.infrastructure.StringSeq deviceTypes = new com.zrdds.infrastructure.StringSeq();// @ID(1)
    public com.zrdds.infrastructure.FloatSeq acTemp = new com.zrdds.infrastructure.FloatSeq();// @ID(2)
    public com.zrdds.infrastructure.StringSeq acStatus = new com.zrdds.infrastructure.StringSeq();// @ID(3)
    public com.zrdds.infrastructure.BooleanSeq lightOn = new com.zrdds.infrastructure.BooleanSeq();// @ID(4)
    public com.zrdds.infrastructure.FloatSeq lightPercent = new com.zrdds.infrastructure.FloatSeq();// @ID(5)
    public com.zrdds.infrastructure.BooleanSeq cameraOn = new com.zrdds.infrastructure.BooleanSeq();// @ID(6)
    public String timeStamp = "";// @ID(7)

    public HomeStatus(){

        this.deviceIds.maximum(255);
        this.deviceTypes.maximum(255);
        this.acTemp.maximum(255);
        this.acStatus.maximum(255);
        this.lightOn.maximum(255);
        this.lightPercent.maximum(255);
        this.cameraOn.maximum(255);
    }

    public HomeStatus(HomeStatus other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        HomeStatus typedSrc = (HomeStatus)src;
        this.deviceIds.copy(typedSrc.deviceIds);
        this.deviceTypes.copy(typedSrc.deviceTypes);
        this.acTemp.copy(typedSrc.acTemp);
        this.acStatus.copy(typedSrc.acStatus);
        this.lightOn.copy(typedSrc.lightOn);
        this.lightPercent.copy(typedSrc.lightPercent);
        this.cameraOn.copy(typedSrc.cameraOn);
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}