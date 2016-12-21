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
package org.trustedanalytics.das.store.memory;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

import org.trustedanalytics.das.parser.Request;
import org.trustedanalytics.das.store.RequestStore;

public class InMemoryRequestStoreTest {

    private RequestStore store;

    @Before
    public void initialize() {
        store = new InMemoryRequestStore();
    }

    @Test
    public void makingPrefixedKey() {
        assertEquals(
            "orgId:key",
            store.getOrgPrefixedKey("orgId", "key"));
    }

    @Test
    public void getput() throws URISyntaxException {
        String orgId = "orgId1";
        String key = "key1";
        Request request = new Request.RequestBuilder(1, "file:///foo/bar.txt").withOrgId(orgId).withId(key).build();
        store.put(request);
        assertThat(store.get(key).get(), equalTo(request));
    }

    @Test
    public void testGetAll() throws Exception {
        String orgId1 = "orgId1";
        String orgId2 = "orgId2";
        Request request1 = new Request.RequestBuilder(1, "file:///foo/bar.txt").withOrgId(orgId1).withId("key1").build();
        Request request2 = new Request.RequestBuilder(2, "file:///foo/bar.txt").withOrgId(orgId1).withId("key2").build();
        Request request3 = new Request.RequestBuilder(3, "file:///foo/bar.txt").withOrgId(orgId2).withId("key3").build();
        store.put(request1);
        store.put(request2);
        store.put(request3);
        assertThat(store.getAll(orgId1).keySet(), containsInAnyOrder("orgId1:key1", "orgId1:key2"));
        assertThat(store.getAll(orgId1).values(), containsInAnyOrder(request1, request2));
        assertThat(store.getAll(orgId2).values(), containsInAnyOrder(request3));
    }

    @Test
    public void delete() throws URISyntaxException {
        String key = "key1";
        String orgId = "orgId";
        Request request =
                new Request.RequestBuilder(1, "file:///foo/bar.txt").withOrgId(orgId).withId(key).build();
        store.put(request);

        store.delete(key);
        
        assertThat(store.getAll(orgId).keySet(), empty());
    }
}
