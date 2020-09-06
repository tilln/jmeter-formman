# HTTP Form Manager [![travis][travis-image]][travis-url]

[travis-image]: https://travis-ci.org/tilln/jmeter-formman.svg?branch=master
[travis-url]: https://travis-ci.org/tilln/jmeter-formman

Overview
--------

This plugin makes JMeter behave a little more like a browser: form fields are automatically populated with preselected values.

* No more manual correlation of hidden inputs, such as session variables.
* Just correlate parameters that actually relate to user input.

![HTTP Form Manager](https://raw.githubusercontent.com/tilln/jmeter-formman/master/docs/before.png)

HTTP form parameters are extracted at runtime from HTML responses and added to HTTP sampler parameters 
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

Copy the [jmeter-formman jar file](https://github.com/tilln/jmeter-formman/releases/download/1.0/jmeter-formman-1.0.jar) 
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

The *HTTP Form Manager* can replace those extractors and list of parameters, 
reducing the parameter list to just the username and password elements.
It can be especially useful for parameter names that are generated dynamically by the application and change all the time.

The plugin will copy all form parameters to the sampler, except the ones that are already defined in the sampler
(the username and password in the screenshot).

### Multiple Forms

Suppose the HTML response contains more than one form, then the plugin needs to determine which form to copy from.

There are several options for identifying which form should match the current sampler.

##### Example: By sampler URL

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

If the HTTP Sampler's *Path* matches `/form1` and the *Method* is POST, then the parameters `__VIEWSTATE` and `submit` will be copied as follows:

|Parameter  |Value  |Added by|
|-----------|-------|--------|
|__VIEWSTATE|value1 |plugin  |
|submit     |submit1|plugin  |

##### Example: By submit element

Should both forms have the same URL path and method, then the value of the submit element may be used instead:

```html
<html>
    <body>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value1" />
            ...
            <input type="submit" name="submit" value="submit1" />
            <input type="submit" name="other-submit" value="cancel" />
        </form>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value2" />
            ...
            <input type="submit" name="submit" value="submit2" />
            <input type="submit" name="other-submit" value="cancel" />
        </form>
    </body>
</html>
```

The plugin will try to match the form's submit element's name and value to a sampler parameter.
So if the user added `submit1` the plugin adds `value1` for `__VIEWSTATE`:

|Parameter  |Value  |Added by|
|-----------|-------|--------|
|submit     |submit1|user    |
|__VIEWSTATE|value1 |plugin  |

However, if the user added `other-submit` both forms would match and therefore no modification performed.

##### Example: By sampler parameters

A unique set of parameters can also be used to identify the form, for instance if the submit elements have the same value.

```html
<html>
    <body>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value1" />
            ...
            <input type="text" name="param1" value="text1" />
            <input type="submit" name="submit" value="submit" />
        </form>
        <form method="post" action="/form1">
            <input type="hidden" name="__VIEWSTATE" value="value2" />
            ...
            <input type="text" name="param2" value="text2" />
            <input type="submit" name="submit" value="submit" />
        </form>
    </body>
</html>
```

The plugin will try to find a form that contains all of the user-defined parameters.
So if the user added `param2` the plugin would copy `__VIEWSTATE` and `submit` from the second form:

|Parameter  |Value  |Added by|
|-----------|-------|--------|
|param2     |text2  |user    |
|__VIEWSTATE|value2 |plugin  |
|submit     |submit |plugin  |

##### Example: By CSS selector

A CSS selector expression may also be used that uniquely identifies one of the forms.

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

The selector `[value=submit2]` would identify the second form, whereas `[name=submit]` would be ambiguous as both forms match.


Configuration
-------------

![Configuration](https://raw.githubusercontent.com/tilln/jmeter-formman/master/docs/config.png)

* Options:
    * "Clear form data each iteration?": Whether to discard any stored form input data when starting a new thread iteration (default: true).
    This avoids copying no longer relevant parameters e.g. when a new user session starts.

* Form Identification:
    If a response contains multiple forms, the following options can be used, in any combination,
    to uniquely identify which form to copy parameters from:
    * "By sampler URL": Consider only forms with the same URL and method (POST or GET) as in the sampler.
    * "By sampler parameters": Consider only forms that contain all the samplers given parameters (name and value).
    * "By submit element": Consider only forms that contain the sampler's given submit element.
    * "By CSS selector": Consider only forms that contain elements selected by a CSS expression (unless empty).

  All of these options will be used in conjunction to narrow down a set of forms.
  If there is no match or more than one match, nothing will be copied, and the sampler's parameter list will not be modified at all.

* Sampler Modification:
    * "Copy form parameters": Whether to copy form parameters from the identified form to the sampler (default: true).
    * "Copy form URL": Whether to copy the URL of the identified form to the sampler (default: false).
    Useful if the form URL is not static, e.g. contains dynamic query parameters.

By default, only HTTP Sampler responses with Content-Type *text/html* will be considered.
The JMeter Property `jmeter.formman.contentType` can be used to redefine that, e.g. *application/xhtml+xml*.

Limitations
-----------

* Form parameters that are not part of the previous HTML response, or are added or modified by JavaScript, need to be correlated manually as usual.

* The plugin has no effect if a form cannot be determined unambiguously.

* Button attributes formaction, formmethod, form are not supported.

* Usage of more than one HTTP Form Manager in a sampler's scope is untested.