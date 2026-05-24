package com.chinmay.myprinter.data.source.printer;

public interface CommandCallback {
    void onSuccess();
    void onError(String error);
}
