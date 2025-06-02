import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: kotlin ProjectAnalyzerKt <project_dir>")
        return
    }
    val projectDir = File(args[0])
    val kotlinFiles = projectDir.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.toList()
    val classes = mutableListOf<ClassInfo>()

    // Parse classes and build info
    for (file in kotlinFiles) {
        classes += parseClasses(file)
    }

    // Build inheritance tree
    val classMap = classes.associateBy { it.name }
    for (cls in classes) {
        cls.parent?.let { parent ->
            classMap[parent]?.children?.add(cls)
        }
    }

    // DIT and NOC
    for (cls in classes) {
        cls.dit = computeDIT(cls, classMap)
        cls.noc = cls.children.size
    }

    // MOOD metrics (project-level and per-class)
    val (mhfNumer, mhfDenom) = classes.fold(0 to 0) { acc, c ->
        acc.first + c.methods.count { it.visibility == "private" } to
                acc.second + c.methods.size
    }
    val mhf = safeDiv(mhfNumer, mhfDenom)

    val (ahfNumer, ahfDenom) = classes.fold(0 to 0) { acc, c ->
        acc.first + c.attrs.count { it.visibility == "private" } to
                acc.second + c.attrs.size
    }
    val ahf = safeDiv(ahfNumer, ahfDenom)

    val (mifNumer, mifDenom) = classes.fold(0 to 0) { acc, c ->
        val (inherited, total) = c.getInheritedMethodStats(classMap)
        acc.first + inherited to acc.second + total
    }
    val mif = safeDiv(mifNumer, mifDenom)

    val (aifNumer, aifDenom) = classes.fold(0 to 0) { acc, c ->
        val (inherited, total) = c.getInheritedAttrStats(classMap)
        acc.first + inherited to acc.second + total
    }
    val aif = safeDiv(aifNumer, aifDenom)

    val (pofNumer, pofDenom) = classes.fold(0 to 0) { acc, c ->
        val overridden = c.methods.count { it.overrides }
        val newMethods = c.methods.size - overridden
        acc.first + overridden to acc.second + (newMethods * c.noc)
    }
    val pof = safeDiv(pofNumer, pofDenom)

    println("Project metrics:")
    println("MHF = %.3f".format(mhf))
    println("AHF = %.3f".format(ahf))
    println("MIF = $mif")
    println("AIF = $aif")
    println("POF = %.3f".format(pof))
    println()
    println("Class, DIT, NOC")

    for (cls in classes) {
        println("${cls.name}, ${cls.dit}, ${cls.noc}")
    }
}

fun safeDiv(numer: Int, denom: Int): Double = if (denom == 0) 0.0 else numer.toDouble() / denom

data class ClassInfo(
    val name: String,
    val parent: String?,
    val methods: List<MethodInfo>,
    val attrs: List<AttrInfo>
) {
    val children = mutableListOf<ClassInfo>()
    var dit = 1
    var noc = 0

    fun getInheritedMethodStats(classMap: Map<String, ClassInfo>): Pair<Int, Int> {
        val inherited = parent?.let { classMap[it]?.methods?.size ?: 0 } ?: 0
        return inherited to (inherited + methods.size)
    }
    fun getInheritedAttrStats(classMap: Map<String, ClassInfo>): Pair<Int, Int> {
        val inherited = parent?.let { classMap[it]?.attrs?.size ?: 0 } ?: 0
        return inherited to (inherited + attrs.size)
    }
}

data class MethodInfo(val name: String, val visibility: String, val overrides: Boolean)
data class AttrInfo(val name: String, val visibility: String)

fun parseClasses(file: File): List<ClassInfo> {
    val text = file.readText()
    val classRegex = Regex("""(?:open|data|sealed|abstract|final|inner)?\s*class\s+(\w+)(?:\s*:\s*([\w<>]+)(?:\([^)]*\))?)?""")
    val methodRegex = Regex("""(private|protected|public)?\s*(override\s+)?fun\s+(\w+)""")
    val attrRegex = Regex("""(private|protected|public)?\s*(val|var)\s+(\w+)""")
    val matches = classRegex.findAll(text)
    return matches.map { match ->
        val className = match.groupValues[1]
        val parent = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        val classBodyStart = match.range.last + 1
        val bodyText = text.drop(classBodyStart)
        val nextClass = classRegex.find(bodyText)
        val classBodyEnd = nextClass?.range?.first ?: bodyText.length
        val classContent = bodyText.substring(0, classBodyEnd)
        val methods = methodRegex.findAll(classContent).map {
            MethodInfo(
                it.groupValues[3],
                it.groupValues[1].ifBlank { "public" },
                it.groupValues[2].isNotBlank()
            )
        }.toList()
        val attrs = attrRegex.findAll(classContent).map {
            AttrInfo(
                it.groupValues[3],
                it.groupValues[1].ifBlank { "public" }
            )
        }.toList()
        ClassInfo(className, parent, methods, attrs)
    }.toList()
}


fun computeDIT(cls: ClassInfo, classMap: Map<String, ClassInfo>): Int {
    return 1 + (cls.parent?.let { classMap[it]?.let { computeDIT(it, classMap) } ?: 0 } ?: 0)
}
