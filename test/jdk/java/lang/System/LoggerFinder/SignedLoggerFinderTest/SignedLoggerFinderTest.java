/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8314263
 * @summary Signed jars triggering Logger finder recursion and StackOverflowError
 * @library /test/lib
 * @build jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.process.*
 *        jdk.test.lib.util.JarUtils
 *        jdk.test.lib.JDKToolLauncher
 * @compile SignedLoggerFinderTest.java SimpleLoggerFinder.java
 * @run main SignedLoggerFinderTest init
 * @run main SignedLoggerFinderTest init sign
 */

import java.io.File;
import java.nio.file.*;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.*;
import java.util.logging.*;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Arrays.asList;

public class SignedLoggerFinderTest {

    /**
     * This test triggers recursion in the broken JDK. The error can
     * manifest in a few different ways.
     * One error seen is "java.lang.NoClassDefFoundError:
     * Could not initialize class jdk.internal.logger.LoggerFinderLoader$ErrorPolicy"
     *
     * The original reported error was a StackOverflow (also seen in different iterations
     * of this run). Running test in signed and unsigned jar mode for sanity coverage.
     * The current bug only manifests when jars are signed.
     */

    private static boolean init = false;
    private static boolean signJars = false;


    private static final String KEYSTORE = "keystore.jks";
    private static final String ALIAS = "JavaTest";
    private static final String STOREPASS = "changeit";
    private static final String KEYPASS = "changeit";
    private static final String DNAME = "CN=sample";
    private static final Path jarPath1 =
        Path.of(System.getProperty("test.classes", "."), "SimpleLoggerFinder.jar");
    private static final Path jarPath2 =
            Path.of(System.getProperty("test.classes", "."), "SimpleLoggerFinder2.jar");

    public static void main(String[] args) throws Throwable {
        init = args.length >=1 && args[0].equals("init");
        signJars = args.length >=2 && args[1].equals("sign");
        if (init) {
            initialize();
            List<String> cmds = new ArrayList<>();
            cmds.add(JDKToolFinder.getJDKTool("java"));
            cmds.addAll(asList(Utils.getTestJavaOpts()));
            cmds.addAll(List.of(
                "-classpath",
                System.getProperty("test.classes") + File.pathSeparator +
                    jarPath1.toString() + File.pathSeparator + jarPath2.toString(),
                "-Dtest.classes=" + System.getProperty("test.classes"),
                // following debug property seems useful to tickle the issue
                "-Dsun.misc.URLClassPath.debug=true",
                // enable logging to verify correct output
                "-Djava.util.logging.config.file=" +
                    Path.of(System.getProperty("test.src", "."), "logging.properties"),
                "SignedLoggerFinderTest",
                "no-init"));

            try {
                OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(cmds.stream()
                                .filter(t -> !t.isEmpty())
                                .toArray(String[]::new))
                        .shouldHaveExitValue(0);
                if (signJars) {
                    outputAnalyzer
                            .shouldContain("TEST LOGGER:")
                            .shouldContain(DNAME);
                }

            } catch (Throwable t) {
                throw new RuntimeException("Unexpected fail.", t);
            }
        } else {
            // set up complete. Run the code to trigger the recursion
            JarFile jf = new JarFile(jarPath1.toString(), true);
            jf.getInputStream(jf.getJarEntry("loggerfinder/SimpleLoggerFinder.class"));
            JarFile jf2 = new JarFile(jarPath2.toString(), true);
            jf2.getInputStream(jf.getJarEntry("loggerfinder/SimpleLoggerFinder.class"));
            Security.setProperty("test", "test");

            assertEquals(System.LoggerFinder.getLoggerFinder().getClass().getName(),
                    "loggerfinder.SimpleLoggerFinder");
            Logger testLogger = Logger.getLogger("jdk.security.event");
            assertEquals(testLogger.getClass().getName(), "java.util.logging.Logger");
        }
    }

    public static void initialize() throws Throwable {
        if (signJars) {
            genKey();
        }

        Path classes = Paths.get(System.getProperty("test.classes", ""));
        JarUtils.createJarFile(jarPath1,
            classes,
            classes.resolve("loggerfinder/SimpleLoggerFinder.class"),
            classes.resolve("loggerfinder/SimpleLoggerFinder$NoOpLogger.class"));

        JarUtils.updateJarFile(jarPath1, Path.of(System.getProperty("test.src")),
            Path.of("META-INF", "services", "java.lang.System$LoggerFinder"));
        if (signJars) {
            signJar(jarPath1.toString());
        }
        // multiple signed jars with services to have ServiceLoader iteration
        Files.copy(jarPath1, jarPath2, REPLACE_EXISTING);
    }

    private static void genKey() throws Throwable {
        String keytool = JDKToolFinder.getJDKTool("keytool");
        Files.deleteIfExists(Paths.get(KEYSTORE));
        ProcessTools.executeCommand(keytool,
                "-J-Duser.language=en",
                "-J-Duser.country=US",
                "-genkey",
                "-keyalg", "rsa",
                "-alias", ALIAS,
                "-keystore", KEYSTORE,
                "-keypass", KEYPASS,
                "-dname", DNAME,
                "-storepass", STOREPASS
        ).shouldHaveExitValue(0);
    }


    private static OutputAnalyzer signJar(String jarName) throws Throwable {
        List<String> args = new ArrayList<>();
        args.add("-verbose");
        args.add(jarName);
        args.add(ALIAS);

        return jarsigner(args);
    }

    private static OutputAnalyzer jarsigner(List<String> extra)
            throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jarsigner")
                .addVMArg("-Duser.language=en")
                .addVMArg("-Duser.country=US")
                .addToolArg("-keystore")
                .addToolArg(KEYSTORE)
                .addToolArg("-storepass")
                .addToolArg(STOREPASS)
                .addToolArg("-keypass")
                .addToolArg(KEYPASS);
        for (String s : extra) {
            if (s.startsWith("-J")) {
                launcher.addVMArg(s.substring(2));
            } else {
                launcher.addToolArg(s);
            }
        }
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    private static void assertEquals(String expected, String received) {
        if (!expected.equals(received)) {
            throw new RuntimeException("Received: " + received);
        }
    }
}

