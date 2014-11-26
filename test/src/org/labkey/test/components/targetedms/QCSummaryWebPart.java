package org.labkey.test.components.targetedms;

import org.junit.Assert;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.components.WebPart;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QCSummaryWebPart extends WebPart
{
    private static final String DEFAULT_TITLE = "QC Summary";

    private Integer _docCount;
    private Integer _fileCount;
    private Integer _precursorCount;

    public QCSummaryWebPart(BaseWebDriverTest test)
    {
        this(test, 0);
    }

    public QCSummaryWebPart(BaseWebDriverTest test, int index)
    {
        super(test, DEFAULT_TITLE, index);
    }

    public void clearCache()
    {
        _docCount = null;
        _fileCount = null;
        _precursorCount = null;
    }

    private void readSummary()
    {
        String docSummary = _test.getText(elements().docSummary);
        Pattern docPattern = Pattern.compile("(\\d+) Skyline document uploaded containing (\\d+) sample files");
        Matcher docMatcher = docPattern.matcher(docSummary);
        Assert.assertTrue(docSummary, docMatcher.find());
        _docCount = Integer.parseInt(docMatcher.group(1));
        _fileCount = Integer.parseInt(docMatcher.group(2));

        String precursorSummary = _test.getText(elements().precursorSummary);
        Pattern precurosrPattern = Pattern.compile("(\\d+) precursors tracked");
        Matcher precursorMatcher = precurosrPattern.matcher(precursorSummary);
        Assert.assertTrue(precursorSummary, precursorMatcher.find());
        _precursorCount = Integer.parseInt(precursorMatcher.group(1));

    }

    public int getDocCount()
    {
        if (_docCount == null)
            readSummary();
        return _docCount;
    }

    public int getFileCount()
    {
        if (_fileCount == null)
            readSummary();
        return _fileCount;
    }

    public int getPrecursorCount()
    {
        if (_precursorCount == null)
            readSummary();
        return _precursorCount;
    }

    @Override
    protected Elements elements()
    {
        return new Elements();
    }

    private class Elements extends WebPart.Elements
    {
        public Locator.XPathLocator docSummary = webPart.append(Locator.tagWithId("div", "docSummary"));
        public Locator.XPathLocator precursorSummary = webPart.append(Locator.tagWithId("div", "precursorSummary"));
    }
}