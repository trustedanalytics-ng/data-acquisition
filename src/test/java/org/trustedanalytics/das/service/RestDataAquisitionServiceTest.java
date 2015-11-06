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
package org.trustedanalytics.das.service;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.ArgumentMatcher;
import org.trustedanalytics.cloud.auth.AuthTokenRetriever;
import org.trustedanalytics.das.dataflow.FlowManager;
import org.trustedanalytics.das.helper.RequestIdGenerator;
import org.trustedanalytics.das.parser.Request;
import org.trustedanalytics.das.security.permissions.PermissionVerifier;
import org.trustedanalytics.das.store.RequestStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@RunWith(MockitoJUnitRunner.class)
public class RestDataAquisitionServiceTest {

    @Mock
    private RequestStore requestStore;

    @Mock
    private RequestIdGenerator idGenerator;

    @Mock
    private FlowManager flowManager;

    @Mock
    private AuthTokenRetriever tokenRetriever;

    @Mock
    private HttpServletRequest context;

    @Mock
    private Function<String, FlowHandler> flowDispatcher;

    @Mock
    PermissionVerifier permissionVerifier;

    @InjectMocks
    private RestDataAcquisitionService service;

    @Before
    public void setUp() {
        when(flowDispatcher.apply(anyString())).thenReturn(new RequestFlowForExistingFile());
        when(flowDispatcher.apply("http")).thenReturn(new RequestFlowForNewFile());
        when(flowDispatcher.apply("https")).thenReturn(new RequestFlowForNewFile());
    }

    @Test
    public void testAdd_whenSourceIsHttpResource_DownloadFile() throws Exception {
        testAdd(getTestHttpRequest());
    }

    @Test
    public void testAdd_whenSourceIsHdfs_SkipDownloadAndNotify() throws Exception {
        testAdd(getTestRequestWithHdfsFile(), Request.State.VALIDATED);
    }

    @Test
    public void testAdd_whenSourceIsLocalFile_SkipDownloadAndNotify() throws Exception {
        testAdd(getTestRequestWithLocalFile1(), Request.State.VALIDATED);
    }

    @Test
    public void testAdd_whenSourceIsLocalFile2_SkipDownloadAndNotify() throws Exception {
        testAdd(getTestRequestWithLocalFile2(), Request.State.VALIDATED);
    }

    @Test
    public void testAdd_whenSourceIsLocalFile3_SkipDownloadAndNotify() throws Exception {
        testAdd(getTestRequestWithLocalFile3(), Request.State.VALIDATED);

    }

    private void testAdd(Request request) throws AccessDeniedException {
        testAdd(request, Request.State.NEW);
    }

    private void testAdd(Request request, Request.State expectedState) throws AccessDeniedException {
        Request expected = Request.newInstance(request);

        String testOrgUUID = UUID.randomUUID().toString();
        request.setToken("2asdas13");
        request.setOrgUUID(testOrgUUID);
        when(tokenRetriever.getAuthToken(any(Authentication.class))).thenReturn("1231aessa");
        when(idGenerator.getId(request.getSource())).thenReturn("2");

        service.addRequest(request, context);

        expected.setState(expectedState);
        expected.setId("2");
        expected.setOrgUUID(testOrgUUID);
        verify(requestStore).put(expected);
    }


    private Request getTestHttpRequest() {
        return Request.newInstance("org", 1, "1", "http://foo/bar.txt");
    }

    private Request getTestRequestWithHdfsFile() {
        return Request.newInstance("org", 1, "1", "hdfs://nameservice1/org/intel/hdfsbroker/userspace/c5378e1f-a35b-4b8b-b800/b519d8c7-2fc0-4842-920b/000000_1");
    }

    private Request getTestRequestWithLocalFile1() {
        return Request.newInstance("org", 1, "1", "file.csv");
    }

    private Request getTestRequestWithLocalFile2() {
        return Request.newInstance("org", 1, "1", "file (1).csv");
    }

    private Request getTestRequestWithLocalFile3() {
        return Request.newInstance("org", 1, "1", "file:///file%20(1).csv");
    }

    @Test
    public void testGetStatus() throws Exception {
        Request request = getTestHttpRequest();
        when(requestStore.get("1")).thenReturn(Optional.of(request));
        Request current = service.getRequest("1", context);
        Request expected = getTestHttpRequest();
        assertThat(current, equalTo(expected));
    }
}
