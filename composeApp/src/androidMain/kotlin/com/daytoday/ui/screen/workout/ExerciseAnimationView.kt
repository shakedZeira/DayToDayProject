package com.daytoday.ui.screen.workout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ExerciseMotionType {
    PLANK_HOLD,
    BICYCLE_CRUNCH,
    SHOULDER_BRIDGE,
    CALF_RAISE,
    LEG_EXTENSION,
    LUNGE,
    ROWING_MACHINE,
    DEADLIFT,
    SHRUG,
    LATERAL_RAISE,
    SWING,
    CARDIO_JUMP,
    TRICEP_EXTENSION,
    OVERHEAD_PRESS,
    ARMS,
    CHEST_PRESS,
    PULL_UP,
    CRUNCH,
    SQUAT,
    DEFAULT
}

private fun has(text: String, vararg keywords: String): Boolean = keywords.any { text.contains(it, ignoreCase = true) }

fun resolveMotionType(name: String, muscleGroup: String): ExerciseMotionType {
    val n = name.lowercase(Locale.ROOT)
    val g = muscleGroup.lowercase(Locale.ROOT)
    return when {
        has(n, "plank", "hollow", "dead bug", "bear crawl", "superman") ->
            ExerciseMotionType.PLANK_HOLD

        has(
            n,
            "bicycle",
            "hanging knee",
            "flutter",
            "v-up",
            "v up",
            "tuck",
            "toe touch",
            "leg raise",
            "mountain climber",
            "climber",
            "russian twist",
            "twist",
            "v-sit",
            "windmill"
        ) ->
            ExerciseMotionType.BICYCLE_CRUNCH

        has(n, "bridge", "hip thrust", "pelvic tilt") ->
            ExerciseMotionType.SHOULDER_BRIDGE

        has(n, "calf", "heel raise", "tibialis") ->
            ExerciseMotionType.CALF_RAISE

        has(
            n,
            "leg extension",
            "knee extension",
            "sissy",
            "wall sit",
            "step up",
            "step-up",
            "leg curl",
            "hamstring curl",
            "seated curl",
            "lying curl"
        ) ->
            ExerciseMotionType.LEG_EXTENSION

        has(n, "lunge", "split squat") ->
            ExerciseMotionType.LUNGE

        has(n, "leg press", "hack squat", "sled") ->
            ExerciseMotionType.SQUAT

        has(
            n,
            "lateral raise",
            "side raise",
            "front raise",
            "upright row",
            "face pull",
            "rear delt",
            "band pull apart",
            "pull apart",
            "pull-apart"
        ) ->
            ExerciseMotionType.LATERAL_RAISE

        has(n, "renegade", "t-bar", "chest supported", "pendlay", "inverted row", "one arm row", "row") ->
            ExerciseMotionType.ROWING_MACHINE

        has(n, "deadlift", "back extension", "hip hinge", "rdl", "sumo", "good morning", "hinge") ->
            ExerciseMotionType.DEADLIFT

        has(n, "shrug", "farmer", "dead hang", "carry") ->
            ExerciseMotionType.SHRUG

        has(n, "swing", "kettlebell", "clean", "snatch", "woodchop", "wood chop") ->
            ExerciseMotionType.SWING

        has(n, "jump", "jumping", "jack", "skater", "box", "hop", "high knees", "plyo", "burpee") ->
            ExerciseMotionType.CARDIO_JUMP

        has(n, "tricep", "skull", "diamond", "close-grip", "close grip", "overhead extension") ->
            ExerciseMotionType.TRICEP_EXTENSION

        has(n, "overhead", "shoulder") || has(g, "shoulder") ->
            ExerciseMotionType.OVERHEAD_PRESS

        has(n, "curl", "bicep", "hammer", "preacher") || has(g, "arm") ->
            ExerciseMotionType.ARMS

        has(n, "push", "bench", "press", "fly", "dip") || has(g, "chest") ->
            ExerciseMotionType.CHEST_PRESS

        has(n, "pull", "chin", "lat") || has(g, "back") ->
            ExerciseMotionType.PULL_UP

        has(n, "crunch", "sit-up", "sit up") || has(g, "core") ->
            ExerciseMotionType.CRUNCH

        has(n, "squat") || has(g, "leg", "glute") ->
            ExerciseMotionType.SQUAT

        else ->
            ExerciseMotionType.DEFAULT
    }
}

@Composable
fun ExerciseAnimationView(
    exerciseName: String,
    muscleGroup: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val motionType = resolveMotionType(exerciseName, muscleGroup)
    val progress by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val contentModifier = if (showLabel) {
            Modifier.weight(1f).fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = contentModifier,
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val limbWidth = (w * 0.045f).coerceIn(2.5f, 6f)
                val equipWidth = (w * 0.035f).coerceIn(2f, 5f)
                val headR = (w * 0.08f).coerceIn(4f, 10f)

                when (motionType) {
                    ExerciseMotionType.PLANK_HOLD ->
                        drawPlankHoldMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.BICYCLE_CRUNCH ->
                        drawBicycleCrunchMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.SHOULDER_BRIDGE ->
                        drawShoulderBridgeMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.CALF_RAISE ->
                        drawCalfRaiseMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.LEG_EXTENSION ->
                        drawLegExtensionMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.LUNGE ->
                        drawLungeMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.ROWING_MACHINE ->
                        drawRowingMachineMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.DEADLIFT ->
                        drawDeadliftMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.SHRUG ->
                        drawShrugMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.LATERAL_RAISE ->
                        drawLateralRaiseMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.SWING ->
                        drawSwingMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.CARDIO_JUMP ->
                        drawCardioJumpMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.TRICEP_EXTENSION ->
                        drawTricepExtensionMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.OVERHEAD_PRESS ->
                        drawOverheadPressMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.ARMS ->
                        drawArmsCurlMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.CHEST_PRESS ->
                        drawChestPressMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.PULL_UP ->
                        drawPullUpMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.CRUNCH ->
                        drawCrunchMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.SQUAT ->
                        drawSquatMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, primaryColor)

                    ExerciseMotionType.DEFAULT ->
                        drawDynamicPulseMotion(progress, w, h, headR, limbWidth, equipWidth, onSurface, secondaryColor)
                }
            }
        }

        if (showLabel) {
            Text(
                text = when (motionType) {
                    ExerciseMotionType.PLANK_HOLD -> "Plank / Core Hold"
                    ExerciseMotionType.BICYCLE_CRUNCH -> "Core / Bicycle Crunch"
                    ExerciseMotionType.SHOULDER_BRIDGE -> "Glute Bridge"
                    ExerciseMotionType.CALF_RAISE -> "Calf Raise"
                    ExerciseMotionType.LEG_EXTENSION -> "Leg Extension"
                    ExerciseMotionType.LUNGE -> "Lunge"
                    ExerciseMotionType.ROWING_MACHINE -> "Horizontal Row"
                    ExerciseMotionType.DEADLIFT -> "Deadlift / Hinge"
                    ExerciseMotionType.SHRUG -> "Shrug / Carry"
                    ExerciseMotionType.LATERAL_RAISE -> "Side Raise"
                    ExerciseMotionType.SWING -> "Kettlebell Swing"
                    ExerciseMotionType.CARDIO_JUMP -> "Cardio / Plyometric"
                    ExerciseMotionType.TRICEP_EXTENSION -> "Triceps Extension"
                    ExerciseMotionType.OVERHEAD_PRESS -> "Overhead Press"
                    ExerciseMotionType.ARMS -> "Arm Curl"
                    ExerciseMotionType.CHEST_PRESS -> "Chest Press"
                    ExerciseMotionType.PULL_UP -> "Pull-Up / Back"
                    ExerciseMotionType.CRUNCH -> "Core / Crunch"
                    ExerciseMotionType.SQUAT -> "Squat / Legs"
                    ExerciseMotionType.DEFAULT -> "Dynamic Workout"
                },
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                minLines = 0,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 1. SQUAT MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawSquatMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.15f, h * 0.9f), Offset(w * 0.85f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val feetX = w * 0.5f
    val currentHipY = h * 0.54f + (h * 0.72f - h * 0.54f) * p
    val currentHipX = w * 0.5f - w * 0.08f * p
    val currentKneeY = h * 0.72f + (h * 0.74f - h * 0.72f) * p
    val currentKneeX = w * 0.5f + w * 0.09f * p
    val currentShoulderY = h * 0.34f + (h * 0.52f - h * 0.34f) * p
    val currentShoulderX = w * 0.5f - w * 0.04f * p
    val currentHeadY = currentShoulderY - headRadius * 1.5f
    val barLeftX = currentShoulderX - w * 0.22f
    val barRightX = currentShoulderX + w * 0.22f

    drawLine(accentColor, Offset(currentShoulderX - w * 0.22f, currentShoulderY), Offset(currentShoulderX + w * 0.22f, currentShoulderY), equipWidth, StrokeCap.Round)
    drawRoundRect(accentColor, Offset(barLeftX - 3f, currentShoulderY - 8f), Size(6f, 16f), CornerRadius(2f, 2f))
    drawRoundRect(accentColor, Offset(barRightX - 3f, currentShoulderY - 8f), Size(6f, 16f), CornerRadius(2f, 2f))

    drawCircle(bodyColor, headRadius, Offset(currentShoulderX, currentHeadY))
    drawLine(bodyColor, Offset(currentShoulderX, currentShoulderY), Offset(currentHipX, currentHipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(currentHipX, currentHipY), Offset(currentKneeX, currentKneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(currentKneeX, currentKneeY), Offset(feetX, floorY), limbWidth, StrokeCap.Round)

    val handX = currentShoulderX + w * 0.12f
    val elbowX = currentShoulderX + w * 0.08f
    val elbowY = currentShoulderY + h * 0.08f
    drawLine(bodyColor, Offset(currentShoulderX, currentShoulderY), Offset(elbowX, currentShoulderY + h * 0.08f), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(handX, currentShoulderY), limbWidth, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 2. CHEST PRESS MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawChestPressMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val benchY = h * 0.68f
    drawLine(bodyColor.copy(alpha = 0.4f), Offset(w * 0.18f, h * 0.68f), Offset(w * 0.72f, h * 0.68f), limbWidth * 1.3f, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.4f), Offset(w * 0.28f, benchY), Offset(w * 0.28f, h * 0.9f), equipWidth, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.4f), Offset(w * 0.65f, benchY), Offset(w * 0.65f, h * 0.9f), equipWidth, StrokeCap.Round)

    val headX = w * 0.26f
    val headY = benchY - headRadius - 2f
    val shoulderX = w * 0.38f
    val shoulderY = benchY - 4f
    val hipX = w * 0.6f
    val hipY = benchY - 4f
    val kneeX = w * 0.7f
    val kneeY = benchY + 6f
    val footX = w * 0.74f
    val footY = h * 0.9f

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, footY), limbWidth, StrokeCap.Round)

    val currentBarY = h * 0.32f + (shoulderY - 8f - h * 0.32f) * p
    val barStartX = shoulderX - w * 0.18f
    val barEndX = shoulderX + w * 0.18f
    drawLine(accentColor, Offset(shoulderX - w * 0.18f, currentBarY), Offset(shoulderX + w * 0.18f, currentBarY), equipWidth, StrokeCap.Round)
    drawRoundRect(accentColor, Offset(barStartX - 4f, currentBarY - 10f), Size(7f, 20f), CornerRadius(2f, 2f))
    drawRoundRect(accentColor, Offset(barEndX - 3f, currentBarY - 10f), Size(7f, 20f), CornerRadius(2f, 2f))

    val elbowX = shoulderX - w * 0.06f * p
    val elbowY = shoulderY + h * 0.1f * p
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(elbowX, shoulderY + h * 0.1f * p), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(shoulderX, currentBarY), limbWidth, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 3. PULL-UP MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawPullUpMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val barY = h * 0.2f
    drawLine(accentColor, Offset(w * 0.15f, h * 0.2f), Offset(w * 0.85f, h * 0.2f), equipWidth, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.3f), Offset(w * 0.15f, barY), Offset(w * 0.15f, h * 0.9f), equipWidth, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.3f), Offset(w * 0.85f, barY), Offset(w * 0.85f, h * 0.9f), equipWidth, StrokeCap.Round)

    val handLeftX = w * 0.38f
    val handRightX = w * 0.62f
    val currentShoulderY = h * 0.44f + (h * 0.24f - h * 0.44f) * p
    val currentHeadY = h * 0.44f + (h * 0.24f - h * 0.44f) * p - headRadius * 1.5f
    val currentHipY = h * 0.44f + (h * 0.24f - h * 0.44f) * p + h * 0.24f
    val currentKneeY = currentShoulderY + h * 0.24f + h * 0.16f
    val currentFeetY = currentShoulderY + h * 0.24f + h * 0.16f + h * 0.14f

    drawCircle(bodyColor, headRadius, Offset(w * 0.5f, currentHeadY))
    drawLine(bodyColor, Offset(w * 0.5f, currentShoulderY), Offset(w * 0.5f, currentHipY), limbWidth, StrokeCap.Round)

    val kneeBendX = w * 0.5f + w * 0.05f * p
    val feetX = w * 0.5f + w * 0.03f * p
    drawLine(bodyColor, Offset(w * 0.5f, currentHipY), Offset(kneeBendX, currentKneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeBendX, currentKneeY), Offset(feetX, currentFeetY), limbWidth, StrokeCap.Round)

    val currentElbowY = currentShoulderY - h * 0.04f + (currentShoulderY + h * 0.08f - (currentShoulderY - h * 0.04f)) * p
    val leftElbowX = w * 0.5f - w * 0.16f
    val rightElbowX = w * 0.5f + w * 0.16f
    drawLine(bodyColor, Offset(w * 0.5f, currentShoulderY), Offset(w * 0.5f - w * 0.16f, currentElbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(leftElbowX, currentElbowY), Offset(handLeftX, barY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(w * 0.5f, currentShoulderY), Offset(rightElbowX, currentElbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(rightElbowX, currentElbowY), Offset(handRightX, barY), limbWidth, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 4. OVERHEAD PRESS MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawOverheadPressMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.2f, h * 0.9f), Offset(w * 0.8f, h * 0.9f), limbWidth, StrokeCap.Round)

    val bodyX = w * 0.5f
    val hipY = h * 0.58f
    val shoulderY = h * 0.38f
    val headY = h * 0.38f - headRadius * 1.5f

    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - w * 0.08f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + w * 0.08f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX, shoulderY), limbWidth, StrokeCap.Round)
    drawCircle(bodyColor, headRadius, Offset(bodyX, headY))

    val currentBarY = shoulderY - 2f + (h * 0.14f - (shoulderY - 2f)) * p
    val barW = w * 0.44f
    drawLine(accentColor, Offset(bodyX - w * 0.44f / 2f, currentBarY), Offset(bodyX + w * 0.44f / 2f, currentBarY), equipWidth, StrokeCap.Round)
    drawRoundRect(accentColor, Offset(bodyX - barW / 2f - 3f, currentBarY - 8f), Size(6f, 16f), CornerRadius(2f, 2f))
    drawRoundRect(accentColor, Offset(bodyX + barW / 2f - 3f, currentBarY - 8f), Size(6f, 16f), CornerRadius(2f, 2f))

    val elbowY = shoulderY + h * 0.1f * (1f - p)
    val leftElbowX = bodyX - w * 0.09f
    val rightElbowX = bodyX + w * 0.09f
    val handLeftX = bodyX - w * 0.12f
    val handRightX = bodyX + w * 0.12f
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(leftElbowX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(leftElbowX, elbowY), Offset(handLeftX, currentBarY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(rightElbowX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(rightElbowX, elbowY), Offset(handRightX, currentBarY), limbWidth, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 5. DEADLIFT MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawDeadliftMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.15f, h * 0.9f), Offset(w * 0.85f, h * 0.9f), limbWidth, StrokeCap.Round)

    val footX = w * 0.54f
    val kneeX = w * 0.5f + (w * 0.54f - w * 0.5f) * p
    val kneeY = floorY - h * 0.18f
    val hipX = w * 0.36f + (w * 0.53f - w * 0.36f) * p
    val bentHipY = h * 0.62f
    val standHipY = h * 0.54f
    val hipY = bentHipY + (h * 0.54f - bentHipY) * p
    val shoulderX = w * 0.56f + (w * 0.52f - w * 0.56f) * p
    val shoulderY = h * 0.5f + (h * 0.34f - h * 0.5f) * p
    val headX = shoulderX + w * 0.04f * (1f - p)
    val headY = shoulderY - headRadius * 1.5f

    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(shoulderX, shoulderY), limbWidth, StrokeCap.Round)
    drawCircle(bodyColor, headRadius, Offset(headX, headY))

    val barY = floorY - 6f + (standHipY + h * 0.05f - (floorY - 6f)) * p
    val barW = w * 0.4f
    drawLine(accentColor, Offset(footX - w * 0.4f / 2f, barY), Offset(footX + w * 0.4f / 2f, barY), equipWidth, StrokeCap.Round)
    drawRoundRect(accentColor, Offset(footX - barW / 2f - 3f, barY - 12f), Size(7f, 24f), CornerRadius(2f, 2f))
    drawRoundRect(accentColor, Offset(footX + barW / 2f - 4f, barY - 12f), Size(7f, 24f), CornerRadius(2f, 2f))

    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(footX, barY), limbWidth, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 6. CRUNCH MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawCrunchMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.82f
    drawLine(accentColor.copy(alpha = 0.6f), Offset(w * 0.12f, h * 0.82f + 4f), Offset(w * 0.88f, h * 0.82f + 4f), limbWidth * 1.2f, StrokeCap.Round)

    val hipX = w * 0.5f
    val hipY = floorY - 3f
    val kneeX = w * 0.68f
    val kneeY = floorY - h * 0.22f
    val footX = w * 0.78f
    val footY = floorY - 2f

    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, footY), limbWidth, StrokeCap.Round)

    val shoulderX = w * 0.28f + (w * 0.36f - w * 0.28f) * p
    val shoulderY = floorY - 4f + (floorY - h * 0.18f - (floorY - 4f)) * p
    val headX = shoulderX - w * 0.08f
    val headY = shoulderY - headRadius * 1.3f

    drawLine(bodyColor, Offset(hipX, hipY), Offset(shoulderX, shoulderY), limbWidth, StrokeCap.Round)
    drawCircle(bodyColor, headRadius, Offset(headX, headY))

    val elbowX = headX - w * 0.05f
    val elbowY = headY + h * 0.04f
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(elbowX, headY + h * 0.04f), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(headX + 2f, headY - 2f), limbWidth, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 7. ARMS CURL MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawArmsCurlMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.25f, h * 0.9f), Offset(w * 0.75f, h * 0.9f), limbWidth, StrokeCap.Round)

    val bodyX = w * 0.46f
    val hipY = h * 0.58f
    val shoulderY = h * 0.36f
    val headY = h * 0.36f - headRadius * 1.5f

    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - w * 0.06f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + w * 0.06f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX, shoulderY), limbWidth, StrokeCap.Round)
    drawCircle(bodyColor, headRadius, Offset(bodyX, headY))

    val elbowX = bodyX + w * 0.08f
    val elbowY = shoulderY + h * 0.16f
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(elbowX, shoulderY + h * 0.16f), limbWidth, StrokeCap.Round)

    val straightHandX = elbowX + w * 0.02f
    val straightHandY = elbowY + h * 0.16f
    val curledHandX = elbowX - w * 0.04f
    val curledHandY = shoulderY + h * 0.02f
    val handX = straightHandX + (curledHandX - straightHandX) * p
    val handY = straightHandY + (curledHandY - straightHandY) * p
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(handX, straightHandY + (curledHandY - straightHandY) * p), limbWidth, StrokeCap.Round)

    val dumbW = w * 0.12f
    drawLine(accentColor, Offset(handX - w * 0.12f / 2f, handY), Offset(handX + w * 0.12f / 2f, handY), equipWidth, StrokeCap.Round)
    drawCircle(accentColor, (w * 0.035f).coerceIn(2.5f, 6f), Offset(handX - dumbW / 2f, handY))
    drawCircle(accentColor, (w * 0.035f).coerceIn(2.5f, 6f), Offset(handX + dumbW / 2f, handY))
}

// -----------------------------------------------------------------------------------------
// 8. DYNAMIC PULSE MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawDynamicPulseMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f

    val bodyX = w * 0.5f
    val hipY = h * 0.58f
    val shoulderY = h * 0.36f
    val headY = h * 0.36f - headRadius * 1.5f

    drawCircle(
        color = accentColor.copy(alpha = (1f - p * 0.7f).coerceIn(0.1f, 0.8f)),
        radius = w * 0.32f * (0.8f + p * 0.3f),
        center = Offset(bodyX, (h * 0.36f + hipY) / 2f),
        style = Stroke(width = limbWidth * 0.6f)
    )

    val armSpread = w * (0.12f + 0.12f * p)
    val armY = shoulderY - h * 0.1f * p
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX - armSpread, shoulderY - h * 0.1f * p), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX + armSpread, armY), limbWidth, StrokeCap.Round)

    val legSpread = w * (0.08f + 0.08f * p)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - w * (0.08f + 0.08f * p), floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + legSpread, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX, shoulderY), limbWidth, StrokeCap.Round)

    drawCircle(bodyColor, headRadius, Offset(bodyX, headY))
}

// -----------------------------------------------------------------------------------------
// 9. PLANK HOLD MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawPlankHoldMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.84f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.12f, h * 0.84f), Offset(w * 0.9f, h * 0.84f), limbWidth * 0.8f, StrokeCap.Round)
    drawLine(accentColor, Offset(w * 0.14f, floorY + limbWidth * 1.5f), Offset(w * 0.64f, floorY + limbWidth * 1.5f), limbWidth * 1.2f, StrokeCap.Round)

    val breath = sin(p * PI.toFloat())
    val supportY = floorY - limbWidth * 0.4f
    val elbowX = w * 0.22f
    val handX = w * 0.34f
    val shoulderX = w * 0.62f
    val shoulderY = floorY - h * 0.28f - h * 0.014f * breath
    val hipX = w * 0.45f
    val hipY = floorY - h * 0.13f - h * 0.009f * breath
    val headX = w * 0.7f
    val headY = shoulderY - headRadius * 0.5f
    val kneeX = w * 0.35f
    val kneeY = floorY - h * 0.06f
    val ankleX = w * 0.27f

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(elbowX, supportY), Offset(handX, supportY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, supportY), Offset(shoulderX, shoulderY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(ankleX, floorY), limbWidth, StrokeCap.Round)

    drawCircle(accentColor, (equipWidth * 0.9f).coerceIn(1.5f, 4.5f), Offset(elbowX, supportY))
}

// -----------------------------------------------------------------------------------------
// 10. BICYCLE CRUNCH MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawBicycleCrunchMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.8f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.1f, h * 0.8f), Offset(w * 0.92f, h * 0.8f), limbWidth * 0.8f, StrokeCap.Round)
    drawLine(accentColor, Offset(w * 0.12f, floorY + limbWidth * 1.5f), Offset(w * 0.9f, floorY + limbWidth * 1.5f), limbWidth * 1.2f, StrokeCap.Round)

    val reach = cos(p * 2f * PI.toFloat())
    val lift = abs(sin(p * 2f * PI.toFloat()))
    val hipX = w * 0.5f
    val hipY = floorY - limbWidth * 0.4f
    val shoulderX = w * 0.3f + (w * 0.38f - w * 0.3f) * p
    val shoulderY = floorY - h * 0.04f + (floorY - h * 0.22f - (floorY - h * 0.04f)) * p
    val headX = shoulderX - w * 0.08f
    val headY = shoulderY - headRadius * 1.2f

    val kneeAX = hipX + w * 0.22f * reach
    val kneeAY = hipY - h * (0.14f + 0.1f * lift)
    val footAX = kneeAX + w * 0.08f * reach
    val footAY = floorY - h * 0.02f
    val kneeBX = hipX - w * 0.22f * reach
    val kneeBY = hipY - h * (0.14f + 0.1f * lift)
    val footBX = kneeBX - w * 0.08f * reach
    val footBY = floorY - h * 0.02f

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeAX, kneeAY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeAX, kneeAY), Offset(footAX, footAY), limbWidth * 0.9f, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeBX, kneeBY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeBX, kneeBY), Offset(footBX, footBY), limbWidth * 0.9f, StrokeCap.Round)

    val elbowX = shoulderX - w * 0.08f
    val elbowY = shoulderY + h * 0.06f
    val handAX = shoulderX - w * 0.02f + (shoulderX - w * 0.12f - (shoulderX - w * 0.02f)) * (0.5f + 0.5f * reach)
    val handBX = shoulderX - w * 0.12f + (shoulderX - w * 0.02f - (shoulderX - w * 0.12f)) * (0.5f + 0.5f * reach)
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(elbowX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(handAX, headY - headRadius * 0.4f), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(handBX, shoulderY + h * 0.1f), limbWidth, StrokeCap.Round)

    drawCircle(accentColor, (equipWidth * 0.9f).coerceIn(1.5f, 4.5f), Offset(hipX, hipY))
}

// -----------------------------------------------------------------------------------------
// 11. SHOULDER BRIDGE MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawShoulderBridgeMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.8f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.1f, h * 0.8f), Offset(w * 0.92f, h * 0.8f), limbWidth * 0.8f, StrokeCap.Round)
    drawLine(accentColor, Offset(w * 0.12f, floorY + limbWidth * 1.5f), Offset(w * 0.86f, floorY + limbWidth * 1.5f), limbWidth * 1.1f, StrokeCap.Round)

    val shoulderX = w * 0.26f
    val shoulderY = floorY - limbWidth * 0.6f
    val headX = w * 0.16f
    val headY = floorY - headRadius * 0.9f
    val hipY = floorY - h * 0.03f + (floorY - h * 0.24f - (floorY - h * 0.03f)) * p
    val hipX = w * 0.44f
    val kneeX = w * 0.62f
    val kneeY = floorY - h * 0.12f
    val footX = w * 0.78f

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, floorY), limbWidth, StrokeCap.Round)

    val armX = shoulderX - w * 0.06f
    val armY = floorY - limbWidth * 0.3f
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(armX, floorY - limbWidth * 0.3f), limbWidth * 0.85f, StrokeCap.Round)

    drawCircle(accentColor, (equipWidth * 0.9f).coerceIn(1.5f, 4.5f), Offset(armX, armY))
}

// -----------------------------------------------------------------------------------------
// 12. CALF RAISE MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawCalfRaiseMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.88f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.18f, h * 0.88f), Offset(w * 0.82f, h * 0.88f), limbWidth * 0.8f, StrokeCap.Round)

    val bodyX = w * 0.5f
    val hipY = h * 0.56f
    val shoulderY = h * 0.34f
    val headY = h * 0.34f - headRadius * 1.5f
    val toeX = bodyX + w * 0.07f
    val heelX = bodyX - w * 0.07f
    val heelY = floorY + (floorY - h * 0.07f - floorY) * p
    val ankleY = (floorY + (floorY - h * 0.07f - floorY) * p + floorY) / 2f

    drawCircle(bodyColor, headRadius, Offset(bodyX, headY))
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX, ankleY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, ankleY), Offset(heelX, heelY), limbWidth * 0.9f, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, ankleY), Offset(toeX, floorY), limbWidth * 0.9f, StrokeCap.Round)

    val handLeftX = bodyX - w * 0.1f
    val handRightX = bodyX + w * 0.1f
    val handY = h * 0.52f
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(handLeftX, h * 0.52f), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(handRightX, handY), limbWidth, StrokeCap.Round)

    val dumbHalf = w * 0.05f
    drawLine(accentColor, Offset(handLeftX - w * 0.05f, handY), Offset(handLeftX + w * 0.05f, handY), equipWidth, StrokeCap.Round)
    drawLine(accentColor, Offset(handRightX - dumbHalf, handY), Offset(handRightX + dumbHalf, handY), equipWidth, StrokeCap.Round)

    drawCircle(accentColor, (w * 0.032f).coerceIn(2f, 5.5f), Offset(toeX, floorY))
}

// -----------------------------------------------------------------------------------------
// 13. LEG EXTENSION MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawLegExtensionMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.1f, h * 0.9f), Offset(w * 0.9f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val seatY = h * 0.62f
    drawLine(accentColor, Offset(w * 0.18f, h * 0.62f), Offset(w * 0.52f, h * 0.62f), equipWidth * 1.3f, StrokeCap.Round)
    drawLine(accentColor, Offset(w * 0.2f, seatY), Offset(w * 0.13f, h * 0.34f), equipWidth * 1.1f, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.3f), Offset(w * 0.22f, seatY), Offset(w * 0.22f, floorY), equipWidth, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.3f), Offset(w * 0.48f, seatY), Offset(w * 0.48f, floorY), equipWidth, StrokeCap.Round)

    val hipX = w * 0.38f
    val hipY = seatY - h * 0.02f
    val shoulderX = w * 0.24f
    val shoulderY = h * 0.4f
    val headX = w * 0.17f
    val headY = shoulderY - headRadius * 1.3f

    val bentKneeX = w * 0.56f
    val bentKneeY = seatY + h * 0.06f
    val bentFootX = w * 0.62f
    val bentFootY = h * 0.84f
    val straightKneeX = w * 0.7f
    val straightKneeY = seatY - h * 0.06f
    val straightFootX = w * 0.82f
    val straightFootY = seatY - h * 0.08f
    val kneeX = bentKneeX + (straightKneeX - bentKneeX) * p
    val kneeY = bentKneeY + (straightKneeY - bentKneeY) * p
    val footX = bentFootX + (straightFootX - bentFootX) * p
    val footY = bentFootY + (straightFootY - bentFootY) * p

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, footY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(shoulderX + w * 0.04f, shoulderY + h * 0.12f), limbWidth, StrokeCap.Round)

    drawLine(accentColor, Offset(kneeX, kneeY), Offset(footX + w * 0.05f, footY - h * 0.01f), equipWidth, StrokeCap.Round)
    drawCircle(accentColor, (w * 0.03f).coerceIn(2f, 5f), Offset(footX - w * 0.03f, footY))
}

// -----------------------------------------------------------------------------------------
// 14. LUNGE MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawLungeMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.12f, h * 0.9f), Offset(w * 0.88f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val hipY = h * 0.52f + (h * 0.7f - h * 0.52f) * p
    val hipX = w * 0.48f + w * 0.02f * p
    val shoulderY = h * 0.32f + (h * 0.5f - h * 0.32f) * p
    val shoulderX = hipX - w * 0.01f * p
    val headY = shoulderY - headRadius * 1.5f

    val frontFootX = w * 0.66f
    val frontKneeX = w * 0.58f
    val frontKneeY = h * 0.72f + (h * 0.76f - h * 0.72f) * p
    val backFootX = w * 0.3f
    val backKneeX = w * 0.36f
    val backKneeY = h * 0.7f + (h * 0.86f - h * 0.7f) * p

    drawCircle(bodyColor, headRadius, Offset(shoulderX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(frontKneeX, frontKneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(frontKneeX, frontKneeY), Offset(frontFootX, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(backKneeX, backKneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(backKneeX, backKneeY), Offset(backFootX, floorY - h * 0.03f), limbWidth, StrokeCap.Round)

    val holdX = hipX + w * 0.1f
    val holdY = shoulderY + h * 0.14f
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(holdX, shoulderY + h * 0.14f), limbWidth, StrokeCap.Round)
    drawCircle(accentColor, (w * 0.04f).coerceIn(3f, 7f), Offset(holdX, holdY))
}

// -----------------------------------------------------------------------------------------
// 15. ROWING MACHINE MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawRowingMachineMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.1f, h * 0.9f), Offset(w * 0.92f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val benchY = h * 0.6f
    drawLine(accentColor, Offset(w * 0.3f, h * 0.6f), Offset(w * 0.62f, h * 0.6f), equipWidth * 1.3f, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.3f), Offset(w * 0.34f, benchY), Offset(w * 0.34f, floorY), equipWidth, StrokeCap.Round)
    drawLine(bodyColor.copy(alpha = 0.3f), Offset(w * 0.58f, benchY), Offset(w * 0.58f, floorY), equipWidth, StrokeCap.Round)

    val hipX = w * 0.56f
    val hipY = benchY - h * 0.02f
    val shoulderX = w * 0.46f + (w * 0.58f - w * 0.46f) * p
    val shoulderY = h * 0.44f
    val headX = shoulderX + w * 0.03f * p
    val headY = shoulderY - headRadius * 1.4f
    val footX = w * 0.16f
    val footY = floorY - limbWidth * 0.4f
    val kneeX = w * 0.28f
    val kneeY = benchY + h * 0.1f

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, footY), limbWidth, StrokeCap.Round)

    val extendedHandX = w * 0.24f
    val extendedHandY = h * 0.42f
    val rowedHandX = shoulderX - w * 0.02f
    val rowedHandY = shoulderY + h * 0.04f
    val handX = extendedHandX + (rowedHandX - extendedHandX) * p
    val handY = extendedHandY + (rowedHandY - extendedHandY) * p
    val elbowX = handX + (shoulderX - handX) * 0.5f
    val elbowY = shoulderY + h * 0.06f
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(elbowX, shoulderY + h * 0.06f), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowX, elbowY), Offset(handX, handY), limbWidth, StrokeCap.Round)

    val barW = w * 0.22f
    drawLine(accentColor, Offset(handX - w * 0.22f / 2f, handY), Offset(handX + w * 0.22f / 2f, handY), equipWidth, StrokeCap.Round)

    val plateR = (w * 0.03f).coerceIn(2f, 5f)
    drawCircle(accentColor, plateR, Offset(handX - barW / 2f, handY))
    drawCircle(accentColor, plateR, Offset(handX + barW / 2f, handY))
}

// -----------------------------------------------------------------------------------------
// 16. SHRUG MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawShrugMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.2f, h * 0.9f), Offset(w * 0.8f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val bodyX = w * 0.5f
    val hipY = h * 0.56f
    val shoulderY = h * 0.36f + (h * 0.3f - h * 0.36f) * p

    drawCircle(
        bodyColor,
        headRadius,
        Offset(bodyX, h * 0.36f + (h * 0.3f - h * 0.36f) * p - headRadius * 1.5f)
    )
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - w * 0.07f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + w * 0.07f, floorY), limbWidth, StrokeCap.Round)

    val handLeftX = bodyX - w * 0.11f
    val handRightX = bodyX + w * 0.11f
    val handY = hipY + h * 0.02f
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(handLeftX, hipY + h * 0.02f), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(handRightX, handY), limbWidth, StrokeCap.Round)

    val plateR = (w * 0.05f).coerceIn(3f, 8f)
    drawLine(accentColor, Offset(handLeftX, handY), Offset(handRightX, handY), equipWidth * 0.8f, StrokeCap.Round)
    drawCircle(accentColor, plateR, Offset(handLeftX, handY))
    drawCircle(accentColor, plateR, Offset(handRightX, handY))
}

// -----------------------------------------------------------------------------------------
// 17. LATERAL RAISE MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawLateralRaiseMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.22f, h * 0.9f), Offset(w * 0.78f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val bodyX = w * 0.5f
    val hipY = h * 0.56f
    val shoulderY = h * 0.34f

    drawCircle(bodyColor, headRadius, Offset(bodyX, h * 0.34f - headRadius * 1.5f))
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - w * 0.06f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + w * 0.06f, floorY), limbWidth, StrokeCap.Round)

    val restHandY = hipY + h * 0.02f
    val restSpread = w * 0.05f
    val raisedHandY = shoulderY - h * 0.03f
    val raisedSpread = w * 0.28f
    val handY = restHandY + (raisedHandY - restHandY) * p
    val spread = restSpread + (raisedSpread - restSpread) * p
    val elbowY = shoulderY + (handY - shoulderY) * 0.5f
    val handLeftX = bodyX - spread
    val handRightX = bodyX + spread
    val elbowLeftX = bodyX - spread * 0.6f
    val elbowRightX = bodyX + spread * 0.6f

    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX - spread * 0.6f, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowLeftX, elbowY), Offset(handLeftX, handY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(elbowRightX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowRightX, elbowY), Offset(handRightX, handY), limbWidth, StrokeCap.Round)

    val dumbHalf = w * 0.055f
    val dumbR = (w * 0.028f).coerceIn(2f, 5f)
    drawLine(accentColor, Offset(handLeftX - dumbHalf, handY), Offset(handLeftX + dumbHalf, handY), equipWidth, StrokeCap.Round)
    drawLine(accentColor, Offset(handRightX - dumbHalf, handY), Offset(handRightX + dumbHalf, handY), equipWidth, StrokeCap.Round)
    drawCircle(accentColor, dumbR, Offset(handLeftX - dumbHalf, handY))
    drawCircle(accentColor, dumbR, Offset(handLeftX + dumbHalf, handY))
    drawCircle(accentColor, dumbR, Offset(handRightX - dumbHalf, handY))
    drawCircle(accentColor, dumbR, Offset(handRightX + dumbHalf, handY))
}

// -----------------------------------------------------------------------------------------
// 18. SWING MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawSwingMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.16f, h * 0.9f), Offset(w * 0.84f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val hipX = w * 0.48f + (w * 0.38f - w * 0.48f) * p
    val hipY = h * 0.52f + (h * 0.66f - h * 0.52f) * p
    val shoulderX = w * 0.5f + (w * 0.44f - w * 0.5f) * p
    val shoulderY = h * 0.32f + (h * 0.42f - h * 0.32f) * p
    val headX = shoulderX + w * 0.02f * (1f - p)
    val headY = shoulderY - headRadius * 1.4f
    val kneeX = w * 0.48f
    val kneeY = h * 0.72f
    val footX = w * 0.52f

    drawCircle(bodyColor, headRadius, Offset(headX, headY))
    drawLine(bodyColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(hipX, hipY), Offset(kneeX, kneeY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeX, kneeY), Offset(footX, floorY), limbWidth, StrokeCap.Round)

    val bellR = (w * 0.05f).coerceIn(3f, 8f)
    val hingeBellX = w * 0.44f
    val hingeBellY = floorY - w * 0.06f
    val standBellX = w * 0.6f
    val standBellY = h * 0.62f
    val bellX = hingeBellX + (standBellX - hingeBellX) * p
    val bellY = hingeBellY + (standBellY - hingeBellY) * p
    drawLine(
        bodyColor,
        Offset(shoulderX, shoulderY),
        Offset(bellX - w * 0.05f, hingeBellY + (standBellY - hingeBellY) * p - bellR * 1.4f),
        limbWidth,
        StrokeCap.Round
    )
    drawCircle(accentColor, bellR, Offset(bellX, bellY))
    drawCircle(
        color = accentColor,
        radius = bellR * 0.55f,
        center = Offset(bellX, bellY),
        style = Stroke(width = equipWidth * 0.8f)
    )
}

// -----------------------------------------------------------------------------------------
// 19. CARDIO JUMP MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawCardioJumpMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.16f, h * 0.9f), Offset(w * 0.84f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val lift = sin(p * PI.toFloat()) * h * 0.08f
    val bodyX = w * 0.5f
    val hipY = h * 0.56f - lift
    val shoulderY = h * 0.34f - lift
    val headY = h * 0.34f - lift - headRadius * 1.5f

    val footSpread = w * 0.03f + (w * 0.2f - w * 0.03f) * p
    val footLift = h * 0.1f * p
    val reachX = w * 0.06f + (w * 0.24f - w * 0.06f) * p
    val reachY = hipY - shoulderY - h * 0.02f + (-(h * 0.16f) - (hipY - shoulderY - h * 0.02f)) * p

    val elbowLeftX = bodyX - reachX * 0.6f
    val elbowRightX = bodyX + reachX * 0.6f
    val elbowY = shoulderY + reachY * 0.35f
    val handLeftX = bodyX - reachX
    val handRightX = bodyX + reachX
    val handY = shoulderY + reachY

    drawCircle(bodyColor, headRadius, Offset(bodyX, headY))
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - footSpread, floorY - footLift), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + footSpread, floorY - footLift), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(elbowLeftX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowLeftX, elbowY), Offset(handLeftX, handY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(elbowRightX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(elbowRightX, elbowY), Offset(handRightX, handY), limbWidth, StrokeCap.Round)

    val handR = (equipWidth * 1.1f).coerceIn(2f, 5f)
    drawCircle(accentColor, handR, Offset(handLeftX, handY))
    drawCircle(accentColor, handR, Offset(handRightX, handY))
}

// -----------------------------------------------------------------------------------------
// 20. TRICEP EXTENSION MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawTricepExtensionMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.9f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.24f, h * 0.9f), Offset(w * 0.76f, h * 0.9f), limbWidth * 0.8f, StrokeCap.Round)

    val bodyX = w * 0.5f
    val hipY = h * 0.56f
    val shoulderY = h * 0.34f

    drawCircle(bodyColor, headRadius, Offset(bodyX, h * 0.34f - headRadius * 1.5f))
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX - w * 0.06f, floorY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(bodyX + w * 0.06f, floorY), limbWidth, StrokeCap.Round)

    val elbowSpread = w * 0.07f
    val elbowY = shoulderY + h * 0.03f
    val bentSpread = w * 0.07f
    val bentHandY = shoulderY - h * 0.02f
    val extendedSpread = w * 0.05f
    val extendedHandY = shoulderY - h * 0.22f
    val handSpread = bentSpread + (extendedSpread - bentSpread) * p
    val handY = bentHandY + (extendedHandY - bentHandY) * p
    val leftElbowX = bodyX - elbowSpread
    val rightElbowX = bodyX + elbowSpread
    val leftHandX = bodyX - handSpread
    val rightHandX = bodyX + handSpread

    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(leftElbowX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(leftElbowX, elbowY), Offset(leftHandX, handY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(rightElbowX, elbowY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(rightElbowX, elbowY), Offset(rightHandX, handY), limbWidth, StrokeCap.Round)

    val dumbHalf = w * 0.09f
    val dumbR = (w * 0.028f).coerceIn(2f, 5f)
    drawLine(accentColor, Offset(leftHandX - dumbHalf, handY), Offset(rightHandX + dumbHalf, handY), equipWidth, StrokeCap.Round)
    drawCircle(accentColor, dumbR, Offset(leftHandX - dumbHalf, handY))
    drawCircle(accentColor, dumbR, Offset(rightHandX + dumbHalf, handY))
}
