package com.chinmay.myprinter.data.source.remote;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Handles APIs that return boolean fields as either true/false or 0/1 inconsistently.
 */
public class FlexibleBooleanAdapter extends TypeAdapter<Boolean> {
    @Override
    public void write(JsonWriter out, Boolean value) throws IOException {
        out.value(value);
    }

    @Override
    public Boolean read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.BOOLEAN) {
            return in.nextBoolean();
        } else if (token == JsonToken.NUMBER) {
            return in.nextInt() != 0;
        } else if (token == JsonToken.NULL) {
            in.nextNull();
            return false;
        } else {
            in.skipValue();
            return false;
        }
    }
}
