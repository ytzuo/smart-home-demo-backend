package com.example.alert.AlertDevice;

public interface Device extends Runnable{
    String getDeviceId();
    String getDeviceType();
}
