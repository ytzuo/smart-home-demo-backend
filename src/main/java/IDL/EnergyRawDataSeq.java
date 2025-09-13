package IDL;

import com.zrdds.infrastructure.ZRSequence;

public class EnergyRawDataSeq extends ZRSequence<EnergyRawData> {

    protected Object[] alloc_element(int length) {
        EnergyRawData[] result = new EnergyRawData[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new EnergyRawData();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        EnergyRawData typedDst = (EnergyRawData)dstEle;
        EnergyRawData typedSrc = (EnergyRawData)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}