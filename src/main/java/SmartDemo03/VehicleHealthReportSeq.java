package SmartDemo03;

import com.zrdds.infrastructure.ZRSequence;

public class VehicleHealthReportSeq extends ZRSequence<VehicleHealthReport> {

    protected Object[] alloc_element(int length) {
        VehicleHealthReport[] result = new VehicleHealthReport[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new VehicleHealthReport();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        VehicleHealthReport typedDst = (VehicleHealthReport)dstEle;
        VehicleHealthReport typedSrc = (VehicleHealthReport)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}