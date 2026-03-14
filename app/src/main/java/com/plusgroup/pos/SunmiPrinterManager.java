// app/src/main/java/com/plusgroup/pos/SunmiPrinterManager.java
package com.plusgroup.pos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

public class SunmiPrinterManager {

    private static final String TAG = "SunmiPrinter";

    private Context            context;
    private SunmiPrinterService printerService;
    private boolean            connected = false;

    public SunmiPrinterManager(Context context) {
        this.context = context;
    }

    // ── Konekte ak inner printer via Sunmi SDK
    public void connect() {
        InnerPrinterManager.getInstance().bindService(context,
            new InnerPrinterCallback() {
                @Override
                protected void onConnected(SunmiPrinterService service) {
                    printerService = service;
                    connected      = true;
                    Log.d(TAG, "Sunmi inner printer konekte ✅");
                    try {
                        // Inisyalize printer a
                        printerService.printerInit(null);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Init erè: " + e.getMessage());
                    }
                }

                @Override
                protected void onDisconnected() {
                    printerService = null;
                    connected      = false;
                    Log.d(TAG, "Sunmi inner printer dekonekte");
                }
            }
        );
    }

    // ── Voye bytes ESC/POS dirèkteman bay printer a
    public void printRaw(byte[] data) {
        if (!connected || printerService == null) {
            Log.e(TAG, "Printer pa konekte — pa ka enprime");
            return;
        }
        try {
            printerService.sendRAWData(data, null);
            Log.d(TAG, "Bytes voye: " + data.length);
        } catch (RemoteException e) {
            Log.e(TAG, "printRaw erè: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected && printerService != null;
    }

    public void disconnect() {
        InnerPrinterManager.getInstance().unBindService(context);
        connected = false;
    }
}
