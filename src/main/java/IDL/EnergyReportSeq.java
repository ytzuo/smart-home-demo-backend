package IDL;

import com.zrdds.infrastructure.ZRSequence;

public class EnergyReportSeq extends ZRSequence<EnergyReport> {

    protected Object[] alloc_element(int length) {
        EnergyReport[] result = new EnergyReport[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new EnergyReport();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        EnergyReport typedDst = (EnergyReport)dstEle;
        EnergyReport typedSrc = (EnergyReport)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}