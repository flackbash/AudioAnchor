package com.prangesoftwaresolutions.audioanchor.helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/*
 * Lays out its children left to right, wrapping to a new row once a child would no longer fit
 * the available width. Used for the sleep timer quick button cards, where the number of cards
 * (0-6, each a different width) isn't known ahead of time. Avoids pulling in a Flexbox/Material
 * Components dependency for this one spot.
 */
public class FlowLayout extends ViewGroup {

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int rowWidth = 0;
        int rowHeight = 0;
        int totalHeight = getPaddingTop() + getPaddingBottom();
        int totalWidth = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            LayoutParams lp = child.getLayoutParams();
            int childWidth = child.getMeasuredWidth() + marginLeft(lp) + marginRight(lp);
            int childHeight = child.getMeasuredHeight() + marginTop(lp) + marginBottom(lp);

            if (rowWidth + childWidth > availableWidth && rowWidth > 0) {
                totalWidth = Math.max(totalWidth, rowWidth);
                totalHeight += rowHeight;
                rowWidth = 0;
                rowHeight = 0;
            }
            rowWidth += childWidth;
            rowHeight = Math.max(rowHeight, childHeight);
        }
        totalWidth = Math.max(totalWidth, rowWidth);
        totalHeight += rowHeight;

        int resolvedWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec) : totalWidth + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(resolvedWidth, resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int availableWidth = right - left - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int rowHeight = 0;

        List<View> row = new ArrayList<>();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = child.getLayoutParams();
            int childWidth = child.getMeasuredWidth() + marginLeft(lp) + marginRight(lp);
            int childHeight = child.getMeasuredHeight() + marginTop(lp) + marginBottom(lp);

            if (x + childWidth > availableWidth + getPaddingLeft() && !row.isEmpty()) {
                placeRow(row, getPaddingLeft(), y, rowHeight);
                row.clear();
                x = getPaddingLeft();
                y += rowHeight;
                rowHeight = 0;
            }
            row.add(child);
            x += childWidth;
            rowHeight = Math.max(rowHeight, childHeight);
        }
        if (!row.isEmpty()) {
            placeRow(row, getPaddingLeft(), y, rowHeight);
        }
    }

    private void placeRow(List<View> row, int startX, int y, int rowHeight) {
        int x = startX;
        for (View child : row) {
            LayoutParams lp = child.getLayoutParams();
            int left = x + marginLeft(lp);
            int top = y + marginTop(lp);
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
            x += child.getMeasuredWidth() + marginLeft(lp) + marginRight(lp);
        }
    }

    private static int marginLeft(LayoutParams lp) {
        return (lp instanceof MarginLayoutParams) ? ((MarginLayoutParams) lp).leftMargin : 0;
    }

    private static int marginRight(LayoutParams lp) {
        return (lp instanceof MarginLayoutParams) ? ((MarginLayoutParams) lp).rightMargin : 0;
    }

    private static int marginTop(LayoutParams lp) {
        return (lp instanceof MarginLayoutParams) ? ((MarginLayoutParams) lp).topMargin : 0;
    }

    private static int marginBottom(LayoutParams lp) {
        return (lp instanceof MarginLayoutParams) ? ((MarginLayoutParams) lp).bottomMargin : 0;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(MarginLayoutParams.WRAP_CONTENT, MarginLayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}
