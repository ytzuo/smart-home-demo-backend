package IDL;

import com.zrdds.infrastructure.*;
import com.zrdds.topic.TypeSupport;
import com.zrdds.publication.DataWriter;
import com.zrdds.subscription.DataReader;
import java.io.UnsupportedEncodingException;

public class EnergyRawDataTypeSupport extends TypeSupport {
    private String type_name = "EnergyRawData";
    private static TypeCodeImpl s_typeCode = null;
    private static EnergyRawDataTypeSupport m_instance = new EnergyRawDataTypeSupport();

    private final byte[] tmp_byte_obj = new byte[1];
    private final char[] tmp_char_obj = new char[1];
    private final short[] tmp_short_obj = new short[1];
    private final int[] tmp_int_obj = new int[1];
    private final long[] tmp_long_obj = new long[1];
    private final float[] tmp_float_obj = new float[1];
    private final double[] tmp_double_obj = new double[1];
    private final boolean[] tmp_boolean_obj = new boolean[1];

    
    private EnergyRawDataTypeSupport(){}

    
    public static TypeSupport get_instance() { return m_instance; }

    public Object create_sampleI() {
        EnergyRawData sample = new EnergyRawData();
        return sample;
    }

    public void destroy_sampleI(Object sample) {

    }

    public int copy_sampleI(Object dst,Object src) {
        EnergyRawData EnergyRawDataDst = (EnergyRawData)dst;
        EnergyRawData EnergyRawDataSrc = (EnergyRawData)src;
        EnergyRawDataDst.copy(EnergyRawDataSrc);
        return 1;
    }

    public int print_sample(Object _sample) {
        if (_sample == null){
            System.out.println("NULL");
            return -1;
        }
        EnergyRawData sample = (EnergyRawData)_sample;
        if (sample.deviceId != null){
            System.out.println("sample.deviceId:" + sample.deviceId);
        }
        else{
            System.out.println("sample.deviceId: null");
        }
        if (sample.deviceType != null){
            System.out.println("sample.deviceType:" + sample.deviceType);
        }
        else{
            System.out.println("sample.deviceType: null");
        }
        int currentPowerSeqTmpLen = sample.currentPowerSeq.length();
        System.out.println("sample.currentPowerSeq.length():" +currentPowerSeqTmpLen);
        for (int i = 0; i < currentPowerSeqTmpLen; ++i){
            System.out.println("sample.currentPowerSeq.get_at(" + i + "):" + sample.currentPowerSeq.get_at(i));
        }
        int dailyConsumptionSeqTmpLen = sample.dailyConsumptionSeq.length();
        System.out.println("sample.dailyConsumptionSeq.length():" +dailyConsumptionSeqTmpLen);
        for (int i = 0; i < dailyConsumptionSeqTmpLen; ++i){
            System.out.println("sample.dailyConsumptionSeq.get_at(" + i + "):" + sample.dailyConsumptionSeq.get_at(i));
        }
        int weeklyConsumptionSeqTmpLen = sample.weeklyConsumptionSeq.length();
        System.out.println("sample.weeklyConsumptionSeq.length():" +weeklyConsumptionSeqTmpLen);
        for (int i = 0; i < weeklyConsumptionSeqTmpLen; ++i){
            System.out.println("sample.weeklyConsumptionSeq.get_at(" + i + "):" + sample.weeklyConsumptionSeq.get_at(i));
        }
        int timeSeqTmpLen = sample.timeSeq.length();
        System.out.println("sample.timeSeq.length():" +timeSeqTmpLen);
        for (int i = 0; i < timeSeqTmpLen; ++i){
            if (sample.timeSeq.get_at(i) != null){
                System.out.println("sample.timeSeq.get_at(" + i + "):" + sample.timeSeq.get_at(i));
            }
            else{
                System.out.println("sample.timeSeq.get_at(" + i + "): null");
            }
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

    public DataReader create_data_reader() {return new EnergyRawDataDataReader();}

    public DataWriter create_data_writer() {return new EnergyRawDataDataWriter();}

    public TypeCode get_inner_typecode(){
        TypeCode userTypeCode = get_typecode();
        if (userTypeCode == null) return null;
        return userTypeCode.get_impl();
    }

    public int get_sizeI(Object _sample,long cdr, int offset) throws UnsupportedEncodingException {
        int initialAlignment = offset;
        EnergyRawData sample = (EnergyRawData)_sample;
        offset += CDRSerializer.get_string_size(sample.deviceId == null ? 0 : sample.deviceId.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.deviceType == null ? 0 : sample.deviceType.getBytes().length, offset);

        offset += CDRSerializer.get_untype_size(4, offset);
        int currentPowerSeqLen = sample.currentPowerSeq.length();
        if (currentPowerSeqLen != 0){
            offset += 4 * currentPowerSeqLen;
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int dailyConsumptionSeqLen = sample.dailyConsumptionSeq.length();
        if (dailyConsumptionSeqLen != 0){
            offset += 4 * dailyConsumptionSeqLen;
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int weeklyConsumptionSeqLen = sample.weeklyConsumptionSeq.length();
        if (weeklyConsumptionSeqLen != 0){
            offset += 4 * weeklyConsumptionSeqLen;
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int timeSeqLen = sample.timeSeq.length();
        if (timeSeqLen != 0){
            for(int i = 0; i<sample.timeSeq.length(); ++i)
            {
                offset += CDRSerializer.get_string_size(sample.timeSeq.get_at(i).getBytes().length,offset);
            }
        }

        offset += CDRSerializer.get_string_size(sample.timeStamp == null ? 0 : sample.timeStamp.getBytes().length, offset);

        return offset - initialAlignment;
    }

    public int serializeI(Object _sample ,long cdr) {
         EnergyRawData sample = (EnergyRawData) _sample;

        if (!CDRSerializer.put_string(cdr, sample.deviceId, sample.deviceId == null ? 0 : sample.deviceId.length())){
            System.out.println("serialize sample.deviceId failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.deviceType, sample.deviceType == null ? 0 : sample.deviceType.length())){
            System.out.println("serialize sample.deviceType failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.currentPowerSeq.length())){
            System.out.println("serialize length of sample.currentPowerSeq failed.");
            return -2;
        }
        if (sample.currentPowerSeq.length() != 0){
            if (!CDRSerializer.put_float_array(cdr, sample.currentPowerSeq.get_contiguous_buffer(), sample.currentPowerSeq.length())){
                System.out.println("serialize sample.currentPowerSeq failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.dailyConsumptionSeq.length())){
            System.out.println("serialize length of sample.dailyConsumptionSeq failed.");
            return -2;
        }
        if (sample.dailyConsumptionSeq.length() != 0){
            if (!CDRSerializer.put_float_array(cdr, sample.dailyConsumptionSeq.get_contiguous_buffer(), sample.dailyConsumptionSeq.length())){
                System.out.println("serialize sample.dailyConsumptionSeq failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.weeklyConsumptionSeq.length())){
            System.out.println("serialize length of sample.weeklyConsumptionSeq failed.");
            return -2;
        }
        if (sample.weeklyConsumptionSeq.length() != 0){
            if (!CDRSerializer.put_float_array(cdr, sample.weeklyConsumptionSeq.get_contiguous_buffer(), sample.weeklyConsumptionSeq.length())){
                System.out.println("serialize sample.weeklyConsumptionSeq failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.timeSeq.length())){
            System.out.println("serialize length of sample.timeSeq failed.");
            return -2;
        }
        for (int i = 0; i < sample.timeSeq.length(); ++i){
            if (!CDRSerializer.put_string(cdr, sample.timeSeq.get_at(i), sample.timeSeq.get_at(i) == null ? 0 : sample.timeSeq.get_at(i).length())){
                System.out.println("serialize sample.timeSeq failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_string(cdr, sample.timeStamp, sample.timeStamp == null ? 0 : sample.timeStamp.length())){
            System.out.println("serialize sample.timeStamp failed.");
            return -2;
        }

        return 0;
    }

    synchronized public int deserializeI(Object _sample, long cdr){
        EnergyRawData sample = (EnergyRawData) _sample;
        sample.deviceId = CDRDeserializer.get_string(cdr);
        if(sample.deviceId ==null){
            System.out.println("deserialize member sample.deviceId failed.");
            return -3;
        }

        sample.deviceType = CDRDeserializer.get_string(cdr);
        if(sample.deviceType ==null){
            System.out.println("deserialize member sample.deviceType failed.");
            return -3;
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.currentPowerSeq failed.");
            return -2;
        }
        if (!sample.currentPowerSeq.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.currentPowerSeq failed.");
            return -3;
        }
        if (!CDRDeserializer.get_float_array(cdr, sample.currentPowerSeq.get_contiguous_buffer(), sample.currentPowerSeq.length())){
            System.out.println("deserialize sample.currentPowerSeq failed.");
            return -2;
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.dailyConsumptionSeq failed.");
            return -2;
        }
        if (!sample.dailyConsumptionSeq.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.dailyConsumptionSeq failed.");
            return -3;
        }
        if (!CDRDeserializer.get_float_array(cdr, sample.dailyConsumptionSeq.get_contiguous_buffer(), sample.dailyConsumptionSeq.length())){
            System.out.println("deserialize sample.dailyConsumptionSeq failed.");
            return -2;
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.weeklyConsumptionSeq failed.");
            return -2;
        }
        if (!sample.weeklyConsumptionSeq.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.weeklyConsumptionSeq failed.");
            return -3;
        }
        if (!CDRDeserializer.get_float_array(cdr, sample.weeklyConsumptionSeq.get_contiguous_buffer(), sample.weeklyConsumptionSeq.length())){
            System.out.println("deserialize sample.weeklyConsumptionSeq failed.");
            return -2;
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.timeSeq failed.");
            return -2;
        }
        if (!sample.timeSeq.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.timeSeq failed.");
            return -3;
        }
        for(int i =0 ;i < sample.timeSeq.length() ;++i)
        {
            sample.timeSeq.set_at(i, CDRDeserializer.get_string(cdr));
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
        EnergyRawData sample = (EnergyRawData)_sample;
        offset += get_sizeI(sample, cdr, offset);
        return offset - initialAlignment;
    }

    public int serialize_keyI(Object _sample, long cdr){
        EnergyRawData sample = (EnergyRawData)_sample;
        return 0;
    }

    public int deserialize_keyI(Object _sample, long cdr) {
        EnergyRawData sample = (EnergyRawData)_sample;
        return 0;
    }

    public TypeCode get_typecode(){
        if (s_typeCode != null) {
            return s_typeCode;
        }
        TypeCodeFactory factory = TypeCodeFactory.get_instance();

        s_typeCode = factory.create_struct_TC("EnergyRawData");
        if (s_typeCode == null){
            System.out.println("create struct EnergyRawData typecode failed.");
            return s_typeCode;
        }
        int ret = 0;
        TypeCodeImpl memberTc = new TypeCodeImpl();
        TypeCodeImpl eleTc = new TypeCodeImpl();

        memberTc = factory.create_string_TC(0xffffffff);
        if (memberTc == null){
            System.out.println("Get Member deviceId TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            0,
            0,
            "deviceId",
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
            System.out.println("Get Member deviceType TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            1,
            1,
            "deviceType",
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
            System.out.println("Get Member currentPowerSeq TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            2,
            2,
            "currentPowerSeq",
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
            System.out.println("Get Member dailyConsumptionSeq TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            3,
            3,
            "dailyConsumptionSeq",
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
            System.out.println("Get Member weeklyConsumptionSeq TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            4,
            4,
            "weeklyConsumptionSeq",
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
            System.out.println("Get Member timeSeq TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            5,
            5,
            "timeSeq",
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
            6,
            6,
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