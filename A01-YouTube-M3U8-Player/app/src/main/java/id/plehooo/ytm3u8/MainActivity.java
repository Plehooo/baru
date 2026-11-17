package id.plehooo.ytm3u8;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String RESOLVER_URL = "http://127.0.0.1:8765/resolve";
    private static final String TAGLINE = "EXTRACT • SELECT • PLAY";
    private static final int BG = Color.rgb(8, 17, 31);
    private static final int PANEL = Color.rgb(17, 30, 49);
    private static final int PANEL_ALT = Color.rgb(23, 39, 61);
    private static final int ACCENT = Color.rgb(94, 234, 212);
    private static final int WHITE = Color.rgb(240, 247, 255);
    private static final int MUTED = Color.rgb(159, 177, 198);
    private static final int WARNING = Color.rgb(255, 196, 100);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<StreamOption> streams = new ArrayList<>();

    private EditText urlInput;
    private Button extractButton;
    private Button playButton;
    private Button copyButton;
    private ProgressBar progress;
    private TextView statusView;
    private TextView titleView;
    private TextView metaView;
    private Spinner qualitySpinner;
    private ArrayAdapter<String> qualityAdapter;
    private PlayerView playerView;
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildScreen();
        setStatus("Menunggu resolver Termux di 127.0.0.1:8765", MUTED);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = text(TAGLINE, 11, ACCENT, true);
        eyebrow.setLetterSpacing(0.16f);
        page.addView(eyebrow, margin(0, 2, 0, 8));

        TextView heading = text("A01 YouTube\nM3U8 Player", 29, WHITE, true);
        heading.setLineSpacing(dp(1), 1.0f);
        page.addView(heading, margin(0, 0, 0, 7));

        TextView intro = text("Ambil stream HLS yang tersedia dari URL YouTube, lalu putar langsung di aplikasi.", 13, MUTED, false);
        intro.setLineSpacing(dp(3), 1.0f);
        page.addView(intro, margin(0, 0, 0, 17));

        LinearLayout inputCard = card();
        page.addView(inputCard, margin(0, 0, 0, 13));
        inputCard.addView(text("SUMBER VIDEO", 11, ACCENT, true), margin(0, 0, 0, 10));

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextColor(WHITE);
        urlInput.setHintTextColor(Color.rgb(120, 141, 165));
        urlInput.setHint("Tempel URL YouTube / YouTube Kids");
        urlInput.setTextSize(14);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setPadding(dp(13), dp(12), dp(13), dp(12));
        urlInput.setBackground(shape(PANEL_ALT, 12, Color.rgb(55, 76, 101), 1));
        inputCard.addView(urlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(49)));
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                hideKeyboard();
                resolveVideo();
                return true;
            }
            return false;
        });

        extractButton = button("EXTRACT M3U8", ACCENT, BG);
        inputCard.addView(extractButton, margin(0, 11, 0, 0));
        extractButton.setOnClickListener(v -> {
            hideKeyboard();
            resolveVideo();
        });

        LinearLayout resultCard = card();
        page.addView(resultCard, margin(0, 0, 0, 13));
        resultCard.addView(text("HASIL EKSTRAKSI", 11, ACCENT, true), margin(0, 0, 0, 8));
        titleView = text("Belum ada video", 16, WHITE, true);
        resultCard.addView(titleView, margin(0, 0, 0, 5));
        metaView = text("Format HLS/M3U8 akan muncul setelah URL berhasil diproses.", 12, MUTED, false);
        resultCard.addView(metaView, margin(0, 0, 0, 11));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        resultCard.addView(progress, margin(0, 0, 0, 8));

        statusView = text("Resolver belum dicek.", 12, MUTED, false);
        statusView.setPadding(dp(11), dp(10), dp(11), dp(10));
        statusView.setBackground(shape(Color.rgb(25, 39, 57), 10, Color.rgb(45, 65, 88), 1));
        resultCard.addView(statusView, margin(0, 0, 0, 12));

        qualitySpinner = new Spinner(this);
        qualitySpinner.setPadding(dp(7), dp(2), dp(7), dp(2));
        qualitySpinner.setBackground(shape(PANEL_ALT, 10, Color.rgb(55, 76, 101), 1));
        qualityAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(WHITE);
                view.setTextSize(13);
                view.setPadding(dp(8), dp(7), dp(8), dp(7));
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(WHITE);
                view.setTextSize(13);
                view.setBackgroundColor(PANEL_ALT);
                view.setPadding(dp(12), dp(11), dp(12), dp(11));
                return view;
            }
        };
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        resultCard.addView(qualitySpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        resultCard.addView(actions, margin(0, 10, 0, 0));
        playButton = button("PUTAR", ACCENT, BG);
        copyButton = button("SALIN URL", PANEL_ALT, WHITE);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(47), 1f);
        actions.addView(playButton, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(47), 1f);
        half2.setMargins(dp(8), 0, 0, 0);
        actions.addView(copyButton, half2);
        playButton.setOnClickListener(v -> playSelected());
        copyButton.setOnClickListener(v -> copySelected());
        playButton.setEnabled(false);
        copyButton.setEnabled(false);
        playButton.setAlpha(0.45f);
        copyButton.setAlpha(0.45f);

        LinearLayout playbackCard = card();
        page.addView(playbackCard, margin(0, 0, 0, 13));
        playbackCard.addView(text("PEMUTAR VIDEO", 11, ACCENT, true), margin(0, 0, 0, 9));
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(218)));
        playbackCard.addView(playerView);
        playbackCard.addView(text("Kontrol play, pause, dan seek tersedia dari kontrol pemutar.", 11, MUTED, false), margin(0, 9, 0, 0));

        LinearLayout helpCard = card();
        page.addView(helpCard, margin(0, 0, 0, 10));
        helpCard.addView(text("PERSIAPAN TERMUX", 11, ACCENT, true), margin(0, 0, 0, 7));
        TextView help = text(
                "1. Pasang resolver dari folder proyek: bash termux/install-termux.sh\n" +
                "2. Jalankan: bash ~/A01_YTM3U8/start-resolver.sh\n" +
                "3. Biarkan Termux berjalan, lalu tekan EXTRACT M3U8 di aplikasi.\n\n" +
                "Resolver hanya menerima URL YouTube dan berjalan lokal di ponsel ini. Tidak memakai cookie atau melewati DRM/pembatasan akses.",
                12, MUTED, false);
        help.setLineSpacing(dp(4), 1.0f);
        helpCard.addView(help);

        TextView footer = text("A01 • PLEHOOO • BUILD 1.0.0", 10, Color.rgb(104, 126, 151), true);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, margin(0, 5, 0, 0));

        setContentView(scroll);
    }

    private void resolveVideo() {
        String input = urlInput.getText().toString().trim();
        if (input.isEmpty()) {
            setStatus("Masukkan URL YouTube terlebih dahulu.", WARNING);
            return;
        }

        setBusy(true);
        setStatus("Menghubungi resolver Termux dan membaca format HLS...", MUTED);
        io.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("url", input);
                JSONObject response = postJson(RESOLVER_URL, request);
                if (!response.optBoolean("ok", false)) {
                    throw new IllegalStateException(response.optString("error", "Ekstraksi gagal."));
                }

                JSONArray array = response.optJSONArray("formats");
                ArrayList<StreamOption> found = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) continue;
                        String streamUrl = item.optString("url", "");
                        if (!streamUrl.startsWith("https://")) continue;
                        found.add(new StreamOption(item));
                    }
                }
                if (found.isEmpty()) {
                    throw new IllegalStateException("Tidak ditemukan format video HLS/M3U8 yang bisa diputar untuk URL ini.");
                }

                String title = response.optString("title", "Video YouTube");
                boolean live = response.optBoolean("is_live", false);
                String extractor = response.optString("extractor", "YouTube");
                main.post(() -> {
                    streams.clear();
                    streams.addAll(found);
                    ArrayList<String> labels = new ArrayList<>();
                    for (StreamOption stream : streams) labels.add(stream.label);
                    qualityAdapter.clear();
                    qualityAdapter.addAll(labels);
                    qualityAdapter.notifyDataSetChanged();
                    titleView.setText(title);
                    metaView.setText(String.format(Locale.US, "%s  •  %d format HLS  •  %s",
                            live ? "LIVE" : "VIDEO", streams.size(), extractor));
                    playButton.setEnabled(true);
                    copyButton.setEnabled(true);
                    playButton.setAlpha(1f);
                    copyButton.setAlpha(1f);
                    setBusy(false);
                    setStatus("M3U8 ditemukan. Pilih kualitas lalu tekan PUTAR.", ACCENT);
                });
            } catch (Exception e) {
                String message = friendlyError(e);
                main.post(() -> {
                    setBusy(false);
                    streams.clear();
                    qualityAdapter.clear();
                    qualityAdapter.notifyDataSetChanged();
                    playButton.setEnabled(false);
                    copyButton.setEnabled(false);
                    playButton.setAlpha(0.45f);
                    copyButton.setAlpha(0.45f);
                    setStatus(message, WARNING);
                });
            }
        });
    }

    private JSONObject postJson(String address, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(100000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IllegalStateException("Resolver memberi respons HTTP " + code);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (body.length() > 8_000_000) throw new IllegalStateException("Respons resolver terlalu besar.");
            JSONObject result = new JSONObject(body.toString());
            if (code < 200 || code >= 300) {
                throw new IllegalStateException(result.optString("error", "Resolver HTTP " + code));
            }
            return result;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void playSelected() {
        StreamOption selected = selectedStream();
        if (selected == null) {
            setStatus("Belum ada URL M3U8 untuk diputar.", WARNING);
            return;
        }
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(25000)
                    .setUserAgent("A01-YouTube-M3U8/1.0 (Android)");
            if (!selected.headers.isEmpty()) {
                httpFactory.setDefaultRequestProperties(selected.headers);
            }
            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        setStatus("Buffering stream HLS...", MUTED);
                    } else if (playbackState == Player.STATE_READY) {
                        setStatus("Player siap. Stream sedang diputar.", ACCENT);
                    } else if (playbackState == Player.STATE_ENDED) {
                        setStatus("Pemutaran selesai.", MUTED);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    setStatus("Gagal memutar stream. Ekstrak ulang URL; link YouTube bisa kedaluwarsa atau format tidak kompatibel.", WARNING);
                }
            });

            MediaItem item = new MediaItem.Builder()
                    .setUri(selected.url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build();
            player.setMediaItem(item);
            player.prepare();
            player.play();
            setStatus("Memulai pemutaran: " + selected.label, ACCENT);
        } catch (Exception e) {
            setStatus("Player tidak bisa dimulai: " + e.getMessage(), WARNING);
        }
    }

    private void copySelected() {
        StreamOption selected = selectedStream();
        if (selected == null) {
            setStatus("Tidak ada URL untuk disalin.", WARNING);
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("YouTube HLS URL", selected.url));
            Toast.makeText(this, "URL M3U8 disalin. Link bisa kedaluwarsa.", Toast.LENGTH_SHORT).show();
        }
    }

    private StreamOption selectedStream() {
        int position = qualitySpinner == null ? -1 : qualitySpinner.getSelectedItemPosition();
        if (position < 0 || position >= streams.size()) return null;
        return streams.get(position);
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage() == null ? "Terjadi kesalahan." : error.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (error instanceof java.net.ConnectException || lower.contains("failed to connect") || lower.contains("connection refused")) {
            return "Resolver belum aktif. Buka Termux lalu jalankan: bash ~/A01_YTM3U8/start-resolver.sh";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "Resolver kehabisan waktu. Periksa internet dan coba ekstrak ulang.";
        }
        return message;
    }

    private void setBusy(boolean busy) {
        extractButton.setEnabled(!busy);
        extractButton.setAlpha(busy ? 0.55f : 1f);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String message, int color) {
        if (statusView == null) return;
        statusView.setText(message);
        statusView.setTextColor(color);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
            urlInput.clearFocus();
        } catch (Exception ignored) {
            // Keyboard dismissal is optional; extraction can continue.
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(14), dp(14), dp(14));
        layout.setBackground(shape(PANEL, 16, Color.rgb(39, 58, 82), 1));
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setTextColor(foreground);
        button.setBackground(shape(background, 12, background == ACCENT ? ACCENT : Color.rgb(65, 85, 110), 1));
        button.setPadding(dp(9), 0, dp(9), 0);
        return button;
    }

    private GradientDrawable shape(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        io.shutdownNow();
        super.onDestroy();
    }

    private static final class StreamOption {
        final String url;
        final String label;
        final Map<String, String> headers = new HashMap<>();

        StreamOption(JSONObject source) throws JSONException {
            url = source.optString("url", "");
            String formatId = source.optString("format_id", "hls");
            String protocol = source.optString("protocol", "m3u8");
            String resolution = source.optString("resolution", "");
            int height = source.optInt("height", 0);
            if (resolution.isEmpty() && height > 0) resolution = height + "p";
            if (resolution.isEmpty()) resolution = "auto";
            double fps = source.optDouble("fps", 0d);
            String fpsText = fps > 0 ? String.format(Locale.US, " • %.0f fps", fps) : "";
            label = resolution + fpsText + " • " + protocol + " • " + formatId;

            JSONObject sourceHeaders = source.optJSONObject("headers");
            if (sourceHeaders != null) {
                Iterator<String> keys = sourceHeaders.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = sourceHeaders.optString(key, "");
                    if (!value.isEmpty() && value.length() <= 2048 && !value.contains("\n") && !value.contains("\r")) {
                        headers.put(key, value);
                    }
                }
            }
        }
    }
}
