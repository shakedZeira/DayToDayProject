    drawCircle(
        color = accentColor,
        radius = bellR * 0.55f,
        center = Offset(bellX, bellY),
        style = Stroke(width = equipWidth * 0.8f)
    )
}

// -----------------------------------------------------------------------------------------
// 27. MOUNTAIN CLIMBER MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawMountainClimberMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.90f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.25f, floorY), Offset(w * 0.75f, floorY), limbWidth * 0.8f, StrokeCap.Round)

    val bodyX = w * 0.50f
    val hipY = h * 0.68f
    val shoulderY = h * 0.48f
    val headY = shoulderY - (headRadius * 1.5f)

    val t = p
    val knee1Y = floorY - (h * 0.04f) + (hipY - floorY + (h * 0.08f)) * sin(t * PI.toFloat())
    val knee2Y = floorY - (h * 0.04f) + (hipY - floorY + (h * 0.08f)) * sin(t * PI.toFloat() + PI.toFloat())

    val kneeFrontX = bodyX + (w * 0.08f)
    val kneeBackX = bodyX - (w * 0.04f)

    drawCircle(bodyColor, headRadius, Offset(bodyX, headY))
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX, hipY), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX - (w * 0.10f), shoulderY + (h * 0.08f)), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, shoulderY), Offset(bodyX + (w * 0.10f), shoulderY + (h * 0.08f)), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(kneeBackX, knee2Y), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeBackX, knee2Y), Offset(kneeBackX - (w * 0.02f), floorY), limbWidth * 0.9f, StrokeCap.Round)
    drawLine(bodyColor, Offset(bodyX, hipY), Offset(kneeFrontX, knee1Y), limbWidth, StrokeCap.Round)
    drawLine(bodyColor, Offset(kneeFrontX, knee1Y), Offset(kneeFrontX - (w * 0.01f), floorY), limbWidth * 0.9f, StrokeCap.Round)
}

// -----------------------------------------------------------------------------------------
// 28. BOX JUMP MOTION
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawBoxJumpMotion(
    p: Float,
    w: Float,
    h: Float,
    headRadius: Float,
    limbWidth: Float,
    equipWidth: Float,
    bodyColor: Color,
    accentColor: Color
) {
    val floorY = h * 0.90f
    drawLine(bodyColor.copy(alpha = 0.35f), Offset(w * 0.16f, floorY), Offset(w * 0.84f, floorY), limbWidth * 0.8f, StrokeCap.Round)

    val lift = sin(p * PI.toFloat()) * h * 0.12f
    val bodyX = w * 0.50f
    val hipY = h * 0.56f - lift
    val shoulderY = h * 0.34f - lift
    val headY = shoulderY - (headRadius * 1.5f)

    val footSpread = w * 0.08f + w * 0.08f * p
    val footLift = h * 0.14f * p

    val reachX = w * 0.18f + w * 0.10f * p
    val reachY = -(h * 0.20f) * p

    val elbowLeftX = bodyX - (reachX * 0.6f)
    val elbowRightX = bodyX + (reachX * 0.6f)
    val elbowY = shoulderY + (reachY * 0.35f)
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

    val boxW = w * 0.20f
    val boxH = w * 0.16f
    val boxTopY = floorY - footLift - boxH
    drawLine(accentColor, Offset(bodyX - boxW / 2, floorY - footLift), Offset(bodyX + boxW / 2, floorY - footLift), equipWidth * 1.2f, StrokeCap.Round)
    drawLine(accentColor, Offset(bodyX - boxW / 2, floorY - footLift), Offset(bodyX - boxW / 2, boxTopY), equipWidth, StrokeCap.Round)
    drawLine(accentColor, Offset(bodyX + boxW / 2, floorY - footLift), Offset(bodyX + boxW / 2, boxTopY), equipWidth, StrokeCap.Round)
    drawLine(accentColor, Offset(bodyX - boxW / 2, boxTopY), Offset(bodyX + boxW / 2, boxTopY), equipWidth * 1.1f, StrokeCap.Round)
}