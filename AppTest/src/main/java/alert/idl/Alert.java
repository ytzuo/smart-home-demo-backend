package alert.idl;


public class Alert{
    public String deviceId = "";// @ID(0)
    public String deviceType = "";// @ID(1)
    public int alert_id = 0;// @ID(2)
    public String level = "";// @ID(3)
    public String description = "";// @ID(4)
    public String timeStamp = "";// @ID(5)

    public Alert(){

    }

    public Alert(Alert other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        Alert typedSrc = (Alert)src;
        this.deviceId =  typedSrc.deviceId;
        this.deviceType =  typedSrc.deviceType;
        this.alert_id =  typedSrc.alert_id;
        this.level =  typedSrc.level;
        this.description =  typedSrc.description;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}