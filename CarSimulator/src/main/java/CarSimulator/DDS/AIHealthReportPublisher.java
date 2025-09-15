package CarSimulator.DDS;

import CarSimulator.AIAnalysisService;
import IDL.AIVehicleHealthReport;
import IDL.AIVehicleHealthReportDataWriter;
import IDL.VehicleHealthReport;
import com.zrdds.infrastructure.*;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI健康报告发布器
 * 用于调用大模型API分析车辆健康数据并发布分析结果
 */
public class AIHealthReportPublisher {
    
    // DDS相关
    private AIVehicleHealthReportDataWriter writer;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // AI分析服务
    private final AIAnalysisService aiAnalysisService;
    
    public AIHealthReportPublisher() {
        this.aiAnalysisService = new AIAnalysisService();
    }
    
    /**
     * 启动AI健康报告发布器
     */
    public boolean start(Publisher pub, Topic topic) {
        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 10;
        
        // 创建DataWriter
        writer = (AIVehicleHealthReportDataWriter) pub.create_datawriter(
                topic,
                dwQos,
                null,
                StatusKind.STATUS_MASK_NONE);
        
        if (writer == null) {
            System.err.println("[AIHealthReportPublisher] 创建 AIVehicleHealthReport DataWriter 失败");
            return false;
        }
        
        System.out.println("[AIHealthReportPublisher] AI健康报告发布器启动成功");
        return true;
    }
    
    /**
     * 分析车辆健康数据并发布AI报告（同步版本）
     */
    public boolean analyzeAndPublishHealthReport(VehicleHealthReport healthReport) {
        if (writer == null) {
            System.err.println("[AIHealthReportPublisher] DataWriter 尚未初始化");
            return false;
        }
        
        if (healthReport == null) {
            System.err.println("[AIHealthReportPublisher] 车辆健康报告为空");
            return false;
        }
        
        try {
            System.out.println("[AIHealthReportPublisher] 开始调用大模型API分析车辆健康数据...");
            
            // 调用AI分析服务
            String aiAnalysis;
            if (aiAnalysisService.isAPIConfigured()) {
                System.out.println("[AIHealthReportPublisher] 使用大模型API进行分析...");
                aiAnalysis = aiAnalysisService.analyzeVehicleHealth(healthReport);
            } else {
                System.out.println("[AIHealthReportPublisher] API未配置，使用模拟分析...");
                aiAnalysis = simulateAIAnalysis(formatHealthDataForAI(healthReport));
            }
            
            // 创建AI健康报告并发布
            AIVehicleHealthReport aiReport = createAIHealthReport(healthReport, aiAnalysis);
            return publishAIReport(aiReport);
            
        } catch (Exception e) {
            System.err.println("[AIHealthReportPublisher] 分析和发布过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 异步分析车辆健康数据并发布AI报告
     */
    public CompletableFuture<Boolean> analyzeAndPublishHealthReportAsync(VehicleHealthReport healthReport) {
        if (writer == null) {
            System.err.println("[AIHealthReportPublisher] DataWriter 尚未初始化");
            return CompletableFuture.completedFuture(false);
        }
        
        if (healthReport == null) {
            System.err.println("[AIHealthReportPublisher] 车辆健康报告为空");
            return CompletableFuture.completedFuture(false);
        }
        
        System.out.println("[AIHealthReportPublisher] 开始异步调用大模型API分析车辆健康数据...");
        
        // 异步调用AI分析服务
        return aiAnalysisService.analyzeVehicleHealthAsync(healthReport)
            .thenApply(aiAnalysis -> {
                try {
                    System.out.println("[AIHealthReportPublisher] AI分析完成，内容长度: " + aiAnalysis.length() + " 字符");
                    System.out.println("[AIHealthReportPublisher] AI分析结果: " + aiAnalysis);
                    
                    // 创建AI健康报告并发布
                    AIVehicleHealthReport aiReport = createAIHealthReport(healthReport, aiAnalysis);
                    boolean success = publishAIReport(aiReport);
                    
                    if (success) {
                        System.out.println("[AIHealthReportPublisher] 异步AI健康分析报告发布成功");
                    } else {
                        System.err.println("[AIHealthReportPublisher] 异步AI健康分析报告发布失败");
                    }
                    
                    return success;
                } catch (Exception e) {
                    System.err.println("[AIHealthReportPublisher] 异步发布AI报告时发生错误: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            })
            .exceptionally(throwable -> {
                System.err.println("[AIHealthReportPublisher] 异步AI分析失败: " + throwable.getMessage());
                throwable.printStackTrace();
                return false;
            });
    }
    
    /**
     * 格式化车辆健康数据为AI分析输入
     */
    private String formatHealthDataForAI(VehicleHealthReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("请简要分析车辆健康数据：\n");
        sb.append("车辆ID: ").append(report.vehicleId).append("\n");
        
        // 处理组件状态 - 只显示异常组件
        if (report.componentTypes != null && report.componentStatuses != null && report.metrics != null) {
            int length = Math.min(report.componentTypes.length(), 
                        Math.min(report.componentStatuses.length(), report.metrics.length()));
            
            boolean hasIssues = false;
            for (int i = 0; i < length; i++) {
                String type = report.componentTypes.get_at(i);
                String status = report.componentStatuses.get_at(i);
                float metric = report.metrics.get_at(i);
                
                // 只显示有问题的组件
                if (!"normal".equals(status)) {
                    if (!hasIssues) {
                        sb.append("异常组件:\n");
                        hasIssues = true;
                    }
                    sb.append("- ").append(type).append(": ").append(status)
                      .append("(").append(String.format("%.1f", metric)).append(")\n");
                }
            }
            
            if (!hasIssues) {
                sb.append("所有组件状态正常\n");
            }
        }
        
        sb.append("\n请用100字内简要回复：整体评估、风险提示、维护建议。");
        
        return sb.toString();
    }
    
    /**
     * 模拟AI分析（替代API调用）
     */
    private String simulateAIAnalysis(String healthData) {
        try {
            System.out.println("[AIHealthReportPublisher] 正在进行AI分析...");
            
            // 模拟分析延时
            Thread.sleep(1000);
            
            // 基于健康数据生成模拟分析结果
            StringBuilder analysis = new StringBuilder();
            analysis.append("车辆健康状况分析报告：\n");
            
            if (healthData.contains("error")) {
                analysis.append("⚠️ 发现异常状态，建议立即检修。\n");
                analysis.append("建议：请尽快联系维修服务中心进行详细检查。");
            } else if (healthData.contains("warning")) {
                analysis.append("⚡ 部分部件需要关注，建议定期保养。\n");
                analysis.append("建议：建议在下次保养时重点检查相关部件。");
            } else {
                analysis.append("✅ 车辆整体状况良好，各项指标正常。\n");
                analysis.append("建议：继续保持良好的驾驶习惯和定期保养。");
            }
            
            String result = analysis.toString();
            System.out.println("[AIHealthReportPublisher] AI分析完成");
            return result;
            
        } catch (Exception e) {
            System.err.println("[AIHealthReportPublisher] 模拟AI分析时发生错误: " + e.getMessage());
            return "分析过程中出现错误，请稍后重试。";
        }
    }
    

    
    /**
     * 创建AI健康报告
     */
    private AIVehicleHealthReport createAIHealthReport(VehicleHealthReport originalReport, String aiAnalysis) {
        AIVehicleHealthReport aiReport = new AIVehicleHealthReport();
        aiReport.vehicleId = originalReport.vehicleId;
        aiReport.reportContent = aiAnalysis;
        aiReport.reportId = "AI_" + System.currentTimeMillis();
        aiReport.generationModel = aiAnalysisService.isAPIConfigured() ? "讯飞星火大模型" : "模拟AI分析";
        aiReport.timeStamp = dateFormat.format(new Date());
        
        return aiReport;
    }
    
    /**
     * 发布AI健康报告
     */
    private boolean publishAIReport(AIVehicleHealthReport report) {
        try {
            ReturnCode_t rtn = writer.write(report, InstanceHandle_t.HANDLE_NIL_NATIVE);
            
            if (rtn == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[AIHealthReportPublisher] ✅ AI健康报告发布成功: vehicleId=%s, reportId=%s%n", 
                        report.vehicleId, report.reportId);
                System.out.printf("[AIHealthReportPublisher] 报告内容: %s%n", report.reportContent);
                return true;
            } else {
                System.err.printf("[AIHealthReportPublisher] ❌ AI健康报告发布失败, 返回码: %s%n", rtn);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("[AIHealthReportPublisher] 发布AI报告时发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    

}