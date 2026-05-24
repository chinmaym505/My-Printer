package com.chinmay.myprinter.data.source.printer;

import com.chinmay.myprinter.data.model.PrinterStatus;

public interface PrinterStatusListener {
    void onStatusUpdate(PrinterStatus status);
}
