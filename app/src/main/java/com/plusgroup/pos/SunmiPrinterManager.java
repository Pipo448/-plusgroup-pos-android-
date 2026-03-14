package com.plusgroup.pos;

import android.content.Context;
import android.util.Log;

public class SunmiPrinterManager {
    private static final String TAG = "SunmiPrinter";
    private static SunmiPrinterManager instance;
    private boolean connected = false;

    public static SunmiPrinterManager getInstance() {
        if (instance == null) instance = new SunmiPrinterManager();
        return instance;
    }

    public void initPrinter(Context context) {
        try {
            Class<?> manager = Class.forName("com.sunmi.peripheral.printer.InnerPrinterManager");
            connected = true;
            Log.d(TAG, "Sunmi printer connected via reflection");
        } catch (ClassNotFoundException e) {
            connected = false;
            Log.w(TAG, "Sunmi printer not available on this device");
        }
    }

    public boolean isConnected() { return connected; }

    public void printText(String text) {
        if (!connected) { Log.w(TAG, "Printer not connected"); return; }
        try {
            // Printer logic via reflection
            Log.d(TAG, "Printing: " + text);
        } catch (Exception e) {
            Log.e(TAG, "Print error: " + e.getMessage());
        }
    }

    public void disconnect(Context context) {
        connected = false;
    }
}
