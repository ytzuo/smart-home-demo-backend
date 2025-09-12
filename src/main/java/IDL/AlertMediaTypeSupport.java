package IDL;

import com.zrdds.infrastructure.*;
import com.zrdds.topic.TypeSupport;
import com.zrdds.publication.DataWriter;
import com.zrdds.subscription.DataReader;
import java.io.UnsupportedEncodingException;

public class AlertMediaTypeSupport extends TypeSupport {
    private String type_name = "AlertMedia";
    private static TypeCodeImpl s_typeCode = null;
    private static AlertMediaTypeSupport m_instance = new AlertMediaTypeSupport();

    private final byte[] tmp_byte_obj = new byte[1];
    private final char[] tmp_char_obj = new char[1];
    private final short[] tmp_short_obj = new short[1];
    private final int[] tmp_int_obj = new int[1];
    private final long[] tmp_long_obj = new long[1];
    private final float[] tmp_float_obj = new float[1];
    private final double[] tmp_double_obj = new double[1];
    private final boolean[] tmp_boolean_obj = new boolean[1];

    
    private AlertMediaTypeSupport(){}

    
    public static TypeSupport get_instance() { return m_instance; }

    public Object create_sampleI() {
        AlertMedia sample = new AlertMedia();
        return sample;
    }

    public void destroy_sampleI(Object sample) {

    }

    public int copy_sampleI(Object dst,Object src) {
        AlertMedia AlertMediaDst = (AlertMedia)dst;
        AlertMedia AlertMediaSrc = (AlertMedia)src;
        AlertMediaDst.copy(AlertMediaSrc);
        return 1;
    }

    public int print_sample(Object _sample) {
        if (_sample == null){
            System.out.println("NULL");
            return -1;
        }
        AlertMedia sample = (AlertMedia)_sample;
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
        System.out.println("sample.alert_id:" + sample.alert_id);
        System.out.println("sample.media_type:" + sample.media_type);
        System.out.println("sample.total_size:" + sample.total_size);
        System.out.println("sample.chunk_seq:" + sample.chunk_seq);
        System.out.println("sample.chunk_size:" + sample.chunk_size);
        int chunkTmpLen = sample.chunk.length();
        System.out.println("sample.chunk.length():" +chunkTmpLen);
        for (int i = 0; i < chunkTmpLen; ++i){
            System.out.println("sample.chunk.get_at(" + i + "):" + sample.chunk.get_at(i));
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

    public DataReader create_data_reader() {return new AlertMediaDataReader();}

    public DataWriter create_data_writer() {return new AlertMediaDataWriter();}

    public TypeCode get_inner_typecode(){
        TypeCode userTypeCode = get_typecode();
        if (userTypeCode == null) return null;
        return userTypeCode.get_impl();
    }

    public int get_sizeI(Object _sample,long cdr, int offset) throws UnsupportedEncodingException {
        int initialAlignment = offset;
        AlertMedia sample = (AlertMedia)_sample;
        offset += CDRSerializer.get_string_size(sample.deviceId == null ? 0 : sample.deviceId.getBytes().length, offset);

        offset += CDRSerializer.get_string_size(sample.deviceType == null ? 0 : sample.deviceType.getBytes().length, offset);

        offset += CDRSerializer.get_untype_size(4, offset);

        offset += CDRSerializer.get_untype_size(4, offset);

        offset += CDRSerializer.get_untype_size(4, offset);

        offset += CDRSerializer.get_untype_size(4, offset);

        offset += CDRSerializer.get_untype_size(4, offset);

        offset += CDRSerializer.get_untype_size(4, offset);
        int chunkLen = sample.chunk.length();
        if (chunkLen != 0){
            offset += 1 * chunkLen;
        }

        return offset - initialAlignment;
    }

    public int serializeI(Object _sample ,long cdr) {
         AlertMedia sample = (AlertMedia) _sample;

        if (!CDRSerializer.put_string(cdr, sample.deviceId, sample.deviceId == null ? 0 : sample.deviceId.length())){
            System.out.println("serialize sample.deviceId failed.");
            return -2;
        }

        if (!CDRSerializer.put_string(cdr, sample.deviceType, sample.deviceType == null ? 0 : sample.deviceType.length())){
            System.out.println("serialize sample.deviceType failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.alert_id)){
            System.out.println("serialize sample.alert_id failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.media_type)){
            System.out.println("serialize sample.media_type failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.total_size)){
            System.out.println("serialize sample.total_size failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.chunk_seq)){
            System.out.println("serialize sample.chunk_seq failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.chunk_size)){
            System.out.println("serialize sample.chunk_size failed.");
            return -2;
        }

        if (!CDRSerializer.put_int(cdr, sample.chunk.length())){
            System.out.println("serialize length of sample.chunk failed.");
            return -2;
        }
        if (sample.chunk.length() != 0){
            if (!CDRSerializer.put_byte_array(cdr, sample.chunk.get_contiguous_buffer(), sample.chunk.length())){
                System.out.println("serialize sample.chunk failed.");
                return -2;
            }
        }

        return 0;
    }

    synchronized public int deserializeI(Object _sample, long cdr){
        AlertMedia sample = (AlertMedia) _sample;
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
            System.out.println("deserialize sample.alert_id failed.");
            return -2;
        }
        sample.alert_id= tmp_int_obj[0];

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize sample.media_type failed.");
            return -2;
        }
        sample.media_type= tmp_int_obj[0];

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize sample.total_size failed.");
            return -2;
        }
        sample.total_size= tmp_int_obj[0];

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize sample.chunk_seq failed.");
            return -2;
        }
        sample.chunk_seq= tmp_int_obj[0];

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize sample.chunk_size failed.");
            return -2;
        }
        sample.chunk_size= tmp_int_obj[0];

        if (!CDRDeserializer.get_int_array(cdr, tmp_int_obj, 1)){
            System.out.println("deserialize length of sample.chunk failed.");
            return -2;
        }
        if (!sample.chunk.ensure_length(tmp_int_obj[0], tmp_int_obj[0])){
            System.out.println("Set maxiumum member sample.chunk failed.");
            return -3;
        }
        if (!CDRDeserializer.get_byte_array(cdr, sample.chunk.get_contiguous_buffer(), sample.chunk.length())){
            System.out.println("deserialize sample.chunk failed.");
            return -2;
        }

        return 0;
    }

    public int get_key_sizeI(Object _sample,long cdr,int offset)throws UnsupportedEncodingException {
        int initialAlignment = offset;
        AlertMedia sample = (AlertMedia)_sample;
        offset += get_sizeI(sample, cdr, offset);
        return offset - initialAlignment;
    }

    public int serialize_keyI(Object _sample, long cdr){
        AlertMedia sample = (AlertMedia)_sample;
        return 0;
    }

    public int deserialize_keyI(Object _sample, long cdr) {
        AlertMedia sample = (AlertMedia)_sample;
        return 0;
    }

    public TypeCode get_typecode(){
        if (s_typeCode != null) {
            return s_typeCode;
        }
        TypeCodeFactory factory = TypeCodeFactory.get_instance();

        s_typeCode = factory.create_struct_TC("IDL.AlertMedia");
        if (s_typeCode == null){
            System.out.println("create struct AlertMedia typecode failed.");
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

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_INT);
        if (memberTc == null){
            System.out.println("Get Member alert_id TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            2,
            2,
            "alert_id",
            memberTc,
            false,
            false);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_INT);
        if (memberTc == null){
            System.out.println("Get Member media_type TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            3,
            3,
            "media_type",
            memberTc,
            false,
            false);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_INT);
        if (memberTc == null){
            System.out.println("Get Member total_size TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            4,
            4,
            "total_size",
            memberTc,
            false,
            false);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_INT);
        if (memberTc == null){
            System.out.println("Get Member chunk_seq TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            5,
            5,
            "chunk_seq",
            memberTc,
            false,
            false);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_INT);
        if (memberTc == null){
            System.out.println("Get Member chunk_size TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            6,
            6,
            "chunk_size",
            memberTc,
            false,
            false);
        if (ret < 0)
        {
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }

        memberTc = factory.get_primitive_TC(TypeCodeKind.DDS_TK_UCHAR);
        if (memberTc != null)
        {
            memberTc = factory.create_sequence_TC(0xffffffff, memberTc);
        }
        if (memberTc == null){
            System.out.println("Get Member chunk TypeCode failed.");
            factory.delete_TC(s_typeCode);
            s_typeCode = null;
            return null;
        }
        ret = s_typeCode.add_member_to_struct(
            7,
            7,
            "chunk",
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