package nz.co.breakpoint.jmeter.modifiers;

import java.beans.PropertyDescriptor;
import org.apache.jmeter.testbeans.BeanInfoSupport;

public class HTTPFormManagerBeanInfo extends BeanInfoSupport {

    public HTTPFormManagerBeanInfo() {
        super(HTTPFormManager.class);

        createPropertyGroup("Options", new String[]{ "clearEachIteration" });
        createPropertyGroup("FormIdentification", new String[]{ "matchSamplerUrl", "matchSamplerParameters", "matchSubmit", "matchCssSelector" });
        createPropertyGroup("SamplerModification", new String[]{ "copyParameters", "copyUrl" });
        PropertyDescriptor p;

        p = property("clearEachIteration");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, true);

        p = property("matchSamplerUrl");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, true);

        p = property("matchSamplerParameters");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, false);

        p = property("matchSubmit");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, false);

        p = property("matchCssSelector");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property("copyParameters");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, true);

        p = property("copyUrl");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, false);
    }
}
