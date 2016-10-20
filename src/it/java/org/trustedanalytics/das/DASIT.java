/**
 * Copyright (c) 2015 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trustedanalytics.das;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.trustedanalytics.cloud.auth.AuthTokenRetriever;
import org.trustedanalytics.das.parser.Request;
import org.trustedanalytics.das.parser.State;
import org.trustedanalytics.das.security.authorization.Authorization;
import org.trustedanalytics.das.service.RequestDTO;
import org.trustedanalytics.usermanagement.orgs.model.Org;
import org.trustedanalytics.usermanagement.security.model.OrgPermission;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Main.class, InTestConfiguration.class})
@WebAppConfiguration
@IntegrationTest("server.port=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles(profiles = {"test", "inmemory"})
public class DASIT {

    @Value("http://localhost:${local.server.port}/rest/das/requests")
    private String baseUrl;

    @Autowired
    private AuthTokenRetriever tokenRetriever;

    @Autowired
    private String TOKEN;

    private TestRestTemplate testRestTemplate;

    private ScheduledExecutorService executor;

    private StatusListener statusListenerMock;

    @Autowired
    private Authorization authorization;
    private URI effectiveBaseUrl;

    @Before
    public void before() throws IOException, URISyntaxException {
        testRestTemplate = new TestRestTemplate();
        executor = Executors.newSingleThreadScheduledExecutor();
        statusListenerMock = mock(StatusListener.class);

        effectiveBaseUrl = new URI(baseUrl);
    }

    private void prepareAccessibleOrgList(OrgPermission toBePlacedInList) throws IOException, ServletException {
        Collection<OrgPermission> accessibleOrgs = new ArrayList<>();
        accessibleOrgs.add(toBePlacedInList);
        when(authorization.getAccessibleOrgs(any())).thenReturn(accessibleOrgs);
    }

    private void noOrgsAccessible() throws IOException, ServletException {
        Collection<OrgPermission> accessibleOrgs = new ArrayList<>();
        when(authorization.getAccessibleOrgs(any())).thenReturn(accessibleOrgs);
    }

    @Test
    public void postRequest()
        throws InterruptedException, IOException, ServletException {
        when(tokenRetriever.getAuthToken(any(Authentication.class))).thenReturn(TOKEN);
        Org org = new Org(UUID.fromString("11111111-2222-3333-4444-555555555555"), "fakeName");
        OrgPermission permission = new OrgPermission(org, false, false);
        prepareAccessibleOrgList(permission);

        ResponseEntity<RequestDTO> response =
            testRestTemplate.postForEntity(effectiveBaseUrl,
                new Request.RequestBuilder(0, "http://foo/bar.txt").withOrgUUID(org.getGuid().toString()).build(),
                RequestDTO.class);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.ACCEPTED));

        String requestId = response.getBody().getId();

        executor.scheduleWithFixedDelay(() -> {
            final RequestDTO addedRequest = testRestTemplate.getForObject(effectiveBaseUrl + "/{id}", RequestDTO.class, requestId);
            statusListenerMock.found(addedRequest.getState());

        }, 0, 100, TimeUnit.MILLISECONDS);
        verify(statusListenerMock, timeout(500)).found(eqState(State.FINISHED));
        verify(authorization, times(2))
            .getAccessibleOrgs(any(HttpServletRequest.class));

        //FIXME: verify the file content and the metadata are stored.
    }


    @Test
    public void postRequest_emptySource_shouldREturn400()
            throws InterruptedException, IOException, ServletException {
        when(tokenRetriever.getAuthToken(any(Authentication.class))).thenReturn(TOKEN);
        Org org = new Org(UUID.fromString("11111111-2222-3333-4444-555555555555"), "fakeName");
        OrgPermission permission = new OrgPermission(org, false, false);
        prepareAccessibleOrgList(permission);

        ResponseEntity<String> response =
                testRestTemplate.postForEntity(effectiveBaseUrl,
                        new Request.RequestBuilder(0, "").withOrgUUID(org.getGuid().toString()).build(),
                        String.class);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.BAD_REQUEST));

    }

    @Test
    public void postRequest_authorizationFails_shouldReturn403()
        throws URISyntaxException, InterruptedException, IOException, ServletException {
        when(tokenRetriever.getAuthToken(any(Authentication.class))).thenReturn(TOKEN);
        String testOrgUUID = "11111111-2222-3333-4444-555555555555";
        noOrgsAccessible();

        ResponseEntity<String> response =
            testRestTemplate.postForEntity(
                effectiveBaseUrl,
                    new Request.RequestBuilder(0, "source_url").withOrgUUID(testOrgUUID).build(),
                String.class);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.FORBIDDEN));

        verify(authorization, times(1))
            .getAccessibleOrgs(any(HttpServletRequest.class));
    }

    @Test
    public void postRequest_authenticationFails_shouldReturn401()
            throws URISyntaxException, InterruptedException, IOException, ServletException {
        when(tokenRetriever.getAuthToken(any(Authentication.class))).thenReturn(null);
        String testOrgUUID = "11111111-2222-3333-4444-555555555555";
        noOrgsAccessible();

        ResponseEntity<String> response =
                testRestTemplate.postForEntity(
                        effectiveBaseUrl,
                        new Request.RequestBuilder(0, "source_url").withOrgUUID(testOrgUUID).build(),
                        String.class);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.UNAUTHORIZED));
    }

    @After
    public void after() {
        executor.shutdown();
    }

    static State eqState(State state) {
        return Mockito.argThat(new RequestMatcher(state));
    }

    private interface StatusListener {
        void found(State state);
    }


    /**
     * Matcher that looks for specific request state.
     */
    private static class RequestMatcher extends ArgumentMatcher<State> {

        private State desiredState;

        public RequestMatcher(State desiredState) {
            this.desiredState = desiredState;
        }

        @Override
        public boolean matches(Object state) {
            return  state == desiredState;
        }

    }
}
