package alert.idl;

import com.zrdds.infrastructure.ZRSequence;

public class PresenceSeq extends ZRSequence<Presence> {

    protected Object[] alloc_element(int length) {
        Presence[] result = new Presence[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new Presence();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        Presence typedDst = (Presence)dstEle;
        Presence typedSrc = (Presence)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}