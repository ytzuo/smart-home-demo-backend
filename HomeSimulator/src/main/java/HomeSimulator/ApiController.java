package HomeSimulator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    // 接收前端 HTTP 请求，并通过 WebSocket 广播消息
    @PostMapping("/send")
    public Result sendMessage(@RequestBody MessageDTO message) {
        WsHandler.broadcast(message.getContent());
        return Result.success("Message broadcasted");
    }

    // 简单的响应DTO（实际项目可使用统一响应类）
    public static class Result {
        private String message;
        private boolean success;

        public static Result success(String message) {
            Result result = new Result();
            result.success = true;
            result.message = message;
            return result;
        }

        // Getters and Setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // 消息DTO（接收前端请求体）
    public static class MessageDTO {
        private String content;

        // Getter and Setter
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}

