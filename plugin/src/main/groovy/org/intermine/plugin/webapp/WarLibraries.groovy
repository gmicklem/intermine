package org.intermine.plugin.webapp

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

/**
 * The libraries (jars) that go into WEB-INF/lib of a mine's war, and the check that no library is there in two versions
 * (im-ee7.54). {@code duplicatesStrategy EXCLUDE} on the war only collapses entries of the same name: foo-1.0.jar and
 * foo-1.1.jar are two names, and Gradle's conflict resolution cannot join the mine's classpath with the jars inside the
 * bio-webapp war.
 *
 * A library is named by its artifact name without the version (and without the classifier of a resolved artifact, which
 * stays part of the name: foo-sources is not foo). For the artifacts of the mine's classpath that name comes from the
 * resolved module id (ModuleComponentIdentifier); for jars that no resolution knows (those of the bio-webapp war, the
 * files of a files() dependency) it is parsed from the file name.
 */
class WarLibraries {
    /** One jar of WEB-INF/lib. {@code group} is null when the jar is not a resolved module. */
    static class Library {
        String name
        String version
        String group
        String fileName
        /** where the jar comes from, for the message */
        String origin

        String describe() {
            (group != null ? "${group}:${name}:${version}" : fileName) + " (${origin})"
        }
    }

    /** name-1.2.3.jar, name-1.2.3-classifier.jar: the name is everything before the first "-" that a digit follows. */
    private static final java.util.regex.Pattern VERSIONED = ~/^(.+?)-(\d.*)\.jar$/

    /**
     * The library of a jar that no resolution knows, by its file name: foo-bar-1.2.jar is foo-bar, version 1.2. A name with
     * a digit after a hyphen (lib-2-2.0.jar) is ambiguous, so the names of the resolved libraries ({@code knownNames}) are
     * tried first, the longest first: lib-2 is a library that the classpath has, so lib-2-1.0.jar is lib-2 in 1.0.
     */
    static Library fromFileName(String fileName, String origin, Collection<String> knownNames = []) {
        String known = knownNames.findAll { fileName.startsWith(it + '-') && fileName.substring(it.length() + 1) ==~ /\d.*\.jar/ }
                .max { it.length() }
        if (known != null) {
            String version = fileName.substring(known.length() + 1, fileName.length() - '.jar'.length())
            return new Library(name: known, version: version, fileName: fileName, origin: origin)
        }
        def m = VERSIONED.matcher(fileName)
        if (m.matches()) {
            return new Library(name: m.group(1), version: m.group(2), fileName: fileName, origin: origin)
        }
        // no version in the name
        new Library(name: fileName.replaceFirst(/\.jar$/, ''), version: '', fileName: fileName, origin: origin)
    }

    /** The library of a resolved artifact: its module name (plus the classifier, if the file name shows one) and version. */
    static Library fromModule(ModuleComponentIdentifier id, File file, String origin) {
        String fileName = file.name
        String name = id.module
        String prefix = "${id.module}-${id.version}"
        if (fileName.startsWith(prefix + '-') && fileName.endsWith('.jar')) {
            name += '-' + fileName.substring(prefix.length() + 1, fileName.length() - '.jar'.length())
        }
        new Library(name: name, version: id.version, group: id.group, fileName: fileName, origin: origin)
    }

    /**
     * The libraries of the resolved artifacts of the mine's classpath that are among {@code files} (the jars of the war's
     * classpath, in order), and for the files that are not resolved artifacts (a files() dependency), those that the file
     * name tells.
     */
    static List<Library> ofClasspath(Collection<File> files, Collection<ResolvedArtifactResult> artifacts, String origin) {
        Map<File, ModuleComponentIdentifier> modules = [:]
        artifacts.each { ResolvedArtifactResult artifact ->
            if (artifact.id.componentIdentifier instanceof ModuleComponentIdentifier) {
                modules[artifact.file] = (ModuleComponentIdentifier) artifact.id.componentIdentifier
            }
        }
        files.findAll { it.isFile() && it.name.endsWith('.jar') }.collect { File file ->
            ModuleComponentIdentifier id = modules[file]
            id != null ? fromModule(id, file, origin) : fromFileName(file.name, origin)
        }
    }

    /**
     * The libraries that are there more than once with different versions (or, for the same file name, from different
     * groups): a list of messages, one per library name; empty when there is none. Jars of one file name and group are one
     * jar (the war lists it once).
     */
    static List<String> duplicates(Collection<Library> libraries) {
        Map<String, List<Library>> byName = new TreeMap<>()
        libraries.each { Library lib -> byName.get(lib.name, []) << lib }
        List<String> messages = []
        byName.each { String name, List<Library> libs ->
            // the war keeps the first jar of a file name: those that are the same file name and group are one
            Map<String, Library> byFileName = new LinkedHashMap<>()
            libs.each { Library lib ->
                Library seen = byFileName[lib.fileName]
                if (seen == null) {
                    byFileName[lib.fileName] = lib
                } else if (seen.group != null && lib.group != null && seen.group != lib.group) {
                    byFileName[lib.fileName + ' ' + lib.group] = lib
                }
            }
            if (byFileName.size() > 1) {
                messages << "${name}: ${byFileName.values()*.describe().join(', ')}".toString()
            }
        }
        messages
    }
}
