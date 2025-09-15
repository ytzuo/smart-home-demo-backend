package CarSimulator;

import CarSimulator.DDS.AIHealthReportPublisher;
import IDL.VehicleHealthReport;
import com.zrdds.infrastructure.FloatSeq;
import com.zrdds.infrastructure.StringSeq;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI集成测试类
 * 用于测试大模型API集成功能
 */
public class AIIntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("=== AI集成测试开始 ===");
        
        // 创建测试用的健康报告
        VehicleHealthReport testReport = createTestHealthReport();
        
        // 创建AI健康报告发布器
        AIHealthReportPublisher publisher = new AIHealthReportPublisher();
        
        // 测试同步调用
        testSyncAnalysis(publisher, testReport);
        
        // 测试异步调用
        testAsyncAnalysis(publisher, testReport);
        
        System.out.println("=== AI集成测试完成 ===");
    }
    
    private static VehicleHealthReport createTestHealthReport() {
        VehicleHealthReport report = new VehicleHealthReport();
        report.vehicleId = "TEST_CAR_001";
        report.timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        
        // 创建测试数据
        String[] componentTypes = {"engine", "tire", "brake", "battery", "oil"};
        String[] componentStatuses = {"normal", "warning", "normal", "error", "normal"};
        float[] metrics = {88.5f, 30.2f, 92.1f, 78.3f, 85.7f};
        
        // 填充组件类型
        report.componentTypes = new StringSeq();
        report.componentTypes.ensure_length(componentTypes.length, componentTypes.length);
        for (int i = 0; i < componentTypes.length; i++) {
            report.componentTypes.set_at(i, componentTypes[i]);
        }
        
        // 填充组件状态
        report.componentStatuses = new StringSeq();
        report.componentStatuses.ensure_length(componentStatuses.length, componentStatuses.length);
        for (int i = 0; i < componentStatuses.length; i++) {
            report.componentStatuses.set_at(i, componentStatuses[i]);
        }
        
        // 填充指标数据
        report.metrics = new FloatSeq();
        report.metrics.ensure_length(metrics.length, metrics.length);
        for (int i = 0; i < metrics.length; i++) {
            report.metrics.set_at(i, metrics[i]);
        }
        
        report.nextMaintenance = "建议在2024年3月进行保养";
        
        return report;
    }
    
    private static void testSyncAnalysis(AIHealthReportPublisher publisher, VehicleHealthReport report) {
        System.out.println("\n--- 测试同步AI分析 ---");
        try {
            long startTime = System.currentTimeMillis();
            
            // 注意：这里不会真正发布到DDS，因为没有初始化DDS环境
            // 但可以测试AI分析逻辑
            System.out.println("开始同步AI分析测试...");
            
            // 创建AI分析服务进行直接测试
            AIAnalysisService aiService = new AIAnalysisService();
            String analysis = aiService.analyzeVehicleHealth(report);
            
            long endTime = System.currentTimeMillis();
            System.out.println("同步分析完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println("分析结果长度: " + analysis.length() + " 字符");
            System.out.println("完整分析结果: " + analysis);
            
            // 验证内容拼接是否正确
            if (analysis.length() > 0 && !analysis.contains("null")) {
                System.out.println("✅ 同步分析测试通过 - 内容拼接正确");
            } else {
                System.err.println("❌ 同步分析测试失败 - 内容拼接异常");
            }
            
        } catch (Exception e) {
            System.err.println("同步测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testAsyncAnalysis(AIHealthReportPublisher publisher, VehicleHealthReport report) {
        System.out.println("\n--- 测试异步AI分析 ---");
        try {
            long startTime = System.currentTimeMillis();
            
            // 创建AI分析服务进行异步测试
            AIAnalysisService aiService = new AIAnalysisService();
            CompletableFuture<String> future = aiService.analyzeVehicleHealthAsync(report);
            
            System.out.println("异步分析请求已提交，等待结果...");
            
            // 等待异步结果
            String analysis = future.get(30, TimeUnit.SECONDS);
            
            long endTime = System.currentTimeMillis();
            System.out.println("异步分析完成，总耗时: " + (endTime - startTime) + "ms");
            System.out.println("分析结果长度: " + analysis.length() + " 字符");
            System.out.println("完整分析结果: " + analysis);
            
            // 验证异步执行和内容拼接
            if (analysis.length() > 0 && !analysis.contains("null")) {
                System.out.println("✅ 异步分析测试通过 - 内容拼接正确，异步执行成功");
            } else {
                System.err.println("❌ 异步分析测试失败 - 内容拼接异常或异步执行失败");
            }
            
        } catch (Exception e) {
            System.err.println("异步测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}