/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.targetedms;

import org.labkey.api.data.Container;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.api.targetedms.TargetedMSService;

/**
 * User: vsharma
 * Date: 8/26/2015
 * Time: 2:31 PM
 */
public class TargetedMSServiceImpl implements TargetedMSService
{
    @Override
    public ITargetedMSRun getRun(int runId, Container container)
    {
        TargetedMSRun run = TargetedMSManager.getRun(runId);
        if(run != null && run.getContainer().equals(container))
        {
            return run;
        }
        return null;
    }
}