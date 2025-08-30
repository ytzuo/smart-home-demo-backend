package com.example.alert.idl;


public class Command{
    public String deviceId = "";// @ID(0)
    public String deviceType = "";// @ID(1)
    public String action = "";// @ID(2)
    public float value = 0;// @ID(3)
    public String timeStamp = "";// @ID(4)

    public Command(){

    }

    public Command(Command other){
        this();
        copy(other);
    }

    public Object copy(Object src) {
        Command typedSrc = (Command)src;
        this.deviceId =  typedSrc.deviceId;
        this.deviceType =  typedSrc.deviceType;
        this.action =  typedSrc.action;
        this.value =  typedSrc.value;
        this.timeStamp =  typedSrc.timeStamp;
        return this;
    }
}