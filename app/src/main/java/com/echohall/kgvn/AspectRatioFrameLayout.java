package com.echohall.kgvn;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * ConstraintLayout + app:layout_constraintDimensionRatio bên trong 1 item
 * của GridLayoutManager đôi khi bị đo ra chiều rộng =0 ở lượt đo ĐẦU TIÊN
 * (trước khi RecyclerView biết chắc độ rộng cột) — và vì tỉ lệ tính ra
 * chiều cao dựa trên chiều rộng đó, kết quả là item bị "bẹp dí" xuống gần
 * như phẳng, không tự đo lại đúng nữa dù layout đã ổn định. Đây chính là
 * nguyên nhân khung thumbnail video bị bẹp thay vì hiện đúng tỉ lệ 16:9.
 *
 * View này tự tính chiều cao = chiều rộng thật đã đo được * (height/width),
 * KHÔNG phụ thuộc vào cơ chế constraint-solver nên luôn ra đúng kết quả
 * ngay từ lần đo đầu tiên.
 */
public class AspectRatioFrameLayout extends FrameLayout {

    private float ratioHeightOverWidth = 9f / 16f;

    public AspectRatioFrameLayout(Context context) {
        super(context);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setRatio(float widthUnits, float heightUnits) {
        if (widthUnits <= 0 || heightUnits <= 0) return;
        ratioHeightOverWidth = heightUnits / widthUnits;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * ratioHeightOverWidth);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
