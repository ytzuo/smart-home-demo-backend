package IDL;

import com.zrdds.infrastructure.*;
import com.zrdds.topic.TypeSupport;
import com.zrdds.publication.DataWriter;
import com.zrdds.subscription.DataReader;
import java.io.UnsupportedEncodingException;

public class HomeStatusTypeSupport extends TypeSupport {
    private String type_name = "HomeStatus";
    private static TypeCodeImpl s_typeCode = null;
    private static HomeStatusTypeSupport m_instance = new HomeStatusTypeSupport();

    private final byte[] tmp_byte_obj = new byte[1];
    private final char[] tmp_char_obj = new char[1];
    private final short[] tmp_short_obj = new short[1];
    private final int[] tmp_int_obj = new int[1];
    private final long[] tmp_long_obj = new long[1];
    private final float[] tmp_float_obj = new float[1];
    private final double[] tmp_double_obj = new double[1];
    private final boolean[] tmp_boolean_obj = new boolean[1];

    
    private HomeStatusTypeSupport(){}

    
    public static TypeSupport get_instance() { return m_instance; }

    public Object create_sampleI() {
        HomeStatus sample = new HomeStatus();
        return sample;
    }

    public void destroy_sampleI(Object sample) {

    }

    public int copy_sampleI(Object dst,Object src) {
        HomeStatus HomeStatusDst = (HomeStatus)dst;
        HomeStatus HomeStatusSrc = (HomeStatus)src;
        HomeStatusDst.copy(HomeStatusSrc);
        return 1;
    }

    public int print_sample(Object _sample) {
        if (_sample == null){
            System.out.println("NULL");
            return -1;
        }
        HomeStatus sample = (HomeStatus)_sample;
        int deviceIdsTmpLen = sample.deviceIds.length();
        System.out.println("sample.deviceIds.length():" +deviceIdsTmpLen);
        for (int i = 0; i < deviceIdsTmpLen; ++i){
            if (sample.deviceIds.get_at(i) != null){
                System.out.println("sample.deviceIds.get_at(" + i + "):" + sample.deviceIds.get_at(i));
            }
            else{
                System.out.println("sample.deviceIds.get_at(" + i + "): null");
            }
        }
        int deviceTypesTmpLen = sample.deviceTypes.length();
        System.out.println("sample.deviceTypes.length():" +deviceTypesTmpLen);
        for (int i = 0; i < deviceTypesTmpLen; ++i){
            if (sample.deviceTypes.get_at(i) != null){
                System.out.println("sample.deviceTypes.get_at(" + i + "):" + sample.deviceTypes.get_at(i));
            }
            else{
                System.out.println("sample.deviceTypes.get_at(" + i + "): null");
            }
        }
        int deviceStatusTmpLen = sample.deviceStatus.length();
        System.out.println("sample.deviceStatus.length():" +deviceStatusTmpLen);
        for (int i = 0; i < deviceStatusTmpLen; ++i){
            if (sample.deviceStatus.get_at(i) != null){
                System.out.println("sample.deviceStatus.get_at(" + i + "):" + sample.deviceStatus.get_at(i));
            }
            else{
                System.out.println("sample.deviceStatus.get_at(" + i + "): null");
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
        return 199172;
    }

    public int get_max_key_sizeI(){
        return 199172;
    }

    public boolean has_keyI(){
        return false;
    }

    public String get_keyhashI(Object sample, long cdr){
        return "-1";
    }

    public DataReader create_data_reader() {return new HomeStatusDataReader();}

    public DataWriter create_data_writer() {return new HomeStatusDataWriter();}

    public TypeCode get_inner_typecode(){
        TypeCode userTypeCode = get_typecode();
        if (userTypeCode == null) return null;
        return userTypeCode.get_impl();
    }

    public int get_sizeI(Object _sample,long cdr, int offset) throws UnsupportedEncodingException {
        int initialAlignment = offset;
        HomeStatus sample = (HomeStatus)_sample;
        offset += CDRSerializer.get_untype_size(4, offset);
        int deviceIdsLen = sample.deviceIds.length();
        if (deviceIdsLen != 0){
            for(int i = 0; i<sample.deviceIds.length(); ++i)
            {
                offset += CDRSerializer.get_string_size(sample.deviceIds.get_at(i).getBytes().length,offset);
            }
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int deviceTypesLen = sample.deviceTypes.length();
        if (deviceTypesLen != 0){
            for(int i = 0; i<sample.deviceTypes.length(); ++i)
            {
                offset += CDRSerializer.get_string_size(sample.deviceTypes.get_at(i).getBytes().length,offset);
            }
        }

        offset += CDRSerializer.get_untype_size(4, offset);
        int deviceStatusLen = sample.deviceStatus.length();
        if (deviceStatusLen != 0){
            for(int i = 0; i<sample.deviceStatus.length(); ++i)
            {
                offset += CDRSerializer.get_string_size(sample.deviceStatus.get_at(i).getBytes().length,offset);
            }
        }

        offset += CDRSerializer.get_string_size(sample.timeStamp == null ? 0 : sample.timeStamp.getBytes().length, offset);

        return offset - initialAlignment;
    }

    public int serializeI(Object _sample ,long cdr) {
         HomeStatus sample = (HomeStatus) _sample;

        if (!CDRSerializer.put_int(cdr, sample.deviceIds.length())){
            System.out.println("serialize length of sample.deviceIds failed.");
            return -2;
        }
        for (int i = 0; i < sample.deviceIds.length(); ++i){
            if (!CDRSerializer.put_string(cdr, sample.deviceIds.get_at(i), sample.deviceIds.get_at(i) == null ? 0 : sample.deviceIds.get_at(i).length())){
                System.out.println("serialize sample.deviceIds failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.deviceTypes.length())){
            System.out.println("serialize length of sample.deviceTypes failed.");
            return -2;
        }
        for (int i = 0; i < sample.deviceTypes.length(); ++i){
            if (!CDRSerializer.put_string(cdr, sample.deviceTypes.get_at(i), sample.deviceTypes.get_at(i) == null ? 0 : sample.deviceTypes.get_at(i).length())){
                System.out.println("serialize sample.deviceTypes failed.");
                return -2;
            }
        }

        if (!CDRSerializer.put_int(cdr, sample.deviceStatus.length())){
            System.out.println("serialize length of sample.deviceStatus failed.");
            return -2;
        }
        for (int i = 0; i < sample.deviceStatus.length(); ++i){
            if (!CDRSerializer.put_string(cdr, sample.deviceStatus.get_at(i), sample.deviceStatus.get_at(i) == null ? 0 : sample.deviceStatus.get_at(i).length())){
                System.out.println("serialize sample.deviceStatus failed.");
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
        HomeStatus sample = (HomeStatus) _sample;
        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.deviceIds failed.");
            return -2;
        }
        if (!sample.deviceIds.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.deviceIds failed.");
            return -3;
        }
        for(int i =0 ;i < sample.deviceIds.length() ;++i)
        {
            sample.deviceIds.set_at(i, CDRDeserializer.get_string(cdr));
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.deviceTypes failed.");
            return -2;
        }
        if (!sample.deviceTypes.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.deviceTypes failed.");
            return -3;
        }
        for(int i =0 ;i < sample.deviceTypes.length() ;++i)
        {
            sample.deviceTypes.set_at(i, CDRDeserializer.get_string(cdr));
        }

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.deviceStatus failed.");
            return -2;
        }
        if (!sample.deviceStatus.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.deviceStatus failed.");
            return -3;
        }
        for(int i =0 ;i < sample.deviceStatus.length() ;++i)
        {
            sample.deviceStatus.set_at(i, CDRDeserializer.get_string(cdr));
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
        HomeStatus sample = (HomeStatus)_sample;
        offset += get_sizeI(sample, cdr, offset);
        return offset - initialAlignment;
    }

    public int serialize_keyI(Object _sample, long cdr){
        HomeStatus sample = (HomeStatus)_sample;
        return 0;
    }

    public int deserialize_keyI(Object _sample, long cdr) {
        HomeStatus sample = (HomeStatus)_sample;
        return 0;
    }

    public TypeCode get_typecode(){
        if (s_typeCode != null) {
            return s_typeCode;
        }
        TypeCodeFactory factory = TypeCodeFactory.get_instance();

        s_typeCode = factory.create_struct_TC("SmartDemo03.HomeStatus");
        if (s_typeCode == null){
            System.out.println("create struct HomeStatus typecode failed.");
            return s_typeCode;
        }
        int ret = 0;
        TypeCodeImpl memberTc = new TypeCodeImpl();
        TypeCodeImpl eleTc = new TypeCodeImpl();

        memberTc = factory.create_string_TC(255);
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(255, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member deviceIds TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            0,
            0,
            "deviceIds",
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
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(255, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member deviceTypes TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            1,
            1,
            "deviceTypes",
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
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(255, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member deviceStatus TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            2,
            2,
            "deviceStatus",
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