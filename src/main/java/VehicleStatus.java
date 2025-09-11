public class VehicleStatus{
    public boolean engineOn = false;// @ID(0)
    public boolean doorsLocked = false;// @ID(1)
    public boolean acOn = false;// @ID(2)
    public float fuelPercent = 0;// @ID(3)
    public String location = "";// @ID(4)
    public String timeStamp = "";// @ID(5)

    public VehicleStatus(){

    }

    public VehicleStatus(VehicleStatus other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        VehicleStatus typedSrc = (VehicleStatus)src;
        this.engineOn =  typedSrc.engineOn;
        this.doorsLocked =  typedSrc.doorsLocked;
        this.acOn =  typedSrc.acOn;
        this.fuelPercent =  typedSrc.fuelPercent;
        this.location =  typedSrc.location;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}