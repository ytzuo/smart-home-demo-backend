package HomeSimulator.furniture;

/**
 * 家具接口 - 所有家具类型需实现此接口
 */
public interface Furniture {
    // 获取家具唯一ID（非null）
    String getId();

    // 获取家具名称（非null）
    String getName();

    // 获取家具类型（如"light"/"ac"，非null）
    String getType();

    // 获取当前状态（如"on"/"off"/"cool"，非null）
    String getStatus();

    // 设置状态（返回是否设置成功）
    boolean setStatus(String newStatus);

    // 获取状态描述（如"客厅灯: 开启"，非null）
    String getStatusDescription();

    // 添加状态变更监听器
    void addStatusChangeListener(StatusChangeListener listener);

    // 移除状态变更监听器
    void removeStatusChangeListener(StatusChangeListener listener);

    /**
     * 状态变更监听器接口
     */
    @FunctionalInterface
    interface StatusChangeListener {
        void onStatusChanged(String furnitureId, String oldStatus, String newStatus);
    }
}