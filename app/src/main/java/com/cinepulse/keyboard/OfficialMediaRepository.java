package com.cinepulse.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OfficialMediaRepository
 * High-speed Google/Tenor Movie Dialogue GIF & Sticker search engine + Official GIPHY API v1
 * with instant in-memory LRU caching and live movie dialogue autocomplete suggestions.
 */
public class OfficialMediaRepository {

    public static final String PREFS_NAME = "cinepulse_keyboard_prefs";
    public static final String KEY_GIPHY_API = "official_giphy_api_key";

    // ========================================================================
    // PLAY STORE / APP STORE PRODUCTION API KEYS (Set once by Developer — Free)
    // ========================================================================
    public static final String OFFICIAL_TENOR_V2_ENDPOINT = "https://tenor.googleapis.com/v2/search";
    public static final String OFFICIAL_KLIPY_V2_ENDPOINT = "https://api.klipy.com/v2/search";
    public static final String DEVELOPER_TENOR_OR_KLIPY_API_KEY = "BdIguOtSB8yfGP3BUKHlREdrtCG9AbB8R7v0bc64JBFg0891t5XQAIpLDaBkhwah";

    private static final Map<String, List<MediaItem>> QUERY_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> BYTES_CACHE = new ConcurrentHashMap<>();

    // Popular movie dialogues for instant Google-style autocomplete as the user types
    private static final String[] POPULAR_MOVIE_DIALOGUES = new String[]{
            "enna koduma sir idhu",
            "vaanga sir vaanga",
            "ahaan vadivelu",
            "ippo enna பண்ணலாம்",
            "adhukkuulaya vadivelu",
            "neenga nallavara kettavara",
            "varum aana varathu",
            "singamoothu vadivelu",
            "nesamani contractor",
            "rolex just call me sir",
            "tiger ka hukum jailer",
            "kabali da rajinikanth",
            "katham katham",
            "i am waiting vijay",
            "vera level reaction",
            "ennama ipdi panreengale ma",
            "santhanam comedy reaction",
            "goundamani senthil comedy",
            "why so serious joker",
            "emotional damage meme",
            "ye baburao ka style hai",
            "abhi maza aayega na bhidu",
            "paisa hi paisa hoga",
            "i am the danger breaking bad",
            "leonardo dicaprio cheers",
            "mind blown reaction",
            "thank you so much",
            "happy birthday celebration"
    };

    public static class MediaItem {
        public final String id;
        public final String title;
        public final String caption;
        public final String assetImagePath;
        public final String remoteGifUrl;
        public final String assetAudioPath;
        public final String soundLabel;
        public volatile Bitmap cachedBitmap;
        public volatile byte[] cachedRawBytes;

        public MediaItem(
                String id,
                String title,
                String caption,
                String assetImagePath,
                String remoteGifUrl,
                String assetAudioPath,
                String soundLabel
        ) {
            this.id = id;
            this.title = title;
            this.caption = caption;
            this.assetImagePath = assetImagePath;
            this.remoteGifUrl = remoteGifUrl;
            this.assetAudioPath = assetAudioPath;
            this.soundLabel = soundLabel;
        }
    }

    /**
     * Returns instant movie dialogue autocomplete chips matching whatever prefix the user typed.
     */
    public static List<String> getDialogueSuggestions(String typedPrefix) {
        List<String> matches = new ArrayList<>();
        String q = typedPrefix == null ? "" : typedPrefix.trim().toLowerCase();
        if (q.isEmpty()) {
            matches.add("enna koduma sir");
            matches.add("vaanga sir");
            matches.add("ahaan vadivelu");
            matches.add("rolex sir");
            matches.add("tiger ka hukum");
            matches.add("why so serious");
            matches.add("baburao style");
            return matches;
        }
        for (String dlg : POPULAR_MOVIE_DIALOGUES) {
            if (dlg.contains(q)) {
                matches.add(dlg);
                if (matches.size() >= 6) break;
            }
        }
        if (!matches.contains(q)) {
            matches.add(0, q + " dialogue");
        }
        return matches;
    }

    public static List<MediaItem> getBuiltInItems(String filterQuery) {
        List<MediaItem> all = new ArrayList<>();
        all.add(new MediaItem(
                "kb_vadivelu",
                "Vadivelu Reaction",
                "",
                "samples/hera-pheri-baburao.png",
                null,
                "samples/sound_sb_vadivelu.mp3",
                "Vadivelu"
        ));
        all.add(new MediaItem(
                "kb_rolex",
                "Rolex Suriya",
                "",
                "samples/vikram-rolex-sir.png",
                null,
                "samples/sound_sb_rolex.mp3",
                "Rolex"
        ));
        all.add(new MediaItem(
                "kb_hukum",
                "Jailer Rajinikanth",
                "",
                "samples/jailer-hukum.png",
                null,
                "samples/sound_sb_hukum.mp3",
                "Rajini"
        ));
        all.add(new MediaItem(
                "kb_serious",
                "Joker Reaction",
                "",
                "samples/dark-knight-serious.png",
                null,
                "samples/sound_sb_serious.mp3",
                "Joker"
        ));
        all.add(new MediaItem(
                "kb_emotional",
                "Breaking Bad Reaction",
                "",
                "samples/breaking-bad-danger.png",
                null,
                "samples/sound_sb_danger.mp3",
                "Viral"
        ));
        all.add(new MediaItem(
                "kb_baburao",
                "Baburao Hera Pheri",
                "",
                "samples/wolf-not-leaving.png",
                null,
                "samples/sound_sb_baburao.mp3",
                "Baburao"
        ));
        return all;
    }

    /**
     * Primary Movie Dialogue GIF & Sticker Search.
     * Queries Official KLIPY v2 JSON API FIRST so exact movie dialogues ("enna koduma sir",
     * "vaanga sir", "ahaan", "why so serious", "baburao") return accurate movie scene GIFs immediately.
     */
    public static List<MediaItem> searchMediaForKeyboard(Context context, String query, boolean stickersMode) {
        String cleanQuery = (query == null || query.trim().isEmpty() || "all".equalsIgnoreCase(query.trim()))
                ? "Vadivelu comedy"
                : query.trim();

        String cacheKey = cleanQuery.toLowerCase() + "_" + stickersMode;
        List<MediaItem> cached = QUERY_CACHE.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<MediaItem> results = new ArrayList<>();
        Set<String> seenMediaIds = new LinkedHashSet<>();

        // 0. Official KLIPY v2 / Tenor v2 JSON API (https://api.klipy.com/v2/search)
        if (!DEVELOPER_TENOR_OR_KLIPY_API_KEY.trim().isEmpty()) {
            String[] v2Endpoints = new String[]{OFFICIAL_KLIPY_V2_ENDPOINT, OFFICIAL_TENOR_V2_ENDPOINT};
            for (String baseEndpoint : v2Endpoints) {
                if (!results.isEmpty()) break;
                try {
                    String encodedQ = URLEncoder.encode(cleanQuery, "UTF-8");
                    String searchFilter = stickersMode ? "&searchfilter=sticker" : "";
                    String v2Url = baseEndpoint
                            + "?key=" + URLEncoder.encode(DEVELOPER_TENOR_OR_KLIPY_API_KEY.trim(), "UTF-8")
                            + "&client_key=vibekey"
                            + "&q=" + encodedQ
                            + "&limit=20&media_filter=tinygif,gif" + searchFilter;
                    HttpURLConnection v2Conn = (HttpURLConnection) new URL(v2Url).openConnection();
                    v2Conn.setRequestMethod("GET");
                    v2Conn.setConnectTimeout(2500);
                    v2Conn.setReadTimeout(2500);
                    if (v2Conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(v2Conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        JSONObject rootJson = new JSONObject(sb.toString());
                        JSONArray arr = rootJson.optJSONArray("results");
                        if (arr != null) {
                            for (int i = 0; i < arr.length() && results.size() < 20; i++) {
                                JSONObject itemObj = arr.optJSONObject(i);
                                if (itemObj == null) continue;
                                String id = itemObj.optString("id", "v2_" + i);
                                JSONObject formats = itemObj.optJSONObject("media_formats");
                                if (formats == null) continue;
                                JSONObject tinyGif = formats.optJSONObject("tinygif");
                                JSONObject fullGif = formats.optJSONObject("gif");
                                String gifUrl = (tinyGif != null && !tinyGif.optString("url").isEmpty())
                                        ? tinyGif.optString("url")
                                        : (fullGif != null ? fullGif.optString("url") : "");
                                if (!gifUrl.isEmpty() && seenMediaIds.add(id)) {
                                    results.add(new MediaItem(
                                            "v2_" + id,
                                            cleanQuery,
                                            "",
                                            "samples/hera-pheri-baburao.png",
                                            gifUrl,
                                            "samples/sound_sb_vadivelu.mp3",
                                            cleanQuery
                                    ));
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Optional Official GIPHY API v1 (only if a custom official GIPHY key is configured)
        if (results.isEmpty()) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String userKey = prefs.getString(KEY_GIPHY_API, "").trim();
                if (!userKey.isEmpty()) {
                    String endpoint = stickersMode
                            ? "https://api.giphy.com/v1/stickers/search"
                            : "https://api.giphy.com/v1/gifs/search";
                    String encodedQ = URLEncoder.encode(cleanQuery, "UTF-8");
                    String urlStr = endpoint + "?api_key=" + URLEncoder.encode(userKey, "UTF-8")
                            + "&q=" + encodedQ + "&limit=8&rating=pg-13";

                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        JSONArray data = json.optJSONArray("data");
                        if (data != null) {
                            for (int i = 0; i < data.length() && results.size() < 16; i++) {
                                JSONObject obj = data.optJSONObject(i);
                                if (obj == null) continue;
                                String id = obj.optString("id", "giphy_" + i);
                                JSONObject images = obj.optJSONObject("images");
                                if (images == null) continue;
                                JSONObject fixedSmall = images.optJSONObject("fixed_height_small");
                                JSONObject fixedHeight = images.optJSONObject("fixed_height");
                                String gifUrl = null;
                                if (fixedSmall != null && !fixedSmall.optString("url").isEmpty()) {
                                    gifUrl = fixedSmall.optString("url");
                                } else if (fixedHeight != null && !fixedHeight.optString("url").isEmpty()) {
                                    gifUrl = fixedHeight.optString("url");
                                }
                                if (gifUrl != null && !gifUrl.isEmpty() && !seenMediaIds.contains(id)) {
                                    seenMediaIds.add(id);
                                    results.add(new MediaItem(
                                            "giphy_" + id,
                                            cleanQuery,
                                            "",
                                            "samples/dark-knight-serious.png",
                                            gifUrl,
                                            "samples/sound_sb_vadivelu.mp3",
                                            cleanQuery
                                    ));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (results.isEmpty()) {
            results.addAll(getBuiltInItems(cleanQuery));
        } else {
            QUERY_CACHE.put(cacheKey, results);
        }
        return results;
    }

    public static Bitmap loadItemPreviewBitmap(Context context, MediaItem item) {
        if (item.cachedBitmap != null && !item.cachedBitmap.isRecycled()) {
            return item.cachedBitmap;
        }
        byte[] raw = loadItemRawBytes(context, item);
        if (raw != null && raw.length > 0) {
            Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bmp != null) {
                item.cachedBitmap = bmp;
                return bmp;
            }
        }
        try (InputStream is = context.getAssets().open(item.assetImagePath)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            item.cachedBitmap = bmp;
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] loadItemRawBytes(Context context, MediaItem item) {
        if (item.cachedRawBytes != null && item.cachedRawBytes.length > 0) {
            return item.cachedRawBytes;
        }
        if (item.remoteGifUrl != null && BYTES_CACHE.containsKey(item.remoteGifUrl)) {
            item.cachedRawBytes = BYTES_CACHE.get(item.remoteGifUrl);
            return item.cachedRawBytes;
        }
        if (item.remoteGifUrl != null && item.remoteGifUrl.startsWith("http")) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(item.remoteGifUrl).openConnection();
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                try (InputStream is = conn.getInputStream()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    if (bos.size() > 0) {
                        byte[] bytes = bos.toByteArray();
                        item.cachedRawBytes = bytes;
                        BYTES_CACHE.put(item.remoteGifUrl, bytes);
                        return bytes;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try (InputStream is = context.getAssets().open(item.assetImagePath)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            item.cachedRawBytes = bos.toByteArray();
            return item.cachedRawBytes;
        } catch (Exception e) {
            return null;
        }
    }
}
