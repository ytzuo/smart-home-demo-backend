package HomeSimulator.DDS;

import IDL.ReportMedia;
import IDL.ReportMediaDataWriter;
import IDL.Blob;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.ReturnCode_t;
import com.zrdds.publication.DataWriterQos;
import com.zrdds.publication.Publisher;
import com.zrdds.topic.Topic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 报表媒体发布器
 * 用于向MobileAppSimulator发送能耗趋势图等报表数据
 */
public class ReportMediaPublisher {
    private ReportMediaDataWriter writer;
    private static final int CHUNK_SIZE = 4096; // 每块大小

    /**
     * 初始化报表媒体发布器
     * @param pub DDS发布器
     * @param topic DDS主题
     * @return 是否初始化成功
     */
    public boolean start(Publisher pub, Topic topic) {
        // 配置QoS
        DataWriterQos dwQos = new DataWriterQos();
        pub.get_default_datawriter_qos(dwQos);
        dwQos.durability.kind = com.zrdds.infrastructure.DurabilityQosPolicyKind.TRANSIENT_LOCAL_DURABILITY_QOS;
        dwQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        dwQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        dwQos.history.depth = 100; // 增加历史深度以支持大数据传输

        // 创建DataWriter
        writer = (ReportMediaDataWriter) pub.create_datawriter(
                topic,
                dwQos,
                null,
                com.zrdds.infrastructure.StatusKind.STATUS_MASK_NONE);

        if (writer == null) {
            System.err.println("[ReportMediaPublisher] 创建 ReportMedia DataWriter 失败");
            return false;
        }
        System.out.println("[ReportMediaPublisher] 报表媒体发布器启动成功");
        return true;
    }

    /**
     * 发送报表媒体数据（如能耗趋势图）
     * @param reportId 报表ID
     * @param reportType 报表类型
     * @param deviceId 设备ID
     * @param fileData 文件二进制数据
     * @return 发送是否成功
     */
    public boolean publishReportMedia(String reportId, String reportType, String deviceId, byte[] fileData) {
        if (writer == null) {
            System.err.println("[ReportMediaPublisher] ReportMedia DataWriter 尚未初始化");
            return false;
        }

        if (fileData == null || fileData.length == 0) {
            System.err.println("[ReportMediaPublisher] 媒体数据为空");
            return false;
        }

        // 检查数据是否有效
        boolean hasNonZeroData = false;
        for (int i = 0; i < Math.min(10, fileData.length); i++) {
            if (fileData[i] != 0) {
                hasNonZeroData = true;
                break;
            }
        }
        System.out.println("[ReportMediaPublisher] 文件数据预览：前10字节" + (hasNonZeroData ? "包含非零数据" : "全部为0"));

        int totalSize = fileData.length;
        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);

        System.out.printf("[ReportMediaPublisher] 开始发送报表媒体: reportId=%s, reportType=%s, deviceId=%s, size=%d bytes, chunks=%d\n",
                reportId, reportType, deviceId, totalSize, totalChunks);

        // 分块发送
        for (int chunkSeq = 0; chunkSeq < totalChunks; chunkSeq++) {
            ReportMedia media = new ReportMedia();
            media.reportId = reportId;
            media.reportType = reportType;
            media.deviceId = deviceId;
            media.total_size = totalSize;
            media.chunk_seq = chunkSeq;
            media.timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 计算当前块的大小
            int currentChunkSize = Math.min(CHUNK_SIZE, totalSize - chunkSeq * CHUNK_SIZE);
            media.chunk_size = currentChunkSize;

            // 设置块数据
            byte[] chunkData = new byte[currentChunkSize];
            System.arraycopy(fileData, chunkSeq * CHUNK_SIZE, chunkData, 0, currentChunkSize);

            // 检查块数据是否有效
            boolean chunkHasData = false;
            for (int i = 0; i < Math.min(10, chunkData.length); i++) {
                if (chunkData[i] != 0) {
                    chunkHasData = true;
                    break;
                }
            }
            System.out.printf("[ReportMediaPublisher] 准备发送块 #%d: 大小=%d, %s\n",
                    chunkSeq, currentChunkSize, chunkHasData ? "包含数据" : "数据为空");

            // 创建和填充Blob对象
            media.chunk = new IDL.Blob();
            try {
                // 预分配空间，然后用set_at逐个填充字节
                media.chunk.ensure_length(currentChunkSize, currentChunkSize);
                for (int i = 0; i < currentChunkSize; i++) {
                    media.chunk.set_at(i, chunkData[i]);
                }

                // 验证Blob对象数据
                System.out.printf("[ReportMediaPublisher] Blob对象创建完成: 长度=%d\n", media.chunk.length());

            } catch (Exception e) {
                System.err.println("[ReportMediaPublisher] 创建Blob对象时出错: " + e.getMessage());
                return false;
            }

            // 增强的Blob对象验证
            boolean blobHasData = false;
            boolean hasImageHeader = false;
            if (media.chunk.length() >= 4 && chunkSeq == 0) {
                // 检查第一个块是否包含JPEG文件头
                if ((chunkData[0] & 0xFF) == 0xFF && (chunkData[1] & 0xFF) == 0xD8) {
                    hasImageHeader = true;
                }
            }
            for (int i = 0; i < Math.min(10, media.chunk.length()); i++) {
                try {
                    if (media.chunk.get_at(i) != 0) {
                        blobHasData = true;
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[ReportMediaPublisher] 获取Blob数据时出错: " + e.getMessage());
                    break;
                }
            }
            System.out.printf("[ReportMediaPublisher] Blob对象验证: 长度=%d, 包含数据=%s, 可能包含图片头=%s\n",
                    media.chunk.length(), blobHasData, hasImageHeader);

            // 发送数据
            ReturnCode_t rtn = writer.write(media, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (rtn == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[ReportMediaPublisher] 已发送块: #%d/%d, size=%d bytes\n",
                        chunkSeq + 1, totalChunks, currentChunkSize);
            } else {
                System.err.printf("[ReportMediaPublisher] 发送块 #%d 失败, 返回码: %s\n", chunkSeq, rtn);
                return false;
            }
        }

        System.out.printf("[ReportMediaPublisher] 报表媒体发送完成 - reportId: %s, reportType: %s\n",
                reportId, reportType);
        return true;
    }
}
