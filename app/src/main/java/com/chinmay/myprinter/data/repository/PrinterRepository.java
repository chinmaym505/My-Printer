package com.chinmay.myprinter.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.chinmay.myprinter.data.model.GCodeFile;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.source.printer.CommandCallback;
import com.chinmay.myprinter.data.source.printer.ConnectionCallback;
import com.chinmay.myprinter.data.source.printer.PrinterClient;
import com.chinmay.myprinter.data.source.printer.PrinterStatusListener;
import com.chinmay.myprinter.data.source.printer.moonraker.MoonrakerClient;
import com.chinmay.myprinter.util.PrintActionReceiver;
import com.chinmay.myprinter.util.PrintProgressNotificationManager;

import java.util.ArrayList;
import java.util.List;

public class PrinterRepository {
    private final PrinterClient printerClient;
    private final MutableLiveData<PrinterStatus> statusLiveData;
    private final MutableLiveData<List<GCodeFile>> filesLiveData;
    private final MutableLiveData<String> errorLiveData;
    private final MutableLiveData<Boolean> connectionLiveData;
    private final MutableLiveData<List<MoonrakerClient.TemperatureHistoryPoint>> temperatureHistoryLiveData;
    private final MutableLiveData<String> thumbnailUrlLiveData;
    private final PrintProgressNotificationManager notificationManager;

    private final CombinedListener combinedListener = new CombinedListener();

    public PrinterRepository(Context context, PrinterClient printerClient) {
        this.printerClient = printerClient;
        this.statusLiveData = new MutableLiveData<>();
        this.filesLiveData = new MutableLiveData<>(new ArrayList<>());
        this.errorLiveData = new MutableLiveData<>();
        this.connectionLiveData = new MutableLiveData<>(false);
        this.temperatureHistoryLiveData = new MutableLiveData<>();
        this.thumbnailUrlLiveData = new MutableLiveData<>();
        this.notificationManager = new PrintProgressNotificationManager(context);

        // Wire the active client into the notification action receiver so that the
        // Pause / Resume / Cancel buttons in the notification work even when backgrounded.
        PrintActionReceiver.setClient(printerClient);

        printerClient.addStatusListener(combinedListener);
    }

    private class CombinedListener implements PrinterStatusListener,
            MoonrakerClient.TemperatureHistoryListener, MoonrakerClient.ThumbnailListener {
        @Override
        public void onStatusUpdate(PrinterStatus status) {
            statusLiveData.postValue(status);
            notificationManager.onStatusUpdate(status);
        }

        @Override
        public void onTemperatureHistory(List<MoonrakerClient.TemperatureHistoryPoint> history) {
            temperatureHistoryLiveData.postValue(history);
        }

        @Override
        public void onThumbnailUrl(String url) {
            thumbnailUrlLiveData.postValue(url);
        }
    }

    public LiveData<PrinterStatus> getPrinterStatus() {
        return statusLiveData;
    }

    public LiveData<List<GCodeFile>> getFiles() {
        return filesLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getConnectionStatus() {
        return connectionLiveData;
    }

    public LiveData<List<MoonrakerClient.TemperatureHistoryPoint>> getTemperatureHistory() {
        return temperatureHistoryLiveData;
    }

    public LiveData<String> getThumbnailUrl() {
        return thumbnailUrlLiveData;
    }

    public void connect(String url, String apiKey) {
        printerClient.connect(url, apiKey, new ConnectionCallback() {
            @Override
            public void onConnected() {
                connectionLiveData.postValue(true);
                refreshFiles();
            }

            @Override
            public void onDisconnected() {
                connectionLiveData.postValue(false);
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
                connectionLiveData.postValue(false);
            }
        });
    }

    public void disconnect() {
        printerClient.disconnect();
        connectionLiveData.postValue(false);
    }

    public boolean isConnected() {
        return printerClient.isConnected();
    }

    public void setTemperature(String heater, int target) {
        printerClient.setTemperature(heater, target, new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void homeAxis(String axis) {
        printerClient.homeAxis(axis, new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void moveAxis(String axis, float distance) {
        printerClient.moveAxis(axis, distance, new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void startPrint(String filename) {
        printerClient.startPrint(filename, new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void pausePrint() {
        printerClient.pausePrint(new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void resumePrint() {
        printerClient.resumePrint(new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void cancelPrint() {
        printerClient.cancelPrint(new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void sendGCode(String gcode) {
        printerClient.sendGCode(gcode, new CommandCallback() {
            @Override
            public void onSuccess() {
                // Success
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue(error);
            }
        });
    }

    public void refreshFiles() {
        new Thread(() -> {
            List<GCodeFile> files = printerClient.listFiles();
            filesLiveData.postValue(files);
        }).start();
    }

    public void deleteFile(String filename) {
        printerClient.deleteFile(filename, new CommandCallback() {
            @Override
            public void onSuccess() {
                refreshFiles();
            }

            @Override
            public void onError(String error) {
                errorLiveData.postValue("Delete failed: " + error);
            }
        });
    }

    public void cleanup() {
        printerClient.removeStatusListener(combinedListener);
        PrintActionReceiver.setClient(null);
        notificationManager.dismiss();
    }
}
