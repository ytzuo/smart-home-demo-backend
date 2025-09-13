package IDL;

import com.zrdds.infrastructure.*;
import com.zrdds.topic.TypeSupport;
import com.zrdds.publication.DataWriter;
import com.zrdds.subscription.DataReader;
import java.io.UnsupportedEncodingException;

public class VehicleHealthReportTypeSupport extends TypeSupport {
    private String type_name = "VehicleHealthReport";
    private static TypeCodeImpl s_typeCode = null;
    private static VehicleHealthReportTypeSupport m_instance = new VehicleHealthReportTypeSupport();

    private final byte[] tmp_byte_obj = new byte[1];
    private final char[] tmp_char_obj = new char[1];
    private final short[] tmp_short_obj = new short[1];
    private final int[] tmp_int_obj = new int[1];
    private final long[] tmp_long_obj = new long[1];
    private final float[] tmp_float_obj = new float[1];
    private final double[] tmp_double_obj = new double[1];
    private final boolean[] tmp_boolean_obj = new boolean[1];

    
    private VehicleHealthReportTypeSupport(){}

    
    public static TypeSupport get_instance() { return m_instance; }

    public Object create_sampleI() {
        VehicleHealthReport sample = new VehicleHealthReport();
        return sample;
    }

    public void destroy_sampleI(Object sample) {

    }

    public int copy_sampleI(Object dst,Object src) {
        VehicleHealthReport VehicleHealthReportDst = (VehicleHealthReport)dst;
        VehicleHealthReport VehicleHealthReportSrc = (VehicleHealthReport)src;
        VehicleHealthReportDst.copy(VehicleHealthReportSrc);
        return 1;
    }

    public int print_sample(Object _sample) {
        if (_sample == null){
            System.out.println("NULL");
            return -1;
        }
        VehicleHealthReport sample = (VehicleHealthReport)_sample;
        if (sample.vehicleId != null){
            System.out.println("sample.vehicleId:" + sample.vehicleId);
        }
        else{
            System.out.println("sample.vehicleId: null");
        }
        int componentTypesTmpLen = sample.componentTypes.length();
        System.out.println("sample.componentTypes.length():" +componentTypesTmpLen);
        for (int i = 0; i < componentTypesTmpLen; ++i){
            if (sample.componentTypes.get_at(i) != null){
                System.out.println("sample.componentTypes.get_at(" + i + "):" + sample.componentTypes.get_at(i));
            }
            else{
                System.out.println("sample.componentTypes.get_at(" + i + "): null");
            }
        }
        int componentStatusesTmpLen = sample.componentStatuses.length();
        System.out.println("sample.componentStatuses.length():" +componentStatusesTmpLen);
        for (int i = 0; i < componentStatusesTmpLen; ++i){
            if (sample.componentStatuses.get_at(i) != null){
                System.out.println("sample.componentStatuses.get_at(" + i + "):" + sample.componentStatuses.get_at(i));
            }
            else{
                System.out.println("sample.componentStatuses.get_at(" + i + "): null");
            }
        }
        int metricsTmpLen = sample.metrics.length();
        System.out.println("sample.metrics.length():" +metricsTmpLen);
        for (int i = 0; i < metricsTmpLen; ++i){
            System.out.println("sample.metrics.get_at(" + i + "):" + sample.metrics.get_at(i));
        }
        if (sample.nextMaintenance != null){
            System.out.println("sample.nextMaintenance:" + sample.nextMaintenance);
        }
        else{
            System.out.println("sample.nextMaintenance: null");
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
        return 0xffffffff;
    }

    public int get_max_key_sizeI(){
        return 0xffffffff;
    }

    public boolean has_keyI(){
        return false;
    }

    public String get_keyhashI(Object sample, long cdr){
        return "-1";
    }

    public DataReader create_data_reader() {return new VehicleHealthReportDataReader();}

    public DataWriter create_data_writer() {return new VehicleHealthReportDataWriter();}

    public TypeCode get_inner_typecode(){
        TypeCode userTypeCode = get_typecode();
        if (userTypeCode == null) return null;
        return userTypeCode.get_impl();
    }

    public int get_sizeI(Object _sample,long cdr, int offset) throws UnsupportedEncodingException {
        int initialAlignment = offset;
        VehicleHealthReport sample = (VehicleHealthReport)_sample;
        offset += CDRSerializer.get_string_size(sample.vehicleId == null ? 0 : sample.vehicleId.getBytes().length, offset);

        offset += CDRSerializer.get_untype_size(4, offset);
        int componentTypesLen = sample.componentTypes.length();
        if (componentTypesLen != 0){
            for(int i = 0; i<sample.componentTypes.length(); ++i)
            {
                offset += CDRSerializer.get_string_size(sample.componentTypes.get_at(i).getBytes().length,offset);
            }
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int componentStatusesLen = sample.componentStatuses.length();
        if (componentStatusesLen != 0){
            for(int i = 0; i<sample.componentStatuses.length(); ++i)
            {
                offset += CDRSerializer.get_string_size(sample.componentStatuses.get_at(i).getBytes().length,offset);
            }
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int metricsLen = sample.metrics.length();
        if (metricsLen != 0){
            offset += 4 * metricsLen;
        }

        offset += CDRSerializer.get_string_size(sample.nextMaintenance == null ? 0 : sample.nextMaintenance.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.timeStamp == null ? 0 : sample.timeStamp.getBytes().length, offset);

        return offset - initialAlignment;
    }

    public int serializeI(Object _sample ,long cdr) {
         VehicleHealthReport sample = (VehicleHealthReport) _sample;

        if (!CDRSerializer.put_string(cdr, sample.vehicleId, sample.vehicleId == null ? 0 : sample.vehicleId.length())){
            System.out.println("serialize sample.vehicleId failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.componentTypes.length())){
            System.out.println("serialize length of sample.componentTypes failed.");
            return -2;
        }
        for (int i = 0; i < sample.componentTypes.length(); ++i){
            if (!CDRSerializer.put_string(cdr, sample.componentTypes.get_at(i), sample.componentTypes.get_at(i) == null ? 0 : sample.componentTypes.get_at(i).length())){
                System.out.println("serialize sample.componentTypes failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.componentStatuses.length())){
            System.out.println("serialize length of sample.componentStatuses failed.");
            return -2;
        }
        for (int i = 0; i < sample.componentStatuses.length(); ++i){
            if (!CDRSerializer.put_string(cdr, sample.componentStatuses.get_at(i), sample.componentStatuses.get_at(i) == null ? 0 : sample.componentStatuses.get_at(i).length())){
                System.out.println("serialize sample.componentStatuses failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.metrics.length())){
            System.out.println("serialize length of sample.metrics failed.");
            return -2;
        }
        if (sample.metrics.length() != 0){
            if (!CDRSerializer.put_float_array(cdr, sample.metrics.get_contiguous_buffer(), sample.metrics.length())){
                System.out.println("serialize sample.metrics failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_string(cdr, sample.nextMaintenance, sample.nextMaintenance == null ? 0 : sample.nextMaintenance.length())){
            System.out.println("serialize sample.nextMaintenance failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.timeStamp, sample.timeStamp == null ? 0 : sample.timeStamp.length())){
            System.out.println("serialize sample.timeStamp failed.");
            return -2;
        }

        return 0;
    }

    synchronized public int deserializeI(Object _sample, long cdr){
        VehicleHealthReport sample = (VehicleHealthReport) _sample;
        sample.vehicleId = CDRDeserializer.get_string(cdr);
        if(sample.vehicleId ==null){
            System.out.println("deserialize member sample.vehicleId failed.");
            return -3;
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.componentTypes failed.");
            return -2;
        }
        if (!sample.componentTypes.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.componentTypes failed.");
            return -3;
        }
        for(int i =0 ;i < sample.componentTypes.length() ;++i)
        {
            sample.componentTypes.set_at(i, CDRDeserializer.get_string(cdr));
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.componentStatuses failed.");
            return -2;
        }
        if (!sample.componentStatuses.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.componentStatuses failed.");
            return -3;
        }
        for(int i =0 ;i < sample.componentStatuses.length() ;++i)
        {
            sample.componentStatuses.set_at(i, CDRDeserializer.get_string(cdr));
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.metrics failed.");
            return -2;
        }
        if (!sample.metrics.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.metrics failed.");
            return -3;
        }
        if (!CDRDeserializer.get_float_array(cdr, sample.metrics.get_contiguous_buffer(), sample.metrics.length())){
            System.out.println("deserialize sample.metrics failed.");
            return -2;
        }

        sample.nextMaintenance = CDRDeserializer.get_string(cdr);
        if(sample.nextMaintenance ==null){
            System.out.println("deserialize member sample.nextMaintenance failed.");
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
        VehicleHealthReport sample = (VehicleHealthReport)_sample;
        offset += get_sizeI(sample, cdr, offset);
        return offset - initialAlignment;
    }

    public int serialize_keyI(Object _sample, long cdr){
        VehicleHealthReport sample = (VehicleHealthReport)_sample;
        return 0;
    }

    public int deserialize_keyI(Object _sample, long cdr) {
        VehicleHealthReport sample = (VehicleHealthReport)_sample;
        return 0;
    }

    public TypeCode get_typecode(){
        if (s_typeCode != null) {
            return s_typeCode;
        }
        TypeCodeFactory factory = TypeCodeFactory.get_instance();

        s_typeCode = factory.create_struct_TC("IDL.VehicleHealthReport");
        if (s_typeCode == null){
            System.out.println("create struct VehicleHealthReport typecode failed.");
            return s_typeCode;
        }
        int ret = 0;
        TypeCodeImpl memberTc = new TypeCodeImpl();
        TypeCodeImpl eleTc = new TypeCodeImpl();

        memberTc = factory.create_string_TC(0xffffffff);
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

        memberTc = factory.create_string_TC(0xffffffff);
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(0xffffffff, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member componentTypes TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            1,
            1,
            "componentTypes",
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

        memberTc = factory.create_string_TC(0xffffffff);
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(0xffffffff, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member componentStatuses TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            2,
            2,
            "componentStatuses",
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

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_FLOAT);
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(0xffffffff, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member metrics TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            3,
            3,
            "metrics",
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

        memberTc = factory.create_string_TC(0xffffffff);
        if (memberTc == null){
            System.out.println("Get Member nextMaintenance TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            4,
            4,
            "nextMaintenance",
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

        memberTc = factory.create_string_TC(0xffffffff);
        if (memberTc == null){
            System.out.println("Get Member timeStamp TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            5,
            5,
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