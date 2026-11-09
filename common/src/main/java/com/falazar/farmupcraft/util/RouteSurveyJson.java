package com.falazar.farmupcraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;

public final class RouteSurveyJson {
    private static final Gson GSON = new Gson();
    private record Document(int version, RouteSurvey.Saved survey) {}
    private RouteSurveyJson() {}

    public static String encode(RouteSurvey.Saved survey) {
        return GSON.toJson(new Document(1, survey));
    }

    public static RouteSurvey.Saved decode(String json) {
        Document document = GSON.fromJson(json, Document.class);
        if (document == null || document.version != 1 || document.survey == null)
            throw new JsonParseException("Missing or unsupported route survey version");
        return document.survey;
    }

    public static List<String> encodeChunks(RouteSurvey.Saved survey) {
        String json = encode(survey);
        List<String> chunks = new ArrayList<>();
        // NBT strings have a 65535-byte UTF limit; route geometry can be much larger.
        for (int i = 0; i < json.length(); i += 16000)
            chunks.add(json.substring(i, Math.min(json.length(), i + 16000)));
        return chunks;
    }

    public static RouteSurvey.Saved decodeChunks(List<String> chunks) { return decode(String.join("", chunks)); }
}
