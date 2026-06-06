package uk.co.sangharsh.logtime.plugin.service;

import org.junit.Assert;
import org.junit.Test;

public class UrlValidatorTest {
    @Test
    public void shouldValidateUrl(){
        Assert.assertTrue(UrlValidator.isValidURL("https://example.com")); // true
        Assert.assertTrue(UrlValidator.isValidURL("ftp://files.server.net"));   // true
        Assert.assertFalse(UrlValidator.isValidURL("example.com"));             // false (Missing scheme)
        Assert.assertFalse(UrlValidator.isValidURL("http://"));
    }
}
