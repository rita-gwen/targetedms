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
package org.labkey.test.tests.targetedms;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.MS2;
import org.labkey.test.components.targetedms.LinkVersionsGrid;
import org.labkey.test.components.targetedms.TargetedMSRunsTable;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category({DailyB.class, MS2.class})
public class TargetedMSLinkVersionsTest extends TargetedMSTest
{
    public static List<String> QC_DOCUMENT_NAMES = Arrays.asList(QC_1_FILE, QC_2_FILE, QC_3_FILE);

    private static int PIPELINE_JOB_COUNTER = 0;

    @Override
    protected String getProjectName()
    {
        return getClass().getSimpleName() + " Project";
    }

    @BeforeClass
    public static void initProject()
    {
        TargetedMSLinkVersionsTest init = (TargetedMSLinkVersionsTest)getCurrentTest();
        init.setupFolder(FolderType.QC);

        // pre-upload the files to the pipeline root so that all of the @Test don't have to worry about it
        init.goToModule("Pipeline");
        init.clickButton("Process and Import Data");
        for (String file : QC_DOCUMENT_NAMES)
            init._fileBrowserHelper.uploadFile(TestFileUtils.getSampleData("TargetedMS/" + file));
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
        clickTab("Runs");

        // since importing one of these runs is really quick, delete and re-import runs
        // for each @Test so that we can assure the Created date ordering of the runs
        deleteExistingQCRuns();
        importData(QC_1_FILE, (PIPELINE_JOB_COUNTER < 3 ? ++PIPELINE_JOB_COUNTER : 3), false);
        importData(QC_2_FILE, (PIPELINE_JOB_COUNTER < 3 ? ++PIPELINE_JOB_COUNTER : 3), false);
        importData(QC_3_FILE, (PIPELINE_JOB_COUNTER < 3 ? ++PIPELINE_JOB_COUNTER : 3), false);
        clickTab("Runs");
    }

    private void deleteExistingQCRuns()
    {
        boolean hasRunsToDelete = false;
        TargetedMSRunsTable table = new TargetedMSRunsTable(this);

        for (String documentName : QC_DOCUMENT_NAMES)
        {
            if (table.getRow("File", documentName) > -1)
            {
                table.checkCheckbox(table.getRow("File", documentName));
                hasRunsToDelete = true;
            }
        }

        if (hasRunsToDelete)
        {
            table.clickHeaderButtonByText("Delete");
            clickButton("Confirm Delete");
        }
    }

    @Test
    public void testReorderMethodChain()
    {
        log("verify originally no linked versions");
        TargetedMSRunsTable table = new TargetedMSRunsTable(this);
        table.goToDocumentDetails(QC_1_FILE);
        waitForElement(LinkVersionsGrid.Elements.noVersions);

        log("link two versions together");
        clickTab("Runs");
        LinkVersionsGrid grid = table.openDialogForDocuments(Arrays.asList(QC_1_FILE, QC_2_FILE));
        assertElementNotPresent(LinkVersionsGrid.Elements.replaceFooter);
        grid.clickSave();
        verifyDocumentDetailsChain(Arrays.asList(QC_1_FILE, QC_2_FILE), 0);

        log("add third document to version chain");
        clickTab("Runs");
        grid = table.openDialogForDocuments(Arrays.asList(QC_1_FILE, QC_3_FILE), 3);
        grid.waitForGrid(QC_DOCUMENT_NAMES, true); // verify QC_2 document is pulled in by association
        assertElementPresent(LinkVersionsGrid.Elements.replaceFooter);
        assertEquals(2, grid.findRemoveLinkIcons().size());
        grid.clickSave();
        verifyDocumentDetailsChain(QC_DOCUMENT_NAMES, 2);

        log("re-order documents in the existing chain");
        clickTab("Runs");
        grid = table.openDialogForDocuments(QC_DOCUMENT_NAMES);
        assertEquals(3, grid.findRemoveLinkIcons().size());
        grid.reorderVersions(2, 0);
        grid.clickSave();
        verifyDocumentDetailsChain(Arrays.asList(QC_3_FILE, QC_1_FILE, QC_2_FILE), 1);
    }

    @Test
    public void testRemoveFromMethodChain()
    {
        log("setup chain for the 3 runs");
        TargetedMSRunsTable table = new TargetedMSRunsTable(this);
        LinkVersionsGrid grid = table.openDialogForDocuments(QC_DOCUMENT_NAMES);
        grid.clickSave();

        log("remove link version from middle of chain");
        grid = table.openDialogForDocuments(QC_DOCUMENT_NAMES);
        assertEquals(3, grid.findRemoveLinkIcons().size());
        grid.removeLinkVersion(1);
        verifyDocumentDetailsChain(Arrays.asList(QC_1_FILE, QC_3_FILE), 0);

        log("remove link version from end of chain");
        clickTab("Runs");
        grid = table.openDialogForDocuments(Arrays.asList(QC_1_FILE, QC_3_FILE));
        assertEquals(2, grid.findRemoveLinkIcons().size());
        grid.removeLinkVersion(1);
        table.goToDocumentDetails(QC_1_FILE);
        waitForElement(LinkVersionsGrid.Elements.noVersions);
    }

    @Test
    public void testDeleteRunWhenInChain()
    {
        log("setup chain for the 3 runs");
        TargetedMSRunsTable table = new TargetedMSRunsTable(this);
        LinkVersionsGrid grid = table.openDialogForDocuments(QC_DOCUMENT_NAMES);
        grid.clickSave();
        verifyDocumentDetailsChain(QC_DOCUMENT_NAMES, 0);

        log("delete the run which is in a chain");
        clickTab("Runs");
        table.deleteRun(QC_2_FILE);

        log("verify that the chain was updated correctly for remaining runs");
        table.goToDocumentDetails(QC_1_FILE);
        waitForElement(LinkVersionsGrid.Elements.noVersions);
        clickTab("Runs");
        table.goToDocumentDetails(QC_3_FILE);
        waitForElement(LinkVersionsGrid.Elements.noVersions);
    }

    private void verifyDocumentDetailsChain(List<String> documentNames, int index)
    {
        TargetedMSRunsTable table = new TargetedMSRunsTable(this);
        table.goToDocumentDetails(documentNames.get(index));

        LinkVersionsGrid grid = new LinkVersionsGrid(this);
        grid.waitForGrid(documentNames, false);
    }
}