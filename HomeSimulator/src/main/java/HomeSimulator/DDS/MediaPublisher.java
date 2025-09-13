package HomeSimulator.DDS;

import IDL.AlertMedia;
import IDL.AlertMediaDataWriter;
import IDL.Blob;
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
            System.err.println("[CarSimulator.DDS.MediaPublisher] 创建 Media DataWriter 失败");
            return false;
        }
        System.out.println("[CarSimulator.DDS.MediaPublisher] 媒体发布器启动成功");
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
    public boolean publishMedia(String deviceId, String deviceType, int mediaType, byte[] fileData,int alertId) {
        if (writer == null) {
            System.err.println("[CarSimulator.DDS.MediaPublisher] Media DataWriter 尚未初始化");
            return false;
        }

        if (fileData == null || fileData.length == 0) {
            System.err.println("[CarSimulator.DDS.MediaPublisher] 媒体数据为空");
            return false;
        }

        // 添加调试信息：检查数据是否有效
        boolean hasNonZeroData = false;
        for (int i = 0; i < Math.min(10, fileData.length); i++) {
            if (fileData[i] != 0) {
                hasNonZeroData = true;
                break;
            }
        }
        System.out.println("[CarSimulator.DDS.MediaPublisher] 文件数据预览：前10字节" + (hasNonZeroData ? "包含非零数据" : "全部为0"));

        int totalSize = fileData.length;
        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);
        //alertId = (int) (System.currentTimeMillis() % 1000000); // 生成唯一报警ID

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
            System.out.println(chunkData.length);
            // 检查块数据是否有效
            boolean chunkHasData = false;
            for (int i = 0; i < Math.min(10, chunkData.length); i++) {
                if (chunkData[i] != 0) {
                    chunkHasData = true;
                    break;
                }
            }
            System.out.printf("[CarSimulator.DDS.MediaPublisher] 准备发送块 #%d: 大小=%d, %s\n",
                    chunkSeq, currentChunkSize, chunkHasData ? "包含数据" : "数据为空");


            // 修复：使用正确的方式创建和填充Blob对象
            media.chunk = new IDL.Blob();
            try {
                // 方法1：使用ensure_length预分配空间，然后用set_at逐个填充字节
                media.chunk.ensure_length(currentChunkSize, currentChunkSize);
                for (int i = 0; i < currentChunkSize; i++) {
                    media.chunk.set_at(i, chunkData[i]);
                }

                // 验证Blob对象数据
                System.out.printf("[CarSimulator.DDS.MediaPublisher] Blob对象创建完成(方法1): 长度=%d\n", media.chunk.length());

                // 验证数据是否正确填充
                boolean blobHasData = false;
                for (int i = 0; i < Math.min(10, media.chunk.length()); i++) {
                    try {
                        if (media.chunk.get_at(i) != 0) {
                            blobHasData = true;
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("[CarSimulator.DDS.MediaPublisher] 获取Blob数据时出错: " + e.getMessage());
                        break;
                    }
                }
                System.out.printf("[CarSimulator.DDS.MediaPublisher] Blob对象验证: 长度=%d, 包含数据=%s\n",
                        media.chunk.length(), blobHasData);
            } catch (Exception e) {
                System.err.println("[CarSimulator.DDS.MediaPublisher] 创建Blob对象时出错: " + e.getMessage());
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
                    System.err.println("[CarSimulator.DDS.MediaPublisher] 获取Blob数据时出错: " + e.getMessage());
                    break;
                }
            }
            System.out.printf("[CarSimulator.DDS.MediaPublisher] Blob对象验证: 长度=%d, 包含数据=%s, 可能包含图片头=%s\n",
                    media.chunk.length(), blobHasData, hasImageHeader);
            System.out.println("aaaaas");
            // 发送数据
            ReturnCode_t rtn = writer.write(media, InstanceHandle_t.HANDLE_NIL_NATIVE);
            if (rtn == ReturnCode_t.RETCODE_OK) {
                System.out.printf("[CarSimulator.DDS.MediaPublisher] 已发送块: #%d/%d, size=%d bytes, alertId: %d\n",
                        chunkSeq + 1, totalChunks, currentChunkSize, alertId);
            } else {
                System.err.printf("[CarSimulator.DDS.MediaPublisher] 发送块 #%d 失败, 返回码: %s\n", chunkSeq, rtn);
                return false;
            }
        }

        System.out.printf("[CarSimulator.DDS.MediaPublisher] 媒体发送完成 - deviceId: %s, deviceType: %s, alertId: %d\n",
                deviceId, deviceType, alertId);
        return true;
    }
}

