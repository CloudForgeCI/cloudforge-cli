package com.cloudforgeci.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The JSON-lines-on-stdout protocol shared by every subcommand: one JSON object per line, no
 * pretty-printing (this is a machine protocol, not a console report), always carrying
 * {@code phase}/{@code status} first so a caller can branch on those two fields without parsing
 * the rest.
 */
final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    static void emit(String phase, String status, Map<String, ?> fields) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("phase", phase);
        line.put("status", status);
        line.putAll(fields);
        try {
            System.out.println(MAPPER.writeValueAsString(line));
            System.out.flush();
        } catch (IOException e) {
            // A caller streaming stdout must never see a torn/partial JSON line -- fall back to
            // a plain stderr line instead of risking a half-written one on stdout.
            System.err.println("[" + phase + "/" + status + "] " + fields);
        }
    }
}
