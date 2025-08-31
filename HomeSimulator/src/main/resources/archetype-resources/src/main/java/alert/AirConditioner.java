package alert;

import alert.AlertDevice.Device;
import com.example.alert.AlertStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class AirConditioner implements Device {
    private String deviceId;
    private String deviceType="空调";
    private int temperature;
    private int alertCount=0;
    private List<String> infoAlerts = new ArrayList<>();
    private List<String> warnAlerts = new ArrayList<>();
    private List<String> alertAlerts = new ArrayList<>();
    Random random=new Random();

    public AirConditioner(String deviceId) {
        this.deviceId = deviceId;
        // INFO级别（一般信息）
        infoAlerts.add("空调设定温度轻微浮动");
        infoAlerts.add("空调运行功率降低");
        infoAlerts.add("空调运行时间增加");
        // WARN级别（警告）
        warnAlerts.add("空调温度异常，建议检查传感器");
        warnAlerts.add("空调运行异常，建议检查线路");
        // ALERT级别（严重警报）
        alertAlerts.add("空调温度异常，建议立即检查");
        alertAlerts.add("空调运行异常，建议立即检查");
        alertAlerts.add("空调故障，建议立即检查");
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
