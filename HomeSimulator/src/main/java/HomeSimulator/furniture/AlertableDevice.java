package HomeSimulator.furniture;

/**
 * 可报警设备接口
 * 实现此接口的设备可以独立检测异常并发出报警
 */
public interface AlertableDevice {
    
    /**
     * 检查设备是否存在异常
     * @return 如果设备异常返回true，否则返回false
     */
    boolean checkAbnormal();
    
    /**
     * 获取设备异常信息
     * @return 异常信息描述
     */
    String getAlertMessage();
    
    /**
     * 获取设备报警类型
     * @return 报警类型
     */
    String getAlertType();
    
    /**
     * 重置设备报警状态
     */
    void resetAlert();
    
    /**
     * 获取设备ID
     * @return 设备ID
     */
    String getDeviceId();
    
    /**
     * 获取设备类型
     * @return 设备类型
     */
    String getDeviceType();
}