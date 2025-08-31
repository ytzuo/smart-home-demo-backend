package CarSimulator;

import CarSimulator.DDS.VehicleCommandReceiver;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class CarSimulator {
    private static final int DOMAIN_ID = 80;
    private static boolean hasLoad = false;
    
    private VehicleStatus vehicleStatus;
    private VehicleCommandReceiver commandReceiver;

    private AtomicBoolean running;
    private Thread statusUpdateThread;
    private Thread statusPublicationThread;

    public CarSimulator() {
        loadLibrary();
        vehicleStatus = new VehicleStatus();
        vehicleStatus.engineOn = false;
        vehicleStatus.doorsLocked = true;
        vehicleStatus.fuelPercent = 100.0f;
        vehicleStatus.location = "HomeBase";
        vehicleStatus.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
        running = new AtomicBoolean(true);
    }

    public void initDDS() {
        commandReceiver = new VehicleCommandReceiver(DOMAIN_ID, this);
        commandReceiver.init();
        System.out.println("DDS初始化成功");
    }

    public void start() {
        initDDS();
        startStatusUpdateThread();
        startUserInputThread();
    }

    private void startStatusUpdateThread() {
        statusUpdateThread = new Thread(() -> {
            System.out.println("状态更新线程已启动");
            try {
                while (running.get()) {
                    if (vehicleStatus.engineOn) {
                        vehicleStatus.fuelPercent = Math.max(0, vehicleStatus.fuelPercent - 0.1f);
                        if (vehicleStatus.fuelPercent <= 0) {
                            vehicleStatus.engineOn = false;
                            System.out.println("油量耗尽，发动机自动关闭");
                        }
                    }
                    vehicleStatus.timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                System.out.println("状态更新线程被中断");
            }
        });
        statusUpdateThread.start();
    }



    private void startUserInputThread() {
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.println("输入指令控制车辆状态：\n" +
                    "1: 启动发动机\n" +
                    "2: 熄火发动机\n" +
                    "3: 锁车门\n" +
                    "4: 解锁车门\n" +
                    "5: 设置油量\n" +
                    "6: 设置位置\n" +
                    "0: 退出");

            while (running.get()) {
                System.out.print("请输入指令编号> ");
                String cmd = scanner.nextLine();

                switch (cmd) {
                    case "1":
                        vehicleStatus.engineOn = true;
                        System.out.println("发动机已启动");
                        break;
                    case "2":
                        vehicleStatus.engineOn = false;
                        System.out.println("发动机已熄火");
                        break;
                    case "3":
                        vehicleStatus.doorsLocked = true;
                        System.out.println("车门已上锁");
                        break;
                    case "4":
                        vehicleStatus.doorsLocked = false;
                        System.out.println("车门已解锁");
                        break;
                    case "5":
                        System.out.print("请输入油量(0-100)> ");
                        try {
                            float fuel = Float.parseFloat(scanner.nextLine());
                            vehicleStatus.fuelPercent = Math.max(0, Math.min(100, fuel));
                            System.out.println("油量已设置为: " + vehicleStatus.fuelPercent);
                        } catch (NumberFormatException e) {
                            System.out.println("输入无效，请输入0-100之间的数字");
                        }
                        break;
                    case "6":
                        System.out.print("请输入位置> ");
                        vehicleStatus.location = scanner.nextLine();
                        System.out.println("位置已设置为: " + vehicleStatus.location);
                        break;

                    case "0":
                        System.out.println("正在退出程序...");
                        shutdown();
                        break;
                    default:
                        System.out.println("无效指令，请重新输入");
                }
            }
        });
        inputThread.start();
    }



    public VehicleStatus getVehicleStatus() {
        return vehicleStatus;
    }

    public void shutdown() {
        running.set(false);
        try {
            if (statusUpdateThread != null) {
                statusUpdateThread.interrupt();
                statusUpdateThread.join(1000);
            }
        } catch (InterruptedException e) {
            System.err.println("等待线程结束时发生中断: " + e.getMessage());
        }

        if (commandReceiver != null) {
            commandReceiver.close();
        }

        System.out.println("车辆模拟器已关闭");
        System.exit(0);
    }

    private static void loadLibrary() {
        if (!hasLoad) {
            System.loadLibrary("ZRDDS_JAVA");
            hasLoad = true;
        }
    }

    public static void main(String[] args) {
        System.out.println("启动车辆模拟器...");
        CarSimulator simulator = new CarSimulator();
        simulator.start();
    }
}
