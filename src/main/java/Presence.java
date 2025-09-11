public class Presence{
    public boolean inRange = false;// @ID(0)
    public String deviceId = "";// @ID(1)
    public String deviceType = "";// @ID(2)
    public String timeStamp = "";// @ID(3)

    public Presence(){

    }

    public Presence(Presence other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        Presence typedSrc = (Presence)src;
        this.inRange =  typedSrc.inRange;
        this.deviceId =  typedSrc.deviceId;
        this.deviceType =  typedSrc.deviceType;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}