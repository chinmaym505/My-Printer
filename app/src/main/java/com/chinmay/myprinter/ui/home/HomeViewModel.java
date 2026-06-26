package com.chinmay.myprinter.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.chinmay.myprinter.data.model.GCodeFile;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.repository.PrinterRepository;
import com.chinmay.myprinter.data.source.printer.moonraker.MoonrakerClient;

import java.util.List;

public class HomeViewModel extends ViewModel {
    private final PrinterRepository repository;

    public HomeViewModel(PrinterRepository repository) {
        this.repository = repository;
    }

    public LiveData<PrinterStatus> getPrinterStatus() {
        return repository.getPrinterStatus();
    }

    public LiveData<List<GCodeFile>> getFiles() {
        return repository.getFiles();
    }

    public LiveData<String> getError() {
        return repository.getError();
    }

    public LiveData<Boolean> getConnectionStatus() {
        return repository.getConnectionStatus();
    }

    public LiveData<List<MoonrakerClient.TemperatureHistoryPoint>> getTemperatureHistory() {
        return repository.getTemperatureHistory();
    }

    public LiveData<String> getThumbnailUrl() {
        return repository.getThumbnailUrl();
    }

    public void connect(String url, String apiKey) {
        repository.connect(url, apiKey);
    }

    public void setTemperature(String heater, int target) {
        repository.setTemperature(heater, target);
    }

    public void homeAxis(String axis) {
        repository.homeAxis(axis);
    }

    public void homeAll() {
        repository.homeAxis("all");
    }

    public void moveAxis(String axis, float distance) {
        repository.moveAxis(axis, distance);
    }

    public void startPrint(String filename) {
        repository.startPrint(filename);
    }

    public void pausePrint() {
        repository.pausePrint();
    }

    public void resumePrint() {
        repository.resumePrint();
    }

    public void cancelPrint() {
        repository.cancelPrint();
    }

    public void refreshFiles() {
        repository.refreshFiles();
    }

    public void deleteFile(String filename) {
        repository.deleteFile(filename);
    }

    public boolean isConnected() {
        return repository.isConnected();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanup();
    }
}
