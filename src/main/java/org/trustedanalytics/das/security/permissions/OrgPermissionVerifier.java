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
package org.trustedanalytics.das.security.permissions;

import org.springframework.stereotype.Component;
import org.trustedanalytics.das.security.PermissionAcquireFilter;
import org.trustedanalytics.das.service.RequestDTO;

import java.nio.file.AccessDeniedException;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

@Component
public class OrgPermissionVerifier implements PermissionVerifier {
    @Override
    public Collection<String> getAccessibleOrgsIDs(HttpServletRequest context) {
        return (Collection<String>) context.getAttribute(PermissionAcquireFilter.ACCESSIBLE_ORGS);
    }

    @Override
    public void throwForbiddenWhenNotAuthorized(HttpServletRequest context, RequestDTO request)
        throws AccessDeniedException {
        throwForbiddenWhenIdNotListed(getAccessibleOrgsIDs(context), request.getOrgUUID());
    }

    @Override
    public void throwForbiddenWhenIdNotListed(Collection<String> hasAccess, String uuid)
        throws AccessDeniedException {
        if(!hasAccess.contains(uuid)) {
            throw new AccessDeniedException(String.format("You have not access to org: %s", uuid));
        }
    }
}
