
//Automatically points to the starsector folder if the mod is placed in to the "mods" folder.
//If you do not place the project in to your mods folder, replace this with the path to Starsectors root folder.
val starsectorPath= "../../";

//The name of the file that the code is compiled to. This will automatically place in to the /jars folder.
val jarName = "TemplateMod.jar"

//Name for the Zip that is created after each build.
//This zip includes the data, graphics, jars, sounds and src folder.
//It also includes the mod_info.json and .version files at the root folder.
val zipName = "TemplateMod.zip"

//Files and folders (relative to the project root) included in the packaged zip.
//Directories keep their structure in the zip; files are placed at the zip root.
//Missing entries are silently skipped by Gradle.
val packageIncludes = listOf(
    "mod_info.json",
    "data",
    "graphics",
    "sounds",
    "src",
)

//File extensions to include from the project root in the packaged zip.
//Each entry is matched as "*.<ext>" against files directly in the project root.
val packageIncludeExtensions = listOf(
    "version",
)

//Other mods to load as compile-time dependencies. Adding them will provide auto-complete for their functions.
//Each entry is the jar name. The build searches every mod /jars/ folder for a matching file ("LazyLib.jar" -> "Starsector/mods/LazyLib/jars/LazyLib.jar")
//Mods added this way still need to be added to mod_info.json if they are always required (hard-dependency).
val modDependencies = listOf(
    "LazyLib.jar",
    "MagicLib.jar",
	"LunaLib.jar",
)

//Additional jars to include, like libraries you ship with your mod.
//Paths are relative to this projects root directory.
val otherDependencies = listOf<String>(
    // "jars/dependency.jar",
)

//Java version to use. Should be 17, as it is what starsector itself uses.
val javaVersion = 17












/// BUILD PIPELINE
/// In Most cases, you should not need to change anything below here.

dependencies {
    addModJars(modDependencies)
    otherDependencies.forEach { addCompileOnlyJar(it) }

    //Loads basic starsector dependencies.
    addStarsectorCoreDependencies()
}

fun DependencyHandler.addStarsectorCoreDependencies() {

    //Starsectors core jars live in different folders per OS, so look them up through the layout.
    val coreDir = starsectorLayout().gameWorkingDir

    //Starsector
    compileOnly(files(File(coreDir, "starfarer.api.jar")))
    compileOnly(files(File(coreDir, "starfarer_obf.jar")))

    //Starsector dependencies
    compileOnly(files(File(coreDir, "commons-compiler.jar")))
    compileOnly(files(File(coreDir, "commons-compiler-jdk.jar")))
    compileOnly(files(File(coreDir, "fs.common_obf.jar")))
    compileOnly(files(File(coreDir, "fs.sound_obf.jar")))
    compileOnly(files(File(coreDir, "janino.jar")))
    compileOnly(files(File(coreDir, "jaxb-api-2.4.0-b180830.0359.jar")))
    compileOnly(files(File(coreDir, "jaxb-api-2.4.0-b180830.0359-sources.jar")))
    compileOnly(files(File(coreDir, "jinput.jar")))
    compileOnly(files(File(coreDir, "jogg-0.0.7.jar")))
    compileOnly(files(File(coreDir, "jorbis-0.0.15.jar")))
    compileOnly(files(File(coreDir, "json.jar")))

    compileOnly(files(File(coreDir, "log4j-1.2.9.jar")))
    compileOnly(files(File(coreDir, "lwjgl.jar")))
    compileOnly(files(File(coreDir, "lwjgl_util.jar")))

    compileOnly(files(File(coreDir, "txw2-3.0.2.jar")))
    compileOnly(files(File(coreDir, "webp-imageio-0.1.6.jar")))
    compileOnly(files(File(coreDir, "xstream-1.4.10.jar")))
}

fun DependencyHandler.addModJars(jarNames: List<String>) {
    if (jarNames.isEmpty()) return

    val modsDir = file("$starsectorPath/mods/")
    // Exclude this project's own folder. Otherwise, the configuration cache
    // treats the mods own /jars/ directory listing as a config-time input, and
    // every rebuild of the mod jar invalidates the cache.
    val thisProjectFolder = projectDir.name
    val modJarFiles = fileTree(modsDir) {
        jarNames.forEach { include("*/jars/**/$it") }
        exclude("$thisProjectFolder/**")
    }

    // Realize the file tree once to detect missing entries.
    val foundNames = modJarFiles.files.map { it.name }.toSet()
    jarNames.filterNot { it in foundNames }.forEach { missing ->
        logger.error(
            "Mod dependency '$missing' was not found in any mod's " +
                "/jars folder under ${modsDir.absolutePath}. "
        )
    }

    compileOnly(modJarFiles)
}

fun DependencyHandler.addCompileOnlyJar(path: String) {
    val jarFile = file(path)
    if (!jarFile.exists()) {
        logger.error("Dependency '$path' was not found at ${jarFile.absolutePath}.")
        return
    }
    compileOnly(files(jarFile))
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
        kotlin {
            setSrcDirs(listOf("src"))
        }
    }
}


tasks.test {
    enabled = false
}

tasks.jar {
    destinationDirectory.set(file("$rootDir/jars"))
    archiveFileName.set(jarName)
}


enum class StarsectorPlatform { WINDOWS, LINUX, MAC }

// Functions rather than vals so they can be called from the `dependencies {}`
// block at the top of the script, which runs before any val declared below it
// would be initialized.
fun currentPlatform(): StarsectorPlatform = System.getProperty("os.name").lowercase().let { os ->
    when {
        "win" in os -> StarsectorPlatform.WINDOWS
        "mac" in os || "darwin" in os -> StarsectorPlatform.MAC
        else -> StarsectorPlatform.LINUX
    }
}

//Holds the per-OS paths Starsector needs: the launcher file, the bundled java executable, and the games working dir.
data class StarsectorLayout(
    val launcherFile: File,
    val javaExecutable: File,
    val gameWorkingDir: File,
)

//Resolves all three paths for the current OS. Starsector ships a different folder structure on each platform.
fun starsectorLayout(): StarsectorLayout = file(starsectorPath).let { root ->
    when (currentPlatform()) {
        StarsectorPlatform.WINDOWS -> StarsectorLayout(
            launcherFile = File(root, "vmparams"),
            javaExecutable = File(root, "jre/bin/java.exe"),
            gameWorkingDir = File(root, "starsector-core"),
        )
        StarsectorPlatform.LINUX -> StarsectorLayout(
            launcherFile = File(root, "starsector.sh"),
            javaExecutable = File(root, "jre_linux/bin/java"),
            gameWorkingDir = root,
        )
        StarsectorPlatform.MAC -> StarsectorLayout(
            launcherFile = File(root, "Contents/MacOS/starsector_mac.sh"),
            javaExecutable = File(root, "Contents/Home/bin/java"),
            gameWorkingDir = File(root, "Contents/Resources/Java"),
        )
    }
}

data class StarsectorLaunchSpec(
    val jvmArgs: List<String>,
    val classpath: List<File>,
    val mainClass: String,
)

//Intermediate value returned by each per-platform parser. Classpath entries are still
//strings here. parseLauncher() resolves them to absolute Files against the working dir.
data class RawLaunchSpec(
    val jvmArgs: List<String>,
    val classpath: List<String>,
    val mainClass: String,
)

//vmparams is a single line file. Just split on whitespace.
fun parseWindowsLauncher(file: File): RawLaunchSpec {
    val tokens = file.readText().trim().split(Regex("\\s+"))
    return sliceJavaCommand(tokens, classpathSeparator = ';', sourceForError = file)
}

//starsector.sh is a shell script. Drop comment lines, join `\`-continuations, then tokenize.
fun parseLinuxLauncher(file: File): RawLaunchSpec {
    val tokens = file.readLines()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n")
        .replace(Regex("""\\\r?\n"""), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
    return sliceJavaCommand(tokens, classpathSeparator = ':', sourceForError = file)
}


//Same as Linux, but the mac script also wraps args in quotes and uses shell vars like ${EXTRAARGS}.
//Strip the quotes and drop unexpanded shell vars before tokenizing.
fun parseMacLauncher(file: File): RawLaunchSpec {
    val tokens = file.readLines()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n")
        .replace(Regex("""\\\r?\n"""), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .map { it.replace("\"", "") }           // strip shell quotes
        .filterNot { it.startsWith("\${") }      // drop unexpanded shell vars (e.g. ${EXTRAARGS})
    return sliceJavaCommand(tokens, classpathSeparator = ':', sourceForError = file)
}

//Given the tokens from a launcher file, finds the `java` invocation and pulls out the parts
//we need: jvmArgs (everything between `java` and `-classpath`), the classpath entries, and the main class.
fun sliceJavaCommand(
    tokens: List<String>,
    classpathSeparator: Char,
    sourceForError: File,
): RawLaunchSpec {
    // Case-sensitive: `../Resources/Java` (capital J) must NOT match the Mac
    // script's `cd` target.
    val javaIdx = tokens.indexOfFirst { token ->
        val basename = token.substringAfterLast('/').substringAfterLast('\\')
        basename == "java" || basename == "java.exe"
    }
    require(javaIdx >= 0) { "Could not locate the java invocation in $sourceForError" }

    val cpIdx = (javaIdx + 1 until tokens.size).firstOrNull { i ->
        tokens[i] == "-classpath" || tokens[i] == "-cp"
    } ?: error("Could not locate -classpath/-cp in $sourceForError")
    require(cpIdx + 2 < tokens.size) {
        "Missing classpath value or main class in $sourceForError"
    }

    val classpath = tokens[cpIdx + 1].split(classpathSeparator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return RawLaunchSpec(
        jvmArgs = tokens.subList(javaIdx + 1, cpIdx),
        classpath = classpath,
        mainClass = tokens[cpIdx + 2],
    )
}

//Reads the launcher for the current OS and returns a fully resolved launch spec.
//Relative classpath entries get resolved against the games working directory.
fun parseLauncher(): StarsectorLaunchSpec {
    val layout = starsectorLayout()
    val launcherFile = layout.launcherFile
    require(launcherFile.exists()) {
        "Starsector launcher file not found at ${launcherFile.absolutePath} " +
                "(expected for platform=${currentPlatform()})"
    }

    val raw = when (currentPlatform()) {
        StarsectorPlatform.WINDOWS -> parseWindowsLauncher(launcherFile)
        StarsectorPlatform.LINUX -> parseLinuxLauncher(launcherFile)
        StarsectorPlatform.MAC -> parseMacLauncher(launcherFile)
    }

    val workingDirPath = layout.gameWorkingDir.toPath()
    val classpath = raw.classpath.map { workingDirPath.resolve(it).normalize().toFile() }
    return StarsectorLaunchSpec(raw.jvmArgs, classpath, raw.mainClass)
}

//Builds the mod jar, then runs Starsector using the same java/classpath/jvmArgs the launcher would use.
tasks.register<JavaExec>("runStarsector") {
    group = "starsector"
    description = "Build the mod and launch Starsector (with launcher)."
    dependsOn(tasks.jar)

    val layout = starsectorLayout()
    val parsed = parseLauncher()
    setExecutable(layout.javaExecutable.absolutePath)
    workingDir = layout.gameWorkingDir
    mainClass.set(parsed.mainClass)
    classpath = files(parsed.classpath)
    jvmArgs = parsed.jvmArgs
}

//Same as above, but skips the launcher window and jumps straight in to the game.
//The extra -D flags are the same ones the launcher passes when you hit play, so the game gets the settings it expects.
tasks.register<JavaExec>("runStarsectorNoLauncher") {
    group = "starsector"
    description = "Build the mod and launch Starsector, skipping the launcher."
    dependsOn(tasks.jar)

    val layout = starsectorLayout()
    val parsed = parseLauncher()
    setExecutable(layout.javaExecutable.absolutePath)
    workingDir = layout.gameWorkingDir
    mainClass.set(parsed.mainClass)
    classpath = files(parsed.classpath)
    jvmArgs = listOf(
        "-DstartRes=1920x1080",
        "-DlaunchDirect=true",
        "-DstartFS=false",
        "-DstartSound=true",
    ) + parsed.jvmArgs
}

tasks.register<Zip>("packageMod") {
    group = "distribution"
    description = "Packages the mod into a ZIP file for release."

    // The name of the resulting zip file
    archiveFileName.set(zipName)
    // Where to put the zip
    destinationDirectory.set(layout.projectDirectory)

    // 1. Include the compiled jar from the build task
    from(tasks.jar) {
        into("jars") // Optional: place inside a jar folder in the zip
    }

    // 2. Include the files and folders listed in packageIncludes.
    // Directories are placed into a same-named folder in the zip; files go at the root.
    packageIncludes.forEach { name ->
        val source = file(name)
        if (source.isDirectory) {
            from(source) { into(name) }
        } else {
            from(source)
        }
    }

    // 3. Include any project-root files matching packageIncludeExtensions.
    from(projectDir) {
        packageIncludeExtensions.forEach { ext -> include("*.$ext") }
    }
}


