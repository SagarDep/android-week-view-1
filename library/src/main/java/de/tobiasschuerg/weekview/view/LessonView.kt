package de.tobiasschuerg.weekview.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import android.os.Debug
import android.util.Log
import android.view.ContextMenu.ContextMenuInfo
import android.view.View
import de.tobiasschuerg.weekview.BuildConfig
import de.tobiasschuerg.weekview.data.Lesson
import de.tobiasschuerg.weekview.data.TimeTableConfig
import de.tobiasschuerg.weekview.util.TextHelper
import de.tobiasschuerg.weekview.util.ViewHelper
import de.tobiasschuerg.weekview.util.dipToPixeel
import de.tobiasschuerg.weekview.util.toLocalString
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class LessonView(
        context: Context,
        private val config: TimeTableConfig,
        val lesson: Lesson
) : View(context) {

    private val TAG = javaClass.simpleName
    private val CORNER_RADIUS = context.dipToPixeel(2f)

    private val textPaint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
        }
    }

    private val subjectName: String by lazy {
        if (config.useShortNames) {
            lesson.shortName
        } else {
            lesson.fullName
        }
    }

    // TODO: specify this outside?
    private val textBounds: Rect = Rect()

    private val weightSum: Int
    private val lessonWeight = 3
    private val fromweight: Int
    private val toWeight: Int
    private val teacherweight: Int
    private val locationWeight: Int
    private val typeWeight: Int

    init {
        val pad = this.context.dipToPixeel(2f).roundToInt()
        setPadding(pad, pad, pad, pad)

        background = PaintDrawable().apply {
            paint.color = lesson.backgroundColor
            setCornerRadius(CORNER_RADIUS)
        }

        /**
         * Calculate weights above & below:
         */
        if (config.showTimeStart) {
            fromweight = 1
        } else {
            fromweight = 0
        }

        if (config.showType) {
            typeWeight = 1
        } else {
            typeWeight = 0
        }

        if (config.showTeacher) {
            teacherweight = 1
        } else {
            teacherweight = 0
        }

        if (config.showLocation) {
            locationWeight = 1
        } else {
            locationWeight = 0
        }

        if (config.showTimeEnd) {
            toWeight = 1
        } else {
            toWeight = 0
        }

        weightSum = fromweight + typeWeight + teacherweight + locationWeight + toWeight + lessonWeight

        val textColor = lesson.textColor
        textPaint.color = textColor
    }

    // TODO: clean up
    override fun onDraw(canvas: Canvas) {
        Log.d(TAG, "Drawing ${lesson.fullName}")

        // only for debugging
        if (Debug.isDebuggerConnected()) {
            for (i in 0..weightSum) {
                val content = height - (paddingTop + paddingBottom)
                val y = content * i / weightSum + paddingTop
                canvas.drawLine(0f, y.toFloat(), canvas.width.toFloat(), y.toFloat(), textPaint)
            }
        }

        // subject
        val maxTextSize = TextHelper.fitText(subjectName, textPaint.textSize * 3, width - (paddingLeft + paddingRight), height / 4)
        textPaint.textSize = maxTextSize
        textPaint.getTextBounds(subjectName, 0, subjectName.length, textBounds)
        var weight = fromweight + typeWeight
        if (weight == 0) {
            weight++
        }
        val subjectY = getY(weight, lessonWeight, textBounds)
        canvas.drawText(subjectName, (width / 2 - textBounds.centerX()).toFloat(), subjectY.toFloat(), textPaint)

        textPaint.textSize = TextHelper.fitText("123456", maxTextSize, width / 2,
                getY(position = 1, bounds = textBounds) - getY(position = 0, bounds = textBounds))

        // start time
        if (config.showTimeStart) {
            val startText = lesson.startTime.toLocalString(context)
            textPaint.getTextBounds(startText, 0, startText.length, textBounds)
            canvas.drawText(startText, (textBounds.left + paddingLeft).toFloat(), (textBounds.height() + paddingTop).toFloat(), textPaint)
        }

        // end time
        if (config.showTimeEnd) {
            val endText = lesson.endTime.toLocalString(context)
            textPaint.getTextBounds(endText, 0, endText.length, textBounds)
            canvas.drawText(endText, (width - (textBounds.right + paddingRight)).toFloat(), (height - paddingBottom).toFloat(), textPaint)
        }

        // type
        if (config.showType && lesson.type != null) {
            textPaint.getTextBounds(lesson.type, 0, lesson.type.length, textBounds)
            val typeY = getY(position = fromweight, bounds = textBounds)
            canvas.drawText(lesson.type, (width / 2 - textBounds.centerX()).toFloat(), typeY.toFloat(), textPaint)
        }

        // teacher
        if (config.showTeacher && lesson.teacher != null) {
            textPaint.getTextBounds(lesson.teacher, 0, lesson.teacher.length, textBounds)
            val teacherY = getY(position = fromweight + typeWeight + lessonWeight, bounds = textBounds)
            canvas.drawText(lesson.teacher, (width / 2 - textBounds.centerX()).toFloat(), teacherY.toFloat(), textPaint)
        }

        // location
        if (config.showLocation && lesson.location != null) {
            textPaint.getTextBounds(lesson.location, 0, lesson.location.length, textBounds)
            val locationY = getY(position = fromweight + typeWeight + lessonWeight + teacherweight, bounds = textBounds)
            canvas.drawText(lesson.location, (width / 2 - textBounds.centerX()).toFloat(), locationY.toFloat(), textPaint)
        }

    }

    private fun getY(position: Int, weight: Int = 1, bounds: Rect): Int {
        val content = height - (paddingTop + paddingBottom)
        val y = (content * (position + 0.5f * weight) / weightSum) + paddingTop
        return Math.round(y) - bounds.centerY()
    }

    var measureCount = 0
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.v(TAG, "Measuring ${lesson.fullName} ${measureCount++}")
        if (BuildConfig.DEBUG) {
            val debugWidth = ViewHelper.debugMeasureSpec(widthMeasureSpec)
            val debugHeight = ViewHelper.debugMeasureSpec(heightMeasureSpec)
            Log.v(TAG, "-> width: $debugWidth\n-> height: $debugHeight")
        }

        val desiredHeightDp = lesson.duration.toMinutes() * config.stretchingFactor
        val desiredHeightPx = context.dipToPixeel(desiredHeightDp).roundToInt()
        val resolvedHeight = resolveSize(desiredHeightPx, heightMeasureSpec)

        setMeasuredDimension(width, resolvedHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        Log.d(TAG, "Laying out ${lesson.fullName}: changed[$changed] ($left, $top),($right, $bottom)")
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun getContextMenuInfo(): ContextMenuInfo {
        return LessonViewContextInfo(lesson)
    }

    data class LessonViewContextInfo(var lesson: Lesson) : ContextMenuInfo

}
