package CarSimulator;

import IDL.VehicleHealthReport;
import com.zrdds.infrastructure.FloatSeq;
import com.zrdds.infrastructure.StringSeq;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class VehicleHealthManager {

    private final String vehicleId;
    private final Random random = new Random();

    public VehicleHealthManager(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public VehicleHealthReport generateReport() {
        VehicleHealthReport report = new VehicleHealthReport();
        report.vehicleId = this.vehicleId;
        report.timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        String[] componentTypesArray = {"engine", "tire", "brake", "battery", "oil"};
        String[] componentStatusesArray = new String[componentTypesArray.length];
        float[] metricsArray = new float[componentTypesArray.length];

        for (int i = 0; i < componentTypesArray.length; i++) {
            String type = componentTypesArray[i];
            String status = getComponentStatus();
            componentStatusesArray[i] = status;
            metricsArray[i] = generateMetricForComponent(type, status);
        }

        // 使用 ensure_length 和 set_at 方法填充序列
        report.componentTypes = new StringSeq();
        report.componentTypes.ensure_length(componentTypesArray.length, componentTypesArray.length);
        for (int i = 0; i < componentTypesArray.length; i++) {
            report.componentTypes.set_at(i, componentTypesArray[i]);
        }

        report.componentStatuses = new StringSeq();
        report.componentStatuses.ensure_length(componentStatusesArray.length, componentStatusesArray.length);
        for (int i = 0; i < componentStatusesArray.length; i++) {
            report.componentStatuses.set_at(i, componentStatusesArray[i]);
        }

        report.metrics = new FloatSeq();
        report.metrics.ensure_length(metricsArray.length, metricsArray.length);
        for (int i = 0; i < metricsArray.length; i++) {
            report.metrics.set_at(i, metricsArray[i]);
        }

        report.nextMaintenance = calculateNextMaintenance(report.componentStatuses);

        return report;
    }

    private String getComponentStatus() {
        int status = random.nextInt(100);
        if (status < 75) {
            return "normal";
        } else if (status < 90) {
            return "warning";
        } else {
            return "error";
        }
    }

    private float generateMetricForComponent(String componentType, String status) {
        switch (componentType) {
            case "engine":
                if ("normal".equals(status)) return 85 + random.nextFloat() * 10;
                if ("warning".equals(status)) return 95 + random.nextFloat() * 10;
                return 110 + random.nextFloat() * 10;
            case "tire":
                if ("normal".equals(status)) return 32 + random.nextFloat() * 4;
                if ("warning".equals(status)) return 29 + random.nextFloat() * 4;
                return 28 + random.nextFloat() * 2;
            case "brake":
                if ("normal".equals(status)) return 85 + random.nextFloat() * 15;
                if ("warning".equals(status)) return 70 + random.nextFloat() * 15;
                return 60 + random.nextFloat() * 10;
            case "battery":
                if ("normal".equals(status)) return 90 + random.nextFloat() * 10;
                if ("warning".equals(status)) return 85 + random.nextFloat() * 5;
                return 80 + random.nextFloat() * 5;
            case "oil":
                if ("normal".equals(status)) return 85 + random.nextFloat() * 15;
                if ("warning".equals(status)) return 75 + random.nextFloat() * 10;
                return 70 + random.nextFloat() * 5;
            default:
                return 100.0f;
        }
    }

    private String calculateNextMaintenance(StringSeq statuses) {
        int warningCount = 0;
        int errorCount = 0;

        if (statuses != null && statuses.length() > 0) {
            for (int i = 0; i < statuses.length(); i++) {
                String status = statuses.get_at(i);
                if ("warning".equals(status)) warningCount++;
                if ("error".equals(status)) errorCount++;
            }
        }

        int daysToAdd = 30;
        if (errorCount > 0) {
            daysToAdd = 7;
        } else if (warningCount > 2) {
            daysToAdd = 14;
        } else if (warningCount > 0) {
            daysToAdd = 21;
        }

        Date now = new Date();
        Date maintenanceDate = new Date(now.getTime() + (long) daysToAdd * 24 * 60 * 60 * 1000);
        return new SimpleDateFormat("yyyy-MM-dd").format(maintenanceDate);
    }
}
