package com.chinmay.myprinter.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.chinmay.myprinter.data.source.printer.CommandCallback;
import com.chinmay.myprinter.data.source.printer.PrinterClient;

/**
 * Receives broadcast intents from the print-progress notification's Pause / Resume / Cancel
 * action buttons and forwards them to the active PrinterClient.
 *
 * The client reference is set by PrinterRepository when the repository is created and
 * cleared when it is cleaned up, so this receiver is always either connected or a no-op.
 */
public class PrintActionReceiver extends BroadcastReceiver {

    private static final String TAG = "PrintActionReceiver";
    private static volatile PrinterClient activeClient;

    /** Called by PrinterRepository to wire up the active client. */
    public static void setClient(PrinterClient client) {
        activeClient = client;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PrinterClient client = activeClient;
        if (client == null || !client.isConnected()) return;

        String action = intent.getAction();
        if (action == null) return;

        CommandCallback noop = new CommandCallback() {
            @Override public void onSuccess() {}
            @Override public void onError(String e) { Log.e(TAG, "Action failed: " + e); }
        };

        switch (action) {
            case PrintProgressNotificationManager.ACTION_PAUSE:
                client.pausePrint(noop);
                break;
            case PrintProgressNotificationManager.ACTION_RESUME:
                client.resumePrint(noop);
                break;
            case PrintProgressNotificationManager.ACTION_CANCEL:
                client.cancelPrint(noop);
                break;
            case PrintProgressNotificationManager.ACTION_HEAT_OFF:
                client.setTemperature("nozzle", 0, noop);
                client.setTemperature("bed",    0, noop);
                break;
        }
    }
}
