package Petroleum.Collab;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

final class PinningTrustManager extends X509ExtendedTrustManager {
    private final X509ExtendedTrustManager system;
    private final String pinned;
    volatile String rejectedFingerprint;

    private PinningTrustManager(X509ExtendedTrustManager system, String pinned) {
        this.system = system;
        this.pinned = pinned;
    }

    static PinningTrustManager create(String pinned) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager extended) {
                return new PinningTrustManager(extended, pinned);
            }
        }
        throw new GeneralSecurityException("no X509 trust manager available");
    }

    SSLContext context() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {this}, null);
        return context;
    }

    static String fingerprint(X509Certificate certificate) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder out = new StringBuilder();
            for (byte b : digest) {
                if (out.length() > 0) {
                    out.append(':');
                }
                out.append(String.format("%02X", b));
            }
            return out.toString();
        } catch (GeneralSecurityException e) {
            throw new CertificateException(e);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        try {
            system.checkServerTrusted(chain, authType, socket);
        } catch (CertificateException e) {
            checkPin(chain, e);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        try {
            system.checkServerTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            checkPin(chain, e);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            system.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            checkPin(chain, e);
        }
    }

    private void checkPin(X509Certificate[] chain, CertificateException failure) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw failure;
        }
        String actual = fingerprint(chain[0]);
        if (!actual.equals(pinned)) {
            rejectedFingerprint = actual;
            throw failure;
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        throw new CertificateException("client certificates are not used");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        throw new CertificateException("client certificates are not used");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("client certificates are not used");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return system.getAcceptedIssuers();
    }
}
