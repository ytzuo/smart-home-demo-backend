package HomeSimulator.furniture;

/**
 * 家具基础接口，为后续IDL扩展预留接口
 * 定义所有家具的通用行为
 */
public interface Furniture {
    /**
     * 获取家具ID
     */
    String getId();
    
    /**
     * 获取家具名称
     */
    String getName();
    
    /**
     * 获取家具类型
     */
    String getType();
    
    /**
     * 获取当前状态
     */
    String getStatus();
    
    /**
     * 设置新状态
     */
    boolean setStatus(String status);
    
    /**
     * 获取状态描述
     */
    String getStatusDescription();
    
    /**
     * 添加状态变更监听器
     */
    void addStatusChangeListener(StatusChangeListener listener);
    
    /**
     * 移除状态变更监听器
     */
    void removeStatusChangeListener(StatusChangeListener listener);
    
    /**
     * 状态变更监听器接口
     */
    interface StatusChangeListener {
        void onStatusChanged(String furnitureId, String oldStatus, String newStatus);
    }
}