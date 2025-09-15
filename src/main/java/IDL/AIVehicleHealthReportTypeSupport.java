package IDL;

import com.zrdds.infrastructure.*;
import com.zrdds.topic.TypeSupport;
import com.zrdds.publication.DataWriter;
import com.zrdds.subscription.DataReader;
import java.io.UnsupportedEncodingException;

public class AIVehicleHealthReportTypeSupport extends TypeSupport {
    private String type_name = "AIVehicleHealthReport";
    private static TypeCodeImpl s_typeCode = null;
    private static AIVehicleHealthReportTypeSupport m_instance = new AIVehicleHealthReportTypeSupport();

    private final byte[] tmp_byte_obj = new byte[1];
    private final char[] tmp_char_obj = new char[1];
    private final short[] tmp_short_obj = new short[1];
    private final int[] tmp_int_obj = new int[1];
    private final long[] tmp_long_obj = new long[1];
    private final float[] tmp_float_obj = new float[1];
    private final double[] tmp_double_obj = new double[1];
    private final boolean[] tmp_boolean_obj = new boolean[1];

    
    private AIVehicleHealthReportTypeSupport(){}

    
    public static TypeSupport get_instance() { return m_instance; }

    public Object create_sampleI() {
        AIVehicleHealthReport sample = new AIVehicleHealthReport();
        return sample;
    }

    public void destroy_sampleI(Object sample) {

    }

    public int copy_sampleI(Object dst,Object src) {
        AIVehicleHealthReport AIVehicleHealthReportDst = (AIVehicleHealthReport)dst;
        AIVehicleHealthReport AIVehicleHealthReportSrc = (AIVehicleHealthReport)src;
        AIVehicleHealthReportDst.copy(AIVehicleHealthReportSrc);
        return 1;
    }

    public int print_sample(Object _sample) {
        if (_sample == null){
            System.out.println("NULL");
            return -1;
        }
        AIVehicleHealthReport sample = (AIVehicleHealthReport)_sample;
        if (sample.vehicleId != null){
            System.out.println("sample.vehicleId:" + sample.vehicleId);
        }
        else{
            System.out.println("sample.vehicleId: null");
        }
        if (sample.reportContent != null){
            System.out.println("sample.reportContent:" + sample.reportContent);
        }
        else{
            System.out.println("sample.reportContent: null");
        }
        if (sample.reportId != null){
            System.out.println("sample.reportId:" + sample.reportId);
        }
        else{
            System.out.println("sample.reportId: null");
        }
        if (sample.generationModel != null){
            System.out.println("sample.generationModel:" + sample.generationModel);
        }
        else{
            System.out.println("sample.generationModel: null");
        }
        if (sample.timeStamp != null){
            System.out.println("sample.timeStamp:" + sample.timeStamp);
        }
        else{
            System.out.println("sample.timeStamp: null");
        }
        return 0;
    }

    public String get_type_name(){
        return this.type_name;
    }

    public int get_max_sizeI(){
        return 1300;
    }

    public int get_max_key_sizeI(){
        return 1300;
    }

    public boolean has_keyI(){
        return false;
    }

    public String get_keyhashI(Object sample, long cdr){
        return "-1";
    }

    public DataReader create_data_reader() {return new AIVehicleHealthReportDataReader();}

    public DataWriter create_data_writer() {return new AIVehicleHealthReportDataWriter();}

    public TypeCode get_inner_typecode(){
        TypeCode userTypeCode = get_typecode();
        if (userTypeCode == null) return null;
        return userTypeCode.get_impl();
    }

    public int get_sizeI(Object _sample,long cdr, int offset) throws UnsupportedEncodingException {
        int initialAlignment = offset;
        AIVehicleHealthReport sample = (AIVehicleHealthReport)_sample;
        offset += CDRSerializer.get_string_size(sample.vehicleId == null ? 0 : sample.vehicleId.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.reportContent == null ? 0 : sample.reportContent.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.reportId == null ? 0 : sample.reportId.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.generationModel == null ? 0 : sample.generationModel.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.timeStamp == null ? 0 : sample.timeStamp.getBytes().length, offset);

        return offset - initialAlignment;
    }

    public int serializeI(Object _sample ,long cdr) {
         AIVehicleHealthReport sample = (AIVehicleHealthReport) _sample;

        if (!CDRSerializer.put_string(cdr, sample.vehicleId, sample.vehicleId == null ? 0 : sample.vehicleId.length())){
            System.out.println("serialize sample.vehicleId failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.reportContent, sample.reportContent == null ? 0 : sample.reportContent.length())){
            System.out.println("serialize sample.reportContent failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.reportId, sample.reportId == null ? 0 : sample.reportId.length())){
            System.out.println("serialize sample.reportId failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.generationModel, sample.generationModel == null ? 0 : sample.generationModel.length())){
            System.out.println("serialize sample.generationModel failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.timeStamp, sample.timeStamp == null ? 0 : sample.timeStamp.length())){
            System.out.println("serialize sample.timeStamp failed.");
            return -2;
        }

        return 0;
    }

    synchronized public int deserializeI(Object _sample, long cdr){
        AIVehicleHealthReport sample = (AIVehicleHealthReport) _sample;
        sample.vehicleId = CDRDeserializer.get_string(cdr);
        if(sample.vehicleId ==null){
            System.out.println("deserialize member sample.vehicleId failed.");
            return -3;
        }

        sample.reportContent = CDRDeserializer.get_string(cdr);
        if(sample.reportContent ==null){
            System.out.println("deserialize member sample.reportContent failed.");
            return -3;
        }

        sample.reportId = CDRDeserializer.get_string(cdr);
        if(sample.reportId ==null){
            System.out.println("deserialize member sample.reportId failed.");
            return -3;
        }

        sample.generationModel = CDRDeserializer.get_string(cdr);
        if(sample.generationModel ==null){
            System.out.println("deserialize member sample.generationModel failed.");
            return -3;
        }

        sample.timeStamp = CDRDeserializer.get_string(cdr);
        if(sample.timeStamp ==null){
            System.out.println("deserialize member sample.timeStamp failed.");
            return -3;
        }

        return 0;
    }

    public int get_key_sizeI(Object _sample,long cdr,int offset)throws UnsupportedEncodingException {
        int initialAlignment = offset;
        AIVehicleHealthReport sample = (AIVehicleHealthReport)_sample;
        offset += get_sizeI(sample, cdr, offset);
        return offset - initialAlignment;
    }

    public int serialize_keyI(Object _sample, long cdr){
        AIVehicleHealthReport sample = (AIVehicleHealthReport)_sample;
        return 0;
    }

    public int deserialize_keyI(Object _sample, long cdr) {
        AIVehicleHealthReport sample = (AIVehicleHealthReport)_sample;
        return 0;
    }

    public TypeCode get_typecode(){
        if (s_typeCode != null) {
            return s_typeCode;
        }
        TypeCodeFactory factory = TypeCodeFactory.get_instance();

        s_typeCode = factory.create_struct_TC("IDL.AIVehicleHealthReport");
        if (s_typeCode == null){
            System.out.println("create struct AIVehicleHealthReport typecode failed.");
            return s_typeCode;
        }
        int ret = 0;
        TypeCodeImpl memberTc = new TypeCodeImpl();
        TypeCodeImpl eleTc = new TypeCodeImpl();

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member vehicleId TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            0,
            0,
            "vehicleId",
            memberTc,
            false,
            false);
        factory.delete_TC(memberTc);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member reportContent TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            1,
            1,
            "reportContent",
            memberTc,
            false,
            false);
        factory.delete_TC(memberTc);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member reportId TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            2,
            2,
            "reportId",
            memberTc,
            false,
            false);
        factory.delete_TC(memberTc);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member generationModel TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            3,
            3,
            "generationModel",
            memberTc,
            false,
            false);
        factory.delete_TC(memberTc);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member timeStamp TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            4,
            4,
            "timeStamp",
            memberTc,
            false,
            false);
        factory.delete_TC(memberTc);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        return s_typeCode;
    }

}