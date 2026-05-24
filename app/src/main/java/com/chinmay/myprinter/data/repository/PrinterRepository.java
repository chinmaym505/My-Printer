package com.chinmay.myprinter.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.chinmay.myprinter.data.model.GCodeFile;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.source.printer.CommandCallback;
import com.chinmay.myprinter.data.source.printer.ConnectionCallback;
import com.chinmay.myprinter.data.source.printer.PrinterClient;
import com.chinmay.myprinter.data.source.printer.PrinterStatusListener;
import com.chinmay.myprinter.data.source.printer.moonraker.MoonrakerClient;

import java.util.ArrayList;
import java.util.List;

public class PrinterRepository {
    private final PrinterClient printerClient;
    private final MutableLiveData<PrinterStatus> statusLiveData;
    private final MutableLiveData<List<GCodeFile>> filesLiveData;
    private final MutableLiveData<String> errorLiveData;
    private final MutableLiveData<Boolean> connectionLiveData;
    private final MutableLiveData<List<MoonrakerClient.TemperatureHistoryPoint>> temperatureHistoryLiveData;

    private final CombinedListener combinedListener = new CombinedListener();

    public PrinterRepository(PrinterClient printerClient) {
        this.printerClient = printerClient;
        this.statusLiveData = new MutableLiveData<>();
        this.filesLiveData = new MutableLiveData<>(new ArrayList<>());
        this.errorLiveData = new MutableLiveData<>();
        this.connectionLiveData = new MutableLiveData<>(false);
        this.temperatureHistoryLiveData = new MutableLiveData<>();

        // Add combined listener
        printerClient.addStatusListener(combinedListener);
    }

    // Combined listener that implements both interfaces
    private class CombinedListener implements PrinterStatusListener, MoonrakerClient.TemperatureHistoryListener {
        @Override
        public void onStatusUpdate(PrinterStatus status) {
            statusLiveData.postValue(status);
        }

        @Override
        public void onTemperatureHistory(List<MoonrakerClient.TemperatureHistoryPoint> history) {
            temperatureHistoryLiveData.postValue(history);
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

    public void cleanup() {
        printerClient.removeStatusListener(combinedListener);
    }
}
