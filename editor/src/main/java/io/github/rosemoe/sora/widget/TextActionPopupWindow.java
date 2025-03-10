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
package io.github.rosemoe.sora.widget;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

import io.github.rosemoe.sora.R;
import io.github.rosemoe.sora.interfaces.EditorTextActionPresenter;

public class TextActionPopupWindow extends TextComposeBasePopup implements View.OnClickListener, EditorTextActionPresenter {
    private final CodeEditor mEditor;
    private final MaterialButton mPasteBtn;
    private final MaterialButton mSelectAll;
    private final MaterialButton mCutBtn;
    private final LinearLayout mContainer;

    private float mDpUnit;

    /**
     * Create a panel for editor
     *
     * @param editor Target editor
     */
    public TextActionPopupWindow(CodeEditor editor) {
        super(editor);
        mEditor = editor;
        mDpUnit = mEditor.getDpUnit();
        int popupHeightInDp = 60;
        popHeightPx = (int) (popupHeightInDp * mDpUnit);

        // Since popup window does provide decor view, we have to pass null to this method
        @SuppressLint("InflateParams")
        View root = LayoutInflater.from(editor.getContext()).inflate(R.layout.text_compose_popup_window, null);
        mSelectAll = root.findViewById(R.id.tcpw_material_button_select_all);
        mContainer = root.findViewById(R.id.text_compose_panel);
        mCutBtn = root.findViewById(R.id.tcpw_material_button_cut);
        MaterialButton copy = root.findViewById(R.id.tcpw_material_button_copy);
        mPasteBtn = root.findViewById(R.id.tcpw_material_button_paste);
        mSelectAll.setOnClickListener(this);
        mCutBtn.setOnClickListener(this);
        copy.setOnClickListener(this);
        mPasteBtn.setOnClickListener(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(mDpUnit * 8);
        gd.setStroke(1, 0xff808080);
        gd.setColor(0xffffffff);
        root.setBackground(gd);
        setContentView(root);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tcpw_material_button_select_all) {
            mEditor.selectAll();
        } else if (id == R.id.tcpw_material_button_cut) {
            mEditor.copyText();
            if (mEditor.getCursor().isSelected() && mEditor.isEditable()) {
                mEditor.getCursor().onDeleteKeyPressed();
            } else {
                mEditor.setSelection(mEditor.getCursor().getRightLine(), mEditor.getCursor().getRightColumn());
            }
        } else if (id == R.id.tcpw_material_button_paste) {
            if (mEditor.isEditable())
                mEditor.pasteText();
            mEditor.setSelection(mEditor.getCursor().getRightLine(), mEditor.getCursor().getRightColumn());
        } else if (id == R.id.tcpw_material_button_copy) {
            mEditor.copyText();
            mEditor.setSelection(mEditor.getCursor().getRightLine(), mEditor.getCursor().getRightColumn());
        }
        hide(DISMISS);
    }

    @Override
    public void onSelectedTextClicked(MotionEvent event) {

    }

    @Deprecated
    @Override
    public void onUpdate() {

    }

    @Override
    public void show() {
        mCutBtn.setVisibility(mEditor.isEditable() ? View.VISIBLE : View.GONE);
        mPasteBtn.setVisibility(mEditor.isEditable() ? View.VISIBLE : View.GONE);
        super.show();
    }

    @Override
    public void onUpdate(int updateReason) {
        hide(updateReason);
    }

    @Override
    public void onBeginTextSelect() {
        setHeight((int) (LinearLayout.LayoutParams.WRAP_CONTENT));
        setWidth((int) (LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public boolean onExit() {
        boolean result = isShowing();
        hide(DISMISS);
        if (mEditor.getCursor().isSelected()) {
            mEditor.setSelection(mEditor.getCursor().getRightLine(), mEditor.getCursor().getRightColumn());
        }
        return result && !isShowing();
    }

    @Override
    public boolean shouldShowCursor() {
        // this will show handler along with popup
        return true;
    }

    @Override
    public void onTextSelectionEnd() {
        TextActionPopupWindow panel = this;
        if (panel.isShowing()) {
            panel.hide(DISMISS);
        } else {
            int first = mEditor.getFirstVisibleRow();
            int last = mEditor.getLastVisibleRow();
            int left = mEditor.getCursor().getLeftLine();
            int right = mEditor.getCursor().getRightLine();
            int toLineBottom;
            if (right <= first) {
                toLineBottom = first;
            } else if (right > last) {
                if (left <= first) {
                    toLineBottom = (first + last) / 2;
                } else if (left >= last) {
                    toLineBottom = last - 2;
                } else {
                    if (left + 3 >= last) {
                        toLineBottom = left - 2;
                    } else {
                        toLineBottom = left + 1;
                    }
                }
            } else {
                if (left <= first) {
                    if (right + 3 >= last) {
                        toLineBottom = right - 2;
                    } else {
                        toLineBottom = right + 1;
                    }
                } else {
                    if (left + 5 >= right) {
                        toLineBottom = right + 1;
                    } else {
                        toLineBottom = (left + right) / 2;
                    }
                }
            }
            toLineBottom = Math.max(0, toLineBottom);
            int panelY = mEditor.getRowBottom(toLineBottom) - mEditor.getOffsetY();
            float handleLeftX = mEditor.getOffset(left, mEditor.getCursor().getLeftColumn());
            float handleRightX = mEditor.getOffset(right, mEditor.getCursor().getRightColumn());
            int panelX = (int) ((handleLeftX + handleRightX) / 2f);
            panel.setExtendedX(panelX);
            panel.setExtendedY(panelY);
            panel.show();
            mContainer.requestFocus();
        }
    }


}
