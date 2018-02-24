package de.tobiasschuerg.weekview.view

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.RelativeLayout
import de.tobiasschuerg.weekview.R
import de.tobiasschuerg.weekview.data.Event
import de.tobiasschuerg.weekview.data.WeekViewConfig
import de.tobiasschuerg.weekview.util.Animation
import de.tobiasschuerg.weekview.util.DayHelper.createListStartingOn
import de.tobiasschuerg.weekview.util.dipToPixelF
import org.threeten.bp.Duration
import org.threeten.bp.LocalTime
import java.util.*
import java.util.Calendar.*
import kotlin.math.roundToInt


class WeekView(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {

    private val TAG = javaClass.simpleName
    private val config = WeekViewConfig() // TODO: add dynamically

    private val backgroundView: WeekBackgroundView
    private val overlapsWith = ArrayList<EventView>()

    private var isInScreenshotMode = false
    private var layoutCount = 0

    private var clickListener: ((view: EventView) -> Unit)? = null
    private var contextMenuListener: OnCreateContextMenuListener? = null
    private var eventTransitionName: String? = null

    private val accentColor: Int

    private val scaleGestureDetector: ScaleGestureDetector


    init {
        val days: MutableList<Int> = createListStartingOn().toMutableList()
        val iterator: MutableIterator<Int> = days.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id == SATURDAY && !config.saturdayEnabled || id == SUNDAY && !config.sundayEnabled) {
                Log.v(TAG, "Day $id is disabled")
                iterator.remove()
            }
        }

        val arr = context.obtainStyledAttributes(attributeSet, R.styleable.WeekView)
        accentColor = arr.getColor(R.styleable.WeekView_accent_color, Color.BLUE)
        arr.recycle()  // Do this when done.

        backgroundView = WeekBackgroundView(context)
        backgroundView.setAccentColor(accentColor)
        addView(backgroundView)

        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    }

    private var scaleFactor: Float = 1f
        set(value) {
            field = value
            backgroundView.scalingFactor = value
        }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = scaleFactor * detector.scaleFactor
            // Don't let the object get too small or too large.
            scaleFactor = Math.max(0.25f, Math.min(factor, 3.0f))
            Log.d(TAG, "Scale factor is $scaleFactor")
            invalidate()
            requestLayout()
            return true
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        super.dispatchTouchEvent(event)
        return scaleGestureDetector.onTouchEvent(event)
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun setEventTransitionName(transitionName: String) {
        this.eventTransitionName = transitionName
        for (childId in 0 until childCount) {
            val child: View = getChildAt(childId)
            if (child is EventView) {
                child.setTransitionName(transitionName)
            }
        }
    }


    fun setLessonClickListener(clickListener: (view: EventView) -> Unit) {
        this.clickListener = clickListener
        for (childIndex in 0 until childCount) {
            val view: View = getChildAt(childIndex)
            if (view is EventView) {
                view.setOnClickListener {
                    clickListener.invoke(view)
                }
            }
        }
    }

    override fun setOnCreateContextMenuListener(contextMenuListener: OnCreateContextMenuListener?) {
        this.contextMenuListener = contextMenuListener
        for (childIndex in 0 until childCount) {
            val view: View = getChildAt(childIndex)
            if (view is EventView) {
                view.setOnCreateContextMenuListener(contextMenuListener)
            }
        }
    }

    fun addLessonsToTimetable(events: List<Event.Single>) {
        Log.d(TAG, "Adding ${events.size} events to timetable")
        val time: LocalTime = LocalTime.now()
        for (event in events) {
            // don't add events for days which are not enabled
            if (event.day == SATURDAY) {
                if (!config.saturdayEnabled) {
                    Log.w(TAG, "Skipping ${event.fullName} as saturday is disabled")
                    continue
                }
            } else if (event.day == SUNDAY) {
                if (!config.sundayEnabled) {
                    Log.w(TAG, "Skipping ${event.fullName} as sunday is disabled")
                    continue
                }
            }

            val lv = EventView(context, event, scaleFactor)
            backgroundView.updateTimes(event.startTime, event.endTime)

            // mark active event
            if (Calendar.getInstance().get(DAY_OF_WEEK) == event.day && // this day
                    event.startTime < time && event.endTime > time) {
                lv.animation = Animation.createBlinkAnimation()
            }

            lv.setOnClickListener { clickListener?.invoke(lv) }
            lv.setOnCreateContextMenuListener(contextMenuListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                lv.transitionName = eventTransitionName
            }

            addView(lv)
        }
        Log.d(TAG, " - Done adding events to timetable")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        Log.v(TAG, "Measuring ($widthSize x $heightSize)")
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        Log.v(TAG, "Laying out timetable for the ${++layoutCount} time.")
        Log.v(TAG, "l: $l, t: $t, r: $r, b: $b")
        super.onLayout(true, l, t, r, b)
        if (isInScreenshotMode) {
            backgroundView.setScreenshotMode(true)
        }

        val childCount = childCount
        var childView: View
        for (childIndex in 0 until childCount) {
            val eventView: EventView
            childView = getChildAt(childIndex)
            if (childView is EventView) {
                eventView = childView
            } else {
                continue
            }

            // FIXME   lessonView.setShortNameEnabled(isShortNameEnabled);

            val column: Int = mapDayToColumn(eventView.event.day)
            if (column < 0) {
                // should not be necessary as wrong days get filtered before.
                Log.v(TAG, "Removing view for event $eventView")
                childView.setVisibility(View.GONE)
                removeView(childView)
                continue
            }
            var left: Int = backgroundView.getColumnStart(column, true).toInt()
            val right: Int = backgroundView.getColumnEnd(column, true).toInt()

            overlapsWith.clear()
            for (j in 0 until childIndex) {
                val v2 = getChildAt(j)
                // get next LessonView
                if (v2 is EventView) {
// check for overlap
                    if (v2.event.day != eventView.event.day) {
                        continue // days differ, overlap not possible
                    } else if (overlaps(eventView, v2)) {
                        overlapsWith.add(v2)
                    }
                }
            }

            if (overlapsWith.size > 0) {
                val width = (right - left) / (overlapsWith.size + 1)
                var iw = 0
                for (view in overlapsWith) {
                    val left2 = left + iw * width
                    view.layout(left2, view.top, left2 + width, view.bottom)
                    iw++
                }
                left = right - width

            }

            eventView.scalingFactor = scaleFactor
            val startTime = backgroundView.startTime
            val lessonStart = eventView.event.startTime
            val offset = Duration.between(startTime, lessonStart)

            val yOffset = offset.toMinutes() * scaleFactor
            val top = context.dipToPixelF(yOffset) + backgroundView.topOffsetPx

            val bottom = top + eventView.measuredHeight
            eventView.layout(left, top.roundToInt(), right, bottom.roundToInt())
        }
    }


    private fun mapDayToColumn(calendarDay: Int): Int {
        val firstDayOfTheWeek = Calendar.getInstance().firstDayOfWeek

        // directly return if day os not enabled
        if (calendarDay == SATURDAY) {
            if (!config.saturdayEnabled) {
                return -1
            }
        } else if (calendarDay == SUNDAY) {
            if (!config.sundayEnabled) {
                return -1
            }
        }

        when (firstDayOfTheWeek) {

            Calendar.MONDAY   -> {
                var column = (calendarDay + 5) % 7 // mo: 0, fr:4, su:6
                if (!config.saturdayEnabled && column == 6) {
                    column--
                }
                return column
            }

            Calendar.SATURDAY -> {
                var col = calendarDay % 7 // sa: 0, su: 1, fr: 6,
                if (!config.sundayEnabled && col > 1) col--
                if (!config.saturdayEnabled) col--
                return col
            }

            Calendar.SUNDAY   -> return if (config.sundayEnabled) {
                calendarDay - 1 // su: 0, mo: 1 fr: 5, sa: 6
            } else {
                calendarDay - 2 // mo: 0 fr: 4, sa: 5, su: -1
            }

            else              -> throw IllegalStateException(firstDayOfTheWeek.toString() + " das is not supported as start day")
        }
    }

    fun setScreenshotModeEnabled(enabled: Boolean) {
        isInScreenshotMode = enabled
    }

    private fun overlaps(left: EventView, right: EventView): Boolean {
        val rightStartsAfterLeftStarts = right.event.startTime >= left.event.startTime
        val rightStartsBeforeLeftEnds = right.event.startTime < left.event.endTime
        val lessonStartsWithing = rightStartsAfterLeftStarts && rightStartsBeforeLeftEnds

        val leftStartsBeforeRightEnds = left.event.startTime < right.event.endTime
        val rightEndsBeforeOrWithLeftEnds = right.event.endTime <= left.event.endTime
        val lessonEndsWithing = leftStartsBeforeRightEnds && rightEndsBeforeOrWithLeftEnds

        val leftStartsAfterRightStarts = left.event.startTime > right.event.startTime
        val rightEndsAfterLeftEnds = right.event.endTime > left.event.endTime
        val lessonWithin = leftStartsAfterRightStarts && rightEndsAfterLeftEnds

        return lessonStartsWithing || lessonEndsWithing || lessonWithin
    }
}
