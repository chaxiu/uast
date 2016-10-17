package org.jetbrains.uast.evaluation

import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.values.UValue

// Role: at the current state, evaluate expression(s)
interface UEvaluator {
    fun analyze(method: UMethod, state: UEvaluationState = method.evaluationState())

    fun evaluate(expression: UExpression, state: UEvaluationState = expression.evaluationState()): UValue
}

fun createEvaluator(context: UastContext): UEvaluator =
        TreeBasedEvaluator(context)

fun UExpression.evaluate(context: UastContext) = createEvaluator(context).evaluate(this)