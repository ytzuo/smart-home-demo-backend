package HomeSimulator.idl;

import com.zrdds.infrastructure.ZRSequence;

public class HomeStatusSeq extends ZRSequence<HomeStatus> {

    protected Object[] alloc_element(int length) {
        HomeStatus[] result = new HomeStatus[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new HomeStatus();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        HomeStatus typedDst = (HomeStatus)dstEle;
        HomeStatus typedSrc = (HomeStatus)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}