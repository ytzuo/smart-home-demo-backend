public class HomeStatus{
    public com.zrdds.infrastructure.StringSeq deviceIds = new com.zrdds.infrastructure.StringSeq();// @ID(0)
    public com.zrdds.infrastructure.StringSeq deviceTypes = new com.zrdds.infrastructure.StringSeq();// @ID(1)
    public com.zrdds.infrastructure.StringSeq deviceStatus = new com.zrdds.infrastructure.StringSeq();// @ID(2)
    public String timeStamp = "";// @ID(3)

    public HomeStatus(){

    }

    public HomeStatus(HomeStatus other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        HomeStatus typedSrc = (HomeStatus)src;
        this.deviceIds.copy(typedSrc.deviceIds);
        this.deviceTypes.copy(typedSrc.deviceTypes);
        this.deviceStatus.copy(typedSrc.deviceStatus);
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}