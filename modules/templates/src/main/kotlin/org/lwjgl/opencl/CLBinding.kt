/* 
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.opencl

import java.io.PrintWriter
import org.lwjgl.generator.*

private val NativeClass.capName: String
	get() = if ( templateName.startsWith(prefix) ) {
		if ( prefix == "CL" )
			"OpenCL${templateName.substring(2)}"
		else
			templateName
	} else {
		"${prefixTemplate}_$templateName"
	}

private val CAPABILITIES_CLASS = "CLCapabilities"

private val CLBinding = Generator.register(object: APIBinding(OPENCL_PACKAGE, CAPABILITIES_CLASS) {

	override fun PrintWriter.generateFunctionGetters(nativeClass: NativeClass) {
		println("\t// --- [ Function Addresses ] ---\n")

		println("\t/** Returns the {@link ${nativeClass.className}} instance for the currently loaded ICD. */")
		println("\tpublic static ${nativeClass.className} getInstance() {")
		println("\t\treturn checkFunctionality(CL.getICD().__${nativeClass.className});")
		println("\t}")

		println("\n\tstatic ${nativeClass.className} create(FunctionProvider provider) {")

		println("\t\t${nativeClass.className} funcs = new ${nativeClass.className}(provider);")

		print("\n\t\tboolean supported = checkFunctions(")
		nativeClass.printPointers(this)
		println(");")

		println("\n\t\treturn supported ? funcs : null;")
		println("\t}\n")
	}

	override fun PrintWriter.generateContent() {
		println("""/**
 * Defines the capabilities of an OpenCL platform or device.
 *
 * <p>The instance returned by {@link CLPlatform#createCapabilities} )} exposes the functionality present on either the platform or any of its devices. This is
 * unlike the {@link CL10#CL_PLATFORM_EXTENSIONS} string, which returns only platform functionality, supported across all platform devices.</p>
 *
 * <p>The instance returned by {@link CLDevice#createCapabilities} exposes only the functionality available on that particular device.</p>
 */""")
		println("public class $CAPABILITIES_CLASS {\n")

		val classes = super.getClasses { o1, o2 ->
			// Core functionality first, extensions after
			val isCL1 = o1.templateName.startsWith("CL")
			val isCL2 = o2.templateName.startsWith("CL")

			if ( isCL1 xor isCL2 )
				(if ( isCL1 ) -1 else 1)
			else
				o1.templateName.compareTo(o2.templateName, ignoreCase = true)
		}

		val classesWithFunctions = classes.filter { it.hasNativeFunctions }
		val alignment = classesWithFunctions.map { it.className.length() }.fold(0) { left, right -> Math.max(left, right) }
		for ( extension in classesWithFunctions ) {
			print("\tfinal ${extension.className}")
			for ( i in 0..(alignment - extension.className.length() - 1) )
				print(' ')
			println(" __${extension.className};")
		}

		println("""
	/** The OpenCL major version. */
	public final int majorVersion;

	/** The OpenCL minor version. */
	public final int minorVersion;
""")

		classes forEach {
			val documentation = it.documentation
			if ( documentation != null )
				println((if ( it.hasBody ) "When true, {@link ${it.className}} is supported." else documentation).toJavaDoc())
			println("\tpublic final boolean ${it.capName};")
		}

		// ICD constructor
		println("""
	$CAPABILITIES_CLASS(FunctionProvider provider) {
		this.majorVersion = 0;
		this.minorVersion = 0;
""")
		for ( extension in classes ) {
			val capName = extension.capName
			if ( extension.hasNativeFunctions ) {
				println("\t\t$capName = (__${extension.className} = ${extension.className}.create(provider)) != null;")
			} else
				println("\t\t$capName = false;")
		}
		println("\t}")

		// Platform/Device constructor
		println("""
	$CAPABILITIES_CLASS(int majorVersion, int minorVersion, Set<String> ext, $CAPABILITIES_CLASS caps) {
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
""")
		for ( extension in classes ) {
			val capName = extension.capName
			if ( extension.hasNativeFunctions ) {
				println("\t\t$capName = (__${extension.className} = CL.checkExtension(ext, \"$capName\", caps.__${extension.className})) != null;")
			} else
				println("\t\t$capName = ext.contains(\"$capName\");")
		}
		println("\t}")

		println("""
	public String toString() {
		StringBuilder buf = new StringBuilder(512);

		buf.append("OpenCL ").append(majorVersion).append('.').append(minorVersion);
		buf.append(" - Extensions: ");
""")
		for ( extension in classes ) {
			if ( extension.templateName.startsWith("CL") )
				continue

			val capName = extension.capName
			println("\t\tif ( $capName ) buf.append(\"$capName \");")
		}
		println("""
		return buf.toString();
	}
""")

		print("}")
	}

})

// DSL Extensions

fun String.nativeClassCL(templateName: String, postfix: String = "", init: (NativeClass.() -> Unit)? = null) =
	nativeClass("org.lwjgl.opencl", templateName, prefix = "CL", postfix = postfix, prefixTemplate = "cl", binding = CLBinding, init = init)

val NativeClass.extensionLink: String
	get() = url("http://www.khronos.org/registry/cl/extensions/${templateName.substring(0, templateName.indexOf('_'))}/cl_$templateName.txt", templateName)

val NativeClass.extensionName: String
	get() = "<strong>$templateName</strong>"