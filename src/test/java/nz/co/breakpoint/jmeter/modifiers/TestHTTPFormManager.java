package nz.co.breakpoint.jmeter.modifiers;

import java.net.URL;
import java.util.Map;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerFactory;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.hamcrest.collection.IsMapContaining;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class TestHTTPFormManager {
    protected JMeterContext context;
    protected HTTPSamplerBase sampler;
    protected SampleResult prev;
    protected HTTPFormManager instance;

    protected final static String html =
    "<html>"+
    "   <body>"+
    "       <form action=\"form\" method=\"POST\" name=\"1\">"+
    "           <input type=\"hidden\" name=\"hidden_input\"    value=\"hidden_value1\" />"+
    "           <input type=\"submit\" name=\"submit\"          value=\"submit_value1\" />"+
    "           <input type=\"submit\" name=\"submit\"          value=\"submit_value2\" />"+
    "       </form>"+
    "       <form action=\"/base/form\" method=\"POST\" name=\"2\">"+
    "           <input type=\"hidden\" name=\"hidden_input\"    value=\"hidden_value2\" />"+
    "           <input type=\"submit\" name=\"submit\"          value=\"submit_value2\" />"+
    "           <input type=\"submit\" name=\"submit\"          value=\"submit_value3\" />"+
    "           <input type=\"submit\" name=\"other-submit\"    value=\"submit_value4\" />"+
    "       </form>"+
    "       <form action=\"//dummy.net/base/form\" name=\"3\">"+ // GET is implicit
    "           <input type=\"hidden\" name=\"hidden_input\"    value=\"hidden_value3\" />"+
    "       </form>"+
    "       <form method=\"post\" name=\"4\">"+
    "           <input type=\"hidden\" name=\"hidden_input\"    value=\"hidden_value4\" />"+
    "       </form>"+
    "       <form action=\"/base/overridden\" method=\"POST\" name=\"5\">"+
    "           <input type=\"hidden\" name=\"hidden_input\"    value=\"hidden_value5\" />"+
    "           <input type=\"submit\" name=\"submit\"          value=\"submit_value5\" formaction=\"/base/form\" />"+
    "       </form>"+
    "       <form action=\"/other-form\" method=\"POST\" name=\"6\">"+
    "           <input type=\"hidden\" name=\"hidden_input\"    value=\"hidden_value\" />"+
    "           <input type=\"text\"   name=\"text_input\"      value=\"text_value\" />"+
    "       </form>"+
    "       <form action=\"/base/form?queryparameter=value\" method=\"POST\" name=\"7\">"+
    "           <input type=\"submit\" name=\"submit\"          value=\"submit_value7\" />"+
    "       </form>"+
    "   </body>"+
    "</html>";

    @Before
    public void setUp() throws Exception {
        sampler = HTTPSamplerFactory.newInstance();
        sampler.setProtocol("http");
        sampler.setDomain("dummy.net");
        sampler.setPath("/base/form");
        sampler.setMethod("POST");
        
        prev = SampleResult.createTestSample(0);
        prev.setURL(new URL("http://dummy.net/base/form"));
        prev.setContentType("text/html");
        prev.setResponseData(html, "UTF-8");
         
        context = JMeterContextService.getContext();
        context.setCurrentSampler(sampler);
        context.setPreviousResult(prev);
        
        instance = new HTTPFormManager();
        instance.setThreadContext(context);
        instance.setContentType("text/html");
    }

    @Test
    public void testNoModificationIfNoFormMatches() throws Exception {
        instance.log.info("testNoModificationIfNoFormMatches");
        sampler.setPath("/base/no-match");
        sampler.addArgument("name", "value");
        instance.process();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        assertThat(args.size(), is(1));
        assertThat(args, IsMapContaining.hasEntry("name", "value"));
    }

    @Test
    public void testFormIsSelectedByMethodAndURL() throws Exception {
        instance.log.info("testFormIsSelectedByMethodAndURL");
        sampler.setMethod("GET"); // matches form 3
        instance.process();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        assertThat(args.size(), is(1));
        assertThat(args, IsMapContaining.hasEntry("hidden_input", "hidden_value3"));
    }

    @Test
    public void testFormIsSelectedByMethodAndURLWithQueryParameters() throws Exception {
        instance.log.info("testFormIsSelectedByMethodAndURLWithQueryParameters");
        sampler.setPath("/base/form?queryparameter=value");
        instance.process();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        assertThat(args.size(), is(1));
        assertThat(args, IsMapContaining.hasEntry("submit", "submit_value7"));
    }

    @Test
    public void testNoModificationToExplicitValue() throws Exception {
        instance.log.info("testNoModificationToExplicitValue");
        sampler.setPath("/other-form");
        sampler.addArgument("text_input", "explicit_value");
        instance.process();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        assertThat(args.size(), is(2));
        assertThat(args, IsMapContaining.hasEntry("hidden_input", "hidden_value"));
        assertThat(args, IsMapContaining.hasEntry("text_input", "explicit_value"));
    }

    @Test
    public void testFormIsSelectedByExplicitSubmit() throws Exception {
        instance.log.info("testFormIsSelectedByExplicitSubmit");
        sampler.addArgument("submit", "submit_value3");
        instance.process();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        assertThat(args.size(), is(2));
        assertThat(args, IsMapContaining.hasEntry("submit", "submit_value3"));
        assertThat(args, IsMapContaining.hasEntry("hidden_input", "hidden_value2"));
    }

    @Test
    public void testNoModificationIfAmbiguous() throws Exception {
        instance.log.info("testNoModificationIfAmbiguous");
        sampler.addArgument("submit", "submit_value2");
        instance.process();
        Map<String, String> args = sampler.getArguments().getArgumentsAsMap();
        assertThat(args.size(), is(1));
        assertThat(args, IsMapContaining.hasEntry("submit", "submit_value2"));
    }
}
