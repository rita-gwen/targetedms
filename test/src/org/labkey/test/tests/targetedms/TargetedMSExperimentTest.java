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
package org.labkey.test.tests.targetedms;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.test.Locator;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.MS2;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.UIContainerHelper;

import static org.junit.Assert.*;

@Category({DailyB.class, MS2.class})
public class TargetedMSExperimentTest extends TargetedMSTest
{
    private static final String SKY_FILE = "MRMer.zip";
    private static final String SKY_FILE2 = "MRMer_renamed_protein.zip";

    public TargetedMSExperimentTest()
    {
        // We want to use the UI when creating the project/folder so that we can verify that we get the wizard
        // that has the extra steps
        setContainerHelper(new UIContainerHelper(this));
    }

    @Test
    public void testSteps()
    {
        setupFolder(FolderType.Experiment);
        importData(SKY_FILE);
        verifyImportedData();
        verifyModificationSearch();
        importData(SKY_FILE2, 2);
        verifyProteinSearch();
        verifyQueries();
    }

    @LogMethod
    protected void verifyImportedData()
    {
        clickAndWait(Locator.linkContainingText("Panorama Dashboard"));
        clickAndWait(Locator.linkContainingText(SKY_FILE));
        verifyRunSummaryCounts(24,44,88,296); // Number of protein, peptides, precursors, transitions
        verifyDocumentDetails();
        verifyPeptide();
    }

    @LogMethod
    protected void verifyDocumentDetails()
    {
        assertTextPresent("CDC19 SGDID:S000000036, Chr I from 71787-73289, Verified ORF, \"Pyruvate kinase, functions as a homotetramer in glycolysis to convert phosphoenolpyruvate to pyruvate, the input for aerobic (TCA cycle) or anaerobic (glucose fermentation) respiration");
        // Verify expected peptides/proteins in the nested view
        //Verify that amino acids from peptides are highlighted in blue as expected.
        assertElementPresent(Locator.xpath("//tr//td//a[span[text()='LTSLNVVAGSDL'][span[contains(@style,'font-weight:bold;color:#0000ff;') and text()='R']]]"));
    }

    @LogMethod
    protected void verifyProteinSearch()
    {
        clickAndWait(Locator.linkContainingText("Panorama Dashboard"));
        assertTextPresent("Mass Spec Search", "Protein Search");
        _ext4Helper.clickExt4Tab("Protein Search");
        waitForElement(Locator.name("identifier"));

        // Test fix for issue 18217
        // MRMer.zip contains the protein YAL038W.  MRMer_renamed_protein.zip contains the same protein with the
        // name YAL038W_renamed.  MRMer.zip is imported first and  a new entry is created in prot.sequences with YAL038W
        // as the bestname. MRMer_renamed_protein is imported second, and an entry is created in prot.identifiers
        // for YAL038W_renamed. A search for YAL038W_renamed should return one protein result.
        setFormElement(Locator.name("identifier"), "YAL038W_renamed");
        waitAndClickAndWait(Locator.lkButton("Search"));
        waitForText("Protein Search Results");
        //waitForText("1 - 7 of 7");
        assertTextPresentInThisOrder("Protein Search", "Matching Proteins (1)", "Targeted MS Peptides");
        assertEquals(1, getElementCount(Locator.xpath("id('dataregion_PotentialProteins')/tbody/tr/td/a[contains(text(),'YAL038W')]")));
        assertEquals(7, getElementCount(Locator.xpath("//td/span/a[contains(text(), 'YAL038W')]")));
        assertEquals(1, getElementCount(Locator.xpath("//td/span/a[contains(text(), 'YAL038W_renamed')]")));
    }

    @LogMethod
    protected void verifyModificationSearch()
    {
        // add modificaiton search webpart and do an initial search by AminoAcid and DeltaMass
        clickAndWait(Locator.linkContainingText("Panorama Dashboard"));
        waitForElement(Locator.id("identifierInput"));
        _ext4Helper.clickExt4Tab("Modification Search");
        waitForElement(Locator.name("aminoAcids"));
        setFormElement(Locator.name("aminoAcids"), "R");
        setFormElement(Locator.name("deltaMass"), "10");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Search"));
        waitForText("Modification Search Results");
        //waitForText("1 - 13 of 13");
        assertTextPresentInThisOrder("Targeted MS Modification Search", "Targeted MS Peptides");
        assertTextPresent("Amino acids:", "Delta mass:");
        assertEquals(13, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'R[+10]')]")));
        assertEquals(0, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'K[+8]')]")));

        // search for K[+8] modification
        setFormElement(Locator.name("aminoAcids"), "k R, N"); // should be split into just chars
        setFormElement(Locator.name("deltaMass"), "8.01"); // should be rounded to a whole number
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Search"));
        //waitForText("1 - 31 of 31");
        assertEquals(0, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'R[+10]')]")));
        assertEquals(31, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'K[+8]')]")));

        // test custom name search type
        _ext4Helper.selectRadioButton("Search by:", "Modification name");
        assertElementNotVisible(Locator.name("aminoAcids"));
        assertElementNotVisible(Locator.name("deltaMass"));
        assertElementNotVisible(Locator.name("unimodName"));
        assertElementVisible(Locator.name("customName"));
        _ext4Helper.selectRadioButton("Type:", "Names used in imported experiments");
        _ext4Helper.selectComboBoxItem("Custom name:", "Label:13C(6)15N(4) (C-term R)");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Search"));
        //waitForText("1 - 13 of 13");
        assertEquals(13, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'R[+10]')]")));
        assertEquals(0, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'K[+8]')]")));
        _ext4Helper.selectComboBoxItem("Custom name:", "Label:13C(6)15N(2) (C-term K)");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Search"));
        //waitForText("1 - 31 of 31");
        assertEquals(0, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'R[+10]')]")));
        assertEquals(31, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'K[+8]')]")));

        // test unimod name search type
        _ext4Helper.selectRadioButton("Type:", "All Unimod modifications");
        assertElementNotVisible(Locator.name("aminoAcids"));
        assertElementNotVisible(Locator.name("deltaMass"));
        assertElementNotVisible(Locator.name("customName"));
        assertElementVisible(Locator.name("unimodName"));
        _ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithLabelContaining("Unimod name:"), "Label:13C(6)15N(4) (C-term R)");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Search"));
        //waitForText("1 - 13 of 13");
        assertEquals(13, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'R[+10]')]")));
        assertEquals(0, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'K[+8]')]")));

        // test C-term search using special character (i.e. ] )
        _ext4Helper.selectRadioButton("Search by:", "Delta mass");
        setFormElement(Locator.name("aminoAcids"), "]");
        setFormElement(Locator.name("deltaMass"), "8");
        waitAndClickAndWait(Ext4Helper.Locators.ext4Button("Search"));
        //waitForText("1 - 31 of 31");
        assertEquals(0, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'R[+10]')]")));
        assertEquals(31, getElementCount( Locator.xpath("//td//a/span[contains(@title, 'K[+8]')]")));
    }

    @LogMethod
    protected void verifyPeptide()
    {
        // Click on a peptide.
        String targetProtein = "LTSLNVVAGSDLR";
        clickAndWait(Locator.linkContainingText(targetProtein));
        //Verify it’s associated with the right protein and other values from details view.
        //protein name, portien, neutral mass, avg. RT , precursor
        assertTextPresent(targetProtein, "YAL038W", "1343.740", "27.9232", "677.8818++ (heavy)");

        //Verify the spectrum shows up correctly.

        //Verify we get the expected number of chromatogram graphs.
        assertElementPresent(Locator.xpath("//img[contains(@src, 'peptideChromatogramChart.view')]"), 1);
        assertElementPresent(Locator.xpath("//img[contains(@src, 'precursorChromatogramChart.view')]"), 2);
        assertElementPresent(Locator.xpath("//img[contains(@alt, 'Chromatogram')]"), 3);

        //Click on a precursor icon link.
        clickAndWait(Locator.linkWithHref("precursorAllChromatogramsChart.view?"));
        //Verify expected values in detail view. Verify chromatogram.
        assertTextPresentInThisOrder("Precursor Chromatograms", "YAL038W", "LTSLNVVAGSDLR", "672.8777");
        assertElementPresent(Locator.xpath("//img[contains(@src, 'Chromatogram')]"));

        goBack();
        clickAndWait(Locator.linkContainingText("YAL038W"));
        //Verify summary info
        assertTextPresent("CDC19 SGDID:S000000036, Chr I from 71787-73289, Verified ORF, \"Pyruvate kinase, functions as a homotetramer in glycolysis to convert phosphoenolpyruvate to pyruvate,",
                "Sequence Coverage", "Peptides", "LTSLNVVAGSDLR", "TNNPETLVALR", "GVNLPGTDVDLPALSEK", "TANDVLTIR",
                "GDLGIEIPAPEVLAVQK", "EPVSDWTDDVEAR", "Peak Areas");

        goBack();
        clickAndWait(Locator.linkContainingText(SKY_FILE));
        //Toggle to Transition view (click on down arrow in Precursor List webpart header)
        click(Locator.xpath("//th[span[contains(text(), 'Precursor List')]]/span/a/span[contains(@class, 'fa-caret-down')]"));
        clickAndWait(Locator.tagContainingText("span", "Transition List"));
        waitForText("Transition List");
        DataRegionTable drt = new DataRegionTable("transitions_view", this);
        drt.getDataAsText(5, "Label");
        assertEquals("heavy", drt.getDataAsText(5, "Label"));
        assertEquals("1353.7491", drt.getDataAsText(5, "Precursor Neutral Mass"));
        assertEquals("677.8818", drt.getDataAsText(5, "Q1 m/z"));
        assertEquals("y7", drt.getDataAsText(5, "Fragment"));
        assertEquals("727.3972", drt.getDataAsText(5, "Q3 m/z"));
        // We don't find these values based on their column headers because DataRegionTable gets confused with the
        // nested data regions having the same id in the HTML. The checks above happen to work because
        // they correspond to columns that aren't in the parent table, so the XPath flips to the second table with
        // that id, which has enough columns to satisfy the Locator
        assertTextPresent("1343.740", "1226.661", "1001.550");

        //Click down arrow next to protein name. Click "Search for other references to this protein"
        Locator l = Locator.xpath("//span[a[text()='YAL038W']]/span/img");
        waitForElement(l);
        mouseOver(l);
        waitForText("Search for other references to this protein");
        clickAndWait(Locator.linkContainingText("Search for other references to this protein"));

        //Verify Targeted MS Peptides section of page.
        //Click on Details link.
        //Spot check some values.
        assertTextPresent("Protein Search Results", "Targeted MS Peptides", "LTSLNVVAGSDLR", "TNNPETLVALR",
                "GVNLPGTDVDLPALSEK", "TANDVLTIR", "GDLGIEIPAPEVLAVQK", "EPVSDWTDDVEAR");
        click(Locator.tag("img").withAttributeContaining("src", "plus.gif"));
        assertTextPresent("I from 71787-73289, Verified ORF, \"Pyruvate kinase, functions as a homotetramer in glycolysis to convert phosphoenolpyruvate to pyruvate, the input for aerobic (TCA cyc...");
    }

    private void verifyQueries()
    {
        // As part of 16.1, the targetedms schema was updated to support both proteomics and small molecule data import
        // into separate tables (i.e. general table plus specific tables for each of the two types).
        // This test is to check backwards compatibility for SQL queries on the schema prior to 16.1
        // Note: this expects two runs to be imported: SKY_FILE and SKY_FILE2.

        // Test query against targetedms.peptide
        String querySql = "SELECT \n" +
                "Id, PeptideGroupId, Sequence, StartIndex, EndIndex, PreviousAa, NextAa, CalcNeutralMass, \n" +
                "NumMissedCleavages, Rank, RtCalculatorScore, PredictedRetentionTime, \n" +
                "AvgMeasuredRetentionTime, Decoy, Note, PeptideModifiedSequence, StandardType,\n" +
                "ExplicitRetentionTime, Annotations NoteAnnotations, \n" +
                "ModifiedPeptideDisplayColumn, RepresentivePrecursorCount,\n" +
                "PeptideGroupId.RunId.Folder.Path,\n" +
                "PeptideGroupId.RunId.File,\n" +
                "PeptideGroupId.Label\n" +
                "FROM peptide";
        createQuery(PageFlowUtil.encode(getProjectName()), "query_peptide", "targetedms", querySql, null, false);
        navigateToQuery("targetedms", "query_peptide");
        waitForElement(Locator.paginationText(45));
        DataRegionTable query = new DataRegionTable("query", this);
        query.setFilter("Sequence", "Equals", "TNNPETLVALR");
        assertEquals(1, query.getDataRowCount());
        assertEquals("YAL038W", query.getDataAsText(0, "Protein / Label"));
        assertElementPresent(Locator.linkWithText("YAL038W"));
        assertEquals("TNNPETLVALR", query.getDataAsText(0, "Modified Peptide"));
        assertEquals("K", query.getDataAsText(0, "Next Aa"));
        assertEquals(SKY_FILE, query.getDataAsText(0, "File"));
        query.clearFilter("Sequence");

        // Test query against targetedms.precursor
        querySql = "SELECT \n" +
                "Id, PeptideId, IsotopeLabelId,\n" +
                "Mz, Charge, NeutralMass, ModifiedSequence, CollisionEnergy, DeclusteringPotential, Decoy,\n" +
                "DecoyMassShift, Note, Modified, RepresentativeDataState, ExplicitCollisionEnergy,\n" +
                "ExplicitDriftTimeMsec, ExplicitDriftTimeHighEnergyOffsetMsec, Annotations, TransitionCount,\n" +
                "ModifiedPrecursorDisplayColumn, NoteAnnotations, \n" +
                "PeptideId.PeptideGroupId.Label, \n" +
                "PeptideId.PeptideGroupId.Description,\n" +
                "PeptideId.PeptideGroupId.NoteAnnotations AS PeptideGroupIdNoteAnnotations,\n" +
                "PeptideId.ModifiedPeptideDisplayColumn, \n" +
                "PeptideId.NoteAnnotations AS PeptideIdNoteAnnotations,\n" +
                "PeptideId.NumMissedCleavages,\n" +
                "PeptideId.CalcNeutralMass,\n" +
                "PeptideId.Rank,\n" +
                "IsotopeLabelId.Name\n" +
                "FROM precursor";
        createQuery(PageFlowUtil.encode(getProjectName()), "query_precursor", "targetedms", querySql, null, false);
        navigateToQuery("targetedms", "query_precursor");
        waitForElement(Locator.paginationText(89));
        query = new DataRegionTable("query", this);
        query.setFilter("ModifiedSequence", "Equals", "LTSLNVVAGSDLR[+10]");
        assertEquals(1, query.getDataRowCount());
        assertEquals("677.8818", query.getDataAsText(0, "Q1 m/z"));
        assertEquals("YAL038W", query.getDataAsText(0, "Protein / Label"));
        assertElementPresent(Locator.linkWithText("YAL038W"));
        assertEquals("LTSLNVVAGSDLR", query.getDataAsText(0, "Peptide"));
        assertEquals("1343.7408", query.getDataAsText(0, "Peptide Neutral Mass"));
        query.clearFilter("ModifiedSequence");

        // Test query against targetedms.transition
        querySql = "SELECT \n" +
                "Id, PrecursorId, Mz, Charge, NeutralMass, NeutralLossMass, FragmentType, FragmentOrdinal,\n" +
                "CleavageAa, LibraryRank, LibraryIntensity, IsotopeDistIndex, IsotopeDistRank,\n" +
                "IsotopeDistProportion, Decoy, DecoyMassShift, Note, MassIndex, MeasuredIonName,\n" +
                "Annotations, Fragment, NoteAnnotations,\n" +
                "PrecursorId.PeptideId.PeptideGroupId.Label,\n" +
                "PrecursorId.PeptideId.PeptideGroupId.Description,\n" +
                "PrecursorId.PeptideId.PeptideGroupId.Annotations AS PeptideGroupIdAnnotations,\n" +
                "PrecursorId.PeptideId.ModifiedPeptideDisplayColumn,\n" +
                "PrecursorId.PeptideId.Annotations AS PeptideIdAnnotations,\n" +
                "PrecursorId.PeptideId.NumMissedCleavages,\n" +
                "PrecursorId.PeptideId.CalcNeutralMass,\n" +
                "PrecursorId.PeptideId.Rank,\n" +
                "PrecursorId.ModifiedPrecursorDisplayColumn,\n" +
                "PrecursorId.Annotations AS PrecursorIdAnnotations,\n" +
                "PrecursorId.IsotopeLabelId.Name,\n" +
                "PrecursorId.NeutralMass AS PrecursorIdNeutralMass,\n" +
                "PrecursorId.Mz AS PrecursorIdMz,\n" +
                "PrecursorId.Charge AS PrecursorIdCharge\n" +
                "FROM transition";
        createQuery(PageFlowUtil.encode(getProjectName()), "query_transition", "targetedms", querySql, null, false);
        navigateToQuery("targetedms", "query_transition");
        waitForElement(Locator.paginationText(1, 100, 299));
        query = new DataRegionTable("query", this);
        query.setFilter("PrecursorId", "Equals", "LTSLNVVAGSDLR[+10]");
        assertEquals(3, query.getDataRowCount());
        assertEquals("677.8818", query.getDataAsText(0, "Precursor Id Mz"));
        assertEquals("YAL038W", query.getDataAsText(0, "Protein / Label"));
        assertElementPresent(Locator.linkWithText("YAL038W"));
        assertEquals("LTSLNVVAGSDLR", query.getDataAsText(0, "Peptide"));
        assertEquals("1343.7408", query.getDataAsText(0, "Peptide Neutral Mass"));
        query.clearFilter("PrecursorId");

        // Test query against targetedms.librarydocprecursor
        querySql = "SELECT GeneralMoleculeId.Id AS Id1, \n" +
                "GeneralMoleculeId.Sequence AS Sequence1,\n" +
                "GeneralMoleculeId.PeptideGroupId.Label AS Protein1,\n" +
                "PeptideId.Id AS Id2,\n" +
                "PeptideId.Sequence AS Sequence2,\n" +
                "PeptideId.PeptideGroupId.Label AS Protein2\n" +
                "FROM librarydocprecursor";
        createQuery(PageFlowUtil.encode(getProjectName()), "query_librarydocprecursor", "targetedms", querySql, null, false);
        navigateToQuery("targetedms", "query_librarydocprecursor");
        waitForElement(Locator.paginationText(89));
        query = new DataRegionTable("query", this);
        query.setFilter("Protein1", "Equals", "YAL038W_renamed");
        assertEquals(1, query.getDataRowCount());
        assertEquals(query.getDataAsText(0, "Id1"), query.getDataAsText(0, "Id2"));
        assertEquals(query.getDataAsText(0, "Sequence1"), query.getDataAsText(0, "Sequence1"));
        assertEquals(query.getDataAsText(0, "Protein1"), query.getDataAsText(0, "Protein2"));
        query.clearFilter("Protein1");
    }
}