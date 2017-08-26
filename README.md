# HTTP Form Manager [![travis][travis-image]][travis-url]

[travis-image]: https://travis-ci.org/tilln/jmeter-formman.svg?branch=master
[travis-url]: https://travis-ci.org/tilln/jmeter-formman

Overview
--------

This plugin makes JMeter behave a little more like a browser: form fields are automatically populated with preselected values.

* No more manual correlation of hidden inputs, such as session variables.
* Just correlate parameters that actually relate to user input.

![HTTP Form Manager](https://raw.githubusercontent.com/tilln/jmeter-formman/master/docs/before.png)

HTTP form POST parameters are extracted at runtime from HTML responses and added to HTTP sampler parameters 
(similar to JMeter's [HTML Link Parser](http://jmeter.apache.org/usermanual/component_reference.html#HTML_Link_Parser)).

![HTTP Form Manager](https://raw.githubusercontent.com/tilln/jmeter-formman/master/docs/after.png)

Installation
------------
<!--
### Via [PluginsManager](https://jmeter-plugins.org/wiki/PluginsManager/)

Under tab "Available Plugins", select "HTTP Form Manager", then click "Apply Changes and Restart JMeter".

### Via Package from [JMeter-Plugins.org](https://jmeter-plugins.org/)

Extract the [zip package](https://jmeter-plugins.org/files/packages/tilln-formman-1.0.zip) into JMeter's lib directory, then restart JMeter.
-->

### Via Manual Download

Copy the [jmeter-formman jar file](https://github.com/tilln/jmeter-formman/releases/download/1.0-SNAPSHOT/jmeter-formman-1.0-SNAPSHOT.jar) 
into JMeter's lib/ext directory and restart JMeter.

Usage
-----

From the context menu, select "Add" / "Pre Processors" / "HTTP Form Manager".

Put the Preprocessor in a scope that contains a series of related HTTP samplers that would normally have to be chained together via Extractors.

### Simple Example

Suppose an HTML response contains a login form with a few parameters, some hidden inputs, and a submit button:

```html
<html>
    <body>
        <form method="post" action="/adfs/ls/?SAMLRequest=fZJRb...">
            <input type="hidden" name="__VIEWSTATE" value="/wEPDwUJ.." />
            <input type="hidden" name="__VIEWSTATEGENERATOR" value="0EE29E36" />
            <input type="hidden" name="__EVENTVALIDATION" value="/wEdAAcLX..." />
            <input type="hidden" name="__db" value="15" />
            ...
            <input name="ctl00$ContentPlaceHolder1$UsernameTextBox" type="text" />
            <input name="ctl00$ContentPlaceHolder1$PasswordTextBox" type="password" />
            ...
            <input type="submit" name="ctl00$ContentPlaceHolder1$btnSubmitButton" value="Sign In" />
        </form>
    </body>
</html>
```

Normally these form parameters would be extracted into JMeter variables via 
[Regular Expression](http://jmeter.apache.org/usermanual/component_reference.html#Regular_Expression_Extractor) or 
[CSS/JQuery Extractors](http://jmeter.apache.org/usermanual/component_reference.html#CSS/JQuery_Extractor)
and then added to the subsequent HTTP sampler, as in the screenshot above.

The HTTP Form Manager can replace those extractors and list of parameters, 
reducing the parameter list to just the username and password elements.

### Multiple Forms

Suppose the HTML response contains more than one form, then the URL path and method are used to determine which parameters to extract and send.

```html
<html>
    <body>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value1" />
            ...
            <input type="submit" name="submit" value="submit1" />
        </form>
        <form method="post" action="/form2">
            <input type="hidden" name="__VIEWSTATE" value="value2" />
            ...
            <input type="submit" name="submit" value="submit2" />
        </form>
    </body>
</html>
```

If the HTTP Sampler path matches form1 and the method is POST, then the parameters `__VIEWSTATE` and `submit` will be dynamically added as follows:

|Parameter  |Value  |Added by|
|-----------|-------|--------|
|__VIEWSTATE|value1 |plugin  |
|submit     |submit1|plugin  |

Should both forms have the same URL path and method, then the value of the submit element needs to be included in the parameter list to determine which form to send:

```html
<html>
    <body>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value1" />
            ...
            <input type="submit" name="submit" value="submit1" />
        </form>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value2" />
            ...
            <input type="submit" name="submit" value="submit2" />
        </form>
    </body>
</html>
```

The plugin will try to match the form's submit element's name and value to a sampler parameter, so if the user added `submit1` the plugin adds `value1` for `__VIEWSTATE`:

|Parameter  |Value  |Added by|
|-----------|-------|--------|
|__VIEWSTATE|value1 |plugin  |
|submit     |submit1|user    |

If there is no match, nothing will be added and the parameter list will not be modified at all.

### Multiple Submit Elements

In case a form has more than one submit element, none is added by the plugin but the user is expected to add one to the parameter list.


Limitations
-----------

* Form parameters that are not part of the previous HTML response, or are added or modified by JavaScript, need to be correlated manually as usual.

* The plugin has no effect if a form cannot be determined unambiguously (i.e. multiple forms with identical URLs and submit elements).
