package nz.co.breakpoint.jmeter.modifiers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestIterationListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;

public class HTTPFormManager extends AbstractTestElement implements PreProcessor, TestBean, TestIterationListener {

    private static final long serialVersionUID = 1L;

    public static Logger log = LoggingManager.getLoggerForClass();

    protected SampleResult lastHtmlResult;
    protected boolean isNextIteration = false;

    protected String contentType = JMeterUtils.getPropDefault("jmeter.formman.contentType", "text/html");
    private boolean clearEachIteration, copyParameters, copyUrl;
    private boolean matchSamplerUrl, matchSamplerParameters, matchSubmit;
    private String matchCssSelector;

    /** Both parsing responses as well as modifying parameters happens in the
     *  Preprocessor method (so there is no need for a separate Postprocessor).
     *  Parsing is only done for a response with the right content type.
     */
    @Override
    public void process() {
        JMeterContext context = getThreadContext();
        Sampler current = context.getCurrentSampler();
        log.debug("Processing sampler \""+current.getName()+"\"");

        // First, irrespective of current sampler, deal with the last result.
        // Ignore irrelevant content types, but only store HTML results for the current sampler
        // (or any subsequent sampler as there could be non-HTTP samplers in between).
        SampleResult prev = context.getPreviousResult(); // should only be null at the very beginning

        if (prev != null && prev.getContentType() != null && prev.getContentType().startsWith(contentType)) {
            log.debug("Storing HTML result from \""+prev.getSampleLabel()+"\"");
            lastHtmlResult = prev; // retain result across non-HTML samplers
        }
        // Now, deal with the actual sampler.
        // Ignore non-HTTP form post samplers and (optionally) samplers at the start of thread iterations.
        if (!(current instanceof HTTPSamplerBase)) {
            log.debug("No HTTP sampler, skipping");
            return;
        }
        HTTPSamplerBase sampler = (HTTPSamplerBase)current;
        if (sampler.getPostBodyRaw()) {
            log.debug("No HTTP Form but raw body, skipping");
            return;
        }
        if (clearEachIteration && isNextIteration) {
            log.debug("Clearing form data on iteration start");
            isNextIteration = false; // make sure to only skip once i.e. the first HTTP sampler in a thread group
            if (lastHtmlResult != null) {
                log.debug("Discarding form data from sampler \""+lastHtmlResult.getSampleLabel()+"\"");
            }
            lastHtmlResult = null;
            return;
        }
        if (lastHtmlResult == null) { // only resource or Ajax requests so far i.e. no form data (or start of new iteration)
            log.debug("No stored form data available, skipping");
            return;
        }
        // Now that we've got a previous HTML result we can parse it and find a form
        Document document = Jsoup.parse(lastHtmlResult.getResponseDataAsString(), lastHtmlResult.getURL().toString());
        FormElement form = findForm(document, sampler);
        modifySampler(sampler, form);
    }

    protected FormElement findForm(Document document, HTTPSamplerBase sampler) {
        // Of all the forms in the parsed HTML document, keep only the matching ones
        //noinspection unchecked,rawtypes - as we know that jsoup return FormElements (cannot use forms() due to jsoup bug #1384)
        List<FormElement> forms = (List)document.select("form");
        forms.removeIf(form -> !isMatch(form, sampler));

        if (forms.size() == 1) {
            log.debug("Unique match found");
            return forms.get(0);
        }

        log.debug((forms.isEmpty() ? "No" : "More than one") + " form match found. No sampler modification.");
        return null;
    }

    protected boolean isMatch(FormElement form, HTTPSamplerBase sampler) {
        log.debug("Trying to match form: "+form.attributes());
        if (isMatchSamplerUrl()) {
            Connection.Request request = form.submit().request();
            // TODO forms with buttons that override these via formaction/formmethod attributes
            String formMethod = request.method().toString();
            String formUrl = request.url().toString();
            String samplerMethod = sampler.getMethod();
            String samplerUrl;
            try {
                samplerUrl = sampler.getUrl().toString();
            } catch (MalformedURLException e) {
                log.warn("Cannot process sampler! ", e);
                return false;
            }
            if (!samplerMethod.equals(formMethod) || !samplerUrl.equals(formUrl)) {
                log.debug("Form does not match sampler URL or method");
                return false;
            }
        }
        final Map<String, String> samplerParameters = sampler.getArguments().getArgumentsAsMap();
        if (isMatchSamplerParameters()) {
            List<Connection.KeyVal> formParams = form.formData();
            Set<String> samplerParams = samplerParameters.keySet();
            if (!formParams.containsAll(samplerParams)) {
                log.debug("Sampler parameters do not match");
                return false;
            }
        }
        if (isMatchSubmit()) {
            boolean submitMatch = false;
            for (Element submit : form.select("[type=submit]")) { // TODO look for buttons in entire doc (with form attributes)
                String submitName = submit.attr("name");
                String submitValue = submit.attr("value");
                if (submitValue != null && submitValue.equals(samplerParameters.get(submitName))) {
                    log.debug("Submit matches a sampler argument name/value: "+submit);
                    submitMatch = true;
                }
            }
            if (!submitMatch) {
                log.debug("Sampler parameters do not match form submit element");
                return false;
            }
        }
        String selector = getMatchCssSelector();
        if (selector != null && !selector.isEmpty()) {
            final Elements el = form.select(selector);
            if (el == null || el.isEmpty()) {
                log.debug("Form does not match CSS selector");
                return false;
            }
        }
        return true;
    }

    protected void modifySampler(HTTPSamplerBase sampler, FormElement form) {
        if (form == null || sampler == null) return;

        if (isCopyUrl()) {
            URL url = form.submit().request().url();
            String path = url.getPath(), query = url.getQuery();
            if (query != null && !query.isEmpty()) path += query;
            log.debug("Copying form URL path "+path);
            sampler.setPath(path);
        }
        if (isCopyParameters()) {
            // Make sure not to copy multiple submit elements
            if (form.select("[type=submit]").size() > 1) {
                log.debug("Form has more than one submit element. Excluding all, assuming sampler has submit element.");
                form.elements().removeIf(e -> "submit".equalsIgnoreCase(e.attr("type")));
            }
            final Map<String, String> samplerParameters = sampler.getArguments().getArgumentsAsMap();
            // Add only form parameters that are not already defined by the user
            for (Connection.KeyVal formParam : form.formData()) {
                if (!samplerParameters.containsKey(formParam.key())) {
                    log.debug("Adding "+formParam);
                    sampler.addArgument(formParam.key(), formParam.value());
                }
            }
        }
    }

    @Override
    public void testIterationStart(LoopIterationEvent event) {
        log.debug("New thread iteration detected");
        isNextIteration = true;
    }

    // Accessors
    public boolean isClearEachIteration() {
        return clearEachIteration;
    }

    public void setClearEachIteration(boolean clearEachIteration) {
        this.clearEachIteration = clearEachIteration;
    }

    public boolean isMatchSamplerUrl() {
        return matchSamplerUrl;
    }

    public void setMatchSamplerUrl(boolean matchSamplerUrl) {
        this.matchSamplerUrl = matchSamplerUrl;
    }

    public boolean isMatchSamplerParameters() {
        return matchSamplerParameters;
    }

    public void setMatchSamplerParameters(boolean matchSamplerParameters) {
        this.matchSamplerParameters = matchSamplerParameters;
    }

    public boolean isMatchSubmit() {
        return matchSubmit;
    }

    public void setMatchSubmit(boolean matchSubmit) {
        this.matchSubmit = matchSubmit;
    }

    public String getMatchCssSelector() {
        return matchCssSelector;
    }

    public void setMatchCssSelector(String matchCssSelector) {
        this.matchCssSelector = matchCssSelector;
    }

    public boolean isCopyParameters() {
        return copyParameters;
    }

    public void setCopyParameters(boolean copyParameters) {
        this.copyParameters = copyParameters;
    }

    public boolean isCopyUrl() {
        return copyUrl;
    }

    public void setCopyUrl(boolean copyUrl) {
        this.copyUrl = copyUrl;
    }
}