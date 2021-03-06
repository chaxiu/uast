package org.jetbrains.uast.values

import com.intellij.psi.PsiType
import org.jetbrains.uast.UVariable

class UVariableValue private constructor(
        val variable: UVariable,
        value: UValue,
        dependencies: Set<UDependency>
) : UDependentValue(value, dependencies), UDependency {

    override fun identityEquals(other: UValue): UValue =
            if (this == other) super.valueEquals(other)
            else when (variable.psi.type) {
                PsiType.BYTE, PsiType.FLOAT, PsiType.DOUBLE, PsiType.LONG,
                PsiType.SHORT, PsiType.INT, PsiType.CHAR, PsiType.BOOLEAN -> super.valueEquals(other)

                else -> UUndeterminedValue
            }

    override fun merge(other: UValue) = when (other) {
        this -> this
        value -> this
        is UVariableValue -> {
            if (variable != other.variable || value != other.value) UPhiValue.create(this, other)
            else create(variable, value, dependencies + other.dependencies)
        }
        is UDependentValue -> {
            if (value != other.value) UPhiValue.create(this, other)
            else create(variable, value, dependencies + other.dependencies)
        }
        else -> UPhiValue.create(this, other)
    }

    override fun copy(dependencies: Set<UDependency>) =
            if (dependencies == this.dependencies) this else create(variable, value, dependencies)

    override fun coerceConstant(constant: UConstant): UValue =
            if (constant == toConstant()) this
            else create(variable, value.coerceConstant(constant), dependencies)

    override fun equals(other: Any?) =
            other is UVariableValue
            && variable == other.variable
            && value == other.value
            && dependencies == other.dependencies

    override fun hashCode(): Int {
        var result = 31
        result = result * 19 + variable.hashCode()
        result = result * 19 + value.hashCode()
        result = result * 19 + dependencies.hashCode()
        return result
    }

    override fun toString() = "(var ${variable.name ?: "<unnamed>"} = ${super.toString()})"

    companion object {

        private fun Set<UDependency>.filterNot(variable: UVariable) =
                filterTo(linkedSetOf()) { it !is UVariableValue || variable != it.variable }

        fun create(variable: UVariable, value: UValue, dependencies: Set<UDependency> = emptySet()): UVariableValue {
            when (variable.psi.type) {
                PsiType.BYTE, PsiType.SHORT -> {
                    val constant = value.toConstant()
                    if (constant is UIntConstant && constant.type == UNumericType.INT) {
                        val castConstant = UIntConstant(constant.value, variable.psi.type)
                        return create(variable, value.coerceConstant(castConstant), dependencies)
                    }
                }
            }
            val dependenciesWithoutSelf = dependencies.filterNot(variable)
            return when {
                value is UVariableValue
                && variable == value.variable
                && dependenciesWithoutSelf == value.dependencies -> value

                value is UDependentValue -> {
                    val valueDependencies = value.dependencies.filterNot(variable)
                    val modifiedValue = value.copy(valueDependencies)
                    UVariableValue(variable, modifiedValue, dependenciesWithoutSelf)
                }

                else -> UVariableValue(variable, value, dependenciesWithoutSelf)
            }
        }
    }
}
