package com.deepcore.kiytoapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.OvershootInterpolator
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.Priority
import com.deepcore.kiytoapp.R
import kotlin.math.*

class MindMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.card_dark)
    }

    private val selectedNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = context.getColor(R.color.primary)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = context.getColor(R.color.gray_light)
    }

    private val tagConnectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        color = context.getColor(R.color.gray_light)
    }

    private val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = context.getColor(R.color.success)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        color = context.getColor(R.color.white)
    }

    private var tasks: List<Task> = emptyList()
    private val nodes = mutableMapOf<Task, MindMapNode>()
    private val path = Path()
    private var selectedTask: Task? = null
    private var suggestionAlpha = 0f
    
    private val matrix = Matrix()
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var isLongPress = false
    private var longPressStartTime = 0L

    private var onTaskClick: ((Task) -> Unit)? = null
    private var onTaskLongClick: ((Task) -> Unit)? = null

    private val connectionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 500
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.1f, 5f)
                invalidate()
                return true
            }
        })

    init {
        connectionAnimator.start()
    }

    fun setOnTaskClickListener(listener: (Task) -> Unit) {
        onTaskClick = listener
    }

    fun setOnTaskLongClickListener(listener: (Task) -> Unit) {
        onTaskLongClick = listener
    }

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
            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()
            nodes[task] = MindMapNode(x, y, task.title)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateNodePositions()
    }

    private fun drawAnimatedConnection(canvas: Canvas, from: MindMapNode, to: MindMapNode) {
        val progress = connectionAnimator.animatedValue as Float
        val midX = (from.x + to.x) / 2
        val midY = (from.y + to.y) / 2
        
        path.reset()
        path.moveTo(from.x, from.y)
        path.quadTo(
            midX, midY - 100f * progress,
            to.x, to.y
        )
        canvas.drawPath(path, tagConnectionPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)

        // Zeichne Verbindungslinien mit Animationen
        nodes.forEach { (task, node) ->
            task.tags.forEach { tag ->
                nodes.entries.find { it.key.title == tag }?.value?.let { targetNode ->
                    drawAnimatedConnection(canvas, node, targetNode)
                }
            }
        }

        // Zeichne Knoten und Text mit Prioritätsfarben
        nodes.forEach { (task, node) ->
            // Zeichne den Hauptknoten
            nodePaint.color = when (task.priority) {
                Priority.HIGH -> context.getColor(R.color.priority_high)
                Priority.MEDIUM -> context.getColor(R.color.priority_medium)
                Priority.LOW -> context.getColor(R.color.priority_low)
            }
            canvas.drawCircle(node.x, node.y, 60f, nodePaint)
            
            // Zeichne Auswahlindikator
            if (task == selectedTask) {
                canvas.drawCircle(node.x, node.y, 65f, selectedNodePaint)
            }

            // Zeichne Text
            canvas.drawText(
                node.title,
                node.x - textPaint.measureText(node.title) / 2,
                node.y + textPaint.textSize / 3,
                textPaint
            )

            // Zeichne Status (abgeschlossen)
            if (task.completed) {
                canvas.drawLine(
                    node.x - 40f, node.y,
                    node.x + 40f, node.y,
                    completedPaint
                )
            }
        }

        canvas.restore()
    }

    private fun findTaskAtPosition(x: Float, y: Float): Task? {
        val touchPoint = PointF((x - translateX) / scaleFactor, (y - translateY) / scaleFactor)
        return nodes.entries.firstOrNull { (task, node) ->
            val distance = sqrt(
                (touchPoint.x - node.x).pow(2) + 
                (touchPoint.y - node.y).pow(2)
            )
            distance <= 60f
        }?.key
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!scaleDetector.onTouchEvent(event)) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    longPressStartTime = System.currentTimeMillis()
                    
                    val touchedTask = findTaskAtPosition(event.x, event.y)
                    if (touchedTask != null) {
                        selectedTask = touchedTask
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    isDragging = touchedTask == null
                    isLongPress = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isLongPress && System.currentTimeMillis() - longPressStartTime > 500) {
                        if (selectedTask != null) {
                            isLongPress = true
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onTaskLongClick?.invoke(selectedTask!!)
                        }
                    }
                    
                    if (isDragging) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        translateX += dx
                        translateY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging && !isLongPress && selectedTask != null) {
                        onTaskClick?.invoke(selectedTask!!)
                    }
                    selectedTask = null
                    isDragging = false
                    isLongPress = false
                    return true
                }
            }
        }
        return true
    }

    fun showSuggestions(suggestions: List<TaskSuggestion>) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator()
            addUpdateListener { animator ->
                suggestionAlpha = animator.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    fun zoomIn() {
        scaleFactor = (scaleFactor * 1.2f).coerceIn(0.1f, 5f)
        invalidate()
    }

    fun zoomOut() {
        scaleFactor = (scaleFactor / 1.2f).coerceIn(0.1f, 5f)
        invalidate()
    }

    fun resetView() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    private data class MindMapNode(
        var x: Float,
        var y: Float,
        val title: String
    )

    data class TaskSuggestion(
        val title: String,
        val description: String,
        val confidence: Float
    )
} 