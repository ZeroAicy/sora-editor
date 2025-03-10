/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2022  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.graphics;

import android.annotation.SuppressLint;
import android.util.Log;

import java.util.List;

import io.github.rosemoe.sora.data.Span;
import io.github.rosemoe.sora.text.ContentLine;
import static io.github.rosemoe.sora.text.TextStyle.*;

public class GraphicTextRow {

    private final static float SKEW_X = -0.2f;
    private final Paint mPaint = new Paint();
    private ContentLine mText;
    private int mStart;
    private int mEnd;
    private int mTabWidth;
    private List<Span> mSpans;

    private final static GraphicTextRow[] sCached = new GraphicTextRow[5];

    private GraphicTextRow() {

    }

    public static GraphicTextRow obtain() {
        GraphicTextRow st;
        synchronized (sCached) {
            for (int i = sCached.length; --i >= 0; ) {
                if (sCached[i] != null) {
                    st = sCached[i];
                    sCached[i] = null;
                    return st;
                }
            }
        }
        st = new GraphicTextRow();
        return st;
    }

    public static void recycle(GraphicTextRow st) {
        st.mText = null;
        st.mSpans = null;
        st.mStart = st.mEnd = st.mTabWidth = 0;
        synchronized (sCached) {
            for (int i = 0; i < sCached.length; ++i) {
                if (sCached[i] == null) {
                    sCached[i] = st;
                    break;
                }
            }
        }
    }

    public void set(ContentLine line, int start, int end, int tabWidth, List<Span> spans, Paint paint) {
        if (mPaint.getTextSize() != paint.getTextSize())
            mPaint.setTextSizeWrapped(paint.getTextSize());
        if (mPaint.getTypeface() != paint.getTypeface())
            mPaint.setTypefaceWrapped(paint.getTypeface());
        if (paint.getFontFeatureSettings() != null)
            mPaint.setFontFeatureSettingsWrapped(paint.getFontFeatureSettings());
        mText = line;
        mTabWidth = tabWidth;
        mStart = start;
        mEnd = end;
        mSpans = spans;
    }

    public float buildMeasureCache() {
        if (mText.widthCache == null) {
            mText.widthCache = new float[Math.max(128, mText.length())];
        }
        return measureTextInternal(mStart, mEnd, mText.widthCache);
    }

    public float[] findOffsetByAdvance(int start, float advance) {
        if (mText.widthCache != null) {
            float w = 0f;
            var cache = mText.widthCache;
            for (int i = start; i < mEnd;i++) {
                if (w > advance) {
                    return new float[] {i, w};
                } else {
                    w += cache[i];
                }
            }
            return new float[] {mEnd, w};
        }
        int regionStart = start;
        int index = 0;
        // Skip leading spans
        while (index < mSpans.size() && mSpans.get(index).column < regionStart) {
            index++;
        }
        float currentPosition = 0f;
        // Find in each region
        var lastStyle = 0L;
        var chars = mText.value;
        float tabAdvance = mPaint.getSpaceWidth() * mTabWidth;
        int offset = start;
        while (index <= mSpans.size() && currentPosition < advance) {
            var regionEnd = index < mSpans.size() ? mSpans.get(index).column : mEnd;
            regionEnd = Math.min(mEnd, regionEnd);
            int styleSpanIndex = Math.max(0, index - 1);
            var style = mSpans.get(styleSpanIndex).getStyleBits();
            if (style != lastStyle) {
                if (isBold(style) != isBold(lastStyle)) {
                    mPaint.setFakeBoldText(isBold(style));
                }
                if (isItalics(style) != isItalics(lastStyle)) {
                    mPaint.setTextSkewX(isItalics(style) ? SKEW_X : 0f);
                }
                lastStyle = style;
            }

            // Find in sub-region
            int res = -1;
            {
                int lastStart = regionStart;
                for (int i = regionStart; i < regionEnd; i++) {
                    if (chars[i] == '\t') {
                        // Here is a tab
                        // Try to find advance
                        if (lastStart != i) {
                            int idx = mPaint.findOffsetByRunAdvance(mText, lastStart, i, advance - currentPosition);
                            currentPosition += mPaint.measureTextRunAdvance(chars, lastStart, idx, regionStart, regionEnd);
                            if (idx < i) {
                                res = idx;
                                break;
                            } else {
                                if (currentPosition + tabAdvance > advance) {
                                    res = i;
                                    break;
                                } else {
                                    currentPosition += tabAdvance;
                                }
                            }
                        } else {
                            if (currentPosition + tabAdvance > advance) {
                                res = i;
                                break;
                            } else {
                                currentPosition += tabAdvance;
                            }
                        }
                        lastStart = i;
                    }
                }
                if (res == -1) {
                    int idx = mPaint.findOffsetByRunAdvance(mText, lastStart, regionEnd, advance - currentPosition);
                    currentPosition += measureText(lastStart, idx);
                    res = idx;
                }
            }

            offset = res;
            if (res < regionEnd) {
                break;
            }

            index ++;
            regionStart = regionEnd;
            if (regionEnd == mEnd) {
                break;
            }
        }
        if (lastStyle != 0L) {
            mPaint.setFakeBoldText(false);
            mPaint.setTextSkewX(0f);
        }
        return new float[] {offset, currentPosition};
    }

    public float measureText(int start, int end) {
        if (start == end) {
            return 0f;
        }
        if (mText.widthCache != null) {
            float width = 0f;
            var cache = mText.widthCache;
            for (int i = start;i < end;i++) {
                width += cache[i];
            }
            return width;
        }
        return measureTextInternal(start, end, null);
    }

    private float measureTextInternal(int start, int end, float[] widths) {
        start = Math.max(start, mStart);
        end = Math.min(end, mEnd);
        if (mSpans.size() == 0) {
            throw new IllegalArgumentException("At least one span is needed");
        }
        int regionStart = start;
        int index = 0;
        // Skip leading spans
        while (index < mSpans.size() && mSpans.get(index).column < regionStart) {
            index++;
        }
        float width = 0f;
        // Measure for each region
        var lastStyle = 0L;
        while (index <= mSpans.size()) {
            var regionEnd = index < mSpans.size() ? mSpans.get(index).column : mEnd;
            regionEnd = Math.min(end, regionEnd);
            int styleSpanIndex = Math.max(0, index - 1);
            var style = mSpans.get(styleSpanIndex).getStyleBits();
            if (style != lastStyle) {
                if (isBold(style) != isBold(lastStyle)) {
                    mPaint.setFakeBoldText(isBold(style));
                }
                if (isItalics(style) != isItalics(lastStyle)) {
                    mPaint.setTextSkewX(isItalics(style) ? SKEW_X : 0f);
                }
                lastStyle = style;
            }
            width += measureTextInner(regionStart, regionEnd, widths);
            index++;
            regionStart = regionEnd;
            if (regionEnd == end) {
                break;
            }
        }
        if (lastStyle != 0L) {
            mPaint.setFakeBoldText(false);
            mPaint.setTextSkewX(0f);
        }
        return width;
    }

    @SuppressLint("NewApi")
    private float measureTextInner(int start, int end, float[] widths) {
        if (start == end) {
            return 0f;
        }
        // Can be called directly
        float width = mPaint.getTextRunAdvances(mText.value, start, end - start, start, end - start, false, widths, widths == null ? 0 : start);
        float tabWidth = mPaint.getSpaceWidth() * mTabWidth;
        int tabCount = 0;
        for (int i = start; i < end; i++) {
            if (mText.charAt(i) == '\t') {
                tabCount++;
                if (widths != null) {
                    widths[i] = tabWidth;
                }
            }
        }
        float extraWidth = tabCount == 0 ? 0 : tabWidth - mPaint.measureText("\t");
        return width + extraWidth;
    }


}
