package com.example.dptfixture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FixtureProbe {
    public static final String RESULT_FILE_NAME = "profile-behavior-result.json";
    private static final Set<String> EVENTS = new LinkedHashSet<>();

    private FixtureProbe() {
    }

    public static synchronized void record(String event) {
        EVENTS.add(event);
    }

    public static void writeResult(Context context, String actualApplicationClass,
                                   String declaredApplicationClass,
                                   String declaredComponentFactoryClass) {
        try {
            JSONObject result = new JSONObject();
            result.put("status", "passed");
            result.put("applicationId", context.getPackageName());
            result.put("actualApplicationClass", value(actualApplicationClass));
            result.put("declaredApplicationClass", value(declaredApplicationClass));
            result.put("declaredComponentFactoryClass", value(declaredComponentFactoryClass));
            result.put("events", new JSONArray(snapshot()));
            write(context, result.toString());
        } catch (JSONException | IOException exception) {
            writeFailure(context, exception);
        }
    }

    public static void writeFailure(Context context, Exception exception) {
        try {
            JSONObject result = new JSONObject();
            result.put("status", "failed");
            result.put("error", exception.getClass().getName() + ":" + exception.getMessage());
            result.put("events", new JSONArray(snapshot()));
            write(context, result.toString());
        } catch (Exception ignored) {
            // The host-side verifier treats a missing result file as a hard failure.
        }
    }

    private static synchronized Set<String> snapshot() {
        return new LinkedHashSet<>(EVENTS);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static void write(Context context, String content) throws IOException {
        File target = new File(context.getFilesDir(), RESULT_FILE_NAME);
        try (FileOutputStream output = new FileOutputStream(target, false)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
}
