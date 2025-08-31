package HomeSimulator.furniture;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import HomeSimulator.furniture.Furniture.StatusChangeListener;

/**
 * 家具管理器 - 管理多个家具的状态和状态修改
 * 支持多线程接收和处理家具状态信息
 */
public class FurnitureManager {
    private final Map<String, Furniture> furnitureMap;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicBoolean running;
    private final List<StatusUpdateListener> statusListeners;
    
    // 状态更新监听器接口
    public interface StatusUpdateListener {
        void onStatusUpdate(String furnitureId, String furnitureType, String newStatus);
    }

    public FurnitureManager() {
        this.furnitureMap = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(4);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(2);
        this.running = new AtomicBoolean(false);
        this.statusListeners = new CopyOnWriteArrayList<>();
        
        // 初始化默认家具
        initializeDefaultFurniture();
    }

    private void initializeDefaultFurniture() {
        // 添加默认灯具
        Light livingRoomLight = new Light("light1", "客厅灯");
        Light bedroomLight = new Light("light2", "卧室灯");
        
        // 添加默认空调
        AirConditioner livingRoomAC = new AirConditioner("ac1", "客厅空调");
        AirConditioner bedroomAC = new AirConditioner("ac2", "卧室空调");
        
        // 注册家具
        registerFurniture(livingRoomLight);
        registerFurniture(bedroomLight);
        registerFurniture(livingRoomAC);
        registerFurniture(bedroomAC);
        
        System.out.println("[HomeSimulator] 初始化默认家具完成");
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            System.out.println("[HomeSimulator] FurnitureManager 已启动");
            
            // 启动状态监控线程
            startStatusMonitoring();
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("[HomeSimulator] FurnitureManager 正在停止...");
            
            executorService.shutdown();
            scheduledExecutorService.shutdown();
            
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            System.out.println("[HomeSimulator] FurnitureManager 已停止");
        }
    }

    private void startStatusMonitoring() {
        // 定期监控家具状态变化
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (running.get()) {
                checkAllFurnitureStatus();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void checkAllFurnitureStatus() {
        for (Furniture furniture : furnitureMap.values()) {
            executorService.submit(() -> {
                try {
                    String currentStatus = furniture.getStatus();
                    // 模拟状态检查逻辑
                    // 这里可以添加实际的状态检查，如传感器读取等
                    
                    // 通知状态监听器
                    notifyStatusUpdate(furniture.getId(), furniture.getType(), currentStatus);
                    
                } catch (Exception e) {
                    System.err.println("[HomeSimulator] 检查家具状态时发生错误: " + e.getMessage());
                }
            });
        }
    }

    public void registerFurniture(Furniture furniture) {
        if (furniture == null) {
            throw new IllegalArgumentException("家具不能为空");
        }
        
        furnitureMap.put(furniture.getId(), furniture);
        
        // 添加状态变更监听器
        furniture.addStatusChangeListener(new StatusChangeListener() {
            @Override
            public void onStatusChanged(String furnitureId, String oldStatus, String newStatus) {
                System.out.printf("[HomeSimulator] 家具状态变更: %s 从 %s 变为 %s%n", 
                        furnitureId, oldStatus, newStatus);
                
                // 通知状态更新监听器
                Furniture furniture = furnitureMap.get(furnitureId);
                if (furniture != null) {
                    notifyStatusUpdate(furnitureId, furniture.getType(), newStatus);
                }
            }
        });
        
        System.out.printf("[HomeSimulator] 注册新家具: %s (%s)%n", furniture.getName(), furniture.getType());
    }

    public void unregisterFurniture(String furnitureId) {
        Furniture removed = furnitureMap.remove(furnitureId);
        if (removed != null) {
            System.out.printf("[HomeSimulator] 移除家具: %s%n", removed.getName());
        }
    }

    public Furniture getFurniture(String furnitureId) {
        return furnitureMap.get(furnitureId);
    }

    public List<Furniture> getAllFurniture() {
        return new ArrayList<>(furnitureMap.values());
    }

    public List<Furniture> getFurnitureByType(String type) {
        List<Furniture> result = new ArrayList<>();
        for (Furniture furniture : furnitureMap.values()) {
            if (furniture.getType().equals(type)) {
                result.add(furniture);
            }
        }
        return result;
    }

    public boolean updateFurnitureStatus(String furnitureId, String newStatus) {
        Furniture furniture = furnitureMap.get(furnitureId);
        if (furniture != null) {
            return furniture.setStatus(newStatus);
        }
        System.err.println("[HomeSimulator] 未找到家具: " + furnitureId);
        return false;
    }

    public void addStatusUpdateListener(StatusUpdateListener listener) {
        statusListeners.add(listener);
    }

    public void removeStatusUpdateListener(StatusUpdateListener listener) {
        statusListeners.remove(listener);
    }

    private void notifyStatusUpdate(String furnitureId, String furnitureType, String newStatus) {
        for (StatusUpdateListener listener : statusListeners) {
            executorService.submit(() -> {
                try {
                    listener.onStatusUpdate(furnitureId, furnitureType, newStatus);
                } catch (Exception e) {
                    System.err.println("[HomeSimulator] 通知状态更新时发生错误: " + e.getMessage());
                }
            });
        }
    }

    public Map<String, String> getAllFurnitureStatus() {
        Map<String, String> statusMap = new HashMap<>();
        for (Furniture furniture : furnitureMap.values()) {
            statusMap.put(furniture.getId(), furniture.getStatus());
        }
        return statusMap;
    }

    public String getFurnitureStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("家具状态汇总:\n");
        
        for (Furniture furniture : furnitureMap.values()) {
            summary.append(String.format("  %s: %s\n", 
                    furniture.getName(), furniture.getStatusDescription()));
        }
        
        return summary.toString();
    }
}