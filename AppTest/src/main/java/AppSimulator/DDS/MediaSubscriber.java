package AppSimulator.DDS;

import IDL.AlertMedia;
import IDL.AlertMediaDataReader;
import IDL.AlertMediaSeq;
import com.zrdds.infrastructure.InstanceHandle_t;
import com.zrdds.infrastructure.LivelinessChangedStatus;
import com.zrdds.infrastructure.SampleInfo;
import com.zrdds.infrastructure.SampleInfoSeq;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;

import java.util.HashMap;
import java.util.Map;

/**
 * MobileApp媒体订阅器
 * 用于接收HomeSimulator发送的图片和视频数据
 */
public class MediaSubscriber {
    private Map<Integer, MediaReceiver> mediaReceivers = new HashMap<>();
    private static final String SAVE_PATH = "./received_media/";

    public boolean start(Subscriber sub, Topic topic) {
        System.out.println("[MediaSubscriber] 正在启动媒体订阅器...");
        System.out.println("[MediaSubscriber] Topic名称: " + topic.get_name());

        // 配置DataReader QoS
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.reliability.kind = com.zrdds.infrastructure.ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = com.zrdds.infrastructure.HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 100;

        // 创建DataReader
        DataReader reader = sub.create_datareader(
                topic,
                drQos,
                new MediaListener(),
                com.zrdds.infrastructure.StatusKind.DATA_AVAILABLE_STATUS);

        if (reader == null) {
            System.err.println("[MediaSubscriber] 创建DataReader失败");
            return false;
        }

        // 创建保存目录
        java.io.File dir = new java.io.File(SAVE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        System.out.println("[MediaSubscriber] 媒体订阅器启动成功");
        return true;
    }


    private class MediaListener implements DataReaderListener {
        @Override
        public void on_data_available(DataReader reader) {
            AlertMediaDataReader mediaReader = (AlertMediaDataReader) reader;
            AlertMediaSeq dataSeq = new AlertMediaSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                mediaReader.take(dataSeq, infoSeq,
                        com.zrdds.infrastructure.ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                        com.zrdds.infrastructure.SampleStateKind.ANY_SAMPLE_STATE,
                        com.zrdds.infrastructure.ViewStateKind.ANY_VIEW_STATE,
                        com.zrdds.infrastructure.InstanceStateKind.ANY_INSTANCE_STATE);

                for (int i = 0; i < dataSeq.length(); i++) {
                    AlertMedia media = dataSeq.get_at(i);
                    processMediaChunk(media);
                }
            } catch (Exception e) {
                System.err.println("[MediaSubscriber] 读取媒体数据异常: " + e.getMessage());
            } finally {
                mediaReader.return_loan(dataSeq, infoSeq);
            }
        }

        @Override
        public void on_requested_deadline_missed(DataReader arg0, com.zrdds.infrastructure.RequestedDeadlineMissedStatus arg1) {}
        @Override
        public void on_requested_incompatible_qos(DataReader arg0, com.zrdds.infrastructure.RequestedIncompatibleQosStatus arg1) {}
        @Override
        public void on_sample_lost(DataReader arg0, com.zrdds.infrastructure.SampleLostStatus arg1) {}
        @Override
        public void on_sample_rejected(DataReader arg0, com.zrdds.infrastructure.SampleRejectedStatus arg1) {}

        @Override
        public void on_liveliness_changed(DataReader dataReader, LivelinessChangedStatus livelinessChangedStatus) {}

        @Override
        public void on_subscription_matched(DataReader arg0, com.zrdds.infrastructure.SubscriptionMatchedStatus arg1) {}

        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {}
    }

    private void processMediaChunk(AlertMedia media) {
        int alertId = media.alert_id;

        // 如果是新的媒体流，创建新的接收器
        if (!mediaReceivers.containsKey(alertId)) {
            mediaReceivers.put(alertId, new MediaReceiver(media.total_size));
            System.out.printf("[MediaSubscriber] 开始接收媒体流: alertId=%d, deviceId=%s, type=%d, size=%d bytes\n",
                    alertId, media.deviceId, media.media_type, media.total_size);
        }

        // 添加数据块
        MediaReceiver receiver = mediaReceivers.get(alertId);

        // 添加调试信息
        System.out.println("[调试] 接收到数据块: chunk_seq=" + media.chunk_seq + ", chunk_size=" + media.chunk_size + ", blob长度=" + media.chunk.length());

        // 处理Blob对象的方式 - 先获取byte数组
        byte[] chunkData = new byte[media.chunk.length()];
        for (int i = 0; i < media.chunk.length(); i++) {
            chunkData[i] = media.chunk.get_at(i);
        }

        // 验证chunkData是否包含实际图片数据的特征
        boolean hasImageHeader = false;
        boolean hasNonZeroData = false;
        if (chunkData.length >= 4) {
            // JPEG文件头检测: JPEG文件通常以0xFFD8开头
            if ((chunkData[0] & 0xFF) == 0xFF && (chunkData[1] & 0xFF) == 0xD8) {
                hasImageHeader = true;
            }
        }
        for (byte b : chunkData) {
            if (b != 0) {
                hasNonZeroData = true;
                break;
            }
        }
        System.out.println("[调试] chunkData数组: 长度=" + chunkData.length + ", 包含非零数据=" + hasNonZeroData + ", 可能包含图片头=" + hasImageHeader);

        boolean isComplete = receiver.addChunk(media.chunk_seq, chunkData);

        // 显示进度
        double progress = receiver.getProgress();
        System.out.printf("[MediaSubscriber] 接收进度: alertId=%d, %.1f%% (块 #%d/%d)\n",
                alertId, progress * 100, media.chunk_seq + 1, receiver.getTotalChunks());

        // 如果接收完成，处理完整媒体
        if (isComplete) {
            byte[] completeData = receiver.getData();
            saveMedia(media, completeData);
            mediaReceivers.remove(alertId); // 清理资源
        }
    }

    private void saveMedia(AlertMedia media, byte[] data) {
        try {
            System.out.println("[调试] 开始保存媒体: 大小=" + data.length + ", deviceId=" + media.deviceId);
            String fileExtension = media.media_type == 1 ? ".jpg" : ".mp4";
            String fileName = SAVE_PATH + media.deviceId + "_" + media.alert_id + fileExtension;

            // 确保保存目录存在
            java.io.File saveDir = new java.io.File(SAVE_PATH);
            if (!saveDir.exists()) {
                if (saveDir.mkdirs()) {
                    System.out.println("[调试] 创建保存目录成功: " + SAVE_PATH);
                } else {
                    System.err.println("[调试] 创建保存目录失败: " + SAVE_PATH);
                }
            }

            java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName);
            fos.write(data);
            fos.close();

            System.out.printf("[MediaSubscriber] 媒体保存成功: %s, size=%d bytes\n",
                    fileName, data.length);
        } catch (Exception e) {
            System.err.println("[MediaSubscriber] 保存媒体文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 内部类：管理单个媒体流的数据接收
    private class MediaReceiver {
        private byte[] data;
        private boolean[] receivedChunks;
        private int receivedBytes = 0;
        private static final int CHUNK_SIZE = 4096;

        public MediaReceiver(int totalSize) {
            data = new byte[totalSize];
            receivedChunks = new boolean[(int) Math.ceil((double) totalSize / CHUNK_SIZE)];
            System.out.println("[调试] 创建MediaReceiver: totalSize=" + totalSize + ", totalChunks=" + receivedChunks.length);
        }

        public boolean addChunk(int chunkSeq, byte[] chunkData) {
            if (chunkSeq >= receivedChunks.length) {
                System.err.println("[MediaSubscriber] 块序号超出范围: " + chunkSeq);
                return false;
            }

            if (!receivedChunks[chunkSeq]) {
                int offset = chunkSeq * CHUNK_SIZE;
                int copyLength = Math.min(chunkData.length, data.length - offset);

                // 确保chunkData不为空
                if (copyLength > 0) {
                    System.arraycopy(chunkData, 0, data, offset, copyLength);
                    receivedChunks[chunkSeq] = true;
                    receivedBytes += copyLength;
                    System.out.println("[调试] 接收块 #" + chunkSeq + ", 大小=" + copyLength + ", 累计接收=" + receivedBytes);
                } else {
                    System.out.println("[调试] 跳过空数据块 #" + chunkSeq);
                    receivedChunks[chunkSeq] = true; // 仍然标记为已接收，但不增加receivedBytes
                }
            }

            // 检查是否所有块都已接收
            for (boolean received : receivedChunks) {
                if (!received) {
                    return false;
                }
            }
            System.out.println("[调试] 所有块接收完成，总大小=" + receivedBytes);
            return true;
        }

        public double getProgress() {
            double progress = (double) receivedBytes / (double) data.length;
            System.out.println("[调试] 计算进度: receivedBytes=" + receivedBytes + ", data.length=" + data.length + ", progress=" + progress);
            return progress;
        }

        public int getTotalChunks() {
            return receivedChunks.length;
        }
        // 添加缺少的getData()方法
        public byte[] getData() {
            return data;
        }
    }
}
