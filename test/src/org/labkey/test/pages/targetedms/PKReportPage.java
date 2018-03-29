package org.labkey.test.pages.targetedms;

import org.labkey.test.Locator;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.util.StringUtils;

import static org.junit.Assert.assertEquals;

public class PKReportPage extends LabKeyPage<PKReportPage.ElementCache>
{
    private int _totalSubgroupTimeRowCount;

    public PKReportPage(WebDriver driver, int totalSubgroupTimeRowCount)
    {
        super(driver);
        _totalSubgroupTimeRowCount = totalSubgroupTimeRowCount;
        waitForPage();
    }

    @Override
    protected void waitForPage()
    {
        waitForElements(elementCache().timeCellLoc, _totalSubgroupTimeRowCount);
    }

    public void setAllSubgroupTimeCheckboxes(String subgroup, int count, boolean check)
    {
        for (int i = 1; i <= count; i++)
        {
            setSubgroupTimeCheckbox(subgroup, true, i, check);
            setSubgroupTimeCheckbox(subgroup, false, i, check);
        }
    }

    public void setSubgroupTimeCheckbox(String subgroup, boolean isC0, int rowIndex, boolean check)
    {
        int colIndex = isC0 ? 2 : 3;
        Locator.XPathLocator loc = Locator.xpath("//*[@id=\"pk-table-input-" + subgroup + "\"]/tbody/tr[" + rowIndex + "]/td[" + colIndex + "]/input");
        if (check)
            checkCheckbox(loc);
        else
            uncheckCheckbox(loc);

        sleep(1000); // each input change persists the settings to the server, so wait a second
    }

    public void setNonIVC0(String subgroup, String newValue)
    {
        Locator inputLoc = Locator.id("nonIVC0-" + subgroup);
        Locator warnLoc = Locator.id("nonIVC0Controls-Warn-" + subgroup);
        Locator btnLoc = Locator.id("btnNonIVC0-" + subgroup);

        if (StringUtils.isEmpty(getFormElement(inputLoc)))
            assertElementVisible(warnLoc);

        setFormElement(inputLoc, newValue);
        click(btnLoc);
        sleep(1000); // each input change persists the settings to the server, so wait a second

        if (!StringUtils.isEmpty(getFormElement(inputLoc)))
            assertElementNotVisible(warnLoc);
    }

    public void verifyTableColumnValues(String table, String subgroup, int colIndex, String expectedValsStr)
    {
        String tableId = "pk-table-" + table + "-" + subgroup;
        String actualVals = columnDataAsString(Locator.id(tableId).findElement(getDriver()), colIndex);
        assertEquals("Incorrect values in data table " + tableId, expectedValsStr, actualVals);
    }

    private String columnDataAsString (WebElement table,int col)
    {
        String retVal = "";
        int size = table.findElements(By.tagName("tr")).size();
        for (int i = 1; i < size ; i++)
            retVal += Locator.xpath("//tbody/tr[" + i + "]/td[" + col + "]").findElement(table).getText() + " ";
        return retVal.trim();
    }

    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        Locator timeCellLoc = Locator.tagWithClass("td", "pk-table-time");
    }
}