package nz.co.breakpoint.jmeter.modifiers;

import java.beans.PropertyDescriptor;
import org.apache.jmeter.testbeans.BeanInfoSupport;

public class HTTPFormManagerBeanInfo extends BeanInfoSupport {

    public HTTPFormManagerBeanInfo() {
        super(HTTPFormManager.class);

        createPropertyGroup("Options", new String[]{ "clearEachIteration", "ignoreUrlParameters" });
        createPropertyGroup("Response", new String[]{ "contentType" });
        PropertyDescriptor p;

        p = property("clearEachIteration");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, true);

        p = property("contentType");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "text/html");

        p = property("ignoreUrlParameters");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, false);
    }
}
