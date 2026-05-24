package com.chinmay.myprinter.data.model;

public class Temperature {
    private float current;
    private float target;

    public Temperature() {
        this.current = 0;
        this.target = 0;
    }

    public Temperature(float current, float target) {
        this.current = current;
        this.target = target;
    }

    public float getCurrent() {
        return current;
    }

    public void setCurrent(float current) {
        this.current = current;
    }

    public float getTarget() {
        return target;
    }

    public void setTarget(float target) {
        this.target = target;
    }
}
