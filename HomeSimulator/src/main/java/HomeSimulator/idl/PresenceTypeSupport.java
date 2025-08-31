package HomeSimulator.idl;

import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriter;
import com.zrdds.subscription.DataReader;
import com.zrdds.topic.TypeSupport;

import java.io.UnsupportedEncodingException;

public class PresenceTypeSupport extends TypeSupport {
    private String type_name = "Presence";
    private static TypeCodeImpl s_typeCode = null;
    private static PresenceTypeSupport m_instance = new PresenceTypeSupport();

    private final byte[] tmp_byte_obj = new byte[1];
    private final char[] tmp_char_obj = new char[1];
    private final short[] tmp_short_obj = new short[1];
    private final int[] tmp_int_obj = new int[1];
    private final long[] tmp_long_obj = new long[1];
    private final float[] tmp_float_obj = new float[1];
    private final double[] tmp_double_obj = new double[1];
    private final boolean[] tmp_boolean_obj = new boolean[1];

    
    private PresenceTypeSupport(){}

    
    public static TypeSupport get_instance() { return m_instance; }

    public Object create_sampleI() {
        Presence sample = new Presence();
        return sample;
    }

    public void destroy_sampleI(Object sample) {

    }

    public int copy_sampleI(Object dst,Object src) {
        Presence PresenceDst = (Presence)dst;
        Presence PresenceSrc = (Presence)src;
        PresenceDst.copy(PresenceSrc);
        return 1;
    }

    public int print_sample(Object _sample) {
        if (_sample == null){
            System.out.println("NULL");
            return -1;
        }
        Presence sample = (Presence)_sample;
        System.out.println("sample.inRange:" + sample.inRange);
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
        return 784;
    }

    public int get_max_key_sizeI(){
        return 784;
    }

    public boolean has_keyI(){
        return false;
    }

    public String get_keyhashI(Object sample, long cdr){
        return "-1";
    }

    public DataReader create_data_reader() {return new PresenceDataReader();}

    public DataWriter create_data_writer() {return new PresenceDataWriter();}

    public TypeCode get_inner_typecode(){
        TypeCode userTypeCode = get_typecode();
        if (userTypeCode == null) return null;
        return userTypeCode.get_impl();
    }

    public int get_sizeI(Object _sample,long cdr, int offset) throws UnsupportedEncodingException {
        int initialAlignment = offset;
        Presence sample = (Presence)_sample;
        offset += CDRSerializer.get_untype_size(1, offset);

        offset += CDRSerializer.get_string_size(sample.deviceId == null ? 0 : sample.deviceId.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.deviceType == null ? 0 : sample.deviceType.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.timeStamp == null ? 0 : sample.timeStamp.getBytes().length, offset);

        return offset - initialAlignment;
    }

    public int serializeI(Object _sample ,long cdr) {
         Presence sample = (Presence) _sample;

        if (!CDRSerializer.put_boolean(cdr, sample.inRange)){
            System.out.println("serialize sample.inRange failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.deviceId, sample.deviceId == null ? 0 : sample.deviceId.length())){
            System.out.println("serialize sample.deviceId failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.deviceType, sample.deviceType == null ? 0 : sample.deviceType.length())){
            System.out.println("serialize sample.deviceType failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.timeStamp, sample.timeStamp == null ? 0 : sample.timeStamp.length())){
            System.out.println("serialize sample.timeStamp failed.");
            return -2;
        }

        return 0;
    }

    synchronized public int deserializeI(Object _sample, long cdr){
        Presence sample = (Presence) _sample;
        if (!CDRDeserializer.get_boolean_array(cdr, tmp_boolean_obj, 1)){
            System.out.println("deserialize sample.inRange failed.");
            return -2;
        }
        sample.inRange= tmp_boolean_obj[0];

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

        sample.timeStamp = CDRDeserializer.get_string(cdr);
        if(sample.timeStamp ==null){
            System.out.println("deserialize member sample.timeStamp failed.");
            return -3;
        }

        return 0;
    }

    public int get_key_sizeI(Object _sample,long cdr,int offset)throws UnsupportedEncodingException {
        int initialAlignment = offset;
        Presence sample = (Presence)_sample;
        offset += get_sizeI(sample, cdr, offset);
        return offset - initialAlignment;
    }

    public int serialize_keyI(Object _sample, long cdr){
        Presence sample = (Presence)_sample;
        return 0;
    }

    public int deserialize_keyI(Object _sample, long cdr) {
        Presence sample = (Presence)_sample;
        return 0;
    }

    public TypeCode get_typecode(){
        if (s_typeCode != null) {
            return s_typeCode;
        }
        TypeCodeFactory factory = TypeCodeFactory.get_instance();

        s_typeCode = factory.create_struct_TC("SmartDemo.Presence");
        if (s_typeCode == null){
            System.out.println("create struct Presence typecode failed.");
            return s_typeCode;
        }
        int ret = 0;
        TypeCodeImpl memberTc = new TypeCodeImpl();
        TypeCodeImpl eleTc = new TypeCodeImpl();

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_BOOLEAN);
        if (memberTc == null){
            System.out.println("Get Member inRange TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            0,
            0,
            "inRange",
            memberTc,
            false,
            false);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member deviceId TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            1,
            1,
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

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member deviceType TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            2,
            2,
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

        memberTc = factory.create_string_TC(255);
        if (memberTc == null){
            System.out.println("Get Member timeStamp TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            3,
            3,
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