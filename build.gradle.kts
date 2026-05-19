
//Automatically points to the starsector folder if the mod is placed in to the "mods" folder.
//If you do not place the project in to your mods folder, replace this with the path to Starsectors root folder.
val starsectorPath= "../../";

//The name of the file that the code is compiled to. This will automatically place in to the /jars folder.
val jarName = "TemplateMod.jar"

//Name for the Zip that is created when you run package_mod.bat.
//This zip includes the data, graphics, jars, sounds and src folder.
//It also includes the mod_info.json and .version files at the root folder.
val zipName = "TemplateMod.zip"

//Other mods to load as compile-time dependencies. Adding them will provide auto-complete for their functions.
//Each entry is the jar name. The build searches every mod /jars/ folder for a matching file ("LazyLib.jar" -> "Starsector/mods/LazyLib/jars/LazyLib.jar")
//Mods added this way still need to be added to mod_info.json if they are always required (hard-dependency).
val modDependencies = listOf(
    "LazyLib.jar", //LazyLib
    "MagicLib.jar", //MagicLib
    "MagicLib-Kotlin.jar",
    "Graphics.jar", //GraphicsLib
    "LunaLib.jar", //LunaLib

    //"ExerelinCore.jar", //Nexerelin
)






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

//Additional jars to include, like libraries you ship with your mod.
//Paths are relative to this projects root directory.
val otherDependencies = listOf<String>(
    // "jars/dependency.jar",
)

//Folder (relative to this project root) that is also searched for modDependencies and otherDependencies.
//Drop jars here when you don't have the source mod installed under /mods/, or want to pin a specific version.
//For modDependencies, files are matched by filename (recursively).
//For otherDependencies, the entry's path is also tried relative to this folder.
val libsFolder = "libs"

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

    //Starsector. The API jar comes through the local Maven repo (see repositories block) so IntelliJ can attach its source.
    //starfarer_obf is obfuscated with no source available, so it stays a plain file dependency.
    compileOnly("com.fs.starfarer:starfarer-api:local")
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



plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()

    //Local Maven repo of staged Starsector API artifacts. The maven layout (vs flatDir) is what
    //actually lets IntelliJ pick up the "-sources.jar" sibling for autocomplete and navigation.
    maven { url = uri(stageStarsectorApi()) }
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

    // Also look inside the local libs folder, if present. Matched by filename, recursively.
    val libsDir = file(libsFolder)
    val libsJarFiles = if (libsDir.exists()) {
        fileTree(libsDir) {
            jarNames.forEach { include("**/$it") }
        }
    } else {
        files()
    }

    val allJarFiles = modJarFiles + libsJarFiles

    // Realize the file tree once to detect missing entries.
    val foundNames = allJarFiles.files.map { it.name }.toSet()
    jarNames.filterNot { it in foundNames }.forEach { missing ->
        logger.error(
            "Mod dependency '$missing' was not found in any mod's " +
                    "/jars folder under ${modsDir.absolutePath} " +
                    "or in ${libsDir.absolutePath}."
        )
    }

    compileOnly(allJarFiles)
}

fun DependencyHandler.addCompileOnlyJar(path: String) {
    val jarFile = file(path)
    if (jarFile.exists()) {
        compileOnly(files(jarFile))
        return
    }
    // Fallback: try resolving the same path relative to the libs folder.
    val libsFile = file("$libsFolder/$path")
    if (libsFile.exists()) {
        compileOnly(files(libsFile))
        return
    }
    logger.error(
        "Dependency '$path' was not found at ${jarFile.absolutePath} " +
                "or at ${libsFile.absolutePath}."
    )
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

//Stages the Starsector API as a local Maven repo under build/starsector-api/.
//Using a maven layout (not flatDir) because IntelliJ only reliably attaches sources when the
//artifact has a POM and follows the standard "<name>-<version>-sources.jar" classifier convention.
//A jar IS a zip with optional manifest, so the source side is just a copy with the right filename.
//If you ever hit a zip layout IntelliJ does not like, swap the copy for a real extract + repack.
//Runs at configuration time so the files exist before Gradle resolves dependencies (including IDE sync).
fun stageStarsectorApi(): File {
    val repoDir = layout.buildDirectory.dir("starsector-api").get().asFile
    val artifactDir = File(repoDir, "com/fs/starfarer/starfarer-api/local")
    artifactDir.mkdirs()
    val coreDir = starsectorLayout().gameWorkingDir

    //Only copy if the source is newer than the staged file, so repeat syncs are cheap.
    fun stageIfStale(src: File, dst: File) {
        if (!src.exists()) return
        if (!dst.exists() || dst.lastModified() < src.lastModified()) {
            src.copyTo(dst, overwrite = true)
        }
    }

    stageIfStale(File(coreDir, "starfarer.api.jar"), File(artifactDir, "starfarer-api-local.jar"))
    stageIfStale(File(coreDir, "starfarer.api.zip"), File(artifactDir, "starfarer-api-local-sources.jar"))

    //Minimal POM. Gradle's maven resolver needs one to recognise the artifact and to look up the -sources classifier.
    val pomFile = File(artifactDir, "starfarer-api-local.pom")
    if (!pomFile.exists()) {
        pomFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.fs.starfarer</groupId>
                <artifactId>starfarer-api</artifactId>
                <version>local</version>
            </project>
            """.trimIndent()
        )
    }
    return repoDir
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

    // Wrap everything inside a top-level folder named after this project's root directory,
    // so the zip extracts to a single "<ProjectName>/" folder ready to drop into /mods/.
    // Every from() below inherits this prefix.
    into(projectDir.name)

    // 1. Include the compiled jar from the build task
    from(tasks.jar) {
        into("jars") // Optional: place inside a jar folder in the zip
    }

    // 2. Include the files and folders listed in packageIncludes.
    // Directories are placed into a same-named folder; files go at the folder root.
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


