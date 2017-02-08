package org.jetbrains.uast

import com.intellij.lang.Language
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.uast.psi.PsiElementBacked

class UastContext(val project: Project) : UastLanguagePlugin {
    private companion object {
        private val CONTEXT_LANGUAGE = object : Language("UastContextLanguage") {}
    }

    override val language: Language
        get() = CONTEXT_LANGUAGE

    override val priority: Int
        get() = 0

    val languagePlugins: Collection<UastLanguagePlugin>
        get() = UastLanguagePlugin.getInstances()

    fun findPlugin(element: PsiElement): UastLanguagePlugin? {
        val language = element.language
        return languagePlugins.firstOrNull { it.language == language }
    }

    override fun isFileSupported(fileName: String) = languagePlugins.any { it.isFileSupported(fileName) }

    fun getMethod(method: PsiMethod): UMethod = convertWithParent<UMethod>(method)!!

    fun getVariable(variable: PsiVariable): UVariable = convertWithParent<UVariable>(variable)!!

    fun getClass(clazz: PsiClass): UClass = convertWithParent<UClass>(clazz)!!

    override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
        return findPlugin(element)?.convertElement(element, parent, requiredType)
    }

    override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
        return findPlugin(element)?.convertElementWithParent(element, requiredType)
    }

    override fun getMethodCallExpression(
            element: PsiElement,
            containingClassFqName: String?,
            methodName: String
    ): UastLanguagePlugin.ResolvedMethod? {
        return findPlugin(element)?.getMethodCallExpression(element, containingClassFqName, methodName)
    }

    override fun getConstructorCallExpression(
            element: PsiElement,
            fqName: String
    ): UastLanguagePlugin.ResolvedConstructor? {
        return findPlugin(element)?.getConstructorCallExpression(element, fqName)
    }

    override fun isExpressionValueUsed(element: UExpression): Boolean {
        val language = element.getLanguage()
        return (languagePlugins.firstOrNull { it.language == language })?.isExpressionValueUsed(element) ?: false
    }

    private tailrec fun UElement.getLanguage(): Language {
        if (this is PsiElementBacked) {
            psi?.language?.let { return it }
        }
        val containingElement = this.containingElement ?: throw IllegalStateException("At least UFile should have a language")
        return containingElement.getLanguage()
    }
}

fun PsiElement?.toUElement() =
        this?.let { ServiceManager.getService(project, UastContext::class.java).convertElementWithParent(this, null) }

fun <T : UElement> PsiElement?.toUElement(cls: Class<out T>): T? =
        this?.let { ServiceManager.getService(project, UastContext::class.java).convertElementWithParent(this, cls) as T? }

inline fun <reified T : UElement> PsiElement?.toUElementOfType(): T? =
        this?.let { ServiceManager.getService(project, UastContext::class.java).convertElementWithParent(this, T::class.java) as T? }

fun <T : UElement> PsiFile.findUElementAt(offset: Int, cls: Class<out T>): T? {
    val element = findElementAt(offset) ?: return null
    val uElement = element.toUElement() ?: return null
    @Suppress("UNCHECKED_CAST")
    return uElement.withContainingElements.firstOrNull { cls.isInstance(it) } as T?
}

@JvmOverloads
fun <T : UElement> PsiElement?.getUParentOfType(cls: Class<out T>, strict: Boolean = false): T? {
    val uElement = this.toUElement() ?: return null
    val sequence = if (strict)
        (uElement.containingElement?.withContainingElements ?: emptySequence())
    else
        uElement.withContainingElements

    @Suppress("UNCHECKED_CAST")
    return sequence.firstOrNull { cls.isInstance(it) } as T?
}

inline fun <reified T : UElement> PsiElement?.getUParentOfType(strict: Boolean = false): T? = getUParentOfType(T::class.java, strict)
