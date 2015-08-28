/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.test.tests;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.util.FileBrowserHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.UIContainerHelper;

import java.util.Arrays;
import java.util.List;

public abstract class TargetedMSTest extends BaseWebDriverTest
{
    protected static final String SProCoP_FILE = "SProCoPTutorial.zip";
    protected static final String QC_1_FILE = "QC_1.sky.zip";
    protected static final String QC_2_FILE = "QC_2.sky.zip";
    protected static final String QC_3_FILE = "QC_3.sky.zip";

    public enum FolderType {
        Experiment, Library, LibraryProtein, QC, Undefined
    }

    public TargetedMSTest()
    {
        setContainerHelper(new UIContainerHelper(this));
    }

    @Override
    protected String getProjectName()
    {
        return "TargetedMS" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
    }

    @LogMethod
    protected void setupFolder(FolderType folderType)
    {
        _containerHelper.createProject(getProjectName(), "Panorama");
        selectFolderType(folderType);
    }

    @LogMethod
    protected void setupFolder(String folderName, FolderType folderType)
    {
        _containerHelper.createProject(folderName, "Panorama");
        selectFolderType(folderType);
    }

    @LogMethod
    protected void importData(String file)
    {
        importData(file, 1);
    }

    @LogMethod
    protected void importData(String file, int jobCount)
    {
        importData(file, jobCount, true);
    }

    @LogMethod
    protected void importData(String file, int jobCount, boolean uploadIfDoesntExist)
    {
        log("Importing file " + file);
        goToModule("Pipeline");
        clickButton("Process and Import Data");
        _fileBrowserHelper.waitForFileGridReady();
        if (uploadIfDoesntExist && !isElementPresent(FileBrowserHelper.Locators.gridRow(file)))
            _fileBrowserHelper.uploadFile(TestFileUtils.getSampleData("TargetedMS/" + file));
        _fileBrowserHelper.importFile(file, "Import Skyline Results");
        waitForText("Skyline document import");
        waitForPipelineJobsToFinish(jobCount);
    }

    @LogMethod
    protected void verifyRunSummaryCounts(int proteinCount, int peptideCount, int precursorCount, int transitionCount)
    {
        log("Verifying expected summary counts");
        assertElementPresent(Locator.xpath("//tr[td[text()='Protein Count']][td[text()='" + proteinCount + "']]"));
        assertElementPresent(Locator.xpath("//tr[td[text()='Peptide Count']][td[text()='" + peptideCount + "']]"));
        assertElementPresent(Locator.xpath("//tr[td[text()='Precursor Count']][td[text()='" + precursorCount + "']]"));
        assertElementPresent(Locator.xpath("//tr[td[text()='Transition Count']][td[text()='" + transitionCount + "']]"));
    }

    @LogMethod
    protected void selectFolderType(FolderType folderType) {
        log("Select Folder Type: " + folderType);
        switch(folderType)
        {
            case Experiment:
                click(Locator.radioButtonById("experimentalData")); // click the first radio button - Experimental Data
                break;
            case Library:
                click(Locator.radioButtonById("chromatogramLibrary")); // click the 2nd radio button - Library
                break;
            case LibraryProtein:
                click(Locator.radioButtonById("chromatogramLibrary")); // click the 2nd radio button - Library
                click(Locator.checkboxByName("precursorNormalized")); // check the normalization checkbox.
                break;
            case QC:
                click(Locator.radioButtonById("QC")); // click the 3rd radio button - QC
                break;
        }
        clickButton("Finish");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        deleteProject(getProjectName(), afterTest);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("targetedms");
    }

    @Override
    public BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }
}
