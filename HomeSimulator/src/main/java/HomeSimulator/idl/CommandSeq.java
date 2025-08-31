package HomeSimulator.idl;

import com.zrdds.infrastructure.ZRSequence;

public class CommandSeq extends ZRSequence<Command> {

    protected Object[] alloc_element(int length) {
        Command[] result = new Command[length];
        for (int i = 0; i < result.length; ++i) {
             result[i] = new Command();
        }
        return result;
    }

    protected Object copy_from_element(Object dstEle, Object srcEle){
        Command typedDst = (Command)dstEle;
        Command typedSrc = (Command)srcEle;
        return typedDst.copy(typedSrc);
    }

    public void pull_from_nativeI(long nativeSeq){

    }

    public void push_to_nativeI(long nativeSeq){

    }
}