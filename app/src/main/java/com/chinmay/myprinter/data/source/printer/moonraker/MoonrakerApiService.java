package com.chinmay.myprinter.data.source.printer.moonraker;

import com.chinmay.myprinter.data.source.printer.moonraker.model.FileListResponse;
import com.chinmay.myprinter.data.source.printer.moonraker.model.MoonrakerResponse;
import com.chinmay.myprinter.data.source.printer.moonraker.model.PrinterObjects;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface MoonrakerApiService {

    /**
     * Get printer information
     */
    @GET("printer/info")
    Call<MoonrakerResponse<Map<String, Object>>> getPrinterInfo();

    /**
     * Query printer objects (temperatures, position, print stats)
     * Example: ?extruder=temperature,target&heater_bed=temperature,target
     */
    @GET("printer/objects/query")
    Call<MoonrakerResponse<PrinterObjects>> queryPrinterObjects(@QueryMap Map<String, String> objects);

    /**
     * Send G-code command
     */
    @POST("printer/gcode/script")
    Call<MoonrakerResponse<String>> sendGcode(@Query("script") String gcode);

    /**
     * Start a print job
     */
    @POST("printer/print/start")
    Call<MoonrakerResponse<String>> startPrint(@Query("filename") String filename);

    /**
     * Pause current print
     */
    @POST("printer/print/pause")
    Call<MoonrakerResponse<String>> pausePrint();

    /**
     * Resume paused print
     */
    @POST("printer/print/resume")
    Call<MoonrakerResponse<String>> resumePrint();

    /**
     * Cancel current print
     */
    @POST("printer/print/cancel")
    Call<MoonrakerResponse<String>> cancelPrint();

    /**
     * List G-code files in gcodes directory.
     * Moonraker returns {"result": [{path, modified, size, permissions}, ...]}
     */
    @GET("server/files/list")
    Call<MoonrakerResponse<List<FileListResponse.FileItem>>> listFiles(@Query("root") String root);

    /**
     * Emergency stop
     */
    @POST("printer/emergency_stop")
    Call<MoonrakerResponse<String>> emergencyStop();

    /**
     * Restart firmware
     */
    @POST("printer/firmware_restart")
    Call<MoonrakerResponse<String>> firmwareRestart();

    /**
     * Get temperature history from server
     */
    @GET("server/temperature_store")
    Call<MoonrakerResponse<Map<String, Object>>> getTemperatureHistory();
}
