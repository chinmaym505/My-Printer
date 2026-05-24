package com.chinmay.myprinter.data.source.printer;

public interface ConnectionCallback {
    void onConnected();
    void onDisconnected();
    void onError(String error);
}
