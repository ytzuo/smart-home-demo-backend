package alert.dds;

/**
 * 简单的App DDS通信演示类
 * 使用新的简化版CommandPublisher和VehicleStatusSubscriber
 */
public class SimpleAppDemo {
    
    public static void main(String[] args) {
        // 创建发布者和订阅者实例
        SimpleCommandPublisher commandPublisher = new SimpleCommandPublisher(0);
        SimpleVehicleStatusSubscriber statusSubscriber = new SimpleVehicleStatusSubscriber(0);
        
        // 初始化
        commandPublisher.init();
        statusSubscriber.init();
        
        try {
            // 等待初始化完成
            Thread.sleep(1000);
            
            // 发送一些测试命令
            System.out.println("\n=== 发送测试命令 ===");
            commandPublisher.publishCommand("car001", "vehicle", "start_engine", 1.0f);
            Thread.sleep(1000);
            
            commandPublisher.publishCommand("car001", "vehicle", "lock_doors", 1.0f);
            Thread.sleep(1000);
            
            commandPublisher.publishCommand("car001", "vehicle", "set_fuel", 85.5f);
            Thread.sleep(1000);
            
            commandPublisher.publishCommand("car001", "vehicle", "update_location", 0.0f);
            
            // 保持运行一段时间以接收状态
            System.out.println("\n=== 等待接收车辆状态 ===");
            Thread.sleep(5000);
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 清理资源
            commandPublisher.close();
            statusSubscriber.close();
        }
    }
}