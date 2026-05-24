package com.chinmay.myprinter.data.source.printer.moonraker.model;

import com.google.gson.annotations.SerializedName;

public class MoonrakerResponse<T> {
    @SerializedName("result")
    private T result;

    @SerializedName("error")
    private MoonrakerError error;

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public MoonrakerError getError() {
        return error;
    }

    public void setError(MoonrakerError error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return error == null;
    }

    public static class MoonrakerError {
        @SerializedName("code")
        private int code;

        @SerializedName("message")
        private String message;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
