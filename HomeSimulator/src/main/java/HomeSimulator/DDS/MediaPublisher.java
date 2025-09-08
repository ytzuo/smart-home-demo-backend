package HomeSimulator.DDS;

import IDL.AlertMedia;
import IDL.AlertMediaDataWriter;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

/**
 * HomeSimulator媒体发布器
 * 用于向MobileAppSimulator发送图片和视频数据
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
            System.err.println("[MediaPublisher] 创建 Media DataWriter 失败");
            return false;
        }
        System.out.println("[MediaPublisher] 媒体发布器启动成功");
        return true;
    }

    /**
     * 发送图片或视频文件
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param mediaType 媒体类型(1=图片, 2=视频)
     * @param fileData 文件二进制数据
     * @return 发送是否成功
     */
    public boolean publishMedia(String deviceId, String deviceType, int mediaType, byte[] fileData) {
        if (writer == null) {
            System.err.println("[MediaPublisher] Media DataWriter 尚未初始化");
            return false;
        }

        if (fileData == null || fileData.length == 0) {
            System.err.println("[MediaPublisher] 媒体数据为空");
            return false;
        }

        int totalSize = fileData.length;
        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);
        int alertId = (int) (System.currentTimeMillis() % 1000000); // 生成唯一报警ID

        System.out.printf("[MediaPublisher] 开始发送媒体: deviceId=%s, deviceType=%s, type=%d, size=%d bytes, chunks=%d\n",
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
            media.chunk = new IDL.Blob();
            for (byte b : chunkData) {
                media.chunk.append(b);
            }

            // 发送数据
            ReturnCode_t rtn = writer.write(media, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (rtn == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[MediaPublisher] 已发送块: #%d/%d, size=%d bytes\n",
                        chunkSeq + 1, totalChunks, currentChunkSize);
            } else {
                System.err.printf("[MediaPublisher] 发送块 #%d 失败, 返回码: %s\n", chunkSeq, rtn);
                return false;
            }
        }

        System.out.println("[MediaPublisher] 媒体发送完成");
        return true;
    }
}

