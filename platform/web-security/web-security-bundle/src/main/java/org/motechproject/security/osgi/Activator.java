package org.motechproject.security.osgi;

import org.apache.commons.io.IOUtils;
import org.apache.felix.http.api.ExtHttpService;
import org.motechproject.osgi.web.MotechOsgiWebApplicationContext;
import org.motechproject.osgi.web.ServletRegistrationException;
import org.motechproject.security.filter.MotechDelegatingFilterProxy;
import org.motechproject.osgi.web.ModuleRegistrationData;
import org.motechproject.osgi.web.UIFrameworkService;
import org.motechproject.osgi.web.UiHttpContext;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

public class Activator implements BundleActivator {
    private static Logger logger = LoggerFactory.getLogger(Activator.class);
    private static final String CONTEXT_CONFIG_LOCATION = "classpath:META-INF/osgi/applicationWebSecurityBundle.xml";
    private static final String SERVLET_URL_MAPPING = "/websecurity/api";
    private static final String RESOURCE_URL_MAPPING = "/websecurity";

    private ServiceTracker httpServiceTracker;
    private ServiceTracker uiServiceTracker;

    private static final String MODULE_NAME = "websecurity";

    private static BundleContext bundleContext;
    private static DelegatingFilterProxy filter;

    @Override
    public void start(BundleContext context) {
        bundleContext = context;

        this.httpServiceTracker = new ServiceTracker(context,
                ExtHttpService.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference ref) {
                Object service = super.addingService(ref);
                serviceAdded((ExtHttpService) service);
                return service;
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                serviceRemoved((ExtHttpService) service);
                super.removedService(ref, service);
            }
        };
        this.httpServiceTracker.open();

        this.uiServiceTracker = new ServiceTracker(context,
                UIFrameworkService.class.getName(), null) {

            @Override
            public Object addingService(ServiceReference ref) {
                Object service = super.addingService(ref);
                serviceAdded((UIFrameworkService) service);
                return service;
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                serviceRemoved((UIFrameworkService) service);
                super.removedService(ref, service);
            }
        };
        this.uiServiceTracker.open();
    }

    public void stop(BundleContext context) {
        this.httpServiceTracker.close();
        this.uiServiceTracker.close();
    }

    public static class WebSecurityApplicationContext extends MotechOsgiWebApplicationContext {

        public WebSecurityApplicationContext() {
            super();
            setBundleContext(Activator.bundleContext);
        }

    }

    private void serviceAdded(ExtHttpService service) {
        try {
            DispatcherServlet dispatcherServlet = new DispatcherServlet();
            dispatcherServlet.setContextConfigLocation(CONTEXT_CONFIG_LOCATION);
            dispatcherServlet.setContextClass(WebSecurityApplicationContext.class);
            ClassLoader old = Thread.currentThread().getContextClassLoader();

            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                UiHttpContext httpContext = new UiHttpContext(service.createDefaultHttpContext());

                service.registerServlet(SERVLET_URL_MAPPING, dispatcherServlet, null, null);
                service.registerResources(RESOURCE_URL_MAPPING, "/webapp", httpContext);
                logger.debug("Servlet registered");

                filter = new MotechDelegatingFilterProxy("springSecurityFilterChain", dispatcherServlet.getWebApplicationContext());
                service.registerFilter(filter, "/.*", null, 0, httpContext);
                logger.debug("Filter registered");
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        } catch (Exception e) {
            throw new ServletRegistrationException(e);
        }
    }

    private void serviceRemoved(ExtHttpService service) {
        service.unregister(SERVLET_URL_MAPPING);
        logger.debug("Servlet unregistered");

        service.unregisterFilter(filter);
        logger.debug("Filter unregistered");
    }

    private void serviceAdded(UIFrameworkService service) {
        ModuleRegistrationData regData = new ModuleRegistrationData();
        regData.setModuleName(MODULE_NAME);
        regData.setUrl("../websecurity/");
        regData.addAngularModule("motech-web-security");
        regData.addSubMenu("#/users", "manageUsers");
        regData.addSubMenu("#/roles", "manageRoles");
        regData.addI18N("messages", "../websecurity/bundles/");

        InputStream is = null;
        StringWriter writer = new StringWriter();
        try {
            is = this.getClass().getClassLoader().getResourceAsStream("header.html");
            IOUtils.copy(is, writer);

            regData.setHeader(writer.toString());
        } catch (IOException e) {
            logger.error("Cant read header.html", e);
            throw new ServletRegistrationException(e);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(writer);
        }

        service.registerModule(regData);
        logger.debug("Web Security registered in UI framework");
    }

    private void serviceRemoved(UIFrameworkService service) {
        service.unregisterModule(MODULE_NAME);
        logger.debug("Web Security unregistered from ui framework");
    }

}
