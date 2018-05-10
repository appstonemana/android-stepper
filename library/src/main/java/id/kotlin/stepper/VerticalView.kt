package id.kotlin.stepper

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.UiThread
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import id.kotlin.stepper.StepperOrientation.VERTICAL
import java.util.ArrayList
import kotlin.properties.Delegates

class VerticalView @JvmOverloads constructor(context: Context,
                                             attrs: AttributeSet? = null,
                                             defStyle: Int = 0) : View(context, attrs, defStyle) {

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 200
        private const val EXPAND_MARK = 1.3f
        private const val STEP_INVALID = -1
    }

    var text: String? = null

    private var orientation = VERTICAL
    private var animDuration: Int = 0
    private var stepCount: Int = 0
    private var currentStep: Int = 0
    private var previousStep: Int = 0

    private var circleRadius: Float = 0f
    private var lineLength: Float = 0f
    private var checkRadius: Float = 0f
    private var indicatorRadius: Float = 0f
    private var lineMargin: Float = 0f
    private var animProgress: Float = 0f
    private var animIndicatorRadius: Float = 0f
    private var animCheckRadius: Float = 0f

    private var showDoneIcon: Boolean = false

    private val linePathList = ArrayList<Path>()
    private val onStepClickListeners = ArrayList<OnStepClickListener>(0)
    private val stepAreaRect = Rect()
    private val stepAreaRectF = RectF()
    private var stepsClickAreas: MutableList<RectF>? = null
    private var gestureDetector: GestureDetector? = null

    private var animatorSet: AnimatorSet? = null
    private var lineAnimator: ObjectAnimator? = null
    private var indicatorAnimator: ObjectAnimator? = null
    private var checkAnimator: ObjectAnimator? = null

    private var defaultPrimaryColor by Delegates.notNull<Int>()
    private var defaultCircleRadius by Delegates.notNull<Float>()
    private var defaultIndicatorRadius by Delegates.notNull<Float>()
    private var defaultLineMargin by Delegates.notNull<Float>()

    private lateinit var indicators: FloatArray

    private lateinit var circlePaint: Paint
    private lateinit var indicatorPaint: Paint
    private lateinit var linePaint: Paint
    private lateinit var lineDonePaint: Paint
    private lateinit var lineDoneAnimatedPaint: Paint
    private lateinit var stepTextPaint: Paint
    private lateinit var stepVerticalPaint: Paint
    private lateinit var stepVerticalIndicatorPaint: Paint
    private lateinit var typeArray: TypedArray
    private lateinit var stepsCirclePaintList: MutableList<Paint>

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            var clickedStep = STEP_INVALID
            stepsClickAreas?.let {
                for (i in it.indices) {
                    if (it[i].contains(e.x, e.y)) {
                        clickedStep = i
                        break
                    }
                }

                if (clickedStep != STEP_INVALID) {
                    setCurrentStep(clickedStep)

                    for (listener in onStepClickListeners) {
                        listener.onStepClicked(clickedStep)
                    }
                }
            }

            return super.onSingleTapConfirmed(e)
        }
    }

    @Suppress("unused")
    private val stepCenterY: Float
        get() = measuredHeight.toFloat() / 2f

    @Suppress("unused")
    private val stepCenterX: Float
        get() = measuredWidth.toFloat() / 2f

    init {
        init(context, attrs, defStyle)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (orientation == VERTICAL) {
            vertical(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        compute()
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        currentStep = savedState.currentStep
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.currentStep = currentStep

        return savedState
    }

    @Suppress("unused")
    fun setOrientation(orientation: StepperOrientation) {
        this.orientation = orientation
        this.currentStep = 0
        compute()
        invalidate()
    }

    private fun computeStepsClickAreas() {
        stepsClickAreas = ArrayList(stepCount)
        for (indicator in indicators) {
            val left = 50f - circleRadius * 2
            val right = 50f + circleRadius * 2
            val top = indicator - circleRadius * 2
            val bottom = indicator + circleRadius
            val area = RectF(left, top, right, bottom)
            stepsClickAreas?.add(area)
        }
    }

    private fun vertical(canvas: Canvas) {
        val x = 50f

        val inLineAnimation = lineAnimator?.isRunning ?: false
        val inIndicatorAnimation = indicatorAnimator?.isRunning ?: false

        val drawToNext = previousStep == currentStep - 1
        val drawFromNext = previousStep == currentStep + 1

        for (i in indicators.indices) {
            val indicator = indicators[i]
            val drawDoneState = i < currentStep || drawFromNext && i == currentStep

            // we had constant x that mean vertical
            canvas.drawCircle(x, indicator, circleRadius, getStepCirclePaint(i))
            val stepLabel = (i + 1).toString()

            if (drawDoneState) {
                var radius = checkRadius

                if (i == previousStep && drawToNext || i == currentStep && drawFromNext) {
                    radius = animCheckRadius
                }
                canvas.drawCircle(x, indicator, radius, getStepIndicatorPaint(i))
            }

            // draw text inside circle
            stepAreaRect.set((x - circleRadius).toInt(), (indicator - circleRadius).toInt(), (x + circleRadius).toInt(), (indicator + circleRadius).toInt())
            stepAreaRectF.set(stepAreaRect)

            val stepTextNumberPaint = getStepTextNumberPaint(i)
            stepAreaRectF.right = stepTextNumberPaint.measureText(stepLabel, 0, stepLabel.length)
            stepAreaRectF.bottom = stepTextNumberPaint.descent() - stepTextNumberPaint.ascent()
            stepAreaRectF.left += (stepAreaRect.width() - stepAreaRectF.right) / 2.0f
            stepAreaRectF.top += (stepAreaRect.height() - stepAreaRectF.bottom) / 2.0f
            canvas.drawText(stepLabel, stepAreaRectF.left, stepAreaRectF.top - stepTextNumberPaint.ascent(), stepTextNumberPaint)

            // draw text in vertical
            canvas.drawText(text, x * 3, stepAreaRectF.top - stepTextNumberPaint.ascent(), getStepTextVerticalNumberPaint(i))
            if (drawDoneState) {
                canvas.drawText(text, x * 3, stepAreaRectF.top - stepTextNumberPaint.ascent(), getStepTextVerticalIndicatorNumberPaint(i))
            }

            if (i < linePathList.size) {
                if (i >= currentStep) {
                    canvas.drawPath(linePathList[i], linePaint)

                    if (i == currentStep && drawFromNext && (inLineAnimation || inIndicatorAnimation)) {
                        // go down
                        canvas.drawPath(linePathList[i], lineDoneAnimatedPaint)
                    }
                } else {
                    if (i == currentStep - 1 && drawToNext && inLineAnimation) {
                        // go up
                        canvas.drawPath(linePathList[i], linePaint)
                        canvas.drawPath(linePathList[i], lineDoneAnimatedPaint)
                    } else {
                        canvas.drawPath(linePathList[i], lineDonePaint)
                    }
                }
            }
        }
    }

    private fun setStepCount(stepCount: Int) {
        if (stepCount < 2) {
            throw IllegalArgumentException("StepCount must be >= 2")
        }

        this.stepCount = stepCount
        this.currentStep = 0
        compute()
        invalidate()
    }

    @UiThread
    private fun setCurrentStep(currentStep: Int) {
        if (currentStep < 0 || currentStep > stepCount) {
            throw IllegalArgumentException("Invalid step value $currentStep")
        }

        this.previousStep = this.currentStep
        this.currentStep = currentStep

        animatorSet?.cancel()
        animatorSet = null
        lineAnimator = null
        indicatorAnimator = null

        if (currentStep == previousStep + 1) {
            animatorSet = AnimatorSet()
            lineAnimator = ObjectAnimator.ofFloat(this, "animProgress", 1.0f, 0.0f)
            checkAnimator = ObjectAnimator.ofFloat(this, "animCheckRadius", indicatorRadius, checkRadius * EXPAND_MARK, checkRadius)
            animIndicatorRadius = 0f
            indicatorAnimator = ObjectAnimator.ofFloat(this, "animIndicatorRadius", 0f, indicatorRadius * 1.4f, indicatorRadius)
            animatorSet?.play(lineAnimator)?.with(checkAnimator)?.before(indicatorAnimator)
        } else if (currentStep == previousStep - 1) {
            animatorSet = AnimatorSet()
            indicatorAnimator = ObjectAnimator.ofFloat(this, "animIndicatorRadius", indicatorRadius, 0f)
            animProgress = 1.0f
            lineDoneAnimatedPaint.pathEffect = null
            lineAnimator = ObjectAnimator.ofFloat(this, "animProgress", 0.0f, 1.0f)
            animCheckRadius = checkRadius
            checkAnimator = ObjectAnimator.ofFloat(this, "animCheckRadius", checkRadius, indicatorRadius)
            animatorSet?.playSequentially(indicatorAnimator, lineAnimator, checkAnimator)
        }

        animatorSet?.let { animset ->
            lineAnimator?.let { line ->
                line.duration = 1000
                line.interpolator = AccelerateDecelerateInterpolator()
            }
            indicatorAnimator?.let { indicator ->
                indicator.duration = 500
                indicator.interpolator = AccelerateDecelerateInterpolator()
            }
            checkAnimator?.let { check ->
                check.duration = 500
                check.interpolator = AccelerateDecelerateInterpolator()
            }
            animset.start()
        }

        invalidate()
    }

    @SuppressLint("CustomViewStyleable")
    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        typeArray = context.obtainStyledAttributes(attrs, R.styleable.stepper, defStyleAttr, 0)
        stepsCirclePaintList = ArrayList(stepCount)

        for (i in 0 until stepCount) {
            val circlePaint = Paint(this.circlePaint)
            circlePaint.color = ContextCompat.getColor(context, R.color.stepper_default_circle)
            stepsCirclePaintList.add(circlePaint)
        }

        initDimens()
        initCirclePaint()
        initIndicatorPaint()
        initTextPaint()
        initVerticalPaint()
        initVerticalIndicatorPaint()
        initLinePaint()
        initRadius()

        typeArray.recycle()
        gestureDetector = GestureDetector(getContext(), gestureListener)
    }

    private fun initDimens() {
        val resources = resources
        defaultPrimaryColor = getPrimaryColor(context)
        defaultCircleRadius = resources.getDimension(R.dimen.stepper_default_circle)
        defaultIndicatorRadius = resources.getDimension(R.dimen.stepper_default_indicator)
        defaultLineMargin = resources.getDimension(R.dimen.stepper_default_line)
    }

    private fun initCirclePaint() {
        circlePaint = Paint().apply {
            strokeWidth = 4f
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.stepper_default_horizontal_line)
            isAntiAlias = true
        }
        setStepCount(5)
    }

    private fun initIndicatorPaint() {
        indicatorPaint = Paint(circlePaint).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.stepper_default_circle)
            isAntiAlias = true
        }
    }

    private fun initTextPaint() {
        stepTextPaint = Paint(indicatorPaint).apply {
            color = ContextCompat.getColor(context, R.color.stepper_default_text)
            textSize = resources.getDimension(R.dimen.stepper_default_horizontal_text)
        }
    }

    private fun initVerticalPaint() {
        stepVerticalPaint = Paint(indicatorPaint).apply {
            textSize = resources.getDimension(R.dimen.stepper_default_vertical_text)
            color = ContextCompat.getColor(context, R.color.stepper_default_horizontal_line)
        }
    }

    private fun initVerticalIndicatorPaint() {
        stepVerticalIndicatorPaint = Paint(stepVerticalPaint).apply {
            color = ContextCompat.getColor(context, R.color.stepper_default_vertical_line)
        }
    }

    private fun initLinePaint() {
        linePaint = Paint().apply {
            strokeWidth = typeArray.getDimension(R.styleable.stepper_line_stroke_width, 4f)
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            color = ContextCompat.getColor(context, R.color.stepper_default_horizontal_line)
            isAntiAlias = true
        }

        lineDonePaint = Paint(linePaint).apply {
            color = ContextCompat.getColor(context, R.color.stepper_default_circle)
        }

        lineDoneAnimatedPaint = Paint(lineDonePaint)
    }

    private fun initRadius() {
        circleRadius = typeArray.getDimension(R.styleable.stepper_circle_radius, defaultCircleRadius)
        checkRadius = circleRadius + circlePaint.strokeWidth / 2f
        indicatorRadius = typeArray.getDimension(R.styleable.stepper_indicator_radius, defaultIndicatorRadius)
        animIndicatorRadius = indicatorRadius
        animCheckRadius = checkRadius
        lineMargin = typeArray.getDimension(R.styleable.stepper_line_margin, defaultLineMargin)
        animDuration = typeArray.getInteger(R.styleable.stepper_anim_duration, DEFAULT_ANIMATION_DURATION)
        showDoneIcon = typeArray.getBoolean(R.styleable.stepper_show_done_icon, true)
    }

    private fun compute() {
        if (orientation == VERTICAL) {
            indicators = FloatArray(stepCount)
            linePathList.clear()

            val gridWidth = measuredWidth / stepCount
            val startX = gridWidth / 2f
            val divider = (measuredWidth - startX * 4f) / (stepCount - 1)
            lineLength = (circleRadius * 2f + circlePaint.strokeWidth) - lineMargin * 2

            for (i in 0 until indicators.size) {
                indicators[i] = startX + divider * i
            }

            for (i in 0 until indicators.size - 1) {
                val position = (indicators[i] + indicators[i + 1]) / 2 - lineLength / 2
                val linePath = Path()
                val lineY = 50f
                linePath.moveTo(lineY, position)
                linePath.lineTo(lineY, position + lineLength)
                linePathList.add(linePath)
            }

            computeStepsClickAreas()
        }
    }

    private fun getStepIndicatorPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, indicatorPaint)
    }

    private fun getStepTextNumberPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepTextPaint)
    }

    private fun getStepTextVerticalNumberPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepVerticalPaint)
    }

    private fun getStepTextVerticalIndicatorNumberPaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, stepVerticalIndicatorPaint)
    }

    private fun getStepCirclePaint(stepPosition: Int): Paint {
        return getPaint(stepPosition, circlePaint)
    }

    private fun getPaint(stepPosition: Int, defaultPaint: Paint): Paint {
        isStepValid(stepPosition)
        return defaultPaint
    }

    private fun isStepValid(stepPos: Int): Boolean {
        if (stepPos < 0 || stepPos > stepCount - 1) {
            throw IllegalArgumentException("Invalid step position. $stepPos is not a valid position! it should be between 0 and stepCount($stepCount)")
        }

        return true
    }

    private fun getPrimaryColor(context: Context): Int {
        var color = context.resources.getIdentifier("colorPrimary", "attr", context.packageName)
        when {
            color != 0 -> {
                val t = TypedValue()
                context.theme.resolveAttribute(color, t, true)
                color = t.data
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val t = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
                color = t.getColor(0, ContextCompat.getColor(context, R.color.stepper_default_primary))
                t.recycle()
            }
            else -> {
                val t = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
                color = t.getColor(0, ContextCompat.getColor(context, R.color.stepper_default_primary))
                t.recycle()
            }
        }

        return color
    }

    @Suppress("unused")
    private fun setAnimProgress(animProgress: Float) {
        this.animProgress = animProgress
        lineDoneAnimatedPaint.pathEffect = createPathEffect(lineLength, animProgress, 0.0f)
        invalidate()
    }

    @Suppress("unused")
    private fun setAnimCheckRadius(animCheckRadius: Float) {
        this.animCheckRadius = animCheckRadius
        invalidate()
    }

    @Suppress("unused")
    private fun setAnimIndicatorRadius(animIndicatorRadius: Float) {
        this.animIndicatorRadius = animIndicatorRadius
        invalidate()
    }

    private fun createPathEffect(pathLength: Float, phase: Float, offset: Float): PathEffect {
        return DashPathEffect(floatArrayOf(pathLength, pathLength), Math.max(phase * pathLength, offset))
    }

    private class SavedState : View.BaseSavedState {

        var currentStep: Int = 0

        constructor(superState: Parcelable) : super(superState)
        private constructor(source: Parcel) : super(source) {
            currentStep = source.readInt()
        }

        companion object {
            @Suppress("unused")
            @JvmField val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }

        override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
            writeInt(currentStep)
        }
    }

    interface OnStepClickListener {

        fun onStepClicked(step: Int)
    }
}