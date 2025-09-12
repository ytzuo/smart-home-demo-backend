package IDL;


public class ReportMedia{
    public String reportId = "";// @ID(0)
    public String reportType = "";// @ID(1)
    public String deviceId = "";// @ID(2)
    public int total_size = 0;// @ID(3)
    public int chunk_seq = 0;// @ID(4)
    public int chunk_size = 0;// @ID(5)
    public IDL.Blob chunk = new IDL.Blob();// @ID(6)
    public String timeStamp = "";// @ID(7)

    public ReportMedia(){

    }

    public ReportMedia(ReportMedia other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        ReportMedia typedSrc = (ReportMedia)src;
        this.reportId =  typedSrc.reportId;
        this.reportType =  typedSrc.reportType;
        this.deviceId =  typedSrc.deviceId;
        this.total_size =  typedSrc.total_size;
        this.chunk_seq =  typedSrc.chunk_seq;
        this.chunk_size =  typedSrc.chunk_size;
        this.chunk.copy(typedSrc.chunk);
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}