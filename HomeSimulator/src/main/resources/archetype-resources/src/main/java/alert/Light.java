package alert;

import alert.AlertDevice.Device;
import com.example.alert.AlertStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
public class Light implements Device {
    private String deviceId;
    private String deviceType="灯";
    private boolean isOn=false;//模拟灯的开闭
    private int alertCount = 0; // 报警计数器
    Random random=new Random();
    // 三个级别报警描述集合
    private List<String> infoAlerts = new ArrayList<>();
    private List<String> warnAlerts = new ArrayList<>();
    private List<String> alertAlerts = new ArrayList<>();

    public Light(String deviceId) {
        this.deviceId = deviceId;
        // INFO级别（一般信息）
        infoAlerts.add("灯亮度强烈波动");
        infoAlerts.add("灯启动延迟>10秒，不符合规格");
        infoAlerts.add("灯功率消耗不在正常范围内");
        // WARN级别（警告）
        warnAlerts.add("灯闪烁频率异常，建议检查线路");
        warnAlerts.add("灯散热温度偏高，需确保通风");
        warnAlerts.add("灯开关触点氧化，可能导致接触不良");
        // ALERT级别（严重警报）
        alertAlerts.add("灯短路风险！请立即断电检查");
        alertAlerts.add("灯持续高温，存在火灾隐患");
        alertAlerts.add("灯驱动模块故障，需紧急更换");
    }
    @Override
    public String getDeviceId() {
        return deviceId;
    }
    @Override
    public String getDeviceType() {
        return deviceType;
    }

    //报警发布
    private void publishAlert(){
        alertCount++;
        //选择一个等级
        String[] levels={"INFO","WARN","ALERT"};
        String level=levels[random.nextInt(levels.length)];
        //根据等级选择报警描述
        String alertDescription;
        if(level.equals("INFO")){
            alertDescription=infoAlerts.get(random.nextInt(infoAlerts.size()));
        }else if(level.equals("WARN")){
            alertDescription=warnAlerts.get(random.nextInt(warnAlerts.size()));
        }else{
            alertDescription=alertAlerts.get(random.nextInt(alertAlerts.size()));
        }
        AlertStart.alertPublisher.publishAlert(deviceId,deviceType,
                alertCount,level,alertDescription);
        AlertStart.isPublishAlert=false;
        AlertStart.targetDevice="";
    }
    @Override
    public void run() {
        while (true){
            try {
                Thread.sleep(1000);
                if(AlertStart.isPublishAlert&& AlertStart.targetDevice.equals(deviceType)){
                    publishAlert();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
