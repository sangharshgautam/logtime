package uk.co.sangharsh.logtime.plugin;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

class LocalSSLTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(final X509Certificate[] arg0,
                                   final String arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public void checkServerTrusted(final X509Certificate[] arg0,
                                   final String arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public final X509Certificate[] getAcceptedIssuers() {
        // TODO Auto-generated method stub
        return null;
    }
}
