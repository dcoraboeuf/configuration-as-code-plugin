package io.jenkins.plugins.casc;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.CheckForNull;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class CredentialsTest {

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @ConfiguredWithCode("GlobalCredentials.yml")
    @Test
    public void testGlobalScopedCredentials() {
        List<StandardUsernamePasswordCredentials> creds = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,Jenkins.getInstanceOrNull(), null, Collections.emptyList());
        assertThat(creds.size(), is(1));
        assertEquals("user1", creds.get(0).getId());
        assertEquals("Administrator", creds.get(0).getUsername());
        assertEquals("secretPassword", creds.get(0).getPassword().getPlainText());

        List<BasicSSHUserPrivateKey> creds2 = CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey.class,Jenkins.getInstanceOrNull(), null, Collections.emptyList());
        assertThat(creds2.size(), is(1));
        assertEquals("agentuser", creds2.get(0).getUsername());
        assertEquals("password", creds2.get(0).getPassphrase().getPlainText());
        assertEquals("ssh private key used to connect ssh slaves", creds2.get(0).getDescription());
    }

    @ConfiguredWithCode("GlobalCredentials.yml")
    @Test
    public void testGlobalScopedCredentialsAndManualCredentialsAfterReconfig() throws Throwable {
        // Configuration has been done

        // Let's register manual credentials
        String credentialsId = UUID.randomUUID().toString();
        Credentials newCreds = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                credentialsId,
                "description,",
                "user",
                "password"
        );
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(newCreds);

        // Checks they are correctly referenced
        StandardUsernamePasswordCredentials foundCreds = getCredentialsById(credentialsId);
        assertNotNull("Credentials are correctly referenced", foundCreds);

        // Let's relaunch the config-as-code
        j.before();

        // Check the manual credentials are still there
        foundCreds = getCredentialsById(credentialsId);
        assertNotNull("Credentials are still there", foundCreds);
    }

    @ConfiguredWithCode("CredentialsWithDomain.yml")
    @Test
    public void testDomainScopedCredentials() {
        List<StandardUsernamePasswordCredentials> creds = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,Jenkins.getInstanceOrNull(), null, Collections.emptyList());
        assertThat(creds.size(), is(1));
        assertEquals("user1", creds.get(0).getId());
        assertEquals("Administrator", creds.get(0).getUsername());
        assertEquals("secret", creds.get(0).getPassword().getPlainText());
    }

    @CheckForNull
    private StandardUsernamePasswordCredentials getCredentialsById(String credentialsId) {
        List<StandardUsernamePasswordCredentials> credList = CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class,
                Jenkins.getInstanceOrNull(),
                null,
                Collections.emptyList()
        );
        StandardUsernamePasswordCredentials foundCreds = null;
        for (StandardUsernamePasswordCredentials credentials : credList) {
            if (StringUtils.equals(credentialsId, credentials.getId())) {
                foundCreds = credentials;
                break;
            }
        }
        return foundCreds;
    }

}
