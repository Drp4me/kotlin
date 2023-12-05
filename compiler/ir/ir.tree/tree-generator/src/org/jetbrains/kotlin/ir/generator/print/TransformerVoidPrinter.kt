/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.generator.print

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.generators.tree.*
import org.jetbrains.kotlin.generators.tree.printer.*
import org.jetbrains.kotlin.ir.generator.IrTree
import org.jetbrains.kotlin.ir.generator.TREE_GENERATOR_README
import org.jetbrains.kotlin.ir.generator.elementTransformerType
import org.jetbrains.kotlin.ir.generator.elementTransformerVoidType
import org.jetbrains.kotlin.ir.generator.model.Element
import org.jetbrains.kotlin.ir.generator.model.Field
import org.jetbrains.kotlin.ir.generator.model.Model
import org.jetbrains.kotlin.utils.SmartPrinter
import org.jetbrains.kotlin.utils.withIndent
import java.io.File

private class TransformerVoidPrinter(
    printer: SmartPrinter,
    override val visitorType: ClassRef<*>,
) : AbstractVisitorPrinter<Element, Field>(printer) {

    override val visitorTypeParameters: List<TypeVariable>
        get() = emptyList()

    override val visitorSuperType: ClassRef<PositionTypeParameterRef>
        get() = elementTransformerType.withArgs(visitorDataType)

    override val visitorDataType: TypeRef
        get() = StandardTypes.nothing.copy(nullable = true)

    // IrPackageFragment is treated as transformByChildren in IrElementTransformerVoid for historical reasons.
    private val Element.isPackageFragment: Boolean
        get() = this == IrTree.packageFragment

    // Despite IrFile and IrExternalPackageFragment being transformByChildren, we treat them differently in IrElementTransformerVoid
    // than in IrElementTransformer for historical reasons. We want to preserve the historical semantics here.
    private val Element.isPackageFragmentChild: Boolean
        get() = elementParents.any { it.element.isPackageFragment }

    private val Element.transformByChildrenVoid: Boolean
        get() = element.transformByChildren || isPackageFragment

    override fun visitMethodReturnType(element: Element): Element =
        when {
            element.isPackageFragment -> element
            element.transformByChildren -> element.getTransformExplicitType()
            else -> element.parentInVisitor?.let(this::visitMethodReturnType) ?: element
        }

    override val allowTypeParametersInVisitorMethods: Boolean
        get() = false

    context(ImportCollector)
    override fun SmartPrinter.printAdditionalMethods() {
        println()
        val typeParameter = TypeVariable("T", listOf(IrTree.rootElement))
        printFunctionWithBlockBody(
            name = "transformPostfix",
            parameters = listOf(FunctionParameter("body", Lambda(receiver = typeParameter, returnType = StandardTypes.unit))),
            returnType = typeParameter,
            typeParameters = listOf(typeParameter),
            extensionReceiver = typeParameter,
            visibility = Visibility.PROTECTED,
            isInline = true,
        ) {
            println("transformChildrenVoid()")
            println("this.body()")
            println("return this")
        }
        println()
        printFunctionWithBlockBody(
            name = "transformChildrenVoid",
            parameters = emptyList(),
            returnType = StandardTypes.unit,
            extensionReceiver = IrTree.rootElement,
            visibility = Visibility.PROTECTED,
        ) {
            println("transformChildrenVoid(this@", visitorType.simpleName, ")")
        }
    }

    context(ImportCollector)
    override fun printMethodsForElement(element: Element) {
        val parent = element.parentInVisitor
        if (!element.transformByChildrenVoid && parent == null) return
        printer.run {
            println()
            printVisitMethodDeclaration(element, hasDataParameter = false, modality = Modality.OPEN)
            if (element.transformByChildrenVoid && !element.isPackageFragmentChild) {
                printBlock {
                    println(element.visitorParameterName, ".transformChildren(this, null)")
                    println("return ", element.visitorParameterName)
                }
            } else {
                println(" =")
                withIndent {
                    print(parent!!.visitFunctionName, "(", element.visitorParameterName, ")")
                    if (element.isPackageFragmentChild) {
                        print(" as ", element.render())
                    }
                    println()
                }
            }
            println()
            printVisitMethodDeclaration(
                element = element,
                modality = Modality.FINAL,
                override = true,
                returnType = element.getTransformExplicitType(),
            )
            println(" =")
            withIndent {
                println(element.visitFunctionName, "(", element.visitorParameterName, ")")
            }
        }
    }
}

fun printTransformerVoid(generationPath: File, model: Model): GeneratedFile =
    printGeneratedType(
        generationPath,
        TREE_GENERATOR_README,
        elementTransformerVoidType.packageName,
        elementTransformerVoidType.simpleName,
    ) {
        TransformerVoidPrinter(this, elementTransformerVoidType).printVisitor(model.elements)
        println()
        val transformerParameter = FunctionParameter("transformer", elementTransformerVoidType)
        printFunctionWithBlockBody(
            name = "transformChildrenVoid",
            parameters = listOf(transformerParameter),
            returnType = StandardTypes.unit,
            extensionReceiver = IrTree.rootElement,
        ) {
            println("transformChildren(", transformerParameter.name, ", null)")
        }
    }
