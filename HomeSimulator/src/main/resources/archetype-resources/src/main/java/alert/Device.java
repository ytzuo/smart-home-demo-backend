package alert;

public interface Device extends Runnable{
    String getDeviceId();
    String getDeviceType();
}
