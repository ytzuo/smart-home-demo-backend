package CarSimulator.DDS;

import IDL.AlertMedia;
import IDL.AlertMediaDataWriter;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

/**
 * 车辆媒体发布器
 * 用于向MobileAppSimulator发送图片数据
 */
public class MediaPublisher {
    private AlertMediaDataWriter writer;
    private static final int CHUNK_SIZE = 4096; // 每块大小

    public boolean start(Publisher pub, Topic topic) {
        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = com.zrdds.infrastructure.DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 100; // 增加历史深度以支持大数据传输

        // 创建DataWriter
        writer = (AlertMediaDataWriter) pub.create_datawriter(
                topic,
                dwQos,
                null,
                com.zrdds.infrastructure.StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.err.println("[CarSimulator.DDS.MediaPublisher] 创建 Media DataWriter 失败");
            return false;
        }
        System.out.println("[CarSimulator.DDS.MediaPublisher] 媒体发布器启动成功");
        return true;
    }

    /**
     * 发送图片文件
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param mediaType 媒体类型(1=图片)
     * @param fileData 文件二进制数据
     * @return 发送是否成功
     */
    public boolean publishMedia(String deviceId, String deviceType, int mediaType, byte[] fileData, int alertId) {
        if (writer == null) {
            System.err.println("[CarSimulator.DDS.MediaPublisher] Media DataWriter 尚未初始化");
            return false;
        }

        if (fileData == null || fileData.length == 0) {
            System.err.println("[CarSimulator.DDS.MediaPublisher] 媒体数据为空");
            return false;
        }

        int totalSize = fileData.length;
        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);
        // 生成唯一报警ID

        System.out.printf("[CarSimulator.DDS.MediaPublisher] 开始发送媒体: deviceId=%s, deviceType=%s, type=%d, size=%d bytes, chunks=%d\n",
                deviceId, deviceType, mediaType, totalSize, totalChunks);

        // 分块发送
        for (int chunkSeq = 0; chunkSeq < totalChunks; chunkSeq++) {
            AlertMedia media = new AlertMedia();
            media.deviceId = deviceId;
            media.deviceType = deviceType;
            media.alert_id = alertId;
            media.media_type = mediaType;
            media.total_size = totalSize;
            media.chunk_seq = chunkSeq;

            // 计算当前块的大小
            int currentChunkSize = Math.min(CHUNK_SIZE, totalSize - chunkSeq * CHUNK_SIZE);
            media.chunk_size = currentChunkSize;

            // 设置块数据
            byte[] chunkData = new byte[currentChunkSize];
            System.arraycopy(fileData, chunkSeq * CHUNK_SIZE, chunkData, 0, currentChunkSize);

            // 创建和填充Blob对象
            media.chunk = new IDL.Blob();
            try {
                // 使用ensure_length预分配空间，然后用set_at逐个填充字节
                media.chunk.ensure_length(currentChunkSize, currentChunkSize);
                for (int i = 0; i < currentChunkSize; i++) {
                    media.chunk.set_at(i, chunkData[i]);
                }
            } catch (Exception e) {
                System.err.println("[CarSimulator.DDS.MediaPublisher] 创建Blob对象时出错: " + e.getMessage());
                return false;
            }

            // 发送数据
            ReturnCode_t rtn = writer.write(media, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (rtn == ReturnCode_t.RETCODE_OK) {
                // 增强块发送成功的日志，包含alertId
                System.out.printf("[CarSimulator.DDS.MediaPublisher] ✅  已发送块: #%d/%d, size=%d bytes, alertId: %d\n",
                        chunkSeq + 1, totalChunks, currentChunkSize, alertId);
            } else {
                // 增强块发送失败的日志，包含alertId
                System.err.printf("[CarSimulator.DDS.MediaPublisher] ❌  发送块 #%d 失败, 返回码: %s, alertId: %d\n",
                        chunkSeq, rtn, alertId);
                return false;
            }
        }

        System.out.println("[CarSimulator.DDS.MediaPublisher] 媒体发送完成");
        return true;
    }
}
