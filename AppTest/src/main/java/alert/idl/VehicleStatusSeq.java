package alert.idl;

import com.zrdds.infrastructure.ZRSequence;

public class VehicleStatusSeq extends ZRSequence<VehicleStatus> {

    protected Object[] alloc_element(int length) {
        VehicleStatus[] result = new VehicleStatus[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new VehicleStatus();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        VehicleStatus typedDst = (VehicleStatus)dstEle;
        VehicleStatus typedSrc = (VehicleStatus)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}