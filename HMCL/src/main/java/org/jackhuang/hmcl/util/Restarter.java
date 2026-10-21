/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public final class Restarter {

    /// Restart the current application.
    public static void restartSelf() throws IOException {
        LOG.info("Restarting LMCL");

        Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null) {
            throw new IOException("Failed to find current LMCL location");
        }

        startJava(thisJar);
    }

    /// Starts a new launcher process from the given JAR file and returns without waiting for it.
    ///
    /// The new process inherits the JVM options of the current process that are still meaningful for a
    /// fresh launcher, and shares the standard input and output of the current process.
    ///
    /// @param jar the launcher JAR to execute
    /// @param appArgs the application arguments passed to the new process
    /// @throws IOException if the process cannot be created
    private static void startJava(Path jar, String... appArgs) throws IOException {
        List<String> commandline = new ArrayList<>();
        commandline.add(JavaRuntime.getDefault().getBinary().toString());

        try {
            for (String inputArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (inputArgument.startsWith("-D") || inputArgument.startsWith("-X")) {
                    commandline.add(inputArgument);
                }
            }
        } catch (Throwable ignored) {
            // ManagementFactory not available
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                if (entry.getKey() instanceof String key && key.startsWith("hmcl.")) {
                    commandline.add("-D" + key + "=" + entry.getValue());
                }
            }
        }

        commandline.add("-jar");
        commandline.add(jar.toAbsolutePath().toString());
        commandline.addAll(Arrays.asList(appArgs));
        LOG.info("Starting process: " + maskCommandline(commandline));
        new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }

    /// Hides credentials contained in JVM system property arguments before they are written to the log.
    ///
    /// @param commandline the full command line of the process being started
    /// @return the command line with sensitive system property values masked
    private static String maskCommandline(List<String> commandline) {
        return commandline.stream().map(str -> {
            if (str.startsWith("-D")) {
                int eqIdx = str.indexOf('=');
                if (eqIdx != -1) {
                    String key = str.substring(2, eqIdx);
                    String value = str.substring(eqIdx + 1);
                    if (key.contains("http.proxy") ||
                            key.startsWith("https.proxy") ||
                            key.startsWith("socksProxy") ||
                            key.equals("hmcl.microsoft.auth.id") ||
                            key.equals("hmcl.curseforge.apikey")
                    ) {
                        return "-D" + key + "=" + (value.isEmpty() ? "" : value.charAt(0) + "*".repeat(value.length() - 1));
                    }
                }
            }

            return str;
        }).collect(Collectors.joining(" "));
    }

    private Restarter() {
    }
}
