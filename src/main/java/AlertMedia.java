public class AlertMedia{
    public String deviceId = "";// @ID(0)
    public String deviceType = "";// @ID(1)
    public int alert_id = 0;// @ID(2)
    public int media_type = 0;// @ID(3)
    public int total_size = 0;// @ID(4)
    public int chunk_seq = 0;// @ID(5)
    public int chunk_size = 0;// @ID(6)
    public Blob chunk = new Blob();// @ID(7)

    public AlertMedia(){

    }

    public AlertMedia(AlertMedia other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        AlertMedia typedSrc = (AlertMedia)src;
        this.deviceId =  typedSrc.deviceId;
        this.deviceType =  typedSrc.deviceType;
        this.alert_id =  typedSrc.alert_id;
        this.media_type =  typedSrc.media_type;
        this.total_size =  typedSrc.total_size;
        this.chunk_seq =  typedSrc.chunk_seq;
        this.chunk_size =  typedSrc.chunk_size;
        this.chunk.copy(typedSrc.chunk);
        return this;
    }
}