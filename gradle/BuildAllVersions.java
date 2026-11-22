import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 配布対象ごとにビルドし、配布用JARをまとめる。 */
class BuildAllVersions {
    public static void main(String[] args) throws IOException, InterruptedException {
        Path project = Path.of(args[0]).normalize();
        List<String> buildOptions = Arrays.asList(args).subList(3, args.length);
        Properties properties = new Properties();
        try (var input = Files.newInputStream(project.resolve("gradle.properties"))) {
            properties.load(input);
        }
        String modVersion = projectProperty(buildOptions, "mod_version", properties.getProperty("mod_version"));
        String archiveName = projectProperty(buildOptions, "archives_base_name", properties.getProperty("archives_base_name"));

        // versionsには配布用のプロファイルだけがあり、26.1系は1本にまとまる。
        List<String> targets;
        try (var directories = Files.list(project.resolve("versions"))) {
            targets = directories.filter(dir -> Files.isRegularFile(dir.resolve("gradle.properties")))
                    .map(dir -> dir.getFileName().toString()).sorted().toList();
        }

        List<Path> jars = new ArrayList<>();
        for (String target : targets) {
            List<String> command = new ArrayList<>();
            command.add(ProcessHandle.current().info().command().orElseThrow());
            command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
            command.addAll(List.of("-jar", project.resolve("gradle/wrapper/gradle-wrapper.jar").toString(), "build"));
            command.addAll(buildOptions);
            command.add("-PmcVersion=" + target);
            int exit = new ProcessBuilder(command).directory(project.toFile()).inheritIO().start().waitFor();
            if (exit != 0) {
                System.exit(exit);
            }
            jars.add(project.resolve("build/mc%s/libs/%s-%s+mc%s.jar".formatted(target, archiveName, modVersion, target)));
        }

        Path output = project.resolve("build/libs/%s-%s-all-versions.zip".formatted(archiveName, modVersion));
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), "all-versions-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (Path jar : jars) {
                    zip.putNextEntry(new ZipEntry(jar.getFileName().toString()));
                    Files.copy(jar, zip);
                    zip.closeEntry();
                }
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        System.out.println(output);
    }

    private static String projectProperty(List<String> options, String name, String defaultValue) {
        String prefix = "-P" + name + "=";
        return options.reversed().stream().filter(option -> option.startsWith(prefix)).findFirst()
                .map(option -> option.substring(prefix.length())).orElse(defaultValue);
    }
}
