package com.cinepulse.keyboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VibeKey (CinePulseInputMethodService)
 * Redesigned Flagship Modern Keyboard:
 * - Single unified top toolbar: [ABC] + [Integrated Search Pill with Blinking Cursor |] + [GIF | Sticker] + [Switch Keyboard Icon]
 * - Full Shift (Capital conversion) and ?123 / =\< Punctuation & Symbols in both Search & ABC keyboards
 * - Zero cartoon emoji clutter (uses crisp Canvas vector icons & clean geometric pills)
 * - Clean edge-to-edge animated GIF/Sticker tiles (no badges covering faces)
 */
public class CinePulseInputMethodService extends InputMethodService {

    private static final boolean ENABLE_AUDIO_GIF_MODE = false;

    private static final int MODE_GIF = 0;       // 1-Tap .gif into WhatsApp/IG
    private static final int MODE_STICKER = 1;   // 1-Tap 512x512 .webp Sticker into WhatsApp/IG
    private static final int MODE_ABC_TYPE = 2;  // Built-in normal QWERTY text typing
    private static final int MODE_AUDIO_GIF = 3; // Hidden for now

    private int currentMode = MODE_GIF;
    private int lastMediaMode = MODE_GIF;
    private boolean isSearchingMedia = false;
    private boolean isShifted = false;
    private boolean searchShifted = false;
    private int searchSymbolsPage = 0; // 0 = letters, 1 = ?123 numbers/punctuation, 2 = =\< extended symbols
    private int chatSymbolsPage = 0;   // 0 = letters, 1 = ?123 numbers/punctuation, 2 = =\< extended symbols
    private boolean cursorBlinkVisible = true;
    private String activeCategoryQuery = "enna koduma sir";
    private final StringBuilder searchQueryBuilder = new StringBuilder();

    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger searchGeneration = new AtomicInteger(0);
    private Runnable liveSearchDebounceRunnable;
    private final Runnable cursorBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSearchingMedia) return;
            cursorBlinkVisible = !cursorBlinkVisible;
            updateSearchDisplayText();
            mainHandler.postDelayed(this, 500);
        }
    };

    private TextView abcToggleBtn;
    private LinearLayout searchPillContainer;
    private TextView searchInputDisplay;
    private TextView clearSearchBtn;
    private ProgressBar loadingSpinner;
    private TextView segGifBtn;
    private TextView segStickerBtn;

    private LinearLayout mediaPaneContainer;
    private LinearLayout abcKeyboardContainer;
    private LinearLayout searchKeysOverlay;
    private LinearLayout searchDynamicKeysContainer;
    private LinearLayout chatDynamicKeysContainer;
    private TextView searchModeToggleKey;
    private TextView chatModeToggleKey;
    private LinearLayout dialogueSuggestionsRow;
    private LinearLayout gridRow1;
    private LinearLayout gridRow2;

    private final List<TextView> categoryChipViews = new ArrayList<>();
    private final List<TextView> letterKeyViews = new ArrayList<>();

    @Override
    public View onCreateInputView() {
        Context ctx = this;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#EEF2FA"));
        root.setPadding(dp(8), dp(7), dp(8), dp(8));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // ====================================================================
        // 1. UNIFIED MODERN TOP TOOLBAR (1 Sleek Row Instead of 2 Clunky Bars!)
        //    [ABC]  [ 🔍 Search dialogue...       ✕ ]  [GIF|Sticker]  [🌐]
        // ====================================================================
        LinearLayout topToolbar = new LinearLayout(ctx);
        topToolbar.setOrientation(LinearLayout.HORIZONTAL);
        topToolbar.setGravity(Gravity.CENTER_VERTICAL);
        topToolbar.setPadding(0, 0, 0, dp(6));

        // Left: [ABC] / [GIFs] Quick Toggle Key
        abcToggleBtn = new TextView(ctx);
        abcToggleBtn.setText("ABC");
        abcToggleBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        abcToggleBtn.setTypeface(Typeface.DEFAULT_BOLD);
        abcToggleBtn.setTextColor(Color.parseColor("#0F172A"));
        abcToggleBtn.setGravity(Gravity.CENTER);
        abcToggleBtn.setBackground(roundRect(Color.WHITE, dp(12), Color.parseColor("#DCE3F0"), dp(1)));
        abcToggleBtn.setOnClickListener(v -> {
            if (currentMode == MODE_ABC_TYPE) {
                switchMode(lastMediaMode);
            } else {
                toggleSearchTypingBar(false);
                switchMode(MODE_ABC_TYPE);
            }
        });
        LinearLayout.LayoutParams abcLp = new LinearLayout.LayoutParams(dp(46), dp(36));
        abcLp.setMarginEnd(dp(6));
        topToolbar.addView(abcToggleBtn, abcLp);

        // Center: Integrated Frosted Search Pill (Tapping anywhere opens live dialogue search)
        searchPillContainer = new LinearLayout(ctx);
        searchPillContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchPillContainer.setGravity(Gravity.CENTER_VERTICAL);
        searchPillContainer.setBackground(roundRect(Color.WHITE, dp(12), Color.parseColor("#DCE3F0"), dp(1)));
        searchPillContainer.setPadding(dp(10), 0, dp(6), 0);
        searchPillContainer.setOnClickListener(v -> {
            if (currentMode == MODE_ABC_TYPE) {
                switchMode(lastMediaMode);
            }
            toggleSearchTypingBar(!isSearchingMedia);
        });

        VectorIconView searchLoupe = new VectorIconView(ctx, VectorIconView.ICON_SEARCH, Color.parseColor("#64748B"));
        searchPillContainer.addView(searchLoupe, new LinearLayout.LayoutParams(dp(15), dp(15)));

        searchInputDisplay = new TextView(ctx);
        searchInputDisplay.setText("Search KLIPY GIFs");
        searchInputDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        searchInputDisplay.setTextColor(Color.parseColor("#64748B"));
        searchInputDisplay.setSingleLine(true);
        searchInputDisplay.setEllipsize(TextUtils.TruncateAt.END);
        searchInputDisplay.setPadding(dp(7), 0, dp(4), 0);
        searchPillContainer.addView(searchInputDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        loadingSpinner = new ProgressBar(ctx);
        loadingSpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        spinLp.setMarginEnd(dp(4));
        searchPillContainer.addView(loadingSpinner, spinLp);

        clearSearchBtn = new TextView(ctx);
        clearSearchBtn.setText("✕");
        clearSearchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        clearSearchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearSearchBtn.setTextColor(Color.parseColor("#64748B"));
        clearSearchBtn.setGravity(Gravity.CENTER);
        clearSearchBtn.setPadding(dp(6), dp(4), dp(6), dp(4));
        clearSearchBtn.setVisibility(View.GONE);
        clearSearchBtn.setOnClickListener(v -> {
            searchQueryBuilder.setLength(0);
            updateSearchDisplayText();
            refreshDialogueSuggestionChips();
            loadMediaGrid(activeCategoryQuery, true);
        });
        searchPillContainer.addView(clearSearchBtn);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        searchLp.setMarginEnd(dp(6));
        topToolbar.addView(searchPillContainer, searchLp);

        // Right-Center: Compact Segmented Toggle [GIF | Sticker]
        LinearLayout segGroup = new LinearLayout(ctx);
        segGroup.setOrientation(LinearLayout.HORIZONTAL);
        segGroup.setGravity(Gravity.CENTER_VERTICAL);
        segGroup.setBackground(roundRect(Color.WHITE, dp(12), Color.parseColor("#DCE3F0"), dp(1)));
        segGroup.setPadding(dp(3), dp(3), dp(3), dp(3));

        segGifBtn = createSegmentButton("GIF", MODE_GIF);
        segStickerBtn = createSegmentButton("Sticker", MODE_STICKER);
        segGroup.addView(segGifBtn, new LinearLayout.LayoutParams(dp(42), dp(30)));
        segGroup.addView(segStickerBtn, new LinearLayout.LayoutParams(dp(54), dp(30)));

        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
        );
        segLp.setMarginEnd(dp(6));
        topToolbar.addView(segGroup, segLp);

        // Far-Right: Dedicated Keyboard Switcher Button (Mini Keyboard + Swap Arrows Icon)
        FrameLayout switchKbBtn = new FrameLayout(ctx);
        switchKbBtn.setBackground(roundRect(Color.parseColor("#EEF2FF"), dp(12), Color.parseColor("#C7D2FE"), dp(1)));
        VectorIconView switchKbIcon = new VectorIconView(ctx, VectorIconView.ICON_KEYBOARD_SWITCH, Color.parseColor("#4F46E5"));
        FrameLayout.LayoutParams swIconLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        switchKbBtn.addView(switchKbIcon, swIconLp);
        switchKbBtn.setOnClickListener(v -> switchToSystemKeyboard());
        topToolbar.addView(switchKbBtn, new LinearLayout.LayoutParams(dp(38), dp(36)));

        root.addView(topToolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // ====================================================================
        // 2. MEDIA PANE (Slim Category Pills + Clean Bento Grid + Search Keys)
        // ====================================================================
        mediaPaneContainer = new LinearLayout(ctx);
        mediaPaneContainer.setOrientation(LinearLayout.VERTICAL);

        // Clean Typography Category Pills (Zero Cartoon Emojis!)
        HorizontalScrollView chipsScroll = new HorizontalScrollView(ctx);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsTrack = new LinearLayout(ctx);
        chipsTrack.setOrientation(LinearLayout.HORIZONTAL);
        chipsTrack.setPadding(dp(1), 0, dp(1), dp(6));

        String[][] categories = new String[][]{
                {"Enna Koduma", "enna koduma sir"},
                {"Vaanga Sir", "vaanga sir vadivelu"},
                {"Vadivelu", "Vadivelu comedy"},
                {"Rolex Sir", "Rolex Suriya"},
                {"Hukum", "tiger ka hukum jailer"},
                {"Vijay", "i am waiting vijay"},
                {"Joker", "why so serious joker"},
                {"Baburao", "ye baburao ka style hai"}
        };

        categoryChipViews.clear();
        for (int i = 0; i < categories.length; i++) {
            final String label = categories[i][0];
            final String query = categories[i][1];
            TextView chip = new TextView(ctx);
            chip.setText(label);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(dp(12), dp(5), dp(12), dp(5));
            styleCategoryChip(chip, i == 0);
            final int chipIdx = i;
            chip.setOnClickListener(v -> {
                for (int j = 0; j < categoryChipViews.size(); j++) {
                    styleCategoryChip(categoryChipViews.get(j), j == chipIdx);
                }
                toggleSearchTypingBar(false);
                activeCategoryQuery = query;
                loadMediaGrid(query, true);
            });
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            chipLp.setMarginEnd(dp(6));
            chipsTrack.addView(chip, chipLp);
            categoryChipViews.add(chip);
        }
        chipsScroll.addView(chipsTrack);
        mediaPaneContainer.addView(chipsScroll);

        // 2-Row Clean Edge-to-Edge Animated Media Grid (No cluttered corner badges!)
        HorizontalScrollView gridScroll = new HorizontalScrollView(ctx);
        gridScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout gridRowsWrapper = new LinearLayout(ctx);
        gridRowsWrapper.setOrientation(LinearLayout.VERTICAL);

        gridRow1 = new LinearLayout(ctx);
        gridRow1.setOrientation(LinearLayout.HORIZONTAL);
        gridRow1.setPadding(dp(1), 0, dp(1), dp(6));

        gridRow2 = new LinearLayout(ctx);
        gridRow2.setOrientation(LinearLayout.HORIZONTAL);
        gridRow2.setPadding(dp(1), 0, dp(1), dp(2));

        gridRowsWrapper.addView(gridRow1);
        gridRowsWrapper.addView(gridRow2);
        gridScroll.addView(gridRowsWrapper);
        mediaPaneContainer.addView(gridScroll);

        // Sleek Elevated Search Typing Drawer (Appears when Search Pill is tapped)
        searchKeysOverlay = buildSearchTypingStrip(ctx);
        searchKeysOverlay.setVisibility(View.GONE);
        mediaPaneContainer.addView(searchKeysOverlay);

        root.addView(mediaPaneContainer);

        // ====================================================================
        // 3. BUILT-IN NORMAL QWERTY KEYBOARD PANE (When [ABC] is Active)
        // ====================================================================
        abcKeyboardContainer = buildFullAbcChatKeyboard(ctx);
        abcKeyboardContainer.setVisibility(View.GONE);
        root.addView(abcKeyboardContainer);

        updateTopToolbarStyles();
        loadMediaGrid(activeCategoryQuery, true);
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        updateTopToolbarStyles();
    }

    private TextView createSegmentButton(String title, final int mode) {
        TextView btn = new TextView(this);
        btn.setText(title);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setOnClickListener(v -> {
            lastMediaMode = mode;
            switchMode(mode);
        });
        return btn;
    }

    private void switchMode(int mode) {
        currentMode = mode;
        updateTopToolbarStyles();
        if (mode == MODE_ABC_TYPE) {
            mediaPaneContainer.setVisibility(View.GONE);
            abcKeyboardContainer.setVisibility(View.VISIBLE);
        } else {
            abcKeyboardContainer.setVisibility(View.GONE);
            mediaPaneContainer.setVisibility(View.VISIBLE);
            String q = searchQueryBuilder.length() >= 2
                    ? searchQueryBuilder.toString().trim()
                    : activeCategoryQuery;
            loadMediaGrid(q, true);
        }
    }

    private void updateTopToolbarStyles() {
        if (abcToggleBtn != null) {
            boolean isAbc = (currentMode == MODE_ABC_TYPE);
            abcToggleBtn.setText(isAbc ? "GIFs" : "ABC");
            abcToggleBtn.setTextColor(isAbc ? Color.WHITE : Color.parseColor("#0F172A"));
            abcToggleBtn.setBackground(roundRect(
                    Color.parseColor(isAbc ? "#4F46E5" : "#FFFFFF"),
                    dp(12),
                    Color.parseColor(isAbc ? "#4F46E5" : "#DCE3F0"),
                    dp(1)
            ));
        }
        styleSegment(segGifBtn, currentMode == MODE_GIF);
        styleSegment(segStickerBtn, currentMode == MODE_STICKER);
    }

    private void styleSegment(TextView seg, boolean active) {
        if (seg == null) return;
        if (active) {
            seg.setTextColor(Color.WHITE);
            seg.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(9), Color.TRANSPARENT, 0));
        } else {
            seg.setTextColor(Color.parseColor("#475569"));
            seg.setBackground(roundRect(Color.TRANSPARENT, dp(9), Color.TRANSPARENT, 0));
        }
    }

    private void styleCategoryChip(TextView chip, boolean active) {
        if (active) {
            chip.setTextColor(Color.WHITE);
            chip.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(99), Color.parseColor("#4F46E5"), 0));
        } else {
            chip.setTextColor(Color.parseColor("#334155"));
            chip.setBackground(roundRect(Color.WHITE, dp(99), Color.parseColor("#DCE3F0"), dp(1)));
        }
    }

    private void toggleSearchTypingBar(boolean show) {
        isSearchingMedia = show;
        mainHandler.removeCallbacks(cursorBlinkRunnable);
        if (show) {
            cursorBlinkVisible = true;
            mainHandler.postDelayed(cursorBlinkRunnable, 500);
        }
        if (searchKeysOverlay != null) {
            searchKeysOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (gridRow2 != null) {
            gridRow2.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (searchPillContainer != null) {
            searchPillContainer.setBackground(roundRect(
                    Color.WHITE,
                    dp(12),
                    Color.parseColor(show ? "#4F46E5" : "#DCE3F0"),
                    dp(show ? 2 : 1)
            ));
        }
        updateSearchDisplayText();
        if (show) {
            refreshDialogueSuggestionChips();
        }
    }

    private void onSearchTextEdited() {
        cursorBlinkVisible = true;
        mainHandler.removeCallbacks(cursorBlinkRunnable);
        if (isSearchingMedia) {
            mainHandler.postDelayed(cursorBlinkRunnable, 500);
        }
        updateSearchDisplayText();
        refreshDialogueSuggestionChips();

        if (liveSearchDebounceRunnable != null) {
            mainHandler.removeCallbacks(liveSearchDebounceRunnable);
        }
        final String typed = searchQueryBuilder.toString().trim();
        if (typed.length() >= 2) {
            liveSearchDebounceRunnable = () -> {
                activeCategoryQuery = typed;
                loadMediaGrid(typed, true);
            };
            mainHandler.postDelayed(liveSearchDebounceRunnable, 260);
        }
    }

    private void updateSearchDisplayText() {
        if (searchInputDisplay == null) return;
        if (clearSearchBtn != null) {
            clearSearchBtn.setVisibility(searchQueryBuilder.length() > 0 ? View.VISIBLE : View.GONE);
        }
        int cursorColor = cursorBlinkVisible ? Color.parseColor("#4F46E5") : Color.TRANSPARENT;

        if (!isSearchingMedia) {
            if (searchQueryBuilder.length() == 0) {
                searchInputDisplay.setText("Search KLIPY GIFs");
                searchInputDisplay.setTextColor(Color.parseColor("#64748B"));
            } else {
                searchInputDisplay.setText(searchQueryBuilder.toString());
                searchInputDisplay.setTextColor(Color.parseColor("#0F172A"));
            }
            return;
        }

        // Active Search Mode: Render Blinking Cursor "|" with official "Search KLIPY" attribution
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (searchQueryBuilder.length() == 0) {
            String cursorStr = "|";
            String hintStr = " Search KLIPY (enna koduma)...";
            ssb.append(cursorStr).append(hintStr);
            ssb.setSpan(new ForegroundColorSpan(cursorColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(Color.parseColor("#94A3B8")), 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            searchInputDisplay.setText(ssb);
        } else {
            String typed = searchQueryBuilder.toString();
            ssb.append(typed).append("|");
            ssb.setSpan(new ForegroundColorSpan(Color.parseColor("#0F172A")), 0, typed.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(cursorColor), typed.length(), ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), typed.length(), ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            searchInputDisplay.setText(ssb);
        }
    }

    private void refreshDialogueSuggestionChips() {
        if (dialogueSuggestionsRow == null) return;
        dialogueSuggestionsRow.removeAllViews();
        Context ctx = this;
        List<String> suggestions = OfficialMediaRepository.getDialogueSuggestions(searchQueryBuilder.toString());

        for (final String suggestion : suggestions) {
            TextView chip = new TextView(ctx);
            chip.setText(suggestion);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setTextColor(Color.parseColor("#4F46E5"));
            chip.setPadding(dp(11), dp(5), dp(11), dp(5));
            chip.setBackground(roundRect(Color.parseColor("#EEF2FF"), dp(99), Color.parseColor("#C7D2FE"), dp(1)));
            chip.setOnClickListener(v -> {
                searchQueryBuilder.setLength(0);
                searchQueryBuilder.append(suggestion);
                updateSearchDisplayText();
                activeCategoryQuery = suggestion;
                toggleSearchTypingBar(false);
                loadMediaGrid(suggestion, true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(dp(6));
            dialogueSuggestionsRow.addView(chip, lp);
        }
    }

    private LinearLayout buildSearchTypingStrip(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, 0);

        // Live Dialogue Autocomplete Strip
        HorizontalScrollView sugScroll = new HorizontalScrollView(ctx);
        sugScroll.setHorizontalScrollBarEnabled(false);
        dialogueSuggestionsRow = new LinearLayout(ctx);
        dialogueSuggestionsRow.setOrientation(LinearLayout.HORIZONTAL);
        dialogueSuggestionsRow.setPadding(dp(1), 0, dp(1), dp(6));
        sugScroll.addView(dialogueSuggestionsRow);
        box.addView(sugScroll);

        // Dynamic 3-row container (switches between Letters [a-z / A-Z], ?123 Punctuation, and =\< Extended Symbols)
        searchDynamicKeysContainer = new LinearLayout(ctx);
        searchDynamicKeysContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(searchDynamicKeysContainer);

        // Bottom Search Bar: [?123 / ABC]  [,]  [     space     ]  [.]  [Done]
        LinearLayout bottomRow = new LinearLayout(ctx);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0, dp(3), 0, 0);

        searchModeToggleKey = createKeycap(ctx, "?123", true);
        searchModeToggleKey.setOnClickListener(v -> {
            searchSymbolsPage = (searchSymbolsPage == 0) ? 1 : 0;
            searchModeToggleKey.setText(searchSymbolsPage == 0 ? "?123" : "ABC");
            rebuildSearchDynamicKeyRows(ctx);
        });
        LinearLayout.LayoutParams symLp = new LinearLayout.LayoutParams(dp(50), dp(36));
        symLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(searchModeToggleKey, symLp);

        TextView commaKey = createKeycap(ctx, ",", false);
        commaKey.setOnClickListener(v -> {
            searchQueryBuilder.append(',');
            onSearchTextEdited();
        });
        LinearLayout.LayoutParams cmLp = new LinearLayout.LayoutParams(dp(32), dp(36));
        cmLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(commaKey, cmLp);

        TextView spaceKey = createKeycap(ctx, "space", false);
        spaceKey.setOnClickListener(v -> {
            searchQueryBuilder.append(' ');
            onSearchTextEdited();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        slp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(spaceKey, slp);

        TextView dotKey = createKeycap(ctx, ".", false);
        dotKey.setOnClickListener(v -> {
            searchQueryBuilder.append('.');
            onSearchTextEdited();
        });
        bottomRow.addView(dotKey, cmLp);

        TextView doneKey = createKeycap(ctx, "Done", true);
        doneKey.setTextColor(Color.WHITE);
        doneKey.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(10), Color.parseColor("#4338CA"), dp(1)));
        doneKey.setOnClickListener(v -> toggleSearchTypingBar(false));
        LinearLayout.LayoutParams dnlp = new LinearLayout.LayoutParams(dp(64), dp(36));
        dnlp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(doneKey, dnlp);

        box.addView(bottomRow);
        rebuildSearchDynamicKeyRows(ctx);
        return box;
    }

    private void rebuildSearchDynamicKeyRows(Context ctx) {
        if (searchDynamicKeysContainer == null) return;
        searchDynamicKeysContainer.removeAllViews();

        String[] rows;
        if (searchSymbolsPage == 0) {
            rows = new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        } else if (searchSymbolsPage == 1) {
            rows = new String[]{"1234567890", "@#$_&-+()/", "*\"':;!?"};
        } else {
            rows = new String[]{"~`|•√π÷×§∆", "£¢€¥^°={}\\", "%©®™✓[]"};
        }

        for (int r = 0; r < rows.length; r++) {
            String rowChars = rows[r];
            LinearLayout keyRow = new LinearLayout(ctx);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER);
            keyRow.setPadding(0, dp(2), 0, dp(2));

            if (r == 1 && searchSymbolsPage == 0) {
                keyRow.setPadding(dp(14), dp(2), dp(14), dp(2));
            }

            // Left Special Key on Row 2 (Shift "⇧" for Capital conversion, or "=\<" / "?123" in punctuation mode)
            if (r == 2) {
                if (searchSymbolsPage == 0) {
                    TextView shiftKey = createKeycap(ctx, "⇧", true);
                    if (searchShifted) {
                        shiftKey.setTextColor(Color.WHITE);
                        shiftKey.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(8), Color.parseColor("#4338CA"), dp(1)));
                    }
                    shiftKey.setOnClickListener(v -> {
                        searchShifted = !searchShifted;
                        rebuildSearchDynamicKeyRows(ctx);
                    });
                    LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(dp(42), dp(36));
                    shLp.setMargins(dp(2), 0, dp(2), 0);
                    keyRow.addView(shiftKey, shLp);
                } else {
                    String secLabel = (searchSymbolsPage == 1) ? "=\\<" : "?123";
                    TextView secSymKey = createKeycap(ctx, secLabel, true);
                    secSymKey.setOnClickListener(v -> {
                        searchSymbolsPage = (searchSymbolsPage == 1) ? 2 : 1;
                        rebuildSearchDynamicKeyRows(ctx);
                    });
                    LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(dp(44), dp(36));
                    shLp.setMargins(dp(2), 0, dp(2), 0);
                    keyRow.addView(secSymKey, shLp);
                }
            }

            for (int i = 0; i < rowChars.length(); i++) {
                final char rawChar = rowChars.charAt(i);
                final String keyText = (searchSymbolsPage == 0 && searchShifted)
                        ? String.valueOf(Character.toUpperCase(rawChar))
                        : String.valueOf(rawChar);
                TextView key = createKeycap(ctx, keyText, false);
                key.setOnClickListener(v -> {
                    searchQueryBuilder.append(keyText);
                    onSearchTextEdited();
                });
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, dp(36), 1f);
                klp.setMargins(dp(2), 0, dp(2), 0);
                keyRow.addView(key, klp);
            }

            if (r == 2) {
                TextView delKey = createKeycap(ctx, "⌫", true);
                delKey.setOnClickListener(v -> {
                    if (searchQueryBuilder.length() > 0) {
                        searchQueryBuilder.deleteCharAt(searchQueryBuilder.length() - 1);
                        onSearchTextEdited();
                    }
                });
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(42), dp(36));
                dlp.setMargins(dp(2), 0, dp(2), 0);
                keyRow.addView(delKey, dlp);
            }
            searchDynamicKeysContainer.addView(keyRow);
        }
    }

    private LinearLayout buildFullAbcChatKeyboard(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(2), 0, dp(2));

        chatDynamicKeysContainer = new LinearLayout(ctx);
        chatDynamicKeysContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(chatDynamicKeysContainer);

        // Bottom Row: [?123/ABC] [SwitchKB] [GIFs] [,] [   VibeKey   ] [.] [Send]
        LinearLayout bottomRow = new LinearLayout(ctx);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0, dp(4), 0, 0);

        chatModeToggleKey = createKeycap(ctx, "?123", true);
        chatModeToggleKey.setOnClickListener(v -> {
            chatSymbolsPage = (chatSymbolsPage == 0) ? 1 : 0;
            chatModeToggleKey.setText(chatSymbolsPage == 0 ? "?123" : "ABC");
            rebuildChatDynamicKeyRows(ctx);
        });
        LinearLayout.LayoutParams symLp = new LinearLayout.LayoutParams(dp(46), dp(39));
        symLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(chatModeToggleKey, symLp);

        // 1-Tap Switch Keyboard key with Keyboard+Swap icon
        FrameLayout qwertySwitchKbBtn = new FrameLayout(ctx);
        qwertySwitchKbBtn.setBackground(roundRect(Color.parseColor("#E2E8F4"), dp(8), Color.parseColor("#CFD8E8"), dp(1)));
        VectorIconView qwertySwitchIcon = new VectorIconView(ctx, VectorIconView.ICON_KEYBOARD_SWITCH, Color.parseColor("#4F46E5"));
        qwertySwitchKbBtn.addView(qwertySwitchIcon, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        qwertySwitchKbBtn.setOnClickListener(v -> switchToSystemKeyboard());
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(36), dp(39));
        swLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(qwertySwitchKbBtn, swLp);

        TextView toGifsBtn = createKeycap(ctx, "GIFs", true);
        toGifsBtn.setTextColor(Color.parseColor("#4F46E5"));
        toGifsBtn.setOnClickListener(v -> switchMode(lastMediaMode));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(dp(46), dp(39));
        stLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(toGifsBtn, stLp);

        TextView commaBtn = createKeycap(ctx, ",", false);
        commaBtn.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(",", 1);
        });
        LinearLayout.LayoutParams cmLp = new LinearLayout.LayoutParams(dp(30), dp(39));
        cmLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(commaBtn, cmLp);

        TextView spaceBar = createKeycap(ctx, "VibeKey", false);
        spaceBar.setTextColor(Color.parseColor("#64748B"));
        spaceBar.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(" ", 1);
        });
        spaceBar.setOnLongClickListener(v -> {
            switchToSystemKeyboard();
            return true;
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, dp(39), 1f);
        spLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(spaceBar, spLp);

        TextView dotBtn = createKeycap(ctx, ".", false);
        dotBtn.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(".", 1);
        });
        bottomRow.addView(dotBtn, cmLp);

        TextView enterBtn = createKeycap(ctx, "Send", true);
        enterBtn.setTextColor(Color.WHITE);
        enterBtn.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(10), Color.parseColor("#4338CA"), dp(1)));
        enterBtn.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                EditorInfo ei = getCurrentInputEditorInfo();
                int action = (ei != null) ? (ei.imeOptions & EditorInfo.IME_MASK_ACTION) : EditorInfo.IME_ACTION_NONE;
                if (action != EditorInfo.IME_ACTION_NONE) {
                    ic.performEditorAction(action);
                } else {
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                }
            }
        });
        LinearLayout.LayoutParams enLp = new LinearLayout.LayoutParams(dp(54), dp(39));
        enLp.setMargins(dp(2), 0, dp(2), 0);
        bottomRow.addView(enterBtn, enLp);

        box.addView(bottomRow);
        rebuildChatDynamicKeyRows(ctx);
        return box;
    }

    private void rebuildChatDynamicKeyRows(Context ctx) {
        if (chatDynamicKeysContainer == null) return;
        chatDynamicKeysContainer.removeAllViews();
        letterKeyViews.clear();

        String[] rows;
        if (chatSymbolsPage == 0) {
            rows = new String[]{"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
        } else if (chatSymbolsPage == 1) {
            rows = new String[]{"1234567890", "@#$_&-+()/", "=*\"':;!?~`", "|•√π÷×§"};
        } else {
            rows = new String[]{"1234567890", "£¢€¥^°={}\\", "%©®™✓[]<>", "¡¿±µ¶∆"};
        }

        for (int r = 0; r < rows.length; r++) {
            String rowChars = rows[r];
            LinearLayout keyRow = new LinearLayout(ctx);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            keyRow.setGravity(Gravity.CENTER);
            keyRow.setPadding(0, dp(2), 0, dp(2));

            if (r == 2 && chatSymbolsPage == 0) {
                keyRow.setPadding(dp(12), dp(2), dp(12), dp(2));
            }

            if (r == 3) {
                if (chatSymbolsPage == 0) {
                    TextView shiftKey = createKeycap(ctx, "⇧", true);
                    if (isShifted) {
                        shiftKey.setTextColor(Color.WHITE);
                        shiftKey.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(8), Color.parseColor("#4338CA"), dp(1)));
                    }
                    shiftKey.setOnClickListener(v -> {
                        isShifted = !isShifted;
                        rebuildChatDynamicKeyRows(ctx);
                    });
                    LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(dp(42), dp(39));
                    shLp.setMargins(dp(2), 0, dp(2), 0);
                    keyRow.addView(shiftKey, shLp);
                } else {
                    String secLabel = (chatSymbolsPage == 1) ? "=\\<" : "?123";
                    TextView secSymKey = createKeycap(ctx, secLabel, true);
                    secSymKey.setOnClickListener(v -> {
                        chatSymbolsPage = (chatSymbolsPage == 1) ? 2 : 1;
                        rebuildChatDynamicKeyRows(ctx);
                    });
                    LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(dp(44), dp(39));
                    shLp.setMargins(dp(2), 0, dp(2), 0);
                    keyRow.addView(secSymKey, shLp);
                }
            }

            for (int i = 0; i < rowChars.length(); i++) {
                final char c = rowChars.charAt(i);
                final String keyText = (chatSymbolsPage == 0 && Character.isLetter(c) && isShifted)
                        ? String.valueOf(Character.toUpperCase(c))
                        : String.valueOf(c);
                final TextView key = createKeycap(ctx, keyText, false);
                if (Character.isLetter(c)) {
                    letterKeyViews.add(key);
                }
                key.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(keyText, 1);
                    }
                });
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, dp(39), 1f);
                klp.setMargins(dp(2), 0, dp(2), 0);
                keyRow.addView(key, klp);
            }

            if (r == 3) {
                TextView backKey = createKeycap(ctx, "⌫", true);
                backKey.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.deleteSurroundingText(1, 0);
                    }
                });
                LinearLayout.LayoutParams bkLp = new LinearLayout.LayoutParams(dp(44), dp(39));
                bkLp.setMargins(dp(2), 0, dp(2), 0);
                keyRow.addView(backKey, bkLp);
            }

            chatDynamicKeysContainer.addView(keyRow);
        }
    }

    private TextView createKeycap(Context ctx, String label, boolean special) {
        TextView key = new TextView(ctx);
        key.setText(label);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, special ? 12 : 15);
        key.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        key.setTextColor(Color.parseColor("#0F172A"));
        key.setGravity(Gravity.CENTER);
        key.setBackground(roundRect(
                Color.parseColor(special ? "#E2E8F4" : "#FFFFFF"),
                dp(8),
                Color.parseColor("#CFD8E8"),
                dp(1)
        ));
        return key;
    }

    private void loadMediaGrid(final String query, final boolean useWebSearch) {
        final int gen = searchGeneration.incrementAndGet();
        if (loadingSpinner != null) loadingSpinner.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            final List<OfficialMediaRepository.MediaItem> items;
            if (useWebSearch) {
                items = OfficialMediaRepository.searchMediaForKeyboard(
                        CinePulseInputMethodService.this,
                        query,
                        currentMode == MODE_STICKER
                );
            } else {
                items = OfficialMediaRepository.getBuiltInItems(query);
            }

            if (gen != searchGeneration.get()) return;

            mainHandler.post(() -> {
                if (gen != searchGeneration.get()) return;
                if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
                renderTwoRowGridStreaming(items, gen);
            });
        });
    }

    /**
     * Renders clean, edge-to-edge rounded movie GIF/Sticker tiles and automatically removes
     * any re-uploaded visual duplicates so every tile in the grid is 100% unique.
     */
    private void renderTwoRowGridStreaming(List<OfficialMediaRepository.MediaItem> items, final int gen) {
        if (gridRow1 == null || gridRow2 == null) return;
        gridRow1.removeAllViews();
        gridRow2.removeAllViews();
        Context ctx = this;
        final java.util.Set<String> seenVisualSignatures = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

        for (int i = 0; i < items.size(); i++) {
            final OfficialMediaRepository.MediaItem item = items.get(i);

            final FrameLayout tile = new FrameLayout(ctx);
            tile.setBackground(roundRect(Color.WHITE, dp(14), Color.parseColor("#DCE3F0"), dp(1)));
            tile.setPadding(dp(2), dp(2), dp(2), dp(2));
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(106), dp(96));
            tileLp.setMarginEnd(dp(6));
            tile.setLayoutParams(tileLp);

            final AnimatedGifTileView animView = new AnimatedGifTileView(ctx, dp(12));
            tile.addView(animView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 1-Touch Instant Send to Chat!
            tile.setOnClickListener(v -> dispatchMediaItemToChat(item));

            if (isSearchingMedia) {
                gridRow1.addView(tile);
            } else {
                if (i % 2 == 0) {
                    gridRow1.addView(tile);
                } else {
                    gridRow2.addView(tile);
                }
            }

            executor.execute(() -> {
                if (gen != searchGeneration.get()) return;
                final byte[] raw = OfficialMediaRepository.loadItemRawBytes(CinePulseInputMethodService.this, item);
                final Bitmap bmp = OfficialMediaRepository.loadItemPreviewBitmap(CinePulseInputMethodService.this, item);
                final String sig = computeVisualSignature(bmp, raw);
                final boolean isDuplicate = (sig != null && !seenVisualSignatures.add(sig));
                mainHandler.post(() -> {
                    if (gen != searchGeneration.get()) return;
                    if (isDuplicate) {
                        tile.setVisibility(View.GONE);
                    } else {
                        animView.setMediaContent(raw, bmp);
                    }
                });
            });
        }
    }

    /**
     * Computes a fast 3x3 center-pixel perceptual color fingerprint so two Tenor re-uploads
     * of the exact same movie clip (even with different IDs/filenames) are caught as duplicates.
     */
    private static String computeVisualSignature(Bitmap bmp, byte[] raw) {
        if (bmp != null && bmp.getWidth() >= 8 && bmp.getHeight() >= 8) {
            StringBuilder sb = new StringBuilder(32);
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            for (int gy = 1; gy <= 3; gy++) {
                for (int gx = 1; gx <= 3; gx++) {
                    int px = bmp.getPixel((w * gx) / 4, (h * gy) / 4);
                    int r = (Color.red(px) >> 5) & 0x7;
                    int g = (Color.green(px) >> 5) & 0x7;
                    int b = (Color.blue(px) >> 5) & 0x7;
                    sb.append(r).append(g).append(b);
                }
            }
            return sb.toString();
        }
        if (raw != null && raw.length > 0) {
            return "len_" + raw.length;
        }
        return null;
    }

    /**
     * Crisp Canvas Vector Icons:
     * - ICON_SEARCH: Search Loupe
     * - ICON_KEYBOARD_SWITCH: Mini Keyboard with Keycaps + Swap Arrows (unmistakable "Switch Keyboard" icon)
     */
    private static class VectorIconView extends View {
        static final int ICON_SEARCH = 1;
        static final int ICON_KEYBOARD_SWITCH = 2;

        private final int iconType;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path arrowPath = new Path();

        VectorIconView(Context context, int iconType, int color) {
            super(context);
            this.iconType = iconType;
            float dp1 = context.getResources().getDisplayMetrics().density;
            strokePaint.setColor(color);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp1 * 1.7f);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint.setColor(color);
            fillPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (iconType == ICON_SEARCH) {
                float r = Math.min(w, h) * 0.34f;
                float cx = w * 0.42f;
                float cy = h * 0.42f;
                canvas.drawCircle(cx, cy, r, strokePaint);
                canvas.drawLine(cx + r * 0.72f, cy + r * 0.72f, w * 0.86f, h * 0.86f, strokePaint);
            } else if (iconType == ICON_KEYBOARD_SWITCH) {
                // 1. Top bidirectional swap arrow (⇄) indicating "Switch"
                float topY = h * 0.20f;
                float leftX = w * 0.22f;
                float rightX = w * 0.78f;
                float head = Math.min(w, h) * 0.11f;
                canvas.drawLine(leftX, topY, rightX, topY, strokePaint);
                // Left arrowhead
                canvas.drawLine(leftX, topY, leftX + head, topY - head * 0.8f, strokePaint);
                canvas.drawLine(leftX, topY, leftX + head, topY + head * 0.8f, strokePaint);
                // Right arrowhead
                canvas.drawLine(rightX, topY, rightX - head, topY - head * 0.8f, strokePaint);
                canvas.drawLine(rightX, topY, rightX - head, topY + head * 0.8f, strokePaint);

                // 2. Crisp Mini Keyboard Body below the swap arrow
                float kbLeft = w * 0.10f;
                float kbTop = h * 0.40f;
                float kbRight = w * 0.90f;
                float kbBottom = h * 0.90f;
                float corner = Math.min(w, h) * 0.10f;
                rect.set(kbLeft, kbTop, kbRight, kbBottom);
                canvas.drawRoundRect(rect, corner, corner, strokePaint);

                // 3. Keycap dots + bottom spacebar inside the mini keyboard
                float dotR = Math.min(w, h) * 0.048f;
                float row1Y = kbTop + (kbBottom - kbTop) * 0.33f;
                canvas.drawCircle(w * 0.28f, row1Y, dotR, fillPaint);
                canvas.drawCircle(w * 0.50f, row1Y, dotR, fillPaint);
                canvas.drawCircle(w * 0.72f, row1Y, dotR, fillPaint);

                float barY = kbTop + (kbBottom - kbTop) * 0.70f;
                canvas.drawLine(w * 0.30f, barY, w * 0.70f, barY, strokePaint);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static class AnimatedGifTileView extends View {
        private final float cornerRadius;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path clipPath = new Path();
        private final RectF rectF = new RectF();
        private Movie gifMovie;
        private Bitmap fallbackBitmap;
        private long movieStart;

        public AnimatedGifTileView(Context context, float cornerRadius) {
            super(context);
            this.cornerRadius = cornerRadius;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setMediaContent(byte[] rawBytes, Bitmap bmp) {
            this.fallbackBitmap = bmp;
            if (rawBytes != null && rawBytes.length > 4
                    && rawBytes[0] == 'G' && rawBytes[1] == 'I' && rawBytes[2] == 'F') {
                try {
                    Movie m = Movie.decodeByteArray(rawBytes, 0, rawBytes.length);
                    if (m != null && m.width() > 0 && m.height() > 0) {
                        this.gifMovie = m;
                    }
                } catch (Exception ignored) {
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            rectF.set(0, 0, w, h);
            clipPath.reset();
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawColor(Color.parseColor("#F8FAFC"));

            if (gifMovie != null) {
                long now = SystemClock.uptimeMillis();
                if (movieStart == 0) movieStart = now;
                int dur = gifMovie.duration();
                if (dur <= 0) dur = 1000;
                int relTime = (int) ((now - movieStart) % dur);
                gifMovie.setTime(relTime);

                float sx = (float) w / gifMovie.width();
                float sy = (float) h / gifMovie.height();
                float scale = Math.max(sx, sy);
                float dx = (w - gifMovie.width() * scale) / 2f;
                float dy = (h - gifMovie.height() * scale) / 2f;

                canvas.save();
                canvas.translate(dx, dy);
                canvas.scale(scale, scale);
                gifMovie.draw(canvas, 0, 0);
                canvas.restore();

                postInvalidateDelayed(66);
            } else if (fallbackBitmap != null && !fallbackBitmap.isRecycled()) {
                canvas.drawBitmap(fallbackBitmap, null, rectF, paint);
            }
            canvas.restore();
        }
    }

    private void dispatchMediaItemToChat(final OfficialMediaRepository.MediaItem item) {
        if (loadingSpinner != null) loadingSpinner.setVisibility(View.VISIBLE);

        final EditorInfo editorInfo = getCurrentInputEditorInfo();
        final String hostPkg = (editorInfo != null && editorInfo.packageName != null)
                ? editorInfo.packageName.toLowerCase()
                : "";
        final boolean isInstagramHost = hostPkg.contains("instagram");
        boolean hostSupportsWaSticker = false;
        boolean hostSupportsWebp = false;
        boolean hostSupportsGif = false;
        if (editorInfo != null) {
            String[] mimes = EditorInfoCompat.getContentMimeTypes(editorInfo);
            for (String m : mimes) {
                if (m == null) continue;
                String low = m.toLowerCase();
                if ("image/webp.wasticker".equals(low)) hostSupportsWaSticker = true;
                if ("image/webp".equals(low) || "image/*".equals(low)) hostSupportsWebp = true;
                if ("image/gif".equals(low) || "image/*".equals(low)) hostSupportsGif = true;
            }
        }
        final boolean useWaStickerWebp = hostSupportsWaSticker || (!isInstagramHost && hostSupportsWebp);
        final boolean preferAnimatedStickerForHost = isInstagramHost || (!useWaStickerWebp && hostSupportsGif);

        executor.execute(() -> {
            try {
                final File outFile;
                final String mimeType;
                if (ENABLE_AUDIO_GIF_MODE && currentMode == MODE_AUDIO_GIF) {
                    outFile = AudioGifCompiler.buildAudioGifMp4File(CinePulseInputMethodService.this, item);
                    mimeType = "video/mp4";
                } else if (currentMode == MODE_STICKER) {
                    if (useWaStickerWebp) {
                        outFile = AudioGifCompiler.buildStickerWebpFile(CinePulseInputMethodService.this, item);
                        mimeType = "image/webp.wasticker";
                    } else if (preferAnimatedStickerForHost) {
                        // Instagram DMs & Stories natively accept animated transparent GIFs or PNG stickers
                        File gifOrPng = AudioGifCompiler.buildGifOrPngFile(CinePulseInputMethodService.this, item);
                        if (gifOrPng.getName().endsWith(".gif")) {
                            outFile = gifOrPng;
                            mimeType = "image/gif";
                        } else {
                            outFile = AudioGifCompiler.buildStickerPngFile(CinePulseInputMethodService.this, item);
                            mimeType = "image/png";
                        }
                    } else {
                        outFile = AudioGifCompiler.buildStickerPngFile(CinePulseInputMethodService.this, item);
                        mimeType = "image/png";
                    }
                } else {
                    outFile = AudioGifCompiler.buildGifOrPngFile(CinePulseInputMethodService.this, item);
                    mimeType = outFile.getName().endsWith(".gif") ? "image/gif" : "image/png";
                }

                final Uri contentUri = FileProvider.getUriForFile(
                        CinePulseInputMethodService.this,
                        getPackageName() + ".fileprovider",
                        outFile
                );

                mainHandler.post(() -> {
                    if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
                    deliverUriToHostApp(contentUri, mimeType, item.title);
                });
            } catch (final Exception e) {
                mainHandler.post(() -> {
                    if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
                    Toast.makeText(CinePulseInputMethodService.this, "Could not send: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void deliverUriToHostApp(Uri contentUri, String mimeType, String description) {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        String targetPkg = editorInfo != null ? editorInfo.packageName : null;

        if (targetPkg != null && !targetPkg.isEmpty()) {
            try {
                grantUriPermission(targetPkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }

        // Also copy rich URI to system clipboard so Instagram / any app supports instant paste if needed
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newUri(getContentResolver(), description, contentUri);
                clipboard.setPrimaryClip(clip);
            }
        } catch (Exception ignored) {
        }

        boolean supportsWaStickerMime = false;
        if (editorInfo != null) {
            String[] supportedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo);
            for (String supported : supportedMimes) {
                if ("image/webp.wasticker".equalsIgnoreCase(supported)) {
                    supportsWaStickerMime = true;
                    break;
                }
            }
        }

        if (ic != null && editorInfo != null) {
            try {
                String[] commitMimes;
                if ("image/webp.wasticker".equals(mimeType)) {
                    commitMimes = supportsWaStickerMime
                            ? new String[]{"image/webp.wasticker", "image/webp"}
                            : new String[]{"image/webp", "image/png"};
                } else {
                    commitMimes = new String[]{mimeType};
                }

                InputContentInfoCompat contentInfo = new InputContentInfoCompat(
                        contentUri,
                        new ClipDescription(description, commitMimes),
                        null
                );
                int flags = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
                }
                boolean committed = InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null);
                if (committed) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/webp.wasticker".equals(mimeType) ? "image/png" : mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (targetPkg != null && !targetPkg.equals(getPackageName())) {
            shareIntent.setPackage(targetPkg);
        }
        try {
            startActivity(shareIntent);
        } catch (Exception ignored) {
        }
    }

    private void switchToSystemKeyboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (switchToPreviousInputMethod()) {
                    return;
                }
                if (switchToNextInputMethod(false)) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private GradientDrawable roundRect(int fillColor, int radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        return gd;
    }

    private int dp(int dpVal) {
        return Math.round(dpVal * getResources().getDisplayMetrics().density);
    }
}
