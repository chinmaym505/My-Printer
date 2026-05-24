package com.chinmay.myprinter.data.source.printer;

import com.chinmay.myprinter.data.model.GCodeFile;
import java.util.List;

public interface PrinterClient {
    // Connection
    void connect(String url, String apiKey, ConnectionCallback callback);
    void disconnect();
    boolean isConnected();

    // Status
    void addStatusListener(PrinterStatusListener listener);
    void removeStatusListener(PrinterStatusListener listener);

    // Temperature control
    void setTemperature(String heater, int target, CommandCallback callback);

    // Movement
    void homeAxis(String axis, CommandCallback callback);
    void moveAxis(String axis, float distance, CommandCallback callback);

    // Print control
    void startPrint(String filename, CommandCallback callback);
    void pausePrint(CommandCallback callback);
    void resumePrint(CommandCallback callback);
    void cancelPrint(CommandCallback callback);

    // File management
    List<GCodeFile> listFiles();
    void deleteFile(String filename, CommandCallback callback);

    // G-code
    void sendGCode(String gcode, CommandCallback callback);
}
