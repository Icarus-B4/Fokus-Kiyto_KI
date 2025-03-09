package com.deepcore.kiytoapp.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.data.Task
import kotlin.math.min

class MindMapCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.card_dark)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        color = ContextCompat.getColor(context, R.color.white)
        textAlign = Paint.Align.CENTER
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.gray_light)
    }

    private var tasks: List<Task> = emptyList()
    private val nodes = mutableMapOf<Task, NodeInfo>()
    private val path = Path()

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private data class NodeInfo(
        var position: PointF,
        var radius: Float = 60f
    )

    fun setTasks(newTasks: List<Task>) {
        tasks = newTasks
        calculateNodePositions()
        invalidate()
    }

    private fun calculateNodePositions() {
        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 3f

        tasks.forEachIndexed { index, task ->
            val angle = (index.toFloat() / tasks.size) * 2 * Math.PI
            val x = centerX + (radius * Math.cos(angle)).toFloat()
            val y = centerY + (radius * Math.sin(angle)).toFloat()
            nodes[task] = NodeInfo(PointF(x, y))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateNodePositions()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)

        // Zeichne Verbindungslinien
        nodes.forEach { (task, node) ->
            task.tags.forEach { tag ->
                nodes.entries.find { it.key.title == tag }?.value?.let { targetNode ->
                    path.reset()
                    path.moveTo(node.position.x, node.position.y)
                    path.lineTo(targetNode.position.x, targetNode.position.y)
                    canvas.drawPath(path, linePaint)
                }
            }
        }

        // Zeichne Knoten und Text
        nodes.forEach { (task, node) ->
            canvas.drawCircle(node.position.x, node.position.y, node.radius, nodePaint)
            canvas.drawText(
                task.title,
                node.position.x,
                node.position.y + textPaint.textSize / 3,
                textPaint
            )
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3f)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            translateX -= distanceX
            translateY -= distanceY
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Finde den angeklickten Knoten
            val x = (e.x - translateX) / scaleFactor
            val y = (e.y - translateY) / scaleFactor

            nodes.forEach { (task, node) ->
                if (isPointInCircle(x, y, node.position.x, node.position.y, node.radius)) {
                    // Hier können wir später die Task-Details öffnen
                    return true
                }
            }
            return false
        }
    }

    private fun isPointInCircle(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }

    fun resetView() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    fun zoomIn() {
        scaleFactor = (scaleFactor * 1.2f).coerceIn(0.5f, 3f)
        invalidate()
    }

    fun zoomOut() {
        scaleFactor = (scaleFactor / 1.2f).coerceIn(0.5f, 3f)
        invalidate()
    }
} 