/**
 * Copyright (c) 2016 Intel Corporation
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.core.Authentication;
import org.trustedanalytics.cloud.auth.AuthTokenRetriever;
import org.trustedanalytics.das.dataflow.FlowManager;
import org.trustedanalytics.das.helper.RequestIdGenerator;
import org.trustedanalytics.das.parser.Request;
import org.trustedanalytics.das.parser.State;
import org.trustedanalytics.das.store.RequestStore;
import org.trustedanalytics.das.subservices.callbacks.CallbacksService;
import org.trustedanalytics.das.subservices.downloader.DownloadStatus;
import org.trustedanalytics.das.subservices.metadata.MetadataParseStatus;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;


@RunWith(MockitoJUnitRunner.class)
public class CallbackServiceTest {

    @Mock
    private FlowManager flowManager;

    @Mock
    private RequestStore requestStore;

    @Mock
    private AuthTokenRetriever retriever;

    @Mock
    private RequestIdGenerator requestIdGenerator;

    @InjectMocks
    private CallbacksService service;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void testRequestUploaded() {

        RequestDTO requestDTO = new RequestDTO();
        requestDTO.setCategory("other");
        requestDTO.setId("1");
        requestDTO.setIdInObjectStore("objectStoreId");
        requestDTO.setOrgUUID("org");
        requestDTO.setPublicRequest(true);
        requestDTO.setSource("file.csv");
        requestDTO.setState(State.FINISHED);
        requestDTO.setTimestamps(null);
        requestDTO.setTitle("title");
        requestDTO.setUserId(1);

        when(retriever.getAuthToken(any(Authentication.class))).thenReturn("token");

        assertEquals(service.uploaderStatusUpdate(requestDTO), "OK");
    }

    @Test
    public void testRequestDownloadedWithGivenRequestStoreId() {

        //given
        DownloadStatus status = getDownloadStatusWithState("DONE");
        Request request = new Request.RequestBuilder(0, "https://file.csv").build();

        //when
        when(requestStore.get("2")).thenReturn(Optional.of(request));

        //then
        String response = service.downloaderStatusUpdate(status, "2");

        verify(flowManager, times(1)).requestDownloaded(request);
        assertEquals(response, "OK");
    }

    @Test
    public void testRequestDownloadedWithEmptyRequestStoreId() {

        //given
        DownloadStatus status = getDownloadStatusWithState("DONE");

        //when
        when(requestStore.get("2")).thenReturn(Optional.empty());

        //then
        exception.expect(HttpMessageNotWritableException.class);
        service.downloaderStatusUpdate(status, "2");
    }

    @Test
    public void testDownloadedRequestFailed() {

        //given
        DownloadStatus status = getDownloadStatusWithState("FAILED");
        Request request = new Request.RequestBuilder(0, "https://file.csv").build();

        //when
        when(requestStore.get("2")).thenReturn(Optional.of(request));

        //then
        String response = service.downloaderStatusUpdate(status, "2");
        verify(flowManager, times(1)).requestFailed("2");
        assertEquals(response, "OK");
    }

    @Test
    public void testMetadataParsedWithStatusDone() {

        //given
        MetadataParseStatus status = new MetadataParseStatus();
        status.setState(MetadataParseStatus.State.DONE);
        status.setDescription("description");

        //when
        String response = service.metadataStatusUpdate(status, "2");

        //then
        verify(flowManager, times(1)).metadataParsed("2");
        assertEquals(response, "OK");
    }

    @Test
    public void testMetadataParsedWithStatusFailed() {

        //given
        MetadataParseStatus status = new MetadataParseStatus();
        status.setState(MetadataParseStatus.State.FAILED);
        status.setDescription("description");

        //when
        String response = service.metadataStatusUpdate(status, "2");

        //then
        verify(flowManager, times(1)).requestFailed("2");
        assertEquals(response, "OK");
    }

    private DownloadStatus getDownloadStatusWithState(String state) {
        DownloadStatus status = new DownloadStatus();
        status.setDownloadedBytes(240L);
        status.setId("1");
        status.setObjectStoreId("objectStoreId");
        status.setSavedObjectId("savedObjectId");
        status.setSource("https://file.csv");
        status.setState(state);
        return status;
    }
}
