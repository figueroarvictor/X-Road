/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.acme;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;

@Slf4j
@Configuration
public class AcmeCommonConfig {

    @Bean
    public AcmeProperties acmeProperties() {
        Resource resource = new FileSystemResource(SystemProperties.getConfPath() + "conf.d/acme.yml");
        if (!SystemProperties.isAcmeChallengePortEnabled() && !resource.exists()) {
            log.warn("Configuration {} does not exist", resource);
            return new AcmeProperties();
        }
        Constructor constructor = createAcmeYamlConstructor();
        Yaml yaml = new Yaml(constructor);
        try (InputStream input = Files.newInputStream(resource.getFile().toPath())) {
            return yaml.loadAs(input, AcmeProperties.class);
        } catch (Exception e) {
            log.warn("Failed to load yaml configuration from {}", resource, e);
            return new AcmeProperties();
        }
    }

    private static Constructor createAcmeYamlConstructor() {
        Constructor constructor = new Constructor(AcmeProperties.class, new LoaderOptions());

        TypeDescription acmePropertiesDescriptor = new TypeDescription(AcmeProperties.class);
        acmePropertiesDescriptor.substituteProperty("eab-credentials",
                AcmeProperties.EabCredentials.class,
                "getEabCredentials",
                "setEabCredentials");
        acmePropertiesDescriptor.substituteProperty("account-keystore-password",
                String.class,
                "getAccountKeystorePassword",
                "setAccountKeystorePassword");
        constructor.addTypeDescription(acmePropertiesDescriptor);

        TypeDescription eabCredentialsDescriptor = new TypeDescription(AcmeProperties.EabCredentials.class);
        eabCredentialsDescriptor.substituteProperty("certificate-authorities",
                Map.class,
                "getCertificateAuthorities",
                "setCertificateAuthorities",
                String.class,
                AcmeProperties.CA.class);
        constructor.addTypeDescription(eabCredentialsDescriptor);

        TypeDescription caDescriptor = new TypeDescription(AcmeProperties.CA.class);
        caDescriptor.substituteProperty("mac-key-base64-encoded",
                boolean.class,
                "isMacKeyBase64Encoded",
                "setMacKeyBase64Encoded");
        constructor.addTypeDescription(caDescriptor);

        TypeDescription credentialsDescription = new TypeDescription(AcmeProperties.Credentials.class);
        credentialsDescription.substituteProperty("mac-key", String.class, "getMacKey", "setMacKey");
        credentialsDescription.substituteProperty("auth-mac-key", String.class, "getAuthMacKey", "setAuthMacKey");
        credentialsDescription.substituteProperty("sign-mac-key", String.class, "getSignMacKey", "setSignMacKey");
        credentialsDescription.substituteProperty("auth-kid", String.class, "getAuthKid", "setAuthKid");
        credentialsDescription.substituteProperty("sign-kid", String.class, "getSignKid", "setSignKid");
        constructor.addTypeDescription(credentialsDescription);

        return constructor;
    }

}
