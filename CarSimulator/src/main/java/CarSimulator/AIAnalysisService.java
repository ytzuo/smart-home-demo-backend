package CarSimulator;

import IDL.VehicleHealthReport;
import okhttp3.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;

/**
 * AI分析服务类
 * 负责调用大模型API进行车辆健康数据分析
 */
public class AIAnalysisService {
    
    // 科大讯飞WebSocket API配置 - 使用有效的API凭证
    private static final String WS_HOST_URL = "wss://spark-api.xf-yun.com/v1/x1";
    private static final String DOMAIN = "x1";
    private static final String APP_ID = "be6246a2"; // 有效的APP_ID
    private static final String API_SECRET = "YTBiZmIxYzNjNjgxOWQ0Y2EyZDVhODJi"; // 有效的API_SECRET
    private static final String API_KEY = "58638fbd39bd9fcb651fc2c04119376d"; // 有效的API_KEY
    private static final int TIMEOUT_SECONDS = 30;
    
    // 用于同步WebSocket响应
    private volatile String wsResponse = null;
    private volatile boolean[] wsCompleted = {false};
    private volatile Exception[] wsError = {null};
    private static final Gson gson = new Gson();
    
    /**
     * 异步调用大模型API分析车辆健康数据
     * @param healthReport 车辆健康报告
     * @return CompletableFuture包装的分析结果
     */
    public CompletableFuture<String> analyzeVehicleHealthAsync(VehicleHealthReport healthReport) {
        return CompletableFuture.supplyAsync(() -> {
            // 首先检查API是否正确配置
            if (!isAPIConfigured()) {
                System.out.println("[AIAnalysisService] API未正确配置，使用备用分析");
                return generateFallbackAnalysis(healthReport);
            }
            
            try {
                return analyzeVehicleHealth(healthReport);
            } catch (Exception e) {
                System.err.println("[AIAnalysisService] AI分析异步调用失败: " + e.getMessage());
                return generateFallbackAnalysis(healthReport);
            }
        }).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .exceptionally(throwable -> {
              System.err.println("[AIAnalysisService] AI分析超时或失败，使用备用分析: " + throwable.getMessage());
              return generateFallbackAnalysis(healthReport);
          });
    }
    
    /**
     * 同步调用大模型API分析车辆健康数据
     * @param healthReport 车辆健康报告
     * @return AI分析结果
     */
    public String analyzeVehicleHealth(VehicleHealthReport healthReport) {
        // 首先检查API是否正确配置
        if (!isAPIConfigured()) {
            System.out.println("[AIAnalysisService] API未正确配置，使用备用分析");
            return generateFallbackAnalysis(healthReport);
        }
        
        try {
            System.out.println("[AIAnalysisService] 开始调用大模型API进行车辆健康分析...");
            
            // 格式化健康数据为AI输入
            String prompt = formatHealthDataForAI(healthReport);
            
            // 调用大模型API
            String apiResponse = callAIAPI(prompt);
            
            // 解析API响应
            String analysis = parseAPIResponse(apiResponse);
            
            System.out.println("[AIAnalysisService] AI分析完成");
            return analysis;
            
        } catch (Exception e) {
            System.err.println("[AIAnalysisService] 调用大模型API失败: " + e.getMessage());
            e.printStackTrace();
            // 返回备用分析结果
            return generateFallbackAnalysis(healthReport);
        }
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
    
    // 添加JSON解析类 - 参考demo
    static class JsonParse {
        Header header;
        Payload payload;
    }

    static class Header {
        int code;
        int status;
        String sid;
    }

    static class Payload {
        Choices choices;
    }

    static class Choices {
        List<Text> text;
    }

    static class Text {
        String role;
        String content;
    }

    static class RoleContent {
        String role;
        String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 调用大模型API (使用OkHttp WebSocket) - 参考demo实现
     */
    private String callAIAPI(String prompt) throws Exception {
        System.out.println("[AIAnalysisService] 开始构建鉴权URL...");
        // 构建鉴权URL
        String authUrl = getAuthUrl(WS_HOST_URL, API_KEY, API_SECRET);
        System.out.println("[AIAnalysisService] 鉴权URL构建完成");
        
        // 重置状态
        wsResponse = null;
        wsCompleted[0] = false;
        wsError[0] = null;
        
        OkHttpClient client = new OkHttpClient.Builder().build();
        String url = authUrl.replace("http://", "ws://").replace("https://", "wss://");
        Request request = new Request.Builder().url(url).build();
        
        final StringBuilder result = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);
        
        System.out.println("[AIAnalysisService] 准备建立WebSocket连接...");
        
        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                super.onOpen(webSocket, response);
                System.out.println("[AIAnalysisService] WebSocket连接已建立，开始发送请求...");
                
                try {
                    // 构建请求JSON - 完全参考demo的格式
                    JSONObject requestJson = new JSONObject();
                    
                    JSONObject header = new JSONObject();  // header参数
                    header.put("app_id", APP_ID);
                    header.put("uid", UUID.randomUUID().toString().substring(0, 10));
                    
                    JSONObject parameter = new JSONObject(); // parameter参数
                    JSONObject chat = new JSONObject();
                    chat.put("domain", DOMAIN);
                    chat.put("temperature", 0.5);
                    chat.put("max_tokens", 4096);
                    parameter.put("chat", chat);
                    
                    JSONObject payload = new JSONObject(); // payload参数
                    JSONObject message = new JSONObject();
                    JSONArray text = new JSONArray();
                    
                    RoleContent roleContent = new RoleContent(); // 问题
                    roleContent.role = "user";
                    roleContent.content = prompt;
                    text.add(JSON.toJSON(roleContent));
                    
                    message.put("text", text);
                    payload.put("message", message);
                    
                    requestJson.put("header", header); // 组合
                    requestJson.put("parameter", parameter);
                    requestJson.put("payload", payload);
                    
                    System.out.println("[AIAnalysisService] 发送请求: " + requestJson.toString());
                    webSocket.send(requestJson.toString());
                    System.out.println("[AIAnalysisService] 请求已发送，等待响应...");
                    
                } catch (Exception e) {
                    System.err.println("[AIAnalysisService] 发送请求失败: " + e.getMessage());
                    e.printStackTrace();
                    wsError[0] = e;
                    latch.countDown();
                }
            }
                
            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                super.onMessage(webSocket, text);
                System.out.println("[AIAnalysisService] 收到响应片段");
                
                try {
                    // 使用Gson解析JSON - 完全参考demo
                    JsonParse myJsonParse = gson.fromJson(text, JsonParse.class);
                    
                    if (myJsonParse.payload != null && myJsonParse.payload.choices != null && myJsonParse.payload.choices.text != null) {
                        for (Text textItem : myJsonParse.payload.choices.text) {
                            // 拼接所有content内容
                            if (textItem.content != null && !textItem.content.trim().isEmpty()) {
                                result.append(textItem.content);
                                System.out.print(textItem.content); // 实时输出内容片段
                            }
                        }
                    }
                    
                    if (myJsonParse.header.code != 0) {
                        System.err.println("\n[AIAnalysisService] API返回错误码: " + myJsonParse.header.code);
                        wsError[0] = new IOException("API返回错误码: " + myJsonParse.header.code);
                        webSocket.close(1000, "");
                        latch.countDown();
                        return;
                    }
                    
                    if (myJsonParse.header.status == 2) {
                        // 响应完成，所有内容已拼接完毕
                        System.out.println("\n[AIAnalysisService] 响应完成，总长度: " + result.length() + " 字符");
                        wsCompleted[0] = true;
                        latch.countDown();
                    }
                    
                } catch (Exception e) {
                    System.err.println("[AIAnalysisService] 处理响应失败: " + e.getMessage());
                    e.printStackTrace();
                    wsError[0] = new IOException("处理响应失败", e);
                    latch.countDown();
                }
            }
                
            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                super.onClosed(webSocket, code, reason);
                System.out.println("[AIAnalysisService] WebSocket连接关闭: statusCode=" + code + ", reason=" + reason + ", wsCompleted=" + wsCompleted[0]);
                if (!wsCompleted[0]) {
                    wsError[0] = new IOException("连接意外关闭: statusCode=" + code + ", reason=" + reason);
                }
                latch.countDown();
            }
                
            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                System.err.println("[AIAnalysisService] WebSocket发生错误: " + t.getMessage());
                t.printStackTrace();
                
                try {
                    if (null != response) {
                        int code = response.code();
                        System.out.println("[AIAnalysisService] onFailure code:" + code);
                        if (response.body() != null) {
                            System.out.println("[AIAnalysisService] onFailure body:" + response.body().string());
                        }
                        if (101 != code) {
                            System.err.println("[AIAnalysisService] connection failed");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
                wsError[0] = new IOException("WebSocket错误: " + t.getMessage(), t);
                latch.countDown();
            }
        };
            
        System.out.println("[AIAnalysisService] 开始建立WebSocket连接...");
        WebSocket webSocket = client.newWebSocket(request, listener);
        
        System.out.println("[AIAnalysisService] WebSocket连接建立成功，等待响应结果...");
        // 等待结果
        boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        System.out.println("[AIAnalysisService] 等待结果完成: completed=" + completed + ", wsCompleted=" + wsCompleted[0] + ", hasError=" + (wsError[0] != null));
        
        if (!completed) {
            System.err.println("[AIAnalysisService] 等待响应超时，关闭WebSocket连接");
            webSocket.close(1000, "超时");
            throw new IOException("API调用超时 - 在" + TIMEOUT_SECONDS + "秒内未收到完整响应");
        }
        
        if (wsError[0] != null) {
            System.err.println("[AIAnalysisService] 检测到WebSocket错误: " + wsError[0].getMessage());
            if (wsError[0] instanceof IOException) {
                throw (IOException) wsError[0];
            } else {
                throw new IOException("API调用失败", wsError[0]);
            }
        }
        
        String resultStr = result.toString();
        System.out.println("[AIAnalysisService] 成功获取响应，长度: " + resultStr.length());
        return resultStr;
    }
    
    /**
     * 提取响应内容 - 参考B_WsXModel.java的响应解析
     */

    
    /**
     * 构建鉴权URL
     */
    private String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        // 将wss://转换为https://进行URL解析
        String httpUrl = hostUrl.replace("wss://", "https://");
        URL url = new URL(httpUrl);
        
        // 时间戳
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        
        // 拼接字符串
        String preStr = "host: " + url.getHost() + "\n";
        preStr += "date: " + date + "\n";
        preStr += "GET " + url.getPath() + " HTTP/1.1";
        
        // SHA256加密
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        
        // 拼接authorization
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, "hmac-sha256", "host date request-line", sha);
        
        String authBase = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
        
        // 返回wss://格式的完整URL
        return hostUrl + "?authorization=" + URLEncoder.encode(authBase, "UTF-8") + "&date=" + URLEncoder.encode(date, "UTF-8") + "&host=" + URLEncoder.encode(url.getHost(), "UTF-8");
    }
    

    
    /**
     * 解析API响应
     */
    private String parseAPIResponse(String apiResponse) {
        try {
            // 简单的JSON解析，提取content字段
            // 在实际项目中建议使用专业的JSON库如Jackson或Gson
            int contentStart = apiResponse.indexOf("\"content\":");
            if (contentStart != -1) {
                contentStart = apiResponse.indexOf("\"", contentStart + 10) + 1;
                int contentEnd = apiResponse.indexOf("\"", contentStart);
                if (contentEnd != -1) {
                    return apiResponse.substring(contentStart, contentEnd)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"");
                }
            }
            
            // 如果解析失败，返回原始响应
            return "AI分析结果: " + apiResponse;
            
        } catch (Exception e) {
            System.err.println("[AIAnalysisService] 解析API响应失败: " + e.getMessage());
            return "AI分析结果解析失败，原始响应: " + apiResponse;
        }
    }
    
    /**
     * 生成备用分析结果（当API调用失败时使用）
     */
    private String generateFallbackAnalysis(VehicleHealthReport report) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("🔧 车辆健康状况分析报告（备用分析）\n\n");
        
        // 基于健康数据生成简单分析
        boolean hasError = false;
        boolean hasWarning = false;
        
        if (report.componentStatuses != null) {
            for (int i = 0; i < report.componentStatuses.length(); i++) {
                String status = report.componentStatuses.get_at(i);
                if ("error".equals(status)) {
                    hasError = true;
                } else if ("warning".equals(status)) {
                    hasWarning = true;
                }
            }
        }
        
        if (hasError) {
            analysis.append("WARNING: 整体评估: 发现严重问题，需要立即处理\n");
            analysis.append("RISK: 风险分析: 部分关键部件存在故障，可能影响行车安全\n");
            analysis.append("MAINTENANCE: 维护建议: 请立即联系专业维修服务中心进行检修\n");
            analysis.append("DRIVING: 驾驶建议: 建议暂停使用车辆，避免进一步损坏\n");
        } else if (hasWarning) {
            analysis.append("CAUTION: 整体评估: 车辆状况良好，但需要关注部分部件\n");
            analysis.append("RISK: 风险分析: 存在潜在风险，建议及时保养\n");
            analysis.append("MAINTENANCE: 维护建议: 建议在下次保养时重点检查相关部件\n");
            analysis.append("DRIVING: 驾驶建议: 可正常使用，注意观察车辆状态\n");
        } else {
            analysis.append("OK: 整体评估: 车辆状况优良，各项指标正常\n");
            analysis.append("RISK: 风险分析: 暂无明显风险，继续保持良好状态\n");
            analysis.append("MAINTENANCE: 维护建议: 按计划进行常规保养即可\n");
            analysis.append("DRIVING: 驾驶建议: 继续保持良好的驾驶习惯\n");
        }
        
        analysis.append("\nNOTE: 此为备用分析结果，建议获取专业AI分析报告");
        
        return analysis.toString();
    }
    
    /**
     * 检查API配置是否有效
     */
    public boolean isAPIConfigured() {
        // 检查API密钥是否已配置且不是默认占位符值
        return API_KEY != null && !API_KEY.trim().isEmpty() && 
               !API_KEY.equals("your_api_key_here") && 
               !APP_ID.equals("your_app_id_here") && 
               !API_SECRET.equals("your_api_secret_here");
    }
}