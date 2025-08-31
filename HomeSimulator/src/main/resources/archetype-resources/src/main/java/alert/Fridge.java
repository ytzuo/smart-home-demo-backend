package alert;

import alert.AlertDevice.Device;
import com.example.alert.AlertStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Fridge implements Device {
    private String deviceId;
    private String deviceType="冰箱";
    private int temperature;
    private int alertCount=0;
    Random random=new Random();
    // 三个级别报警描述集合
    private List<String> infoAlerts = new ArrayList<>();
    private List<String> warnAlerts = new ArrayList<>();
    private List<String> alertAlerts = new ArrayList<>();

    public Fridge(String deviceId) {
        this.deviceId = deviceId;
        // 一般信息（INFO）
        infoAlerts.add("冰箱内部温度出现轻微波动，可能因频繁开关门，暂不影响使用");
        infoAlerts.add("冰箱进入节能模式，功耗略降，属正常调整");
        infoAlerts.add("压缩机单次运行时间比平时稍长，可能因环境温度升高，无其他异常");
        // 警告（WARN）
        warnAlerts.add("温度传感器读数持续偏高，可能接触不良或积尘，建议清洁或更换");
        warnAlerts.add("检测到瞬时电流不稳，请确认插座与电源线连接是否牢固");
        // 严重警报（ALERT）
        alertAlerts.add("冷藏室温度已超 8 ℃ 持续 30 分钟，请立即检查门封、散热空间及食材摆放");
        alertAlerts.add("压缩机 1 小时内频繁启停 8 次以上，疑似制冷系统故障，请立即断电并联系售后");
        alertAlerts.add("检测到冷冻室温度回升至 0 ℃ 以上，疑似制冷剂泄漏或压缩机故障，请立即处理");
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
