package nz.co.breakpoint.jmeter.modifiers;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestIterationListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;

public class HTTPFormManager extends AbstractTestElement implements PreProcessor, TestBean, TestIterationListener {

    private static final long serialVersionUID = 1L;

    public static Logger log = LoggingManager.getLoggerForClass();

    protected SampleResult lastHtmlResult;
    protected boolean isNextIteration = false;

    private String contentType;
    private boolean clearEachIteration;

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
        // Now that we've got a previous HTML result and an HTTP form sampler
        // we get the HTTP verb/method and URL for comparing with the form's ones
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        final String method = sampler.getMethod();
        final String url;
        try {
            url = sampler.getUrl().toString();
        }
        catch (MalformedURLException e) {
            log.warn("Cannot process sampler! ", e);
            return;
        }
        Document document = Jsoup.parse(lastHtmlResult.getResponseDataAsString(), lastHtmlResult.getURL().toString());

        // Of all the forms in the parsed HTML document, keep only the ones
        // with method and URL matching the current sampler
        //noinspection unchecked,rawtypes
        List<FormElement> forms = (List)document.select("form");

        forms.removeIf(form -> {
            Connection.Request request = form.submit().request();
            // TODO keep forms with buttons that override these via formaction/formmethod attributes
            return !method.equals(request.method().toString())
                || !url.equals(request.url().toString());
        });
        if (forms.isEmpty()) {
            log.debug("No form found matching sampler method and URL: "+method+" "+url);
            return;
        }

        // As there could be more than one form candidate left with the same URL and method,
        // we're trying to find a form with a submit button that matches a given sampler argument
        FormElement form = null;

        if (forms.size() == 1) {
            form = forms.get(0);
        } else {
            log.debug("More than one form matches sampler URL, trying to match submit button...");
            for (FormElement candidate : forms) {
                for (Element submit : candidate.select("[type=submit]")) { // TODO look for buttons in entire doc (with form attributes)
                    String submitName = submit.attr("name");
                    String submitValue = submit.attr("value");
                    if (submitValue != null && submitValue.equals(args.get(submitName))) {
                        log.debug("Submit matches a sampler argument name/value: "+submit);
                        if (form == null) {
                            form = candidate;
                            break; // next form
                        }
                        log.warn("Multiple forms match "+method+" "+url+". No sampler modification.");
                        return;
                    }
                }
            }
        }
        if (form == null) {
            log.debug("No match found. No sampler modification.");
            return;
        }
        log.debug("Form match found: id=\""+form.attr("id")+"\", action="+form.attr("action"));

        if (form.select("[type=submit]").size() > 1) {
            log.debug("Form has more than one submit element. Excluding all, assuming sampler has submit element.");
            form.elements().removeIf(e -> "submit".equalsIgnoreCase(e.attr("type")));
        }

        for (Connection.KeyVal param : form.formData()) {
            if (!args.containsKey(param.key())) {
                log.debug("Adding "+param);
                sampler.addArgument(param.key(), param.value());
            }
        }
    }

    @Override
    public void testIterationStart(LoopIterationEvent event) {
        log.debug("New thread iteration detected");
        isNextIteration = true;
    }

    // Accessors
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isClearEachIteration() {
        return clearEachIteration;
    }

    public void setClearEachIteration(boolean clearEachIteration) {
        this.clearEachIteration = clearEachIteration;
    }
}