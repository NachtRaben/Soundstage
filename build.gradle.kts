import dev.armadeus.armalib.gradle.jarcheck.ReleaseJarInspector
import dev.armadeus.armalib.gradle.jarcheck.ReleaseJarRules
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    id("armalib.mod")
    id("armalib.bundle")
    id("armalib.jarcheck")
    id("armalib.rig")
    id("me.modmuss50.mod-publish-plugin")
}

fun prop(key: String): String = providers.gradleProperty(key).get()
fun optProp(key: String): String? = providers.gradleProperty(key).orNull

val soundstageModId = prop("mod_id")
val armalibVersion = prop("armalib_version")
val isNeoForge = armalib.loader == dev.armadeus.armalib.gradle.Loader.NEOFORGE
val isForge = armalib.loader == dev.armadeus.armalib.gradle.Loader.FORGE
val isFabric = armalib.loader == dev.armadeus.armalib.gradle.Loader.FABRIC
val is1201 = armalib.target.minecraft == "1.20.1"

version = prop("mod_version")
group = prop("mod_group_id")

armalib {
    modId(soundstageModId)
    fabricApi("fabric-api-base", "fabric-sound-api-v1")
    forgeMixins("soundstage-common.mixins.json")
}

if (armalib.target.neoForge == null && armalib.target.minecraft.startsWith("26.")) {
    dependencies { "compileOnly"("net.fabricmc.fabric-api:fabric-api:${armalib.target.fabricApi}") }
}

sourceSets.named("main") {
    resources {
        srcDir(rootProject.file("src/generated/resources"))
        exclude("src/generated/**/.cache")
    }
}

val rig: SourceSet = sourceSets.getByName("rig")
rig.java.setSrcDirs(listOf(rootProject.file("src/rig/java")))
rig.resources.setSrcDirs(listOf(rootProject.file("src/rig/resources")))
sourceSets.named("test") {
    java.setSrcDirs(listOf(rootProject.file("src/test/java")))
    resources.setSrcDirs(listOf(rootProject.file("src/test/resources")))
}

armalibRig {
    framework("dev.armadeus:armalib-rig-${project.name}:$armalibVersion")
}

tasks.named("build") { dependsOn(tasks.named("rigClasses")) }

repositories {
    maven("https://maven.parchmentmc.org") {
        content { includeGroupByRegex("org\\.parchmentmc.*") }
    }
    if (providers.gradleProperty("armalib.local").isPresent) {
        mavenLocal { content { includeGroup("dev.armadeus") } }
    }
    maven("https://repo.armadeus.dev/snapshots") {
        content { includeGroup("dev.armadeus") }
    }
    maven("https://repo.armadeus.dev/releases") {
        content { includeGroup("dev.nachtraben.resonance") }
    }
    maven("https://maven.isxander.dev/releases") {
        content { includeGroup("dev.isxander") }
    }
}

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.addAll(listOf("-Xmaxerrs", "5000")) }

evaluationDependsOn(":core")
val coreMain: SourceSet = project(":core").extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main")

dependencies {
    implementation(project(":core"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly("dev.nachtraben.resonance:resonance-api:1.0.0")

    testImplementation("dev.armadeus:armalib-lavaplayer:$armalibVersion")

    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.commons.io)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

if (isForge) dependencies {
    compileOnly(libs.mixinextras.common)
    annotationProcessor(libs.mixinextras.common)
    "jarJar"(libs.mixinextras.forge) {
        version {
            strictly("[0.4.1,)")
            prefer(libs.versions.mixinextras.get())
        }
    }
    runtimeOnly(libs.mixinextras.forge)
}

armalibBundle {
    nest("dev.armadeus:armalib-lavaplayer-youtube:$armalibVersion")
    nest("org.jcodec:jcodec:${libs.versions.jcodec.get()}")
    nest("org.tukaani:xz:${libs.versions.xz.get()}")
    nestMod("dev.armadeus:armalib-config-${project.name}:$armalibVersion")
    nestMod("dev.armadeus:armalib-audio-${project.name}:$armalibVersion")
}

tasks.jar {
    from(coreMain.output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val writeTargetVersions = tasks.register("writeTargetVersions") {
    val out = layout.buildDirectory.file("libs/target-versions.properties")
    val minecraft = armalib.target.minecraft
    val loaderLines = armalib.target.neoForge?.let { "neo_version=$it\n" }
        ?: armalib.target.forge?.let { "forge_version=$it\n" }
        ?: "fabric_loader_version=${armalib.target.fabricLoader}\nfabric_api_version=${armalib.target.fabricApi}\n"
    inputs.property("minecraft", minecraft)
    inputs.property("loader", loaderLines)
    outputs.file(out)
    doLast {
        out.get().asFile.writeText("minecraft_version=$minecraft\n" + loaderLines)
    }
}
tasks.named("assemble") { dependsOn(writeTargetVersions) }

val publishVersion = project.version.toString()
val publishJar = layout.buildDirectory.file("libs/soundstage-${project.name}-$publishVersion.jar")
val publishLoader = when {
    isNeoForge -> "neoforge"
    isForge -> "forge"
    else -> "fabric"
}
val publishLoaderName = mapOf("neoforge" to "NeoForge", "forge" to "Forge", "fabric" to "Fabric").getValue(publishLoader)
val publishChangelog = publishVersion.let { version ->
    providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText.map { text ->
    val lines = text.lines()
    val start = lines.indexOfFirst { it.trim() == "## $version" }
    require(start >= 0) { "CHANGELOG.md has no section headed \"## $version\"" }
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    lines.subList(start + 1, end).joinToString("\n").trim()
    }
}
publishMods {
    file.set(publishJar)
    version.set("$publishVersion+${project.name}")
    displayName.set("Soundstage $publishVersion for ${armalib.target.minecraft} $publishLoaderName")
    changelog.set(publishChangelog)
    type.set(when (providers.gradleProperty("publish.type").orNull ?: "release") {
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> STABLE
    })
    modLoaders.add(publishLoader)
    dryRun.set(!providers.gradleProperty("publish.live").isPresent)
    modrinth {
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN").orElse(providers.gradleProperty("modrinth.token")))
        projectId.set("BMMPW6ZA")
        minecraftVersions.add(armalib.target.minecraft)
        environment.set(CLIENT_AND_SERVER)
        projectDescription.set(providers.fileContents(rootProject.layout.projectDirectory.file("docs/LISTING.md")).asText
            .map { it.substringAfter("\n").trimStart() })
        if (isFabric) requires { slug.set("fabric-api") }
    }
    curseforge {
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN").orElse(providers.gradleProperty("curseforge.token")))
        projectId.set("1727017")
        minecraftVersions.add(armalib.target.minecraft)
        javaVersions.add(when {
            is1201 -> JavaVersion.VERSION_17
            armalib.target.minecraft.startsWith("26.") -> JavaVersion.VERSION_25
            else -> JavaVersion.VERSION_21
        })
        client.set(true)
        server.set(true)
        if (isFabric) requires("fabric-api")
    }
}
tasks.matching { it.name in setOf("publishMods", "publishModrinth", "publishCurseforge") }.configureEach {
    dependsOn("assemble", "checkReleaseJar")
    val snapshot = publishVersion.endsWith("-SNAPSHOT")
    doFirst {
        require(!snapshot) { "a -SNAPSHOT version is never uploaded to Modrinth or CurseForge" }
    }
}

armalibJarCheck {
    requiredFiles.put("META-INF/LICENSE", rootProject.file("LICENSE"))
    requiredFiles.put("META-INF/NOTICE", rootProject.file("NOTICE"))
}

if (isNeoForge || isForge) extensions.configure<net.neoforged.moddevgradle.dsl.ModDevExtension> {
    if (!armalib.target.minecraft.startsWith("26.") && !is1201) parchment {
        minecraftVersion = prop("parchment_minecraft_version")
        mappingsVersion = prop("parchment_mappings_version")
    }

    mods.getByName(soundstageModId).sourceSet(coreMain)

    addModdingDependenciesTo(sourceSets.getByName("test"))

    runs.register("data") {
        data()
        programArguments.addAll(
            "--mod", soundstageModId,
            "--all",
            "--output", rootProject.file("src/generated/resources/").absolutePath,
            "--existing", rootProject.file("src/main/resources/").absolutePath
        )
    }

    runs.configureEach {
        systemProperty("forge.logging.markers", "REGISTRIES")
        if (providers.gradleProperty("soundstageSyncTrace").isPresent) {
            systemProperty("soundstage.synctrace", "true")
        }
    }
}

val fabricAccessWidener: File = if (armalib.target.minecraft == "1.21.1")
    rootProject.file("src/main/resources/soundstage.accesswidener")
else rootProject.file("src/main/accesswideners/${armalib.target.minecraft}.accesswidener")
if (isFabric) {
    extensions.configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
        mods.maybeCreate(soundstageModId).sourceSet(coreMain, project(":core"))
        accessWidenerPath.set(fabricAccessWidener)
    }
    tasks.named("test") { enabled = false }
}

val irisDevMods: Map<String, Pair<String, String>> = mapOf(
    "1.21.1-neoforge" to ("mc1.21.1-0.6.13-neoforge" to "1.8.12+1.21.1-neoforge"),
    "1.21.1-fabric" to ("mc1.21.1-0.6.13-fabric" to "1.8.8+1.21.1-fabric"),
    "1.21.11-fabric" to ("mc1.21.11-0.8.7-fabric" to "1.10.7+1.21.11-fabric"),
    "1.21.11-neoforge" to ("mc1.21.11-0.8.7-neoforge" to "1.10.7+1.21.11-neoforge"),
    "26.1-fabric" to ("mc26.1.2-0.9.2-fabric" to "1.11.4+26.1-fabric"),
    "26.1-neoforge" to ("mc26.1.2-0.9.2-neoforge" to "1.11.4+26.1-neoforge"),
    "26.2-fabric" to ("mc26.2-0.9.2-fabric" to "1.11.4+26.2-fabric"),
    "26.2-neoforge" to ("mc26.2-0.9.2-neoforge" to "1.11.4+26.2-neoforge"),
    "26.3-fabric" to ("mc26.3-0.9.2-fabric" to "1.11.6+26.3-fabric"),
    "1.20.1-fabric" to ("mc1.20.1-0.5.12-beta.2-fabric" to "1.7.6+1.20.1"),
    "1.20.1-forge" to ("0.3.31+mc1.20.1" to "1.20.1-1.8.0"),
)
val sodiumCompileVersion = when (project.name) {
    "1.20.1-forge" -> null
    "26.3-neoforge" -> "mc26.3-0.9.2-neoforge"
    else -> irisDevMods[project.name]?.first
}
if (sodiumCompileVersion != null) {
    repositories {
        maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    }
    if (isNeoForge) {
        val sodiumJar = configurations.create("sodiumCompileJar") { isTransitive = false }
        dependencies { sodiumJar("maven.modrinth:sodium:$sodiumCompileVersion") }
        val sodiumNested = tasks.register<Sync>("sodiumNestedJars") {
            from(zipTree(sodiumJar.singleFile)) { include("META-INF/jarjar/*.jar") }
            into(layout.buildDirectory.dir("sodium-compile"))
            eachFile { path = name }
            includeEmptyDirs = false
        }
        dependencies {
            "compileOnly"(files(sodiumJar))
            "compileOnly"(fileTree(layout.buildDirectory.dir("sodium-compile")) { include("*.jar"); builtBy(sodiumNested) })
        }
    } else {
        dependencies { "modCompileOnly"("maven.modrinth:sodium:$sodiumCompileVersion") }
    }
}
val irisDevMod = irisDevMods[project.name]
if (providers.gradleProperty("withIris").isPresent && irisDevMod != null) {
    val (sodium, iris) = irisDevMod
    repositories {
        maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    }
    val irisLibraries = listOf("org.anarres:jcpp:1.4.14",
            "io.github.douira:glsl-transformer:" + (if (armalib.target.minecraft == "1.21.1" || is1201) "2.0.1" else "3.0.0-pre3"),
            "org.antlr:antlr4-runtime:4.13.1")
    val rigClientDir = providers.gradleProperty("armalib.rigClientDir").orNull
    if (isNeoForge && armalib.target.minecraft != "1.21.1") {
        tasks.matching { it.name == "runRigClient" }.configureEach {
            (this as JavaForkOptions).jvmArgs("-Dneoforge.disableGlValidation=true")
        }
    }
    if (isNeoForge && armalib.target.minecraft != "1.21.1" && rigClientDir != null) {
        tasks.matching { it.name == "runRigClient" }.configureEach {
            doFirst {
                val config = File(rigClientDir, "config/neoforge-client.toml")
                if (!config.exists()) {
                    config.parentFile.mkdirs()
                    config.writeText("enableB3DValidationLayer = false\nshowLoadWarnings = false\n")
                }
            }
        }
    }
    if (is1201) {
        tasks.matching { it.name == "runRigClient" }.configureEach {
            (this as JavaForkOptions).jvmArgs("-Dsodium.checks.issue2561=false")
        }
    }
    dependencies {
        if (isForge) {
            "modRuntimeOnly"("maven.modrinth:embeddium:$sodium")
            "modRuntimeOnly"("maven.modrinth:oculus:$iris")
            "runtimeOnly"("org.anarres:jcpp:1.4.14")
            tasks.matching { it.name == "runRigServer" }.configureEach {
                val provider = javaClass.getMethod("getClasspathProvider").invoke(this) as ConfigurableFileCollection
                val original = files(*provider.from.toTypedArray())
                provider.setFrom(original.filter { f -> "oculus" !in f.name && "embeddium" !in f.name })
            }
        } else if (isNeoForge) {
            "runtimeOnly"("maven.modrinth:sodium:$sodium")
            "runtimeOnly"("maven.modrinth:iris:$iris")
            if (armalib.target.minecraft != "1.21.1") irisLibraries.forEach { "runtimeOnly"(it) }
        } else {
            "modLocalRuntime"("maven.modrinth:sodium:$sodium")
            "modLocalRuntime"("maven.modrinth:iris:$iris")
            irisLibraries.forEach { "localRuntime"(it) }
        }
    }
}

if (isNeoForge && providers.gradleProperty("withResonance").isPresent) {
    val resonanceJarPath = optProp("resonanceJarPath")
        ?: "${System.getProperty("user.home")}/.modcache/resonance/handoff/resonance-0.1.0+1.21.1-neoforge.jar"
    dependencies {
        "runtimeOnly"(files(resonanceJarPath))
        "runtimeOnly"("dev.isxander:yet-another-config-lib:3.7.1+1.21.1-neoforge") {
            isTransitive = false
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    if (isNeoForge && stonecutter.eval(stonecutter.current.version, ">=26.2")) {
        filesMatching("META-INF/neoforge.mods.toml") { filter { it.replace("logoFile = ", "iconFile = ") } }
    }
    if (stonecutter.eval(stonecutter.current.version, ">=26.3")) {
        inputs.property("advancementRecipesKey", "recipes")
        filesMatching("data/*/advancement/recipes/**") { filter { it.replace("\"recipe\": ", "\"recipes\": ") } }
        inputs.property("lootTableModifiers", "26.3")
        val resourcesOut = destinationDir
        doLast {
            fun convert(node: Any?): Any? = when (node) {
                is Map<*, *> -> {
                    val out = LinkedHashMap<String, Any?>()
                    for ((key, value) in node) {
                        when (key) {
                            "functions" -> {
                                val modifiers = (value as List<*>).map { convert(it) }
                                out["modifier"] = if (modifiers.size == 1) modifiers[0] else modifiers
                            }
                            "conditions" -> {
                                val conditions = (value as List<*>).map { convert(it) }
                                require(conditions.size == 1) { "26.3 loot tables: more than one condition in $node" }
                                out["condition"] = conditions[0]
                            }
                            "function", "condition" -> out["type"] = value
                            else -> out[key as String] = convert(value)
                        }
                    }
                    out
                }
                is List<*> -> node.map { convert(it) }
                else -> node
            }
            resourcesOut.walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.invariantSeparatorsPath.contains("/loot_table/") }
                .forEach { file ->
                    val json = groovy.json.JsonSlurper().parseText(file.readText())
                    file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(convert(json))))
                }
        }
    }
    if (!isFabric) exclude("soundstage.accesswidener")
    else if (fabricAccessWidener.name != "soundstage.accesswidener") {
        exclude("soundstage.accesswidener")
        from(fabricAccessWidener) { rename { "soundstage.accesswidener" } }
    }
    from(rootProject.projectDir) {
        include("THIRD-PARTY-NOTICES.md")
        include("licenses/**")
        into("META-INF/soundstage")
    }
    from(rootProject.projectDir) {
        include("LICENSE")
        include("NOTICE")
        into("META-INF")
    }
}

val recipeSource: File = rootProject.file("src/main/recipes")
val recipeGeneratedRoot: File = layout.buildDirectory.dir("generated/recipes").get().asFile
val recipeGeneratedDir = File(recipeGeneratedRoot, "data/$soundstageModId/recipe")

val recipeObjectForm = armalib.target.minecraft == "1.21.1" || is1201
val recipeResultItem = is1201
val generateRecipes = tasks.register("generateRecipes") {
    val source = recipeSource
    val out = recipeGeneratedDir
    val objectForm = recipeObjectForm
    val resultItem = recipeResultItem
    inputs.dir(source)
    inputs.property("objectForm", objectForm)
    inputs.property("resultItem", resultItem)
    outputs.dir(out)
    doLast {
        out.deleteRecursively()
        out.mkdirs()
        val ingredient: (Any) -> Any = { value ->
            when {
                !objectForm -> value
                value !is String -> value
                value.startsWith("#") -> mapOf("tag" to value.substring(1))
                else -> mapOf("item" to value)
            }
        }
        source.listFiles { f -> f.extension == "json" }?.forEach { file ->
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parse(file) as MutableMap<String, Any>
            @Suppress("UNCHECKED_CAST")
            (json["key"] as? Map<String, Any>)?.let { key ->
                json["key"] = key.mapValues { (_, value) -> ingredient(value) }
            }
            @Suppress("UNCHECKED_CAST")
            (json["ingredients"] as? List<Any>)?.let { list ->
                json["ingredients"] = list.map(ingredient)
            }
            @Suppress("UNCHECKED_CAST")
            (json["result"] as? MutableMap<String, Any>)?.let { result ->
                if (resultItem && "id" in result) result["item"] = result.remove("id")!!
            }
            File(out, file.name).writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
        }
        println("soundstage: generated ${out.listFiles()?.size ?: 0} recipes" + (if (objectForm) " in the object form" else ""))
    }
}

val itemModelsGeneratedRoot: File = layout.buildDirectory.dir("generated/itemModels").get().asFile
val specialItems = listOf("speaker", "satellite_speaker", "center_speaker", "subwoofer", "party_box", "receiver")
val plainItems = listOf("screen", "speaker_link")
val generateItemModels = tasks.register("generateItemModels") {
    val modelDir = rootProject.file("src/main/resources/assets/$soundstageModId/models/item")
    val out = itemModelsGeneratedRoot
    val enabled = armalib.target.minecraft != "1.21.1" && !is1201
    val special = specialItems
    val plain = plainItems
    val modId = soundstageModId
    inputs.dir(modelDir)
    inputs.property("enabled", enabled)
    outputs.dir(out)
    doLast {
        out.deleteRecursively()
        if (!enabled) return@doLast
        val items = File(out, "assets/$modId/items").apply { mkdirs() }
        val models = File(out, "assets/$modId/models/item").apply { mkdirs() }
        for (name in special) {
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parse(File(modelDir, "$name.json")) as MutableMap<String, Any>
            json.remove("parent")
            File(models, "$name.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
            File(items, "$name.json").writeText("""{"model": {"type": "minecraft:special", "base": "$modId:item/$name", "model": {"type": "$modId:block_item"}}}""" + "\n")
        }
        for (name in plain) {
            File(items, "$name.json").writeText("""{"model": {"type": "minecraft:model", "model": "$modId:item/$name"}}""" + "\n")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateItemModels)
    if (armalib.target.minecraft != "1.21.1" && !is1201) {
        val generatedPath = itemModelsGeneratedRoot.absolutePath
        for (name in specialItems) {
            filesMatching("assets/$soundstageModId/models/item/$name.json") {
                if (!file.absolutePath.startsWith(generatedPath)) exclude()
            }
        }
    }
    from(itemModelsGeneratedRoot)
    dependsOn(generateRecipes)
    from(recipeGeneratedRoot)
}

val lootTables1201Root: File = layout.buildDirectory.dir("generated/lootTables1201").get().asFile
val generateLootTables1201 = tasks.register("generateLootTables1201") {
    val source = rootProject.file("src/main/resources/data")
    val out = lootTables1201Root
    val enabled = is1201
    inputs.dir(source)
    inputs.property("enabled", enabled)
    outputs.dir(out)
    doLast {
        out.deleteRecursively()
        if (!enabled) return@doLast
        source.listFiles()?.forEach { namespace ->
            val tables = File(namespace, "loot_table")
            if (!tables.isDirectory) return@forEach
            tables.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { file ->
                @Suppress("UNCHECKED_CAST")
                val json = groovy.json.JsonSlurper().parse(file) as MutableMap<String, Any>
                fun strip(node: Any?) {
                    when (node) {
                        is MutableMap<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val map = node as MutableMap<String, Any?>
                            (map["functions"] as? MutableList<Any?>)?.removeIf {
                                (it as? Map<*, *>)?.get("function") == "minecraft:copy_components"
                            }
                            map.values.forEach(::strip)
                        }
                        is List<*> -> node.forEach(::strip)
                    }
                }
                strip(json)
                val target = File(out, "data/${namespace.name}/loot_tables/${file.relativeTo(tables).path}")
                target.parentFile.mkdirs()
                target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)) + "\n")
            }
        }
    }
}
if (is1201) tasks.named<ProcessResources>("processResources") {
    dependsOn(generateLootTables1201)
    exclude("data/*/loot_table/**")
    from(lootTables1201Root)
    filesMatching("data/*/advancement/**") {
        filter { line -> Regex("\"items\": \"([^\"]+)\"").replace(line) { "\"items\": [\"${it.groupValues[1]}\"]" } }
    }
    filesMatching("pack.mcmeta") {
        filter { line -> line.replace(Regex("\"pack_format\": \\d+"), "\"pack_format\": 15").replace(Regex(",\\s*\"supported_formats\": \\[[^]]*]"), "") }
    }
    val renames = listOf("recipe" to "recipes", "loot_table" to "loot_tables", "advancement" to "advancements",
        "tags/block" to "tags/blocks", "tags/item" to "tags/items")
    eachFile {
        for ((singular, plural) in renames) {
            val match = Regex("^data/([^/]+)/${Regex.escape(singular)}/").find(path) ?: continue
            path = "data/${match.groupValues[1]}/$plural/" + path.substring(match.range.last + 1)
            break
        }
    }
}

if (is1201) tasks.named("test") { enabled = false }
if (is1201) tasks.named("compileTestJava") { enabled = false }
tasks.named<Test>("test") {
    workingDir = rootProject.projectDir
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val checkLoaderIsolation = tasks.register("checkLoaderIsolation") {
    val javaDir = rootProject.file("src/main/java/com/nachtraben/soundstage")
    inputs.dir(javaDir)
    outputs.upToDateWhen { true }
    doLast {
        val loaderApi = Regex("""\b(net\.neoforged|net\.minecraftforge|net\.fabricmc|dev\.architectury)\b""")
        val allowedDir = File(javaDir, "platforms")

        val offenders = javaDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .filterNot { it.startsWith(allowedDir) }
            .mapNotNull { f ->
                val hits = mutableListOf<String>()
                f.readLines().forEachIndexed { i, line ->
                    if (loaderApi.containsMatchIn(line)) {
                        hits += "  ${f.relativeTo(javaDir)}:${i + 1}  ${line.trim()}"
                    }
                }
                if (hits.isEmpty()) null else hits.joinToString("\n")
            }.toList()

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Loader APIs must stay inside platforms/, but found:\n" +
                    offenders.joinToString("\n") +
                    "\n\nEither put the loader-specific part in platforms/ and reach it through a " +
                    "neutral seam; config/SoundstageSettings is the worked example."
            )
        }
    }
}

tasks.named("check") { dependsOn(checkLoaderIsolation) }

val checkSoftDependencyIsolation = tasks.register("checkSoftDependencyIsolation") {
    dependsOn(tasks.named("classes"))
    val classesDir = layout.buildDirectory.dir("classes/java/main")
    inputs.dir(classesDir)
    outputs.upToDateWhen { true }
    doLast {
        val isolated = mapOf(
            "dev/nachtraben/resonance/api/" to
                "com/nachtraben/soundstage/client/audio/resonance/ResonanceStreamingSoundInstance"
        )
        val root = classesDir.get().asFile
        if (!root.exists()) return@doLast

        val failures = mutableListOf<String>()
        for ((prefix, bridge) in isolated) {
            val offenders = root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .filter {
                    it.readBytes().toString(Charsets.ISO_8859_1).contains(prefix)
                }
                .map { it.relativeTo(root).path.removeSuffix(".class") }
                .filterNot { it == bridge || it.startsWith("$bridge$") }
                .toList()
            if (offenders.isNotEmpty()) {
                failures += "$prefix must be referenced only by $bridge, but is also referenced by:\n" +
                    offenders.joinToString("\n") { "  $it" }
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                failures.joinToString("\n\n") +
                    "\n\nThis makes an optional dependency mandatory: the mod will fail to load for " +
                    "anyone without it installed. Reach the type through the bridge class instead, " +
                    "resolved by name — see ResonanceStreamingSoundInstance."
            )
        }
    }
}

tasks.named("check") { dependsOn(checkSoftDependencyIsolation) }

val checkSoundstageNotices = tasks.register("checkSoundstageNotices") {
    group = "verification"
    description = "Fails if a jar nested in the release jar has no entry in THIRD-PARTY-NOTICES.md."
    val releaseJar = armalib.releaseJar
    val notices = rootProject.file("THIRD-PARTY-NOTICES.md")
    inputs.file(releaseJar)
    inputs.file(notices)
    outputs.upToDateWhen { true }
    doLast {
        val jar = releaseJar.get().asFile
        fun json(text: String): Map<String, Any> {
            @Suppress("UNCHECKED_CAST")
            return groovy.json.JsonSlurper().parseText(text) as Map<String, Any>
        }
        val (entries, nested) = ZipFile(jar).use { zip ->
            fun read(name: String) = zip.getEntry(name)?.let { zip.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
            val names = zip.entries().toList().map { it.name }
            val ids = read("META-INF/jarjar/metadata.json")?.let { metadata ->
                @Suppress("UNCHECKED_CAST")
                (json(metadata)["jars"] as List<Map<String, Any>>).map { entry ->
                    @Suppress("UNCHECKED_CAST")
                    val id = entry["identifier"] as Map<String, String>
                    "${id["group"]}:${id["artifact"]}"
                }
            } ?: read("fabric.mod.json")?.let { mod ->
                @Suppress("UNCHECKED_CAST")
                ((json(mod)["jars"] as? List<Map<String, String>>) ?: emptyList()).map { entry ->
                    val file = entry.getValue("file")
                    val inner = ZipInputStream(zip.getInputStream(zip.getEntry(file))).use { input ->
                        generateSequence { input.nextEntry }.firstOrNull { it.name == "fabric.mod.json" }
                            ?.let { input.readBytes().toString(Charsets.UTF_8) }
                    } ?: throw GradleException("$file, nested in ${jar.name}, has no fabric.mod.json")
                    val version = json(inner)["version"].toString()
                    ":" + file.substringAfterLast('/').removeSuffix(".jar").removeSuffix("-$version")
                }
            } ?: emptyList()
            names to ids
        }
        val nestedFiles = entries.filter {
            (it.startsWith("META-INF/jarjar/") || it.startsWith("META-INF/jars/")) && it.endsWith(".jar")
        }

        fun problems(text: String): List<String> {
            val found = mutableListOf<String>()
            if (nestedFiles.size != nested.size) {
                found += "the jar nests ${nestedFiles.size} jars but its metadata names ${nested.size}"
            }
            nested.filterNot { text.contains(it) }.forEach {
                found += "nests $it, which THIRD-PARTY-NOTICES.md does not name"
            }
            if ("META-INF/soundstage/THIRD-PARTY-NOTICES.md" !in entries) {
                found += "does not carry META-INF/soundstage/THIRD-PARTY-NOTICES.md"
            }
            if (entries.none { it.startsWith("META-INF/soundstage/licenses/") }) {
                found += "carries no licence texts under META-INF/soundstage/licenses/"
            }
            return found
        }

        val text = notices.readText()
        val green = problems(text)
        if (green.isNotEmpty()) {
            throw GradleException("${jar.name}'s third-party notices are incomplete:\n  - " +
                green.joinToString("\n  - "))
        }
        if (nested.isEmpty()) throw GradleException("${jar.name} nests nothing, so this check checks nothing")
        val removed = nested.first()
        val red = problems(text.replace(removed, ""))
        if (red.none { it.contains(removed) }) {
            throw GradleException("red control passed: with $removed removed from the notices the check " +
                "reported nothing about it")
        }
        println("checkSoundstageNotices: ${jar.name} OK, ${nested.size} nested jars named: ${nested.joinToString()}")
        println("checkSoundstageNotices: red control ($removed removed from the notices) failed as it must: ${red.first { it.contains(removed) }}")
    }
}
tasks.named("check") { dependsOn(checkSoundstageNotices) }

val checkShippedLicenceRedControl = tasks.register("checkShippedLicenceRedControl") {
    group = "verification"
    description = "Plants an altered and a missing LICENSE/NOTICE in copies of the release jar; each must fail the licence rule."
    val releaseJar = armalib.releaseJar
    val licence = rootProject.file("LICENSE")
    val notice = rootProject.file("NOTICE")
    val work = layout.buildDirectory.dir("soundstage/licence-red-control")
    inputs.file(releaseJar)
    inputs.file(licence)
    inputs.file(notice)
    outputs.dir(work)
    doLast {
        val jar = releaseJar.get().asFile
        val required = mapOf("META-INF/LICENSE" to licence.readBytes(), "META-INF/NOTICE" to notice.readBytes())
        val rules = ReleaseJarRules(requiredFiles = required)
        val dir = work.get().asFile.apply { deleteRecursively(); mkdirs() }

        fun requiredProblems(file: File) =
            ReleaseJarInspector.inspect(file, rules).filter { "META-INF/LICENSE" in it || "META-INF/NOTICE" in it }

        val green = requiredProblems(jar)
        if (green.isNotEmpty()) throw GradleException("${jar.name} fails the licence rule itself: $green")

        fun plant(name: String, change: (String, ByteArray) -> ByteArray?): File {
            val copy = File(dir, "$name.jar")
            ZipFile(jar).use { zip ->
                ZipOutputStream(copy.outputStream()).use { out ->
                    for (entry in zip.entries()) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val changed = if (entry.isDirectory) bytes else change(entry.name, bytes) ?: continue
                        out.putNextEntry(ZipEntry(entry.name))
                        out.write(changed)
                        out.closeEntry()
                    }
                }
            }
            return copy
        }
        fun flipped(bytes: ByteArray) = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }

        val controls = listOf(
            Triple("NOTICE altered", "META-INF/NOTICE", plant("notice-altered") { name, b -> if (name == "META-INF/NOTICE") flipped(b) else b }),
            Triple("LICENSE altered", "META-INF/LICENSE", plant("licence-altered") { name, b -> if (name == "META-INF/LICENSE") flipped(b) else b }),
            Triple("NOTICE missing", "META-INF/NOTICE", plant("notice-missing") { name, b -> if (name == "META-INF/NOTICE") null else b }),
        )
        for ((what, path, copy) in controls) {
            val found = requiredProblems(copy).filter { path in it }
            if (found.isEmpty()) {
                throw GradleException("red control '$what' passed: the licence rule reported nothing for ${copy.name}")
            }
            println("checkShippedLicenceRedControl: red control '$what' failed the check as it must: ${found.first()}")
        }
    }
}
tasks.named("check") { dependsOn(checkShippedLicenceRedControl) }
