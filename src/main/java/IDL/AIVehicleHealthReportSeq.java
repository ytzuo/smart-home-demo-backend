package IDL;

import com.zrdds.infrastructure.ZRSequence;

public class AIVehicleHealthReportSeq extends ZRSequence<AIVehicleHealthReport> {

    protected Object[] alloc_element(int length) {
        AIVehicleHealthReport[] result = new AIVehicleHealthReport[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new AIVehicleHealthReport();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        AIVehicleHealthReport typedDst = (AIVehicleHealthReport)dstEle;
        AIVehicleHealthReport typedSrc = (AIVehicleHealthReport)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}