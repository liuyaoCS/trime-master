/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.osfans.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.KeyEvent;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.osfans.helper.Config;
import com.osfans.helper.Event;
import com.osfans.keyboard.Key;
import com.osfans.keyboard.Keyboard;
import com.osfans.trime.R;
import com.osfans.trime.R.drawable;
import com.osfans.trime.R.layout;

public class KeyboardView extends View implements View.OnClickListener {
    public interface OnKeyboardActionListener {
        void onPress(int primaryCode);
        void onRelease(int primaryCode);
        void onEvent(Event event);
        void onKey(int primaryCode, int mask);
        void onText(CharSequence text);
        boolean swipeLeft();
        boolean swipeRight();
        boolean swipeDown();
        boolean swipeUp();
    }

    // private static final boolean DEBUG = false;
    private static final int NOT_A_KEY = -1;
    private static final int[] LONG_PRESSABLE_STATE_SET = { android.R.attr.state_long_pressable };   
    
    private Keyboard mKeyboard;
    private int mCurrentKeyIndex = NOT_A_KEY;
    private int mLabelTextSize;
    private int mKeyTextSize;
    private float mShadowRadius;
    private int mShadowColor;
    private float mBackgroundDimAmount;
    
    private int key_symbol_color, hilited_key_symbol_color;
    private int mSymbolSize;
    private Paint mPaintSymbol;
    
    private TextView mPreviewText;
    private PopupWindow mPreviewPopup;
    private int mPreviewTextSizeLarge;
    private int mPreviewOffset;
    private int mPreviewHeight;
    private int[] mOffsetInWindow;

    private PopupWindow mPopupKeyboard;
    private View mMiniKeyboardContainer;
    private KeyboardView mMiniKeyboard;
    private boolean mMiniKeyboardOnScreen;
    private View mPopupParent;
    private int mMiniKeyboardOffsetX;
    private int mMiniKeyboardOffsetY;
    private Map<Key,View> mMiniKeyboardCache;
    private int[] mWindowOffset;
    private Key[] mKeys;

    /** Listener for {@link OnKeyboardActionListener}. */
    private OnKeyboardActionListener mKeyboardActionListener;
    
    private static final int MSG_SHOW_PREVIEW = 1;
    private static final int MSG_REMOVE_PREVIEW = 2;
    private static final int MSG_REPEAT = 3;
    private static final int MSG_LONGPRESS = 4;

    private static final int DELAY_BEFORE_PREVIEW = 40;
    private static final int DELAY_AFTER_PREVIEW = 60;
    
    private int mVerticalCorrection;
    private int mProximityThreshold;

    private boolean mPreviewCentered = false;
    private boolean mShowPreview = true;
    // private boolean mShowTouchPoints = true;
    private int mPopupPreviewX;
    private int mPopupPreviewY;

    private int mLastX;
    private int mLastY;
    private int mStartX;
    private int mStartY;

    private boolean mProximityCorrectOn = true;
    
    private Paint mPaint;
    private Rect mPadding;
    
    private long mDownTime;
    private long mLastMoveTime;
    private int mLastKey;
    private int mLastCodeX;
    private int mLastCodeY;
    private int mCurrentKey = NOT_A_KEY;
    private long mLastKeyTime;
    private long mCurrentKeyTime;
    private int[] mKeyIndices = new int[12];
    private GestureDetector mGestureDetector;
    private int mPopupX;
    private int mPopupY;
    private int mRepeatKeyIndex = NOT_A_KEY;
    private int mPopupLayout;
    private boolean mAbortKey;
    private Key mInvalidatedKey;
    private Rect mClipRegion = new Rect(0, 0, 0, 0);
    
    private ColorStateList mKeyBackColor, mKeyTextColor;

    private int repeat_interval = 50; // ~20 keys per second
    private int repeat_start_delay = 400;
    private int longpress_timeout = 500;
    // Deemed to be too short : ViewConfiguration.getLongPressTimeout();

    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];

    // For multi-tap
    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;
    private int multitap_interval = 800; // milliseconds
    private StringBuilder mPreviewLabel = new StringBuilder(1);

    /** Whether the keyboard bitmap needs to be redrawn before it's blitted. **/
    private boolean mDrawPending;
    /** The dirty region in the keyboard bitmap */
    private Rect mDirtyRect = new Rect();
    /** The keyboard bitmap for faster updates */
    private Bitmap mBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    private Canvas mCanvas;
    
    private Context mContext;
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_PREVIEW:
                    showKey(msg.arg1);
                    break;
                case MSG_REMOVE_PREVIEW:
                    mPreviewText.setVisibility(INVISIBLE);
                    break;
                case MSG_REPEAT:
                    if (repeatKey()) {
                        Message repeat = Message.obtain(this, MSG_REPEAT);
                        sendMessageDelayed(repeat, repeat_interval);
                    }
                    break;
                case MSG_LONGPRESS:
                    openPopupIfRequired((MotionEvent) msg.obj);
                    break;
            }
        }
    };


    public void reset() {
        Config config = Config.get();
        key_symbol_color = config.getColor("key_symbol_color");
        hilited_key_symbol_color = config.getColor("hilited_key_symbol_color");
        mShadowColor = config.getColor("shadow_color");

        mSymbolSize = config.getPixel("symbol_text_size");
        mKeyTextSize = config.getPixel("key_text_size");
        mVerticalCorrection = config.getPixel("vertical_correction");
        mPreviewOffset = config.getPixel("preview_offset");
        mPreviewHeight = config.getPixel("preview_height");
        mLabelTextSize = config.getPixel("label_text_size");

        mBackgroundDimAmount = config.getFloat("background_dim_amount");
        mShadowRadius = config.getFloat("shadow_radius");

        mKeyBackColor = new ColorStateList(Key.KEY_STATES, new int[]{
            config.getColor("hilited_on_key_back_color"),
            config.getColor("hilited_off_key_back_color"),
            config.getColor("on_key_back_color"),
            config.getColor("off_key_back_color"),
            config.getColor("hilited_key_back_color"),
            config.getColor("key_back_color")
        });

        mKeyTextColor = new ColorStateList(Key.KEY_STATES, new int[]{
            config.getColor("hilited_on_key_text_color"),
            config.getColor("hilited_off_key_text_color"),
            config.getColor("on_key_text_color"),
            config.getColor("off_key_text_color"),
            config.getColor("hilited_key_text_color"),
            config.getColor("key_text_color")
        });

        mPreviewText.setTextColor(config.getColor("preview_text_color"));
        mPreviewText.setBackgroundColor(config.getColor("preview_back_color"));
        //mPreviewText.setBackgroundResource(R.drawable.key_bg);
        mPreviewTextSizeLarge = config.getInt("preview_text_size");
        mPreviewText.setTextSize(mPreviewTextSizeLarge);
        mShowPreview = config.getBoolean("show_preview");
        setBackgroundColor(config.getColor("keyboard_back_color"));

        mPaint.setTypeface(config.getFont("key_font"));
        mPaintSymbol.setTypeface(config.getFont("symbol_font"));
        mPaintSymbol.setColor(key_symbol_color);
        mPaintSymbol.setTextSize(mSymbolSize);
        mPreviewText.setTypeface(config.getFont("preview_font"));

        repeat_interval = config.getInt("repeat_interval");
        repeat_start_delay = config.getInt("repeat_start_delay");
        longpress_timeout = config.getInt("longpress_timeout");
        multitap_interval = config.getInt("multitap_interval");
        invalidateAllKeys();
    }

    public KeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflate =
                (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext=context;
        mPreviewText = (TextView) inflate.inflate(R.layout.keyboard_key_preview, null);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Align.CENTER);
        mPaintSymbol = new Paint();
        mPaintSymbol.setAntiAlias(true);
        mPaintSymbol.setTextAlign(Align.CENTER);
        reset();

        mPreviewPopup = new PopupWindow(context);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);
        mPreviewPopup.setTouchable(false);

        mPopupLayout = R.layout.keyboard_popup_keyboard;
        mPopupKeyboard = new PopupWindow(context);
        mPopupKeyboard.setBackgroundDrawable(null);
        //mPopupKeyboard.setClippingEnabled(false);        
        mPopupParent = this;
        //mPredicting = true;

        mPadding = new Rect(0, 0, 0, 0);
        mMiniKeyboardCache = new HashMap<Key,View>();

        //mKeyBackground.getPadding(mPadding);
        
        resetMultiTap();
        initGestureDetector();
    }

    private void initGestureDetector() {
        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent me1, MotionEvent me2, 
                    float velocityX, float velocityY) {
                final float absX = Math.abs(velocityX);
                final float absY = Math.abs(velocityY);
                if (velocityX > 500 && absY < absX) {
                    return swipeRight();
                } else if (velocityX < -500 && absY < absX) {
                    return swipeLeft();
                } else if (velocityY < -500 && absX < absY) {
                    return swipeUp();
                } else if (velocityY > 500 && absX < 200) {
                    return swipeDown();
                } else if (absX > 800 || absY > 800) {
                    return true;
                }
                return false;
            }
        });

        mGestureDetector.setIsLongpressEnabled(false);
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    /**
     * Returns the {@link OnKeyboardActionListener} object.
     * @return the listener attached to this keyboard
     */
    protected OnKeyboardActionListener getOnKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    public void setKeyboard(Keyboard keyboard) {
        if (mKeyboard != null) {
            showPreview(NOT_A_KEY);
        }
        if (mKeyboard == keyboard) return;
        mKeyboard = keyboard;
        List<Key> keys = mKeyboard.getKeys();
        mKeys = keys.toArray(new Key[keys.size()]);
        requestLayout();
        // Release buffer, just in case the new keyboard has a different size. 
        // It will be reallocated on the next draw.
        mBuffer = null;
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
        mMiniKeyboardCache.clear(); // Not really necessary to do every time, but will free up views
    }

    /**
     * Returns the current keyboard being displayed by this view.
     * @return the currently attached keyboard
     * @see #setKeyboard(Keyboard)
     */
    public Keyboard getKeyboard() {
        return mKeyboard;
    }
    
    /**
     * Sets the state of the shift key of the keyboard, if any.
     * @param shifted whether or not to enable the state of the shift key
     * @return true if the shift key state changed, false if there was no change
     * @see KeyboardView#isShifted()
     */
    public boolean setShifted(boolean on, boolean shifted) {
        if (mKeyboard != null) {
            if (mKeyboard.setShifted(on, shifted)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    public boolean resetShifted() {
        if (mKeyboard != null) {
            if (mKeyboard.resetShifted()) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the state of the shift key of the keyboard, if any.
     * @return true if the shift is in a pressed state, false otherwise. If there is
     * no shift key on the keyboard or there is no keyboard attached, it returns false.
     * @see KeyboardView#setShifted(boolean)
     */
    public boolean isShifted() {
        if (mKeyboard != null) {
            return mKeyboard.isShifted();
        }
        return false;
    }

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled. 
     * @param previewEnabled whether or not to enable the key feedback popup
     * @see #isPreviewEnabled()
     */
    public void setPreviewEnabled(boolean previewEnabled) {
        mShowPreview = previewEnabled;
    }

    /**
     * Returns the enabled state of the key feedback popup.
     * @return whether or not the key feedback popup is enabled
     * @see #setPreviewEnabled(boolean)
     */
    public boolean isPreviewEnabled() {
        return mShowPreview;
    }
    
    public void setVerticalCorrection(int verticalOffset) {
        
    }
    public void setPopupParent(View v) {
        mPopupParent = v;
    }
    
    public void setPopupOffset(int x, int y) {
        mMiniKeyboardOffsetX = x;
        mMiniKeyboardOffsetY = y;
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
    }

    /**
     * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key
     * codes for adjacent keys.  When disabled, only the primary key code will be
     * reported.
     * @param enabled whether or not the proximity correction is enabled
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }

    /**
     * Returns true if proximity correction is enabled.
     */
    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }

    /** 
     * Popup keyboard close button clicked.
     * @hide 
     */
    public void onClick(View v) {
        dismissPopupKeyboard();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Round up a little
        if (mKeyboard == null) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
        } else {
            int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            }
            setMeasuredDimension(width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
        }
    }

    /**
     * Compute the average distance between adjacent keys (horizontally and vertically)
     * and square it to get the proximity threshold. We use a square here and in computing
     * the touch distance from a key's center to avoid taking a square root.
     * @param keyboard
     */
    private void computeProximityThreshold(Keyboard keyboard) {
        if (keyboard == null) return;
        final Key[] keys = mKeys;
        if (keys == null) return;
        int length = keys.length;
        int dimensionSum = 0;
        for (int i = 0; i < length; i++) {
            Key key = keys[i];
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mProximityThreshold = (int) (dimensionSum * 1.4f / length);
        mProximityThreshold *= mProximityThreshold; // Square it
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawPending || mBuffer == null) {
            onBufferDraw();
        }
        canvas.drawBitmap(mBuffer, 0, 0, null);
    }
    
    private void onBufferDraw() {
        if (mBuffer == null) {
            mBuffer = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBuffer);
            invalidateAllKeys();
        }
        final Canvas canvas = mCanvas;
        canvas.clipRect(mDirtyRect, Op.REPLACE);
        
        if (mKeyboard == null) return;
        
        final Paint paint = mPaint;
        Drawable keyBackground;
        final Rect clipRegion = mClipRegion;
        final Rect padding = mPadding;
        final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final Key[] keys = mKeys;
        final Key invalidKey = mInvalidatedKey;

        boolean drawSingleKey = false;
        if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
          // Is clipRegion completely contained within the invalidated key?
          if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left &&
                  invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top &&
                  invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right &&
                  invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
              drawSingleKey = true;
          }
        }
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        final int keyCount = keys.length;
        final float symbolTop = (mPaintSymbol.getTextSize());//mPaintSymbol.descent();
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[i];
            if (drawSingleKey && invalidKey != key) {
                continue;
            }
            int[] drawableState = key.getCurrentDrawableState();
            keyBackground = new ColorDrawable(mKeyBackColor.getColorForState(drawableState, 0));
            //keyBackground=mContext.getResources().getDrawable(R.drawable.key_bg);
            mPaint.setColor(mKeyTextColor.getColorForState(drawableState, 0));
            mPaintSymbol.setColor(key.pressed ? hilited_key_symbol_color: key_symbol_color);

            // Switch the character to uppercase if shift is pressed
            String label = key.getLabel();
            String hint = key.hint;
            int left = (key.width - padding.left - padding.right) / 2 + padding.left;
            int top = padding.top;
            
            final Rect bounds = keyBackground.getBounds();
            if (key.width != bounds.right || 
                    key.height != bounds.bottom) {
                keyBackground.setBounds(0, 0, key.width, key.height);
            }
            canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
            keyBackground.draw(canvas);
            
            if (!label.isEmpty()) {
                // For characters, use large font. For labels like "Done", use small font.
                if (label.length() > 1) {
                    paint.setTextSize(mLabelTextSize);
                } else {
                    paint.setTextSize(mKeyTextSize);
                }
                // Draw a drop shadow for the text
                paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                // Draw the text
                canvas.drawText(label, left,
                    (key.height - padding.top - padding.bottom) / 2
                            + (paint.getTextSize() - paint.descent()) / 2 + top,
                    paint);

                if (key.long_click != null) {
                    mPaintSymbol.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                    canvas.drawText(key.getSymbolLabel(), left, symbolTop + top, mPaintSymbol);
                }

                if (!hint.isEmpty()) {
                    mPaintSymbol.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                    canvas.drawText(hint, left, key.height  - padding.bottom - symbolTop + top, mPaintSymbol);
                }

                // Turn off drop shadow
                paint.setShadowLayer(0, 0, 0, 0);
            }
            canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
        }
        mInvalidatedKey = null;
        // Overlay a dark rectangle to dim the keyboard
        if (mMiniKeyboardOnScreen) {
            paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }

        /*if (DEBUG && mShowTouchPoints) {
            paint.setAlpha(128);
            paint.setColor(0xFFFF0000);
            canvas.drawCircle(mStartX, mStartY, 3, paint);
            canvas.drawLine(mStartX, mStartY, mLastX, mLastY, paint);
            paint.setColor(0xFF0000FF);
            canvas.drawCircle(mLastX, mLastY, 3, paint);
            paint.setColor(0xFF00FF00);
            canvas.drawCircle((mStartX + mLastX) / 2, (mStartY + mLastY) / 2, 2, paint);
        }*/
        
        mDrawPending = false;
        mDirtyRect.setEmpty();
    }

    private int getKeyIndices(int x, int y, int[] allKeys) {
        final Key[] keys = mKeys;
        // final boolean shifted = mKeyboard.isShifted();
        int primaryIndex = NOT_A_KEY;
        int closestKey = NOT_A_KEY;
        int closestKeyDist = mProximityThreshold + 1;
        java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);
            if (((mProximityCorrectOn 
                    && (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold) 
                    || isInside)
                    && key.getCode() > 32) {
                // Find insertion point
                final int nCodes = 1;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }
                
                if (allKeys == null) continue;
                
                for (int j = 0; j < mDistances.length; j++) {
                    if (mDistances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(mDistances, j, mDistances, j + nCodes,
                                mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - j - nCodes);
                        allKeys[j] = key.getCode();
                        mDistances[j] = dist;
                        break;
                    }
                }
            }
            
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }
        }
        if (primaryIndex == NOT_A_KEY) {
            primaryIndex = closestKey;
        }
        return primaryIndex;
    }

    private void detectAndSendKey(int x, int y, long eventTime) {
        int index = mCurrentKey;
        if (index != NOT_A_KEY && index < mKeys.length) {
            final Key key = mKeys[index];
            if (key.isShift()) {
               setShifted(false, !isShifted());
            } else {
                int code = key.getCode();
                //TextEntryState.keyPressedAt(key, x, y);
                int[] codes = new int[MAX_NEARBY_KEYS];
                Arrays.fill(codes, NOT_A_KEY);
                getKeyIndices(x, y, codes);
                mKeyboardActionListener.onEvent(key.getEvent());
                mKeyboardActionListener.onRelease(NOT_A_KEY);
                resetShifted();
            }
            mLastSentIndex = index;
            mLastTapTime = eventTime;
        }
    }

    private void showPreview(int keyIndex) {
        int oldKeyIndex = mCurrentKeyIndex;
        final PopupWindow previewPopup = mPreviewPopup;
        
        mCurrentKeyIndex = keyIndex;
        // Release the old key and press the new key
        final Key[] keys = mKeys;
        if (oldKeyIndex != mCurrentKeyIndex) {
            if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
                keys[oldKeyIndex].onReleased(mCurrentKeyIndex == NOT_A_KEY);
                invalidateKey(oldKeyIndex);
            }
            if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
                keys[mCurrentKeyIndex].onPressed();
                invalidateKey(mCurrentKeyIndex);
            }
        }
        // If key changed and preview is on ...
        if (oldKeyIndex != mCurrentKeyIndex && mShowPreview) {
            mHandler.removeMessages(MSG_SHOW_PREVIEW);
            if (previewPopup.isShowing()) {
                if (keyIndex == NOT_A_KEY) {
                    mHandler.sendMessageDelayed(mHandler
                            .obtainMessage(MSG_REMOVE_PREVIEW), 
                            DELAY_AFTER_PREVIEW);
                }
            }
            if (keyIndex != NOT_A_KEY) {
                if (previewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
                    // Show right away, if it's already visible and finger is moving around
                    showKey(keyIndex);
                } else {
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MSG_SHOW_PREVIEW, keyIndex, 0), 
                            DELAY_BEFORE_PREVIEW);
                }
            }
        }
    }
    
    private void showKey(final int keyIndex) {
        final PopupWindow previewPopup = mPreviewPopup;
        final Key[] keys = mKeys;
        Key key = keys[keyIndex];
        mPreviewText.setCompoundDrawables(null, null, null, null);
        mPreviewText.setText(key.getPreviewText());
        mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), key.width 
                + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
        final int popupHeight = mPreviewHeight;
        LayoutParams lp = mPreviewText.getLayoutParams();
        if (lp != null) {
            lp.width = popupWidth;
            lp.height = popupHeight;
        }
        if (!mPreviewCentered) {
            mPopupPreviewX = key.x - mPreviewText.getPaddingLeft() + getPaddingLeft();
            mPopupPreviewY = key.y - popupHeight + mPreviewOffset;
        } else {
            // TODO: Fix this if centering is brought back
            mPopupPreviewX = 160 - mPreviewText.getMeasuredWidth() / 2;
            mPopupPreviewY = - mPreviewText.getMeasuredHeight();
        }
        mHandler.removeMessages(MSG_REMOVE_PREVIEW);
        if (mOffsetInWindow == null) {
            mOffsetInWindow = new int[2];
            getLocationInWindow(mOffsetInWindow);
            mOffsetInWindow[0] += mMiniKeyboardOffsetX; // Offset may be zero
            mOffsetInWindow[1] += mMiniKeyboardOffsetY; // Offset may be zero
        }
        // Set the preview background state
        mPreviewText.getBackground().setState(
                key.popupResId != 0 ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
        if (previewPopup.isShowing()) {
            previewPopup.update(mPopupPreviewX + mOffsetInWindow[0],
                    mPopupPreviewY + mOffsetInWindow[1], 
                    popupWidth, popupHeight);
        } else {
            previewPopup.setWidth(popupWidth);
            previewPopup.setHeight(popupHeight);
            previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY, 
                    mPopupPreviewX + mOffsetInWindow[0], 
                    mPopupPreviewY + mOffsetInWindow[1]);
        }
        mPreviewText.setVisibility(VISIBLE);
    }

    /**
     * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only 
     * draws the cached buffer.
     * @see #invalidateKey(int)
     */
    public void invalidateAllKeys() {
        mDirtyRect.union(0, 0, getWidth(), getHeight());
        mDrawPending = true;
        invalidate();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param keyIndex the index of the key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    public void invalidateKey(int keyIndex) {
        if (mKeys == null) return;
        if (keyIndex < 0 || keyIndex >= mKeys.length) {
            return;
        }
        final Key key = mKeys[keyIndex];
        mInvalidatedKey = key;
        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
        onBufferDraw();
        invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
    }

    public void invalidateKeys(List<Key> keys) {
      if (keys == null || keys.size() == 0) return;
      for (Key key: keys) {
        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(), 
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
      }
      onBufferDraw();
      invalidate();
    }

    public void invalidateComposingKeys() {
      List<Key> keys = mKeyboard.getComposingKeys();
      if (keys != null && keys.size() > 5) invalidateAllKeys();
      else invalidateKeys(keys);
    }

    private boolean openPopupIfRequired(MotionEvent me) {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false;
        }
        if (mCurrentKey < 0 || mCurrentKey >= mKeys.length) {
            return false;
        }

        Key popupKey = mKeys[mCurrentKey];        
        boolean result = onLongPress(popupKey);
        if (result) {
            mAbortKey = true;
            showPreview(NOT_A_KEY);
        }
        return result;
    }

    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated
     * with this key through the attributes popupLayout and popupCharacters.
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key popupKey) {
        int popupKeyboardId = popupKey.popupResId;

        if (popupKeyboardId != 0) {
            mMiniKeyboardContainer = mMiniKeyboardCache.get(popupKey);
            if (mMiniKeyboardContainer == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                mMiniKeyboardContainer = inflater.inflate(mPopupLayout, null);
                mMiniKeyboard = (KeyboardView) mMiniKeyboardContainer.findViewById(
                        android.R.id.keyboardView);
                View closeButton = mMiniKeyboardContainer.findViewById(
                        android.R.id.closeButton);
                if (closeButton != null) closeButton.setOnClickListener(this);
                mMiniKeyboard.setOnKeyboardActionListener(new OnKeyboardActionListener() {
                    public void onEvent(Event event) {
                        mKeyboardActionListener.onEvent(event);
                        dismissPopupKeyboard();
                    }
                    public void onKey(int primaryCode, int mask) {
                        mKeyboardActionListener.onKey(primaryCode, mask);
                        dismissPopupKeyboard();
                    }
                    public void onText(CharSequence text) {
                        mKeyboardActionListener.onText(text);
                        dismissPopupKeyboard();
                    }
                    
                    public boolean swipeLeft() { return false; }
                    public boolean swipeRight() { return false; }
                    public boolean swipeUp() { return false; }
                    public boolean swipeDown() { return false; }
                    public void onPress(int primaryCode) {
                        mKeyboardActionListener.onPress(primaryCode);
                    }
                    public void onRelease(int primaryCode) {
                        mKeyboardActionListener.onRelease(primaryCode);
                    }
                });
                //mInputView.setSuggest(mSuggest);
                Keyboard keyboard;
                if (popupKey.popupCharacters != null) {
                    keyboard = new Keyboard(getContext(), popupKey.popupCharacters, -1, getPaddingLeft() + getPaddingRight());
                } else {
                    keyboard = new Keyboard(getContext());
                }
                mMiniKeyboard.setKeyboard(keyboard);
                mMiniKeyboard.setPopupParent(this);
                mMiniKeyboardContainer.measure(
                        MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST), 
                        MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
                
                mMiniKeyboardCache.put(popupKey, mMiniKeyboardContainer);
            } else {
                mMiniKeyboard = (KeyboardView) mMiniKeyboardContainer.findViewById(
                        android.R.id.keyboardView);
            }
            if (mWindowOffset == null) {
                mWindowOffset = new int[2];
                getLocationInWindow(mWindowOffset);
            }
            mPopupX = popupKey.x + getPaddingLeft();
            mPopupY = popupKey.y + getPaddingTop();
            mPopupX = mPopupX + popupKey.width - mMiniKeyboardContainer.getMeasuredWidth();
            mPopupY = mPopupY - mMiniKeyboardContainer.getMeasuredHeight();
            final int x = mPopupX + mMiniKeyboardContainer.getPaddingRight() + mWindowOffset[0];
            final int y = mPopupY + mMiniKeyboardContainer.getPaddingBottom() + mWindowOffset[1];
            mMiniKeyboard.setPopupOffset(x < 0 ? 0 : x, y);
            mMiniKeyboard.setShifted(false, isShifted());
            mPopupKeyboard.setContentView(mMiniKeyboardContainer);
            mPopupKeyboard.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
            mPopupKeyboard.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
            mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
            mMiniKeyboardOnScreen = true;
            //mMiniKeyboard.onTouchEvent(getTranslatedEvent(me));
            invalidateAllKeys();
            return true;
        } else {
            Key key = popupKey;
            if (key.long_click != null) {
              mKeyboardActionListener.onEvent(key.long_click);
              return true;
            }
            if (key.isShift()) {
                setShifted(!key.on, !key.on);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent me) {
        int touchX = (int) me.getX() - getPaddingLeft();
        int touchY = (int) me.getY() + mVerticalCorrection - getPaddingTop();
        int action = me.getAction();
        long eventTime = me.getEventTime();
        int keyIndex = getKeyIndices(touchX, touchY, null);

        if (mGestureDetector.onTouchEvent(me)) {
            showPreview(NOT_A_KEY);
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);            
            return true;
        }
        
        // Needs to be called after the gesture detector gets a turn, as it may have
        // displayed the mini keyboard
        if (mMiniKeyboardOnScreen) {
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mAbortKey = false;
                mStartX = touchX;
                mStartY = touchY;
                mLastCodeX = touchX;
                mLastCodeY = touchY;
                mLastKeyTime = 0;
                mCurrentKeyTime = 0;
                mLastKey = NOT_A_KEY;
                mCurrentKey = keyIndex;
                mDownTime = me.getEventTime();
                mLastMoveTime = mDownTime;
                checkMultiTap(eventTime, keyIndex);
                mKeyboardActionListener.onPress(keyIndex != NOT_A_KEY ? 
                        mKeys[keyIndex].click.code : 0);
                if (mCurrentKey >= 0 && mKeys[mCurrentKey].click.repeatable) {
                    mRepeatKeyIndex = mCurrentKey;
                    repeatKey();
                    Message msg = mHandler.obtainMessage(MSG_REPEAT);
                    mHandler.sendMessageDelayed(msg, repeat_start_delay);
                }
                if (mCurrentKey != NOT_A_KEY) {
                    Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                    mHandler.sendMessageDelayed(msg, longpress_timeout);
                }
                showPreview(keyIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                boolean continueLongPress = false;
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex;
                        mCurrentKeyTime = eventTime - mDownTime;
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime;
                            continueLongPress = true;
                        } else {
                            resetMultiTap();
                            mLastKey = mCurrentKey;
                            mLastCodeX = mLastX;
                            mLastCodeY = mLastY;
                            mLastKeyTime =
                                    mCurrentKeyTime + eventTime - mLastMoveTime;
                            mCurrentKey = keyIndex;
                            mCurrentKeyTime = 0;
                        }
                    }
                    if (keyIndex != mRepeatKeyIndex) {
                        mHandler.removeMessages(MSG_REPEAT);
                        mRepeatKeyIndex = NOT_A_KEY;
                    }
                }
                if (!continueLongPress) {
                    // Cancel old longpress
                    mHandler.removeMessages(MSG_LONGPRESS);
                    // Start new longpress if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                        mHandler.sendMessageDelayed(msg, longpress_timeout);
                    }
                }
                showPreview(keyIndex);
                break;

            case MotionEvent.ACTION_UP:
                mHandler.removeMessages(MSG_SHOW_PREVIEW);
                mHandler.removeMessages(MSG_REPEAT);
                mHandler.removeMessages(MSG_LONGPRESS);
                if (keyIndex == mCurrentKey) {
                    mCurrentKeyTime += eventTime - mLastMoveTime;
                } else {
                    resetMultiTap();
                    mLastKey = mCurrentKey;
                    mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
                    mCurrentKey = keyIndex;
                    mCurrentKeyTime = 0;
                }
                if (mCurrentKeyTime < mLastKeyTime && mLastKey != NOT_A_KEY) {
                    mCurrentKey = mLastKey;
                    touchX = mLastCodeX;
                    touchY = mLastCodeY;
                }
                showPreview(NOT_A_KEY);
                Arrays.fill(mKeyIndices, NOT_A_KEY);
                // If we're not on a repeating key (which sends on a DOWN event)
                if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
                    detectAndSendKey(touchX, touchY, eventTime);
                }
                invalidateKey(keyIndex);
                mRepeatKeyIndex = NOT_A_KEY;
                break;
        }
        mLastX = touchX;
        mLastY = touchY;
        return true;
    }

    private boolean repeatKey() {
        Key key = mKeys[mRepeatKeyIndex];
        detectAndSendKey(key.x, key.y, mLastTapTime);
        return true;
    }
    
    protected boolean swipeRight() {
        return mKeyboardActionListener.swipeRight();
    }
    
    protected boolean swipeLeft() {
        return mKeyboardActionListener.swipeLeft();
    }

    protected boolean swipeUp() {
        return mKeyboardActionListener.swipeUp();
    }

    protected boolean swipeDown() {
        return mKeyboardActionListener.swipeDown();
    }

    public void closing() {
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
        mHandler.removeMessages(MSG_REPEAT);
        mHandler.removeMessages(MSG_LONGPRESS);
        mHandler.removeMessages(MSG_SHOW_PREVIEW);
        
        dismissPopupKeyboard();
        mBuffer = null;
        mCanvas = null;
        mMiniKeyboardCache.clear();
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }

    private void dismissPopupKeyboard() {
        if (mPopupKeyboard.isShowing()) {
            mPopupKeyboard.dismiss();
            mMiniKeyboardOnScreen = false;
            invalidateAllKeys();
        }
    }

    public boolean handleBack() {
        if (mPopupKeyboard.isShowing()) {
            dismissPopupKeyboard();
            return true;
        }
        return false;
    }

    private void resetMultiTap() {
        mLastSentIndex = NOT_A_KEY;
        mTapCount = 0;
        mLastTapTime = -1;
        mInMultiTap = false;
    }
    
    private void checkMultiTap(long eventTime, int keyIndex) {
        if (keyIndex == NOT_A_KEY) return;
        Key key = mKeys[keyIndex];
        if (eventTime > mLastTapTime + multitap_interval || keyIndex != mLastSentIndex) {
            resetMultiTap();
        }
    }
}