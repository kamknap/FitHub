package pl.fithubapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import pl.fithubapp.data.UserWeightHistoryDto
import android.graphics.Paint
import android.graphics.Path
import android.text.TextPaint
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WeightChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr){

    private var data: List<UserWeightHistoryDto> = emptyList()

    data class ParsedPoint(
        val date: ZonedDateTime,
        val weight: Float
    )

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.divider)
        strokeWidth = dp(1.5f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.purple_primary)
        strokeWidth = dp(3f)
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.green_success)
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.white)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = context.getColor(R.color.text_secondary)
        textAlign = Paint.Align.CENTER
    }

    private val weightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        color = context.getColor(R.color.text_primary)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Marginesy
    private val leftPadding = dp(40f)
    private val rightPadding = dp(20f)
    private val topPadding = dp(30f)
    private val bottomPadding = dp(50f)

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")

    fun setData(weightHistory: List<UserWeightHistoryDto>) {
        this.data = weightHistory.sortedBy { it.measuredAt }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val parsedData = data.mapNotNull { history ->
            try {
                ParsedPoint(
                    date = ZonedDateTime.parse(history.measuredAt),
                    weight = history.weightKg.toFloat()
                )
            } catch (e: Exception) {
                null
            }
        }

        if (parsedData.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val chartWidth = width - leftPadding - rightPadding
        val chartHeight = height - topPadding - bottomPadding
        val bottomY = height - bottomPadding

        // Zakresy
        val minWeight = parsedData.minOf { it.weight } - 1
        val maxWeight = parsedData.maxOf { it.weight } + 1
        val weightRange = (maxWeight - minWeight).coerceAtLeast(0.1f) // Zabezpieczenie przed dzieleniem przez zero


        // Osie
        canvas.drawLine(leftPadding, topPadding, leftPadding, bottomY, axisPaint)
        canvas.drawLine(leftPadding, bottomY, width - rightPadding, bottomY, axisPaint)

        // Gradient pod linią
        val gradientPath = Path()
        parsedData.forEachIndexed { index, point ->
            val x = leftPadding + (index.toFloat() / (parsedData.size - 1).coerceAtLeast(1)) * chartWidth
            val y = topPadding + (1f - (point.weight - minWeight) / weightRange) * chartHeight
            if (index == 0) {
                gradientPath.moveTo(x, y)
            } else {
                gradientPath.lineTo(x, y)
            }
        }
        val lastX = leftPadding + chartWidth
        gradientPath.lineTo(lastX, bottomY)
        gradientPath.lineTo(leftPadding, bottomY)
        gradientPath.close()

        //  gradient
        gradientPaint.shader = LinearGradient(
            0f, topPadding,
            0f, bottomY,
            context.getColor(R.color.purple_primary_very_light),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(gradientPath, gradientPaint)

        // Linia wykresu
        val path = Path()
        parsedData.forEachIndexed { index, point ->
            val x = leftPadding + (index.toFloat() / (parsedData.size - 1).coerceAtLeast(1)) * chartWidth
            val y = topPadding + (1f - (point.weight - minWeight) / weightRange) * chartHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        val maxLabels = 5
        val step = if (parsedData.size > 1) {
            ((parsedData.size - 1) / (maxLabels - 1)).coerceAtLeast(1)
        } else {
            1
        }

        // Punkty i etykiety
        parsedData.forEachIndexed { index, point ->
            val x = leftPadding + (index.toFloat() / (parsedData.size - 1).coerceAtLeast(1)) * chartWidth
            val y = topPadding + (1f - (point.weight - minWeight) / weightRange) * chartHeight

            // Punkt z białym obramowaniem
            canvas.drawCircle(x, y, dp(6f), pointStrokePaint)
            canvas.drawCircle(x, y, dp(5f), pointPaint)

            canvas.drawText("${point.weight}", x, y - dp(12f), weightPaint)

            if (index == 0 || index == parsedData.size - 1 || (index > 0 && index % step == 0)) {
                canvas.drawText(point.date.format(dateFormatter), x, bottomY + dp(20f), labelPaint)
            }
        }

        //os Y
        val weightStep = 2.0f
        var currentWeight = (minWeight / weightStep).toInt() * weightStep
        while (currentWeight <= maxWeight) {
            if (currentWeight > minWeight) {
                val y = topPadding + (1f - (currentWeight - minWeight) / weightRange) * chartHeight

                canvas.drawText("${currentWeight.toInt()}kg", leftPadding - dp(10f), y, labelPaint.apply {
                    textAlign = Paint.Align.RIGHT
                })
            }
            currentWeight += weightStep
        }
        labelPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawEmptyState(canvas: Canvas) {
        val text = "Brak danych do wyświetlenia"
        val x = width / 2f
        val y = height / 2f
        canvas.drawText(text, x, y, labelPaint)
    }

    private fun dp(dp: Float): Float = dp * resources.displayMetrics.density
    private fun sp(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}