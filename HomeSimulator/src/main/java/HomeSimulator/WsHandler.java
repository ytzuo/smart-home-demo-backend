package HomeSimulator;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WsHandler extends TextWebSocketHandler {
    // 线程安全的会话池，存储所有连接的前端会话
    private static final Set<WebSocketSession> SESSIONS = ConcurrentHashMap.newKeySet();

    // 新连接建立时加入会话池
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSIONS.add(session);
    }

    // 连接关闭时从会话池移除
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session);
    }

    // 处理前端发送的消息（前端→后端）
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        // 示例：收到消息后广播给所有连接（可替换为实际业务逻辑）
        broadcast("Backend received: " + payload);
    }

    // 后端主动广播消息给所有前端（后端→前端）
    public static void broadcast(String message) {
        SESSIONS.parallelStream().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException ignored) {}
        });
    }
}
