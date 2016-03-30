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

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.MS2;
import org.labkey.test.components.targetedms.GuideSet;
import org.labkey.test.components.targetedms.GuideSetStats;
import org.labkey.test.components.targetedms.GuideSetWebPart;
import org.labkey.test.components.targetedms.ParetoPlotsWebPart;
import org.labkey.test.components.targetedms.QCPlotsWebPart;
import org.labkey.test.pages.targetedms.PanoramaDashboard;
import org.labkey.test.pages.targetedms.ParetoPlotPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.RelativeUrl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category({DailyB.class, MS2.class})
public class TargetedMSQCGuideSetTest extends TargetedMSTest
{
    private static final String[] PRECURSORS = {
            "ATEEQLK",
            "FFVAPFPEVFGK",
            "GASIVEDK",
            "LVNELTEFAK",
            "VLDALDSIK",
            "VLVLDTDYK",
            "VYVEELKPTPEGDLEILLQK"};

    private static GuideSet gs1 = new GuideSet("2013/08/01", "2013/08/01 00:00:01", "first guide set, entirely before initial data with no data points in range");
    private static GuideSet gs2 = new GuideSet("2013/08/02", "2013/08/11", "second guide set, starts before initial data start date with only one data point in range");
    private static GuideSet gs3 = new GuideSet("2013/08/14 22:48:37", "2013/08/16 20:26:28", "third guide set, ten data points in range", 10);
    private static GuideSet gs4 = new GuideSet("2013/08/21 07:56:12", "2013/08/21 13:15:01", "fourth guide set, four data points in range", 4);
    private static GuideSet gs5 = new GuideSet("2013/08/27 03:00", "2013/08/31 00:00", "fifth guide set, extends beyond last initial data point with two data points in range");


    @Override
    protected String getProjectName()
    {
        return getClass().getSimpleName() + " Project";
    }

    @BeforeClass
    public static void initProject()
    {
        TargetedMSQCGuideSetTest init = (TargetedMSQCGuideSetTest)getCurrentTest();

        init.setupFolder(FolderType.QC);
        init.importData(SProCoP_FILE);

        init.createGuideSet(gs1);
        init.createGuideSet(gs2);
        init.createGuideSet(gs3);
        init.createGuideSet(gs4);
        init.createGuideSet(gs5);
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @Test
    public void testGuideSetStats()
    {
        // verify guide set mean/std dev/num records from SQL queries
        verifyGuideSet1Stats(gs1);
        verifyGuideSet2Stats(gs2);
        verifyGuideSet3Stats(gs3);
        verifyGuideSet4Stats(gs4);
        verifyGuideSet5Stats(gs5);
    }

    @Test
    public void testGuideSetCreateValidation()
    {
        String overlapErrorMsg = "The training date range overlaps with an existing guide set's training date range.";

        // test validation error message from Guide Set Insert New page
        goToProjectHome();
        createGuideSet(new GuideSet("2013/08/10 00:00:01", "2013/08/10 00:00:00", null), "The training start date/time must be before the training end date/time.");
        createGuideSet(new GuideSet("2013/08/01", "2013/08/12", null), overlapErrorMsg);
        createGuideSet(new GuideSet("2013/08/01", "2013/08/03", null), overlapErrorMsg);
        createGuideSet(new GuideSet("2013/08/10", "2013/08/12", null), overlapErrorMsg);

        // test validation error message from QC plot guide set creation mode
        goToProjectHome();
        createGuideSet(new GuideSet("2013/08/09 11:39:00", "2013/08/11 18:34:14", null, 2), overlapErrorMsg);
        createGuideSet(new GuideSet("2013/08/21 01:12:00", "2013/08/21 07:56:12", null, 5), overlapErrorMsg);
        createGuideSet(new GuideSet("2013/08/09 11:39:00", "2013/08/27 14:45:49", null, 47), overlapErrorMsg);
    }

    @Test
    public void testGuideSetPlotDisplay()
    {
        PanoramaDashboard qcDashboard = new PanoramaDashboard(this);
        QCPlotsWebPart qcPlotsWebPart = qcDashboard.getQcPlotsWebPart();

        // 4 of the 5 guide sets are visible in plot region based on the initial data
        List<Pair<String, Integer>> shapeCounts = new ArrayList<>();
        shapeCounts.add(Pair.of(SvgShapes.CIRCLE.getPathPrefix(), 4));
        shapeCounts.add(Pair.of(SvgShapes.TRIANGLE.getPathPrefix(), 23));
        shapeCounts.add(Pair.of(SvgShapes.SQUARE.getPathPrefix(), 18));
        shapeCounts.add(Pair.of(SvgShapes.DIAMOND.getPathPrefix(), 2));
        verifyGuideSetRelatedElementsForPlots(qcPlotsWebPart, 4, shapeCounts, 47);

        // check box for group x-axis values by date and verify
        qcPlotsWebPart.setGroupXAxisValuesByDate(true);
        verifyGuideSetRelatedElementsForPlots(qcPlotsWebPart, 4, shapeCounts, 20);
        qcPlotsWebPart.setShowAllPeptidesInSinglePlot(true, 1);
        assertEquals("Unexpected number of training range rects visible", 4, qcPlotsWebPart.getGuideSetTrainingRectCount());
        qcPlotsWebPart.setShowAllPeptidesInSinglePlot(false, null);
        qcPlotsWebPart.setGroupXAxisValuesByDate(false);

        // filter plot by start/end date to check reference points without training points in view
        qcPlotsWebPart.filterQCPlots("2013-08-19", "2013-08-19", PRECURSORS.length);
        shapeCounts = new ArrayList<>();
        shapeCounts.add(Pair.of(SvgShapes.CIRCLE.getPathPrefix(), 2));
        shapeCounts.add(Pair.of(SvgShapes.TRIANGLE.getPathPrefix(), 0));
        verifyGuideSetRelatedElementsForPlots(qcPlotsWebPart, 0, shapeCounts, 2);
    }

    @Test
    public void testParetoPlot()
    {
        clickAndWait(Locator.linkWithText("Pareto Plot")); //go to Pareto Plot tab

        waitForElement(Locator.css("svg"));

        ParetoPlotPage paretoPage = new ParetoPlotPage(getDriver());
        ParetoPlotsWebPart paretoPlotsWebPart = paretoPage.getParetoPlotsWebPart();

        assertEquals("Wrong number of Pareto plots", 4, paretoPlotsWebPart.getNumOfParetoPlots());
        assertEquals("Wrong number of non-conformers for PA", 69, paretoPlotsWebPart.getPlotBarHeight(3, 0));
        assertEquals("Wrong number of non-conformers for P Area", 64, paretoPlotsWebPart.getPlotBarHeight(3, 1));
        assertEquals("Wrong number of non-conformers for T Area", 60, paretoPlotsWebPart.getPlotBarHeight(3, 2));
        assertEquals("Wrong number of non-conformers for MA", 57, paretoPlotsWebPart.getPlotBarHeight(3, 3));
        assertEquals("Wrong number of non-conformers for T/P Ratio", 29, paretoPlotsWebPart.getPlotBarHeight(3, 4));
        assertEquals("Wrong number of non-conformers for RT", 16, paretoPlotsWebPart.getPlotBarHeight(3, 5));
        assertEquals("Wrong number of non-conformers for FWHM", 13, paretoPlotsWebPart.getPlotBarHeight(3, 6));
        assertEquals("Wrong number of non-conformers for FWB", 7, paretoPlotsWebPart.getPlotBarHeight(3, 7));

        verifyTicksOnPlots(paretoPlotsWebPart, 3);
        verifyDownloadableParetoPlotPdf();
        verifyNavigationToPanoramaDashboard(3, 0);
    }

    @Test
    public void testEmptyParetoPlot()
    {
        setupSubfolder(getProjectName(), "Empty Pareto Plot Test", FolderType.QC); //create a Panorama folder of type QC

        clickAndWait(Locator.linkWithText("Pareto Plot")); //go to Pareto Plot tab

        ParetoPlotPage paretoPage = new ParetoPlotPage(getDriver());
        ParetoPlotsWebPart paretoPlotsWebPart = paretoPage.getParetoPlotsWebPart();

        paretoPlotsWebPart.clickLeveyJenningsLink(this);

        assertElementPresent(Locator.tagWithClass("span", "labkey-wp-title-text").withText(QCPlotsWebPart.DEFAULT_TITLE));
    }

    private void verifyGuideSetRelatedElementsForPlots(QCPlotsWebPart qcPlotsWebPart, int visibleTrainingRanges, List<Pair<String, Integer>> shapeCounts, int axisTickCount)
    {
        for (Pair<String, Integer> shapeCount : shapeCounts)
        {
            String pathPrefix = shapeCount.getLeft();
            int count = qcPlotsWebPart.getPointElements("d", pathPrefix, true).size();
            assertEquals("Unexpected guide set shape count for " + pathPrefix, shapeCount.getRight() * PRECURSORS.length, count);
        }

        assertEquals("Unexpected number of training range rects visible", visibleTrainingRanges * PRECURSORS.length, qcPlotsWebPart.getGuideSetTrainingRectCount());
        assertEquals("Unexpected number of error bar elements", axisTickCount * PRECURSORS.length * 4, qcPlotsWebPart.getGuideSetErrorBarPathCount("error-bar-vert"));
    }

    private void validateGuideSetStats(GuideSet gs)
    {
        for (GuideSetStats stats : gs.getStats())
        {
            navigateToGuideSetStatsQuery(stats.getMetricName());

            DataRegionTable table = new DataRegionTable("query", this);
            table.setFilter("GuideSetId", "Equals", String.valueOf(gs.getRowId()));
            if (stats.getPrecursor() != null)
                table.setFilter("Sequence", "Equals", stats.getPrecursor());
            else
                table.setFilter("Sequence", "Is Blank", null);

            assertEquals("Unexpected number of filtered rows", 1, table.getDataRowCount());
            assertEquals("Unexpected guide set stats record count", stats.getNumRecords(), Integer.parseInt(table.getDataAsText(0, "NumRecords")));

            if (stats.getMean() != null)
                assertEquals("Unexpected guide set stats mean", stats.getMean(), Double.parseDouble(table.getDataAsText(0, "Mean")), 0.0005);
            else
                assertNull("Unexpected guide set stats mean", stats.getMean());

            if (stats.getStdDev() != null)
                assertEquals("Unexpected guide set stats std dev", stats.getStdDev(), Double.parseDouble(table.getDataAsText(0, "StandardDev")), 0.0005);
            else
                assertNull("Unexpected guide set stats std dev", stats.getStdDev());
        }
    }

    private void navigateToGuideSetStatsQuery(String metricName)
    {
        RelativeUrl queryURL = new RelativeUrl("query", "executequery");
        queryURL.setContainerPath(getCurrentContainerPath());
        queryURL.addParameter("schemaName", "targetedms");
        queryURL.addParameter("query.queryName", "GuideSetStats_" + metricName);

        queryURL.navigate(this);
    }

    private void verifyGuideSet1Stats(GuideSet gs)
    {
        gs.addStats(new GuideSetStats("retentionTime", 0));
        gs.addStats(new GuideSetStats("peakArea", 0));
        gs.addStats(new GuideSetStats("fwhm", 0));
        gs.addStats(new GuideSetStats("fwb", 0));
        gs.addStats(new GuideSetStats("ratio", 0));
        gs.addStats(new GuideSetStats("transitionPrecursorRatio", 0));
        gs.addStats(new GuideSetStats("massAccuracy", 0));

        validateGuideSetStats(gs);
    }

    private void verifyGuideSet2Stats(GuideSet gs)
    {
        gs.addStats(new GuideSetStats("retentionTime", 1, PRECURSORS[0], 14.880, null));
        gs.addStats(new GuideSetStats("peakArea", 1, PRECURSORS[0], 1.1613580288E10, null));
        gs.addStats(new GuideSetStats("fwhm", 1, PRECURSORS[0], 0.096, null));
        gs.addStats(new GuideSetStats("fwb", 1, PRECURSORS[0], 0.292, null));
        gs.addStats(new GuideSetStats("ratio", 0));
        gs.addStats(new GuideSetStats("transitionPrecursorRatio", 1, PRECURSORS[0], 0.06410326063632965, null));
        gs.addStats(new GuideSetStats("massAccuracy", 1, PRECURSORS[0], -0.0025051420088857412, null));

        validateGuideSetStats(gs);
    }

    private void verifyGuideSet3Stats(GuideSet gs)
    {
        gs.addStats(new GuideSetStats("retentionTime", 10, PRECURSORS[1], 32.151, 0.026));
        gs.addStats(new GuideSetStats("peakArea", 10, PRECURSORS[1], 2.930734907392E11, 6.454531590675328E10));
        gs.addStats(new GuideSetStats("fwhm", 10, PRECURSORS[1], 0.11, 0.015));
        gs.addStats(new GuideSetStats("fwb", 10, PRECURSORS[1], 0.326, 0.025));
        gs.addStats(new GuideSetStats("ratio", 0));
        gs.addStats(new GuideSetStats("transitionPrecursorRatio", 10, PRECURSORS[1], 0.16636697351932525, 0.024998646348985));
        gs.addStats(new GuideSetStats("massAccuracy", 10, PRECURSORS[1], -0.14503030776977538, 0.5113428116648383));

        validateGuideSetStats(gs);
    }

    private void verifyGuideSet4Stats(GuideSet gs)
    {
        gs.addStats(new GuideSetStats("retentionTime", 4, PRECURSORS[2], 14.031, 0.244));
        gs.addStats(new GuideSetStats("peakArea", 4, PRECURSORS[2], 1.1564451072E10, 1.5713155146840603E9));
        gs.addStats(new GuideSetStats("fwhm", 4, PRECURSORS[2], 0.088, 0.006));
        gs.addStats(new GuideSetStats("fwb", 4, PRECURSORS[2], 0.259, 0.013));
        gs.addStats(new GuideSetStats("ratio", 0));
        gs.addStats(new GuideSetStats("transitionPrecursorRatio", 4, PRECURSORS[2], 0.0, 0.0));
        gs.addStats(new GuideSetStats("massAccuracy", 4, PRECURSORS[2], 1.7878320217132568, 0.09473514310269647));

        validateGuideSetStats(gs);
    }

    private void verifyGuideSet5Stats(GuideSet gs)
    {
        gs.addStats(new GuideSetStats("retentionTime", 2, PRECURSORS[3], 24.581, 0.011));
        gs.addStats(new GuideSetStats("peakArea", 2, PRECURSORS[3], 5.6306905088E10, 1.5347948865359387E9));
        gs.addStats(new GuideSetStats("fwhm", 2, PRECURSORS[3], 0.072, 0.009));
        gs.addStats(new GuideSetStats("fwb", 2, PRECURSORS[3], 0.219, 0.011));
        gs.addStats(new GuideSetStats("ratio", 0));
        gs.addStats(new GuideSetStats("transitionPrecursorRatio", 2, PRECURSORS[3], 0.06426714546978474, 0.02016935064728605));
        gs.addStats(new GuideSetStats("massAccuracy", 2, PRECURSORS[3], 1.6756309866905212, 0.23667992679147354));

        validateGuideSetStats(gs);
    }

    private void createGuideSet(GuideSet guideSet)
    {
        createGuideSet(guideSet, null);
    }

    private void createGuideSet(GuideSet guideSet, String expectErrorMsg)
    {
        if (guideSet.getBrushSelectedPoints() != null)
        {
            // create the guide set from the QC plot brush selection
            if (null != getUrlParam("pageId"))
                clickTab("Panorama Dashboard");

            PanoramaDashboard qcDashboard = new PanoramaDashboard(this);
            qcDashboard.getQcPlotsWebPart().createGuideSet(guideSet, expectErrorMsg);
            qcDashboard.getQcPlotsWebPart().waitForReady();
        }
        else
        {
            createGuideSetFromTable(guideSet);
        }

        if (expectErrorMsg == null)
            addRowIdForCreatedGuideSet(guideSet);
    }

    private void addRowIdForCreatedGuideSet(GuideSet guideSet)
    {
        if (!"Guide Sets".equals(getUrlParam("pageId", true)))
            clickTab("Guide Sets");

        GuideSetWebPart guideSetWebPart = new GuideSetWebPart(this, getProjectName());
        guideSet.setRowId(guideSetWebPart.getRowId(guideSet));
    }

    private void verifyTicksOnPlots(ParetoPlotsWebPart paretoPlotsWebPart, int guideSetNum)
    {
        List<String> ticks = paretoPlotsWebPart.getTicks(guideSetNum);

        for(String chartType : ticks)
            assertTrue("Chart Type tick '" + chartType + "' is not valid", paretoPlotsWebPart.isChartTypeTickValid(chartType));
    }

    private void verifyDownloadableParetoPlotPdf()
    {
        //Check for clickable pdf button for Pareto Plot
        clickAndWaitForDownload(Locator.css("#paretoPlot-GuideSet-3-exportToPDFbutton > a"));
    }

    private void verifyNavigationToPanoramaDashboard(int guideSetNum, int barPlotNum)
    {
        //click on "Peak Area" bar
        clickAndWait(Locator.css("#paretoPlot-GuideSet-" + guideSetNum + "-" + barPlotNum + " > a:nth-child(1) > rect"));

        //check navigation to 'Panorama Dashboard' tab
        assertEquals("Panorama Dashboard", getText(Locator.css(".tab-nav-active")));

        PanoramaDashboard qcDashboard = new PanoramaDashboard(this);
        QCPlotsWebPart qcPlotsWebPart = qcDashboard.getQcPlotsWebPart();

        //test for correct chart type
        assertEquals(QCPlotsWebPart.ChartType.PEAK, qcPlotsWebPart.getCurrentChartType());

        //compare url Start Date with input form Start Date
        assertEquals("startDate in the URL does not equal 'Start Date' on the page", parseUrlDate(getUrlParam("startDate", true)), parseFormDate(qcPlotsWebPart.getCurrentStartDate()));

        //compare url End Date with input form End Date
        assertEquals("endDate in the URL does not equal 'End Date' on the page", parseUrlDate(getUrlParam("endDate", true)), parseFormDate(qcPlotsWebPart.getCurrentEndDate()));
    }

    private Date parseUrlDate(String urlDate)
    {
        SimpleDateFormat urlDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        try
        {
            return urlDateFormat.parse(urlDate);
        }
        catch (ParseException fail)
        {
            throw new RuntimeException(fail);
        }
    }

    private Date parseFormDate(String formDate)
    {
        SimpleDateFormat formDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try
        {
            return formDateFormat.parse(formDate);
        }
        catch (ParseException fail)
        {
            throw new RuntimeException(fail);
        }
    }
}
