package com.avnan.blecontrollerapp;

import java.util.HashMap;

public class GattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    // Services
    public static String HRV_CONTROL_SERV_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    // Characteristics
    public static String HRV_CONTROL_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    public static String CONTROL_ACKNOWLEDGE_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9";
    public static String TIMER_VAL_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26aa";
    // Descriptors

    static {
        // Services
        attributes.put(HRV_CONTROL_SERV_UUID, "HRV Control Service");
        // Characteristics
        attributes.put(HRV_CONTROL_CHAR_UUID, "HRV Control Characteristic");
        attributes.put(CONTROL_ACKNOWLEDGE_UUID, "Control Input Acknowledgement");
        attributes.put(TIMER_VAL_UUID, "Countdown Timer Value");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }

}
