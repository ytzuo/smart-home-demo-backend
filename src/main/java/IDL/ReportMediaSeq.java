package IDL;

import com.zrdds.infrastructure.ZRSequence;

public class ReportMediaSeq extends ZRSequence<ReportMedia> {

    protected Object[] alloc_element(int length) {
        ReportMedia[] result = new ReportMedia[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new ReportMedia();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        ReportMedia typedDst = (ReportMedia)dstEle;
        ReportMedia typedSrc = (ReportMedia)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}