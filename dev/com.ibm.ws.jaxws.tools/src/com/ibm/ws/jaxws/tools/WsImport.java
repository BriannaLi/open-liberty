/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.jaxws.tools;

import java.io.File;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Arrays;

import com.ibm.ws.jaxws.tools.internal.JaxWsToolsConstants;
import com.ibm.ws.jaxws.tools.internal.JaxWsToolsUtil;
import com.sun.tools.ws.wscompile.WsimportTool;

/**
 * IBM Wrapper for wsimport tool.
 */
public class WsImport {
    private static final PrintStream err = System.err;

    public static void main(String[] args) {
        if (isTargetRequired(args)) {
            String errMsg = JaxWsToolsUtil.formatMessage(JaxWsToolsConstants.ERROR_PARAMETER_TARGET_MISSED_KEY);
            err.println(errMsg);
            System.exit(1);
        }
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                System.setProperty("javax.xml.accessExternalSchema", "all");
                return null;
            }
        });

        //Pass in the JWS and JAX-B APIs as a -classpath arg when Java 9 or above.
        //Otherwise the javac process started by the tooling doesn't contain these APIs.
        if (getMajorJavaVersion() > 8) {
            String classpathValue = null;
            Class<?> JAXB = null;
            Class<?> WebService = null;
            Class<?> Service = null;
            try {
                JAXB = Thread.currentThread().getContextClassLoader().loadClass("javax.xml.bind.JAXB");
                WebService = Thread.currentThread().getContextClassLoader().loadClass("javax.jws.WebService");
                Service = Thread.currentThread().getContextClassLoader().loadClass("javax.xml.ws.Service");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                System.exit(2);
            }

            classpathValue = getJarFileOfClass(JAXB);
            classpathValue = classpathValue + File.pathSeparator + getJarFileOfClass(WebService);
            classpathValue = classpathValue + File.pathSeparator + getJarFileOfClass(Service);

            if (classpathValue != null) {
                boolean classpathSet = false;

                //Search for existing -cp or -classpath arg.
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals("-cp") || args[i].equals("-classpath")) {
                        args[i + 1] = args[i + 1] + File.pathSeparator + classpathValue;
                        classpathSet = true;
                    }
                }

                //No existing -cp or -classpath arg was found so add it to the end (just before the SEI class).
                if (!classpathSet && args.length > 0) {
                    args = Arrays.copyOf(args, args.length + 2);
                    args[args.length - 1] = args[args.length - 3]; //push SEI class to the end of args
                    args[args.length - 2] = classpathValue; //insert paths for -classpath arg
                    args[args.length - 3] = "-classpath"; //insert the -classpath arg
                }
            }
        }

        System.exit(new WsimportTool(System.out).run(args) ? 0 : 1);
    }

    private static boolean isTargetRequired(String[] args) {
        boolean helpExisted = false;
        boolean versionExisted = false;
        boolean targetExisted = false;

        for (String arg : args) {
            if (arg.equals(JaxWsToolsConstants.PARAM_HELP)) {
                helpExisted = true;
            } else if (arg.equals(JaxWsToolsConstants.PARAM_VERSION)) {
                versionExisted = true;
            } else if (arg.equals(JaxWsToolsConstants.PARAM_TARGET)) {
                targetExisted = true;
            }

            continue;
        }

        return args.length > 0 && !helpExisted && !versionExisted && !targetExisted;
    }

    private static String getJarFileOfClass(final Class<?> javaClass) {
        ProtectionDomain protectionDomain = AccessController.doPrivileged(
                                                                          new PrivilegedAction<ProtectionDomain>() {
                                                                              @Override
                                                                              public ProtectionDomain run() {
                                                                                  return javaClass.getProtectionDomain();
                                                                              }
                                                                          });

        return protectionDomain.getCodeSource().getLocation().getPath();

    }

    private static int getMajorJavaVersion() {
        String version = System.getProperty("java.version");
        String[] versionElements = version.split("\\D");
        int i = Integer.valueOf(versionElements[0]) == 1 ? 1 : 0;
        return Integer.valueOf(versionElements[i]);
    }
}
