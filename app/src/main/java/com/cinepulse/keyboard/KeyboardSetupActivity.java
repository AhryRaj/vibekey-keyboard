package com.cinepulse.keyboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.List;

/**
 * KeyboardSetupActivity
 * Modern Flagship Guided Onboarding for VibeKey — GIF & Sticker Keyboard.
 * Features an auto-playing 60fps 3-Scene Interactive Motion Video Showcase,
 * live progress tracker, illustrated step cards, and visual keyboard-switching diagrams.
 */
public class KeyboardSetupActivity extends AppCompatActivity {

    private TextView step1Badge;
    private TextView step2Badge;
    private TextView step1Btn;
    private TextView step2Btn;
    private TextView progressSummaryText;
    private View progressFillView;
    private FrameLayout progressTrackView;
    private OnboardingMotionPlayerView motionPlayerView;
    private final TextView[] sceneTabs = new TextView[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F1F5F9"));
        scroll.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(container);

        // ====================================================================
        // 1. COMPACT BRAND HEADER + LIVE Guided Video Showcase
        // ====================================================================
        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setBackground(roundRect(Color.parseColor("#0F172A"), dp(24), Color.parseColor("#1E293B"), dp(1)));
        heroCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        heroLp.bottomMargin = dp(16);
        heroCard.setLayoutParams(heroLp);

        // Brand Top Row
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brandTitle = new TextView(this);
        brandTitle.setText("VibeKey");
        brandTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        brandTitle.setTypeface(Typeface.DEFAULT_BOLD);
        brandTitle.setTextColor(Color.WHITE);
        brandRow.addView(brandTitle);

        TextView brandSubPill = new TextView(this);
        brandSubPill.setText("GIF & STICKER KEYBOARD");
        brandSubPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        brandSubPill.setTypeface(Typeface.DEFAULT_BOLD);
        brandSubPill.setTextColor(Color.parseColor("#A5B4FC"));
        brandSubPill.setPadding(dp(9), dp(3), dp(9), dp(3));
        brandSubPill.setBackground(roundRect(Color.parseColor("#1E1B4B"), dp(99), Color.parseColor("#4338CA"), dp(1)));
        LinearLayout.LayoutParams subPillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subPillLp.setMarginStart(dp(10));
        brandRow.addView(brandSubPill, subPillLp);
        heroCard.addView(brandRow);

        TextView heroTagline = new TextView(this);
        heroTagline.setText("Watch the 3-step visual guide below or tap any scene to preview:");
        heroTagline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heroTagline.setTextColor(Color.parseColor("#94A3B8"));
        heroTagline.setPadding(0, dp(4), 0, dp(12));
        heroCard.addView(heroTagline);

        // Auto-Playing 60fps Visual Motion Video Player
        motionPlayerView = new OnboardingMotionPlayerView(this, sceneIndex -> updateSceneTabStyles(sceneIndex));
        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(236)
        );
        heroCard.addView(motionPlayerView, playerLp);

        // Interactive Scene Switcher Pills below the video stage
        LinearLayout sceneTabsRow = new LinearLayout(this);
        sceneTabsRow.setOrientation(LinearLayout.HORIZONTAL);
        sceneTabsRow.setGravity(Gravity.CENTER);
        sceneTabsRow.setPadding(0, dp(12), 0, 0);

        String[] tabLabels = new String[]{
                "1. Enable Toggle",
                "2. Switch Icon",
                "3. 1-Tap Send"
        };
        for (int i = 0; i < 3; i++) {
            final int sceneIdx = i;
            TextView tab = new TextView(this);
            tab.setText(tabLabels[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(8), dp(7), dp(8), dp(7));
            tab.setOnClickListener(v -> {
                motionPlayerView.setScene(sceneIdx);
                updateSceneTabStyles(sceneIdx);
            });
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) tabLp.setMarginEnd(dp(6));
            sceneTabsRow.addView(tab, tabLp);
            sceneTabs[i] = tab;
        }
        updateSceneTabStyles(0);
        heroCard.addView(sceneTabsRow);

        container.addView(heroCard);

        // ====================================================================
        // 2. GUIDED SETUP PROGRESS BAR CARD
        // ====================================================================
        LinearLayout progressCard = createElevatedCard();
        progressCard.setPadding(dp(16), dp(12), dp(16), dp(14));

        LinearLayout progRow = new LinearLayout(this);
        progRow.setOrientation(LinearLayout.HORIZONTAL);
        progRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView progLabel = new TextView(this);
        progLabel.setText("Guided Setup");
        progLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        progLabel.setTypeface(Typeface.DEFAULT_BOLD);
        progLabel.setTextColor(Color.parseColor("#0F172A"));
        progRow.addView(progLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progressSummaryText = new TextView(this);
        progressSummaryText.setText("0 of 2 Completed");
        progressSummaryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        progressSummaryText.setTypeface(Typeface.DEFAULT_BOLD);
        progressSummaryText.setTextColor(Color.parseColor("#4F46E5"));
        progRow.addView(progressSummaryText);
        progressCard.addView(progRow);

        progressTrackView = new FrameLayout(this);
        progressTrackView.setBackground(roundRect(Color.parseColor("#E2E8F0"), dp(99), 0, 0));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        trackLp.topMargin = dp(8);
        progressFillView = new View(this);
        progressFillView.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(99), 0, 0));
        progressTrackView.addView(progressFillView, new FrameLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.MATCH_PARENT));
        progressCard.addView(progressTrackView, trackLp);

        container.addView(progressCard);

        // ====================================================================
        // 3. GUIDED STEP 1 CARD (WITH ILLUSTRATED TOGGLE GRAPHIC)
        // ====================================================================
        LinearLayout step1Card = createElevatedCard();

        LinearLayout s1Top = new LinearLayout(this);
        s1Top.setOrientation(LinearLayout.HORIZONTAL);
        s1Top.setGravity(Gravity.CENTER_VERTICAL);

        StepIllustrationView s1Illust = new StepIllustrationView(this, StepIllustrationView.TYPE_TOGGLE);
        LinearLayout.LayoutParams illLp1 = new LinearLayout.LayoutParams(dp(52), dp(52));
        illLp1.setMarginEnd(dp(12));
        s1Top.addView(s1Illust, illLp1);

        LinearLayout s1TextCol = new LinearLayout(this);
        s1TextCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout s1TitleRow = new LinearLayout(this);
        s1TitleRow.setOrientation(LinearLayout.HORIZONTAL);
        s1TitleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView s1Title = new TextView(this);
        s1Title.setText("Step 1 • Enable VibeKey");
        s1Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s1Title.setTypeface(Typeface.DEFAULT_BOLD);
        s1Title.setTextColor(Color.parseColor("#0F172A"));
        s1TitleRow.addView(s1Title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        step1Badge = new TextView(this);
        step1Badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        step1Badge.setTypeface(Typeface.DEFAULT_BOLD);
        step1Badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        s1TitleRow.addView(step1Badge);
        s1TextCol.addView(s1TitleRow);

        TextView s1Desc = new TextView(this);
        s1Desc.setText("Turn ON 'VibeKey — GIF & Sticker Keyboard' in your phone's keyboard settings.");
        s1Desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s1Desc.setTextColor(Color.parseColor("#475569"));
        s1Desc.setPadding(0, dp(4), 0, 0);
        s1TextCol.addView(s1Desc);

        s1Top.addView(s1TextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        step1Card.addView(s1Top);

        step1Btn = createActionBtn("1. Open Keyboard Settings", true);
        LinearLayout.LayoutParams btn1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btn1Lp.topMargin = dp(12);
        step1Btn.setOnClickListener(v -> {
            motionPlayerView.setScene(0);
            updateSceneTabStyles(0);
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        step1Card.addView(step1Btn, btn1Lp);
        container.addView(step1Card);

        // ====================================================================
        // 4. GUIDED STEP 2 CARD (WITH ILLUSTRATED KEYBOARD SWITCHER GRAPHIC)
        // ====================================================================
        LinearLayout step2Card = createElevatedCard();

        LinearLayout s2Top = new LinearLayout(this);
        s2Top.setOrientation(LinearLayout.HORIZONTAL);
        s2Top.setGravity(Gravity.CENTER_VERTICAL);

        StepIllustrationView s2Illust = new StepIllustrationView(this, StepIllustrationView.TYPE_KEYBOARD_SWITCH);
        LinearLayout.LayoutParams illLp2 = new LinearLayout.LayoutParams(dp(52), dp(52));
        illLp2.setMarginEnd(dp(12));
        s2Top.addView(s2Illust, illLp2);

        LinearLayout s2TextCol = new LinearLayout(this);
        s2TextCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout s2TitleRow = new LinearLayout(this);
        s2TitleRow.setOrientation(LinearLayout.HORIZONTAL);
        s2TitleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView s2Title = new TextView(this);
        s2Title.setText("Step 2 • Switch to VibeKey");
        s2Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s2Title.setTypeface(Typeface.DEFAULT_BOLD);
        s2Title.setTextColor(Color.parseColor("#0F172A"));
        s2TitleRow.addView(s2Title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        step2Badge = new TextView(this);
        step2Badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        step2Badge.setTypeface(Typeface.DEFAULT_BOLD);
        step2Badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        s2TitleRow.addView(step2Badge);
        s2TextCol.addView(s2TitleRow);

        TextView s2Desc = new TextView(this);
        s2Desc.setText("Choose VibeKey as your active input method so it opens inside WhatsApp & Instagram.");
        s2Desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s2Desc.setTextColor(Color.parseColor("#475569"));
        s2Desc.setPadding(0, dp(4), 0, 0);
        s2TextCol.addView(s2Desc);

        s2Top.addView(s2TextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        step2Card.addView(s2Top);

        step2Btn = createActionBtn("2. Select VibeKey Keyboard", true);
        LinearLayout.LayoutParams btn2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btn2Lp.topMargin = dp(12);
        step2Btn.setOnClickListener(v -> {
            motionPlayerView.setScene(1);
            updateSceneTabStyles(1);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
        step2Card.addView(step2Btn, btn2Lp);
        container.addView(step2Card);

        // ====================================================================
        // 5. VISUAL QUICK-SWITCH GUIDE (2 ILLUSTRATED CARDS + OPEN WHATSAPP)
        // ====================================================================
        LinearLayout guideCard = createElevatedCard();
        TextView guideTitle = new TextView(this);
        guideTitle.setText("How to Switch Keyboards Anytime");
        guideTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        guideTitle.setTypeface(Typeface.DEFAULT_BOLD);
        guideTitle.setTextColor(Color.parseColor("#0F172A"));
        guideCard.addView(guideTitle);

        LinearLayout twoColGuide = new LinearLayout(this);
        twoColGuide.setOrientation(LinearLayout.HORIZONTAL);
        twoColGuide.setPadding(0, dp(10), 0, dp(12));

        // Left visual box: VibeKey -> Normal Keyboard
        LinearLayout boxA = new LinearLayout(this);
        boxA.setOrientation(LinearLayout.VERTICAL);
        boxA.setGravity(Gravity.CENTER_HORIZONTAL);
        boxA.setBackground(roundRect(Color.parseColor("#F8FAFC"), dp(14), Color.parseColor("#E2E8F0"), dp(1)));
        boxA.setPadding(dp(10), dp(10), dp(10), dp(10));
        StepIllustrationView iconA = new StepIllustrationView(this, StepIllustrationView.TYPE_KEYBOARD_SWITCH);
        boxA.addView(iconA, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView titleA = new TextView(this);
        titleA.setText("In VibeKey");
        titleA.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleA.setTypeface(Typeface.DEFAULT_BOLD);
        titleA.setTextColor(Color.parseColor("#0F172A"));
        titleA.setPadding(0, dp(6), 0, dp(2));
        boxA.addView(titleA);
        TextView descA = new TextView(this);
        descA.setText("Tap the top-right Switch Keyboard icon to return to SwiftKey/Gboard.");
        descA.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        descA.setTextColor(Color.parseColor("#475569"));
        descA.setGravity(Gravity.CENTER);
        boxA.addView(descA);

        // Right visual box: Normal Keyboard -> VibeKey
        LinearLayout boxB = new LinearLayout(this);
        boxB.setOrientation(LinearLayout.VERTICAL);
        boxB.setGravity(Gravity.CENTER_HORIZONTAL);
        boxB.setBackground(roundRect(Color.parseColor("#F8FAFC"), dp(14), Color.parseColor("#E2E8F0"), dp(1)));
        boxB.setPadding(dp(10), dp(10), dp(10), dp(10));
        StepIllustrationView iconB = new StepIllustrationView(this, StepIllustrationView.TYPE_NAVBAR_KB);
        boxB.addView(iconB, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView titleB = new TextView(this);
        titleB.setText("In SwiftKey / Gboard");
        titleB.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleB.setTypeface(Typeface.DEFAULT_BOLD);
        titleB.setTextColor(Color.parseColor("#0F172A"));
        titleB.setPadding(0, dp(6), 0, dp(2));
        boxB.addView(titleB);
        TextView descB = new TextView(this);
        descB.setText("Tap the small keyboard icon at the bottom-right corner of your screen.");
        descB.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        descB.setTextColor(Color.parseColor("#475569"));
        descB.setGravity(Gravity.CENTER);
        boxB.addView(descB);

        LinearLayout.LayoutParams colLpA = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        colLpA.setMarginEnd(dp(8));
        LinearLayout.LayoutParams colLpB = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        twoColGuide.addView(boxA, colLpA);
        twoColGuide.addView(boxB, colLpB);
        guideCard.addView(twoColGuide);

        // Quick Launch WhatsApp Button
        TextView openWhatsAppBtn = createActionBtn("Open WhatsApp & Start Sending", false);
        openWhatsAppBtn.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, "Open any chat app (WhatsApp, Instagram, Telegram) to use VibeKey!", Toast.LENGTH_SHORT).show();
            }
        });
        guideCard.addView(openWhatsAppBtn);

        container.addView(guideCard);

        setContentView(scroll);
        refreshKeyboardStatusBadges();
    }

    private void updateSceneTabStyles(int activeIdx) {
        for (int i = 0; i < sceneTabs.length; i++) {
            TextView tab = sceneTabs[i];
            if (tab == null) continue;
            boolean active = (i == activeIdx);
            tab.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#94A3B8"));
            tab.setBackground(roundRect(
                    Color.parseColor(active ? "#4F46E5" : "#1E293B"),
                    dp(10),
                    Color.parseColor(active ? "#818CF8" : "#334155"),
                    dp(1)
            ));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshKeyboardStatusBadges();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            refreshKeyboardStatusBadges();
        }
    }

    private void refreshKeyboardStatusBadges() {
        boolean isEnabled = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            for (InputMethodInfo info : list) {
                if (getPackageName().equals(info.getPackageName())) {
                    isEnabled = true;
                    break;
                }
            }
        }

        String defaultIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean isSelected = defaultIme != null && defaultIme.startsWith(getPackageName() + "/");

        int completedCount = (isEnabled ? 1 : 0) + (isSelected ? 1 : 0);
        if (progressSummaryText != null) {
            if (completedCount == 2) {
                progressSummaryText.setText("✓ All Set & Ready!");
                progressSummaryText.setTextColor(Color.parseColor("#059669"));
            } else {
                progressSummaryText.setText(completedCount + " of 2 Completed");
                progressSummaryText.setTextColor(Color.parseColor("#4F46E5"));
            }
        }

        if (progressTrackView != null && progressFillView != null) {
            progressTrackView.post(() -> {
                int totalW = progressTrackView.getWidth();
                int targetW = (completedCount == 0) ? dp(24) : (completedCount == 1 ? totalW / 2 : totalW);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetW, ViewGroup.LayoutParams.MATCH_PARENT);
                progressFillView.setLayoutParams(lp);
                progressFillView.setBackground(roundRect(
                        Color.parseColor(completedCount == 2 ? "#10B981" : "#4F46E5"),
                        dp(99), 0, 0
                ));
            });
        }

        if (step1Badge != null) {
            step1Badge.setText(isEnabled ? "✓ Enabled" : "Step 1");
            step1Badge.setTextColor(Color.parseColor(isEnabled ? "#059669" : "#4F46E5"));
            step1Badge.setBackground(roundRect(
                    Color.parseColor(isEnabled ? "#ECFDF5" : "#EEF2FF"),
                    dp(99),
                    Color.parseColor(isEnabled ? "#6EE7B7" : "#C7D2FE"),
                    dp(1)
            ));
        }

        if (step2Badge != null) {
            step2Badge.setText(isSelected ? "✓ Active" : "Step 2");
            step2Badge.setTextColor(Color.parseColor(isSelected ? "#059669" : "#4F46E5"));
            step2Badge.setBackground(roundRect(
                    Color.parseColor(isSelected ? "#ECFDF5" : "#EEF2FF"),
                    dp(99),
                    Color.parseColor(isSelected ? "#6EE7B7" : "#C7D2FE"),
                    dp(1)
            ));
        }

        // Automatically cue the video player to whichever step the user needs next!
        if (motionPlayerView != null) {
            int autoScene = !isEnabled ? 0 : (!isSelected ? 1 : 2);
            motionPlayerView.setScene(autoScene);
            updateSceneTabStyles(autoScene);
        }
    }

    private LinearLayout createElevatedCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(Color.WHITE, dp(20), Color.parseColor("#E2E8F0"), dp(1)));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView createActionBtn(String text, boolean primary) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(13), dp(16), dp(13));
        if (primary) {
            btn.setTextColor(Color.WHITE);
            btn.setBackground(roundRect(Color.parseColor("#4F46E5"), dp(14), Color.parseColor("#4338CA"), dp(1)));
        } else {
            btn.setTextColor(Color.parseColor("#0F172A"));
            btn.setBackground(roundRect(Color.parseColor("#EEF2FF"), dp(14), Color.parseColor("#C7D2FE"), dp(1)));
        }
        return btn;
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

    // ========================================================================
    // CUSTOM ILLUSTRATION BADGE VIEW FOR STEP CARDS & QUICK-SWITCH GUIDE
    // ========================================================================
    private static class StepIllustrationView extends View {
        static final int TYPE_TOGGLE = 1;
        static final int TYPE_KEYBOARD_SWITCH = 2;
        static final int TYPE_NAVBAR_KB = 3;

        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        StepIllustrationView(Context context, int type) {
            super(context);
            this.type = type;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float d = getResources().getDisplayMetrics().density;

            // Soft rounded background badge
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#EEF2FF"));
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, d * 14, d * 14, paint);

            if (type == TYPE_TOGGLE) {
                // Draw glowing Indigo Toggle Switch in ON position
                float tw = w * 0.64f;
                float th = h * 0.36f;
                float left = (w - tw) / 2f;
                float top = (h - th) / 2f;
                paint.setColor(Color.parseColor("#4F46E5"));
                rect.set(left, top, left + tw, top + th);
                canvas.drawRoundRect(rect, th / 2f, th / 2f, paint);

                paint.setColor(Color.WHITE);
                float knobR = (th * 0.76f) / 2f;
                canvas.drawCircle(left + tw - th / 2f, top + th / 2f, knobR, paint);
            } else if (type == TYPE_KEYBOARD_SWITCH) {
                // Draw the exact Mini Keyboard + Swap Arrows icon
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(d * 2.0f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(Color.parseColor("#4F46E5"));

                float topY = h * 0.28f;
                float lx = w * 0.28f;
                float rx = w * 0.72f;
                float head = w * 0.08f;
                canvas.drawLine(lx, topY, rx, topY, paint);
                canvas.drawLine(lx, topY, lx + head, topY - head, paint);
                canvas.drawLine(lx, topY, lx + head, topY + head, paint);
                canvas.drawLine(rx, topY, rx - head, topY - head, paint);
                canvas.drawLine(rx, topY, rx - head, topY + head, paint);

                rect.set(w * 0.20f, h * 0.44f, w * 0.80f, h * 0.78f);
                canvas.drawRoundRect(rect, d * 4, d * 4, paint);

                paint.setStyle(Paint.Style.FILL);
                float dotR = d * 1.8f;
                float ry = h * 0.56f;
                canvas.drawCircle(w * 0.35f, ry, dotR, paint);
                canvas.drawCircle(w * 0.50f, ry, dotR, paint);
                canvas.drawCircle(w * 0.65f, ry, dotR, paint);

                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(w * 0.36f, h * 0.69f, w * 0.64f, h * 0.69f, paint);
            } else if (type == TYPE_NAVBAR_KB) {
                // Draw Android bottom navigation bar with glowing bottom-right keyboard icon
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#1E293B"));
                rect.set(w * 0.14f, h * 0.30f, w * 0.86f, h * 0.70f);
                canvas.drawRoundRect(rect, d * 8, d * 8, paint);

                // Center home pill
                paint.setColor(Color.parseColor("#64748B"));
                rect.set(w * 0.34f, h * 0.47f, w * 0.54f, h * 0.53f);
                canvas.drawRoundRect(rect, d * 2, d * 2, paint);

                // Glowing circle highlight on bottom-right keyboard icon
                paint.setColor(Color.parseColor("#818CF8"));
                canvas.drawCircle(w * 0.71f, h * 0.50f, d * 7, paint);
            }
        }
    }

    // ========================================================================
    // 60FPS AUTO-PLAYING 3-SCENE MOTION VIDEO SHOWCASE PLAYER
    // ========================================================================
    private static class OnboardingMotionPlayerView extends View {
        interface OnSceneChangeListener {
            void onSceneChanged(int newScene);
        }

        private static final long SCENE_DURATION_MS = 4800L;
        private int currentScene = 0;
        private long sceneStartMs;
        private final long playerInitMs;
        private final OnSceneChangeListener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();
        private final android.graphics.Movie[] sampleMovies = new android.graphics.Movie[3];
        private final Bitmap[] frameBufs = new Bitmap[3];
        private final Canvas[] frameCanvases = new Canvas[3];

        @SuppressWarnings("deprecation")
        OnboardingMotionPlayerView(Context context, OnSceneChangeListener listener) {
            super(context);
            this.listener = listener;
            this.sceneStartMs = System.currentTimeMillis();
            this.playerInitMs = this.sceneStartMs;
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            String[] pngFallbacks = new String[]{
                    "samples/hera-pheri-baburao.png",
                    "samples/breaking-bad-danger.png",
                    "samples/vikram-rolex-sir.png"
            };
            String[] gifAssets = new String[]{
                    "samples/onboarding_gif_1.gif",
                    "samples/onboarding_gif_2.gif",
                    "samples/onboarding_gif_3.gif"
            };

            for (int i = 0; i < 3; i++) {
                Bitmap fallbackBmp = null;
                try (InputStream is = context.getAssets().open(pngFallbacks[i])) {
                    fallbackBmp = BitmapFactory.decodeStream(is);
                } catch (Exception ignored) {
                }

                try (InputStream gis = context.getAssets().open(gifAssets[i])) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = gis.read(buf)) != -1) {
                        bos.write(buf, 0, r);
                    }
                    byte[] gifBytes = bos.toByteArray();
                    if (fallbackBmp == null) {
                        fallbackBmp = BitmapFactory.decodeByteArray(gifBytes, 0, gifBytes.length);
                    }
                    android.graphics.Movie m = android.graphics.Movie.decodeByteArray(gifBytes, 0, gifBytes.length);
                    if (m != null && m.width() > 0 && m.height() > 0) {
                        sampleMovies[i] = m;
                        frameBufs[i] = Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888);
                        frameCanvases[i] = new Canvas(frameBufs[i]);
                        if (fallbackBmp != null) {
                            frameCanvases[i].drawBitmap(fallbackBmp, null, new RectF(0, 0, m.width(), m.height()), paint);
                        }
                        m.setTime(0);
                        m.draw(frameCanvases[i], 0, 0);
                    }
                } catch (Exception ignored) {
                }

                if (frameBufs[i] == null && fallbackBmp != null) {
                    frameBufs[i] = fallbackBmp;
                }
            }
        }

        void setScene(int sceneIndex) {
            this.currentScene = sceneIndex % 3;
            this.sceneStartMs = System.currentTimeMillis();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float d = getResources().getDisplayMetrics().density;

            long elapsed = System.currentTimeMillis() - sceneStartMs;
            if (elapsed >= SCENE_DURATION_MS) {
                currentScene = (currentScene + 1) % 3;
                sceneStartMs = System.currentTimeMillis();
                elapsed = 0;
                if (listener != null) {
                    listener.onSceneChanged(currentScene);
                }
            }
            float progress = Math.min(1f, elapsed / (float) SCENE_DURATION_MS);

            // Stage rounded background
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    0, 0, w, h,
                    Color.parseColor("#1E293B"),
                    Color.parseColor("#0F172A"),
                    Shader.TileMode.CLAMP
            ));
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, d * 16, d * 16, paint);
            paint.setShader(null);

            // 1. Top Story Progress Bars (Instagram/Modern Onboarding video style)
            float barGap = d * 6;
            float barPad = d * 14;
            float barW = (w - barPad * 2 - barGap * 2) / 3f;
            float barY = d * 12;
            for (int i = 0; i < 3; i++) {
                float bx = barPad + i * (barW + barGap);
                paint.setColor(Color.parseColor("#334155"));
                rect.set(bx, barY, bx + barW, barY + d * 4);
                canvas.drawRoundRect(rect, d * 2, d * 2, paint);

                float fillRatio = (i < currentScene) ? 1f : (i == currentScene ? progress : 0f);
                if (fillRatio > 0f) {
                    paint.setColor(Color.parseColor("#818CF8"));
                    rect.set(bx, barY, bx + barW * fillRatio, barY + d * 4);
                    canvas.drawRoundRect(rect, d * 2, d * 2, paint);
                }
            }

            // 2. Render Active Animated Scene
            if (currentScene == 0) {
                drawScene1EnableSettings(canvas, w, h, d, progress);
            } else if (currentScene == 1) {
                drawScene2SwitchKeyboard(canvas, w, h, d, progress);
            } else {
                drawScene3LiveWhatsAppSend(canvas, w, h, d, progress);
            }

            postInvalidateOnAnimation();
        }

        private void drawScene1EnableSettings(Canvas canvas, float w, float h, float d, float p) {
            // Scene Caption
            textPaint.setTextSize(d * 13);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("Step 1: Toggle ON VibeKey in Settings", d * 14, d * 36, textPaint);

            // Mockup Phone Card
            float cardL = d * 18;
            float cardT = d * 48;
            float cardR = w - d * 18;
            float cardB = h - d * 14;
            paint.setColor(Color.parseColor("#F8FAFC"));
            rect.set(cardL, cardT, cardR, cardB);
            canvas.drawRoundRect(rect, d * 14, d * 14, paint);

            // Row 1: Default Keyboard (Always ON)
            drawMockSettingRow(canvas, cardL + d * 12, cardT + d * 14, cardR - d * 12, d,
                    "System Keyboard (SwiftKey / Gboard)", "Default typing keyboard", 1f, false);

            // Row 2: VibeKey (Animates smoothly from OFF -> ON between p=0.20 and p=0.48)
            float toggleAnim = clamp01((p - 0.22f) / 0.24f);
            drawMockSettingRow(canvas, cardL + d * 12, cardT + d * 76, cardR - d * 12, d,
                    "VibeKey — GIF & Sticker Keyboard", "Instant Movie Dialogue GIFs & Stickers", toggleAnim, true);

            // Animated Touch Ripple on the Toggle Switch
            float swX = cardR - d * 34;
            float swY = cardT + d * 100;
            if (p > 0.15f && p < 0.65f) {
                float rippleP = clamp01((p - 0.15f) / 0.45f);
                paint.setColor(Color.argb((int) ((1f - rippleP) * 130), 79, 70, 229));
                canvas.drawCircle(swX, swY, d * (12 + rippleP * 22), paint);
            }

            // Floating Status Pill when turned ON
            if (toggleAnim > 0.8f) {
                paint.setColor(Color.parseColor("#10B981"));
                rect.set(w / 2f - d * 70, cardB - d * 36, w / 2f + d * 70, cardB - d * 12);
                canvas.drawRoundRect(rect, d * 99, d * 99, paint);
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(d * 11);
                String ok = "✓ VibeKey Enabled!";
                float tw = textPaint.measureText(ok);
                canvas.drawText(ok, (w - tw) / 2f, cardB - d * 20, textPaint);
            }
        }

        private void drawMockSettingRow(Canvas canvas, float l, float t, float r, float d,
                                        String title, String sub, float toggleVal, boolean highlight) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(highlight ? Color.parseColor("#EEF2FF") : Color.WHITE);
            rect.set(l, t, r, t + d * 52);
            canvas.drawRoundRect(rect, d * 10, d * 10, paint);

            if (highlight) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(d * 1.5f);
                paint.setColor(Color.parseColor("#818CF8"));
                canvas.drawRoundRect(rect, d * 10, d * 10, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(d * 12);
            canvas.drawText(title, l + d * 12, t + d * 22, textPaint);

            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTextSize(d * 10);
            canvas.drawText(sub, l + d * 12, t + d * 39, textPaint);

            // Toggle Switch
            float swW = d * 38;
            float swH = d * 22;
            float swL = r - swW - d * 10;
            float swT = t + (d * 52 - swH) / 2f;
            int offCol = Color.parseColor("#CBD5E1");
            int onCol = Color.parseColor("#4F46E5");
            paint.setColor(blendColor(offCol, onCol, toggleVal));
            rect.set(swL, swT, swL + swW, swT + swH);
            canvas.drawRoundRect(rect, swH / 2f, swH / 2f, paint);

            paint.setColor(Color.WHITE);
            float knobX = swL + swH / 2f + toggleVal * (swW - swH);
            canvas.drawCircle(knobX, swT + swH / 2f, swH * 0.38f, paint);
        }

        private void drawScene2SwitchKeyboard(Canvas canvas, float w, float h, float d, float p) {
            textPaint.setTextSize(d * 13);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("Step 2: Use the Switch Keyboard Icon Anytime", d * 14, d * 36, textPaint);

            // Top Toolbar Mockup showing [ABC] [Search...] [GIF|Sticker] [SwitchIcon]
            float barL = d * 18;
            float barT = d * 52;
            float barR = w - d * 18;
            paint.setColor(Color.parseColor("#EEF2FA"));
            rect.set(barL, barT, barR, h - d * 14);
            canvas.drawRoundRect(rect, d * 14, d * 14, paint);

            // Toolbar pills
            paint.setColor(Color.WHITE);
            rect.set(barL + d * 10, barT + d * 10, barL + d * 50, barT + d * 42);
            canvas.drawRoundRect(rect, d * 10, d * 10, paint);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(d * 11);
            canvas.drawText("ABC", barL + d * 19, barT + d * 30, textPaint);

            rect.set(barL + d * 56, barT + d * 10, barR - d * 124, barT + d * 42);
            canvas.drawRoundRect(rect, d * 10, d * 10, paint);
            textPaint.setColor(Color.parseColor("#64748B"));
            canvas.drawText("Search dialogue...", barL + d * 66, barT + d * 30, textPaint);

            // Highlighted Switch Keyboard Button on Top-Right!
            float swBtnL = barR - d * 46;
            float swBtnT = barT + d * 10;
            paint.setColor(Color.parseColor("#4F46E5"));
            rect.set(swBtnL, swBtnT, barR - d * 10, swBtnT + d * 32);
            canvas.drawRoundRect(rect, d * 10, d * 10, paint);

            // Draw Mini Keyboard + Swap icon in white inside the button
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d * 1.6f);
            paint.setColor(Color.WHITE);
            float cx = (swBtnL + barR - d * 10) / 2f;
            float cy = swBtnT + d * 16;
            canvas.drawLine(cx - d * 6, cy - d * 6, cx + d * 6, cy - d * 6, paint);
            rect.set(cx - d * 8, cy - d * 1, cx + d * 8, cy + d * 8);
            canvas.drawRoundRect(rect, d * 2, d * 2, paint);
            paint.setStyle(Paint.Style.FILL);

            // Pulse ring around the Switch Keyboard icon
            float pulse = (float) Math.sin(p * Math.PI * 4) * 0.5f + 0.5f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d * 2f);
            paint.setColor(Color.argb((int) (180 * (1f - pulse * 0.5f)), 79, 70, 229));
            rect.set(swBtnL - d * 4, swBtnT - d * 4, barR - d * 6, swBtnT + d * 36);
            canvas.drawRoundRect(rect, d * 12, d * 12, paint);
            paint.setStyle(Paint.Style.FILL);

            // Animated "Choose Input Method" Sheet sliding up
            float sheetSlide = clamp01((p - 0.20f) / 0.25f);
            float sheetTop = barT + d * 54 + (1f - sheetSlide) * d * 40;
            paint.setColor(Color.WHITE);
            rect.set(barL + d * 20, sheetTop, barR - d * 20, h - d * 22);
            canvas.drawRoundRect(rect, d * 14, d * 14, paint);

            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(d * 12);
            canvas.drawText("Choose input method", barL + d * 34, sheetTop + d * 22, textPaint);

            // Radio Option 1: VibeKey Selected
            boolean vibeSelected = p > 0.45f;
            paint.setColor(Color.parseColor(vibeSelected ? "#EEF2FF" : "#F8FAFC"));
            rect.set(barL + d * 30, sheetTop + d * 30, barR - d * 30, sheetTop + d * 60);
            canvas.drawRoundRect(rect, d * 8, d * 8, paint);
            paint.setColor(Color.parseColor(vibeSelected ? "#4F46E5" : "#94A3B8"));
            canvas.drawCircle(barL + d * 44, sheetTop + d * 45, d * 6, paint);
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(d * 11);
            canvas.drawText("VibeKey — GIF & Sticker Keyboard", barL + d * 58, sheetTop + d * 49, textPaint);

            // Radio Option 2: Normal Keyboard
            paint.setColor(Color.parseColor(!vibeSelected ? "#EEF2FF" : "#F8FAFC"));
            rect.set(barL + d * 30, sheetTop + d * 66, barR - d * 30, sheetTop + d * 94);
            canvas.drawRoundRect(rect, d * 8, d * 8, paint);
            paint.setColor(Color.parseColor(!vibeSelected ? "#4F46E5" : "#94A3B8"));
            canvas.drawCircle(barL + d * 44, sheetTop + d * 80, d * 6, paint);
            textPaint.setColor(Color.parseColor("#475569"));
            canvas.drawText("Microsoft SwiftKey / Gboard", barL + d * 58, sheetTop + d * 84, textPaint);
        }

        @SuppressWarnings("deprecation")
        private void drawScene3LiveWhatsAppSend(Canvas canvas, float w, float h, float d, float p) {
            textPaint.setTextSize(d * 13);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("Step 3: Type Dialogue & 1-Tap Send to Chat!", d * 14, d * 36, textPaint);

            // Advance all 3 live animated Movie GIFs onto their software canvases
            long now = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                if (sampleMovies[i] != null && frameCanvases[i] != null) {
                    int dur = sampleMovies[i].duration();
                    if (dur <= 0) dur = 1200;
                    int relTime = (int) ((now - playerInitMs) % dur);
                    sampleMovies[i].setTime(relTime);
                    sampleMovies[i].draw(frameCanvases[i], 0, 0);
                }
            }

            float chatL = d * 18;
            float chatT = d * 48;
            float chatR = w - d * 18;
            float chatB = h - d * 12;

            // WhatsApp Dark Chat Area + Bottom Keyboard Split
            float kbTop = chatT + (chatB - chatT) * 0.46f;
            paint.setColor(Color.parseColor("#0B141A"));
            rect.set(chatL, chatT, chatR, kbTop);
            canvas.drawRoundRect(rect, d * 14, d * 14, paint);

            // Keyboard Tray Area
            paint.setColor(Color.parseColor("#EEF2FA"));
            rect.set(chatL, kbTop - d * 6, chatR, chatB);
            canvas.drawRoundRect(rect, d * 12, d * 12, paint);

            // Search Pill inside Keyboard with live typing effect
            paint.setColor(Color.WHITE);
            rect.set(chatL + d * 10, kbTop + d * 2, chatR - d * 84, kbTop + d * 26);
            canvas.drawRoundRect(rect, d * 8, d * 8, paint);

            // Crisp vector search loupe inside the search bar
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d * 1.5f);
            paint.setColor(Color.parseColor("#64748B"));
            canvas.drawCircle(chatL + d * 20, kbTop + d * 13, d * 4, paint);
            canvas.drawLine(chatL + d * 23, kbTop + d * 16, chatL + d * 26, kbTop + d * 19, paint);
            paint.setStyle(Paint.Style.FILL);

            String fullQuery = "Enna Koduma...";
            int charCount = Math.min(fullQuery.length(), (int) (clamp01(p / 0.35f) * fullQuery.length()));
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTextSize(d * 10.5f);
            canvas.drawText(fullQuery.substring(0, charCount), chatL + d * 32, kbTop + d * 18, textPaint);

            // GIF | Sticker badge in top-right of keyboard
            paint.setColor(Color.parseColor("#4F46E5"));
            rect.set(chatR - d * 78, kbTop + d * 2, chatR - d * 10, kbTop + d * 26);
            canvas.drawRoundRect(rect, d * 8, d * 8, paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(d * 9.5f);
            canvas.drawText("GIF • Sticker", chatR - d * 70, kbTop + d * 18, textPaint);

            // 3 Live Animated Movie GIF Tiles inside the Keyboard Grid
            float tileY = kbTop + d * 32;
            float tileH = chatB - tileY - d * 8;
            float tileW = (chatR - chatL - d * 32) / 3f;
            for (int i = 0; i < 3; i++) {
                float tx = chatL + d * 10 + i * (tileW + d * 6);
                rect.set(tx, tileY, tx + tileW, tileY + tileH);
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(rect, d * 10, d * 10, paint);

                if (frameBufs[i] != null) {
                    canvas.save();
                    clipPath.reset();
                    clipPath.addRoundRect(rect, d * 10, d * 10, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                    canvas.drawBitmap(frameBufs[i], null, rect, paint);
                    canvas.restore();
                }
            }

            // Finger Tap Ripple on Tile 0 at p = 0.42..0.62
            float firstTileCX = chatL + d * 10 + tileW / 2f;
            float firstTileCY = tileY + tileH / 2f;
            if (p >= 0.38f && p <= 0.62f) {
                float rp = (p - 0.38f) / 0.24f;
                paint.setColor(Color.argb((int) ((1f - rp) * 160), 79, 70, 229));
                canvas.drawCircle(firstTileCX, firstTileCY, d * (10 + rp * 20), paint);
            }

            // Sent WhatsApp Chat Bubble appears in the dark chat window after tap (p > 0.50)
            if (p >= 0.48f) {
                float sendP = clamp01((p - 0.48f) / 0.18f);
                float bubW = d * 116;
                float bubH = kbTop - chatT - d * 18;
                float bubR = chatR - d * 12;
                float bubL = bubR - bubW;
                float bubT = chatT + d * 6 + (1f - sendP) * d * 25;

                paint.setColor(Color.parseColor("#005C4B"));
                rect.set(bubL, bubT, bubR, bubT + bubH);
                canvas.drawRoundRect(rect, d * 10, d * 10, paint);

                if (frameBufs[0] != null) {
                    RectF imgRect = new RectF(bubL + d * 4, bubT + d * 4, bubR - d * 4, bubT + bubH - d * 14);
                    canvas.save();
                    clipPath.reset();
                    clipPath.addRoundRect(imgRect, d * 8, d * 8, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                    canvas.drawBitmap(frameBufs[0], null, imgRect, paint);
                    canvas.restore();
                }

                textPaint.setColor(Color.parseColor("#A7F3D0"));
                textPaint.setTextSize(d * 8.5f);
                canvas.drawText("Sent in 1 Tap • 14:42 ✓✓", bubL + d * 8, bubT + bubH - d * 3, textPaint);
            }
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private static int blendColor(int c1, int c2, float ratio) {
            float ir = 1f - ratio;
            int r = (int) (Color.red(c1) * ir + Color.red(c2) * ratio);
            int g = (int) (Color.green(c1) * ir + Color.green(c2) * ratio);
            int b = (int) (Color.blue(c1) * ir + Color.blue(c2) * ratio);
            return Color.rgb(r, g, b);
        }
    }
}
