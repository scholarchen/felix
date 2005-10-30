/*
 *   Copyright 2005 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.felix.framework;

import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

import org.apache.felix.framework.searchpolicy.*;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.moduleloader.LibrarySource;
import org.apache.felix.moduleloader.ResourceSource;
import org.osgi.framework.*;


class SystemBundle extends BundleImpl
{
    private List m_activatorList = null;
    private BundleActivator m_activator = null;
    private Thread m_shutdownThread = null;
    private Object[][] m_attributes = null;
    private ResourceSource[] m_resSources = null;
    private LibrarySource[] m_libSources = null;

    protected SystemBundle(Felix felix, BundleInfo info, List activatorList)
        throws BundleException
    {
        super(felix, info);

        // Create an activator list if necessary.
        if (activatorList == null)
        {
            activatorList = new ArrayList();
        }

        // Add the bundle activator for the package admin service.
        activatorList.add(new PackageAdminActivator(felix));

        // Add the bundle activator for the start level service.
        activatorList.add(new StartLevelActivator(felix));

        m_activatorList = activatorList;

        // The system bundle exports framework packages as well as
        // arbitrary user-defined packages from the system class path.
        // We must construct the system bundle's export metadata.

        // Get system property that specifies which class path
        // packages should be exported by the system bundle.
        R4Package[] classPathPkgs = null;
        try
        {
            classPathPkgs = R4Package.parseImportOrExportHeader(
                getFelix().getConfig().get(FelixConstants.FRAMEWORK_SYSTEMPACKAGES));
        }
        catch (Exception ex)
        {
            getFelix().getLogger().log(
                LogWrapper.LOG_ERROR,
                "Error parsing system bundle export statement.", ex);
        }

        // Now, create the list of standard framework exports for
        // the system bundle.
        R4Package[] exports = new R4Package[classPathPkgs.length + 3];

        exports[0] = new R4Package(
            "org.osgi.framework",
            new R4Directive[0],
            new R4Attribute[] { new R4Attribute("version", "1.3.0", false) });

        exports[1] = new R4Package(
            "org.osgi.service.packageadmin",
            new R4Directive[0],
            new R4Attribute[] { new R4Attribute("version", "1.2.0", false) });

        exports[2] = new R4Package(
            "org.osgi.service.startlevel",
            new R4Directive[0],
            new R4Attribute[] { new R4Attribute("version", "1.0.0", false) });

        // Copy the class path exported packages.
        System.arraycopy(classPathPkgs, 0, exports, 3, classPathPkgs.length);

        m_attributes = new Object[][] {
            new Object[] { R4SearchPolicy.EXPORTS_ATTR, exports },
            new Object[] { R4SearchPolicy.IMPORTS_ATTR, new R4Package[0] }
        };

        m_resSources = new ResourceSource[0];

        m_libSources = null;

        String exportString = "";
        for (int i = 0; i < exports.length; i++)
        {
            exportString = exportString +
            exports[i].getId()
            + "; specification-version=\""
            + exports[i].getVersionLow().toString() + "\"";

            if (i < (exports.length - 1))
            {
                exportString = exportString + ", ";
                exportString = exportString +
                    exports[i].getId()
                    + "; specification-version=\""
                    + exports[i].getVersionLow().toString() + "\", ";
            }
        }

        // Initialize header map as a case insensitive map.
        Map map = new StringMap(false);
        map.put(FelixConstants.BUNDLE_VERSION, FelixConstants.FELIX_VERSION_VALUE);
        map.put(FelixConstants.BUNDLE_NAME, "System Bundle");
        map.put(FelixConstants.BUNDLE_DESCRIPTION,
            "This bundle is system specific; it implements various system services.");
        map.put(FelixConstants.EXPORT_PACKAGE, exportString);
        ((SystemBundleArchive) getInfo().getArchive()).setManifestHeader(map);
    }

    public Object[][] getAttributes()
    {
        return m_attributes;
    }

    public ResourceSource[] getResourceSources()
    {
        return m_resSources;
    }

    public LibrarySource[] getLibrarySources()
    {
        return m_libSources;
    }

    public synchronized void start() throws BundleException
    {
        // The system bundle is only started once and it
        // is started by the framework.
        if (getState() == Bundle.ACTIVE)
        {
            throw new BundleException("Cannot start the system bundle.");
        }

        getInfo().setState(Bundle.STARTING);

        try {
            getInfo().setContext(new BundleContextImpl(getFelix(), this));
            getActivator().start(getInfo().getContext());
        } catch (Throwable throwable) {
throwable.printStackTrace();
            throw new BundleException(
                "Unable to start system bundle.", throwable);
        }

        // Do NOT set the system bundle state to active yet, this
        // must be done after all other bundles have been restarted.
        // This will be done after the framework is initialized.
    }

    public synchronized void stop() throws BundleException
    {
        if (System.getSecurityManager() != null)
        {
            AccessController.checkPermission(new AdminPermission());
        }

        // Spec says stop() on SystemBundle should return immediately and
        // shutdown framework on another thread.
        if (getFelix().getStatus() == Felix.RUNNING_STATUS)
        {
            // Initial call of stop, so kick off shutdown.
            m_shutdownThread = new Thread("FelixShutdown") {
                public void run()
                {
                    try
                    {
                        getFelix().shutdown();
                    }
                    catch (Exception ex)
                    {
                        getFelix().getLogger().log(
                            LogWrapper.LOG_ERROR,
                            "SystemBundle: Error while shutting down.", ex);
                    }

                    // Only shutdown the JVM if the framework is running stand-alone.
                    String embedded = getFelix().getConfig()
                        .get(FelixConstants.EMBEDDED_EXECUTION_PROP);
                    boolean isEmbedded = (embedded == null)
                        ? false : embedded.equals("true");
                    if (!isEmbedded)
                    {
                        if (System.getSecurityManager() != null)
                        {
                            AccessController.doPrivileged(new PrivilegedAction() {
                                public Object run()
                                {
                                    System.exit(0);
                                    return null;
                                }
                            });
                        }
                        else
                        {
                            System.exit(0);
                        }
                    }
                }
            };
            getInfo().setState(Bundle.STOPPING);
            m_shutdownThread.start();
        }
        else if ((getFelix().getStatus() == Felix.STOPPING_STATUS) &&
                 (Thread.currentThread() == m_shutdownThread))
        {
            // Callback from shutdown thread, so do our own stop.
            try
            {
                getActivator().stop(getInfo().getContext());
            }
            catch (Throwable throwable)
            {
                throw new BundleException(
                        "Unable to stop system bundle.", throwable);
            }
        }
    }

    public synchronized void uninstall() throws BundleException
    {
        throw new BundleException("Cannot uninstall the system bundle.");
    }

    public synchronized void update() throws BundleException
    {
        update(null);
    }

    public synchronized void update(InputStream is) throws BundleException
    {
        if (System.getSecurityManager() != null)
        {
            AccessController.checkPermission(new AdminPermission());
        }

        // TODO: This is supposed to stop and then restart the framework.
        throw new BundleException("System bundle update not implemented yet.");
    }

    protected BundleActivator getActivator()
        throws Exception
    {
        if (m_activator == null)
        {
            m_activator = new SystemBundleActivator(getFelix(), m_activatorList);
        }
        return m_activator;
    }
}
