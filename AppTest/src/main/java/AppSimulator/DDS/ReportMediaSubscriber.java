package AppSimulator.DDS;

import IDL.ReportMedia;
import IDL.ReportMediaDataReader;
import IDL.ReportMediaSeq;
import com.zrdds.infrastructure.*;
import com.zrdds.subscription.DataReader;
import com.zrdds.subscription.DataReaderListener;
import com.zrdds.subscription.DataReaderQos;
import com.zrdds.subscription.Subscriber;
import com.zrdds.topic.Topic;
import java.util.HashMap;
import java.util.Map;

/**
 * 报告媒体订阅器 - 专门接收能耗趋势图分片（ReportMedia），不影响原警报媒体（AlertMedia）处理
 */
public class ReportMediaSubscriber {

    // 分片缓存：key=reportId（能耗趋势图唯一标识），value=分片接收器
    private final Map<String, MediaReceiver> mediaReceivers = new HashMap<>();

    // 独立保存目录，避免与警报媒体混淆
    private static final String SAVE_PATH = "./received_media/energy_trends/";

    // 数据回调监听器（可选，供MobileAppSimulator获取图片数据）
    public interface ReportMediaListener {
        void onEnergyTrendReceived(String deviceId, String reportId, byte[] imageData);
    }

    private ReportMediaListener dataListener;

    public void setReportMediaListener(ReportMediaListener listener) {
        this.dataListener = listener;
    }

    /**
     * 启动报告媒体订阅器（独立于原MediaSubscriber）
     */
    public boolean start(Subscriber sub, Topic reportMediaTopic) {
        System.out.println("[ReportMediaSubscriber] 启动能耗趋势图订阅器...");
        System.out.println("[ReportMediaSubscriber] Topic名称: " + reportMediaTopic.get_name());

        // 配置QoS（与后端ReportMedia发布器匹配）
        DataReaderQos drQos = new DataReaderQos();
        sub.get_default_datareader_qos(drQos);
        drQos.reliability.kind = ReliabilityQosPolicyKind.RELIABLE_RELIABILITY_QOS;
        drQos.history.kind = HistoryQosPolicyKind.KEEP_LAST_HISTORY_QOS;
        drQos.history.depth = 100;

        // 创建ReportMedia专用DataReader
        DataReader reader = sub.create_datareader(
                reportMediaTopic,
                drQos,
                new ReportMediaListenerImpl(),
                StatusKind.DATA_AVAILABLE_STATUS);

        if (reader == null) {
            System.err.println("[ReportMediaSubscriber] 创建DataReader失败");
            return false;
        }

        // 创建独立保存目录（避免与原警报媒体冲突）
        java.io.File dir = new java.io.File(SAVE_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        System.out.println("[ReportMediaSubscriber] 能耗趋势图订阅器启动成功");
        return true;
    }

    /**
     * 内部监听器 - 处理ReportMedia分片
     */
    private class ReportMediaListenerImpl implements DataReaderListener{

        @Override
        public void on_requested_deadline_missed(DataReader dataReader, RequestedDeadlineMissedStatus requestedDeadlineMissedStatus) {

        }

        @Override
        public void on_requested_incompatible_qos(DataReader dataReader, RequestedIncompatibleQosStatus requestedIncompatibleQosStatus) {

        }

        @Override
        public void on_sample_rejected(DataReader dataReader, SampleRejectedStatus sampleRejectedStatus) {

        }

        @Override
        public void on_liveliness_changed(DataReader dataReader, LivelinessChangedStatus livelinessChangedStatus) {

        }

        @Override
        public void on_data_available(DataReader dataReader) {
            ReportMediaDataReader mediaReader = (ReportMediaDataReader) dataReader;
            ReportMediaSeq dataSeq = new ReportMediaSeq();
            SampleInfoSeq infoSeq = new SampleInfoSeq();

            try {
                mediaReader.take(dataSeq, infoSeq,
                        ResourceLimitsQosPolicy.LENGTH_UNLIMITED,
                        SampleStateKind.ANY_SAMPLE_STATE,
                        ViewStateKind.ANY_VIEW_STATE,
                        InstanceStateKind.ANY_INSTANCE_STATE);

                for (int i = 0; i < dataSeq.length(); i++) {
                    if (infoSeq.get_at(i).valid_data) {
                        ReportMedia media = dataSeq.get_at(i);
                        processMediaChunk(media); // 处理能耗趋势图分片
                    }
                }
            } catch (Exception e) {
                System.err.println("[ReportMediaSubscriber] 读取分片异常: " + e.getMessage());
            } finally {
                mediaReader.return_loan(dataSeq, infoSeq);
            }
        }

        @Override
        public void on_sample_lost(DataReader dataReader, SampleLostStatus sampleLostStatus) {

        }

        @Override
        public void on_subscription_matched(DataReader dataReader, SubscriptionMatchedStatus subscriptionMatchedStatus) {

        }

        @Override
        public void on_data_arrived(DataReader dataReader, Object o, SampleInfo sampleInfo) {

        }
    }
    /**
     * 处理单个ReportMedia分片（逻辑类似原MediaSubscriber，但适配ReportMedia字段）
     */
    private void processMediaChunk(ReportMedia media) {
        String reportId = media.reportId; // 能耗趋势图唯一ID（替代原alertId）
        String deviceId = media.deviceId;
        int chunkSeq = media.chunk_seq;
        int totalSize = media.total_size;

        // 新媒体流：创建接收器
        if (!mediaReceivers.containsKey(reportId)) {
            mediaReceivers.put(reportId, new MediaReceiver(totalSize));
            System.out.printf("[ReportMediaSubscriber] 开始接收趋势图: reportId=%s, 设备ID=%s, 总大小=%dB\n",
                    reportId, deviceId, totalSize);
        }

        // 提取分片数据（与原Blob处理逻辑一致）
        MediaReceiver receiver = mediaReceivers.get(reportId);
        byte[] chunkData = new byte[media.chunk.length()];
        for (int i = 0; i < media.chunk.length(); i++) {
            chunkData[i] = media.chunk.get_at(i);
        }

        // 添加分片并检查完整性
        boolean isComplete = receiver.addChunk(chunkSeq, chunkData);
        double progress = receiver.getProgress();
        System.out.printf("[ReportMediaSubscriber] 接收进度: reportId=%s, %.1f%% (分片 #%d)\n",
                reportId, progress * 100, chunkSeq + 1);

        // 分片完整：保存图片并通知监听器
        if (isComplete) {
            byte[] imageData = receiver.getData();
            saveEnergyTrendImage(deviceId, reportId, imageData);
            if (dataListener != null) {
                dataListener.onEnergyTrendReceived(deviceId, reportId, imageData); // 回调给MobileApp
            }
            mediaReceivers.remove(reportId); // 清理缓存
        }
    }
    /**
     * 保存能耗趋势图（独立路径，避免覆盖原警报图片）
     */
    private void saveEnergyTrendImage(String deviceId, String reportId, byte[] data) {
        try {
            String fileName = SAVE_PATH + deviceId + "_EnergyTrend_" + reportId + ".jpg";
            java.io.FileOutputStream fos = new java.io.FileOutputStream(fileName);
            fos.write(data);
            fos.close();
            System.out.printf("[ReportMediaSubscriber] 趋势图保存成功: %s (大小=%dB)\n", fileName, data.length);
        } catch (Exception e) {
            System.err.println("[ReportMediaSubscriber] 保存趋势图失败: " + e.getMessage());
        }
    }

    /**
     * 内部类：分片接收器（复用原MediaReceiver逻辑，未修改）
     */
    private class MediaReceiver {
        private byte[] data;
        private boolean[] receivedChunks;
        private int receivedBytes = 0;
        private static final int CHUNK_SIZE = 4096;

        public MediaReceiver(int totalSize) {
            data = new byte[totalSize];
            receivedChunks = new boolean[(int) Math.ceil((double) totalSize / CHUNK_SIZE)];
        }

        public boolean addChunk(int chunkSeq, byte[] chunkData) {
            if (chunkSeq >= receivedChunks.length) return false;
            if (!receivedChunks[chunkSeq]) {
                int offset = chunkSeq * CHUNK_SIZE;
                int copyLength = Math.min(chunkData.length, data.length - offset);
                System.arraycopy(chunkData, 0, data, offset, copyLength);
                receivedChunks[chunkSeq] = true;
                receivedBytes += copyLength;
            }
            // 检查所有分片是否接收完成
            for (boolean received : receivedChunks) {
                if (!received) return false;
            }
            return true;
        }

        public double getProgress() {
            return (double) receivedBytes / data.length;
        }

        public byte[] getData() {
            return data;
        }
    }
}
