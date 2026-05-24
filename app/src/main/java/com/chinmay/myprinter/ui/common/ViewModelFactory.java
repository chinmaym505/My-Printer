package com.chinmay.myprinter.ui.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.chinmay.myprinter.data.repository.PrinterRepository;
import com.chinmay.myprinter.ui.home.HomeViewModel;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private final PrinterRepository printerRepository;

    public ViewModelFactory(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(printerRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
