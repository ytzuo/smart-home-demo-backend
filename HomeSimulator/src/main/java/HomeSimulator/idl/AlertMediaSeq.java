package HomeSimulator.idl;

import com.zrdds.infrastructure.ZRSequence;

public class AlertMediaSeq extends ZRSequence<AlertMedia> {

    protected Object[] alloc_element(int length) {
        AlertMedia[] result = new AlertMedia[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new AlertMedia();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        AlertMedia typedDst = (AlertMedia)dstEle;
        AlertMedia typedSrc = (AlertMedia)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}