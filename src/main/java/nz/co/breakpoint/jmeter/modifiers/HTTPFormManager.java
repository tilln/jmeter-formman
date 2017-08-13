package nz.co.breakpoint.jmeter.modifiers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;

public class HTTPFormManager extends AbstractTestElement implements PreProcessor, TestBean {

    private static final long serialVersionUID = 1L;

    public static Logger log = LoggingManager.getLoggerForClass();

    protected SampleResult lastHtmlResult;

    /** Both parsing responses as well as modifying parameters happens in the 
     *  Preprocessor method (so there is no need for a separate Postprocessor).
     *  Parsing is only done for a response with the right content type.
     */
    @Override
    public void process() {
        JMeterContext context = getThreadContext();
        SampleResult prev = context.getPreviousResult();
        if (prev != null && prev.getContentType() != null && prev.getContentType().startsWith("text/html")) {
            lastHtmlResult = prev;
        }
        if (lastHtmlResult == null || !(context.getCurrentSampler() instanceof HTTPSamplerBase)) return;

        HTTPSamplerBase sampler = (HTTPSamplerBase)context.getCurrentSampler();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        String method = sampler.getMethod();
        URL url;
        try {
            url = sampler.getUrl();
        }
        catch (MalformedURLException e) {
            log.warn("Cannot process sampler! ", e);
            return;
        }
        Document document = Jsoup.parse(lastHtmlResult.getResponseDataAsString(), lastHtmlResult.getURL().toString());

        List<FormElement> forms = document.select("form").forms();

        forms.removeIf(f -> 
            !method.equals(f.submit().request().method().toString()) 
            || !url.equals(f.submit().request().url())
        );
        if (forms.isEmpty()) return;

        FormElement form = null;

        if (forms.size() == 1) {
            form = forms.get(0);
        } else {
            log.debug("More than one form matches sampler URL, trying to match submit button...");
            for (FormElement candidate : forms) {
                for (Element submit : candidate.select("[type=submit]")) {
                    if (submit.attr("value").equals(args.get(submit.attr("name")))) {
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

        if (form.select("[type=submit]").size() > 1) {
            log.debug("Form has more than one submit element. Removing all, assuming sampler has submit element.");
            form.elements().removeIf(e -> "submit".equalsIgnoreCase(e.attr("type")));
        }

        for (Connection.KeyVal param : form.formData()) {
            if (!args.containsKey(param.key())) {
                log.debug("Adding "+param);
                sampler.addArgument(param.key(), param.value());
            }
        }
    }
}